package com.cs.ide.app;
import android.content.Context;
import java.io.File;
public class EnvironmentSetup {
    public static File getEnvironmentRoot(Context context) {
        return context.getFilesDir();
    }
    public static File getBinDir(Context context) {
        return new File(getEnvironmentRoot(context), "bin");
    }
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
