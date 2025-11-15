package com.cs.ide.app;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.cs.ide.termux.terminal.TerminalSession;
import com.cs.ide.termux.terminal.TerminalSessionClient;
import com.cs.ide.termux.view.TerminalView;
import java.io.File;
public class TermuxSessionManager {
    private static final String TAG = "TermuxSessionManager";
    private static int sessionCounter = 1000;
    public static TerminalSession startSession(Context context, TerminalView terminalView, final SessionCallback callback) {
        final int currentSessionId = sessionCounter++; 
        File rootDir = EnvironmentSetup.getEnvironmentRoot(context);
        File binDir = EnvironmentSetup.getBinDir(context);
        String rootDirPath = rootDir.getAbsolutePath();
        String binPath = binDir.getAbsolutePath();
        String homePath = rootDirPath + "/home";
        String executablePath = binPath + "/bash";
        if (!new File(executablePath).exists()) {
            executablePath = binPath + "/sh"; 
        }
        String workingDirectory = homePath;
        String[] environment = new String[]{"PATH=" + binPath + ":" + System.getenv("PATH"), "HOME=" + homePath, "TERM=xterm-256color", "SESSION_ID=" + currentSessionId, "LANG=en_US.UTF-8"};
        final TerminalSession terminalSession = new TerminalSession(executablePath, workingDirectory, environment, environment, currentSessionId, new TerminalSessionClient() {
            private final Handler mainHandler = new Handler(Looper.getMainLooper());
            @Override
            public void onTextChanged(@NonNull TerminalSession changedSession) {
            }
            @Override
            public void onExit(int exitCode) {
                Log.d(TAG, "Session ID " + currentSessionId + " exited with code: " + exitCode);
                mainHandler.post(() -> callback.onSessionExit(currentSessionId, exitCode));
            }
            @Override
            public void onTitleChanged(TerminalSession session) {
            }
            @Override
            public void onSessionFinished(TerminalSession session) {
            }
            @Override
            public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
            }
            @Override
            public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
            }
            @Override
            public void onClipboardText(TerminalSession session, String text) {
            }
            @Override
            public void onBell(@NonNull TerminalSession session) {
            }
            @Override
            public void onColorsChanged(@NonNull TerminalSession session) {
            }
            @Override
            public void onTerminalCursorStateChange(boolean state) {
            }
            @Override
            public void onScreenUpdated() {
            }
            @Override
            public void onSessionActivity() {
            }
            @Override
            public void onTerminalFeed(byte[] data) {
            }
            @Override
            public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
            }
            @Override
            public Integer getTerminalCursorStyle() {
                return 0;
            }
            @Override
            public void logError(String tag, String message) {
                Log.e(tag, message);
            }
            @Override
            public void logWarn(String tag, String message) {
                Log.w(tag, message);
            }
            @Override
            public void logInfo(String tag, String message) {
                Log.i(tag, message);
            }
            @Override
            public void logDebug(String tag, String message) {
                Log.d(tag, message);
            }
            @Override
            public void logVerbose(String tag, String message) {
                Log.v(tag, message);
            }
            @Override
            public void logStackTraceWithMessage(String tag, String message, Exception e) {
                Log.e(tag, message, e);
            }
            @Override
            public void logStackTrace(String tag, Exception e) {
            }
            @Override
            public void onTerminalResize(int columns, int rows) {
            }
        });
        terminalSession.initializeEmulator(80, 24, 0, 0);
        terminalView.attachSession(terminalSession);
        return terminalSession;
    }
    public static void sendCommand(TerminalSession session, String input) {
        if (session != null) {
            try {
                session.write(input + "\n");
            }  catch (Exception e) {
            }
        }
    }
    public static void closeSession(TerminalSession session) {
        if (session != null) {
            session.finishIfRunning(); 
        }
    }
    public interface SessionCallback {
        void onSessionExit(int sessionId, int exitCode);
    }
}
