# Android arm64-v8a Native Libraries Directory (方案一: 内置完整 Linux 原生运行环境)

本目录为 Pi Remote 的 Android 原生二进制/共享库目录。

## 方案一：完整 Linux 原生子系统机制说明
为彻底绕过 Android 10+（API 29-36）系统的 W^X（禁止在私有可写目录动态执行二进制）与 SELinux 限制，并在免 Root 前提下提供真正的 Linux 用户态系统：
- **`libbash.so`**：适配 Android arm64-v8a 的 GNU Bash 可执行 ELF 二进制，支持完整的 Bash 语法、色彩 Prompt、Tab 补全与 Shell 脚本。
- **`libbusybox.so`**：适配 Android arm64-v8a 的 BusyBox 核心工具箱，内置 `tar`、`gzip`、`grep`、`sed`、`awk`、`find`、`curl`、`wget`、`vi`、`top`、`ps`、`kill` 等 100+ 标准 Linux 核心命令。
- **`libnode.so`**：适配 Android arm64-v8a Bionic libc 的 Node.js 完整可执行二进制。
- **`libproot.so`**：PRoot 用户态沙箱虚拟化核心，配合 Alpine Linux minirootfs 实现 fake-root (`uid 0`) 与文件系统 chroot 挂载。

### 系统加载机制
1. `app/build.gradle.kts` 中配置了 `packaging { jniLibs { useLegacyPackaging = true } }`；
2. 安装 APK 后，Android 系统 PackageManager 自动将这些 ELF 二进制提取至受信任的只读目录 `context.applicationInfo.nativeLibraryDir`（例如 `/data/app/.../lib/arm64/`）；
3. 该目录拥有系统内核原生的免 Root 可执行权限，App 启动终端时即可直接拉起真正的 GNU Bash 与 Linux 工具链！
