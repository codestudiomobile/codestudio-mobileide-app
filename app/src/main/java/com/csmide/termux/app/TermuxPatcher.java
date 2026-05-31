package com.csmide.termux.app;

import android.util.Log;

import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * TermuxPatcher handles changing the package name in bootstrap files.
 * It replaces the default "com.termux" package name with the current application's package name.
 */
public class TermuxPatcher {

	private static final String LOG_TAG = "TermuxPatcher";
	private static final String OLD_PACKAGE_NAME = "com.termux";
	private static final String NEW_PACKAGE_NAME = TermuxConstants.TERMUX_PACKAGE_NAME;

	private static final Set<String> COMPRESSED_EXTENSIONS = new HashSet<>(Arrays.asList(
			"gz", "bz2", "xz", "zst", "zip", "apk", "deb", "jar", "png", "jpg", "jpeg", "gif", "pdf"
	));

	/**
	 * Patches the extracted bootstrap files in the staging directory.
	 *
	 * @param stagingDir The directory containing the extracted bootstrap files.
	 */
	public static boolean patchBootstrap(File stagingDir) {
		log("Patching bootstrap in " + (stagingDir != null ? stagingDir.getAbsolutePath() : "null"));
		try {
			if (stagingDir == null || !stagingDir.exists()) {
				logError("Staging directory does not exist: " + stagingDir, null);
				return false;
			}

			log("Renaming entries...");
			renameEntries(stagingDir);

			log("Patching content...");
			int patchedCount = patchDirectory(stagingDir);
			log("Bootstrap patching completed. Patched " + patchedCount + " files.");
			return true;
		} catch (Exception e) {
			logError("Bootstrap patching failed", e);
			return false;
		}
	}

	public static void renameEntries(File dir) {
		if (dir == null || isSymlink(dir))
			return; // Do not recurse into symlinked directories
		File[] files = dir.listFiles();
		if (files == null)
			return;

		for (File child : files) {
			try {
				if (child.isDirectory()) {
					renameEntries(child);
				}

				String name = child.getName();
				String newName = name;
				if (newName.contains(OLD_PACKAGE_NAME)) {
					newName = newName.replace(OLD_PACKAGE_NAME, NEW_PACKAGE_NAME);
				}

				if (!newName.equals(name)) {
					File newFile = new File(child.getParentFile(), newName);
					if (child.renameTo(newFile)) {
						log("Renamed " + name + " to " + newName);
					}
				}
			} catch (Throwable t) {
				logError("Error renaming " + child.getAbsolutePath(), t);
			}
		}
	}

	private static void log(String message) {
		String msg = (message == null) ? "null" : message;
		try {
			Log.i(LOG_TAG, msg);
		} catch (Throwable e) {
			System.out.println(msg);
		}
	}

	public static int patchDirectory(File file) {
		if (file == null) return 0;
		int count = 0;
		try {
			if (isSymlink(file)) {
				if (patchSymlink(file)) {
					count++;
				}
			} else if (file.isDirectory()) {
				File[] files = file.listFiles();
				if (files == null) return 0;

				for (File child : files) {
					try {
						count += patchDirectory(child);
					} catch (Throwable t) {
						logError("Failed to patch entry in directory: " + child.getAbsolutePath(), t);
					}
				}
			} else if (file.isFile()) {
				String name = file.getName();
				if (name.endsWith(".pyc")) {
					if (file.delete()) {
						log("Deleted python bytecode cache: " + file.getAbsolutePath());
						return 1;
					}
				}

				if (patchFile(file)) {
					count++;
				}
			}
		} catch (Throwable t) {
			logError("Failed to patch: " + file.getAbsolutePath(), t);
		}
		return count;
	}

	public static boolean isSymlink(File file) {
		if (file == null) return false;
		try {
			// Using getCanonicalFile to check for symlinks in a way compatible with all Android versions.
			File parent = file.getParentFile();
			File canon;
			if (parent == null) {
				canon = file;
			} else {
				File canonParent = parent.getCanonicalFile();
				canon = new File(canonParent, file.getName());
			}
			return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
		} catch (Throwable e) {
			return false;
		}
	}

