package com.cs.ide.termux.app;

import androidx.annotation.Keep;

import com.cs.ide.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;

/**
 * CLI tool to patch packages by replacing 'com.termux' with the app's package name.
 * It can patch installed directories or .deb files before installation.
 */
@Keep
public class TermuxPackagePatcher {

	private static PrintWriter logWriter;

	public static void main(String[] args) {
		setupLogging();
		log("Starting Termux Package Patcher...");
		log("Args: " + String.join(" ", args));

		try {
			if (args.length > 0) {
				for (String path : args) {
					if (path.equals("--stdin")) {
						log("Reading paths from stdin (APT hook mode)");
						readFromStdin();
					} else {
						processPath(path);
					}
				}
			} else {
				log("No args, patching entire prefix: " + TermuxConstants.TERMUX_PREFIX_DIR_PATH);
				File prefixDir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
				TermuxPatcher.patchDirectory(prefixDir);
			}
			log("Finished successfully.");
		} catch (Exception e) {
			log("FATAL ERROR: " + e.getMessage());
			if (logWriter != null) {
				e.printStackTrace(logWriter);
			}
			System.err.println("Termux Package Patcher: Error - " + e.getMessage());
			System.exit(1);
		} finally {
			if (logWriter != null) {
				logWriter.close();
			}
		}
	}

	private static void setupLogging() {
		try {
			File logFile = new File(TermuxConstants.TERMUX_FILES_DIR_PATH + "/usr/tmp/patcher.log");
			if (!logFile.getParentFile().exists()) {
				logFile.getParentFile().mkdirs();
			}
			logWriter = new PrintWriter(new FileWriter(logFile, true));
		} catch (IOException e) {
			// Can't log to file
		}
	}

	private static void log(String message) {
		String msg = "[" + new Date() + "] " + message;
		System.out.println(msg);
		if (logWriter != null) {
			logWriter.println(msg);
			logWriter.flush();
		}
	}

	private static void readFromStdin() throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;

		boolean inHeader = true;
		int protocolVersion = 1;

		while ((line = reader.readLine()) != null) {
			log("STDIN: " + line);
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
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
				// If it looks like a path and we are in header, we probably missed the blank line or it's version 1
				if (trimmed.startsWith("/")) {
					inHeader = false;
				} else {
					continue;
				}
			}

			if (protocolVersion == 1) {
				processPath(trimmed);
			} else {
				// Version 2/3: action is the last field
				String[] parts = trimmed.split("\\s+");
				if (parts.length >= 5) {
					String action = parts[parts.length - 1];
					if (action.startsWith("/") && action.endsWith(".deb")) {
						processPath(action);
					}
				}
			}
		}
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

		for (File file : files) {
			if (file.isDirectory()) {
				renameEntries(file);
			}

			String name = file.getName();
			if (name.contains("com.termux")) {
				String newName = name.replace("com.termux", TermuxConstants.TERMUX_PACKAGE_NAME);
				File newFile = new File(file.getParentFile(), newName);
				if (file.renameTo(newFile)) {
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
						android.system.Os.chmod(file.getAbsolutePath(), 0755);
						log("Fixed permissions for: " + file.getName());
					} catch (Exception e) {
						log("Failed to fix permissions for " + file.getName() + ": " + e.getMessage());
					}
				}
			}
		}
	}

	private static void runCommand(String... command) throws Exception {
		log("Exec: " + String.join(" ", command));
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		String pathEnv = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":" + System.getenv("PATH") + ":/system/bin:/system/xbin";
		pb.environment().put("PATH", pathEnv);
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
