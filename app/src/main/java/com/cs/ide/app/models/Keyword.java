package com.cs.ide.app.models;

/**
 * Keyword represents a single programming language keyword for auto-completion.
 * Implements {@link Code} to provide details for the code editor's completion system.
 */
public class Keyword implements Code {
    private final String title;
    private final String prefix;

    /**
     * Constructs a Keyword where title and prefix are the same.
     *
     * @param title The keyword text.
     */
    public Keyword(String title) {
        this.title = title;
        this.prefix = title;
    }

    /**
     * Constructs a Keyword with a custom title and prefix.
     *
     * @param title  The text to show in the list.
     * @param prefix The actual keyword text.
     */
    public Keyword(String title, String prefix) {
        this.title = title;
        this.prefix = prefix;
    }

    /**
     * Gets the title displayed in the auto-completion list.
     * @return The keyword title.
     */
    @Override
    public String getCodeTitle() {
        return title;
    }

    /**
     * Gets the prefix used for matching when the user types.
     * @return The keyword prefix.
     */
    @Override
    public String getCodePrefix() {
        return prefix;
    }

    /**
     * Gets the body text to be inserted into the editor.
     * @return The keyword body.
     */
    @Override
    public String getCodeBody() {
        return prefix;
    }
}
