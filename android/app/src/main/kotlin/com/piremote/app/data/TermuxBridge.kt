package com.piremote.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Termux Interoperability Bridge.
 * Handles package presence detection, Intent launching, clipboard assistance,
 * direct APK downloading, and com.termux.RUN_COMMAND IPC.
 */
object TermuxBridge {

    const val TERMUX_PACKAGE_NAME = "com.termux"
    const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    const val TERMUX_SERVICE_NAME = "com.termux.app.RunCommandService"

    const val SETUP_COMMAND = "pkg update -y && pkg install -y nodejs-lts git && npm install -g @earendil-works/pi-coding-agent"
    const val RUN_PI_COMMAND = "pi"

    // Official download sources
    const val URL_FDROID_TERMUX = "https://f-droid.org/packages/com.termux/"
    const val URL_GITHUB_LATEST = "https://github.com/termux/termux-app/releases/latest"

    /**
     * Checks if Termux is currently installed on the Android device.
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the Termux main application window.
     */
    fun launch(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开 Termux: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies the full one-line setup command to clipboard and immediately launches Termux.
     */
    fun copySetupAndLaunch(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("termux_setup", SETUP_COMMAND))
            Toast.makeText(context, "已复制环境安装命令！请在 Termux 窗口长按并选择 Paste 执行", Toast.LENGTH_LONG).show()

            if (!launch(context)) {
                Toast.makeText(context, "未检测到 Termux，请先下载安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "操作失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches browser to directly download the Termux APK.
     */
    fun openDownload(context: Context, preferGithub: Boolean = false) {
        val targetUrl = if (preferGithub) URL_GITHUB_LATEST else URL_FDROID_TERMUX
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开下载页面: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Executes a command inside Termux using the official com.termux.RUN_COMMAND protocol.
     */
    fun runCommand(
        context: Context,
        command: String,
        workingDir: String? = null,
        inBackground: Boolean = false,
    ): Boolean {
        if (!isInstalled(context)) {
            Toast.makeText(context, "未安装 Termux，无法执行", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val intent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE_NAME, TERMUX_SERVICE_NAME)
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir ?: "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", inBackground)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // 0: Open / Switch to foreground session
            }
            context.startService(intent)
            true
        } catch (_: Exception) {
            // Fallback: Copy command and launch Termux normally
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("termux_cmd", command))
            Toast.makeText(context, "已复制指令，正在拉起 Termux...", Toast.LENGTH_SHORT).show()
            launch(context)
        }
    }
}
