package com.cs.ide.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
    /** Extra key for progress percentage. */
    public static final String EXTRA_PROGRESS_PERCENT = "progress_percent";
    /** Extra key for progress description text. */
    public static final String EXTRA_PROGRESS_TEXT = "progress_text";

    /** Notification channel ID for the service. */
    private static final String CHANNEL_ID = "AptServiceChannel";
    /** Unique ID for the foreground notification. */
    private static final int NOTIFICATION_ID = 1001;

    /** Tracks if an installation is currently running. */
    private boolean isRunning = false;
    /** The currently running apt process. */
    private Process currentProcess = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
        return START_NOT_STICKY;
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
                // Using APT::Status-Fd=1 to write machine-readable progress to stdout
                ProcessBuilder pb = new ProcessBuilder(
                        "/data/data/com.cs.ide/files/usr/bin/apt-get",
                        "-o", "APT::Status-Fd=1",
                        "-y",
                        "install",
                        pkg
                );
                pb.environment().put("PREFIX", "/data/data/com.cs.ide/files/usr");
                pb.environment().put("LD_LIBRARY_PATH", "/data/data/com.cs.ide/files/usr/lib");
                pb.environment().put("PATH", "/data/data/com.cs.ide/files/usr/bin");

                currentProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("pmstatus:")) {
                            // pmstatus:pkgname:percent:desc
                            String[] parts = line.split(":", 4);
                            if (parts.length >= 4) {
                                try {
                                    float percent = Float.parseFloat(parts[2]);
                                    String desc = parts[3];
                                    broadcastProgress((int) percent, desc);
                                    updateNotification(desc, (int) percent);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
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
                broadcastProgress(100, getString(R.string.msg_installation_complete));
                stopForeground(true);
                stopSelf();
            }
        }).start();
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

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.title_package_manager))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_code_studio)
                .setProgress(100, progress, progress == 0)
                .setContentIntent(pendingIntent)
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
