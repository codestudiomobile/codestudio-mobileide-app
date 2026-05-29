package com.cs.ide.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.cs.ide.R;
import com.cs.ide.app.activities.ManageLanguagesActivity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LanguageManagerService handles queuing and background installation of language packages.
 * It supports retries on connection timeouts and provides cumulative progress notifications.
 */
public class LanguageManagerService extends Service {
	public static final String ACTION_INSTALL_PACKAGE = "com.cs.ide.action.INSTALL_PACKAGE";
	public static final String EXTRA_PACKAGE_KEY = "package_key";
	public static final String EXTRA_PACKAGE_NAME = "package_name";
	public static final String EXTRA_COMMAND = "install_command";
	public static final String ACTION_PROGRESS_UPDATE = "com.cs.ide.action.PROGRESS_UPDATE";
	public static final String EXTRA_PROGRESS = "progress";
	public static final String EXTRA_STATUS_TEXT = "status_text";
	public static final String ACTION_REQUEST_CONFIRM = "com.cs.ide.action.PACKAGE_REQUEST_CONFIRM";
	public static final String ACTION_CONFIRM = "com.cs.ide.action.PACKAGE_CONFIRM";
	public static final String ACTION_CANCEL = "com.cs.ide.action.PACKAGE_CANCEL";
	public static final String EXTRA_DOWNLOAD_SIZE = "download_size";
	public static final String EXTRA_INSTALL_SIZE = "install_size";
	private static final String TAG = "LanguageManagerService";
	private static final String CHANNEL_ID = "LanguageManagerChannel";
	private static final int NOTIFICATION_ID = 2001;

	private final Queue<InstallTask> installQueue = new LinkedList<>();
	private final List<String> completedPackages = new ArrayList<>();
	private boolean isProcessing = false;
	private InstallTask currentTask = null;
	private Process currentProcess = null;
	private java.io.BufferedWriter processWriter = null;
	private boolean awaitingConfirmation = false;

	private final android.content.BroadcastReceiver controlReceiver = new android.content.BroadcastReceiver() {
		@Override
		public void onReceive(android.content.Context context, Intent intent) {
			if (ACTION_CONFIRM.equals(intent.getAction())) {
				confirmTask();
			} else if (ACTION_CANCEL.equals(intent.getAction())) {
				cancelTask();
			}
		}
	};

	private int currentProgress = 0;
	private String currentStatusText = "Idle";

	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
		android.content.IntentFilter filter = new android.content.IntentFilter();
		filter.addAction(ACTION_CONFIRM);
		filter.addAction(ACTION_CANCEL);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(controlReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(controlReceiver, filter);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(controlReceiver);
	}

	private void confirmTask() {
		if (awaitingConfirmation && processWriter != null) {
			new Thread(() -> {
				try {
					processWriter.write("y\n");
					processWriter.flush();
					awaitingConfirmation = false;
				} catch (java.io.IOException e) {
					Log.e(TAG, "Failed to send confirmation", e);
				}
			}).start();
		}
	}

