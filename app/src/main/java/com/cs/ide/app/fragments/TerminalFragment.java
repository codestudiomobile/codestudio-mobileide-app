package com.cs.ide.app.fragments;

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
import com.cs.ide.app.TermuxSessionManager;
import com.cs.ide.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.cs.ide.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.cs.ide.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.cs.ide.termux.shared.termux.extrakeys.ExtraKeysView;
import com.cs.ide.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.cs.ide.termux.terminal.TerminalSession;
import com.cs.ide.termux.view.TerminalView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;

/**
 * TerminalFragment provides an interactive shell interface within the IDE.
 * It uses a TerminalView connected to a TerminalSession managed by TermuxSessionManager.
 */
public class TerminalFragment extends Fragment implements TermuxSessionManager.SessionCallback {
    private static final String TAG = "TerminalFragment";
    
    private TerminalView terminalView;
    private TerminalSession currentSession;
    private ConsoleInputListener listener;
    private Uri launchUri;
    private boolean isAwaitingFinalEnter = false;

    /**
     * Creates a new instance of TerminalFragment.
     *
     * @param uri The URI that triggered the terminal (e.g., compile command URI).
     * @return A new instance.
     */
    public static TerminalFragment newInstance(Uri uri) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ConsoleInputListener) {
            listener = (ConsoleInputListener) context;
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
        
        // Start the terminal session
        currentSession = TermuxSessionManager.startSession(requireContext(), terminalView, this);

        setupExtraKeys(view);
        
        // If launched with a specific command (via URI), execute it
        if (launchUri != null && "compile".equals(launchUri.getHost())) {
            String command = launchUri.getQueryParameter("command");
            String cwd = launchUri.getQueryParameter("cwd");
            if (command != null) {
                if (cwd != null) {
                    runCommand("cd \"" + cwd + "\" && " + command);
                } else {
                    runCommand(command);
                }
            }
        }
    }

    /**
     * Sets up the extra keys toolbar for the terminal.
     */
    private void setupExtraKeys(View view) {
        ExtraKeysView extraKeysView = view.findViewById(R.id.terminalExtraKeys);
        if (extraKeysView != null) {
            try {
                // Configure the extra keys layout (2 rows)
                String extraKeysConfig = "[[" +
                        "\"ESC\", \"TAB\", \"CTRL\", \"ALT\", {key: \"UP\", popup: \"PAGEUP\"}, \"-\", \"KEYBOARD\"" +
                        "],[" +
                        "\"LEFT\", \"DOWN\", \"RIGHT\", \"ENTER\", {key: \"BKSP\", popup: \"DEL\"}, \"/\", \"DRAWER\"" +
                        "]]";

                ExtraKeysInfo extraKeysInfo = new ExtraKeysInfo(extraKeysConfig, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
                
                // Initialize the client to handle button clicks
                TerminalExtraKeys terminalExtraKeys = new TerminalExtraKeys(terminalView) {
                    @Override
                    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
                        String key = buttonInfo.getKey();
                        if ("KEYBOARD".equals(key)) {
                            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                            }
                        } else if ("DRAWER".equals(key)) {
                            // This would ideally open a side drawer if one exists in MainActivity
                            Toast.makeText(requireContext(), "Drawer key pressed", Toast.LENGTH_SHORT).show();
                        } else {
                            super.onExtraKeyButtonClick(view, buttonInfo, button);
                        }
                    }
                };

                extraKeysView.setExtraKeysViewClient(terminalExtraKeys);
                extraKeysView.reload(extraKeysInfo, 0); // heightPx is only used for Lollipop compat in reload
            } catch (JSONException e) {
                Log.e(TAG, "Error setting up extra keys", e);
            }
        }
    }

    /**
     * Runs a command in the terminal session, adding a prompt to continue at the end.
     *
     * @param rawCommand The shell command to run.
     */
    public void runCommand(String rawCommand) {
        if (currentSession == null) {
            Toast.makeText(requireContext(), "Terminal not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        isAwaitingFinalEnter = false;
        
        // Wrap the command to capture exit code and wait for user input before closing
        String wrappedCommand = String.format(
                "%s; EXIT_CODE=$?; printf '\\nExecution finished (Exit Code: %%s). Press ENTER to continue...' \"$EXIT_CODE\" >&2; read -r -n 1;",
                rawCommand.replace("'", "'\\''")
        );
        
        try {
            currentSession.write(wrappedCommand + "\n");
        } catch (Exception e) {
            Log.e(TAG, "Error running command", e);
        }
        
        isAwaitingFinalEnter = true;
        if (listener != null) {
            listener.onUserInputSubmitted(rawCommand);
        }
    }

    /**
     * Callback when the terminal session exits.
     */
    @Override
    public void onSessionExit(int sessionId, int exitCode) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::closeFragmentAndSession);
        }
    }

    /**
     * Sends raw string input to the terminal session.
     *
     * @param input The input string.
     */
    public void sendInput(String input) {
        if (currentSession != null) {
            TermuxSessionManager.sendCommand(currentSession, input);
        }
    }

    /**
     * Cleans up the terminal session and removes the fragment.
     */
    private void closeFragmentAndSession() {
        if (currentSession != null) {
            TermuxSessionManager.closeSession(currentSession);
            currentSession = null;
        }
        
        if (isAdded() && getParentFragmentManager() != null) {
            // Logic to handle tab removal should ideally be in ViewPagerAdapter/MainActivity.
            // For now, we assume the host handles UI cleanup or we just hide keyboard.
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

    /**
     * Listener interface for console input submission.
     */
    public interface ConsoleInputListener {
        void onUserInputSubmitted(String input);
    }
}
