package com.csmide.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.csmide.R;
import com.csmide.app.activities.ManageLanguagesActivity;
import com.csmide.termux.shared.termux.TermuxConstants;

/**
 * AptBackgroundService handles the installation of packages via 'apt-get' in the background.
 * It provides foreground notifications to keep the user informed of the progress and
 * broadcasts updates to the UI.
 */
public class AptBackgroundService extends Service {
	/**
	 * Action for starting a package installation.
	 */
	public static final String ACTION_INSTALL = "com.csmide.action.APT_INSTALL";
	/**
	 * Extra key for the package name.
	 */
	public static final String EXTRA_PACKAGE = "package";

	/**
	 * Action for broadcasting installation progress.
	 */
	public static final String ACTION_PROGRESS = "com.csmide.action.APT_PROGRESS";
	/**
	 * Action for requesting confirmation from the user.
	 */
	public static final String ACTION_REQUEST_CONFIRM = "com.csmide.action.APT_REQUEST_CONFIRM";
	/**
	 * Action for user confirmation.
	 */
	public static final String ACTION_CONFIRM = "com.csmide.action.APT_CONFIRM";
	/**
	 * Action for user cancellation.
	 */
	public static final String ACTION_CANCEL = "com.csmide.action.APT_CANCEL";

	/**
	 * Extra key for progress percentage.
	 */
	public static final String EXTRA_PROGRESS_PERCENT = "progress_percent";
	/**
	 * Extra key for progress description text.
	 */
	public static final String EXTRA_PROGRESS_TEXT = "progress_text";
	/**
	 * Extra key for download size.
	 */
	public static final String EXTRA_DOWNLOAD_SIZE = "download_size";
	/**
	 * Extra key for install size.
	 */
	public static final String EXTRA_INSTALL_SIZE = "install_size";
	public static final String EXTRA_EXTRACT_PROGRESS = "extract_progress";
	public static final String EXTRA_SPEED = "download_speed";

	/**
	 * Notification channel ID for the service.
	 */
	private static final String CHANNEL_ID = "AptServiceChannel";
	/**
	 * Unique ID for the foreground notification.
	 */
	private static final int NOTIFICATION_ID = 1001;

	/**
	 * Tracks if an installation is currently running.
	 */
	private boolean isRunning = false;
	/**
	 * The currently running apt process.
	 */
	private Process currentProcess = null;
	private java.io.BufferedWriter processWriter = null;
	private boolean awaitingConfirmation = false;