	private void cancelTask() {
		if (currentProcess != null) {
			currentProcess.destroy();
		}
		awaitingConfirmation = false;
		// The thread in runInstallation will exit and call processNextInQueue
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_INSTALL_PACKAGE.equals(intent.getAction())) {
			String key = intent.getStringExtra(EXTRA_PACKAGE_KEY);
			String name = intent.getStringExtra(EXTRA_PACKAGE_NAME);
			String command = intent.getStringExtra(EXTRA_COMMAND);

			if (key != null && name != null && command != null) {
				// If it's the current task, just broadcast progress
				if (currentTask != null && currentTask.key.equals(key)) {
					broadcastProgress(currentProgress, currentStatusText);
					return START_STICKY;
				}

				// If it's already in the queue, ignore
				synchronized (installQueue) {
					for (InstallTask task : installQueue) {
						if (task.key.equals(key)) {
							return START_STICKY;
						}
					}
					installQueue.offer(new InstallTask(key, name, command));
				}

				if (!isProcessing) {
					processNextInQueue();
				} else {
					// Update notification with queue count, but keep showing current progress
					updateNotification(currentStatusText, currentProgress);
					broadcastProgress(-1, getString(R.string.msg_queued_packages, installQueue.size()));
				}
			}
		}
		return START_STICKY;
	}

	private void processNextInQueue() {
		currentTask = installQueue.poll();
		if (currentTask == null) {
			isProcessing = false;
			stopForeground(true);
			stopSelf();
			return;
		}

		isProcessing = true;
		runInstallation(currentTask);
	}

	private void runInstallation(InstallTask task) {
		new Thread(() -> {
			boolean success = false;
			boolean retryNeeded = false;
			String finalErrorMsg = "";
			awaitingConfirmation = false;

			try {
				String prefix = "/data/data/" + getPackageName() + "/files/usr";

				// Remove -y if it's a pkg install command to allow interactive size check
				String cmd = task.command;
				if (cmd.startsWith("pkg install") && cmd.contains("-y")) {
					cmd = cmd.replace("-y", "");
				}

				ProcessBuilder pb = new ProcessBuilder(
						prefix + "/bin/sh",
						"-c",
						cmd
				);

				pb.environment().put("PREFIX", prefix);
				pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
				pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));
				pb.environment().put("HOME", "/data/data/" + getPackageName() + "/files/home");
				pb.environment().put("TERM", "xterm");
				pb.redirectErrorStream(true);

				currentProcess = pb.start();
				processWriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(currentProcess.getOutputStream()));

				java.io.InputStream in = currentProcess.getInputStream();
				byte[] buffer = new byte[8192];
				int n;
				StringBuilder outputAccumulator = new StringBuilder();
				LinkedList<String> lastLines = new LinkedList<>();
				Pattern percentPattern = Pattern.compile("(\\d+)%");

				String actionPrefix = (task.command.contains("uninstall") || task.command.contains("rm -rf")) ? "Uninstalling " : "Installing ";
				currentStatusText = actionPrefix + task.name + "…";
				currentProgress = 0;
				broadcastProgress(0, currentStatusText);
				updateNotification(currentStatusText, 0);

				long lastProgressTime = System.currentTimeMillis();
				int lastPercent = -1;

				while ((n = in.read(buffer)) != -1) {
					String chunk = new String(buffer, 0, n);
					outputAccumulator.append(chunk);

					// Process complete lines for logging and line-based detection
					int newlineIdx;
					while ((newlineIdx = outputAccumulator.indexOf("\n")) != -1) {
						String line = outputAccumulator.substring(0, newlineIdx).trim();
						outputAccumulator.delete(0, newlineIdx + 1);

						if (line.isEmpty()) continue;

						// Strip ANSI escape codes for cleaner detection
						String cleanLine = line.replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
						Log.d(TAG, "[" + task.name + "] " + cleanLine);
						lastLines.add(cleanLine);
						if (lastLines.size() > 15) lastLines.removeFirst();

						// Package Not Found Detection
						if (cleanLine.contains("Unable to locate package") || cleanLine.contains("E: package not found") || cleanLine.contains("package not available")) {
							finalErrorMsg = "Package not found in repositories";
							retryNeeded = true;
							success = false;
						}

						// Connection/Sync issues detection
						String lowerLine = cleanLine.toLowerCase();
						if (lowerLine.contains("connection timed out") ||
								lowerLine.contains("failed to fetch") ||
								lowerLine.contains("unexpected size") ||
								lowerLine.contains("hash sum mismatch") ||
								lowerLine.contains("temporary failure resolving")) {
							retryNeeded = true;
							finalErrorMsg = cleanLine;
						}

						// Progress detection
						Matcher pm = percentPattern.matcher(cleanLine);
						if (pm.find()) {
							int percent = Integer.parseInt(pm.group(1));
							if (percent != lastPercent) {
								lastPercent = percent;
								lastProgressTime = System.currentTimeMillis();
								String status = actionPrefix + task.name + " (" + percent + "%)";
								currentProgress = percent;
								currentStatusText = status;
								broadcastProgress(percent, status);
								updateNotification(status, percent);
							}
						}

						if (line.contains("Processing triggers") || line.contains("Setting up")) {
							if (currentProgress < 95) {
								currentProgress = 95;
								currentStatusText = "Finalizing " + task.name + "…";
								broadcastProgress(95, currentStatusText);
								updateNotification(currentStatusText, 95);
							}
						}
					}

					// Prompt Detection (check the accumulator even if no newline)
					String currentOutput = outputAccumulator.toString().replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
					if (currentOutput.contains("Do you want to continue? [Y/n]")) {
						String downloadSize = "unknown";
						String installSize = "unknown";

						StringBuilder fullContext = new StringBuilder();
						for (String l : lastLines) fullContext.append(l).append("\n");
						fullContext.append(currentOutput);
						String context = fullContext.toString();

						Matcher m = Pattern.compile("Need to get ([\\d.,]+\\s*[kMG]B)").matcher(context);
						if (m.find()) downloadSize = m.group(1);

						m = Pattern.compile("After this operation, ([\\d.,]+\\s*[kMG]B)").matcher(context);
						if (m.find()) installSize = m.group(1);

						awaitingConfirmation = true;
						broadcastRequestConfirm(downloadSize, installSize);
						updateNotification("Confirm installation (" + downloadSize + ")", 0);
						// Clear the prompt from accumulator to avoid multiple broadcasts
						outputAccumulator.setLength(0);
					}

					if (System.currentTimeMillis() - lastProgressTime > 15000 && lastPercent != -1 && lastPercent < 100) {
						updateNotification(getString(R.string.msg_download_slow, task.name), lastPercent);
					}
				}

				int exitCode = currentProcess.waitFor();
				success = (exitCode == 0);
				if (!success && finalErrorMsg.isEmpty()) {
					finalErrorMsg = "Exit code " + exitCode;
					if (exitCode == 100) {
						retryNeeded = true;
					}
				}

			} catch (Exception e) {
				Log.e(TAG, "Installation failed for " + task.name, e);
				finalErrorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
			} finally {
				currentProcess = null;
				processWriter = null;
			}

			if (success) {
				completedPackages.add(task.name);
				String successMsg = (task.command.contains("uninstall") || task.command.contains("rm -rf")) ?
						getString(R.string.msg_package_uninstalled_success, task.name) :
						getString(R.string.msg_package_installed_success, task.name);
				broadcastProgress(100, successMsg);
				processNextInQueue();
			} else if (retryNeeded && task.retryCount < 2) {
				task.retryCount++;
				// On retry, prepend cleanup and update
				if (task.command.startsWith("pkg install")) {
					String prefix = "/data/data/" + getPackageName() + "/files/usr";
					// If it was a hash mismatch or size error, clean the lists first
					if (finalErrorMsg.toLowerCase().contains("size") || finalErrorMsg.toLowerCase().contains("hash")) {
						task.command = "rm -rf " + prefix + "/var/lib/apt/lists/* && pkg update -y && " + task.command;
					} else {
						task.command = "pkg update -y && " + task.command;
					}
				}
				broadcastProgress(-1, "Refreshing mirrors and retrying " + task.name + "… (" + task.retryCount + "/2)");
				updateNotification("Retrying " + task.name, 0);
				try {
					Thread.sleep(3000);
				} catch (InterruptedException ignored) {
				}
				runInstallation(task);
			} else {
				String errorPrefix = retryNeeded ? "Internet or Server error: " : "Error: ";
				broadcastProgress(-1, errorPrefix + task.name + " (" + finalErrorMsg + ")");
				updateNotification("Installation failed: " + task.name, 0);
				processNextInQueue();
			}
		}).start();
	}

	private void broadcastRequestConfirm(String downloadSize, String installSize) {
		Intent intent = new Intent(ACTION_REQUEST_CONFIRM);
		intent.putExtra(EXTRA_DOWNLOAD_SIZE, downloadSize);
		intent.putExtra(EXTRA_INSTALL_SIZE, installSize);
		intent.putExtra(EXTRA_PACKAGE_NAME, currentTask != null ? currentTask.name : "Package");
		sendBroadcast(intent);
	}

	private void broadcastProgress(int progress, String statusText) {
		Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
		intent.putExtra(EXTRA_PROGRESS, progress);
		intent.putExtra(EXTRA_STATUS_TEXT, statusText);
		intent.putExtra(EXTRA_PACKAGE_KEY, currentTask != null ? currentTask.key : "");
		sendBroadcast(intent);
	}

	private void updateNotification(String text, int progress) {
		Notification notification = buildNotification(text, progress);
		startForeground(NOTIFICATION_ID, notification);
	}

	private Notification buildNotification(String text, int progress) {
		Intent notificationIntent = new Intent(this, ManageLanguagesActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

		// Add a cancel action directly to the notification
		Intent cancelIntent = new Intent(ACTION_CANCEL);
		PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

		String title = getString(R.string.title_package_manager);
		if (!completedPackages.isEmpty()) {
			title += " (" + completedPackages.size() + " done)";
		}

		if (!installQueue.isEmpty()) {
			title += " [" + installQueue.size() + " queued]";
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(title)
				.setContentText(text)
				.setSmallIcon(R.drawable.ic_code_studio)
				.setProgress(100, progress, progress <= 0)
				.setContentIntent(pendingIntent)
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent);

		return builder.build();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel serviceChannel = new NotificationChannel(
					CHANNEL_ID,
					getString(R.string.channel_name_package_manager),
					NotificationManager.IMPORTANCE_LOW
			);
			NotificationManager manager = getSystemService(NotificationManager.class);
			if (manager != null) {
				manager.createNotificationChannel(serviceChannel);
			}
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private static class InstallTask {
		String key;
		String name;
		String command;
		int retryCount = 0;

		InstallTask(String key, String name, String command) {
			this.key = key;
			this.name = name;
			this.command = command;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof InstallTask) {
				return key.equals(((InstallTask) obj).key);
			}
			return false;
		}
	}
}
