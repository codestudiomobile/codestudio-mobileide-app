package com.cs.ide.app.execution;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CommandUpdater checks for updates to the command configuration from a remote repository
 * and updates the local storage if a new version is available.
 */
public class CommandUpdater {
    private static final String TAG = "CommandUpdater";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/codestudiomobile/codestudio-commands/main/version.json";
    private static final String LOCAL_PREF_KEY = "updated_commands_json";
    private static final String COMMANDS_URL = "https://raw.githubusercontent.com/codestudiomobile/codestudio-commands/main/commands.json";
    private static final String PREF_NAME = "CommandConfigPrefs";
    private static final String LAST_CHECK_KEY = "last_update_check_ms";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000; // Check once a day

    /**
     * Checks for updates to the command configuration in a background thread.
     *
     * @param context The application context.
     */
    public static void checkForUpdates(Context context) {
        if (!shouldCheckForUpdate(context)) {
            Log.i(TAG, "Skipping update check. Last check was recently.");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                String remoteVersion = fetchRemoteVersion();
                String localVersion = getLocalVersion(context);
                if (!remoteVersion.equals(localVersion)) {
                    String newCommands = fetchRemoteCommands();
                    if (isValidJson(newCommands)) {
                        saveToPrefs(context, newCommands);
                        saveVersion(context, remoteVersion);
                        Log.i(TAG, "Commands updated to version: " + remoteVersion);
                    }
                } else {
                    Log.i(TAG, "No update needed. Version unchanged.");
                }
                saveLastCheckTime(context);
            } catch (Exception e) {
                Log.e(TAG, "Update failed: " + e.getMessage());
            } finally {
                executor.shutdown();
            }
        });
    }

    private static boolean shouldCheckForUpdate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(LAST_CHECK_KEY, 0);
        return (System.currentTimeMillis() - lastCheck) > CHECK_INTERVAL_MS;
    }

    private static void saveLastCheckTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply();
    }

    private static boolean isValidJson(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    // --- Private Helper Methods ---

    /**
     * Fetches the version number from the remote repository.
     *
     * @return The remote version string.
     * @throws IOException   If an I/O error occurs.
     * @throws JSONException If a JSON parsing error occurs.
     */
    private static String fetchRemoteVersion() throws IOException, JSONException {
        URL url = new URL(VERSION_URL);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            JSONObject json = new JSONObject(builder.toString());
            return json.getString("version");
        }
    }

    /**
     * Fetches the command configuration JSON from the remote repository.
     *
     * @return The command configuration string.
     * @throws IOException If an I/O error occurs.
     */
    private static String fetchRemoteCommands() throws IOException {
        URL url = new URL(COMMANDS_URL);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to fetch commands. HTTP code: " + responseCode);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Saves the command configuration to shared preferences.
     *
     * @param context The application context.
     * @param json    The JSON configuration string.
     */
    private static void saveToPrefs(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(LOCAL_PREF_KEY, json).apply();
    }

    /**
     * Saves the version number of the command configuration.
     *
     * @param context The application context.
     * @param version The version string.
     */
    private static void saveVersion(Context context, String version) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("commands_version", version).apply();
    }

    /**
     * Gets the local version number of the command configuration.
     *
     * @param context The application context.
     * @return The local version string, or "0.0.0" if not found.
     */
    private static String getLocalVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString("commands_version", "0.0.0");
    }
}