	private final android.content.BroadcastReceiver controlReceiver = new android.content.BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_CONFIRM.equals(intent.getAction())) {
				confirmInstallation();
			} else if (ACTION_CANCEL.equals(intent.getAction())) {
				cancelInstallation();
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_CONFIRM);
		filter.addAction(ACTION_CANCEL);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(controlReceiver, filter);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(controlReceiver);
	}

	private void confirmInstallation() {
		if (awaitingConfirmation && processWriter != null) {
			try {
				processWriter.write("y\n");
				processWriter.flush();
				awaitingConfirmation = false;
			} catch (java.io.IOException e) {
				android.util.Log.e("AptService", "Failed to send confirmation", e);
			}
		}
	}

	private void cancelInstallation() {
		if (currentProcess != null) {
			currentProcess.destroy();
		}
		isRunning = false;
		stopForeground(true);
		stopSelf();
	}

	/**
	 * Handles start commands. If ACTION_INSTALL is received, it starts the package installation process.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_INSTALL.equals(intent.getAction())) {
			String pkg = intent.getStringExtra(EXTRA_PACKAGE);
			if (pkg != null && !isRunning) {
				startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.msg_starting_installation), 0));
				runAptCommand(pkg);
			}
		}
		return START_STICKY;
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		// Do not stop the service when the app is swiped away.
		// The foreground notification ensures it keeps running.
		android.util.Log.d("AptService", "Task removed, but service continues in foreground.");
		super.onTaskRemoved(rootIntent);
	}

	/**
	 * Executes the apt-get install command in a background thread.
	 * Parses the machine-readable output from apt (using Status-Fd=1) to update progress.
	 *
	 * @param pkg The name of the package to install.
	 */
	private void runAptCommand(String pkg) {
		isRunning = true;
		new Thread(() -> {
			try {
				ProcessBuilder pb;
				boolean isCustom = pkg.startsWith("custom_command:");
				if (isCustom) {
					String command = pkg.substring("custom_command:".length());
					if (command.contains("install") || command.contains("upgrade")) {
						if (!command.contains("--status-fd")) {
							command = command.replace("apt-get install", "apt-get install --status-fd=3")
									.replace("apt install", "apt-get install --status-fd=3");
							command += " 3>&1";
						}
					}
					pb = new ProcessBuilder(
							TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
							"-c",
							command
					);
					broadcastProgress(0, "Running custom command...");
				} else {
					// Using apt-get with status-fd for better tracking
					pb = new ProcessBuilder(
							TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
							"-c",
							"apt-get install --status-fd=3 " + pkg + " 3>&1"
					);
				}

				String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
				pb.environment().put("PREFIX", prefix);
				pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
				pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));
				pb.environment().put("HOME", prefix.replace("/usr", "/home"));
				pb.environment().put("TERM", "xterm");
				pb.redirectErrorStream(true);

				currentProcess = pb.start();
				processWriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(currentProcess.getOutputStream()));

				try {
					java.io.InputStream in = currentProcess.getInputStream();
					byte[] buffer = new byte[8192];
					int n;
					StringBuilder outputAccumulator = new StringBuilder();
					java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();
					java.util.regex.Pattern percentPattern = java.util.regex.Pattern.compile("(\\d+)%");

					long lastBytes = 0;
					long lastTime = System.currentTimeMillis();
					String currentSpeed = "";

					while ((n = in.read(buffer)) != -1) {
						String chunk = new String(buffer, 0, n);
						outputAccumulator.append(chunk);

						// Process complete lines
						int newlineIdx;
						while ((newlineIdx = outputAccumulator.indexOf("\n")) != -1) {
							String line = outputAccumulator.substring(0, newlineIdx).trim();
							outputAccumulator.delete(0, newlineIdx + 1);

							if (line.isEmpty()) continue;

							// Strip ANSI escape codes
							String cleanLine = line.replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
							android.util.Log.d("AptService", "PKG: " + cleanLine);
							lastLines.add(cleanLine);
							if (lastLines.size() > 15) lastLines.removeFirst();

							// --- Status-Fd Parsing ---
							if (cleanLine.startsWith("dlstatus:")) {
								String[] parts = cleanLine.split(":");
								if (parts.length >= 4) {
									try {
										long completed = Long.parseLong(parts[2]);
										long total = Long.parseLong(parts[3]);
										int percent = (int) (completed * 100 / (total > 0 ? total : 1));

										long now = System.currentTimeMillis();
										long dt = now - lastTime;
										if (dt >= 1000) {
											double speedVal = (completed - lastBytes) / (dt / 1000.0);
											currentSpeed = formatSpeed(speedVal);
											lastBytes = completed;
											lastTime = now;
										}

										String statusText = "Downloading " + pkg + " (" + percent + "%) " + currentSpeed;
										broadcastProgress(percent, -1, currentSpeed, statusText);
										updateNotification(statusText, percent);
									} catch (Exception ignored) {
									}
								}
								continue;
							}

							if (cleanLine.startsWith("pmstatus:")) {
								String[] parts = cleanLine.split(":");
								if (parts.length >= 3) {
									try {
										float percent = Float.parseFloat(parts[2]);
										String status = parts.length > 3 ? parts[3] : "Installing...";
										int p = (int) percent;
										String statusText = "Installing " + pkg + ": " + status + " (" + p + "%)";
										broadcastProgress(100, p, "", statusText);
										updateNotification(statusText, p);
									} catch (Exception ignored) {
									}
								}
								continue;
							}

							// 2. Fallback Progress % (regex)
							java.util.regex.Matcher pm = percentPattern.matcher(cleanLine);
							if (pm.find()) {
								int percent = Integer.parseInt(pm.group(1));
								broadcastProgress(percent, "Processing...");
								updateNotification("Processing...", percent);
							}
						}

						// Prompt Detection
						String currentOutput = outputAccumulator.toString().replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
						if (currentOutput.contains("Do you want to continue? [Y/n]")) {
							String downloadSize = "unknown";
							String installSize = "unknown";

							StringBuilder fullContext = new StringBuilder();
							for (String l : lastLines) fullContext.append(l).append("\n");
							fullContext.append(currentOutput);
							String context = fullContext.toString();

							java.util.regex.Matcher m = java.util.regex.Pattern.compile("Need to get ([\\d.,]+\\s*[kMG]B)").matcher(context);
							if (m.find()) downloadSize = m.group(1);

							m = java.util.regex.Pattern.compile("After this operation, ([\\d.,]+\\s*[kMG]B)").matcher(context);
							if (m.find()) installSize = m.group(1);

							awaitingConfirmation = true;
							broadcastRequestConfirm(downloadSize, installSize);
							updateNotification("Confirm installation (" + downloadSize + ")", 0);
							outputAccumulator.setLength(0);
						}
					}
				} catch (java.io.IOException e) {
					android.util.Log.e("AptService", "Error reading stream", e);
				}

				currentProcess.waitFor();

			} catch (Exception e) {
				android.util.Log.e("AptService", "Apt command failed", e);
				broadcastProgress(100, getString(R.string.msg_error_prefix, e.getMessage()));
				updateNotification(getString(R.string.msg_installation_failed), 100);
			} finally {
				isRunning = false;
				currentProcess = null;
				processWriter = null;
				broadcastProgress(100, getString(R.string.msg_installation_complete));
				stopForeground(true);
				stopSelf();
			}
		}).start();
	}

	private void broadcastRequestConfirm(String downloadSize, String installSize) {
		Intent intent = new Intent(ACTION_REQUEST_CONFIRM);
		intent.putExtra(EXTRA_DOWNLOAD_SIZE, downloadSize);
		intent.putExtra(EXTRA_INSTALL_SIZE, installSize);
		sendBroadcast(intent);
	}

	/**
	 * Broadcasts the installation progress to listening UI components.
	 *
	 * @param percent Progress percentage.
	 * @param text    Description of the current step.
	 */
	private void broadcastProgress(int percent, String text) {
		broadcastProgress(percent, -1, "", text);
	}

	private void broadcastProgress(int percent, int extractPercent, String speed, String text) {
		Intent intent = new Intent(ACTION_PROGRESS);
		intent.putExtra(EXTRA_PROGRESS_PERCENT, percent);
		if (extractPercent != -1) intent.putExtra(EXTRA_EXTRACT_PROGRESS, extractPercent);
		if (speed != null && !speed.isEmpty()) intent.putExtra(EXTRA_SPEED, speed);
		intent.putExtra(EXTRA_PROGRESS_TEXT, text);
		sendBroadcast(intent);
	}

	/**
	 * Builds a notification for the foreground service.
	 *
	 * @param text     The notification text.
	 * @param progress The progress to display in the progress bar.
	 * @return The constructed Notification object.
	 */
	private Notification buildNotification(String text, int progress) {
		Intent notificationIntent = new Intent(this, ManageLanguagesActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

		// Add a cancel action directly to the notification for better user control
		Intent cancelIntent = new Intent(ACTION_CANCEL);
		PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

		return new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.title_package_manager))
				.setContentText(text)
				.setSmallIcon(R.drawable.ic_code_studio)
				.setProgress(100, progress, progress == 0)
				.setContentIntent(pendingIntent)
				.setOngoing(true) // Prevent swiping away
				.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
				.setOnlyAlertOnce(true)
				.build();
	}

	/**
	 * Updates the existing foreground notification with new progress.
	 *
	 * @param text     The new notification text.
	 * @param progress The new progress value.
	 */
	private void updateNotification(String text, int progress) {
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, buildNotification(text, progress));
		}
	}

	/**
	 * Creates the notification channel required for Android O and above.
	 */
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel serviceChannel = new NotificationChannel(
					CHANNEL_ID,
					getString(R.string.channel_name_package_manager),
					NotificationManager.IMPORTANCE_LOW
			);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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

	private String formatSpeed(double bytesPerSecond) {
		if (bytesPerSecond < 1024)
			return String.format(java.util.Locale.US, "%.1f B/s", bytesPerSecond);
		double kb = bytesPerSecond / 1024.0;
		if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB/s", kb);
		double mb = kb / 1024.0;
		return String.format(java.util.Locale.US, "%.1f MB/s", mb);
	}
}
