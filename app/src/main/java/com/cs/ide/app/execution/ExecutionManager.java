package com.cs.ide.app.execution;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.cs.ide.R;
import com.cs.ide.app.activities.MainActivity;
import com.cs.ide.app.models.FileItem;
import com.cs.ide.app.utils.FileUtils;

import java.io.File;

/**
 * ExecutionManager is responsible for handling the execution of various file types
 * within the IDE. It determines the appropriate command to run based on the file extension
 * and initiates the execution process, typically by adding a new terminal tab.
 */
public class ExecutionManager {
	private static final String TAG = "ExecutionManager";

	/**
	 * Executes the given file item if its type is supported.
	 *
	 * @param activity The MainActivity instance where the execution is triggered.
	 * @param item     The FileItem representing the file to be executed.
	 */
	public static void runFile(MainActivity activity, FileItem item) {
		if (item == null || item.uri == null) {
			Toast.makeText(activity, R.string.no_file_selected_to_run, Toast.LENGTH_SHORT).show();
			return;
		}

		String mimeType = activity.getMimeType(item.uri);
		if (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("application/xhtml+xml"))) {
			openHtmlInBrowser(activity, item.uri);
			return;
		}

		String absoluteFilePath = FileUtils.getAbsolutePathFromUri(activity, item.uri);
		String fileName = item.displayName;

		if (absoluteFilePath == null || fileName == null) {
			Toast.makeText(activity, "Execution failed: Cannot resolve file path.", Toast.LENGTH_LONG).show();
			Log.e(TAG, "Failed to resolve URI: " + item.uri);
			return;
		}

		String command = resolveExecutionCommand(absoluteFilePath, fileName);
		if (command == null) {
			Toast.makeText(activity, R.string.unsupported_file_type, Toast.LENGTH_SHORT).show();
			return;
		}

		String cwd = new File(absoluteFilePath).getParent();
		if (cwd == null) {
			cwd = activity.getFilesDir().getPath() + "/home";
		}

		executeInTerminal(activity, command, cwd, fileName.toLowerCase());
	}

	/**
	 * Checks if the given URI has an extension that allows it to be run.
	 *
	 * @param fileUri The file URI to check.
	 * @return True if the extension is supported for execution, false otherwise.
	 */
	public static boolean extensionAllowsRun(Uri fileUri) {
		String last = fileUri.getLastPathSegment();
		if (last == null) return false;
		String lower = last.toLowerCase();
		return lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".java") ||
				lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
				lower.endsWith(".html") || lower.endsWith(".xml") || lower.endsWith(".rb") ||
				lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".php") ||
				lower.endsWith(".sh") || lower.endsWith(".swift") || lower.endsWith(".kt") ||
				lower.endsWith(".scala") || lower.endsWith(".pl") || lower.endsWith(".lua") ||
				lower.endsWith(".sql") || lower.endsWith(".r") || lower.endsWith(".dart") ||
				lower.endsWith(".cs");
	}

	// --- Private Helper Methods ---

	/**
	 * Resolves the appropriate shell command for executing a file based on its name/extension.
	 *
	 * @param absoluteFilePath The absolute path to the file.
	 * @param fileName         The name of the file.
	 * @return The command string, or null if unsupported.
	 */
	private static String resolveExecutionCommand(String absoluteFilePath, String fileName) {
		String lowerFileName = fileName.toLowerCase();
		if (lowerFileName.endsWith(".c")) {
			return "gcc \"" + absoluteFilePath + "\" -o \"$HOME/out\" && \"$HOME/out\"";
		} else if (lowerFileName.endsWith(".cpp")) {
			return "g++ \"" + absoluteFilePath + "\" -o \"$HOME/out\" && \"$HOME/out\"";
		} else if (lowerFileName.endsWith(".py")) {
			return "python \"" + absoluteFilePath + "\"";
		} else if (lowerFileName.endsWith(".js") || lowerFileName.endsWith(".ts")) {
			return "node \"" + absoluteFilePath + "\"";
		} else if (lowerFileName.endsWith(".java")) {
			return "java \"" + absoluteFilePath + "\"";
		} else if (lowerFileName.endsWith(".sh")) {
			return "bash \"" + absoluteFilePath + "\"";
		}
		return null;
	}

	/**
	 * Opens an HTML file in an external browser.
	 *
	 * @param context The application context.
	 * @param fileUri The URI of the HTML file.
	 */
	private static void openHtmlInBrowser(Context context, Uri fileUri) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(fileUri, "text/html");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		try {
			context.startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(context, R.string.no_app_found_to_view, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Initiates the execution of a command in a new terminal tab.
	 *
	 * @param activity  The MainActivity instance.
	 * @param command   The command to execute.
	 * @param cwd       The current working directory for the command.
	 * @param labelName The label for the terminal tab.
	 */
	private static void executeInTerminal(MainActivity activity, String command, String cwd, String labelName) {
		Uri compileUri = Uri.parse("app://com.cs.ide/compile")
				.buildUpon()
				.appendQueryParameter("command", command)
				.appendQueryParameter("cwd", cwd)
				.appendQueryParameter("timestamp", String.valueOf(System.currentTimeMillis()))
				.build();

		MainActivity.viewPagerAdapter.addTab(compileUri, activity.getString(R.string.run_prefix, labelName));
	}
}
