package com.csmide.termux.app;

import android.util.Log;

import androidx.annotation.Keep;

import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Set;
import java.util.Arrays;

/**
 * CLI tool to patch packages by replacing 'com.termux' with the app's package name.
 * It can patch installed directories or .deb files before installation.
 */
@Keep
public class TermuxPackagePatcher {

	private static final String TAG = "TermuxPackagePatcher";
	private static PrintWriter logWriter;

	public static void main(String[] args) {
		setupLogging();
		log("Starting Termux Package Patcher...");
		log("Working dir: " + new File(".").getAbsolutePath());
		log("Arguments: " + java.util.Arrays.toString(args));

		try {
			if (args.length > 0) {
				for (String path : args) {
					if (path.equals("--stdin")) {
						log("Reading paths from stdin (APT hook mode)");
						readFromStdin();
					} else {
						System.out.println("Patching: " + new File(path).getName());
						processPath(path);
					}
				}
			} else {
				log("No args, patching entire prefix: " + TermuxConstants.TERMUX_PREFIX_DIR_PATH);
				System.out.println("Scanning and patching entire environment... Please wait.");
				File prefixDir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
				int patchedCount = TermuxPatcher.patchDirectory(prefixDir);
				log("Patched " + patchedCount + " files/links.");
				System.out.println("\nFinished. Patched " + patchedCount + " entries.");
			}
			log("Patching finished successfully.");
			System.out.println("Termux Package Patcher: Success");
		} catch (Throwable e) {
			log("FATAL ERROR: " + e.getMessage());
			e.printStackTrace();
			if (logWriter != null) {
				e.printStackTrace(logWriter);
			}
			System.err.println("Termux Package Patcher: Patching Failed - " + e.getMessage());
			System.exit(1);
		} finally {
			if (logWriter != null) {
				logWriter.close();
			}
		}
	}

	private static void setupLogging() {
		try {
			// Ensure we use a safe path for logging
			String filesDir = TermuxConstants.TERMUX_FILES_DIR_PATH;
			File logFile = new File(filesDir + "/usr/tmp/patcher.log");
			File parent = logFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			logWriter = new PrintWriter(new FileWriter(logFile, true));
		} catch (Exception e) {
			// Can't log to file, will use stdout
		}
	}

	private static void log(String message) {
		// Use android.util.Log to send to logcat (hidden from terminal screen)
		try {
			Log.i(TAG, message);
		} catch (NoClassDefFoundError e) {
			// Fallback for non-Android environments
		}
		if (logWriter != null) {
			logWriter.println(message);
			logWriter.flush();
		}
	}

	private static void readFromStdin() throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		boolean inHeader = true;
		int protocolVersion = 1;

		log("Reading from STDIN...");

