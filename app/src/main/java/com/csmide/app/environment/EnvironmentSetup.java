package com.csmide.app.environment;

import android.content.Context;

import java.io.File;

/**
 * EnvironmentSetup provides utility methods for managing the application's
 * internal storage environment and directory structure.
 */
public class EnvironmentSetup {

	/**
	 * Gets the root directory for the application's files.
	 *
	 * @param context The application context.
	 * @return The root file directory.
	 */
	public static File getEnvironmentRoot(Context context) {
		return context.getFilesDir();
	}

	/**
	 * Gets the binary directory for stored executables.
	 *
	 * @param context The application context.
	 * @return The bin directory file.
	 */
	public static File getBinDir(Context context) {
		return new File(new File(getEnvironmentRoot(context), "usr"), "bin");
	}

	/**
	 * Ensures the necessary directory structure is created in internal storage.
	 *
	 * @param context The application context.
	 */
	public static void setupStorage(Context context) {
		File root = getEnvironmentRoot(context);
		File bin = getBinDir(context);

		if (!root.exists()) {
			root.mkdirs();
		}
		if (!bin.exists()) {
			bin.mkdirs();
		}

		File home = new File(root, "home");
		if (!home.exists()) {
			home.mkdirs();
		}
	}
}
