package com.csmide.app.editor;

import static com.csmide.app.utils.AppPreferences.CURRENT_TAB;
import static com.csmide.app.utils.AppPreferences.TAB_NAME_KEY;
import static com.csmide.app.utils.AppPreferences.TAB_PATH_KEY;
import static com.csmide.app.utils.AppPreferences.TAB_URI_KEY;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.csmide.app.adapters.ViewPagerAdapter;
import com.csmide.app.utils.AppPreferences;
import com.csmide.app.utils.FileUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.io.File;
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
	public TabState loadRecentTabs(Context context) {
		String jsonUris = preferences.getString(TAB_URI_KEY, null);
		String jsonNames = preferences.getString(TAB_NAME_KEY, null);
		String jsonPaths = preferences.getString(TAB_PATH_KEY, null);
		int currentTab = preferences.getInt(CURRENT_TAB, -1);

		List<Uri> uriList = new ArrayList<>();
		List<String> namesList = new ArrayList<>();

		if (jsonUris == null || jsonNames == null) {
			return new TabState(uriList, namesList, -1);
		}

		try {
			Gson gson = new Gson();
			Type listStringType = new TypeToken<List<String>>() {
			}.getType();
			List<String> rawNames = gson.fromJson(jsonNames, listStringType);
			List<String> rawUris = gson.fromJson(jsonUris, listStringType);
			List<String> rawPaths = jsonPaths != null ? gson.fromJson(jsonPaths, listStringType) : null;

			if (rawNames == null || rawUris == null) {
				Log.w(TAG, "Failed to deserialize tab lists");
				return new TabState(uriList, namesList, -1);
			}

			int adjustedCurrentTab = -1;
			for (int i = 0; i < rawUris.size(); i++) {
				String uriString = rawUris.get(i);
				if (uriString == null) continue;

				// Filter out transient tabs (e.g., compilation results)
				if (uriString.startsWith("app://com.csmide/compile")) {
					continue;
				}

				Uri uri = Uri.parse(uriString);

				// Fallback for lost permissions on reinstall
				boolean hasPermission = false;
				try {
					context.getContentResolver().query(uri, null, null, null, null).close();
					hasPermission = true;
				} catch (Exception ignored) {
				}

				if (!hasPermission && rawPaths != null && i < rawPaths.size() && rawPaths.get(i) != null) {
					File file = new File(rawPaths.get(i));
					if (file.exists()) {
						uri = Uri.fromFile(file);
					}
				}

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
	public void saveOpenedTabs(Context context, ViewPagerAdapter adapter, TabLayout tabLayout) {
		if (adapter == null || tabLayout == null) return;

		List<Uri> allUris = adapter.getFileUris();
		List<String> allNames = adapter.getFileNames();
		List<Boolean> isPrivate = adapter.isPrivateTab;
		int currentTab = tabLayout.getSelectedTabPosition();

		List<Uri> uriList = new ArrayList<>();
		List<String> namesList = new ArrayList<>();
		List<String> pathList = new ArrayList<>();
		int adjustedCurrentTab = -1;

		for (int i = 0; i < allUris.size(); i++) {
			Uri uri = allUris.get(i);
			// Skip private or transient tabs
			if ((i < isPrivate.size() && isPrivate.get(i)) ||
					(uri != null && uri.toString().startsWith("app://com.csmide/compile"))) {
				if (i == currentTab) adjustedCurrentTab = -1;
				continue;
			}
			uriList.add(uri);
			namesList.add(allNames.get(i));

			String path = uri != null ? FileUtils.getAbsolutePathFromUri(context, uri) : null;
			pathList.add(path);

			if (i == currentTab) {
				adjustedCurrentTab = uriList.size() - 1;
			}
		}

		if (uriList.isEmpty()) {
			preferences.edit().remove(TAB_URI_KEY).remove(TAB_NAME_KEY).remove(TAB_PATH_KEY).remove(CURRENT_TAB).apply();
			return;
		}

		List<String> uriStringList = new ArrayList<>();
		for (Uri uri : uriList) {
			uriStringList.add(uri != null ? uri.toString() : null);
		}

		Gson gson = new Gson();
		preferences.edit()
				.putString(TAB_URI_KEY, gson.toJson(uriStringList))
				.putString(TAB_NAME_KEY, gson.toJson(namesList))
				.putString(TAB_PATH_KEY, gson.toJson(pathList))
				.putInt(CURRENT_TAB, adjustedCurrentTab)
				.apply();
	}

	/**
	 * State holder for restored tabs.
	 */
	public record TabState(List<Uri> uris, List<String> names, int activeTabIndex) {
	}
}
