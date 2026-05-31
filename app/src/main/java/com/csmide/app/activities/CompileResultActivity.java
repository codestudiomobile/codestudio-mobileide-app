package com.csmide.app.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.csmide.R;
import com.csmide.termux.app.TermuxService;
import com.csmide.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.csmide.termux.terminal.TerminalSession;
import com.csmide.termux.view.TerminalView;
import com.csmide.termux.view.TerminalViewClient;

/**
 * CompileResultActivity displays the execution output of a compilation or run command.
 * it uses a terminal emulator view to show real-time output and supports ANSI color formatting.
 */
public class CompileResultActivity extends AppCompatActivity implements ServiceConnection {
	public static final String EXTRA_COMMAND = "command";
	public static final String EXTRA_CWD = "cwd";

	private TerminalView terminalView;
	private TermuxService termuxService;
	private TerminalSession terminalSession;
	private String commandToExecute;
	private String cwd;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_compile_result);

		// Find or create the terminal view
		terminalView = findViewById(R.id.terminal_view_container).findViewById(R.id.terminal_view);
		if (terminalView == null) {
			terminalView = new TerminalView(this, null);
			terminalView.setId(R.id.terminal_view);

			android.content.SharedPreferences prefs = getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
			int textSizeSp = prefs.getInt(com.csmide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.csmide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
			float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics());
			terminalView.setTextSize(Math.round(px));

			((android.widget.FrameLayout) findViewById(R.id.terminal_view_container)).addView(terminalView);
			registerForContextMenu(terminalView);
		}

		TextView actionClose = findViewById(R.id.actionClose);
		actionClose.setOnClickListener(v -> finish());

		// Extract command and working directory from intent
		Intent intent = getIntent();
		if (intent != null) {
			commandToExecute = intent.getStringExtra(EXTRA_COMMAND);
			cwd = intent.getStringExtra(EXTRA_CWD);
		}

		// Bind to TermuxService to run the shell command
		Intent serviceIntent = new Intent(this, TermuxService.class);
		startService(serviceIntent);
		bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
	}

	/**
	 * Called when the connection to TermuxService is established.
	 * Initiates the command execution.
	 */
	@Override
	public void onCreateContextMenu(android.view.ContextMenu menu, View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(android.view.Menu.NONE, 10, android.view.Menu.NONE, R.string.action_copy);
		menu.add(android.view.Menu.NONE, 12, android.view.Menu.NONE, R.string.action_share_selected_text);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		int id = item.getItemId();
		if (id == 10) {
			String text = terminalView.getSelectedText();
			if (text != null) {
				com.csmide.termux.shared.interact.ShareUtils.copyTextToClipboard(this, text);
				terminalView.setCopyMode(false);
			}
			return true;
		} else if (id == 12) {
			String selectedText = terminalView.getSelectedText();
			if (selectedText != null) {
				com.csmide.termux.shared.interact.ShareUtils.shareText(this, getString(R.string.title_share_selected_text), selectedText, getString(R.string.title_share_selected_text_with));
				terminalView.setCopyMode(false);
			}
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		TermuxService.LocalBinder binder = (TermuxService.LocalBinder) service;
		termuxService = binder.service;

		if (commandToExecute != null && !commandToExecute.isEmpty()) {
			// Wrap command to highlight stderr in red using bash redirection
			String wrappedCommand = "eval '" + commandToExecute.replace("'", "'\\''")
					+ "' 2> >(awk '{print \"\\033[31m\" $0 \"\\033[0m\"}' >&2)";

			// Create a temporary termux session for the command
			com.csmide.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession = termuxService.createTermuxSession(
					"bash", new String[]{"-c", wrappedCommand}, null, cwd, false, "Compile Result");

			if (termuxSession != null) {
				terminalSession = termuxSession.getTerminalSession();
				terminalSession.updateTerminalSessionClient(new TermuxTerminalSessionClientBase() {
					@Override
					public void onTextChanged(@NonNull TerminalSession changedSession) {
						terminalView.onScreenUpdated();
					}

					@Override
					public void onScreenUpdated() {
						terminalView.onScreenUpdated();
					}
				});

				// Configure terminal view client with default implementations
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

					@Override
					public void onEmulatorSet() {
					}

					@Override
					public void onSingleTapUp(android.view.MotionEvent e) {
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
	protected void onDestroy() {
		super.onDestroy();
		if (terminalSession != null && termuxService != null) {
			terminalSession.finishIfRunning();
		}
		try {
			unbindService(this);
		} catch (Exception ignored) {
		}
	}
}
