package com.cs.ide.termux.app;

import android.util.Log;

import com.cs.ide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
		if (isSymlink(file)) {
			if (patchSymlink(file)) {
				count++;
			}
		} else if (file.isDirectory()) {
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

	private static boolean isSymlink(File file) {
		try {
			return android.system.Os.lstat(file.getAbsolutePath()).st_mode == android.system.OsConstants.S_IFLNK;
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean patchSymlink(File file) {
		try {
			String target = android.system.Os.readlink(file.getAbsolutePath());
			if (target.contains(OLD_PACKAGE_NAME)) {
				String newTarget = target.replace(OLD_PACKAGE_NAME, NEW_PACKAGE_NAME);
				if (!file.delete()) {
					Log.w(LOG_TAG, "Failed to delete symlink for replacement: " + file.getAbsolutePath());
					return false;
				}
				android.system.Os.symlink(newTarget, file.getAbsolutePath());
				log("Patched symlink: " + file.getAbsolutePath() + " -> " + newTarget);
				return true;
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, "Failed to patch symlink: " + file.getAbsolutePath(), e);
		}
		return false;
	}

	private static boolean patchFile(File file) {
		// Ensure file is writable before attempting to patch
		boolean wasWritable = file.canWrite();
		if (!wasWritable) {
			if (!file.setWritable(true)) {
				Log.w(LOG_TAG, "Failed to set writable: " + file.getAbsolutePath());
			}
		}

		boolean packagePatched = patchPackageName(file);
		boolean alignmentPatched = patchElfAlignment(file);

		return packagePatched || alignmentPatched;
	}

	private static boolean patchPackageName(File file) {
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

			// Skip binary files that are definitely not scripts or ELFs if needed?
			// No, TermuxPatcher should be thorough.

			// Visual feedback for large files
			if (length > 5 * 1024 * 1024) {
				System.out.println("Patching large file: " + file.getName() + " (" + (length / 1024 / 1024) + "MB)...");
			}

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

				boolean bufferModified = false;
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
						bufferModified = true;
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
				log("Patched package name in: " + file.getAbsolutePath());
				if (length > 20 * 1024 * 1024) System.out.println("100% Done.");
			}
			return modified;
		} catch (IOException e) {
			// Some files might be busy, read-only, or not a regular file
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
