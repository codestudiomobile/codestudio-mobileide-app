package com.cs.ide.app.fragments;

import android.content.Context;
import android.content.SharedPreferences;
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
public class TerminalFragment extends Fragment implements TermuxSessionManager.SessionCallback, SharedPreferences.OnSharedPreferenceChangeListener {
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
		registerForContextMenu(terminalView);

		applyPreferences();

		// Start the terminal session
		currentSession = TermuxSessionManager.startSession(requireContext(), terminalView, this);

		terminalView.setTerminalViewClient(new com.cs.ide.termux.view.TerminalViewClient() {
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

			@Override
			public void onSingleTapUp(android.view.MotionEvent e) {
				terminalView.requestFocus();
			}

			@Override
			public boolean shouldBackButtonBeMappedToEscape() {
				return false;
			}

			@Override
			public boolean shouldEnforceCharBasedInput() {
				return false;
			}

			@Override
			public boolean shouldUseCtrlSpaceWorkaround() {
				return false;
			}

			@Override
			public boolean isTerminalViewSelected() {
				return true;
			}

			@Override
			public void copyModeChanged(boolean copyMode) {
			}

			@Override
			public void onCopyTextToClipboard(String text) {
				com.cs.ide.termux.shared.interact.ShareUtils.copyTextToClipboard(requireContext(), text);
			}

			@Override
			public void onPasteTextFromClipboard() {
				String text = com.cs.ide.termux.shared.interact.ShareUtils.getTextStringFromClipboardIfSet(requireContext(), true);
				if (text != null) {
					currentSession.write(text);
				}
			}

			@Override
			public boolean shouldShowMoreInActionMode() {
				return true;
			}

			@Override
			public boolean onKeyDown(int keyCode, android.view.KeyEvent e, TerminalSession session) {
				return false;
			}

			@Override
			public boolean onKeyUp(int keyCode, android.view.KeyEvent e) {
				return false;
			}

			@Override
			public boolean onLongPress(android.view.MotionEvent event) {
				return false;
			}

			@Override
			public boolean readControlKey() {
				return false;
			}

			@Override
			public boolean readAltKey() {
				return false;
			}

			@Override
			public boolean readShiftKey() {
				return false;
			}

			@Override
			public boolean readFnKey() {
				return false;
			}

			@Override
			public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
				return false;
			}

			@Override
			public void logError(String tag, String message) {
			}

			@Override
			public void logWarn(String tag, String message) {
			}

			@Override
			public void logInfo(String tag, String message) {
			}

			@Override
			public void logDebug(String tag, String message) {
			}

			@Override
			public void logVerbose(String tag, String message) {
			}

			@Override
			public void logStackTraceWithMessage(String tag, String message, Exception e) {
			}

			@Override
			public void logStackTrace(String tag, Exception e) {
			}

			@Override
			public void onEmulatorSet() {
			}
		});

		setupExtraKeys(view);

		// If launched with a specific command (via URI), execute it
		if (launchUri != null && "compile".equals(launchUri.getHost())) {
			String command = launchUri.getQueryParameter("command");
			if (command != null) {
				// Don't use runCommand() here to avoid double-wrapping and quote mangling
				// The command from ExecutionManager is already fully wrapped and escaped.
				try {
					currentSession.write(command + "\n");
				} catch (Exception e) {
					Log.e(TAG, "Error writing command to session", e);
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

	@Override
	public void onResume() {
		super.onResume();
		applyPreferences();
	}

	@Override
	public void onStart() {
		super.onStart();
		requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE.equals(key)) {
			if (getActivity() != null) {
				getActivity().runOnUiThread(this::applyPreferences);
			}
		}
	}

	private void applyPreferences() {
		if (terminalView == null || !isAdded()) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		int textSizeSp = prefs.getInt(com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.cs.ide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
		float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics());
		terminalView.setTextSize(Math.round(px));
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
	public void onCreateContextMenu(@NonNull android.view.ContextMenu menu, @NonNull View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(android.view.Menu.NONE, 10, android.view.Menu.NONE, R.string.action_copy);
		menu.add(android.view.Menu.NONE, 12, android.view.Menu.NONE, R.string.action_share_selected_text);
	}

	@Override
	public boolean onContextItemSelected(@NonNull android.view.MenuItem item) {
		int id = item.getItemId();
		if (id == 10) {
			String text = terminalView.getSelectedText();
			if (text != null) {
				com.cs.ide.termux.shared.interact.ShareUtils.copyTextToClipboard(requireContext(), text);
				terminalView.setCopyMode(false);
			}
			return true;
		} else if (id == 12) {
			String selectedText = terminalView.getSelectedText();
			if (selectedText != null) {
				com.cs.ide.termux.shared.interact.ShareUtils.shareText(requireContext(), getString(R.string.title_share_selected_text), selectedText, getString(R.string.title_share_selected_text_with));
				terminalView.setCopyMode(false);
			}
			return true;
		}
		return super.onContextItemSelected(item);
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
