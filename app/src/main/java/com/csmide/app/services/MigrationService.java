package com.csmide.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.system.Os;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.csmide.R;
import com.csmide.app.activities.MigrationActivity;
import com.csmide.termux.app.TermuxPatcher;
import com.csmide.termux.shared.logger.Logger;
import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * MigrationService handles the Termux backup import process in a foreground service.
 * This ensures the process continues even if the activity is closed.
 */
public class MigrationService extends Service {

	public static final String ACTION_START_IMPORT = "com.csmide.action.START_IMPORT";
	public static final String ACTION_GET_STATUS = "com.csmide.action.GET_STATUS";
	public static final String EXTRA_BACKUP_URI = "backup_uri";

	public static final String ACTION_PROGRESS_UPDATE = "com.csmide.action.MIGRATION_PROGRESS";
	public static final String EXTRA_STATUS_TEXT = "status_text";
	public static final String EXTRA_IS_COMPLETE = "is_complete";
	public static final String EXTRA_IS_SUCCESS = "is_success";

	private static final String LOG_TAG = "MigrationService";
	private static final String CHANNEL_ID = "MigrationServiceChannel";
	private static final int NOTIFICATION_ID = 2001;
	private static final String BACKUP_FILE_NAME = "termux-backup.tar.gz";
	private static final String HOME_OLD = "home.old";
	private static final String USR_OLD = "usr.old";