	private static boolean patchSymlink(File file) {
		try {
			Path path = file.toPath();
			String target = Files.readSymbolicLink(path).toString();
			String newTarget = target;
			if (newTarget.contains(OLD_PACKAGE_NAME)) {
				newTarget = newTarget.replace(OLD_PACKAGE_NAME, NEW_PACKAGE_NAME);
			}

			if (!newTarget.equals(target)) {
				Files.delete(path);
				Files.createSymbolicLink(path, Paths.get(newTarget));
				log("Patched symlink: " + file.getAbsolutePath() + " -> " + newTarget);
				return true;
			}
		} catch (Exception e) {
			logError("Failed to patch symlink: " + file.getAbsolutePath(), e);
		}
		return false;
	}

	public static boolean patchFile(File file) {
		// Skip patching compressed files in-place to avoid corrupting them
		String name = file.getName().toLowerCase();
		int lastDot = name.lastIndexOf('.');
		if (lastDot != -1) {
			String ext = name.substring(lastDot + 1);
			if (COMPRESSED_EXTENSIONS.contains(ext)) {
				return false;
			}
		}

		// Ensure file is writable before attempting to patch
		boolean wasWritable = file.canWrite();
		if (!wasWritable) {
			try {
				if (!file.setWritable(true)) {
					logWarning("Failed to set writable: " + file.getAbsolutePath());
				}
			} catch (SecurityException e) {
				return false;
			}
		}

		boolean isElf = isElfBinary(file);

		boolean packagePatched = patchStringInFile(file, OLD_PACKAGE_NAME, NEW_PACKAGE_NAME);

		boolean alignmentPatched = isElf && patchElfAlignment(file);

		return packagePatched || alignmentPatched;
	}

	private static boolean isElfBinary(File file) {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			if (raf.length() < 4) return false;
			byte[] magic = new byte[4];
			raf.readFully(magic);
			return magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean isTextFile(File file) {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			long length = raf.length();
			if (length == 0) return true;
			
			// Read sample to check for binary-ness
			byte[] buffer = new byte[(int) Math.min(length, 8192)];
			raf.readFully(buffer);
			
			int nullCount = 0;
			int controlChars = 0;
			for (byte b : buffer) {
				if (b == 0) nullCount++;
				else if (b < 32 && b != '\n' && b != '\r' && b != '\t' && b != 27 /* ESC */) controlChars++;
			}
			
			// Heuristic: few nulls and few control chars usually means text
			return (nullCount * 100.0 / buffer.length) < 0.1 && (controlChars * 100.0 / buffer.length) < 2.0;
		} catch (IOException e) {
			return false;
		}
	}

	private static void logError(String message, Throwable e) {
		String msg = (message == null) ? "null error" : message;
		try {
			Log.e(LOG_TAG, msg, e);
		} catch (Throwable err) {
			System.err.println(msg);
			if (e != null) e.printStackTrace();
		}
	}

	private static void logWarning(String message) {
		String msg = (message == null) ? "null warning" : message;
		try {
			Log.w(LOG_TAG, msg);
		} catch (Throwable err) {
			System.err.println("WARNING: " + msg);
		}
	}

	private static boolean patchPackageName(File file) {
		return patchStringInFile(file, OLD_PACKAGE_NAME, NEW_PACKAGE_NAME);
	}

