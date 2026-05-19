package com.cs.ide.termux.shared.termux.shell.command.environment;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.cs.ide.app.utils.AppPreferences;

import com.cs.ide.termux.shared.errors.Error;
import com.cs.ide.termux.shared.file.FileUtils;
import com.cs.ide.termux.shared.logger.Logger;
import com.cs.ide.termux.shared.shell.command.environment.AndroidShellEnvironment;
import com.cs.ide.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.cs.ide.termux.shared.termux.TermuxBootstrap;
import com.cs.ide.termux.shared.termux.TermuxConstants;
import com.cs.ide.termux.shared.termux.shell.TermuxShellUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Environment for Termux.
 */
public class TermuxShellEnvironment extends AndroidShellEnvironment {

    private static final String LOG_TAG = "TermuxShellEnvironment";

    /**
     * Environment variable for the termux
     * {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH}.
     */
    public static final String ENV_PREFIX = "PREFIX";

    public TermuxShellEnvironment() {
        super();
        shellCommandShellEnvironment = new TermuxShellCommandShellEnvironment();
    }

    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void init(@NonNull Context currentPackageContext) {
        TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext);
    }

    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void writeEnvironmentToFile(@NonNull Context currentPackageContext) {
        HashMap<String, String> environmentMap = new TermuxShellEnvironment().getEnvironment(currentPackageContext,
                false);
        String environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap);

        // Write environment string to temp file and then move to final location since
        // otherwise
        // writing may happen while file is being sourced/read
        Error error = FileUtils.writeTextToFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                Charset.defaultCharset(), environmentString, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }

        error = FileUtils.moveRegularFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                TermuxConstants.TERMUX_ENV_FILE_PATH, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    /** Get shell environment for Termux. */
    @NonNull
    @Override
    public HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext, boolean isFailSafe) {

        // Termux environment builds upon the Android environment
        HashMap<String, String> environment = super.getEnvironment(currentPackageContext, isFailSafe);

        HashMap<String, String> termuxAppEnvironment = TermuxAppShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxAppEnvironment != null)
            environment.putAll(termuxAppEnvironment);

        HashMap<String, String> termuxApiAppEnvironment = TermuxAPIShellEnvironment
                .getEnvironment(currentPackageContext);
        if (termuxApiAppEnvironment != null)
            environment.putAll(termuxApiAppEnvironment);

        boolean useProot = !isFailSafe && new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/proot").exists();
        String fakePackagePath = "/data/data/com.termux";
        String homePath = useProot ? fakePackagePath + "/files/home" : TermuxConstants.TERMUX_HOME_DIR_PATH;
        String prefixPath = useProot ? fakePackagePath + "/files/usr" : TermuxConstants.TERMUX_PREFIX_DIR_PATH;


        environment.put(ENV_HOME, homePath);
        environment.put(ENV_PREFIX, prefixPath);

        if (useProot) {
            environment.put("TERMUX_PROOT_ACTIVE", "1");
        }

        // Pass the currently opened folder to the shell environment
        SharedPreferences prefs = currentPackageContext.getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
        String lastFolderUriStr = prefs.getString(AppPreferences.LAST_FOLDER_URI_KEY, null);
        if (lastFolderUriStr != null) {
            String path = com.cs.ide.app.utils.FileUtils.getAbsolutePathFromUri(currentPackageContext, Uri.parse(lastFolderUriStr));
            if (path != null) {
                environment.put("OPENED_FOLDER", path);
            }
        }

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that
        // system binaries can be used
        if (!isFailSafe) {
            String tmpDir = useProot ? prefixPath + "/tmp" : TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
            environment.put(ENV_TMPDIR, tmpDir);
            
            String binPath = useProot ? prefixPath + "/bin" : TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
            
            if (TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                // Termux in android 5/6 era shipped busybox binaries in applets directory
                environment.put(ENV_PATH, binPath + ":" + binPath + "/applets");
                environment.put(ENV_LD_LIBRARY_PATH, useProot ? prefixPath + "/lib" : TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            } else {
                // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should
                // be unset by default
                environment.put(ENV_PATH, binPath);
                environment.remove(ENV_LD_LIBRARY_PATH);
            }
        }

        return environment;
    }


    @NonNull
    @Override
    public String getDefaultWorkingDirectoryPath() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    @NonNull
    @Override
    public String getDefaultBinPath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    }

    @NonNull
    @Override
    public String[] setupShellCommandArguments(@NonNull String executable, String[] arguments) {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments);
    }

}
