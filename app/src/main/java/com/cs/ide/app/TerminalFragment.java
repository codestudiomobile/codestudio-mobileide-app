package com.cs.ide.app;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.cs.ide.R;
import com.cs.ide.termux.terminal.TerminalSession;
import com.cs.ide.termux.view.TerminalView;
public class TerminalFragment extends Fragment implements TermuxSessionManager.SessionCallback {
    private static final String TAG = "TerminalFragment";
    private TerminalView terminalView;
    private TerminalSession currentSession;
    private ConsoleInputListener listener;
    private Uri launchUri;
    private boolean isAwaitingFinalEnter = false;
    public static TerminalFragment newInstance(Uri uri) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        Log.d(TAG, "newInstance: created");
        return fragment;
    }
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ConsoleInputListener) {
            listener = (ConsoleInputListener) context;
        } else {
        }
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            launchUri = getArguments().getParcelable("uri");
        }
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_terminal_code_studio, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        terminalView = view.findViewById(R.id.terminalView);
        currentSession = TermuxSessionManager.startSession(requireContext(), terminalView, this);
    }
    public void runCommand(String rawCommand) {
        if (currentSession == null) {
            Toast.makeText(requireContext(), "Terminal not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        isAwaitingFinalEnter = false;
        String wrappedCommand = String.format(
                "%s; EXIT_CODE=$?; printf '\\nExecution finished (Exit Code: %%s). Press ENTER to continue...' \"$EXIT_CODE\" >&2; read -r -n 1;",
                rawCommand.replace("'", "'\\''")
        );
        try {
            currentSession.write(wrappedCommand + "\n");
            Log.i(TAG, "Executing wrapped command: " + wrappedCommand);
        }   catch (Exception e) {
        }
        isAwaitingFinalEnter = true;
        if (listener != null)
            listener.onUserInputSubmitted(rawCommand);
    }
    @Override
    public void onSessionExit(int sessionId, int exitCode) {
        Log.d(TAG, "Session ID " + sessionId + " terminated naturally. Cleaning up.");
        closeFragmentAndSession();
    }
    public void sendInput(String input) {
        if (currentSession != null) {
            TermuxSessionManager.sendCommand(currentSession, input);
        }
    }
    private void closeFragmentAndSession() {
        if (currentSession != null) {
            TermuxSessionManager.closeSession(currentSession);
            currentSession = null;
        }
        if (getFragmentManager() != null) {
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss(); 
            Toast.makeText(requireContext(), "Terminal session closed.", Toast.LENGTH_SHORT).show();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && terminalView != null) {
                imm.hideSoftInputFromWindow(terminalView.getWindowToken(), 0);
            }
        }
    }
    @Override
    public void onDestroyView() {
        TermuxSessionManager.closeSession(currentSession);
        super.onDestroyView();
    }
    public interface ConsoleInputListener {
        void onUserInputSubmitted(String input);
    }
}