	private static boolean patchStringInFile(File file, String oldStr, String newStr) {
		// Use RandomAccessFile to avoid loading huge files into memory
		// Since both strings are same length, we can patch in-place.

		byte[] oldBytes = oldStr.getBytes(StandardCharsets.UTF_8);
		byte[] newBytes = newStr.getBytes(StandardCharsets.UTF_8);

		if (oldBytes.length != newBytes.length) {
			logError("CRITICAL: String length mismatch! " + oldStr + " vs " + newStr, null);
			return false;
		}

		try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
			long length = raf.length();
			if (length < oldBytes.length) return false;

			byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
			long pos = 0;
			boolean modified = false;
			int lastPercent = -1;

			while (pos <= length - oldBytes.length) {
				raf.seek(pos);
				int bytesToRead = (int) Math.min(buffer.length, length - pos);
				raf.readFully(buffer, 0, bytesToRead);

				// Log progress for very large files
				if (length > 20 * 1024 * 1024) {
					int percent = (int) ((pos * 100) / length);
					if (percent % 10 == 0 && percent != lastPercent) {
						System.out.print(percent + "% ");
						lastPercent = percent;
					}
				}

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
				log("Patched '" + oldStr + "' in: " + file.getAbsolutePath());
				if (length > 20 * 1024 * 1024) System.out.println("100% Done.");
			}
			return modified;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Patches ELF files to ensure loadable segments have at least 16KB alignment.
	 * This is required for compatibility with Android 15's 16KB page size.
	 * Robustly handles 32/64-bit, Little/Big Endian, and various architectures.
	 */
	private static boolean patchElfAlignment(File file) {
		try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
			long fileLength = raf.length();
			if (fileLength < 64) return false;
			byte[] header = new byte[64];
			raf.readFully(header);

			// Check ELF magic: 7f 45 4c 46
			if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F')
				return false;

			// EI_CLASS: 1 = 32-bit, 2 = 64-bit
			int eiClass = header[4];
			if (eiClass != 1 && eiClass != 2) return false;
			boolean is64Bit = (eiClass == 2);

			// EI_DATA: 1 = Little Endian, 2 = Big Endian
			int eiData = header[5];
			if (eiData != 1 && eiData != 2) return false;
			ByteOrder order = (eiData == 1) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

			long phoff;
			int phentsize;
			int phnum;

			ByteBuffer bb = ByteBuffer.wrap(header).order(order);
			if (is64Bit) {
				// e_phoff is at 32 (8 bytes)
				phoff = bb.getLong(32);
				// e_phentsize is at 54 (2 bytes)
				phentsize = bb.getShort(54) & 0xFFFF;
				// e_phnum is at 56 (2 bytes)
				phnum = bb.getShort(56) & 0xFFFF;
			} else {
				// e_phoff is at 28 (4 bytes)
				phoff = bb.getInt(28) & 0xFFFFFFFFL;
				// e_phentsize is at 42 (2 bytes)
				phentsize = bb.getShort(42) & 0xFFFF;
				// e_phnum is at 44 (2 bytes)
				phnum = bb.getShort(44) & 0xFFFF;
			}

			if (phoff == 0 || phnum == 0 || phentsize == 0) return false;
			if (phoff + (long) phnum * phentsize > fileLength) return false;

			boolean modified = false;
			for (int i = 0; i < phnum; i++) {
				long entryOffset = phoff + (long) i * phentsize;
				raf.seek(entryOffset);
				byte[] entry = new byte[phentsize];
				raf.readFully(entry);

				ByteBuffer ebb = ByteBuffer.wrap(entry).order(order);
				// p_type is the first 4 bytes for both 32 and 64 bit
				int type = ebb.getInt(0);

				if (type == 1) { // PT_LOAD
					// p_align offset:
					// ELF32: offset 28 (4 bytes)
					// ELF64: offset 48 (8 bytes)
					int alignOffset = is64Bit ? 48 : 28;
					if (alignOffset + (is64Bit ? 8 : 4) > phentsize) continue;

					long p_align = is64Bit ? ebb.getLong(alignOffset) : (ebb.getInt(alignOffset) & 0xFFFFFFFFL);

					// Increase alignment to 16KB (16384) if it's smaller and non-zero (power of 2)
					if (p_align > 0 && p_align < 16384 && (p_align & (p_align - 1)) == 0) {
						raf.seek(entryOffset + alignOffset);
						byte[] newAlignData = new byte[is64Bit ? 8 : 4];
						ByteBuffer nbb = ByteBuffer.wrap(newAlignData).order(order);
						if (is64Bit) {
							nbb.putLong(16384);
						} else {
							nbb.putInt(16384);
						}
						raf.write(newAlignData);
						modified = true;
					}
				}
			}
			if (modified) {
				log("Fixed ELF alignment for 16KB page size (Android 15+): " + file.getName());
			}
			return modified;
		} catch (Exception e) {
			// Silent fail for non-compatible or malformed files
			return false;
		}
	}
}
