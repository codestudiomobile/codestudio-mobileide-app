package com.cs.ide.termux.app;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Universal on-the-fly byte patcher for Termux packages.
 * Dynamically replaces com.termux with com.cs.ide while preserving DEX and ZIP checksum integrity.
 * This acts as a robust layer between file fetching and usage.
 */
public class TermuxPatcher {

    private static final String TAG = "TermuxPatcher";
    public static final byte[] TARGET = "com.termux".getBytes();
    public static final byte[] REPLACEMENT = "com.cs.ide".getBytes();

    /**
     * Patches a file's content on the fly.
     * 
     * @param fileName The name of the file (to determine type)
     * @param input    The raw bytes of the file
     * @return Patched bytes or original if patching isn't safe or possible
     */
    public static byte[] patchFile(String fileName, byte[] input) {
        if (fileName == null || input == null || input.length == 0) return input;

        String lowerName = fileName.toLowerCase();

        // 1. Handle Archives recursively
        if (lowerName.endsWith(".apk") || lowerName.endsWith(".jar") || lowerName.endsWith(".zip")) {
            return patchZip(input);
        }

        // 2. Handle DEX files (Android Dalvik Executables)
        if (lowerName.endsWith(".dex")) {
            byte[] patched = input.clone();
            replaceBytesInPlace(patched, TARGET, REPLACEMENT);
            fixDexChecksums(patched);
            return patched;
        }

        // 3. Skip cryptographic files to avoid corruption
        if (lowerName.endsWith(".gpg") || lowerName.endsWith(".sig") || lowerName.endsWith(".key")) {
            return input;
        }

        // 4. Robust script and repository patching
        if (isTextFile(lowerName)) {
            String contentString = new String(input);
            boolean modified = false;

            // Fix 'am' wrapper and storage setup script to prevent Aborted/broadcast errors
            if (lowerName.endsWith("/bin/am") || lowerName.endsWith("/bin/termux-setup-storage")) {
                // Ensure am broadcast uses the correct package and doesn't have stray "com.termux" references
                contentString = contentString.replace("com.termux", "com.cs.ide");
                // Fix potential shell variable issues
                contentString = contentString.replace("TERMUX_APP__USER_ID", "TERMUX_APP__USER_ID:-0");
                
                // Inject GPG key fetcher into commonly used scripts
                if (!contentString.contains("termux-autofix-keys")) {
                    String keyFetcher = "\n# Auto-fetch Termux GPG keys if missing\n" +
                        "if [ ! -f \"$PREFIX/etc/apt/trusted.gpg.d/termux-autofix.gpg\" ]; then\n" +
                        "  echo \"[*] Fetching missing Termux GPG keys...\"\n" +
                        "  mkdir -p \"$PREFIX/etc/apt/trusted.gpg.d/\"\n" +
                        "  (curl -sL https://packages.termux.dev/termux-main/termux-keyring.gpg -o \"$PREFIX/etc/apt/trusted.gpg.d/termux-autofix.gpg\" || wget -q https://packages.termux.dev/termux-main/termux-keyring.gpg -O \"$PREFIX/etc/apt/trusted.gpg.d/termux-autofix.gpg\") 2>/dev/null &\n" +
                        "fi\n";
                    contentString = keyFetcher + contentString;
                }
                modified = true;
            }

            // Patch repository files to use our custom package name in paths
            if (contentString.contains("com.termux")) {
                contentString = contentString.replace("com.termux", "com.cs.ide");
                modified = true;
            }

            if (modified) return contentString.getBytes();
        }

        // 5. For everything else (ELF binaries, etc.), do a fast byte replacement
        byte[] output = input.clone();
        replaceBytesInPlace(output, TARGET, REPLACEMENT);

        return output;
    }

    private static boolean isTextFile(String lowerName) {
        return lowerName.endsWith(".list") || lowerName.endsWith(".sh") || 
               lowerName.contains("/bin/") || lowerName.contains("/etc/") ||
               lowerName.endsWith(".conf") || lowerName.endsWith(".prop");
    }

    /**
     * Efficiently replaces occurrences of target bytes with replacement bytes in a byte array.
     */
    private static void replaceBytesInPlace(byte[] input, byte[] target, byte[] replacement) {
        if (input == null || input.length == 0 || target == null || target.length == 0 || replacement == null || replacement.length != target.length) {
            return;
        }
        for (int i = 0; i <= input.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (input[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, input, i, replacement.length);
                i += target.length - 1; // Skip ahead
            }
        }
    }

    /**
     * Re-calculates and repairs DEX file checksums (Adler32 and SHA-1).
     */
    private static void fixDexChecksums(byte[] dex) {
        if (dex == null || dex.length < 40) return;
        if (dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') return;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(dex, 32, dex.length - 32);
            byte[] sha1 = md.digest();
            System.arraycopy(sha1, 0, dex, 12, 20);

            Adler32 adler32 = new Adler32();
            adler32.update(dex, 12, dex.length - 12);
            long checksum = adler32.getValue();
            dex[8] = (byte) (checksum & 0xff);
            dex[9] = (byte) ((checksum >> 8) & 0xff);
            dex[10] = (byte) ((checksum >> 16) & 0xff);
            dex[11] = (byte) ((checksum >> 24) & 0xff);
        } catch (Exception e) {
            Log.e(TAG, "Failed to repair DEX checksums", e);
        }
    }

    private static byte[] patchZip(byte[] zipBytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes);
            ZipInputStream zis = new ZipInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream entryOut = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    entryOut.write(buffer, 0, len);
                }
                byte[] entryBytes = entryOut.toByteArray();
                byte[] patchedEntryBytes = patchFile(entry.getName(), entryBytes);

                ZipEntry newEntry = new ZipEntry(entry.getName());
                if (entry.getMethod() == ZipEntry.STORED || entry.getName().endsWith(".dex")) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setSize(patchedEntryBytes.length);
                    newEntry.setCompressedSize(patchedEntryBytes.length);
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    crc.update(patchedEntryBytes);
                    newEntry.setCrc(crc.getValue());
                } else {
                    newEntry.setMethod(ZipEntry.DEFLATED);
                }
                zos.putNextEntry(newEntry);
                zos.write(patchedEntryBytes);
                zos.closeEntry();
            }
            zos.close();
            zis.close();
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch archive", e);
            return zipBytes;
        }
    }
}