	private boolean isRunning = false;
	private String lastStatusText = "";

	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_START_IMPORT.equals(action)) {
				Uri uri = intent.getParcelableExtra(EXTRA_BACKUP_URI);
				if (uri != null && !isRunning) {
					startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.msg_importing), true));
					startImportTask(uri);
				}
			} else if (ACTION_GET_STATUS.equals(action)) {
				if (isRunning) {
					updateStatus(lastStatusText, false, false);
				}
			}
		}
		return START_NOT_STICKY;
	}

	private void startImportTask(Uri uri) {
		isRunning = true;
		new Thread(() -> {
			File homeDir = TermuxConstants.TERMUX_HOME_DIR;
			File usrDir = TermuxConstants.TERMUX_PREFIX_DIR;
			File homeOld = new File(homeDir.getParent(), HOME_OLD);
			File usrOld = new File(usrDir.getParent(), USR_OLD);
			boolean homeMovedInternal = false;
			boolean usrMovedInternal = false;

			try {
				try {
					getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
				} catch (SecurityException ignored) {
				}

				updateStatus(getString(R.string.msg_importing), false, false);

				// 1. Copy URI to a temporary file in app storage
				File tempFile = new File(getCacheDir(), BACKUP_FILE_NAME);
				try (InputStream in = getContentResolver().openInputStream(uri);
				     OutputStream out = new FileOutputStream(tempFile)) {
					byte[] buffer = new byte[8192];
					int read;
					if (in != null) {
						while ((read = in.read(buffer)) != -1) {
							out.write(buffer, 0, read);
						}
					}
				}

				// 2. Find tar before moving directories
				String tarPath = null;
				File termuxTar = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tar");
				if (termuxTar.exists()) {
					tarPath = termuxTar.getAbsolutePath();
				}

				// 3. Backup existing home and usr
				File homeBak = new File(homeDir.getParent(), HOME_OLD + ".bak");
				File usrBak = new File(usrDir.getParent(), USR_OLD + ".bak");
				if (homeOld.exists()) {
					deleteRecursive(homeBak);
					homeOld.renameTo(homeBak);
				}
				if (usrOld.exists()) {
					deleteRecursive(usrBak);
					usrOld.renameTo(usrBak);
				}

				if (homeDir.exists()) {
					homeMovedInternal = homeDir.renameTo(homeOld);
				}
				if (usrDir.exists()) {
					usrMovedInternal = usrDir.renameTo(usrOld);
				}

				if (!homeDir.exists()) homeDir.mkdirs();
				if (!usrDir.exists()) usrDir.mkdirs();

				if (tarPath != null && tarPath.startsWith(usrDir.getAbsolutePath())) {
					tarPath = usrOld.getAbsolutePath() + tarPath.substring(usrDir.getAbsolutePath().length());
				}

				if (tarPath == null || !new File(tarPath).exists()) {
					if (new File("/system/bin/tar").exists()) {
						tarPath = "/system/bin/tar";
					} else if (new File("/system/xbin/tar").exists()) {
						tarPath = "/system/xbin/tar";
					} else {
						throw new Exception(getString(R.string.msg_error_tar_not_found));
					}
				}

				// 4. Extract
				ProcessBuilder pb = new ProcessBuilder(
						tarPath, "-zxpf", tempFile.getAbsolutePath(), "-C", TermuxConstants.TERMUX_FILES_DIR_PATH
				);

				if (tarPath.startsWith(usrOld.getAbsolutePath())) {
					String binPath = usrOld.getAbsolutePath() + "/bin";
					String libPath = usrOld.getAbsolutePath() + "/lib";
					pb.environment().put("LD_LIBRARY_PATH", libPath);
					pb.environment().put("PATH", binPath + ":" + System.getenv("PATH"));
				}

				pb.redirectErrorStream(true);
				Process process = pb.start();

				StringBuilder tarOutput = new StringBuilder();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						tarOutput.append(line).append("\n");
					}
				}

				int exitCode = process.waitFor();

				if (exitCode == 0) {
					updateStatus("Optimizing environment and fixing permissions...", false, false);

					// 5. Fix permissions efficiently
					fixPermissions();

					// 6. Patch paths (com.termux -> com.csmide)
					TermuxPatcher.patchBootstrap(TermuxConstants.TERMUX_FILES_DIR);

					deleteRecursive(homeBak);
					deleteRecursive(usrBak);

					updateStatus(getString(R.string.msg_import_success), true, true);
				} else {
					throw new Exception("Tar exited with code " + exitCode + (tarOutput.length() > 0 ? ": " + tarOutput.toString().trim() : ""));
				}

			} catch (Exception e) {
				Logger.logStackTraceWithMessage(LOG_TAG, "Import failed", e);
				// ROLLBACK on failure
				if (homeMovedInternal) {
					deleteRecursive(homeDir);
					homeOld.renameTo(homeDir);
				}
				if (usrMovedInternal) {
					deleteRecursive(usrDir);
					usrOld.renameTo(usrDir);
				}
				updateStatus(getString(R.string.msg_import_failed, e.getMessage()), true, false);
			} finally {
				File tempFile = new File(getCacheDir(), BACKUP_FILE_NAME);
				if (tempFile.exists()) tempFile.delete();
				isRunning = false;
				stopForeground(false);
				stopSelf();
			}
		}).start();
	}

	private void updateStatus(String text, boolean isComplete, boolean isSuccess) {
		this.lastStatusText = text;
		updateNotification(text, !isComplete);
		Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
		intent.putExtra(EXTRA_STATUS_TEXT, text);
		intent.putExtra(EXTRA_IS_COMPLETE, isComplete);
		intent.putExtra(EXTRA_IS_SUCCESS, isSuccess);
		sendBroadcast(intent);
	}

	private void updateNotification(String text, boolean ongoing) {
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, buildNotification(text, ongoing));
		}
	}

	private Notification buildNotification(String text, boolean ongoing) {
		Intent notificationIntent = new Intent(this, MigrationActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

		return new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.title_migration))
				.setContentText(text)
				.setSmallIcon(R.drawable.ic_foreground)
				.setOngoing(ongoing)
				.setContentIntent(pendingIntent)
				.setAutoCancel(!ongoing)
				.setOnlyAlertOnce(true)
				.build();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel serviceChannel = new NotificationChannel(
					CHANNEL_ID,
					"Migration Service Channel",
					NotificationManager.IMPORTANCE_LOW
			);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				manager.createNotificationChannel(serviceChannel);
			}
		}
	}

	private void fixPermissions() {
		fixPermissionsRecursive(TermuxConstants.TERMUX_FILES_DIR);
	}

	private void fixPermissionsRecursive(File file) {
		// CRITICAL: Safety check to ensure we never touch files outside internal app storage.
		if (!isSafePath(file)) {
			Logger.logWarn(LOG_TAG, "Skipping permission fix for unsafe path: " + file.getAbsolutePath());
			return;
		}

		try {
			// Never follow symbolic links for permission changes.
			if (Files.isSymbolicLink(file.toPath())) return;

			if (file.isDirectory()) {
				Os.chmod(file.getAbsolutePath(), 0700);
				File[] children = file.listFiles();
				if (children != null) {
					for (File child : children) {
						fixPermissionsRecursive(child);
					}
				}
			} else {
				int mode = 0600;
				String path = file.getAbsolutePath();
				if (path.contains("/bin/") || path.contains("/lib/apt/methods") || path.contains("/libexec/")) {
					mode = 0700;
				}
				Os.chmod(file.getAbsolutePath(), mode);
			}
		} catch (Exception e) {
			Logger.logStackTraceWithMessage(LOG_TAG, "Failed to fix permissions for " + file.getAbsolutePath(), e);
		}
	}

	/**
	 * Deletes a file or directory recursively.
	 * EXTREME CAUTION: This method is guarded to NEVER follow symbolic links
	 * and NEVER touch paths outside the app's internal private storage.
	 */
	private void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory == null || !fileOrDirectory.exists()) return;

		// 1. Path Safety Guard: Block any operation outside of /data/data/com.csmide/
		if (!isSafePath(fileOrDirectory)) {
			Logger.logError(LOG_TAG, "BLOCKED attempt to delete unsafe path: " + fileOrDirectory.getAbsolutePath());
			return;
		}

		if (fileOrDirectory.isDirectory()) {
			try {
				// 2. Symlink Guard: If this is a link, delete the link only. DO NOT follow it.
				if (Files.isSymbolicLink(fileOrDirectory.toPath())) {
					fileOrDirectory.delete();
					return;
				}
			} catch (Exception e) {
				Logger.logWarn(LOG_TAG, "Error checking symlink for " + fileOrDirectory.getName());
			}

			File[] children = fileOrDirectory.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}

		// Final check before actual deletion
		if (isSafePath(fileOrDirectory)) {
			fileOrDirectory.delete();
		}
	}

	/**
	 * Strict Sandbox Guard.
	 * Returns true ONLY if the file resides within the application's private internal storage.
	 * This prevents accidental modification or deletion of user's SD card/external files via symlinks.
	 */
	private boolean isSafePath(File file) {
		if (file == null) return false;
		try {
			String canonicalPath = file.getCanonicalPath();
			File filesDir = getFilesDir();
			if (filesDir == null) return false;
			File parentDir = filesDir.getParentFile();
			if (parentDir == null) return false;
			String internalRoot = parentDir.getCanonicalPath(); // This is /data/data/com.csmide
			return canonicalPath.startsWith(internalRoot);
		} catch (Exception e) {
			return false;
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
