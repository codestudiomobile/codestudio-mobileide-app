package com.cs.ide.termux.app;

import com.cs.ide.termux.shared.logger.Logger;
import com.cs.ide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    public static void patchBootstrap(File stagingDir) {
        Logger.logInfo(LOG_TAG, "Patching bootstrap in " + stagingDir.getAbsolutePath());
        patchDirectory(stagingDir);
        Logger.logInfo(LOG_TAG, "Bootstrap patching completed.");
    }

    private static void patchDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                patchDirectory(file);
            } else if (file.isFile()) {
                patchFile(file);
            }
        }
    }

    private static void patchFile(File file) {
        // We only patch files that might contain the package name.
        // This includes binaries (for hardcoded paths) and scripts.
        // Since com.termux and com.cs.ide are both 10 characters, we can do a simple binary replacement.
        
        try {
            byte[] content = readFile(file);
            boolean modified = false;
            
            byte[] oldBytes = OLD_PACKAGE_NAME.getBytes(StandardCharsets.UTF_8);
            byte[] newBytes = NEW_PACKAGE_NAME.getBytes(StandardCharsets.UTF_8);
            
            for (int i = 0; i <= content.length - oldBytes.length; i++) {
                boolean match = true;
                for (int j = 0; j < oldBytes.length; j++) {
                    if (content[i + j] != oldBytes[j]) {
                        match = false;
                        break;
                    }
                }
                
                if (match) {
                    System.arraycopy(newBytes, 0, content, i, newBytes.length);
                    modified = true;
                    i += oldBytes.length - 1;
                }
            }
            
            if (modified) {
                writeFile(file, content);
                Logger.logInfo(LOG_TAG, "Patched file: " + file.getAbsolutePath());
                if (file.getName().equals("am") || file.getName().equals("termux-am")) {
                    Logger.logInfo(LOG_TAG, "Content of " + file.getName() + ":\n" + new String(content, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            Logger.logError(LOG_TAG, "Failed to patch file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private static byte[] readFile(File file) throws IOException {
        long length = file.length();
        byte[] bytes = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = in.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        return bytes;
    }

    private static void writeFile(File file, byte[] content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