		String line;
		while ((line = reader.readLine()) != null) {
			String trimmed = line.trim();
			log("STDIN LINE: [" + trimmed + "]");

			if (trimmed.isEmpty()) {
				log("Detected header separator (empty line)");
				inHeader = false;
				continue;
			}

			if (inHeader) {
				if (trimmed.startsWith("VERSION ")) {
					try {
						protocolVersion = Integer.parseInt(trimmed.substring(8));
						log("Detected APT protocol version: " + protocolVersion);
					} catch (Exception e) {
						log("Failed to parse protocol version, assuming 1");
					}
				}
				// Config lines in protocol 2/3 don't start with /
				// Package lines in protocol 1 start with /
				if (trimmed.startsWith("/")) {
					log("Detected path in header, switching to data mode (Protocol 1?)");
					inHeader = false;
				} else {
					continue;
				}
			}

			if (protocolVersion == 1) {
				if (trimmed.startsWith("/")) {
					processPath(trimmed);
				}
			} else {
				// Version 2/3: fields are: pkg version architecture status path
				// We expect at least 5 fields.
				String[] parts = trimmed.split("\\s+");
				if (parts.length >= 5) {
					String debPath = parts[parts.length - 1];
					if (debPath.startsWith("/") && debPath.endsWith(".deb")) {
						log("Found deb package from APT protocol " + protocolVersion + ": " + debPath);
						processPath(debPath);
					} else {
						log("Ignoring line (not a deb path): " + debPath);
					}
				} else {
					log("Ignoring malformed line: " + trimmed);
				}
			}
		}
		log("Finished reading from STDIN.");
	}

	private static void processPath(String path) throws Exception {
		log("Processing path: " + path);
		File file = new File(path);
		if (!file.exists()) {
			log("File does not exist: " + path);
			return;
		}

		if (path.endsWith(".deb")) {
			patchDeb(file);
		} else {
			log("Patching directory/file: " + path);
			TermuxPatcher.patchDirectory(file);
		}
	}

	private static void patchDeb(File debFile) throws Exception {
		log("Patching .deb: " + debFile.getAbsolutePath());
		// Use a temp dir in the same parent dir to avoid cross-device issues if any
		File tempDir = new File(debFile.getParentFile(), "tmp_patch_" + debFile.getName());
		if (tempDir.exists()) deleteDirectory(tempDir);
		if (!tempDir.mkdirs()) {
			throw new Exception("Failed to create temp directory: " + tempDir.getAbsolutePath());
		}

		try {
			log("Extracting to " + tempDir.getAbsolutePath());
			runCommand(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dpkg-deb", "-R", debFile.getAbsolutePath(), tempDir.getAbsolutePath());

			log("Renaming entries...");
			renameEntries(tempDir);

			log("Patching content...");
			TermuxPatcher.patchDirectory(tempDir);

			log("Fixing maintainer script permissions...");
			fixMaintainerScriptPermissions(tempDir);

			log("Repacking...");
			runCommand(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dpkg-deb", "-b", tempDir.getAbsolutePath(), debFile.getAbsolutePath());
			log("Success.");
		} catch (Exception e) {
			log("Error during deb patching: " + e.getMessage());
			throw e;
		} finally {
			deleteDirectory(tempDir);
		}
	}

	private static void renameEntries(File dir) {
		File[] files = dir.listFiles();
		if (files == null) return;

		for (File child : files) {
			if (child.isDirectory()) {
				renameEntries(child);
			}

			String name = child.getName();
			String newName = name;
			if (newName.contains("com.termux")) {
				newName = newName.replace("com.termux", TermuxConstants.TERMUX_PACKAGE_NAME);
			}
			// Rename standalone 'termux' to 'csmide' in filenames as well
			if (newName.contains("termux")) {
				newName = newName.replace("termux", "csmide");
			}

			if (!newName.equals(name)) {
				File newFile = new File(child.getParentFile(), newName);
				if (child.renameTo(newFile)) {
					log("Renamed " + name + " to " + newName);
				}
			}
		}
	}

	private static void deleteDirectory(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) deleteDirectory(file);
				else file.delete();
			}
		}
		dir.delete();
	}

	private static void fixMaintainerScriptPermissions(File tempDir) {
		File debianDir = new File(tempDir, "DEBIAN");
		if (debianDir.exists() && debianDir.isDirectory()) {
			File[] files = debianDir.listFiles();
			if (files != null) {
				for (File file : files) {
					// Maintainer scripts like postinst must be executable (>= 0555).
					// We set them to 0755 to ensure they can be run during installation.
					try {
						java.nio.file.Path path = file.toPath();
						java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");
						java.nio.file.Files.setPosixFilePermissions(path, perms);
						log("Fixed permissions for: " + file.getName());
					} catch (Exception e) {
						// Fallback to chmod via shell if PosixFilePermissions fail (e.g. non-POSIX FS)
						try {
							Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()}).waitFor();
							log("Fixed permissions (chmod) for: " + file.getName());
						} catch (Exception e2) {
							log("Failed to fix permissions for " + file.getName() + ": " + e.getMessage());
						}
					}
				}
			}
		}
	}

	private static void runCommand(String... command) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (String s : command) sb.append(s).append(" ");
		log("Exec: " + sb.toString().trim());
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		String pathEnv = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":" + System.getenv("PATH") + ":/system/bin:/system/xbin";
		pb.environment().put("PATH", pathEnv);
		pb.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
		pb.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
		pb.environment().put("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
		Process p = pb.start();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			String line;
			while ((line = r.readLine()) != null) {
				log("  OUT: " + line);
			}
		}
		int exitCode = p.waitFor();
		if (exitCode != 0) {
			throw new Exception("Command failed with code " + exitCode);
		}
	}
}
