package com.csmide.app.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.csmide.R;
import com.csmide.app.activities.MainActivity;
import com.csmide.termux.app.TermuxService;
import com.csmide.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.csmide.termux.terminal.TerminalSession;
import com.csmide.termux.terminal.TerminalSessionClient;
import com.csmide.termux.view.TerminalView;
import com.csmide.termux.view.TerminalViewClient;

/**
 * CompileResultFragment displays the output of a background compilation or execution command
 * using a terminal emulator view. It binds to the TermuxService to manage the underlying process.
 */
public class CompileResultFragment extends Fragment implements ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String ARG_COMMAND = "command";
	private static final String ARG_CWD = "cwd";
	private static final String ARG_URI = "uri";
	private static final String ARG_SESSION_DIR = "session_dir";
	private final android.os.Handler updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	private TerminalView terminalView;
	private TermuxService termuxService;
	private TerminalSession terminalSession;
	private String commandToExecute;
	private String cwd;
	private String sessionDir;
	private boolean isSessionStarted = false;
	private String sessionUriString;

	/**
	 * Factory method to create a new instance of CompileResultFragment.
	 *
	 * @param command    The shell command to execute.
	 * @param cwd        The working directory for the command.
	 * @param uri        The unique URI identifying this compilation session.
	 * @param sessionDir The internal session directory for cleanup.
	 * @return A new instance of CompileResultFragment.
	 */
	public static CompileResultFragment newInstance(String command, String cwd, Uri uri, String sessionDir) {
		CompileResultFragment fragment = new CompileResultFragment();
		Bundle args = new Bundle();
		args.putString(ARG_COMMAND, command);
		args.putString(ARG_CWD, cwd);
		args.putString(ARG_URI, uri.toString());
		args.putString(ARG_SESSION_DIR, sessionDir);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			commandToExecute = getArguments().getString(ARG_COMMAND);
			cwd = getArguments().getString(ARG_CWD);
			sessionDir = getArguments().getString(ARG_SESSION_DIR);
			sessionUriString = getArguments().getString(ARG_URI);
		}
		if (savedInstanceState != null) {
			isSessionStarted = savedInstanceState.getBoolean("isSessionStarted", false);
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("isSessionStarted", isSessionStarted);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_compile_result, container, false);
		FrameLayout terminalContainer = view.findViewById(R.id.terminal_view_container);

		terminalView = new TerminalView(requireContext(), null);
		terminalView.setId(R.id.terminal_view);
		terminalView.setIsOutputScreen(true);
		terminalView.setFocusable(true);
		terminalView.setFocusableInTouchMode(true);

		// Ensure terminal view fills the container to avoid UI glitches
		terminalView.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));

		android.content.SharedPreferences prefs = requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, android.content.Context.MODE_PRIVATE);
		int textSizeSp = prefs.getInt(com.csmide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.csmide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
		float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics());
		terminalView.setTextSize(Math.round(px));

		terminalContainer.addView(terminalView);

		applyPreferences();

		Intent serviceIntent = new Intent(requireContext(), TermuxService.class);
		requireContext().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

		// Wait for layout to ensure we have non-zero dimensions before attaching session
		terminalView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				if (terminalView.getWidth() > 0 && terminalView.getHeight() > 0) {
					terminalView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

					// Use a substantial delay to ensure the surface is definitely ready and avoids the race condition
					terminalView.postDelayed(() -> {
						if (isAdded()) {
							tryStartSession();
						}
					}, 500);
				}
			}
		});

		return view;
	}

	private void closeTab() {
		if (getActivity() instanceof MainActivity activity) {
			Bundle args = getArguments();
			if (args != null && args.containsKey(ARG_URI)) {
				Uri myUri = Uri.parse(args.getString(ARG_URI));
				String label = myUri.getQueryParameter("label");
				if (label != null) {
					activity.switchToTabByName(label);
				}
				activity.closeFileInViewPager(myUri);
			}
		}
	}

	private void applyPreferences() {
		if (terminalView == null || !isAdded()) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, android.content.Context.MODE_PRIVATE);
		int textSizeSp = prefs.getInt(com.csmide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.csmide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
		float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics());
		terminalView.setTextSize(Math.round(px));
	}

	@Override
	public void onStart() {
		super.onStart();
		requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (com.csmide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE.equals(key)) {
			if (getActivity() != null) {
				getActivity().runOnUiThread(this::applyPreferences);
			}
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		TermuxService.LocalBinder binder = (TermuxService.LocalBinder) service;
		termuxService = binder.service;
		// Only try to start if view is already laid out and ready
		if (terminalView != null && terminalView.getWidth() > 0) {
			tryStartSession();
		}
	}

	private void tryStartSession() {
		if (termuxService == null || terminalView == null || terminalView.getWidth() == 0 || terminalView.getHeight() == 0) {
			return;
		}

		// Try to recover session if it was already started (e.g. tab switched)
		if (terminalSession == null && sessionUriString != null) {
			com.csmide.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession =
					termuxService.getTermuxSessionForShellName(sessionUriString);
			if (termuxSession != null) {
				terminalSession = termuxSession.getTerminalSession();
				isSessionStarted = true;
			}
		}

		if (terminalSession != null) {
			// Session already exists
			terminalView.setTerminalViewClient(createTerminalViewClient());
			terminalView.attachSession(terminalSession);
			terminalSession.updateTerminalSessionClient(createTerminalSessionClient());

			terminalView.post(() -> {
				terminalView.requestFocus();
				terminalView.onScreenUpdated();
				terminalView.invalidate();
			});
			if (!terminalSession.isRunning()) {
				startExitPolling();
			}
			return;
		}

		if (isSessionStarted) return;

		if (commandToExecute != null && !commandToExecute.isEmpty()) {
			isSessionStarted = true;

			com.csmide.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession = termuxService.createTermuxSession(
					"bash", new String[]{"--noprofile", "--norc", "-c", commandToExecute}, null, cwd, false, sessionUriString);

			if (termuxSession != null) {
				terminalSession = termuxSession.getTerminalSession();
				terminalView.stopTerminalCursorBlinker();
				terminalView.setTerminalViewClient(createTerminalViewClient());
				terminalView.attachSession(terminalSession);
				terminalSession.updateTerminalSessionClient(createTerminalSessionClient());

				// Force initial draw
				terminalView.post(() -> {
					terminalView.requestFocus();
					terminalView.onScreenUpdated();
					terminalView.invalidate();

					// Ensure keyboard doesn't open automatically on initial run
					InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
					if (imm != null) {
						imm.hideSoftInputFromWindow(terminalView.getWindowToken(), 0);
					}
				});

				startExitPolling();
			} else {
				isSessionStarted = false;
				Context context = getContext();
				if (context != null) {
					Toast.makeText(context, "Failed to create terminal session", Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	private TerminalSessionClient createTerminalSessionClient() {
		return new TermuxTerminalSessionClientBase() {
			@Override
			public void onTextChanged(@NonNull TerminalSession changedSession) {
				terminalView.onScreenUpdated();
			}

			@Override
			public void onScreenUpdated() {
				terminalView.onScreenUpdated();
			}

			@Override
			public void onSessionFinished(@NonNull TerminalSession finishedSession) {
				closeTab();
			}
		};
	}

	private TerminalViewClient createTerminalViewClient() {
		return new TerminalViewClient() {
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
				int newSize = terminalView.mRenderer.mTextSize;
				newSize += (increase ? 1 : -1) * 2;
				newSize = Math.max(4, Math.min(newSize, 256));
				terminalView.setTextSize(newSize);
			}

			@Override
			public void onEmulatorSet() {
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
				return true;
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
			}

			@Override
			public void onPasteTextFromClipboard() {
			}

			@Override
			public boolean shouldShowMoreInActionMode() {
				return false;
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
		};
	}

	private void startExitPolling() {
		android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (terminalSession != null && !terminalSession.isRunning()) {
					closeTab();
				} else if (isAdded()) {
					handler.postDelayed(this, 500);
				}
			}
		}, 1000);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		termuxService = null;
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		applyPreferences();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (terminalSession != null && isRemoving()) {
			terminalSession.finishIfRunning();
		}
		if (sessionDir != null && isRemoving()) {
			cleanupDirectory(new java.io.File(sessionDir));
		}
		try {
			requireContext().unbindService(this);
		} catch (Exception ignored) {
		}
	}

	private void cleanupDirectory(java.io.File dir) {
		if (dir.isDirectory()) {
			java.io.File[] children = dir.listFiles();
			if (children != null) {
				for (java.io.File child : children) {
					cleanupDirectory(child);
				}
			}
		}
		dir.delete();
	}
}
