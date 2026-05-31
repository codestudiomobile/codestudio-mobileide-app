package com.csmide.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FontManager {
	private static final String TAG = "FontManager";
	private static Typeface currentTypeface;
	private static String currentFontPath;

	public static List<String> getAvailableFonts(Context context) {
		List<String> fonts = new ArrayList<>();
		try {
			String[] assets = context.getAssets().list("fonts");
			if (assets != null) {
				for (String asset : assets) {
					if (asset.endsWith(".ttf") || asset.endsWith(".otf")) {
						fonts.add("fonts/" + asset);
					}
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Error listing fonts from assets", e);
		}
		return fonts;
	}

	public static String getFontDisplayName(String fontPath) {
		if (fontPath == null) return "Default";
		String name = fontPath;
		if (name.startsWith("fonts/")) {
			name = name.substring(6);
		}
		int dotIndex = name.lastIndexOf('.');
		if (dotIndex != -1) {
			name = name.substring(0, dotIndex);
		}
		// Replace hyphens and underscores with spaces and capitalize
		name = name.replace('-', ' ').replace('_', ' ');
		return name;
	}

	public static Typeface getTypeface(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		String fontPath = prefs.getString(AppPreferences.KEY_EDITOR_FONT, AppPreferences.DEFAULT_FONT);

		if (currentTypeface == null || !fontPath.equals(currentFontPath)) {
			try {
				currentTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
				currentFontPath = fontPath;
			} catch (Exception e) {
				Log.e(TAG, "Failed to load typeface: " + fontPath, e);
				currentTypeface = Typeface.MONOSPACE;
				currentFontPath = fontPath;
			}
		}
		return currentTypeface;
	}

	public static void updateFont(Context context, String fontPath) {
		SharedPreferences prefs = context.getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		prefs.edit().putString(AppPreferences.KEY_EDITOR_FONT, fontPath).apply();
		currentTypeface = null; // Reset to force reload
	}

	public static void applyFontToViewHierarchy(View view, Typeface typeface) {
		if (view instanceof ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				applyFontToViewHierarchy(vg.getChildAt(i), typeface);
			}
		} else if (view instanceof TextView tv) {
			tv.setTypeface(typeface);
		}
	}
}
