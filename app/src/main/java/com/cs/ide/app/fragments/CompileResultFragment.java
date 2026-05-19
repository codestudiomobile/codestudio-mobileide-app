package com.cs.ide.app.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cs.ide.R;
import com.cs.ide.app.activities.MainActivity;
import com.cs.ide.termux.app.TermuxService;
import com.cs.ide.termux.terminal.TerminalSession;
import com.cs.ide.termux.view.TerminalView;
import com.cs.ide.termux.view.TerminalViewClient;

/**
 * CompileResultFragment displays the output of a background compilation or execution command
 * using a terminal emulator view. It binds to the TermuxService to manage the underlying process.
 */
public class CompileResultFragment extends Fragment implements ServiceConnection {
    private static final String ARG_COMMAND = "command";
    private static final String ARG_CWD = "cwd";
    private static final String ARG_URI = "uri";

    private TerminalView terminalView;
    private TermuxService termuxService;
    private TerminalSession terminalSession;
    private String commandToExecute;
    private String cwd;

    /**
     * Factory method to create a new instance of CompileResultFragment.
     *
     * @param command The shell command to execute.
     * @param cwd     The working directory for the command.
     * @param uri     The unique URI identifying this compilation session.
     * @return A new instance of CompileResultFragment.
     */
    public static CompileResultFragment newInstance(String command, String cwd, Uri uri) {
        CompileResultFragment fragment = new CompileResultFragment();
        Bundle args = new Bundle();
        args.putString(ARG_COMMAND, command);
        args.putString(ARG_CWD, cwd);
        args.putString(ARG_URI, uri.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            commandToExecute = getArguments().getString(ARG_COMMAND);
            cwd = getArguments().getString(ARG_CWD);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_compile_result, container, false);
        FrameLayout terminalContainer = view.findViewById(R.id.terminal_view_container);

        terminalView = new TerminalView(requireContext(), null);
        terminalView.setId(R.id.terminal_view);

        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, android.content.Context.MODE_PRIVATE);
        int textSizeSp = prefs.getInt(com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.cs.ide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
        float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics());
        terminalView.setTextSize(Math.round(px));

        terminalContainer.addView(terminalView);

        view.findViewById(R.id.actionClose).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                Bundle args = getArguments();
                if (args != null && args.containsKey(ARG_URI)) {
                    Uri myUri = Uri.parse(args.getString(ARG_URI));
                    ((MainActivity) getActivity()).closeFileInViewPager(myUri);
                }
            }
        });

        Intent serviceIntent = new Intent(requireContext(), TermuxService.class);
        requireContext().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        return view;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        TermuxService.LocalBinder binder = (TermuxService.LocalBinder) service;
        termuxService = binder.service;

        if (commandToExecute != null && !commandToExecute.isEmpty() && terminalView != null) {
            // Inject ANSI Red Color for stderr using bash pipeline
            String wrappedCommand = "eval '" + commandToExecute.replace("'", "'\\''")
                    + "' 2> >(awk '{print \"\\033[31m\" $0 \"\\033[0m\"}' >&2)";

            com.cs.ide.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession = termuxService.createTermuxSession(
                    "bash", new String[] { "-c", wrappedCommand }, null, cwd, false, "Compile Result");

            if (termuxSession != null) {
                terminalSession = termuxSession.getTerminalSession();
                terminalView.setTerminalViewClient(new TerminalViewClient() {
                    @Override
                    public float onScale(float scale) {
                        if (scale < 0.9f || scale > 1.1f) {
                            boolean increase = scale > 1.f;
                            changeFontSize(increase);
                            return 1.0f;
                        }
                        return scale;
                    }

                    private void changeFontSize(boolean increase) {
                        int fontSize = terminalView.mRenderer.mTextSize;
                        fontSize += (increase ? 1 : -1) * 2;
                        fontSize = Math.max(4, Math.min(fontSize, 256));
                        terminalView.setTextSize(fontSize);
                    }

                    @Override public void onEmulatorSet() {}
                    @Override public void onSingleTapUp(android.view.MotionEvent e) {}
                    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
                    @Override public boolean shouldEnforceCharBasedInput() { return false; }
                    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
                    @Override public boolean isTerminalViewSelected() { return true; }
                    @Override public void copyModeChanged(boolean copyMode) {}
                    @Override public void onCopyTextToClipboard(String text) {}
                    @Override public void onPasteTextFromClipboard() {}
                    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent e, TerminalSession session) { return false; }
                    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent e) { return false; }
                    @Override public boolean onLongPress(android.view.MotionEvent event) { return false; }
                    @Override public boolean readControlKey() { return false; }
                    @Override public boolean readAltKey() { return false; }
                    @Override public boolean readShiftKey() { return false; }
                    @Override public boolean readFnKey() { return false; }
                    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
                    @Override public void logError(String tag, String message) {}
                    @Override public void logWarn(String tag, String message) {}
                    @Override public void logInfo(String tag, String message) {}
                    @Override public void logDebug(String tag, String message) {}
                    @Override public void logVerbose(String tag, String message) {}
                    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) {}
                    @Override public void logStackTrace(String tag, Exception e) {}
                });
                terminalView.attachSession(terminalSession);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        termuxService = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (terminalSession != null) {
            terminalSession.finishIfRunning();
        }
        try {
            requireContext().unbindService(this);
        } catch (Exception ignored) {}
    }
}
