package com.cs.ide.termux.app;

import android.util.Log;

import com.cs.ide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * TermuxPatcher handles changing the package name in bootstrap files.
 * It replaces the default "com.termux" package name with the current application's package name.
 */
public class TermuxPatcher {

	private static final String LOG_TAG = "TermuxPatcher";
	private static final String OLD_PACKAGE_NAME = "com.termux";
	private static final String NEW_PACKAGE_NAME = TermuxConstants.TERMUX_PACKAGE_NAME;

	/**
	 * Patches the extracted bootstrap files in the staging directory.
	 *
	 * @param stagingDir The directory containing the extracted bootstrap files.
	 */
	public static boolean patchBootstrap(File stagingDir) {
		log("Patching bootstrap in " + stagingDir.getAbsolutePath());
		try {
			int patchedCount = patchDirectory(stagingDir);
			log("Bootstrap patching completed. Patched " + patchedCount + " files.");
			return true;
		} catch (Exception e) {
			Log.e(LOG_TAG, "Bootstrap patching failed", e);
			return false;
		}
	}

	private static void log(String message) {
		Log.i(LOG_TAG, message);
	}

	public static int patchDirectory(File file) {
		int count = 0;
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files == null) return 0;

			for (File child : files) {
				count += patchDirectory(child);
			}
		} else if (file.isFile()) {
			if (patchFile(file)) {
				count++;
			}
		}
		return count;
	}

	private static boolean patchFile(File file) {
		// Use RandomAccessFile to avoid loading huge files into memory
		// Since both package names are 10 bytes, we can patch in-place.

		byte[] oldBytes = OLD_PACKAGE_NAME.getBytes(StandardCharsets.UTF_8);
		byte[] newBytes = NEW_PACKAGE_NAME.getBytes(StandardCharsets.UTF_8);

		if (oldBytes.length != newBytes.length) {
			Log.e(LOG_TAG, "CRITICAL: Package name length mismatch! " + OLD_PACKAGE_NAME + " vs " + NEW_PACKAGE_NAME);
			return false;
		}

		try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
			long length = raf.length();
			if (length < oldBytes.length) return false;

			byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
			long pos = 0;
			boolean modified = false;

			while (pos <= length - oldBytes.length) {
				raf.seek(pos);
				int bytesToRead = (int) Math.min(buffer.length, length - pos);
				raf.readFully(buffer, 0, bytesToRead);

				for (int i = 0; i <= bytesToRead - oldBytes.length; i++) {
					boolean match = true;
					for (int j = 0; j < oldBytes.length; j++) {
						if (buffer[i + j] != oldBytes[j]) {
							match = false;
							break;
						}
					}

					if (match) {
						raf.seek(pos + i);
						raf.write(newBytes);
						modified = true;
						// Update buffer to avoid double-patching if we were to re-read
						System.arraycopy(newBytes, 0, buffer, i, newBytes.length);
						i += oldBytes.length - 1;
					}
				}

				if (bytesToRead < buffer.length) {
					break; // End of file
				}

				// Move forward, but overlap by oldBytes.length - 1 to catch matches across boundaries
				pos += (bytesToRead - oldBytes.length + 1);
			}

			if (modified) {
				log("Patched file: " + file.getAbsolutePath());
			}
			return modified;
		} catch (IOException e) {
			// Some files might be busy or read-only
			return false;
		}
	}
}
