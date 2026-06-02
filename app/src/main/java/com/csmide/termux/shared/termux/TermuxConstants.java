package com.csmide.termux.shared.termux;

import android.annotation.SuppressLint;
import android.content.Intent;

import com.csmide.termux.shared.shell.command.ExecutionCommand;
import com.csmide.termux.shared.shell.command.ExecutionCommand.Runner;

import java.io.File;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

/**
 * A class that defines shared constants of the Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 */
// @formatter:off
public final class TermuxConstants {

	/* Termux organization and GitHub variables */
	public static final String TERMUX_GITHUB_ORGANIZATION_NAME = "termux";
	public static final String TERMUX_GITHUB_ORGANIZATION_URL = "https://github.com/" + TERMUX_GITHUB_ORGANIZATION_NAME;
	public static final String FDROID_PACKAGES_BASE_URL = "https://f-droid.org/en/packages";
	/* App and Package names/URLs */
	public static final String TERMUX_APP_NAME = "Termux";
	public static final String TERMUX_PACKAGE_NAME = "com.csmide";
	public static final String TERMUX_GITHUB_REPO_NAME = "termux-app";
	public static final String TERMUX_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_GITHUB_REPO_NAME;
	public static final String TERMUX_GITHUB_ISSUES_REPO_URL = TERMUX_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_GITHUB_WIKI_REPO_URL = TERMUX_GITHUB_REPO_URL + "/wiki";
	public static final String TERMUX_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_PACKAGE_NAME;
	/* Plugin App Names and Packages */
	public static final String TERMUX_API_APP_NAME = "Termux:API";
	public static final String TERMUX_API_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".api";
	public static final String TERMUX_API_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_API_PACKAGE_NAME;
	public static final String TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_API_PACKAGE_NAME + "_preferences";
	public static final String TERMUX_API_GITHUB_REPO_NAME = "termux-api";
	public static final String TERMUX_API_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_API_GITHUB_REPO_NAME;
	public static final String TERMUX_API_GITHUB_ISSUES_REPO_URL = TERMUX_API_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_BOOT_APP_NAME = "Termux:Boot";
	public static final String TERMUX_BOOT_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".boot";
	public static final String TERMUX_BOOT_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_BOOT_PACKAGE_NAME;
	public static final String TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_BOOT_PACKAGE_NAME + "_preferences";
	public static final String TERMUX_BOOT_GITHUB_REPO_NAME = "termux-boot";
	public static final String TERMUX_BOOT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_BOOT_GITHUB_REPO_NAME;
	public static final String TERMUX_BOOT_GITHUB_ISSUES_REPO_URL = TERMUX_BOOT_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_FLOAT_APP_NAME = "Termux:Float";
	public static final String TERMUX_FLOAT_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".window";
	public static final String TERMUX_FLOAT_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_FLOAT_PACKAGE_NAME;
	public static final String TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_FLOAT_PACKAGE_NAME + "_preferences";
	public static final String TERMUX_FLOAT_GITHUB_REPO_NAME = "termux-float";
	public static final String TERMUX_FLOAT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_FLOAT_GITHUB_REPO_NAME;
	public static final String TERMUX_FLOAT_GITHUB_ISSUES_REPO_URL = TERMUX_FLOAT_GITHUB_REPO_URL + "/issues";
	/* Plugin App Names and Packages */
	public static final String TERMUX_STYLING_APP_NAME = "Termux:Styling";
	public static final String TERMUX_STYLING_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".styling";
	public static final String TERMUX_STYLING_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_STYLING_PACKAGE_NAME;
	public static final String TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_STYLING_PACKAGE_NAME + "_preferences";
	public static final String TERMUX_STYLING_GITHUB_REPO_NAME = "termux-styling";
	public static final String TERMUX_STYLING_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_STYLING_GITHUB_REPO_NAME;
	public static final String TERMUX_STYLING_GITHUB_ISSUES_REPO_URL = TERMUX_STYLING_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_TASKER_APP_NAME = "Termux:Tasker";
	public static final String TERMUX_TASKER_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".tasker";
	public static final String TERMUX_TASKER_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_TASKER_PACKAGE_NAME;
	public static final String TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_TASKER_PACKAGE_NAME + "_preferences";
	public static final String TERMUX_TASKER_GITHUB_REPO_NAME = "termux-tasker";
	public static final String TERMUX_TASKER_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_TASKER_GITHUB_REPO_NAME;
	public static final String TERMUX_TASKER_GITHUB_ISSUES_REPO_URL = TERMUX_TASKER_GITHUB_REPO_URL + "/issues";
	/* Termux Widget constants */
	public static final String TERMUX_WIDGET_APP_NAME = "Termux:Widget";
	public static final String TERMUX_WIDGET_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".widget";
	public static final String TERMUX_WIDGET_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_WIDGET_PACKAGE_NAME;
	public static final String TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_WIDGET_PACKAGE_NAME + "_preferences";
	/*
	 * Termux packages urls.
	 */
	/* Termux Plugin App Lists */
	public static final List<String> TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST = Arrays.asList(
			TERMUX_API_PACKAGE_NAME,
			TERMUX_BOOT_PACKAGE_NAME,
			TERMUX_FLOAT_PACKAGE_NAME,
			TERMUX_STYLING_PACKAGE_NAME,
			TERMUX_TASKER_PACKAGE_NAME,
			TERMUX_WIDGET_PACKAGE_NAME);
	public static final String TERMUX_WIDGET_GITHUB_REPO_NAME = "termux-widget";
	public static final String TERMUX_WIDGET_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_WIDGET_GITHUB_REPO_NAME;
	public static final String TERMUX_WIDGET_GITHUB_ISSUES_REPO_URL = TERMUX_WIDGET_GITHUB_REPO_URL + "/issues";
	public static final List<String> TERMUX_PLUGIN_APP_NAMES_LIST = Arrays.asList(
			TERMUX_API_APP_NAME,
			TERMUX_BOOT_APP_NAME,
			TERMUX_FLOAT_APP_NAME,
			TERMUX_STYLING_APP_NAME,
			TERMUX_TASKER_APP_NAME,
			TERMUX_WIDGET_APP_NAME);
	/* APK Release Types and Signing Digests */
	public static final String APK_RELEASE_FDROID = "F-Droid";
	public static final String APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST = "228FB2CFE90831C1499EC3CCAF61E96E8E1CE70766B9474672CE427334D41C42";
	public static final String APK_RELEASE_GITHUB = "Github";
	public static final String APK_RELEASE_GITHUB_SIGNING_CERTIFICATE_SHA256_DIGEST = "B6DA01480EEFD5FBF2CD3771B8D1021EC791304BDD6C4BF41D3FAABAD48EE5E1";
	public static final String APK_RELEASE_GOOGLE_PLAYSTORE = "Google Play Store";
	public static final String APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST = "738F0A30A04D3C8A1BE304AF18D0779BCF3EA88FB60808F657A3521861C2EBF9";
	public static final String APK_RELEASE_TERMUX_DEVS = "Termux Devs";
	public static final String APK_RELEASE_TERMUX_DEVS_SIGNING_CERTIFICATE_SHA256_DIGEST = "F7A038EB551F1BE8FDF388686B784ABAB4552A5D82DF423E3D8F1B5CBE1C69AE";
	/*
	 * Termux miscellaneous urls.
	 */
	/* Termux Repository and Documentation URLs */
	public static final String TERMUX_PACKAGES_GITHUB_REPO_NAME = "termux-packages";
	public static final String TERMUX_PACKAGES_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_PACKAGES_GITHUB_REPO_NAME;
	public static final String TERMUX_PACKAGES_GITHUB_ISSUES_REPO_URL = TERMUX_PACKAGES_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_PACKAGES_GITHUB_WIKI_REPO_URL = TERMUX_PACKAGES_GITHUB_REPO_URL + "/wiki";
	public static final String TERMUX_API_APT_PACKAGE_NAME = "termux-api";
	public static final String TERMUX_API_APT_GITHUB_REPO_NAME = "termux-api-package";
	public static final String TERMUX_API_APT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_API_APT_GITHUB_REPO_NAME;
	public static final String TERMUX_API_APT_GITHUB_ISSUES_REPO_URL = TERMUX_API_APT_GITHUB_REPO_URL + "/issues";
	public static final String TERMUX_SITE = TERMUX_APP_NAME + " Site";
	public static final String TERMUX_SITE_URL = "https://termux.dev";
	public static final String TERMUX_WIKI = TERMUX_APP_NAME + " Wiki";
	public static final String TERMUX_WIKI_URL = "https://wiki.termux.com";
	public static final String TERMUX_SUPPORT_EMAIL_URL = "support@termux.dev";
	public static final String TERMUX_SUPPORT_EMAIL_MAILTO_URL = "mailto:" + TERMUX_SUPPORT_EMAIL_URL;
	public static final String TERMUX_REDDIT_SUBREDDIT = "r/termux";
	public static final String TERMUX_REDDIT_SUBREDDIT_URL = "https://www.reddit.com/r/termux";
	public static final String TERMUX_DONATE_URL = TERMUX_SITE_URL + "/donate";

