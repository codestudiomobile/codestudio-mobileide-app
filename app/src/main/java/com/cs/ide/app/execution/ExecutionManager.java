package com.cs.ide.app.execution;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.cs.ide.R;
import com.cs.ide.app.activities.MainActivity;
import com.cs.ide.app.models.FileItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ExecutionManager is responsible for handling the execution of various file types
 * within the IDE. It moves files to internal storage for execution and ensures
 * they are cleaned up afterwards.
 */
public class ExecutionManager {
	private static final String TAG = "ExecutionManager";
	private static final String EXEC_DIR_NAME = "bin_exec_cache";

	/**
	 * Executes the given file item if its type is supported.
	 *
	 * @param activity The MainActivity instance.
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

		// 1. Prepare unique internal execution directory
		String fileName = item.displayName;
		if (fileName == null) fileName = "temp_file";

		File baseExecDir = new File(activity.getFilesDir(), EXEC_DIR_NAME);
		File sessionDir = new File(baseExecDir, "session_" + System.currentTimeMillis());

		if (!sessionDir.exists() && !sessionDir.mkdirs()) {
			Toast.makeText(activity, "Failed to create execution environment.", Toast.LENGTH_SHORT).show();
			return;
		}

		File targetFile = new File(sessionDir, fileName);

		// 2. Copy file to internal storage
		if (!copyUriToInternal(activity, item.uri, targetFile)) {
			Toast.makeText(activity, "Failed to prepare file for execution.", Toast.LENGTH_SHORT).show();
			cleanupDirectory(sessionDir);
			return;
		}

		// 3. Resolve command using the internal path
		CommandFetcher fetcher = new CommandFetcher(activity);
		String internalPath = targetFile.getAbsolutePath();
		String command = fetcher.resolveCommandForFile(internalPath);

		if (command == null) {
			// Fallback to basic resolution
			command = resolveExecutionCommandFallback(internalPath, fileName);
		}

		if (command == null) {
			Toast.makeText(activity, R.string.unsupported_file_type, Toast.LENGTH_SHORT).show();
			cleanupDirectory(sessionDir);
			return;
		}

		// 4. Wrap command with formatting and cleanup
		String timeStr = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.US).format(new java.util.Date());
		String wrappedCommand = String.format(
				"echo \"Current Time:%s\"; " +
						"eval '%s' 2> >(while read line; do echo -e \"\\e[31m$line\\e[0m\" >&2; done); " +
						"EXIT_CODE=$?; " +
						"rm -rf \"%s\"; " +
						"echo; echo -e \"\\e[33m[Process finished - press Enter to close tab]\\e[0m\"; " +
						"read _unused_; " +
						"exit $EXIT_CODE",
				timeStr,
				command.replace("'", "'\\''"),
				sessionDir.getAbsolutePath()
		);

		executeInTerminal(activity, wrappedCommand, sessionDir.getAbsolutePath(), fileName.toLowerCase(), sessionDir.getAbsolutePath(), activity.viewPager.getCurrentItem() + 1);
	}

	/**
	 * Copies a file from a Uri to a target internal file.
	 */
	private static boolean copyUriToInternal(Context context, Uri uri, File dest) {
		try (InputStream in = context.getContentResolver().openInputStream(uri);
		     OutputStream out = new FileOutputStream(dest)) {
			if (in == null) return false;
			byte[] buf = new byte[8192];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error copying file to internal storage", e);
			return false;
		}
	}

	/**
	 * Cleans up any remaining execution directories. Call this on app start.
	 */
	public static void clearAllExecCache(Context context) {
		File baseExecDir = new File(context.getFilesDir(), EXEC_DIR_NAME);
		cleanupDirectory(baseExecDir);
	}

	private static void cleanupDirectory(File dir) {
		if (dir.isDirectory()) {
			File[] children = dir.listFiles();
			if (children != null) {
				for (File child : children) {
					cleanupDirectory(child);
				}
			}
		}
		dir.delete();
	}

	/**
	 * Checks if the given URI has an extension that allows it to be run.
	 */
	public static boolean extensionAllowsRun(Context context, Uri fileUri) {
		String last = fileUri.getLastPathSegment();
		if (last == null) return false;

		String extension = "";
		int i = last.lastIndexOf('.');
		if (i >= 0) {
			extension = last.substring(i);
		}

		if (extension.isEmpty()) return false;

		CommandFetcher fetcher = new CommandFetcher(context);
		if (fetcher.isExtensionSupported(extension)) {
			return true;
		}

		String lower = extension.toLowerCase();
		return lower.equals(".html") || lower.equals(".xml");
	}

	private static String resolveExecutionCommandFallback(String absoluteFilePath, String fileName) {
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

	private static void executeInTerminal(MainActivity activity, String command, String cwd, String labelName, String sessionDirPath, int insertIndex) {
		try {
			Uri compileUri = Uri.parse("app://com.cs.ide/compile")
					.buildUpon()
					.appendQueryParameter("command", command)
					.appendQueryParameter("cwd", cwd)
					.appendQueryParameter("session_dir", sessionDirPath)
					.appendQueryParameter("timestamp", String.valueOf(System.currentTimeMillis()))
					.build();

			int pos = MainActivity.viewPagerAdapter.insertTab(insertIndex, compileUri, activity.getString(R.string.run_prefix, labelName), true);
			activity.viewPager.setCurrentItem(pos, true);
		} catch (Exception e) {
			Log.e(TAG, "Failed to execute in terminal", e);
			Toast.makeText(activity, "Failed to start execution: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
}
