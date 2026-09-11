package com.piremote.terminal;

/**
 * Callbacks the emulator makes back into its host.
 *
 * Ported from Termux's `TerminalSessionClient` and deliberately trimmed: the
 * original is tied to `TerminalSession`, which owns the PTY and native JNI. In
 * this project the PTY lives on the PC and is reached over the relay, so only
 * the parts the emulator itself calls are kept here.
 *
 * Every method may be replaced by passing {@code null}, in which case
 * {@link Logger} falls back to stderr and the default cursor style is used.
 */
public interface TerminalSessionClient {

    /** Cursor shape requested by the host, or null for the emulator default. */
    Integer getTerminalCursorStyle();

    /** The emulator wants the host to show or hide the cursor (e.g. DECTCEM). */
    void onTerminalCursorStateChange(boolean state);

    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);
}
