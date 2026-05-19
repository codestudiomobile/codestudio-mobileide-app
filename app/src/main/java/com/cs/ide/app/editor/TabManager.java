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
 * Manages persistence and restoration of open editor tabs.
 * Uses SharedPreferences and GSON to save file URIs and names.
 */
public class TabManager {
    private static final String TAG = "TabManager";
    private final SharedPreferences preferences;

    public TabManager(Context context) {
        this.preferences = context.getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Loads saved tabs from the previous session.
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
            List<String> rawNames = gson.fromJson(jsonNames, listStringType);
            List<String> rawUris = gson.fromJson(jsonUris, listStringType);

            if (rawNames == null || rawUris == null) {
                Log.w(TAG, "Failed to deserialize tab lists");
                return new TabState(uriList, namesList, -1);
            }

            int adjustedCurrentTab = -1;
            for (int i = 0; i < rawUris.size(); i++) {
                String uriString = rawUris.get(i);
                if (uriString == null) continue;

                // Filter out transient tabs (e.g., compilation results)
                if (uriString.startsWith("app://com.cs.ide/compile")) {
                    continue;
                }

                Uri uri = Uri.parse(uriString);
                String name = (i < rawNames.size()) ? rawNames.get(i) : uri.getLastPathSegment();
                if (name == null) name = "Untitled";

                uriList.add(uri);
                namesList.add(name);

                if (i == currentTab) {
                    adjustedCurrentTab = uriList.size() - 1;
                }
            }
            currentTab = adjustedCurrentTab;

            if (uriList.size() != namesList.size()) {
                Log.w(TAG, "Mismatch in URI and name list sizes after filtering");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved tabs", e);
        }
        return new TabState(uriList, namesList, currentTab);
    }

    /**
     * Persists the current state of open tabs.
     */
    public void saveOpenedTabs(ViewPagerAdapter adapter, TabLayout tabLayout) {
        if (adapter == null || tabLayout == null) return;

        List<Uri> allUris = adapter.getFileUris();
        List<String> allNames = adapter.getFileNames();
        List<Boolean> isPrivate = adapter.isPrivateTab;
        int currentTab = tabLayout.getSelectedTabPosition();

        List<Uri> uriList = new ArrayList<>();
        List<String> namesList = new ArrayList<>();
        int adjustedCurrentTab = -1;

        for (int i = 0; i < allUris.size(); i++) {
            Uri uri = allUris.get(i);
            // Skip private or transient tabs
            if ((i < isPrivate.size() && isPrivate.get(i)) ||
                    (uri != null && uri.toString().startsWith("app://com.cs.ide/compile"))) {
                if (i == currentTab) adjustedCurrentTab = -1;
                continue;
            }
            uriList.add(uri);
            namesList.add(allNames.get(i));
            if (i == currentTab) {
                adjustedCurrentTab = uriList.size() - 1;
            }
        }

        if (uriList.isEmpty()) {
            preferences.edit().remove(TAB_URI_KEY).remove(TAB_NAME_KEY).remove(CURRENT_TAB).apply();
            return;
        }

        List<String> uriStringList = new ArrayList<>();
        for (Uri uri : uriList) {
            uriStringList.add(uri.toString());
        }

        Gson gson = new Gson();
        preferences.edit()
                .putString(TAB_URI_KEY, gson.toJson(uriStringList))
                .putString(TAB_NAME_KEY, gson.toJson(namesList))
                .putInt(CURRENT_TAB, adjustedCurrentTab)
                .apply();
    }

    /**
     * State holder for restored tabs.
     */
    public record TabState(List<Uri> uris, List<String> names, int activeTabIndex) {}
}
