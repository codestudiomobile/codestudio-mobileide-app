package com.cs.ide.app.models;

/**
 * Interface representing a code suggestion or snippet.
 * Used by adapters to display and insert code into the editor.
 */
public interface Code {
    /**
     * Gets the title of the code item to be displayed in the suggestions list.
     *
     * @return The display title.
     */
    String getCodeTitle();

    /**
     * Gets the prefix used for filtering suggestions.
     *
     * @return The prefix string.
     */
    String getCodePrefix();

    /**
     * Gets the actual code body to be inserted into the editor.
     *
     * @return The code snippet body.
     */
    String getCodeBody();
}
