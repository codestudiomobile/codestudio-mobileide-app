package com.csmide.app.execution;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import com.csmide.R;
import com.csmide.app.activities.MainActivity;
import com.csmide.app.models.FileItem;
import com.csmide.app.utils.FileUtils;

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

		new Thread(() -> {
			final String fileName = (item.displayName != null) ? item.displayName : FileUtils.getFileName(activity, item.uri);
			String mimeType = activity.getMimeType(item.uri);
			boolean isHtml = (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("application/xhtml+xml")));
			if (!isHtml) {
				String fileNameLower = fileName.toLowerCase();
				if (fileNameLower.endsWith(".html") || fileNameLower.endsWith(".htm")) {
					isHtml = true;
				}
			}

			if (isHtml) {
				String absolutePath = FileUtils.getAbsolutePathFromUri(activity, item.uri);
				if (absolutePath != null && new File(absolutePath).exists()) {
					activity.runOnUiThread(() -> openHtmlInBrowser(activity, Uri.fromFile(new File(absolutePath))));
				} else {
					runHtmlWithCache(activity, item, fileName);
				}
				return;
			}

			String absolutePath = FileUtils.getAbsolutePathFromUri(activity, item.uri);

			// Direct Filesystem Execution: Optimized path for local files
			if (absolutePath != null) {
				File file = new File(absolutePath);
				if (file.exists()) {
					runDirectly(activity, absolutePath, fileName);
					return;
				}
			}

			// Internal Cache Execution: Fallback for SAF-based files
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
			executeInTerminal(activity, wrappedCommand, cwd, fileName, sessionDirPath, activity.viewPager.getCurrentItem() + 1, Uri.fromFile(new File(absolutePath)));
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
			executeInTerminal(activity, wrappedCommand, sessionDir.getAbsolutePath(), fileName, sessionDir.getAbsolutePath(), activity.viewPager.getCurrentItem() + 1, item.uri);
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

		// 3. Run the command. We avoid complex redirections for stderr to prevent buffering issues
		// that can bunch up interactive prompts (especially in languages like Python).
		sb.append(String.format("%s; ", resolvedCommand));

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
		String name = FileUtils.getFileName(context, fileUri);
		if (name == null) return false;

		String extension = "";
		int i = name.lastIndexOf('.');
		if (i >= 0) {
			extension = name.substring(i);
		}

		if (extension.isEmpty()) return false;

		String lower = extension.toLowerCase();
		if (lower.equals(".html") || lower.equals(".htm") || lower.equals(".xml") || lower.equals(".class") || lower.equals(".pyc")) {
			return true;
		}

		CommandFetcher fetcher = new CommandFetcher(context);
		return fetcher.isExtensionSupported(extension);
	}

	private static String resolveExecutionCommandFallback(String absoluteFilePath, String fileName, String internalBinPath) {
		String lowerFileName = fileName.toLowerCase();
		String fileNameWithoutExt = fileName.replaceAll("\\.[^.]+$", "");

		// Use single-quoted relative paths for maximum shell safety.
		// We rely on 'cd' to the parent directory which is handled in wrapCommand.
		String qFile = "'" + fileName.replace("'", "'\\''") + "'";
		String qFileNameWithoutExt = "'" + fileNameWithoutExt.replace("'", "'\\''") + "'";

		// If internalBinPath is provided, we use it for the executable (usually a cache path without spaces)
		String output = (internalBinPath != null) ? internalBinPath : fileNameWithoutExt;
		String qOutput = (internalBinPath != null) ? "'" + internalBinPath.replace("'", "'\\''") + "'" : qFileNameWithoutExt;

		if (lowerFileName.endsWith(".c")) {
			return String.format("gcc %s -o %s", qFile, qOutput);
		} else if (lowerFileName.endsWith(".cpp")) {
			return String.format("g++ %s -o %s", qFile, qOutput);
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
		} else if (lowerFileName.endsWith(".cs")) {
			return String.format("mcs %s && mono %s.exe", qFile, qFileNameWithoutExt);
		}
		return null;
	}

	private static void runHtmlWithCache(MainActivity activity, FileItem item, String fileName) {
		new Thread(() -> {
			File baseExecDir = activity.getExternalCacheDir();
			if (baseExecDir == null) baseExecDir = activity.getCacheDir();

			File htmlDir = new File(baseExecDir, "html_preview");
			File sessionDir = new File(htmlDir, "session_" + System.nanoTime());

			if (!sessionDir.exists() && !sessionDir.mkdirs()) {
				activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create HTML preview environment.", Toast.LENGTH_SHORT).show());
				return;
			}

			boolean success = false;
			// If we have a tree URI, try to copy siblings for "multiple files support"
			if ("content".equals(item.uri.getScheme()) && DocumentsContract.isTreeUri(item.uri)) {
				success = copySiblingsToInternal(activity, item.uri, sessionDir, fileName);
			} else {
				File targetFile = new File(sessionDir, fileName);
				success = copyUriToInternal(activity, item.uri, targetFile);
			}

			if (success) {
				File targetFile = new File(sessionDir, fileName);
				activity.runOnUiThread(() -> openHtmlInBrowser(activity, Uri.fromFile(targetFile)));
			} else {
				activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to prepare HTML preview.", Toast.LENGTH_SHORT).show());
			}
		}).start();
	}

	private static boolean copySiblingsToInternal(Context context, Uri fileUri, File destDir, String fileName) {
		try {
			// If we can't easily find siblings via SAF, just copy the main file.
			// However, if we're in the same folder as currentDirectoryUri, we can list siblings.
			if (MainActivity.currentDirectoryUri != null && "content".equals(MainActivity.currentDirectoryUri.getScheme())) {
				// This is a simplification: assuming the file is a direct child of currentDirectoryUri
				// Real implementation would need to verify the parent relationship.
				String parentId = DocumentsContract.getTreeDocumentId(MainActivity.currentDirectoryUri);
				Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(MainActivity.currentDirectoryUri, parentId);
				try (android.database.Cursor cursor = context.getContentResolver().query(childrenUri,
						new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
								DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
					if (cursor != null && cursor.moveToFirst()) {
						do {
							String id = cursor.getString(0);
							String name = cursor.getString(1);
							Uri childUri = DocumentsContract.buildDocumentUriUsingTree(MainActivity.currentDirectoryUri, id);
							copyUriToInternal(context, childUri, new File(destDir, name));
						} while (cursor.moveToNext());
						return true;
					}
				}
			}

			File targetFile = new File(destDir, fileName);
			return copyUriToInternal(context, fileUri, targetFile);
		} catch (Exception e) {
			Log.e(TAG, "Error copying siblings", e);
			return false;
		}
	}

	private static void openHtmlInBrowser(Context context, Uri fileUri) {
		if (context instanceof MainActivity activity) {
			String fileName = FileUtils.getFileName(context, fileUri);
			String tabTitle = activity.getString(R.string.run_prefix, fileName);

			Uri previewUri = Uri.parse(com.csmide.app.adapters.ViewPagerAdapter.HTML_PREVIEW_URI_PREFIX)
					.buildUpon()
					.appendQueryParameter("url", fileUri.toString())
					.appendQueryParameter("source_uri", fileUri.toString()) // Include for edit button
					.build();

			int existingPos = MainActivity.viewPagerAdapter.findTabPositionByName(tabTitle);
			if (existingPos != -1) {
				Uri oldUri = MainActivity.viewPagerAdapter.getFileUris().get(existingPos);
				MainActivity.viewPagerAdapter.updateTabInfo(oldUri, previewUri, tabTitle);
				activity.viewPager.setCurrentItem(existingPos, false);
			} else {
				int insertIndex = activity.viewPager.getCurrentItem() + 1;
				int pos = MainActivity.viewPagerAdapter.insertTab(insertIndex, previewUri, tabTitle, true);
				activity.viewPager.setCurrentItem(pos, false);
			}
		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(fileUri, "text/html");
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
			try {
				context.startActivity(intent);
			} catch (Exception e) {
				Log.e(TAG, "Failed to open HTML", e);
				Toast.makeText(context, R.string.no_app_found_to_view, Toast.LENGTH_LONG).show();
			}
		}
	}

	private static void executeInTerminal(MainActivity activity, String command, String cwd, String labelName, String sessionDirPath, int insertIndex, Uri sourceUri) {
		try {
			String tabTitle = activity.getString(R.string.run_prefix, labelName);

			Uri compileUri = Uri.parse("app://com.csmide/compile")
					.buildUpon()
					.appendQueryParameter("command", command)
					.appendQueryParameter("cwd", cwd)
					.appendQueryParameter("session_dir", sessionDirPath)
					.appendQueryParameter("label", labelName)
					.appendQueryParameter("source_uri", sourceUri != null ? sourceUri.toString() : null)
					.appendQueryParameter("timestamp", String.valueOf(System.nanoTime())) // Force uniqueness to re-run
					.build();

			// Check if a run tab for this file already exists and replace it to stop existing process
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