	/* Internal Directory Paths */
	@SuppressLint("SdCardPath")
	public static final String TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME;
	public static final File TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR = new File(TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH);
	public static final String TERMUX_FILES_DIR_PATH = TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "/files";
	public static final File TERMUX_FILES_DIR = new File(TERMUX_FILES_DIR_PATH);
	public static final String TERMUX_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr";
	public static final File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);
	public static final String TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin";
	public static final File TERMUX_BIN_PREFIX_DIR = new File(TERMUX_BIN_PREFIX_DIR_PATH);
	public static final String TERMUX_ETC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/etc";
	public static final File TERMUX_ETC_PREFIX_DIR = new File(TERMUX_ETC_PREFIX_DIR_PATH);
	public static final String TERMUX_CONFIG_PREFIX_DIR_PATH = TERMUX_ETC_PREFIX_DIR_PATH + "/termux";
	public static final File TERMUX_CONFIG_PREFIX_DIR = new File(TERMUX_CONFIG_PREFIX_DIR_PATH);
	public static final String TERMUX_ENV_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env";
	public static final String TERMUX_ENV_TEMP_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env.tmp";
	public static final String TERMUX_INCLUDE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/include";
	public static final File TERMUX_INCLUDE_PREFIX_DIR = new File(TERMUX_INCLUDE_PREFIX_DIR_PATH);
	public static final String TERMUX_LIB_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/lib";
	public static final File TERMUX_LIB_PREFIX_DIR = new File(TERMUX_LIB_PREFIX_DIR_PATH);
	public static final String TERMUX_LIBEXEC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/libexec";
	public static final File TERMUX_LIBEXEC_PREFIX_DIR = new File(TERMUX_LIBEXEC_PREFIX_DIR_PATH);
	public static final String TERMUX_SHARE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/share";
	public static final File TERMUX_SHARE_PREFIX_DIR = new File(TERMUX_SHARE_PREFIX_DIR_PATH);
	public static final String TERMUX_TMP_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/tmp";
	public static final File TERMUX_TMP_PREFIX_DIR = new File(TERMUX_TMP_PREFIX_DIR_PATH);
	public static final List<String> TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY = Arrays.asList(
			TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH, TermuxConstants.TERMUX_ENV_FILE_PATH);
	public static final String TERMUX_VAR_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/var";
	public static final File TERMUX_VAR_PREFIX_DIR = new File(TERMUX_VAR_PREFIX_DIR_PATH);
	public static final String TERMUX_STAGING_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr-staging";
	public static final File TERMUX_STAGING_PREFIX_DIR = new File(TERMUX_STAGING_PREFIX_DIR_PATH);
	public static final String TERMUX_HOME_DIR_PATH = TERMUX_FILES_DIR_PATH + "/home";
	public static final File TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);



	/*
	 * Termux app and plugin preferences and properties file paths.
	 */
	/**
	 * Termux app config home directory path
	 */
	public static final String TERMUX_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.config/termux"; // Default: "/data/data/com.termux/files/home/.config/termux"
	/**
	 * Termux app config home directory
	 */
	public static final File TERMUX_CONFIG_HOME_DIR = new File(TERMUX_CONFIG_HOME_DIR_PATH);
	/**
	 * Termux app properties secondary file path
	 */
	public static final String TERMUX_PROPERTIES_SECONDARY_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.properties"; // Default: "/data/data/com.termux/files/home/.config/termux/termux.properties"
	/**
	 * Termux app properties secondary file
	 */
	public static final File TERMUX_PROPERTIES_SECONDARY_FILE = new File(TERMUX_PROPERTIES_SECONDARY_FILE_PATH);
	/**
	 * Termux:Float app properties secondary file path
	 */
	public static final String TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.float.properties"; // Default: "/data/data/com.termux/files/home/.config/termux/termux.float.properties"
	/**
	 * Termux:Float app properties secondary file
	 */
	public static final File TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE = new File(TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH);
	/**
	 * Termux app data home directory path
	 */
	public static final String TERMUX_DATA_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.termux"; // Default: "/data/data/com.termux/files/home/.termux"
	/**
	 * Termux app data home directory
	 */
	public static final File TERMUX_DATA_HOME_DIR = new File(TERMUX_DATA_HOME_DIR_PATH);
	/**
	 * Termux app properties primary file path
	 */
	public static final String TERMUX_PROPERTIES_PRIMARY_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/termux.properties"; // Default: "/data/data/com.termux/files/home/.termux/termux.properties"
	/**
	 * Termux app properties primary file
	 */
	public static final File TERMUX_PROPERTIES_PRIMARY_FILE = new File(TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
	/**
	 * Termux app properties file paths list. **DO NOT** allow these files to be modified by
	 * {@link android.content.ContentProvider} exposed to external apps, since they may silently
	 * modify the values for security properties like security.PROP_ALLOW_EXTERNAL_APPS set by users
	 * without their explicit consent.
	 */
	public static final List<String> TERMUX_PROPERTIES_FILE_PATHS_LIST = Arrays.asList(
			TERMUX_PROPERTIES_PRIMARY_FILE_PATH,
			TERMUX_PROPERTIES_SECONDARY_FILE_PATH);
	/**
	 * Termux:Float app properties primary file path
	 */
	public static final String TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/termux.float.properties"; // Default: "/data/data/com.termux/files/home/.termux/termux.float.properties"
	/**
	 * Termux:Float app properties primary file
	 */
	public static final File TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE = new File(TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH);
	/**
	 * Termux:Float app properties file paths list. **DO NOT** allow these files to be modified by
	 * {@link android.content.ContentProvider} exposed to external apps, since they may silently
	 * modify the values for security properties like security.PROP_ALLOW_EXTERNAL_APPS set by users
	 * without their explicit consent.
	 */
	public static final List<String> TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST = Arrays.asList(
			TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH,
			TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH);
	/**
	 * Termux app and Termux:Styling colors.properties file path
	 */
	public static final String TERMUX_COLOR_PROPERTIES_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/colors.properties"; // Default: "/data/data/com.termux/files/home/.termux/colors.properties"
	/**
	 * Termux app and Termux:Styling colors.properties file
	 */
	public static final File TERMUX_COLOR_PROPERTIES_FILE = new File(TERMUX_COLOR_PROPERTIES_FILE_PATH);
	/**
	 * Termux app and Termux:Styling font.ttf file path
	 */
	public static final String TERMUX_FONT_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/font.ttf"; // Default: "/data/data/com.termux/files/home/.termux/font.ttf"
	/**
	 * Termux app and Termux:Styling font.ttf file
	 */
	public static final File TERMUX_FONT_FILE = new File(TERMUX_FONT_FILE_PATH);
	/**
	 * Termux app directory path to store scripts to be run at boot by Termux:Boot
	 */
	public static final String TERMUX_BOOT_SCRIPTS_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/boot"; // Default: "/data/data/com.termux/files/home/.termux/boot"
	/**
	 * Termux app directory to store scripts to be run at boot by Termux:Boot
	 */
	public static final File TERMUX_BOOT_SCRIPTS_DIR = new File(TERMUX_BOOT_SCRIPTS_DIR_PATH);
	/**
	 * Termux app directory path to store scripts to be run by 3rd party twofortyfouram locale plugin
	 * host apps like Tasker app via the Termux:Tasker plugin client
	 */
	public static final String TERMUX_TASKER_SCRIPTS_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/tasker"; // Default: "/data/data/com.termux/files/home/.termux/tasker"
	/**
	 * Termux app directory to store scripts to be run by 3rd party twofortyfouram locale plugin host apps like Tasker app via the Termux:Tasker plugin client
	 */
	public static final File TERMUX_TASKER_SCRIPTS_DIR = new File(TERMUX_TASKER_SCRIPTS_DIR_PATH);
	/**
	 * Termux app storage home directory path
	 */
	public static final String TERMUX_STORAGE_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/storage"; // Default: "/data/data/com.termux/files/home/storage"
	/**
	 * Termux app storage home directory
	 */
	public static final File TERMUX_STORAGE_HOME_DIR = new File(TERMUX_STORAGE_HOME_DIR_PATH);
	/**
	 * Termux app and plugins crash log file path
	 */
	public static final String TERMUX_CRASH_LOG_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log.md"; // Default: "/data/data/com.termux/files/home/crash_log.md"




	/*
	 * Termux app plugin specific paths.
	 */
	/**
	 * Termux app and plugins crash log backup file path
	 */
	public static final String TERMUX_CRASH_LOG_BACKUP_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log_backup.md"; // Default: "/data/data/com.termux/files/home/crash_log_backup.md"
	/**
	 * Termux app directory path to store foreground scripts that can be run by the termux launcher
	 * widget provided by Termux:Widget
	 */
	public static final String TERMUX_SHORTCUT_SCRIPTS_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.shortcuts"; // Default: "/data/data/com.termux/files/home/.shortcuts"
	/**
	 * Termux app directory to store foreground scripts that can be run by the termux launcher widget provided by Termux:Widget
	 */
	public static final File TERMUX_SHORTCUT_SCRIPTS_DIR = new File(TERMUX_SHORTCUT_SCRIPTS_DIR_PATH);
	/**
	 * Termux and plugin apps directory path
	 */
	public static final String TERMUX_APPS_DIR_PATH = TERMUX_FILES_DIR_PATH + "/apps"; // Default: "/data/data/com.termux/files/apps"
	/**
	 * Termux and plugin apps directory
	 */
	public static final File TERMUX_APPS_DIR = new File(TERMUX_APPS_DIR_PATH);
	/**
	 * Termux app default SharedPreferences file basename without extension
	 */
	public static final String TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_PACKAGE_NAME + "_preferences"; // Default: "com.termux_preferences"
	/**
	 * Termux app directory basename that stores background scripts that can be run by the termux
	 * launcher widget provided by Termux:Widget
	 */
	public static final String TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME = "tasks"; // Default: "tasks"
	/**
	 * Termux app directory path to store background scripts that can be run by the termux launcher
	 * widget provided by Termux:Widget
	 */
	public static final String TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH = TERMUX_SHORTCUT_SCRIPTS_DIR_PATH + "/" + TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME; // Default: "/data/data/com.termux/files/home/.shortcuts/tasks"
	/**
	 * Termux app directory to store background scripts that can be run by the termux launcher widget provided by Termux:Widget
	 */
	public static final File TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR = new File(TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH);
	/**
	 * Termux app directory basename that stores icons for the foreground and background scripts
	 * that can be run by the termux launcher widget provided by Termux:Widget
	 */
	public static final String TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME = "icons"; // Default: "icons"
	/**
	 * Termux app directory path to store icons for the foreground and background scripts that can
	 * be run by the termux launcher widget provided by Termux:Widget
	 */
	public static final String TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH = TERMUX_SHORTCUT_SCRIPTS_DIR_PATH + "/" + TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME; // Default: "/data/data/com.termux/files/home/.shortcuts/icons"
	/**
	 * Termux app directory to store icons for the foreground and background scripts that can be
	 * run by the termux launcher widget provided by Termux:Widget
	 */
	public static final File TERMUX_SHORTCUT_SCRIPT_ICONS_DIR = new File(TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH);
	/* Notification Channels and IDs */
	public static final String TERMUX_APP_NOTIFICATION_CHANNEL_ID = "termux_notification_channel";
	public static final String TERMUX_APP_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " App";
	public static final int TERMUX_APP_NOTIFICATION_ID = 1337;

	public static final String TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID = "termux_run_command_notification_channel";
	public static final String TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " RunCommandService";
	public static final int TERMUX_RUN_COMMAND_NOTIFICATION_ID = 1338;

	public static final String TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID = "termux_plugin_command_errors_notification_channel";
	public static final String TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " Plugin Commands Errors";

	public static final String TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID = "termux_crash_reports_notification_channel";
	public static final String TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " Crash Reports";

	public static final String TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID = "termux_float_notification_channel";
	public static final String TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_FLOAT_APP_NAME + " App";
	public static final int TERMUX_FLOAT_APP_NOTIFICATION_ID = 1339;

	/* Miscellaneous Actions and Permissions */
	public static final String PERMISSION_RUN_COMMAND = TERMUX_PACKAGE_NAME + ".permission.RUN_COMMAND";
	public static final String PROP_ALLOW_EXTERNAL_APPS = "allow-external-apps";
	public static final String PROP_DEFAULT_VALUE_ALLOW_EXTERNAL_APPS = "false";
	public static final String BROADCAST_TERMUX_OPENED = TERMUX_PACKAGE_NAME + ".app.OPENED";
	public static final String TERMUX_FILE_SHARE_URI_AUTHORITY = TERMUX_PACKAGE_NAME + ".files";
	public static final String COMMA_NORMAL = ",";
	public static final String COMMA_ALTERNATIVE = "‚";
	public static final String TERMUX_ENV_PREFIX_ROOT = "TERMUX";


	/**
	 * Constants specifically for the main Termux application.
	 */
	public static final class TERMUX_APP {

		public static final String APPS_DIR_PATH = TERMUX_APPS_DIR_PATH + "/" + TERMUX_PACKAGE_NAME;
		public static final String TERMUX_AM_SOCKET_FILE_PATH = APPS_DIR_PATH + "/termux-am/am.sock";

		public static final String BUILD_CONFIG_CLASS_NAME = TERMUX_PACKAGE_NAME + ".BuildConfig";
		public static final String FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".app.api.file.FileShareReceiverActivity";
		public static final String FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".app.api.file.FileViewReceiverActivity";

		public static final String TERMUX_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxActivity";
		public static final String TERMUX_SETTINGS_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.activities.SettingsActivity";
		public static final String TERMUX_SERVICE_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxService";
		public static final String RUN_COMMAND_SERVICE_NAME = TERMUX_PACKAGE_NAME + ".app.RunCommandService";

		/**
		 * Constants for TermuxActivity.
		 */
		public static final class TERMUX_ACTIVITY {
			public static final String EXTRA_FAILSAFE_SESSION = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.failsafe_session";
			public static final String ACTION_NOTIFY_APP_CRASH = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.notify_app_crash";
			public static final String ACTION_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style";
			public static final String ACTION_RESTART_TERMINAL = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.restart_terminal";

			@Deprecated
			public static final String EXTRA_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style";
			public static final String EXTRA_RECREATE_ACTIVITY = TERMUX_APP.TERMUX_ACTIVITY_NAME + ".EXTRA_RECREATE_ACTIVITY";
			public static final String ACTION_REQUEST_PERMISSIONS = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.request_storage_permissions";
			public static final String ACTION_OPEN_NEW_WINDOW = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.open_new_window";
		}


		/**
		 * Termux app core service.
		 */
		public static final class TERMUX_SERVICE {

			/**
			 * Intent action to stop TERMUX_SERVICE
			 */
			public static final String ACTION_STOP_SERVICE = TERMUX_PACKAGE_NAME + ".service_stop"; // Default: "com.termux.service_stop"


			/**
			 * Intent action to make TERMUX_SERVICE acquire a wakelock
			 */
			public static final String ACTION_WAKE_LOCK = TERMUX_PACKAGE_NAME + ".service_wake_lock"; // Default: "com.termux.service_wake_lock"


			/**
			 * Intent action to make TERMUX_SERVICE release wakelock
			 */
			public static final String ACTION_WAKE_UNLOCK = TERMUX_PACKAGE_NAME + ".service_wake_unlock"; // Default: "com.termux.service_wake_unlock"


			/**
			 * Intent action to execute command with TERMUX_SERVICE
			 */
			public static final String ACTION_SERVICE_EXECUTE = TERMUX_PACKAGE_NAME + ".service_execute"; // Default: "com.termux.service_execute"

			/**
			 * Uri scheme for paths sent via intent to TERMUX_SERVICE
			 */
			public static final String URI_SCHEME_SERVICE_EXECUTE = TERMUX_PACKAGE_NAME + ".file"; // Default: "com.termux.file"
			/**
			 * Intent {@code String[]} extra for arguments to the executable of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".execute.arguments"; // Default: "com.termux.execute.arguments"
			/**
			 * Intent {@code String} extra for stdin of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_STDIN = TERMUX_PACKAGE_NAME + ".execute.stdin"; // Default: "com.termux.execute.stdin"
			/**
			 * Intent {@code String} extra for command current working directory for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_WORKDIR = TERMUX_PACKAGE_NAME + ".execute.cwd"; // Default: "com.termux.execute.cwd"
			/**
			 * Intent {@code boolean} extra for whether to run command in background {@link Runner#APP_SHELL} or foreground {@link Runner#TERMINAL_SESSION} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			@Deprecated
			public static final String EXTRA_BACKGROUND = TERMUX_PACKAGE_NAME + ".execute.background"; // Default: "com.termux.execute.background"
			/**
			 * Intent {@code String} extra for command the {@link Runner} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RUNNER = TERMUX_PACKAGE_NAME + ".execute.runner"; // Default: "com.termux.execute.runner"
			/**
			 * Intent {@code String} extra for custom log level for background commands defined by {@link com.csmide.termux.shared.logger.Logger} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = TERMUX_PACKAGE_NAME + ".execute.background_custom_log_level"; // Default: "com.termux.execute.background_custom_log_level"
			/**
			 * Intent {@code String} extra for session action for {@link Runner#TERMINAL_SESSION} commands for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_SESSION_ACTION = TERMUX_PACKAGE_NAME + ".execute.session_action"; // Default: "com.termux.execute.session_action"
			/**
			 * Intent {@code String} extra for shell name for commands for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_SHELL_NAME = TERMUX_PACKAGE_NAME + ".execute.shell_name"; // Default: "com.termux.execute.shell_name"
			/**
			 * Intent {@code String} extra for the {@link ExecutionCommand.ShellCreateMode}  for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent.
			 */
			public static final String EXTRA_SHELL_CREATE_MODE = TERMUX_PACKAGE_NAME + ".execute.shell_create_mode"; // Default: "com.termux.execute.shell_create_mode"
			/**
			 * Intent {@code String} extra for label of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_COMMAND_LABEL = TERMUX_PACKAGE_NAME + ".execute.command_label"; // Default: "com.termux.execute.command_label"
			/**
			 * Intent markdown {@code String} extra for description of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_COMMAND_DESCRIPTION = TERMUX_PACKAGE_NAME + ".execute.command_description"; // Default: "com.termux.execute.command_description"
			/**
			 * Intent markdown {@code String} extra for help of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_COMMAND_HELP = TERMUX_PACKAGE_NAME + ".execute.command_help"; // Default: "com.termux.execute.command_help"
			/**
			 * Intent markdown {@code String} extra for help of the plugin API for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent (Internal Use Only)
			 */
			public static final String EXTRA_PLUGIN_API_HELP = TERMUX_PACKAGE_NAME + ".execute.plugin_api_help"; // Default: "com.termux.execute.plugin_help"
			/**
			 * Intent {@code Parcelable} extra for the pending intent that should be sent with the
			 * result of the execution command to the execute command caller for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_PENDING_INTENT = "pendingIntent"; // Default: "pendingIntent"
			/**
			 * Intent {@code String} extra for the directory path in which to write the result of the
			 * execution command for the execute command caller for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_DIRECTORY = TERMUX_PACKAGE_NAME + ".execute.result_directory"; // Default: "com.termux.execute.result_directory"
			/**
			 * Intent {@code boolean} extra for whether the result should be written to a single file
			 * or multiple files (err, errmsg, stdout, stderr, exit_code) in
			 * {@link #EXTRA_RESULT_DIRECTORY} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_SINGLE_FILE = TERMUX_PACKAGE_NAME + ".execute.result_single_file"; // Default: "com.termux.execute.result_single_file"
			/**
			 * Intent {@code String} extra for the basename of the result file that should be created
			 * in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is {@code true}
			 * for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_FILE_BASENAME = TERMUX_PACKAGE_NAME + ".execute.result_file_basename"; // Default: "com.termux.execute.result_file_basename"
			/**
			 * Intent {@code String} extra for the output {@link Formatter} format of the
			 * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_FILE_OUTPUT_FORMAT = TERMUX_PACKAGE_NAME + ".execute.result_file_output_format"; // Default: "com.termux.execute.result_file_output_format"
			/**
			 * Intent {@code String} extra for the error {@link Formatter} format of the
			 * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_FILE_ERROR_FORMAT = TERMUX_PACKAGE_NAME + ".execute.result_file_error_format"; // Default: "com.termux.execute.result_file_error_format"
			/**
			 * Intent {@code String} extra for the optional suffix of the result files that should
			 * be created in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is
			 * {@code false} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
			 */
			public static final String EXTRA_RESULT_FILES_SUFFIX = TERMUX_PACKAGE_NAME + ".execute.result_files_suffix"; // Default: "com.termux.execute.result_files_suffix"


			/**
			 * The value for {@link #EXTRA_SESSION_ACTION} extra that will set the new session as
			 * the current session and will start {@link TERMUX_ACTIVITY} if its not running to bring
			 * the new session to foreground.
			 */
			public static final int VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY = 0;

			/**
			 * The value for {@link #EXTRA_SESSION_ACTION} extra that will keep any existing session
			 * as the current session and will start {@link TERMUX_ACTIVITY} if its not running to
			 * bring the existing session to foreground. The new session will be added to the left
			 * sidebar in the sessions list.
			 */
			public static final int VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY = 1;

			/**
			 * The value for {@link #EXTRA_SESSION_ACTION} extra that will set the new session as
			 * the current session but will not start {@link TERMUX_ACTIVITY} if its not running
			 * and session(s) will be seen in Termux notification and can be clicked to bring new
			 * session to foreground. If the {@link TERMUX_ACTIVITY} is already running, then this
			 * will behave like {@link #VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY}.
			 */
			public static final int VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY = 2;

			/**
			 * The value for {@link #EXTRA_SESSION_ACTION} extra that will keep any existing session
			 * as the current session but will not start {@link TERMUX_ACTIVITY} if its not running
			 * and session(s) will be seen in Termux notification and can be clicked to bring
			 * existing session to foreground. If the {@link TERMUX_ACTIVITY} is already running,
			 * then this will behave like {@link #VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY}.
			 */
			public static final int VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY = 3;

			/**
			 * The minimum allowed value for {@link #EXTRA_SESSION_ACTION}.
			 */
			public static final int MIN_VALUE_EXTRA_SESSION_ACTION = VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY;

			/**
			 * The maximum allowed value for {@link #EXTRA_SESSION_ACTION}.
			 */
			public static final int MAX_VALUE_EXTRA_SESSION_ACTION = VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY;


			/**
			 * Intent {@code Bundle} extra to store result of execute command that is sent back for the
			 * TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent if the {@link #EXTRA_PENDING_INTENT} is not
			 * {@code null}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE = "result"; // Default: "result"
			/**
			 * Intent {@code String} extra for stdout value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = "stdout"; // Default: "stdout"
			/**
			 * Intent {@code String} extra for original length of stdout value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"; // Default: "stdout_original_length"
			/**
			 * Intent {@code String} extra for stderr value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = "stderr"; // Default: "stderr"
			/**
			 * Intent {@code String} extra for original length of stderr value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH = "stderr_original_length"; // Default: "stderr_original_length"
			/**
			 * Intent {@code int} extra for exit code value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = "exitCode"; // Default: "exitCode"
			/**
			 * Intent {@code int} extra for err value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"; // Default: "err"
			/**
			 * Intent {@code String} extra for errmsg value of execute command of the {@link #EXTRA_PLUGIN_RESULT_BUNDLE}
			 */
			public static final String EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = "errmsg"; // Default: "errmsg"

		}

		/**
		 * Termux app run command service to receive commands sent by 3rd party apps.
		 */
		public static final class RUN_COMMAND_SERVICE {

			/**
			 * Termux RUN_COMMAND Intent help url
			 */
			public static final String RUN_COMMAND_API_HELP_URL = TERMUX_GITHUB_WIKI_REPO_URL + "/RUN_COMMAND-Intent"; // Default: "https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent"


			/**
			 * Intent action to execute command with RUN_COMMAND_SERVICE
			 */
			public static final String ACTION_RUN_COMMAND = TERMUX_PACKAGE_NAME + ".RUN_COMMAND"; // Default: "com.termux.RUN_COMMAND"

			/**
			 * Intent {@code String} extra for absolute path of command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_COMMAND_PATH = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_PATH"; // Default: "com.termux.RUN_COMMAND_PATH"
			/**
			 * Intent {@code String[]} extra for arguments to the executable of the command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_ARGUMENTS"; // Default: "com.termux.RUN_COMMAND_ARGUMENTS"
			/**
			 * Intent {@code boolean} extra for whether to replace comma alternative characters in arguments with comma characters for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"; // Default: "com.termux.RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"
			/**
			 * Intent {@code String} extra for the comma alternative characters in arguments that should be replaced instead of the default {@link #COMMA_ALTERNATIVE} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"; // Default: "com.termux.RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"

			/**
			 * Intent {@code String} extra for stdin of the command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_STDIN = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_STDIN"; // Default: "com.termux.RUN_COMMAND_STDIN"
			/**
			 * Intent {@code String} extra for current working directory of command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_WORKDIR = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_WORKDIR"; // Default: "com.termux.RUN_COMMAND_WORKDIR"
			/**
			 * Intent {@code boolean} extra for whether to run command in background {@link Runner#APP_SHELL} or foreground {@link Runner#TERMINAL_SESSION} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			@Deprecated
			public static final String EXTRA_BACKGROUND = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_BACKGROUND"; // Default: "com.termux.RUN_COMMAND_BACKGROUND"
			/**
			 * Intent {@code String} extra for command the {@link Runner} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RUNNER = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RUNNER"; // Default: "com.termux.RUN_COMMAND_RUNNER"
			/**
			 * Intent {@code String} extra for custom log level for background commands defined by {@link com.csmide.termux.shared.logger.Logger} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL"; // Default: "com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL"
			/**
			 * Intent {@code String} extra for session action of {@link Runner#TERMINAL_SESSION} commands for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_SESSION_ACTION = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_SESSION_ACTION"; // Default: "com.termux.RUN_COMMAND_SESSION_ACTION"
			/**
			 * Intent {@code String} extra for shell name of commands for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_SHELL_NAME = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_SHELL_NAME"; // Default: "com.termux.RUN_COMMAND_SHELL_NAME"
			/**
			 * Intent {@code String} extra for the {@link ExecutionCommand.ShellCreateMode}  for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent.
			 */
			public static final String EXTRA_SHELL_CREATE_MODE = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_SHELL_CREATE_MODE"; // Default: "com.termux.RUN_COMMAND_SHELL_CREATE_MODE"
			/**
			 * Intent {@code String} extra for label of the command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_COMMAND_LABEL = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_COMMAND_LABEL"; // Default: "com.termux.RUN_COMMAND_COMMAND_LABEL"
			/**
			 * Intent markdown {@code String} extra for description of the command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_COMMAND_DESCRIPTION = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_COMMAND_DESCRIPTION"; // Default: "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
			/**
			 * Intent markdown {@code String} extra for help of the command for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_COMMAND_HELP = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_COMMAND_HELP"; // Default: "com.termux.RUN_COMMAND_COMMAND_HELP"
			/**
			 * Intent {@code Parcelable} extra for the pending intent that should be sent with the result of the execution command to the execute command caller for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_PENDING_INTENT = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_PENDING_INTENT"; // Default: "com.termux.RUN_COMMAND_PENDING_INTENT"
			/**
			 * Intent {@code String} extra for the directory path in which to write the result of
			 * the execution command for the execute command caller for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_DIRECTORY = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_DIRECTORY"; // Default: "com.termux.RUN_COMMAND_RESULT_DIRECTORY"
			/**
			 * Intent {@code boolean} extra for whether the result should be written to a single file
			 * or multiple files (err, errmsg, stdout, stderr, exit_code) in
			 * {@link #EXTRA_RESULT_DIRECTORY} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_SINGLE_FILE = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_SINGLE_FILE"; // Default: "com.termux.RUN_COMMAND_RESULT_SINGLE_FILE"
			/**
			 * Intent {@code String} extra for the basename of the result file that should be created
			 * in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is {@code true}
			 * for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_FILE_BASENAME = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_FILE_BASENAME"; // Default: "com.termux.RUN_COMMAND_RESULT_FILE_BASENAME"
			/**
			 * Intent {@code String} extra for the output {@link Formatter} format of the
			 * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_FILE_OUTPUT_FORMAT = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_FILE_OUTPUT_FORMAT"; // Default: "com.termux.RUN_COMMAND_RESULT_FILE_OUTPUT_FORMAT"
			/**
			 * Intent {@code String} extra for the error {@link Formatter} format of the
			 * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_FILE_ERROR_FORMAT = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_FILE_ERROR_FORMAT"; // Default: "com.termux.RUN_COMMAND_RESULT_FILE_ERROR_FORMAT"
			/**
			 * Intent {@code String} extra for the optional suffix of the result files that should be
			 * created in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is
			 * {@code false} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
			 */
			public static final String EXTRA_RESULT_FILES_SUFFIX = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_RESULT_FILES_SUFFIX"; // Default: "com.termux.RUN_COMMAND_RESULT_FILES_SUFFIX"

		}
	}


	/**
	 * Termux:API app constants.
	 */
	public static final class TERMUX_API_APP {

		/**
		 * Termux:API app main activity name.
		 */
		public static final String TERMUX_API_MAIN_ACTIVITY_NAME = TERMUX_API_PACKAGE_NAME + ".activities.TermuxAPIMainActivity"; // Default: "com.termux.api.activities.TermuxAPIMainActivity"

		/**
		 * Termux:API app launcher activity name. This is an `activity-alias` for {@link #TERMUX_API_MAIN_ACTIVITY_NAME} used for launchers with {@link Intent#CATEGORY_LAUNCHER}.
		 */
		public static final String TERMUX_API_LAUNCHER_ACTIVITY_NAME = TERMUX_API_PACKAGE_NAME + ".activities.TermuxAPILauncherActivity"; // Default: "com.termux.api.activities.TermuxAPILauncherActivity"

	}


	/**
	 * Termux:Boot app constants.
	 */
	public static final class TERMUX_BOOT_APP {

		/**
		 * Termux:Boot app main activity name.
		 */
		public static final String TERMUX_BOOT_MAIN_ACTIVITY_NAME = TERMUX_BOOT_PACKAGE_NAME + ".activities.TermuxBootMainActivity"; // Default: "com.termux.boot.activities.TermuxBootMainActivity"

		/**
		 * Termux:Boot app launcher activity name. This is an `activity-alias` for {@link #TERMUX_BOOT_MAIN_ACTIVITY_NAME} used for launchers with {@link Intent#CATEGORY_LAUNCHER}.
		 */
		public static final String TERMUX_BOOT_LAUNCHER_ACTIVITY_NAME = TERMUX_BOOT_PACKAGE_NAME + ".activities.TermuxBootLauncherActivity"; // Default: "com.termux.boot.activities.TermuxBootLauncherActivity"

	}


	/**
	 * Termux:Float app constants.
	 */
	public static final class TERMUX_FLOAT_APP {

		/**
		 * Termux:Float app core activity name.
		 */
		public static final String TERMUX_FLOAT_ACTIVITY_NAME = TERMUX_FLOAT_PACKAGE_NAME + ".TermuxFloatActivity"; // Default: "com.termux.window.TermuxFloatActivity"

		/**
		 * Termux:Float app core service name.
		 */
		public static final String TERMUX_FLOAT_SERVICE_NAME = TERMUX_FLOAT_PACKAGE_NAME + ".TermuxFloatService"; // Default: "com.termux.window.TermuxFloatService"

		/**
		 * Termux:Float app core service.
		 */
		public static final class TERMUX_FLOAT_SERVICE {

			/**
			 * Intent action to stop TERMUX_FLOAT_SERVICE.
			 */
			public static final String ACTION_STOP_SERVICE = TERMUX_FLOAT_PACKAGE_NAME + ".ACTION_STOP_SERVICE"; // Default: "com.termux.float.ACTION_STOP_SERVICE"

			/**
			 * Intent action to show float window.
			 */
			public static final String ACTION_SHOW = TERMUX_FLOAT_PACKAGE_NAME + ".ACTION_SHOW"; // Default: "com.termux.float.ACTION_SHOW"

			/**
			 * Intent action to hide float window.
			 */
			public static final String ACTION_HIDE = TERMUX_FLOAT_PACKAGE_NAME + ".ACTION_HIDE"; // Default: "com.termux.float.ACTION_HIDE"

		}

	}


	/**
	 * Termux:Styling app constants.
	 */
	public static final class TERMUX_STYLING_APP {

		/**
		 * Termux:Styling app core activity name.
		 */
		public static final String TERMUX_STYLING_ACTIVITY_NAME = TERMUX_STYLING_PACKAGE_NAME + ".TermuxStyleActivity"; // Default: "com.termux.styling.TermuxStyleActivity"


		/**
		 * Termux:Styling app main activity name.
		 */
		public static final String TERMUX_STYLING_MAIN_ACTIVITY_NAME = TERMUX_STYLING_PACKAGE_NAME + ".activities.TermuxStylingMainActivity"; // Default: "com.termux.styling.activities.TermuxStylingMainActivity"

		/**
		 * Termux:Styling app launcher activity name. This is an `activity-alias` for {@link #TERMUX_STYLING_MAIN_ACTIVITY_NAME} used for launchers with {@link Intent#CATEGORY_LAUNCHER}.
		 */
		public static final String TERMUX_STYLING_LAUNCHER_ACTIVITY_NAME = TERMUX_STYLING_PACKAGE_NAME + ".activities.TermuxStylingLauncherActivity"; // Default: "com.termux.styling.activities.TermuxStylingLauncherActivity"

	}


	/**
	 * Termux:Tasker app constants.
	 */
	public static final class TERMUX_TASKER_APP {

		/**
		 * Termux:Tasker app main activity name.
		 */
		public static final String TERMUX_TASKER_MAIN_ACTIVITY_NAME = TERMUX_TASKER_PACKAGE_NAME + ".activities.TermuxTaskerMainActivity"; // Default: "com.termux.tasker.activities.TermuxTaskerMainActivity"

		/**
		 * Termux:Tasker app launcher activity name. This is an `activity-alias` for {@link #TERMUX_TASKER_MAIN_ACTIVITY_NAME} used for launchers with {@link Intent#CATEGORY_LAUNCHER}.
		 */
		public static final String TERMUX_TASKER_LAUNCHER_ACTIVITY_NAME = TERMUX_TASKER_PACKAGE_NAME + ".activities.TermuxTaskerLauncherActivity"; // Default: "com.termux.tasker.activities.TermuxTaskerLauncherActivity"

	}


	/**
	 * Termux:Widget app constants.
	 */
	public static final class TERMUX_WIDGET_APP {

		/**
		 * Termux:Widget app main activity name.
		 */
		public static final String TERMUX_WIDGET_MAIN_ACTIVITY_NAME = TERMUX_WIDGET_PACKAGE_NAME + ".activities.TermuxWidgetMainActivity"; // Default: "com.termux.widget.activities.TermuxWidgetMainActivity"

		/**
		 * Termux:Widget app launcher activity name. This is an `activity-alias` for {@link #TERMUX_WIDGET_MAIN_ACTIVITY_NAME} used for launchers with {@link Intent#CATEGORY_LAUNCHER}.
		 */
		public static final String TERMUX_WIDGET_LAUNCHER_ACTIVITY_NAME = TERMUX_WIDGET_PACKAGE_NAME + ".activities.TermuxWidgetLauncherActivity"; // Default: "com.termux.widget.activities.TermuxWidgetLauncherActivity"


		/**
		 * Intent {@code String} extra for the token of the Termux:Widget app shortcuts.
		 */
		public static final String EXTRA_TOKEN_NAME = TERMUX_PACKAGE_NAME + ".shortcut.token"; // Default: "com.termux.shortcut.token"


		/**
		 * Termux:Widget app {@link android.appwidget.AppWidgetProvider} class.
		 */
		public static final class TERMUX_WIDGET_PROVIDER {

			/**
			 * Intent action for if an item is clicked in the widget.
			 */
			public static final String ACTION_WIDGET_ITEM_CLICKED = TERMUX_WIDGET_PACKAGE_NAME + ".ACTION_WIDGET_ITEM_CLICKED"; // Default: "com.termux.widget.ACTION_WIDGET_ITEM_CLICKED"


			/**
			 * Intent action to refresh files in the widget.
			 */
			public static final String ACTION_REFRESH_WIDGET = TERMUX_WIDGET_PACKAGE_NAME + ".ACTION_REFRESH_WIDGET"; // Default: "com.termux.widget.ACTION_REFRESH_WIDGET"


			/**
			 * Intent {@code String} extra for the file clicked for the TERMUX_WIDGET_PROVIDER.ACTION_WIDGET_ITEM_CLICKED intent.
			 */
			public static final String EXTRA_FILE_CLICKED = TERMUX_WIDGET_PACKAGE_NAME + ".EXTRA_FILE_CLICKED"; // Default: "com.termux.widget.EXTRA_FILE_CLICKED"
		}

	}

}
// @formatter:on
