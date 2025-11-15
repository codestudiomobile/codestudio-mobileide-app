package com.cs.ide.app;

public class LanguagePack {
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_INSTALLED = 1;
    public static final int STATUS_INSTALLING = 2;
    public final String key;
    public final String name;
    public final String installCommand;
    public final String checkCommand;
    public int status;

    public LanguagePack(String key, String name, String installCommand, String checkCommand, int status) {
        this.key = key;
        this.name = name;
        this.installCommand = installCommand;
        this.checkCommand = checkCommand;
        this.status = status;
    }

    public String getUninstallCommand() {
        return installCommand.replace("install", "uninstall");
    }

    @Override
    public String toString() {
        return name + " (" + (status == STATUS_INSTALLED ? "Installed" : "Available") + ")";
    }
}
