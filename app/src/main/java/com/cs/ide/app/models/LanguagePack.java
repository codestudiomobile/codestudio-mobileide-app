package com.cs.ide.app.models;

/**
 * LanguagePack represents a set of tools or an environment that can be installed 
 * (e.g., Python, C++, etc.). It holds the metadata and commands required 
 * for its management via the package manager.
 */
public class LanguagePack {
    /** Status indicating the package is not installed but can be downloaded. */
    public static final int STATUS_AVAILABLE = 0;
    /** Status indicating the package is currently installed on the system. */
    public static final int STATUS_INSTALLED = 1;
    /** Status indicating an installation or uninstallation is currently in progress. */
    public static final int STATUS_INSTALLING = 2;

    public static final int TYPE_RUNTIME = 0;
    public static final int TYPE_SUGGESTION = 1;

    /** Unique identifier for the package (often the apt package name). */
    public final String key;
    /** Human-readable name of the language or tool. */
    public final String name;
    /** The shell command used to install the package. */
    public final String installCommand;
    /** The shell command used to check if the package is already installed. */
    public final String checkCommand;
    /** The current installation status of the package. */
    public int status;
    public final int type;
    public String companionKey;

    /**
     * Constructs a new LanguagePack.
     *
     * @param key            The package key.
     * @param name           The display name.
     * @param installCommand The installation command.
     * @param checkCommand   The status check command.
     * @param status         Initial status.
     * @param type           Type of package (Runtime or Suggestion).
     */
    public LanguagePack(String key, String name, String installCommand, String checkCommand, int status, int type) {
        this.key = key;
        this.name = name;
        this.installCommand = installCommand;
        this.checkCommand = checkCommand;
        this.status = status;
        this.type = type;
    }

    /**
     * Generates the command for uninstalling this package by modifying the install command.
     *
     * @return The uninstallation command string.
     */
    public String getUninstallCommand() {
        return installCommand.replace("install", "uninstall");
    }

    @Override
    public String toString() {
        return name + " (" + (status == STATUS_INSTALLED ? "Installed" : "Available") + ")";
    }
}
