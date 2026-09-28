package com.piremote.app.data

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

enum class LinuxEnvironmentStatus {
    NOT_INSTALLED,   // 原生精简 Shell 模式 (仅自带 /system/bin/sh)
    BASH_READY,      // 内置 GNU Bash + BusyBox 100+ 工具箱已就绪
    INITIALIZING,    // 正在部署/解压 Alpine Linux 根文件系统
    READY,           // Alpine Linux 完整子系统 (含 apk 包管理器) 完全就绪
}

data class ToolchainInfo(
    val hasNode: Boolean = false,
    val nodeVersion: String? = null,
    val hasNpm: Boolean = false,
    val npmVersion: String? = null,
    val hasGit: Boolean = false,
    val gitVersion: String? = null,
    val hasBash: Boolean = false,
    val bashVersion: String? = null,
    val hasBusyBox: Boolean = false,
    val busyBoxVersion: String? = null,
    val hasAlpine: Boolean = false,
    val alpineVersion: String? = null,
    val hasProot: Boolean = false,
    val prootVersion: String? = null,
    val hasPython: Boolean = false,
    val pythonVersion: String? = null,
    val hasPiAgent: Boolean = false,
    val piVersion: String? = null,
    val hasClaudeCode: Boolean = false,
    val claudeVersion: String? = null,
    val environmentType: String = "Android Native",
)

/**
 * Manages the embedded Linux subsystem, GNU Bash, Alpine rootfs, and Node.js toolchain.
 * Supports detecting tool availability, extracting native ELF binaries, streaming download
 * and decompression of Alpine Linux minirootfs, and configuring runtime environment variables.
 */
class LinuxEnvironmentManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LinuxEnvMgr"

        @Volatile
        private var INSTANCE: LinuxEnvironmentManager? = null

        fun getInstance(context: Context): LinuxEnvironmentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LinuxEnvironmentManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        val STATX_SHIM_C_SOURCE = """
#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <errno.h>
#include <string.h>
#include <stdint.h>

#ifndef SYS_statx
#if defined(__aarch64__)
#define SYS_statx 291
#elif defined(__x86_64__)
#define SYS_statx 332
#elif defined(__arm__)
#define SYS_statx 397
#else
#define SYS_statx 291
#endif
#endif

#ifndef STATX_BASIC_STATS
#define STATX_BASIC_STATS 0x000007ffU
#endif

struct statx_timestamp {
    int64_t tv_sec;
    uint32_t tv_nsec;
    int32_t __reserved;
};

struct statx {
    uint32_t stx_mask;
    uint32_t stx_blksize;
    uint64_t stx_attributes;
    uint32_t stx_nlink;
    uint32_t stx_uid;
    uint32_t stx_gid;
    uint16_t stx_mode;
    uint16_t __spare0[1];
    uint64_t stx_ino;
    uint64_t stx_size;
    uint64_t stx_blocks;
    uint64_t stx_attributes_mask;
    struct statx_timestamp stx_atime;
    struct statx_timestamp stx_btime;
    struct statx_timestamp stx_ctime;
    struct statx_timestamp stx_mtime;
    uint32_t stx_rdev_major;
    uint32_t stx_rdev_minor;
    uint32_t stx_dev_major;
    uint32_t stx_dev_minor;
    uint64_t __spare2[14];
};

static inline long raw_syscall(long n, long a1, long a2, long a3, long a4, long a5, long a6) {
#if defined(__aarch64__)
    register long r8 __asm__("x8") = n;
    register long r0 __asm__("x0") = a1;
    register long r1 __asm__("x1") = a2;
    register long r2 __asm__("x2") = a3;
    register long r3 __asm__("x3") = a4;
    register long r4 __asm__("x4") = a5;
    register long r5 __asm__("x5") = a6;
    __asm__ __volatile__(
        "svc #0"
        : "=r"(r0)
        : "r"(r8), "0"(r0), "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r5)
        : "memory", "cc"
    );
    return r0;
#else
    static long (*libc_syscall)(long, ...) = NULL;
    if (!libc_syscall) {
        libc_syscall = (long (*)(long, ...))dlsym(RTLD_NEXT, "syscall");
    }
    return libc_syscall ? libc_syscall(n, a1, a2, a3, a4, a5, a6) : -ENOSYS;
#endif
}

static int do_statx_shim(int dirfd, const char *pathname, int flags, unsigned int mask, struct statx *statxbuf) {
    if (!statxbuf) {
        errno = EFAULT;
        return -1;
    }

    int at_flags = 0;
    if (flags & 0x100)  at_flags |= AT_SYMLINK_NOFOLLOW;
    if (flags & 0x1000) at_flags |= 0x1000; // AT_EMPTY_PATH

    struct stat st;
    int res = -1;

    if ((flags & 0x1000) && (!pathname || pathname[0] == '\0')) {
        res = fstat(dirfd, &st);
    } else {
        res = fstatat(dirfd, pathname ? pathname : "", &st, at_flags);
    }

    if (res != 0) {
        return -1;
    }

    memset(statxbuf, 0, sizeof(*statxbuf));
    statxbuf->stx_mask = STATX_BASIC_STATS;
    statxbuf->stx_blksize = (uint32_t)st.st_blksize;
    statxbuf->stx_nlink = (uint32_t)st.st_nlink;
    statxbuf->stx_uid = (uint32_t)st.st_uid;
    statxbuf->stx_gid = (uint32_t)st.st_gid;
    statxbuf->stx_mode = (uint16_t)st.st_mode;
    statxbuf->stx_ino = (uint64_t)st.st_ino;
    statxbuf->stx_size = (uint64_t)st.st_size;
    statxbuf->stx_blocks = (uint64_t)st.st_blocks;

    statxbuf->stx_atime.tv_sec = st.st_atim.tv_sec;
    statxbuf->stx_atime.tv_nsec = (uint32_t)st.st_atim.tv_nsec;
    statxbuf->stx_mtime.tv_sec = st.st_mtim.tv_sec;
    statxbuf->stx_mtime.tv_nsec = (uint32_t)st.st_mtim.tv_nsec;
    statxbuf->stx_ctime.tv_sec = st.st_ctim.tv_sec;
    statxbuf->stx_ctime.tv_nsec = (uint32_t)st.st_ctim.tv_nsec;
    statxbuf->stx_btime = statxbuf->stx_ctime;

    statxbuf->stx_dev_major = (uint32_t)((st.st_dev >> 8) & 0xfff);
    statxbuf->stx_dev_minor = (uint32_t)((st.st_dev & 0xff) | ((st.st_dev >> 12) & 0xfff00));
    statxbuf->stx_rdev_major = (uint32_t)((st.st_rdev >> 8) & 0xfff);
    statxbuf->stx_rdev_minor = (uint32_t)((st.st_rdev & 0xff) | ((st.st_rdev >> 12) & 0xfff00));

    return 0;
}

int statx(int dirfd, const char *pathname, int flags, unsigned int mask, struct statx *statxbuf) {
    return do_statx_shim(dirfd, pathname, flags, mask, statxbuf);
}

long syscall(long number, ...) {
    va_list args;
    va_start(args, number);
    if (number == SYS_statx) {
        int dirfd = va_arg(args, int);
        const char *pathname = va_arg(args, const char *);
        int flags = va_arg(args, int);
        unsigned int mask = va_arg(args, unsigned int);
        struct statx *statxbuf = va_arg(args, struct statx *);
        va_end(args);
        return do_statx_shim(dirfd, pathname, flags, mask, statxbuf);
    }
    long a1 = va_arg(args, long);
    long a2 = va_arg(args, long);
    long a3 = va_arg(args, long);
    long a4 = va_arg(args, long);
    long a5 = va_arg(args, long);
    long a6 = va_arg(args, long);
    va_end(args);

    long ret = raw_syscall(number, a1, a2, a3, a4, a5, a6);
    if (ret < 0 && ret >= -4095) {
        errno = -ret;
        return -1;
    }
    return ret;
}
        """.trimIndent()

        val STATX_BUILD_SH_SOURCE = """
#!/bin/sh
set -e
mkdir -p /opt/statfix
if [ ! -f /opt/statfix/statx_shim.so ]; then
    if ! command -v gcc >/dev/null 2>&1; then
        echo "[statfix] 正在安装编译工具链以生成 statx 垫片..."
        apk add --no-cache build-base >/dev/null 2>&1 || true
    fi
    if command -v gcc >/dev/null 2>&1 && [ -f /opt/statfix/statx_shim.c ]; then
        echo "[statfix] 正在编译 statx_shim.so..."
        gcc -shared -fPIC -O2 /opt/statfix/statx_shim.c -o /opt/statfix/statx_shim.so
        chmod 0755 /opt/statfix/statx_shim.so
    fi
fi
if [ -f /opt/statfix/statx_shim.so ]; then
    echo "/opt/statfix/statx_shim.so" > /etc/ld.so.preload
    echo "[statfix] statx 垫片已成功配置至 /etc/ld.so.preload"
fi
        """.trimIndent()
    }

    private val _status = MutableStateFlow(LinuxEnvironmentStatus.NOT_INSTALLED)
    val status: StateFlow<LinuxEnvironmentStatus> = _status.asStateFlow()

    private val _toolchain = MutableStateFlow(ToolchainInfo())
    val toolchain: StateFlow<ToolchainInfo> = _toolchain.asStateFlow()

    private val _installProgress = MutableStateFlow(0f to "")
    val installProgress: StateFlow<Pair<Float, String>> = _installProgress.asStateFlow()

    val nativeDir: File = File(context.applicationInfo.nativeLibraryDir)
    val nativeNodeFile: File = File(nativeDir, "libnode.so")
    val nativeBashFile: File = File(nativeDir, "libbash.so")
    val nativeBusyboxFile: File = File(nativeDir, "libbusybox.so")
    val nativeProotFile: File = File(nativeDir, "libproot.so")

    val rootfsDir: File = File(context.filesDir, "alpine_rootfs")
    val usrBinDir: File = File(rootfsDir, "usr/bin")
    val usrLocalBinDir: File = File(rootfsDir, "usr/local/bin")
    val homeDir: File = File(rootfsDir, "home/user").apply { if (!exists()) mkdirs() }
    val rootHomeDir: File = File(rootfsDir, "root").apply { if (!exists()) mkdirs() }
    val npmGlobalDir: File = File(homeDir, ".npm-global").apply { if (!exists()) mkdirs() }
    val npmBinDir: File = File(npmGlobalDir, "bin").apply { if (!exists()) mkdirs() }
    val tmpDir: File = File(rootfsDir, "tmp").apply { if (!exists()) mkdirs() }

    val localProotFile: File = File(context.filesDir, "bin/proot")
    val localBashFile: File = File(context.filesDir, "bin/bash")
    val localBusyboxFile: File = File(context.filesDir, "bin/busybox")

    init {
        CoroutineScope(Dispatchers.IO).launch {
            detectEnvironment()
        }
    }

    fun getProotExecutable(): File? {
        if (nativeProotFile.exists() && nativeProotFile.canExecute()) return nativeProotFile
        val origFile = File(localProotFile.parentFile, "proot.orig")
        if (origFile.exists()) {
            origFile.setExecutable(true, false)
            try { Os.chmod(origFile.absolutePath, 493) } catch (_: Exception) {}
        }
        if (localProotFile.exists()) {
            localProotFile.setExecutable(true, false)
            try { Os.chmod(localProotFile.absolutePath, 493) /* 0755 */ } catch (_: Exception) {}
            if (localProotFile.canExecute()) return localProotFile
        }
        if (origFile.exists() && origFile.canExecute()) return origFile
        return null
    }

    fun getBashExecutable(): File? {
        if (nativeBashFile.exists() && nativeBashFile.canExecute()) return nativeBashFile
        if (localBashFile.exists()) {
            localBashFile.setExecutable(true, false)
            try { Os.chmod(localBashFile.absolutePath, 493) } catch (_: Exception) {}
            if (localBashFile.canExecute()) return localBashFile
        }
        return null
    }

    fun getBusyboxExecutable(): File? {
        if (nativeBusyboxFile.exists() && nativeBusyboxFile.canExecute()) return nativeBusyboxFile
        if (localBusyboxFile.exists()) {
            localBusyboxFile.setExecutable(true, false)
            try { Os.chmod(localBusyboxFile.absolutePath, 493) } catch (_: Exception) {}
            if (localBusyboxFile.canExecute()) return localBusyboxFile
        }
        return null
    }

    fun isNativeNodeBundled(): Boolean = nativeNodeFile.exists() && nativeNodeFile.canExecute()
    fun isNativeBashBundled(): Boolean = getBashExecutable() != null
    fun isNativeBusyboxBundled(): Boolean = getBusyboxExecutable() != null
    fun isNativeProotBundled(): Boolean = getProotExecutable() != null

    fun isAlpineRootfsInstalled(): Boolean {
        val release = File(rootfsDir, "etc/alpine-release")
        val apk = File(rootfsDir, "etc/apk")
        val etcDir = File(rootfsDir, "etc")
        return (release.exists() || apk.exists()) && etcDir.exists()
    }

    /**
     * Ensures all guest mountpoints (sdcard, storage, native, dev, proc, sys, tmp) exist
     * and sets POSIX execute permissions (0755) on all binaries in the rootfs.
     */
    fun ensureRootfsStructureAndPermissions() {
        if (!rootfsDir.exists()) return

        val statfixDir = File(rootfsDir, "opt/statfix").apply { mkdirs() }

        // 1. Create essential guest mountpoints so PRoot bind-mounts succeed
        listOf("dev", "proc", "sys", "tmp", "root", "home/user", "sdcard", "storage", "native", "lib", "lib64", "bin", "sbin").forEach { sub ->
            try {
                File(rootfsDir, sub).mkdirs()
            } catch (_: Exception) {}
        }

        // Ensure tmp has full read/write/execute permissions (0777) for PRoot loader extraction
        try {
            val tmp = File(rootfsDir, "tmp")
            tmp.setReadable(true, false)
            tmp.setWritable(true, false)
            tmp.setExecutable(true, false)
            Os.chmod(tmp.absolutePath, 511 /* 0777 */)
        } catch (_: Exception) {}

        val binDir = File(rootfsDir, "bin")
        val busyboxFile = File(binDir, "busybox")
        val shFile = File(binDir, "sh")

        // 2. Ensure /bin/busybox is valid and executable
        if (busyboxFile.exists()) {
            try {
                busyboxFile.setExecutable(true, false)
                Os.chmod(busyboxFile.absolutePath, 493 /* 0755 */)
            } catch (_: Exception) {}
        }

        // 3. Ensure /bin/sh is a real working ELF binary (or valid symlink to busybox)
        // If sh is missing, or is a broken text file (< 10KB), copy the real busybox ELF over it!
        if (busyboxFile.exists() && busyboxFile.length() > 50000) {
            val needRepairSh = !shFile.exists() || shFile.length() < 10000
            if (needRepairSh) {
                shFile.delete()
                try {
                    Os.symlink("busybox", shFile.absolutePath)
                } catch (_: Exception) {}
                if (!shFile.exists() || shFile.length() < 10000) {
                    try {
                        busyboxFile.copyTo(shFile, overwrite = true)
                    } catch (_: Exception) {}
                }
            }
            shFile.setExecutable(true, false)
            try { Os.chmod(shFile.absolutePath, 493) } catch (_: Exception) {}
        }

        // 4. Ensure musl dynamic linker (/lib/ld-musl-aarch64.so.1) is a real ELF binary!
        val libDir = File(rootfsDir, "lib")
        val muslLibc = File(libDir, "libc.musl-aarch64.so.1")
        val muslLd = File(libDir, "ld-musl-aarch64.so.1")
        val lib64Dir = File(rootfsDir, "lib64").apply { mkdirs() }
        val muslLd64 = File(lib64Dir, "ld-musl-aarch64.so.1")

        // In Alpine, libc.musl-aarch64.so.1 is the actual dynamic linker and libc combined (~600KB)
        val realMusl = when {
            muslLibc.exists() && muslLibc.length() > 50000 -> muslLibc
            muslLd.exists() && muslLd.length() > 50000 -> muslLd
            else -> null
        }

        if (realMusl != null) {
            realMusl.setExecutable(true, false)
            try { Os.chmod(realMusl.absolutePath, 493) } catch (_: Exception) {}

            // Ensure ld-musl-aarch64.so.1 in /lib is not a corrupted text file
            if (!muslLd.exists() || muslLd.length() < 10000) {
                muslLd.delete()
                try {
                    Os.symlink("libc.musl-aarch64.so.1", muslLd.absolutePath)
                } catch (_: Exception) {}
                if (!muslLd.exists() || muslLd.length() < 10000) {
                    try { realMusl.copyTo(muslLd, overwrite = true) } catch (_: Exception) {}
                }
            }
            muslLd.setExecutable(true, false)
            try { Os.chmod(muslLd.absolutePath, 493) } catch (_: Exception) {}

            // Ensure /lib64/ld-musl-aarch64.so.1 also exists
            if (!muslLd64.exists() || muslLd64.length() < 10000) {
                try {
                    muslLd.copyTo(muslLd64, overwrite = true)
                    muslLd64.setExecutable(true, false)
                    Os.chmod(muslLd64.absolutePath, 493)
                } catch (_: Exception) {}
            }
        }

        // 5. Repair any corrupted symlinks in bin, sbin, usr/bin that were saved as plain text
        if (busyboxFile.exists() && busyboxFile.length() > 50000) {
            listOf("bin", "sbin", "usr/bin", "usr/sbin").forEach { sub ->
                val dir = File(rootfsDir, sub)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && f.length() in 1..200) {
                            try {
                                val text = f.readText().trim()
                                if (text == "busybox" || text == "/bin/busybox" || text.endsWith("/busybox")) {
                                    f.delete()
                                    try {
                                        Os.symlink("busybox", f.absolutePath)
                                    } catch (_: Exception) {
                                        busyboxFile.copyTo(f, overwrite = true)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        try {
                            f.setExecutable(true, false)
                            Os.chmod(f.absolutePath, 493)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 6. Ensure /sbin/apk is executable
        val apkFile = File(rootfsDir, "sbin/apk")
        if (apkFile.exists()) {
            try {
                apkFile.setExecutable(true, false)
                Os.chmod(apkFile.absolutePath, 493)
            } catch (_: Exception) {}
        }

        // 7. Ensure PRoot executable has 0755
        val proot = getProotExecutable()
        if (proot != null && proot.exists()) {
            try {
                proot.setExecutable(true, false)
                Os.chmod(proot.absolutePath, 493)
            } catch (_: Exception) {}
        }

        // 8. Create Termux-compatible pkg command wrapper so `pkg install/add/update` maps to apk
        val wrapperScript = """
            #!/bin/sh
            unset PROOT_NO_SECCOMP
            export npm_config_prefix="/usr/local"
            case "${'$'}1" in
                install|add)
                    shift
                    case "${'$'}1" in
                        pi|pi-agent|@earendil-works/pi-coding-agent)
                            echo "[Pi Remote] 正在全自动安装 Pi Agent (@earendil-works/pi-coding-agent)..."
                            rm -f /usr/local/bin/pi
                            if ! npm install -g --force --registry=https://registry.npmmirror.com @earendil-works/pi-coding-agent; then
                                npm install -g --force --registry=https://registry.npmjs.org @earendil-works/pi-coding-agent || true
                            fi
                            if [ -f "/opt/statfix/pi_launcher.sh" ]; then
                                cp -f /opt/statfix/pi_launcher.sh /usr/local/bin/pi
                                chmod 755 /usr/local/bin/pi
                            fi
                            chmod -R 755 /usr/local/bin 2>/dev/null || true
                            exit 0
                            ;;
                        claude|claude-code|@anthropic-ai/claude-code)
                            echo "[Pi Remote] 正在全自动安装 Claude Code (@anthropic-ai/claude-code)..."
                            rm -f /usr/local/bin/claude
                            if ! npm install -g --force --registry=https://registry.npmmirror.com @anthropic-ai/claude-code; then
                                npm install -g --force --registry=https://registry.npmjs.org @anthropic-ai/claude-code || true
                            fi
                            if [ -f "/opt/statfix/claude_launcher.sh" ]; then
                                cp -f /opt/statfix/claude_launcher.sh /usr/local/bin/claude
                                chmod 755 /usr/local/bin/claude
                            fi
                            chmod -R 755 /usr/local/bin 2>/dev/null || true
                            exit 0
                            ;;
                    esac
                    if ! apk add "${'$'}@"; then
                        echo "[提示] 安装失败或遇到异常，正在尝试自动解决版本漂移..."
                        apk upgrade --available busybox 2>/dev/null || true
                        if ! apk add "${'$'}@"; then
                            echo "[提示] 正在尝试自动切换备用镜像源..."
                            if grep -q "dl-cdn.alpinelinux.org" /etc/apk/repositories; then
                                echo "已自动切换到中科大国内源 (USTC)..."
                                sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories
                            elif grep -q "mirrors.ustc.edu.cn" /etc/apk/repositories; then
                                echo "已自动切换到阿里云国内源 (Aliyun)..."
                                sed -i 's/mirrors.ustc.edu.cn/mirrors.aliyun.com/g' /etc/apk/repositories
                            else
                                echo "已自动切换到 Alpine 官方全球 CDN..."
                                sed -i 's/mirrors.aliyun.com/dl-cdn.alpinelinux.org/g' /etc/apk/repositories
                            fi
                            apk update && exec apk add "${'$'}@"
                        fi
                    fi
                    # Auto-check and build statx shim if nodejs was installed
                    if [ ! -f /opt/statfix/statx_shim.so ] && [ -f /usr/bin/node ]; then
                        echo "[提示] 检测到已安装 Node.js，正在自动编译就绪 statx 兼容垫片..."
                        sh /opt/statfix/build.sh 2>/dev/null || true
                    fi
                    chmod -R 755 /usr/local/bin 2>/dev/null || true
                    ;;
                pi|agent|pi-agent)
                    echo "[Pi Remote] 正在全自动安装/更新 Pi Agent..."
                    rm -f /usr/local/bin/pi
                    if ! npm install -g --force --registry=https://registry.npmmirror.com @earendil-works/pi-coding-agent; then
                        npm install -g --force --registry=https://registry.npmjs.org @earendil-works/pi-coding-agent || true
                    fi
                    if [ -f "/opt/statfix/pi_launcher.sh" ]; then
                        cp -f /opt/statfix/pi_launcher.sh /usr/local/bin/pi
                        chmod 755 /usr/local/bin/pi
                    fi
                    chmod -R 755 /usr/local/bin 2>/dev/null || true
                    ;;
                claude|claude-code)
                    echo "[Pi Remote] 正在全自动安装/更新 Claude Code..."
                    rm -f /usr/local/bin/claude
                    if ! npm install -g --force --registry=https://registry.npmmirror.com @anthropic-ai/claude-code; then
                        npm install -g --force --registry=https://registry.npmjs.org @anthropic-ai/claude-code || true
                    fi
                    if [ -f "/opt/statfix/claude_launcher.sh" ]; then
                        cp -f /opt/statfix/claude_launcher.sh /usr/local/bin/claude
                        chmod 755 /usr/local/bin/claude
                    fi
                    chmod -R 755 /usr/local/bin 2>/dev/null || true
                    ;;
                uninstall|remove|del)
                    shift
                    exec apk del "${'$'}@"
                    ;;
                update)
                    shift
                    if ! apk update "${'$'}@"; then
                        echo "[提示] 更新失败，正在尝试自动切换备用镜像源..."
                        if grep -q "dl-cdn.alpinelinux.org" /etc/apk/repositories; then
                            echo "已自动切换到中科大国内源 (USTC)..."
                            sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories
                        else
                            echo "已自动切换到 Alpine 官方全球 CDN..."
                            sed -i 's/mirrors.ustc.edu.cn/dl-cdn.alpinelinux.org/g' /etc/apk/repositories
                        fi
                        exec apk update "${'$'}@"
                    fi
                    ;;
                upgrade)
                    shift
                    exec apk upgrade --available "${'$'}@"
                    ;;
                search)
                    shift
                    exec apk search "${'$'}@"
                    ;;
                show|info)
                    shift
                    exec apk info "${'$'}@"
                    ;;
                full|setup-full)
                    echo "正在安装 Alpine 完整系统核心套件 (alpine-base, alpine-conf, ca-certificates, tzdata, coreutils, procps)..."
                    exec apk add alpine-base alpine-conf ca-certificates tzdata coreutils procps "${'$'}@"
                    ;;
                mirror)
                    case "${'$'}2" in
                        global|official)
                            echo "Switching to official global CDN (dl-cdn.alpinelinux.org)..."
                            sed -i 's/mirrors.ustc.edu.cn/dl-cdn.alpinelinux.org/g' /etc/apk/repositories
                            sed -i 's/mirrors.aliyun.com/dl-cdn.alpinelinux.org/g' /etc/apk/repositories
                            apk update
                            npm config set registry https://registry.npmjs.org 2>/dev/null || true
                            echo "npm 全局源已同步切换至官方源 (registry.npmjs.org)"
                            ;;
                        ustc)
                            echo "Switching to USTC mirror (mirrors.ustc.edu.cn)..."
                            sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories
                            sed -i 's/mirrors.aliyun.com/mirrors.ustc.edu.cn/g' /etc/apk/repositories
                            apk update
                            npm config set registry https://registry.npmmirror.com 2>/dev/null || true
                            echo "npm 全局源已同步切换至国内淘宝/阿里云镜像源 (registry.npmmirror.com)"
                            ;;
                        aliyun)
                            echo "Switching to Aliyun mirror (mirrors.aliyun.com)..."
                            sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
                            sed -i 's/mirrors.ustc.edu.cn/mirrors.aliyun.com/g' /etc/apk/repositories
                            apk update
                            npm config set registry https://registry.npmmirror.com 2>/dev/null || true
                            echo "npm 全局源已同步切换至国内淘宝/阿里云镜像源 (registry.npmmirror.com)"
                            ;;
                        auto|"")
                            echo "正在探测可用镜像源..."
                            best_m=""
                            for m in "mirrors.ustc.edu.cn" "mirrors.aliyun.com" "dl-cdn.alpinelinux.org"; do
                                echo -n "测试 ${'$'}m ... "
                                if wget -q --spider -T 3 "https://${'$'}m/alpine/v3.20/main/aarch64/APKINDEX.tar.gz" 2>/dev/null; then
                                    echo "连接正常"
                                    best_m="${'$'}m"
                                    break
                                else
                                    echo "不可达"
                                fi
                            done
                            if [ -z "${'$'}best_m" ]; then
                                best_m="mirrors.ustc.edu.cn"
                            fi
                            echo "自动选用 Alpine 镜像: ${'$'}best_m"
                            echo "https://${'$'}best_m/alpine/v3.20/main" > /etc/apk/repositories
                            echo "https://${'$'}best_m/alpine/v3.20/community" >> /etc/apk/repositories
                            apk update
                            if [ "${'$'}best_m" != "dl-cdn.alpinelinux.org" ]; then
                                npm config set registry https://registry.npmmirror.com 2>/dev/null || true
                                echo "npm 镜像源已自动切换为国内高速源 (registry.npmmirror.com)"
                            else
                                npm config set registry https://registry.npmjs.org 2>/dev/null || true
                                echo "npm 镜像源已自动切换为官方全球源 (registry.npmjs.org)"
                            fi
                            ;;
                        *)
                            echo "Usage: pkg mirror [global|ustc|aliyun|auto]"
                            echo "Current repositories:"
                            cat /etc/apk/repositories
                            ;;
                    esac
                    ;;
                *)
                    exec apk "${'$'}@"
                    ;;
            esac
        """.trimIndent() + "\n"

        listOf("usr/local/bin", "bin").forEach { dirRel ->
            try {
                val d = File(rootfsDir, dirRel).apply { mkdirs() }
                val pkgFile = File(d, "pkg")
                pkgFile.writeText(wrapperScript)
                pkgFile.setExecutable(true, false)
                Os.chmod(pkgFile.absolutePath, 493 /* 0755 */)
            } catch (_: Exception) {}
        }

        // 9. Ensure /etc/profile.d, /root/.bashrc, /root/.profile export unified POSIX PATH and npm prefix
        try {
            val profileDir = File(rootfsDir, "etc/profile.d").apply { mkdirs() }
            val envScript = File(profileDir, "00-env.sh")
            val envContent = """
                #!/bin/sh
                unset PROOT_NO_SECCOMP
                export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.npm-global/bin:${'$'}PATH"
                export npm_config_prefix="/usr/local"
                export NODE_PATH="/usr/local/lib/node_modules:/usr/lib/node_modules"
                export LANG="en_US.UTF-8"
                export TERM="xterm-256color"
                export COLORTERM="truecolor"
                export PI_SKIP_VERSION_CHECK=1
                if [ -f "/opt/statfix/term_size" ]; then
                    read -r c r < /opt/statfix/term_size 2>/dev/null
                    [ -n "${'$'}c" ] && export COLUMNS="${'$'}c"
                    [ -n "${'$'}r" ] && export LINES="${'$'}r"
                fi
                if [ -f "/opt/statfix/tty_shim.cjs" ]; then
                    export NODE_OPTIONS="--require /opt/statfix/tty_shim.cjs ${'$'}{NODE_OPTIONS}"
                fi
            """.trimIndent() + "\n"
            envScript.writeText(envContent)
            envScript.setExecutable(true, false)
            Os.chmod(envScript.absolutePath, 493 /* 0755 */)

            // Sync to /root/.bashrc & /root/.profile
            val rootBashrc = File(rootHomeDir, ".bashrc")
            val bashrcContent = """
                # Alpine Linux Root Shell Environment
                unset PROOT_NO_SECCOMP
                if [ -f "/opt/statfix/statx_shim.so" ]; then
                    export LD_PRELOAD=/opt/statfix/statx_shim.so
                fi
                export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.npm-global/bin:${'$'}PATH"
                export npm_config_prefix="/usr/local"
                export NODE_PATH="/usr/local/lib/node_modules:/usr/lib/node_modules"
                export LANG="en_US.UTF-8"
                export TERM="xterm-256color"
                export COLORTERM="truecolor"
                export PI_SKIP_VERSION_CHECK=1
                if [ -f "/opt/statfix/term_size" ]; then
                    read -r c r < /opt/statfix/term_size 2>/dev/null
                    [ -n "${'$'}c" ] && export COLUMNS="${'$'}c"
                    [ -n "${'$'}r" ] && export LINES="${'$'}r"
                fi
                if [ -f "/opt/statfix/tty_shim.cjs" ]; then
                    export NODE_OPTIONS="--require /opt/statfix/tty_shim.cjs ${'$'}{NODE_OPTIONS}"
                fi
                export PS1='\[\033[01;32m\]root@localhost\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]# '
                alias ls='ls --color=auto'
                alias ll='ls -la --color=auto'
                alias grep='grep --color=auto'
                alias pi='/usr/local/bin/pi'
                alias claude='/usr/local/bin/claude'
            """.trimIndent() + "\n"
            rootBashrc.writeText(bashrcContent)

            val rootProfile = File(rootHomeDir, ".profile")
            rootProfile.writeText(bashrcContent)

            // Force npm prefix and default to fast domestic registry
            val etcNpmrc = File(rootfsDir, "etc/npmrc")
            etcNpmrc.writeText("prefix=/usr/local\nregistry=https://registry.npmmirror.com/\n")
            val rootNpmrc = File(rootHomeDir, ".npmrc")
            rootNpmrc.writeText("prefix=/usr/local\nregistry=https://registry.npmmirror.com/\n")

            // Ensure robust DNS resolution (/etc/resolv.conf) and local host mapping
            val resolvFile = File(rootfsDir, "etc/resolv.conf")
            resolvFile.writeText("nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            try { Os.chmod(resolvFile.absolutePath, 420 /* 0644 */) } catch (_: Exception) {}

            val hostsFile = File(rootfsDir, "etc/hosts")
            if (!hostsFile.exists() || hostsFile.length() < 10) {
                hostsFile.writeText("127.0.0.1 localhost\n::1 localhost\n")
                try { Os.chmod(hostsFile.absolutePath, 420) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 10. Ensure /dev/shm symlinks to /tmp so Python multiprocessing / POSIX sem_open does not fail with ENOSYS
        try {
            val devDir = File(rootfsDir, "dev").apply { mkdirs() }
            val shmDir = File(devDir, "shm")
            if (!shmDir.exists()) {
                try {
                    Os.symlink("/tmp", shmDir.absolutePath)
                } catch (_: Exception) {
                    shmDir.mkdirs()
                }
            }
        } catch (_: Exception) {}

        // 11. statx shim multi-tier self-healing and /etc/ld.so.preload integration
        try {
            statfixDir.mkdirs()
            val shimSo = File(statfixDir, "statx_shim.so")
            val shimC = File(statfixDir, "statx_shim.c")
            val buildSh = File(statfixDir, "build.sh")

            // Always write the latest C source code and compile script
            shimC.writeText(STATX_SHIM_C_SOURCE)
            buildSh.writeText(STATX_BUILD_SH_SOURCE)
            buildSh.setExecutable(true, false)
            try { Os.chmod(buildSh.absolutePath, 493 /* 0755 */) } catch (_: Exception) {}

            val internalBackup = File(context.filesDir, "statx_shim.so")
            val externalDocsDir = File("/storage/emulated/0/Documents/PiAgent")
            val externalBackup = File(externalDocsDir, "statx_shim.so")
            val externalSource = File(externalDocsDir, "statx_shim.c")

            // Tier 1: Target .so exists in container (> 1KB) -> back it up to internal and external storage
            if (shimSo.exists() && shimSo.length() > 1000) {
                shimSo.setExecutable(true, false)
                try { Os.chmod(shimSo.absolutePath, 493 /* 0755 */) } catch (_: Exception) {}
                if (!internalBackup.exists() || internalBackup.length() != shimSo.length()) {
                    try { shimSo.copyTo(internalBackup, overwrite = true) } catch (_: Exception) {}
                }
                try {
                    if (!externalDocsDir.exists()) externalDocsDir.mkdirs()
                    if (externalDocsDir.canWrite()) {
                        if (!externalBackup.exists() || externalBackup.length() != shimSo.length()) {
                            shimSo.copyTo(externalBackup, overwrite = true)
                        }
                        if (!externalSource.exists() || externalSource.length() != shimC.length()) {
                            shimC.copyTo(externalSource, overwrite = true)
                        }
                    }
                } catch (_: Exception) {}
            } else {
                // Tier 2: Target .so missing -> restore from internal backup
                var restored = false
                if (internalBackup.exists() && internalBackup.length() > 1000) {
                    try {
                        internalBackup.copyTo(shimSo, overwrite = true)
                        shimSo.setExecutable(true, false)
                        Os.chmod(shimSo.absolutePath, 493)
                        restored = true
                        Log.i(TAG, "Restored statx_shim.so from internal app backup (${shimSo.length()} bytes)")
                    } catch (_: Exception) {}
                }
                // Tier 3: Restore from external Documents/PiAgent backup
                if (!restored && externalBackup.exists() && externalBackup.length() > 1000) {
                    try {
                        externalBackup.copyTo(shimSo, overwrite = true)
                        shimSo.setExecutable(true, false)
                        Os.chmod(shimSo.absolutePath, 493)
                        externalBackup.copyTo(internalBackup, overwrite = true)
                        restored = true
                        Log.i(TAG, "Restored statx_shim.so from external storage backup (${shimSo.length()} bytes)")
                    } catch (_: Exception) {}
                }
            }

            // If shimSo is present, configure musl's /etc/ld.so.preload & /etc/profile.d
            if (shimSo.exists() && shimSo.length() > 1000) {
                val preloadFile = File(rootfsDir, "etc/ld.so.preload")
                preloadFile.writeText("/opt/statfix/statx_shim.so\n")
                try { Os.chmod(preloadFile.absolutePath, 420 /* 0644 */) } catch (_: Exception) {}

                val profileDir = File(rootfsDir, "etc/profile.d").apply { mkdirs() }
                val shimProfile = File(profileDir, "00-statfix.sh")
                shimProfile.writeText("#!/bin/sh\nunset PROOT_NO_SECCOMP\nexport LD_PRELOAD=/opt/statfix/statx_shim.so\n")
                shimProfile.setExecutable(true, false)
                try { Os.chmod(shimProfile.absolutePath, 493 /* 0755 */) } catch (_: Exception) {}
            }

            // Export restore_statfix.sh into container /root for manual utility
            val rootRestoreScript = File(rootHomeDir, "restore_statfix.sh")
            rootRestoreScript.writeText("""
                #!/bin/sh
                echo "=== Pi Remote 兼容层维护脚本 ==="
                sh /opt/statfix/build.sh
                echo "LD Preload 状态:"
                cat /etc/ld.so.preload 2>/dev/null || echo "未配置"
                apk upgrade --available busybox 2>/dev/null || true
                echo "修复完成！"
            """.trimIndent() + "\n")
            rootRestoreScript.setExecutable(true, false)
            try { Os.chmod(rootRestoreScript.absolutePath, 493) } catch (_: Exception) {}

            // Export tty_shim.cjs into /opt/statfix so Node.js detects interactive TTY in PRoot pipe
            val ttyShimFile = File(statfixDir, "tty_shim.cjs")
            val ttyShimSource = """
                // [Pi Remote] Dynamic TTY emulator & dimension synchronizer for PRoot anonymous pipes
                const fs = require('fs');

                let cachedCols = 48;
                let cachedRows = 24;
                let lastCheckTime = 0;

                function refreshTermSize(force) {
                    const now = Date.now();
                    if (!force && (now - lastCheckTime < 200)) {
                        return;
                    }
                    lastCheckTime = now;
                    try {
                        if (fs.existsSync('/opt/statfix/term_size')) {
                            const data = fs.readFileSync('/opt/statfix/term_size', 'utf8').trim().split(/\s+/);
                            const c = parseInt(data[0], 10);
                            const r = parseInt(data[1], 10);
                            if (!isNaN(c) && c >= 10) cachedCols = c;
                            if (!isNaN(r) && r >= 3) cachedRows = r;
                            return;
                        }
                    } catch (_) {}
                    const envC = parseInt(process.env.COLUMNS, 10);
                    const envR = parseInt(process.env.LINES, 10);
                    if (!isNaN(envC) && envC >= 10) cachedCols = envC;
                    if (!isNaN(envR) && envR >= 3) cachedRows = envR;
                }

                refreshTermSize(true);

                try {
                    if (fs.existsSync('/opt/statfix/term_size')) {
                        fs.watchFile('/opt/statfix/term_size', { interval: 250 }, () => {
                            const oldCols = cachedCols;
                            const oldRows = cachedRows;
                            refreshTermSize(true);
                            if (oldCols !== cachedCols || oldRows !== cachedRows) {
                                if (process.stdout && typeof process.stdout.emit === 'function') {
                                    process.stdout.emit('resize');
                                }
                                if (process.stderr && typeof process.stderr.emit === 'function') {
                                    process.stderr.emit('resize');
                                }
                            }
                        });
                    }
                } catch (_) {}

                if (!process.stdin.isTTY) {
                    process.stdin.isTTY = true;
                    if (!process.stdin.setRawMode) {
                        process.stdin.setRawMode = function(mode) {
                            this.isRaw = !!mode;
                            return this;
                        };
                    }
                }

                if (!process.stdout.isTTY) {
                    process.stdout.isTTY = true;
                }

                try {
                    Object.defineProperty(process.stdout, 'columns', {
                        get: function() {
                            refreshTermSize(false);
                            return cachedCols;
                        },
                        set: function(v) {
                            if (typeof v === 'number' && v > 0) cachedCols = v;
                        },
                        configurable: true,
                        enumerable: true
                    });

                    Object.defineProperty(process.stdout, 'rows', {
                        get: function() {
                            refreshTermSize(false);
                            return cachedRows;
                        },
                        set: function(v) {
                            if (typeof v === 'number' && v > 0) cachedRows = v;
                        },
                        configurable: true,
                        enumerable: true
                    });
                } catch (_) {
                    process.stdout.columns = cachedCols;
                    process.stdout.rows = cachedRows;
                }

                process.stdout.getWindowSize = function() {
                    refreshTermSize(false);
                    return [cachedCols, cachedRows];
                };

                if (!process.stderr.isTTY) {
                    process.stderr.isTTY = true;
                }

                try {
                    Object.defineProperty(process.stderr, 'columns', {
                        get: function() {
                            refreshTermSize(false);
                            return cachedCols;
                        },
                        set: function(v) {
                            if (typeof v === 'number' && v > 0) cachedCols = v;
                        },
                        configurable: true,
                        enumerable: true
                    });

                    Object.defineProperty(process.stderr, 'rows', {
                        get: function() {
                            refreshTermSize(false);
                            return cachedRows;
                        },
                        set: function(v) {
                            if (typeof v === 'number' && v > 0) cachedRows = v;
                        },
                        configurable: true,
                        enumerable: true
                    });
                } catch (_) {
                    process.stderr.columns = cachedCols;
                    process.stderr.rows = cachedRows;
                }

                process.stderr.getWindowSize = function() {
                    refreshTermSize(false);
                    return [cachedCols, cachedRows];
                };
            """.trimIndent() + "\n"
            ttyShimFile.writeText(ttyShimSource)
            ttyShimFile.setReadable(true, false)
            try { Os.chmod(ttyShimFile.absolutePath, 420 /* 0644 */) } catch (_: Exception) {}
        } catch (_: Exception) {}

        // 12. Configure git safe.directory = * for root and user so no fatal: detected dubious ownership errors
        try {
            val gitConfigContent = "[safe]\n\tdirectory = *\n"
            val rootGitConfig = File(rootHomeDir, ".gitconfig")
            if (!rootGitConfig.exists() || !rootGitConfig.readText().contains("safe")) {
                rootGitConfig.writeText(gitConfigContent)
            }
            val userGitConfig = File(homeDir, ".gitconfig")
            if (!userGitConfig.exists() || !userGitConfig.readText().contains("safe")) {
                userGitConfig.writeText(gitConfigContent)
            }
        } catch (_: Exception) {}

        // 13. Auto-symlink historical/orphaned npm binaries into /usr/local/bin
        try {
            val usrLocalBin = File(rootfsDir, "usr/local/bin").apply { mkdirs() }
            val usrLocalLibModules = File(rootfsDir, "usr/local/lib/node_modules").apply { mkdirs() }

            val candidateBinDirs = mutableListOf<File>()
            candidateBinDirs.add(File(rootfsDir, "root/.npm-global/bin"))
            candidateBinDirs.add(File(rootfsDir, "home/user/.npm-global/bin"))
            candidateBinDirs.add(File(rootfsDir, "home/.npm-global/bin"))
            candidateBinDirs.add(npmBinDir)

            val rootfsDataDir = File(rootfsDir, "data")
            if (rootfsDataDir.exists()) {
                rootfsDataDir.walkTopDown().maxDepth(8).filter { it.isDirectory && it.name == "bin" && it.parentFile?.name == ".npm-global" }.forEach { dir ->
                    candidateBinDirs.add(dir)
                }
            }

            for (srcDir in candidateBinDirs) {
                if (srcDir.exists() && srcDir.isDirectory) {
                    srcDir.listFiles()?.forEach { binFile ->
                        if (binFile.isFile && binFile.name != "pkg" && binFile.name != "pi" && binFile.name != "claude") {
                            val target = File(usrLocalBin, binFile.name)
                            if (!target.exists() || target.length() == 0L) {
                                try {
                                    binFile.copyTo(target, overwrite = true)
                                    target.setExecutable(true, false)
                                    Os.chmod(target.absolutePath, 493 /* 0755 */)
                                    Log.i(TAG, "Auto-linked orphan binary ${binFile.name} -> /usr/local/bin/${binFile.name}")
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            // Rescue orphaned node_modules into /usr/local/lib/node_modules
            val candidateModuleDirs = mutableListOf<File>()
            candidateModuleDirs.add(File(rootfsDir, "root/.npm-global/lib/node_modules"))
            candidateModuleDirs.add(File(rootfsDir, "home/user/.npm-global/lib/node_modules"))
            candidateModuleDirs.add(File(npmGlobalDir, "lib/node_modules"))
            if (rootfsDataDir.exists()) {
                rootfsDataDir.walkTopDown().maxDepth(8).filter { it.isDirectory && it.name == "node_modules" && it.parentFile?.name == "lib" }.forEach { dir ->
                    candidateModuleDirs.add(dir)
                }
            }
            for (modDir in candidateModuleDirs) {
                if (modDir.exists() && modDir.isDirectory) {
                    modDir.listFiles()?.forEach { pkgDir ->
                        if (pkgDir.isDirectory) {
                            val targetMod = File(usrLocalLibModules, pkgDir.name)
                            if (!targetMod.exists()) {
                                try {
                                    if (pkgDir.name.startsWith("@")) {
                                        targetMod.mkdirs()
                                        pkgDir.listFiles()?.forEach { subPkg ->
                                            val subTarget = File(targetMod, subPkg.name)
                                            if (!subTarget.exists()) {
                                                subPkg.copyRecursively(subTarget, overwrite = true)
                                            }
                                        }
                                    } else {
                                        pkgDir.copyRecursively(targetMod, overwrite = true)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            // 14. Zero-Config Smart Self-Healing Launcher for Pi Agent (`pi`)
            val piExec = File(usrLocalBin, "pi")
            val piBundleCli = File(usrLocalLibModules, "@earendil-works/pi-coding-agent/dist/bundle/cli.js")
            val piAltCli = File(usrLocalLibModules, "@earendil-works/pi-coding-agent/dist/cli.js")
            val realCliFile = if (piBundleCli.exists()) piBundleCli else if (piAltCli.exists()) piAltCli else null

            // Clean any corrupted cli.js from previous shell redirection
            if (realCliFile != null && isCorruptedCliJs(realCliFile)) {
                try {
                    File(usrLocalLibModules, "@earendil-works/pi-coding-agent").deleteRecursively()
                    Log.w(TAG, "Purged corrupted @earendil-works/pi-coding-agent module")
                } catch (_: Exception) {}
            }

            val piLauncherScript = """
                #!/bin/sh
                # [Pi Remote] Smart Self-Healing Launcher for Pi Coding Agent
                unset PROOT_NO_SECCOMP
                if [ -f "/opt/statfix/statx_shim.so" ]; then
                    export LD_PRELOAD=/opt/statfix/statx_shim.so
                fi
                export npm_config_prefix="/usr/local"
                export NODE_PATH="/usr/local/lib/node_modules:/usr/lib/node_modules"
                export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH"
                export TERM="xterm-256color"
                export COLORTERM="truecolor"
                export PI_SKIP_VERSION_CHECK=1
                if [ -f "/opt/statfix/term_size" ]; then
                    read -r c r < /opt/statfix/term_size 2>/dev/null
                    [ -n "${'$'}c" ] && export COLUMNS="${'$'}c"
                    [ -n "${'$'}r" ] && export LINES="${'$'}r"
                fi
                if [ -f "/opt/statfix/tty_shim.cjs" ]; then
                    export NODE_OPTIONS="--require /opt/statfix/tty_shim.cjs ${'$'}{NODE_OPTIONS}"
                fi
                
                BUNDLE_CLI="/usr/local/lib/node_modules/@earendil-works/pi-coding-agent/dist/bundle/cli.js"
                ALT_CLI="/usr/local/lib/node_modules/@earendil-works/pi-coding-agent/dist/cli.js"
                
                NEED_INSTALL=0
                if [ ! -f "${'$'}BUNDLE_CLI" ] && [ ! -f "${'$'}ALT_CLI" ]; then
                    NEED_INSTALL=1
                elif [ -f "${'$'}BUNDLE_CLI" ] && grep -q -E "statx_shim|Auto-Install Stub|/bin/sh" "${'$'}BUNDLE_CLI" 2>/dev/null; then
                    NEED_INSTALL=1
                    rm -rf /usr/local/lib/node_modules/@earendil-works/pi-coding-agent
                fi
                
                if [ "${'$'}NEED_INSTALL" = "1" ]; then
                    printf "\033[1;36m[Pi Remote] 正在全自动部署并就绪 Pi Agent (@earendil-works/pi-coding-agent)...\033[0m\n"
                    printf "\033[36m-> 正在从国内高速镜像源下载软件包...\033[0m\n"
                    if ! npm install -g --force --registry=https://registry.npmmirror.com @earendil-works/pi-coding-agent; then
                        printf "\033[33m-> 正在尝试官方全球源备用节点...\033[0m\n"
                        npm install -g --force --registry=https://registry.npmjs.org @earendil-works/pi-coding-agent || true
                    fi
                    if [ -f "/opt/statfix/pi_launcher.sh" ]; then
                        cp -f /opt/statfix/pi_launcher.sh /usr/local/bin/pi
                        chmod 755 /usr/local/bin/pi
                    fi
                fi
                
                TARGET_CLI=""
                if [ -f "${'$'}BUNDLE_CLI" ]; then
                    TARGET_CLI="${'$'}BUNDLE_CLI"
                elif [ -f "${'$'}ALT_CLI" ]; then
                    TARGET_CLI="${'$'}ALT_CLI"
                fi
                
                if [ -n "${'$'}TARGET_CLI" ]; then
                    chmod 755 "${'$'}TARGET_CLI" 2>/dev/null || true
                    node "${'$'}TARGET_CLI" "${'$'}@"
                    EXIT_CODE=${'$'}?
                    echo "__PIREMOTE_DONE__:${'$'}EXIT_CODE:${'$'}(pwd)"
                    exit ${'$'}EXIT_CODE
                else
                    printf "\033[1;31m[Pi Remote] 启动失败：未找到可执行入口，请确认已联网。\033[0m\n"
                    echo "__PIREMOTE_DONE__:1:${'$'}(pwd)"
                    exit 1
                fi
            """.trimIndent() + "\n"

            val piMaster = File(statfixDir, "pi_launcher.sh")
            piMaster.writeText(piLauncherScript)
            piMaster.setExecutable(true, false)
            try { Os.chmod(piMaster.absolutePath, 493) } catch (_: Exception) {}

            piExec.delete()
            piExec.writeText(piLauncherScript)
            piExec.setExecutable(true, false)
            try { Os.chmod(piExec.absolutePath, 493) } catch (_: Exception) {}

            val usrBinPi = File(rootfsDir, "usr/bin/pi")
            try {
                usrBinPi.delete()
                Os.symlink("/usr/local/bin/pi", usrBinPi.absolutePath)
            } catch (_: Exception) {}

            // 15. Zero-Config Smart Self-Healing Launcher for Claude Code (`claude`)
            val claudeExec = File(usrLocalBin, "claude")
            val claudeLauncherScript = """
                #!/bin/sh
                # [Pi Remote] Smart Self-Healing Launcher for Claude Code
                unset PROOT_NO_SECCOMP
                if [ -f "/opt/statfix/statx_shim.so" ]; then
                    export LD_PRELOAD=/opt/statfix/statx_shim.so
                fi
                export npm_config_prefix="/usr/local"
                export NODE_PATH="/usr/local/lib/node_modules:/usr/lib/node_modules"
                export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH"
                export TERM="xterm-256color"
                export COLORTERM="truecolor"
                export PI_SKIP_VERSION_CHECK=1
                if [ -f "/opt/statfix/term_size" ]; then
                    read -r c r < /opt/statfix/term_size 2>/dev/null
                    [ -n "${'$'}c" ] && export COLUMNS="${'$'}c"
                    [ -n "${'$'}r" ] && export LINES="${'$'}r"
                fi
                if [ -f "/opt/statfix/tty_shim.cjs" ]; then
                    export NODE_OPTIONS="--require /opt/statfix/tty_shim.cjs ${'$'}{NODE_OPTIONS}"
                fi
                
                CLAUDE_CLI="/usr/local/lib/node_modules/@anthropic-ai/claude-code/cli.js"
                
                if [ ! -f "${'$'}CLAUDE_CLI" ]; then
                    printf "\033[1;36m[Pi Remote] 正在全自动部署并就绪 Claude Code (@anthropic-ai/claude-code)...\033[0m\n"
                    printf "\033[36m-> 正在从国内高速镜像源下载软件包...\033[0m\n"
                    if ! npm install -g --force --registry=https://registry.npmmirror.com @anthropic-ai/claude-code; then
                        npm install -g --force --registry=https://registry.npmjs.org @anthropic-ai/claude-code || true
                    fi
                    if [ -f "/opt/statfix/claude_launcher.sh" ]; then
                        cp -f /opt/statfix/claude_launcher.sh /usr/local/bin/claude
                        chmod 755 /usr/local/bin/claude
                    fi
                fi
                
                if [ -f "${'$'}CLAUDE_CLI" ]; then
                    chmod 755 "${'$'}CLAUDE_CLI" 2>/dev/null || true
                    node "${'$'}CLAUDE_CLI" "${'$'}@"
                    EXIT_CODE=${'$'}?
                    echo "__PIREMOTE_DONE__:${'$'}EXIT_CODE:${'$'}(pwd)"
                    exit ${'$'}EXIT_CODE
                else
                    printf "\033[1;31m[Pi Remote] 启动失败：未找到 Claude Code 入口，请确认已联网。\033[0m\n"
                    echo "__PIREMOTE_DONE__:1:${'$'}(pwd)"
                    exit 1
                fi
            """.trimIndent() + "\n"

            val claudeMaster = File(statfixDir, "claude_launcher.sh")
            claudeMaster.writeText(claudeLauncherScript)
            claudeMaster.setExecutable(true, false)
            try { Os.chmod(claudeMaster.absolutePath, 493) } catch (_: Exception) {}

            claudeExec.delete()
            claudeExec.writeText(claudeLauncherScript)
            claudeExec.setExecutable(true, false)
            try { Os.chmod(claudeExec.absolutePath, 493) } catch (_: Exception) {}

            val usrBinClaude = File(rootfsDir, "usr/bin/claude")
            try {
                usrBinClaude.delete()
                Os.symlink("/usr/local/bin/claude", usrBinClaude.absolutePath)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /**
     * Builds the unified executable PATH variable, prioritizing nativeLibraryDir, rootfs, and npm-global.
     */
    fun buildPath(): String {
        val parts = mutableListOf<String>()

        // 1. Native library directory (extracted libbash.so, libbusybox.so, libnode.so with SELinux execute permission)
        if (nativeDir.exists()) parts.add(nativeDir.absolutePath)

        // 2. User npm global binaries (~/.npm-global/bin) & local bin
        if (npmBinDir.exists()) parts.add(npmBinDir.absolutePath)
        val localBin = File(context.filesDir, "bin")
        if (localBin.exists()) parts.add(localBin.absolutePath)

        // 3. Embedded rootfs bin & usr/local/bin & usr/bin
        if (usrLocalBinDir.exists()) parts.add(usrLocalBinDir.absolutePath)
        if (usrBinDir.exists()) parts.add(usrBinDir.absolutePath)
        val binDir = File(rootfsDir, "bin")
        if (binDir.exists()) parts.add(binDir.absolutePath)
        val sbinDir = File(rootfsDir, "sbin")
        if (sbinDir.exists()) parts.add(sbinDir.absolutePath)
        val usrSbinDir = File(rootfsDir, "usr/sbin")
        if (usrSbinDir.exists()) parts.add(usrSbinDir.absolutePath)

        // 4. Termux integration fallback if installed on device
        val termuxBin = File("/data/data/com.termux/files/usr/bin")
        if (termuxBin.exists() && termuxBin.canRead()) {
            parts.add(termuxBin.absolutePath)
        }

        // 5. Standard Android paths
        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        parts.add(sysPath)

        return parts.joinToString(":")
    }

    /**
     * Injects standard POSIX, Bash & Node.js environment variables into a target environment map.
     */
    fun configureEnvironment(env: MutableMap<String, String>, workingDir: File? = null) {
        val isAlpine = isAlpineRootfsInstalled()
        val targetHome = if (isAlpine) rootHomeDir.canonicalPath else (workingDir?.canonicalPath ?: context.filesDir.canonicalPath)
        val currentPwd = (workingDir ?: context.filesDir).canonicalPath

        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["LANG"] = "en_US.UTF-8"
        env["USER"] = if (isAlpine) "root" else "android"
        env["HOSTNAME"] = "localhost"
        env["PWD"] = currentPwd
        env["TMPDIR"] = tmpDir.canonicalPath
        env["PROOT_TMP_DIR"] = tmpDir.canonicalPath
        env["NATIVE_LIB_DIR"] = nativeDir.canonicalPath
        env["ROOTFS"] = rootfsDir.canonicalPath
        env["ALPINE_ROOT"] = rootfsDir.canonicalPath
        env["PI_SKIP_VERSION_CHECK"] = "1"

        val termSizeFile = File(rootfsDir, "opt/statfix/term_size")
        if (termSizeFile.exists()) {
            try {
                val parts = termSizeFile.readText().trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    env["COLUMNS"] = parts[0]
                    env["LINES"] = parts[1]
                }
            } catch (_: Exception) {}
        }
        if (!env.containsKey("COLUMNS")) {
            val dm = context.resources.displayMetrics
            val approxCellWidth = 12f * dm.scaledDensity * 0.605f
            val approxCols = (dm.widthPixels / approxCellWidth).toInt().coerceIn(35, 120)
            val approxRows = ((dm.heightPixels * 0.65f) / (12f * dm.scaledDensity * 1.25f)).toInt().coerceIn(15, 60)
            env["COLUMNS"] = approxCols.toString()
            env["LINES"] = approxRows.toString()
        }
        val ttyShim = File(rootfsDir, "opt/statfix/tty_shim.cjs")
        if (ttyShim.exists()) {
            val existing = env["NODE_OPTIONS"]
            env["NODE_OPTIONS"] = if (!existing.isNullOrBlank()) "--require /opt/statfix/tty_shim.cjs $existing" else "--require /opt/statfix/tty_shim.cjs"
        }

        if (isAlpine) {
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.npm-global/bin"
            env["HOME"] = "/root"
            env["npm_config_prefix"] = "/usr/local"
            env["NODE_PATH"] = "/usr/local/lib/node_modules:/usr/lib/node_modules"
        } else {
            env["PATH"] = buildPath()
            env["HOME"] = targetHome
            env["npm_config_prefix"] = npmGlobalDir.canonicalPath
            val nodeModulesList = mutableListOf<String>()
            val npmGlobalModules = File(npmGlobalDir, "lib/node_modules")
            if (npmGlobalModules.exists()) nodeModulesList.add(npmGlobalModules.absolutePath)
            val rootfsModules = File(rootfsDir, "usr/lib/node_modules")
            if (rootfsModules.exists()) nodeModulesList.add(rootfsModules.absolutePath)
            if (nodeModulesList.isNotEmpty()) {
                env["NODE_PATH"] = nodeModulesList.joinToString(":")
            }
        }

        // Do NOT set PROOT_NO_SECCOMP=1. Disabling seccomp forces PRoot into pure ptrace
        // emulation where system calls like fchdir() are unimplemented (ENOSYS), causing
        // apk post-install scripts and triggers (bash, busybox, etc.) to fail with error 127.
        // Seccomp mode 2 passes fchdir through to the kernel directly and runs reliably.
        env.remove("PROOT_NO_SECCOMP")
        env.remove("LD_PRELOAD")

        if (isNativeNodeBundled()) {
            env["NODE_EXE"] = nativeNodeFile.canonicalPath
        }
        if (isNativeBashBundled()) {
            env["SHELL"] = nativeBashFile.canonicalPath
        } else {
            env["SHELL"] = "/system/bin/sh"
        }
    }

    /**
     * Inspects the environment to see if Bash, BusyBox, Alpine Linux, PRoot, Node.js, and CLI tools are available.
     */
    suspend fun detectEnvironment() = withContext(Dispatchers.IO) {
        val path = buildPath()

        // 0. Check Alpine Linux minirootfs & PRoot sandbox first so subsequent probes know if Alpine is available
        val alpineRelease = File(rootfsDir, "etc/alpine-release")
        val hasAlpine = isAlpineRootfsInstalled()
        if (hasAlpine) {
            try {
                ensureRootfsStructureAndPermissions()
            } catch (_: Exception) {}
        }
        val alpineVer = if (hasAlpine && alpineRelease.exists()) {
            try { alpineRelease.readText().trim() } catch (_: Exception) { "v3.20" }
        } else null

        val hasProot = isNativeProotBundled()
        val prootVer = if (hasProot) {
            probeCommand("proot --version", path)?.lineSequence()?.firstOrNull()?.trim() ?: "v5.1.0"
        } else null

        // 1. Check Native Bash & BusyBox
        val bashVer = if (hasAlpine) {
            val alpineBash = File(rootfsDir, "bin/bash").takeIf { it.exists() } ?: File(rootfsDir, "usr/bin/bash").takeIf { it.exists() }
            if (alpineBash != null) {
                probeCommandInAlpine("bash --version") ?: "GNU Bash (Alpine)"
            } else {
                probeCommand("bash --version", path)
            }
        } else {
            probeCommand("bash --version", path)
        }
        val hasBash = isNativeBashBundled() || !bashVer.isNullOrBlank()

        val busyBoxVer = if (hasAlpine) {
            val alpineBusyBox = File(rootfsDir, "bin/busybox")
            if (alpineBusyBox.exists()) {
                probeCommandInAlpine("busybox") ?: "BusyBox (Alpine)"
            } else {
                probeCommand("busybox", path)
            }
        } else {
            probeCommand("busybox", path)
        }
        val hasBusyBox = isNativeBusyboxBundled() || !busyBoxVer.isNullOrBlank()

        // 2. Check Node.js
        val alpineNode = File(rootfsDir, "usr/bin/node")
        val nodeVer = if (hasAlpine && alpineNode.exists()) {
            probeCommandInAlpine("node -v") ?: "Node.js (Alpine)"
        } else {
            probeCommand("node -v", path)
        }
        val hasNode = isNativeNodeBundled() || !nodeVer.isNullOrBlank()

        // 6. Check other toolchains
        val alpineNpm = File(rootfsDir, "usr/bin/npm")
        val npmVer = if (hasNode) {
            if (hasAlpine && alpineNpm.exists()) {
                probeCommandInAlpine("npm -v") ?: "就绪 (Alpine)"
            } else {
                probeCommand("npm -v", path)
            }
        } else null

        val alpineGit = File(rootfsDir, "usr/bin/git")
        val gitVer = if (hasAlpine && alpineGit.exists()) {
            probeCommandInAlpine("git --version") ?: "git (Alpine)"
        } else {
            probeCommand("git --version", path)
        }

        val alpinePy = File(rootfsDir, "usr/bin/python3").takeIf { it.exists() } ?: File(rootfsDir, "usr/bin/python").takeIf { it.exists() }
        val pyVer = if (hasAlpine && alpinePy != null) {
            probeCommandInAlpine("python3 --version || python --version") ?: "Python 3 (Alpine)"
        } else {
            probeCommand("python3 --version || python --version", path)
        }

        val piVer = if (hasNode) {
            if (hasAlpine) {
                val alpinePi = File(rootfsDir, "usr/local/bin/pi").takeIf { it.exists() }
                    ?: File(rootfsDir, "usr/bin/pi").takeIf { it.exists() }
                val isRealPi = alpinePi != null && !isAutoInstallStub(alpinePi)

                if (isRealPi) {
                    val raw = probeCommandInAlpine("pi --version")
                    if (raw != null && !raw.contains("Auto-Install") && !raw.contains("not found")) {
                        raw.lineSequence().firstOrNull()?.trim()
                    } else "已安装 (Alpine)"
                } else null
            } else {
                probeCommand("pi --version", path)
            }
        } else null

        val claudeVer = if (hasNode) {
            if (hasAlpine) {
                val alpineClaude = File(rootfsDir, "usr/local/bin/claude").takeIf { it.exists() }
                    ?: File(rootfsDir, "usr/bin/claude").takeIf { it.exists() }
                val isRealClaude = alpineClaude != null && !isAutoInstallStub(alpineClaude)

                if (isRealClaude) {
                    val raw = probeCommandInAlpine("claude --version")
                    if (raw != null && !raw.contains("Auto-Install") && !raw.contains("not found")) {
                        raw.lineSequence().firstOrNull()?.trim()
                    } else "已安装 (Alpine)"
                } else null
            } else {
                probeCommand("claude --version", path)
            }
        } else null

        val envType = when {
            hasAlpine && hasProot -> "Alpine Linux 子系统 (PRoot 沙箱)"
            hasAlpine -> "Alpine Linux 根文件系统"
            hasBash -> "内置 GNU Bash 完整环境"
            hasNode -> "内置独立运行时 (libnode.so)"
            else -> "Android 原生免装环境 (精简 Shell)"
        }

        val cleanBashVer = bashVer?.let { raw ->
            val match = Regex("""version\s+([0-9]+(?:\.[0-9]+)+)""").find(raw)
            if (match != null) {
                "v${match.groupValues[1]}"
            } else {
                raw.lineSequence().firstOrNull()
                    ?.replace("GNU bash, version ", "v")
                    ?.replace("GNU bash, ", "")
                    ?.substringBefore("(")
                    ?.trim()
            }
        }

        val cleanBusyBoxVer = busyBoxVer?.let { raw ->
            val match = Regex("""v([0-9]+(?:\.[0-9]+)+)""").find(raw)
            if (match != null) {
                "v${match.groupValues[1]}"
            } else {
                raw.lineSequence().firstOrNull()
                    ?.replace("multi-call binary.", "")
                    ?.substringBefore("(")
                    ?.replace("BusyBox", "")
                    ?.trim()
                    ?.let { if (it.isNotBlank()) "v${it.removePrefix("v")}" else null }
            }
        }

        val cleanPyVer = pyVer?.let { raw ->
            val match = Regex("""([0-9]+(?:\.[0-9]+)+)""").find(raw)
            if (match != null) "v${match.groupValues[1]}" else raw.trim()
        }

        _toolchain.value = ToolchainInfo(
            hasNode = hasNode,
            nodeVersion = nodeVer,
            hasNpm = !npmVer.isNullOrBlank(),
            npmVersion = npmVer,
            hasGit = !gitVer.isNullOrBlank(),
            gitVersion = gitVer?.replace("git version ", "")?.trim(),
            hasBash = hasBash,
            bashVersion = cleanBashVer,
            hasBusyBox = hasBusyBox,
            busyBoxVersion = cleanBusyBoxVer,
            hasAlpine = hasAlpine,
            alpineVersion = alpineVer,
            hasProot = hasProot,
            prootVersion = prootVer,
            hasPython = !pyVer.isNullOrBlank(),
            pythonVersion = cleanPyVer,
            hasPiAgent = !piVer.isNullOrBlank(),
            piVersion = piVer,
            hasClaudeCode = !claudeVer.isNullOrBlank(),
            claudeVersion = claudeVer,
            environmentType = envType,
        )

        _status.value = when {
            hasAlpine -> LinuxEnvironmentStatus.READY
            hasBash || hasBusyBox -> LinuxEnvironmentStatus.BASH_READY
            hasNode -> LinuxEnvironmentStatus.READY
            else -> LinuxEnvironmentStatus.NOT_INSTALLED
        }

        Log.i(TAG, "Environment detected: status=${_status.value}, bash=$bashVer, alpine=$alpineVer, node=$nodeVer")
    }

    /**
     * Downloads and deploys the complete Alpine Linux aarch64 minirootfs (~3.5MB).
     * Extracts rootfs, configures /etc/resolv.conf, apk mirrors, and default shell environment.
     */
    fun deployAlpineRootfs(scope: CoroutineScope, onComplete: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            _status.value = LinuxEnvironmentStatus.INITIALIZING
            val tarGzFile = File(context.cacheDir, "alpine-minirootfs-aarch64.tar.gz")

            val downloadMirrors = listOf(
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                "https://mirrors.ustc.edu.cn/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                "https://mirrors.aliyun.com/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"
            )

            var downloadSuccess = false
            var successfulMirrorUrl = ""
            for (urlStr in downloadMirrors) {
                try {
                    _installProgress.value = 0.05f to "连接镜像源 (${URL(urlStr).host})..."
                    val url = URL(urlStr)
                    val isGlobal = urlStr.contains("dl-cdn.alpinelinux.org")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = if (isGlobal) 7000 else 12000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "PiRemote-Android/1.0")
                    }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val totalBytes = conn.contentLength.toLong()
                        var downloaded = 0L

                        _installProgress.value = 0.1f to "正在下载 Alpine Linux 根文件系统 (~3.5MB)..."
                        BufferedInputStream(conn.inputStream).use { bis ->
                            FileOutputStream(tarGzFile).use { fos ->
                                val buffer = ByteArray(32768)
                                var read: Int
                                while (bis.read(buffer).also { read = it } != -1) {
                                    fos.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0) {
                                        val pct = 0.1f + 0.55f * (downloaded.toFloat() / totalBytes)
                                        val mb = String.format("%.1f", downloaded / 1048576f)
                                        val totalMb = String.format("%.1f", totalBytes / 1048576f)
                                        _installProgress.value = pct to "正在下载 Alpine Linux ($mb MB / $totalMb MB)..."
                                    }
                                }
                            }
                        }
                        if (tarGzFile.exists() && tarGzFile.length() > 500000) {
                            downloadSuccess = true
                            successfulMirrorUrl = urlStr
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror failed: $urlStr, error: ${e.message}")
                    tarGzFile.delete()
                }
            }

            if (!downloadSuccess) {
                _status.value = LinuxEnvironmentStatus.NOT_INSTALLED
                withContext(Dispatchers.Main) {
                    onComplete(false, "下载 Alpine Linux 根文件系统失败，请检查网络连接后重试。")
                }
                return@launch
            }

            // Extraction Phase
            try {
                _installProgress.value = 0.7f to "准备解压 Alpine Linux 根文件系统..."
                rootfsDir.mkdirs()

                _installProgress.value = 0.75f to "正在解压并挂载系统文件..."
                GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile))).use { gzIn ->
                    TarExtractor.extract(gzIn, rootfsDir) { bytes, currentEntry ->
                        val display = currentEntry.substringAfterLast('/')
                        _installProgress.value = 0.85f to "正在提取: $display"
                    }
                }

                _installProgress.value = 0.92f to "配置网络 DNS 与 APK 源..."
                val etcDir = File(rootfsDir, "etc").apply { if (!exists()) mkdirs() }
                // Ensure /etc/alpine-release exists
                val releaseFile = File(etcDir, "alpine-release")
                if (!releaseFile.exists() || releaseFile.length() == 0L) {
                    releaseFile.writeText("3.20.3\n")
                }

                // Ensure all binary structures, /bin/sh, ld-musl, and permissions are valid
                ensureRootfsStructureAndPermissions()

                // Write /etc/resolv.conf
                File(etcDir, "resolv.conf").writeText(
                    "nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 114.114.114.114\n"
                )

                // Write /etc/hosts
                File(etcDir, "hosts").writeText(
                    "127.0.0.1 localhost\n::1 localhost\n"
                )

                // Write apk mirrors: if downloaded via official CDN, default to official global CDN, otherwise USTC
                val isOverseas = successfulMirrorUrl.contains("dl-cdn.alpinelinux.org")
                val defaultRepo = if (isOverseas) {
                    "https://dl-cdn.alpinelinux.org/alpine/v3.20/main\n" +
                    "https://dl-cdn.alpinelinux.org/alpine/v3.20/community\n"
                } else {
                    "https://mirrors.ustc.edu.cn/alpine/v3.20/main\n" +
                    "https://mirrors.ustc.edu.cn/alpine/v3.20/community\n"
                }
                val apkDir = File(etcDir, "apk").apply { if (!exists()) mkdirs() }
                File(apkDir, "repositories").writeText(defaultRepo)

                // Write root & user profile and .bashrc
                val profileContent = """
                    # Pi Remote Linux Environment
                    export PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:${nativeDir.absolutePath}:${'$'}PATH"
                    export LANG="en_US.UTF-8"
                    export COLORTERM="truecolor"
                    export PS1='\[\033[01;31m\]root@localhost\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]# '
                    alias ls='ls --color=auto'
                    alias ll='ls -la --color=auto'
                    alias grep='grep --color=auto'
                """.trimIndent()
                File(rootHomeDir, ".profile").writeText(profileContent)
                File(rootHomeDir, ".bashrc").writeText(profileContent)
                File(homeDir, ".bashrc").writeText(profileContent)

                // Cleanup archive
                tarGzFile.delete()

                // Ensure PRoot is downloaded if not bundled in nativeLibraryDir
                if (getProotExecutable() == null) {
                    _installProgress.value = 0.94f to "正在下载 PRoot 用户态沙箱运行时..."
                    downloadProotInternal()
                }

                _installProgress.value = 0.98f to "正在校验 Linux 子系统可用性..."
                detectEnvironment()

                _installProgress.value = 1.0f to "Alpine Linux 部署成功！"
                _status.value = LinuxEnvironmentStatus.READY

                withContext(Dispatchers.Main) {
                    onComplete(true, "Alpine Linux 完整发行版部署成功！终端已升级为真实 Linux 子系统。")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                _status.value = LinuxEnvironmentStatus.NOT_INSTALLED
                withContext(Dispatchers.Main) {
                    onComplete(false, "解压失败: ${e.localizedMessage}")
                }
            }
        }
    }

    fun downloadProotInternal(): Boolean {
        if (getProotExecutable() != null) return true
        val prootMirrors = listOf(
            "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot",
            "https://fastly.jsdelivr.net/gh/skirsten/proot-portable-android-binaries@master/aarch64/proot",
            "https://ghproxy.net/https://raw.githubusercontent.com/skirsten/proot-portable-android-binaries/master/aarch64/proot",
            "https://raw.gitmirror.com/skirsten/proot-portable-android-binaries/master/aarch64/proot",
            "https://raw.githubusercontent.com/skirsten/proot-portable-android-binaries/master/aarch64/proot"
        )
        localProotFile.parentFile?.mkdirs()
        for (pUrl in prootMirrors) {
            try {
                val conn = (URL(pUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PiRemote-Android/1.0")
                }
                if (conn.responseCode in 200..299) {
                    BufferedInputStream(conn.inputStream).use { bis ->
                        FileOutputStream(localProotFile).use { fos ->
                            bis.copyTo(fos)
                        }
                    }
                    if (localProotFile.length() > 50000) {
                        localProotFile.setExecutable(true, false)
                        try { Os.chmod(localProotFile.absolutePath, 493) } catch (_: Exception) {}
                        Log.i(TAG, "Successfully downloaded proot to ${localProotFile.absolutePath} (${localProotFile.length()} bytes)")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror $pUrl failed: ${e.message}")
                localProotFile.delete()
            }
        }
        return false
    }

    fun ensureProotAvailable(scope: CoroutineScope, onComplete: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val ok = downloadProotInternal()
            detectEnvironment()
            withContext(Dispatchers.Main) {
                onComplete(ok)
            }
        }
    }

    /**
     * Initializes the workspace and checks real environment usability.
     */
    fun setupEnvironment(scope: CoroutineScope, onComplete: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                _status.value = LinuxEnvironmentStatus.INITIALIZING

                _installProgress.value = 0.3f to "配置本地工作目录与运行参数..."
                rootfsDir.mkdirs()
                File(rootfsDir, "bin").mkdirs()
                File(rootfsDir, "usr/bin").mkdirs()
                File(rootfsDir, "usr/local/bin").mkdirs()
                File(rootfsDir, "etc").mkdirs()
                tmpDir.mkdirs()
                homeDir.mkdirs()
                rootHomeDir.mkdirs()
                npmGlobalDir.mkdirs()
                npmBinDir.mkdirs()
                File(npmGlobalDir, "lib/node_modules").mkdirs()

                _installProgress.value = 0.6f to "生成 Shell 启动环境与别名配置..."
                val bashrc = File(homeDir, ".bashrc")
                val bashrcContent = """
                    # Pi Remote Local Environment
                    export PATH="${nativeDir.absolutePath}:${npmBinDir.absolutePath}:/data/data/com.termux/files/usr/bin:/usr/local/bin:/usr/bin:/bin:${'$'}PATH"
                    export npm_config_prefix="${npmGlobalDir.absolutePath}"
                    export NODE_PATH="${npmGlobalDir.absolutePath}/lib/node_modules:${rootfsDir.absolutePath}/usr/lib/node_modules"
                    export TMPDIR="${tmpDir.absolutePath}"
                    export LANG="en_US.UTF-8"
                    export COLORTERM="truecolor"
                    export PS1='\[\033[01;32m\]android@localhost\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
                    alias ls='ls --color=auto'
                    alias ll='ls -la --color=auto'
                    alias grep='grep --color=auto'
                """.trimIndent()
                try {
                    bashrc.writeText(bashrcContent)
                } catch (_: Exception) {}

                val npmrc = File(homeDir, ".npmrc")
                try {
                    npmrc.writeText("prefix=${npmGlobalDir.absolutePath}\n")
                } catch (_: Exception) {}

                _installProgress.value = 0.9f to "正在检测可用 Linux 与 Node.js 命令行工具..."
                detectEnvironment()

                val tc = _toolchain.value
                val isReady = tc.hasAlpine || tc.hasBash || tc.hasNode
                val message = if (isReady) {
                    _installProgress.value = 1.0f to "Linux 环境校验成功！"
                    "Linux 环境已就绪 (${tc.environmentType})"
                } else {
                    _installProgress.value = 1.0f to "手机本地未安装扩展 Linux CLI"
                    "手机本地处于原生精简模式。可点击「一键部署 Alpine Linux」安装完整发行版。"
                }

                withContext(Dispatchers.Main) {
                    onComplete(isReady, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup Linux environment", e)
                _status.value = LinuxEnvironmentStatus.NOT_INSTALLED
                withContext(Dispatchers.Main) {
                    onComplete(false, "配置失败: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Checks if a launcher file is our shell-based Auto-Install Stub without reading large files into memory.
     * Prevents OutOfMemoryError on Android by checking file length first and reading at most 2KB.
     */
    private fun isAutoInstallStub(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val len = try { file.length() } catch (_: Exception) { 0L }
        // Shell stubs are tiny (~1-2KB). Any real binary or Node bundle > 64KB is definitely not our stub.
        if (len > 64 * 1024L) return false
        return try {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(2048)
                val read = reader.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val head = String(buffer, 0, read)
                    head.contains("Auto-Install Stub")
                } else false
            }
        } catch (_: Exception) { false }
    }

    /**
     * Checks if a Node CLI entrypoint (e.g. dist/cli.js) was corrupted by shell script redirection.
     * Safely reads at most 2KB from the file head to avoid allocating huge buffers.
     */
    private fun isCorruptedCliJs(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return try {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(2048)
                val read = reader.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val head = String(buffer, 0, read)
                    head.contains("statx_shim") || head.contains("Auto-Install Stub") || head.contains("/bin/sh")
                } else false
            }
        } catch (_: Exception) { false }
    }

    private fun probeCommand(cmd: String, pathEnv: String): String? {
        return try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
            val env = pb.environment()
            env["PATH"] = pathEnv
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readLine()?.trim() }
            proc.waitFor()
            if (proc.exitValue() == 0 && !output.isNullOrBlank()) {
                output
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun probeCommandInAlpine(cmd: String): String? {
        val proot = getProotExecutable() ?: return null
        if (!isAlpineRootfsInstalled()) return null
        return try {
            val bindArgs = mutableListOf<String>()
            if (File("/dev").exists()) { bindArgs.add("-b"); bindArgs.add("/dev") }
            if (File("/proc").exists()) { bindArgs.add("-b"); bindArgs.add("/proc") }
            if (File("/sys").exists()) { bindArgs.add("-b"); bindArgs.add("/sys") }
            if (nativeDir.exists()) { bindArgs.add("-b"); bindArgs.add("${nativeDir.absolutePath}:/native") }

            val commandList = mutableListOf(
                proot.absolutePath,
                "--link2symlink",
                "-0",
                "-r", rootfsDir.absolutePath,
            )
            commandList.addAll(bindArgs)
            commandList.add("-w")
            commandList.add("/root")
            commandList.add("/bin/sh")
            commandList.add("-c")
            commandList.add(cmd)

            val pb = ProcessBuilder(commandList)
            pb.directory(rootfsDir)
            val env = pb.environment()
            configureEnvironment(env)
            pb.redirectErrorStream(true)

            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readLine()?.trim() }
            proc.waitFor()
            if (proc.exitValue() == 0 && !output.isNullOrBlank()) {
                output
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dependency-free streaming Tar extraction engine for Android.
     */
    private object TarExtractor {
        fun extract(
            inputStream: InputStream,
            targetDir: File,
            onProgress: ((Long, String) -> Unit)? = null
        ) {
            val header = ByteArray(512)
            var totalBytesRead = 0L
            var zeroBlockCount = 0

            while (true) {
                val bytesRead = readFully(inputStream, header, 512)
                if (bytesRead < 512) break
                totalBytesRead += bytesRead

                // End of archive check: two consecutive 512-byte blocks of zeros
                if (header.all { it == 0.toByte() }) {
                    zeroBlockCount++
                    if (zeroBlockCount >= 2) break
                    continue
                } else {
                    zeroBlockCount = 0
                }

                // File name: offset 0..99
                val name = parseName(header, 0, 100)
                if (name.isEmpty()) continue

                // Check for POSIX ustar prefix: offset 345..499 if magic is "ustar"
                val isUstar = header.size >= 262 &&
                        header[257] == 'u'.code.toByte() &&
                        header[258] == 's'.code.toByte() &&
                        header[259] == 't'.code.toByte() &&
                        header[260] == 'a'.code.toByte() &&
                        header[261] == 'r'.code.toByte()

                val prefix = if (isUstar) parseName(header, 345, 155) else ""
                val rawPath = if (prefix.isNotEmpty()) "$prefix/$name" else name

                // Sanitize entry path: strip leading ./, /, and backslashes
                var cleanPath = rawPath.replace('\\', '/')
                while (cleanPath.startsWith("./")) {
                    cleanPath = cleanPath.substring(2)
                }
                while (cleanPath.startsWith("/")) {
                    cleanPath = cleanPath.substring(1)
                }
                if (cleanPath.isEmpty() || cleanPath == ".") continue

                // Size: offset 124..135 (octal ASCII)
                val size = parseOctal(header, 124, 12)

                // Type flag: offset 156
                val typeFlag = header[156].toInt().toChar()

                val targetFile = File(targetDir, cleanPath)
                // Zip Slip protection
                if (!targetFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    val paddedSize = if (size % 512 == 0L) size else size + (512 - (size % 512))
                    skipBytes(inputStream, paddedSize)
                    totalBytesRead += paddedSize
                    continue
                }

                when (typeFlag) {
                    '5' -> { // Directory
                        targetFile.mkdirs()
                    }
                    '2' -> { // Symbolic link
                        val linkTarget = parseName(header, 157, 100)
                        targetFile.parentFile?.mkdirs()
                        try {
                            if (targetFile.exists()) targetFile.delete()
                            Os.symlink(linkTarget, targetFile.absolutePath)
                        } catch (_: Exception) {
                            val srcFile = File(targetDir, linkTarget.removePrefix("./").removePrefix("/"))
                            if (srcFile.exists() && srcFile.isFile) {
                                try {
                                    srcFile.copyTo(targetFile, overwrite = true)
                                    targetFile.setExecutable(true, false)
                                    Os.chmod(targetFile.absolutePath, 493)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    '1' -> { // Hard link
                        val linkTarget = parseName(header, 157, 100)
                        targetFile.parentFile?.mkdirs()
                        val srcFile = File(targetDir, linkTarget.removePrefix("./").removePrefix("/"))
                        try {
                            if (targetFile.exists()) targetFile.delete()
                            if (srcFile.exists()) {
                                srcFile.copyTo(targetFile, overwrite = true)
                                targetFile.setExecutable(true, false)
                            } else {
                                Os.symlink(linkTarget, targetFile.absolutePath)
                            }
                        } catch (_: Exception) {
                            try { Os.symlink(linkTarget, targetFile.absolutePath) } catch (_: Exception) {}
                        }
                    }
                    '0', '\u0000' -> { // Regular file
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            var remaining = size
                            val fileBuf = ByteArray(16384)
                            while (remaining > 0) {
                                val toRead = minOf(remaining, fileBuf.size.toLong()).toInt()
                                val r = inputStream.read(fileBuf, 0, toRead)
                                if (r <= 0) break
                                fos.write(fileBuf, 0, r)
                                remaining -= r
                                totalBytesRead += r
                            }
                        }
                        // Skip 512-byte tar padding
                        val padding = ((512 - (size % 512)) % 512).toInt()
                        if (padding > 0) {
                            skipBytes(inputStream, padding.toLong())
                            totalBytesRead += padding
                        }
                        if (cleanPath.contains("bin/") || cleanPath.contains("sbin/")) {
                            targetFile.setExecutable(true, false)
                        }
                    }
                    else -> {
                        // Skip PAX, GNU long names, or unknown data blocks
                        val paddedSize = if (size % 512 == 0L) size else size + (512 - (size % 512))
                        skipBytes(inputStream, paddedSize)
                        totalBytesRead += paddedSize
                    }
                }
                onProgress?.invoke(totalBytesRead, cleanPath)
            }
        }

        private fun parseName(header: ByteArray, offset: Int, length: Int): String {
            var end = offset
            val max = offset + length
            while (end < max && header[end] != 0.toByte()) {
                end++
            }
            return String(header, offset, end - offset, Charsets.UTF_8).trim()
        }

        private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
            var result = 0L
            var i = offset
            val end = offset + length
            while (i < end && (header[i] == ' '.code.toByte() || header[i] == 0.toByte())) {
                i++
            }
            while (i < end && header[i] >= '0'.code.toByte() && header[i] <= '7'.code.toByte()) {
                result = (result shl 3) + (header[i] - '0'.code.toByte())
                i++
            }
            return result
        }

        private fun readFully(inputStream: InputStream, buffer: ByteArray, length: Int): Int {
            var total = 0
            while (total < length) {
                val r = inputStream.read(buffer, total, length - total)
                if (r <= 0) break
                total += r
            }
            return total
        }

        private fun skipBytes(inputStream: InputStream, count: Long) {
            var remaining = count
            val skipBuf = ByteArray(8192)
            while (remaining > 0) {
                val toSkip = minOf(remaining, skipBuf.size.toLong()).toInt()
                val r = inputStream.read(skipBuf, 0, toSkip)
                if (r <= 0) break
                remaining -= r
            }
        }
    }
}
