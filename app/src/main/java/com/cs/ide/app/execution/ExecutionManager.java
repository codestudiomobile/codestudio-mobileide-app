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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ExecutionManager is responsible for handling the execution of various file types
 * within the IDE. It prioritizes direct filesystem execution for speed and falls back
 * to internal cache for SAF-based files.
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

		final String fileName = (item.displayName != null) ? item.displayName : FileUtils.getFileName(activity, item.uri);
		String mimeType = activity.getMimeType(item.uri);
		if (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("application/xhtml+xml"))) {
			openHtmlInBrowser(activity, item.uri);
			return;
		}

		// Perform everything in background to keep UI smooth
		new Thread(() -> {
			String absolutePath = FileUtils.getAbsolutePathFromUri(activity, item.uri);

			// 1. HIGH-SPEED PATH: Direct Filesystem Execution
			// If we have a real path, we can run it "instantly" without copying.
			if (absolutePath != null) {
				File file = new File(absolutePath);
				if (file.exists()) {
					runDirectly(activity, absolutePath, fileName);
					return;
				}
			}

			// 2. FALLBACK PATH: Internal Cache Execution (for SAF)
			runWithCache(activity, item, fileName);
		}).start();
	}

	private static void runDirectly(MainActivity activity, String absolutePath, String fileName) {
		CommandFetcher fetcher = new CommandFetcher(activity);

		// Prepare internal path for the output binary in the app's files directory (more stable than cache)
		File baseExecDir = new File(activity.getFilesDir(), EXEC_DIR_NAME);
		String sessionId = "direct_session_" + System.nanoTime();
		File sessionDir = new File(baseExecDir, sessionId);

		String fileNameWithoutExt = fileName.replaceAll("\\.[^.]+$", "");
		String internalBinPath = new File(sessionDir, fileNameWithoutExt).getAbsolutePath();

		String resolvedCommand = fetcher.resolveCommandForFile(absolutePath, internalBinPath);

		if (resolvedCommand == null) {
			resolvedCommand = resolveExecutionCommandFallback(absolutePath, fileName, internalBinPath);
		}

		boolean needsSessionDir = false;

		// If still null, it might be an executable binary or script that needs execution permission
		if (resolvedCommand == null) {
			File sourceFile = new File(absolutePath);
			if (sourceFile.exists() && sourceFile.isFile()) {
				needsSessionDir = true;
				if (!sessionDir.exists() && !sessionDir.mkdirs()) {
					activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create execution environment.", Toast.LENGTH_SHORT).show());
					return;
				}
				File targetExec = new File(sessionDir, fileName);
				if (copyFile(sourceFile, targetExec)) {
					targetExec.setExecutable(true);
					resolvedCommand = String.format("chmod +x '%1$s' && '%1$s'", targetExec.getAbsolutePath().replace("'", "'\\''"));
				}
			}
		} else if (resolvedCommand.contains(sessionId)) {
			needsSessionDir = true;
			if (!sessionDir.exists() && !sessionDir.mkdirs()) {
				activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create execution environment.", Toast.LENGTH_SHORT).show());
				return;
			}
		}

		if (resolvedCommand == null) {
			activity.runOnUiThread(() -> Toast.makeText(activity, R.string.unsupported_file_type, Toast.LENGTH_SHORT).show());
			return;
		}

		String cwd = new File(absolutePath).getParent();
		final String sessionDirPath = needsSessionDir ? sessionDir.getAbsolutePath() : null;
		String wrappedCommand = wrapCommand(resolvedCommand, sessionDirPath, cwd);

		activity.runOnUiThread(() -> {
			executeInTerminal(activity, wrappedCommand, cwd, fileName.toLowerCase(), sessionDirPath, activity.viewPager.getCurrentItem() + 1);
		});
	}

	private static void runWithCache(MainActivity activity, FileItem item, String fileName) {
		// Prepare unique internal execution directory in FILES folder
		File baseExecDir = new File(activity.getFilesDir(), EXEC_DIR_NAME);
		File sessionDir = new File(baseExecDir, "cache_session_" + System.nanoTime());

		if (!sessionDir.exists() && !sessionDir.mkdirs()) {
			activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create execution environment.", Toast.LENGTH_SHORT).show());
			return;
		}

		File targetFile = new File(sessionDir, fileName);

		// Copy only the current file for execution by default
		boolean preparationSuccess = copyUriToInternal(activity, item.uri, targetFile);

		if (!preparationSuccess) {
			activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to prepare file(s) for execution.", Toast.LENGTH_SHORT).show());
			cleanupDirectory(sessionDir);
			return;
		}

		targetFile.setExecutable(true);

		CommandFetcher fetcher = new CommandFetcher(activity);
		String internalPath = targetFile.getAbsolutePath();
		String fileNameWithoutExt = fileName.replaceAll("\\.[^.]+$", "");
		String internalBinPath = new File(sessionDir, fileNameWithoutExt + ".bin").getAbsolutePath();

		String resolvedCommand = fetcher.resolveCommandForFile(internalPath, internalBinPath);

		if (resolvedCommand == null) {
			resolvedCommand = resolveExecutionCommandFallback(internalPath, fileName, internalBinPath);
		}

		if (resolvedCommand == null) {
			// If it's not a known source type, try to run it directly from cache (as it might be an executable)
			resolvedCommand = String.format("chmod +x '%1$s' && '%1$s'", internalPath.replace("'", "'\\''"));
		}

		String wrappedCommand = wrapCommand(resolvedCommand, sessionDir.getAbsolutePath(), sessionDir.getAbsolutePath());

		activity.runOnUiThread(() -> {
			executeInTerminal(activity, wrappedCommand, sessionDir.getAbsolutePath(), fileName.toLowerCase(), sessionDir.getAbsolutePath(), activity.viewPager.getCurrentItem() + 1);
		});
	}

	private static String wrapCommand(String resolvedCommand, String sessionDirPath, String cwd) {
		StringBuilder sb = new StringBuilder();

		// 1. Basic environment setup
		sb.append("export PATH=$PREFIX/bin:$PATH; ");
		sb.append("export LD_LIBRARY_PATH=$PREFIX/lib; ");

		// 2. Critical: Navigate to the correct directory first
		if (cwd != null) {
			String escapedCwd = cwd.replace("'", "'\\''");
			sb.append(String.format("cd '%1$s' || { echo -e \"\\e[31mError: Directory not found: %1$s\\e[0m\"; exit 1; }; ", escapedCwd));
			sb.append(String.format("export HOME='%s'; ", escapedCwd));
		}

		// 3. Run the command and capture stderr in red
		// Using a subshell to ensure the exit code of the resolved command is captured correctly
		sb.append(String.format("( %s ) 2> >(while read line; do echo -e \"\\e[31m$line\\e[0m\" >&2; done); ", resolvedCommand));

		// 4. Cleanup and exit logic
		sb.append("EXIT_CODE=$?; ");
		if (sessionDirPath != null) {
			sb.append(String.format("rm -rf '%s'; ", sessionDirPath.replace("'", "'\\''")));
		}
		sb.append("echo; echo -e \"\\e[33m[Process finished with code $EXIT_CODE - press Enter to close tab]\\e[0m\"; ");
		sb.append("read -r _unused_; ");
		sb.append("exit $EXIT_CODE");
		return sb.toString();
	}

	private static boolean copyFile(File src, File dst) {
		try (InputStream in = new java.io.FileInputStream(src);
		     OutputStream out = new FileOutputStream(dst)) {
			byte[] buf = new byte[8192];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error copying file", e);
			return false;
		}
	}

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

	public static void clearAllExecCache(Context context) {
		File baseExecDirFiles = new File(context.getFilesDir(), EXEC_DIR_NAME);
		cleanupDirectory(baseExecDirFiles);

		File baseExecDirCache = new File(context.getCacheDir(), EXEC_DIR_NAME);
		cleanupDirectory(baseExecDirCache);
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
		return lower.equals(".html") || lower.equals(".xml") || lower.equals(".class") || lower.equals(".pyc");
	}

	private static String resolveExecutionCommandFallback(String absoluteFilePath, String fileName, String internalBinPath) {
		String lowerFileName = fileName.toLowerCase();
		String fileNameWithoutExt = fileName.replaceAll("\\.[^.]+$", "");

		// Use single-quoted relative paths for maximum shell safety.
		// We rely on 'cd' to the parent directory which is handled in wrapCommand.
		String qFile = "'" + fileName.replace("'", "'\\''") + "'";
		String qFileNameWithoutExt = "'" + fileNameWithoutExt.replace("'", "'\\''") + "'";

		// If internalBinPath is provided, we use it for the executable (usually a cache path without spaces)
		String output = (internalBinPath != null) ? internalBinPath : "./" + fileNameWithoutExt;
		String qOutput = (internalBinPath != null) ? "'" + internalBinPath.replace("'", "'\\''") + "'" : "./" + qFileNameWithoutExt;

		// For compiled languages, we still need to handle the executable prefix
		String runPrefix = (internalBinPath != null) ? "" : "./";
		String runCmd = (internalBinPath != null) ? qOutput : runPrefix + qOutput;

		if (lowerFileName.endsWith(".c")) {
			return String.format("clang %s -o %s && %s", qFile, qOutput, runCmd);
		} else if (lowerFileName.endsWith(".cpp")) {
			return String.format("clang++ %s -o %s && %s", qFile, qOutput, runCmd);
		} else if (lowerFileName.endsWith(".py") || lowerFileName.endsWith(".pyc")) {
			// -u for unbuffered output to show results immediately
			return String.format("python -u %s", qFile);
		} else if (lowerFileName.endsWith(".js") || lowerFileName.endsWith(".ts")) {
			return String.format("node %s", qFile);
		} else if (lowerFileName.endsWith(".java")) {
			// For Java, we run from the current directory where the class file was created
			return String.format("javac %s && java %s", qFile, qFileNameWithoutExt);
		} else if (lowerFileName.endsWith(".class")) {
			// For pre-compiled Java classes
			return String.format("java %s", qFileNameWithoutExt);
		} else if (lowerFileName.endsWith(".sh")) {
			return String.format("bash %s", qFile);
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
			String tabTitle = activity.getString(R.string.run_prefix, labelName);

			Uri compileUri = Uri.parse("app://com.cs.ide/compile")
					.buildUpon()
					.appendQueryParameter("command", command)
					.appendQueryParameter("cwd", cwd)
					.appendQueryParameter("session_dir", sessionDirPath)
					.appendQueryParameter("label", labelName)
					.build();

			// Check if a run tab for this file already exists and reuse it
			int existingPos = MainActivity.viewPagerAdapter.findTabPositionByName(tabTitle);
			if (existingPos != -1) {
				Uri oldUri = MainActivity.viewPagerAdapter.getFileUris().get(existingPos);
				MainActivity.viewPagerAdapter.updateTabInfo(oldUri, compileUri, tabTitle);
				activity.viewPager.setCurrentItem(existingPos, false);
			} else {
				int pos = MainActivity.viewPagerAdapter.insertTab(insertIndex, compileUri, tabTitle, true);
				activity.viewPager.setCurrentItem(pos, false);
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to execute in terminal", e);
			Toast.makeText(activity, "Failed to start execution: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
}
