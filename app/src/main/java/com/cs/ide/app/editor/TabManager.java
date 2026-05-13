package com.cs.ide.app.editor;

import static com.cs.ide.app.utils.AppPreferences.CURRENT_TAB;
import static com.cs.ide.app.utils.AppPreferences.TAB_NAME_KEY;
import static com.cs.ide.app.utils.AppPreferences.TAB_URI_KEY;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cs.ide.app.adapters.ViewPagerAdapter;
import com.cs.ide.app.utils.AppPreferences;
import com.google.android.material.tabs.TabLayout;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * TabManager is responsible for persisting and restoring the state of open editor tabs.
 * It uses SharedPreferences and GSON to serialize/deserialize the list of open file URIs
 * and their corresponding names, ensuring the user's workspace is preserved between sessions.
 */
public class TabManager {
    private static final String TAG = "TabManager";
    private final SharedPreferences preferences;

    /**
     * Constructs a TabManager.
     *
     * @param context The context used to access SharedPreferences.
     */
    public TabManager(Context context) {
        this.preferences = context.getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Loads the list of tabs that were open in the last session from SharedPreferences.
     *
     * @return A TabState object containing the lists of URIs, names, and the last active tab index.
     */
    @NonNull
    public TabState loadRecentTabs() {
        String jsonUris = preferences.getString(TAB_URI_KEY, null);
        String jsonNames = preferences.getString(TAB_NAME_KEY, null);
        int currentTab = preferences.getInt(CURRENT_TAB, -1);
        List<Uri> uriList = new ArrayList<>();
        List<String> namesList = new ArrayList<>();

        if (jsonUris == null || jsonNames == null) {
            return new TabState(uriList, namesList, -1);
        }

        try {
            Gson gson = new Gson();
            Type listStringType = new TypeToken<List<String>>() {}.getType();
            namesList = gson.fromJson(jsonNames, listStringType);
            List<String> uriStringList = gson.fromJson(jsonUris, listStringType);
            for (String uriString : uriStringList) {
                uriList.add(Uri.parse(uriString));
            }
            if (uriList.size() != namesList.size()) {
                // Data mismatch, discard state
                return new TabState(new ArrayList<>(), new ArrayList<>(), -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved tabs", e);
            return new TabState(new ArrayList<>(), new ArrayList<>(), -1);
        }
        return new TabState(uriList, namesList, currentTab);
    }

    /**
     * Persists the currently opened tabs to SharedPreferences.
     *
     * @param adapter    The ViewPagerAdapter containing the fragment list.
     * @param tabLayout  The TabLayout showing the tab headers.
     */
    public void saveOpenedTabs(ViewPagerAdapter adapter, TabLayout tabLayout) {
        if (adapter == null || tabLayout == null) return;

        List<Uri> uriList = adapter.getFileUris();
        List<String> namesList = adapter.getFileNames();
        int currentTab = tabLayout.getSelectedTabPosition();

        // If no tabs are open, clear the saved state
        if (uriList.isEmpty() || currentTab == -1) {
            preferences.edit().remove(TAB_URI_KEY).remove(TAB_NAME_KEY).remove(CURRENT_TAB).apply();
            return;
        }

        List<String> uriStringList = new ArrayList<>();
        for (Uri uri : uriList) {
            uriStringList.add(uri.toString());
        }

        Gson gson = new Gson();
        String jsonUris = gson.toJson(uriStringList);
        String jsonNames = gson.toJson(namesList);

        preferences.edit()
                .putString(TAB_URI_KEY, jsonUris)
                .putString(TAB_NAME_KEY, jsonNames)
                .putInt(CURRENT_TAB, currentTab)
                .apply();
    }

    /**
     * Inner class representing the state of open tabs.
     */
    public static class TabState {
        public final List<Uri> uris;
        public final List<String> names;
        public final int activeTabIndex;

        public TabState(List<Uri> uris, List<String> names, int activeTabIndex) {
            this.uris = uris;
            this.names = names;
            this.activeTabIndex = activeTabIndex;
        }
    }
}
