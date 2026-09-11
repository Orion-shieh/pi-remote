// This module is a port of the terminal emulator from termux-app.
//
//   https://github.com/termux/termux-app/tree/master/terminal-emulator
//
// termux-app is licensed under the GNU General Public License v3.0, so this
// module - and the application that links it - are covered by the same licence.
// The full text is in LICENSE-GPLv3.md next to this file.
//
// What was changed during the port:
//
//  * Package renamed from com.termux.terminal to com.piremote.terminal.
//  * Android dependencies removed so the module stays a plain JVM library and
//    its test suite runs without a device or emulator:
//      - Logger: android.util.Log -> stderr fallback
//      - TerminalColors: android.graphics.Color -> plain bit arithmetic
//      - TerminalEmulator: android.util.Base64 -> java.util.Base64
//  * TerminalSessionClient trimmed to the methods TerminalEmulator actually
//    calls. PTY ownership stays on the PC side, reached over the relay, so
//    TerminalSession, JNI and the native build were not ported.
//  * KeyHandler was moved to the :terminal-view module, which can see
//    android.view.KeyEvent.

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // JUnit 4 still ships the junit.framework.* classes the ported tests use.
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
