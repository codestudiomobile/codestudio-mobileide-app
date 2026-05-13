package com.cs.ide.app.utils;

/**
 * AppPreferences contains the keys and constant values for shared preferences used across the application.
 * It provides a central location for managing preference identifiers for editor settings, 
 * last opened folders, and tab state.
 */
public class AppPreferences {
    public static final String PREFERENCE_NAME = "AppPreferences";
    public static final String KEY_EDITOR_STARTUP = "openEditorOnStartup";
    public static final String KEY_WELCOME_STARTUP = "openWelcomeScreenOnStartup";
    public static final String KEY_EDITOR_TEXT_SIZE = "editorTextSize";
    public static final String KEY_PINCH_TO_ZOOM = "pinchToZoom";
    public static final String KEY_SHOW_LINE_NUMBERS = "showLineNumbers";
    public static final String KEY_AUTO_INDENTATION = "autoIndentation";
    public static final String KEY_SYNTAX_HIGHLIGHTING = "syntaxHighlighting";
    public static final String KEY_WORD_WRAP = "wordWrap";
    public static final String LAST_FOLDER_URI_KEY = "lastFolderUri";
    public static final String TAB_URI_KEY = "tab_uris";
    public static final String TAB_NAME_KEY = "tab_names";
    public static final String CURRENT_TAB = "current_tab";

    public static final int DEFAULT_TEXT_SIZE = 14;
}
