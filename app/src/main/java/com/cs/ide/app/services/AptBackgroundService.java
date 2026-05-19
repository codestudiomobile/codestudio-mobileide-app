package com.cs.ide.app.services;

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

import com.cs.ide.R;
import com.cs.ide.app.activities.ManageLanguagesActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * AptBackgroundService handles the installation of packages via 'apt-get' in the background.
 * It provides foreground notifications to keep the user informed of the progress and 
 * broadcasts updates to the UI.
 */
public class AptBackgroundService extends Service {
    /** Action for starting a package installation. */
    public static final String ACTION_INSTALL = "com.cs.ide.action.APT_INSTALL";
    /** Extra key for the package name. */
    public static final String EXTRA_PACKAGE = "package";

    /** Action for broadcasting installation progress. */
    public static final String ACTION_PROGRESS = "com.cs.ide.action.APT_PROGRESS";
    /** Action for requesting confirmation from the user. */
    public static final String ACTION_REQUEST_CONFIRM = "com.cs.ide.action.APT_REQUEST_CONFIRM";
    /** Action for user confirmation. */
    public static final String ACTION_CONFIRM = "com.cs.ide.action.APT_CONFIRM";
    /** Action for user cancellation. */
    public static final String ACTION_CANCEL = "com.cs.ide.action.APT_CANCEL";

    /** Extra key for progress percentage. */
    public static final String EXTRA_PROGRESS_PERCENT = "progress_percent";
    /** Extra key for progress description text. */
    public static final String EXTRA_PROGRESS_TEXT = "progress_text";
    /** Extra key for download size. */
    public static final String EXTRA_DOWNLOAD_SIZE = "download_size";
    /** Extra key for install size. */
    public static final String EXTRA_INSTALL_SIZE = "install_size";

    /** Notification channel ID for the service. */
    private static final String CHANNEL_ID = "AptServiceChannel";
    /** Unique ID for the foreground notification. */
    private static final int NOTIFICATION_ID = 1001;

    /** Tracks if an installation is currently running. */
    private boolean isRunning = false;
    /** The currently running apt process. */
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
                    pb = new ProcessBuilder(
                            "/data/data/com.cs.ide/files/usr/bin/sh",
                            "-c",
                            command
                    );
                    broadcastProgress(0, "Running custom command...");
                } else {
                    // Using pkg install without -y to get the size prompt
                    pb = new ProcessBuilder(
                            "/data/data/com.cs.ide/files/usr/bin/pkg",
                            "install",
                            pkg
                    );
                }
                
                String prefix = "/data/data/com.cs.ide/files/usr";
                pb.environment().put("PREFIX", prefix);
                pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
                pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));
                pb.redirectErrorStream(true);

                currentProcess = pb.start();
                processWriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(currentProcess.getOutputStream()));

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();
                    while ((line = reader.readLine()) != null) {
                        lastLines.add(line);
                        if (lastLines.size() > 5) lastLines.removeFirst();
                        
                        String combined = String.join("\n", lastLines);
                        
                        // Check for sizes and y/n prompt
                        if (combined.contains("Do you want to continue? [Y/n]")) {
                            String downloadSize = "unknown";
                            String installSize = "unknown";
                            
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Need to get ([\\d.,]+\\s*[kMG]B)").matcher(combined);
                            if (m.find()) downloadSize = m.group(1);
                            
                            m = java.util.regex.Pattern.compile("After this operation, ([\\d.,]+\\s*[kMG]B)").matcher(combined);
                            if (m.find()) installSize = m.group(1);
                            
                            awaitingConfirmation = true;
                            broadcastRequestConfirm(downloadSize, installSize);
                            updateNotification("Awaiting confirmation: Download " + downloadSize, 0);
                            lastLines.clear(); // Avoid re-triggering for the same prompt
                        }
                        
                        // Parse progress percentage
                        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(\\d+)%").matcher(line);
                        if (pm.find()) {
                            int percent = Integer.parseInt(pm.group(1));
                            String status = "Processing...";
                            if (line.contains("kB/s") || line.contains("MB/s")) {
                                status = "Downloading... ";
                                java.util.regex.Matcher sm = java.util.regex.Pattern.compile("([\\d.,]+\\s*[kMG]B/s)").matcher(line);
                                if (sm.find()) status += sm.group(1);
                            } else if (line.contains("Unpacking")) {
                                status = "Extracting...";
                            } else if (line.contains("Setting up")) {
                                status = "Installing...";
                            }
                            
                            broadcastProgress(percent, status);
                            updateNotification(status, percent);
                        } else if (line.contains("Progress: [")) {
                            // Extract progress from bar if % not found
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[\\s*(\\d+)%\\]").matcher(line);
                            if (m.find()) {
                                int percent = Integer.parseInt(m.group(1));
                                broadcastProgress(percent, "Installing...");
                                updateNotification("Installing...", percent);
                            }
                        }
                        
                        android.util.Log.d("AptService", "PKG: " + line);
                    }
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
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS_PERCENT, percent);
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
}
