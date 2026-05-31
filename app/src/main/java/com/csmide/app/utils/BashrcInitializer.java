package com.csmide.app.utils;

import android.content.Context;
import android.util.Log;

import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * BashrcInitializer is responsible for initializing the custom bashrc content
 * from assets to the Termux environment's bash.bashrc.
 * It overwrites the existing content and ensures Unix (LF) line endings.
 */
public class BashrcInitializer {
	private static final String TAG = "BashrcInitializer";

	/**
	 * Overwrites $PREFIX/etc/bash.bashrc with content from bash.bashrc asset.
	 * Explicitly converts all line endings to LF to prevent "command not found" errors.
	 *
	 * @param context The application context.
	 */
	public static void initialize(Context context) {
		File etcDir = new File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH);
		if (!etcDir.exists()) {
			if (!etcDir.mkdirs()) {
				Log.e(TAG, "Failed to create directory: " + etcDir.getAbsolutePath());
				return;
			}
		}

		File bashrc = new File(etcDir, "bash.bashrc");

		Log.d(TAG, "Initializing bash.bashrc (ensuring Unix line endings)...");

		try (InputStream in = context.getAssets().open("bash.bashrc");
		     BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		     PrintWriter writer = new PrintWriter(new FileOutputStream(bashrc, false))) {

			String line;
			while ((line = reader.readLine()) != null) {
				writer.print(line.replace("com.termux", TermuxConstants.TERMUX_PACKAGE_NAME) + "\n");
			}
			Log.d(TAG, "Successfully initialized bash.bashrc at " + bashrc.getAbsolutePath());
		} catch (Exception e) {
			Log.e(TAG, "Failed to initialize bash.bashrc at " + bashrc.getAbsolutePath(), e);
		}
	}
}
