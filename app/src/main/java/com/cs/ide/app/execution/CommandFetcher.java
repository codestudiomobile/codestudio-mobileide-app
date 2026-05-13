package com.cs.ide.app.execution;

import android.content.Context;
import android.util.Log;

import com.cs.ide.app.models.LanguagePack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * CommandFetcher handles the loading and parsing of command configurations from
 * JSON files located in assets or shared preferences.
 */
public class CommandFetcher {
    private static final String TAG = "CommandFetcher";
    private static final String CONFIG_FILE_NAME = "commands.json";
    private static final String PREF_NAME = "CommandConfigPrefs";
    private static final String PREF_KEY_UPDATED_CONFIG = "updated_commands_json";

    public final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Context context;

    /**
     * Constructs a new CommandFetcher.
     *
     * @param context The application context.
     */
    public CommandFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Loads all language packs asynchronously.
     *
     * @return A Future containing a list of LanguagePack objects.
     */
    public Future<List<LanguagePack>> loadAllLanguagePacksAsync() {
        return executorService.submit(() -> {
            List<LanguagePack> packs = new ArrayList<>();
            String configJson = loadConfigurationJson();
            if (configJson == null) return packs;
            try {
                JSONObject fullConfig = new JSONObject(configJson);
                Iterator<String> keys = fullConfig.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.equals("terminal")) continue;
                    JSONObject langConfig = fullConfig.getJSONObject(key);
                    String name = langConfig.optString("name", capitalize(key));
                    String install = langConfig.optString("install", "");
                    String check = langConfig.optString("check", "");
                    if (!install.isEmpty()) {
                        packs.add(new LanguagePack(
                                key,
                                name,
                                install,
                                check, 
                                LanguagePack.STATUS_AVAILABLE 
                        ));
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing configuration JSON for language list.", e);
            }
            return packs;
        });
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    // --- Private Helper Methods ---

    /**
     * Loads the configuration JSON from shared preferences or assets.
     *
     * @return The JSON configuration string, or null if it cannot be loaded.
     */
    private String loadConfigurationJson() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String updatedConfig = prefs.getString(PREF_KEY_UPDATED_CONFIG, null);
        if (updatedConfig != null) {
            Log.d(TAG, "Loaded updated config from SharedPreferences.");
            return updatedConfig;
        }
        try {
            InputStream is = context.getAssets().open(CONFIG_FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            Log.d(TAG, "Loaded default config from assets.");
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Could not load " + CONFIG_FILE_NAME + " from assets.", e);
        }
        return null;
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param s The string to capitalize.
     * @return The capitalized string.
     */
    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
