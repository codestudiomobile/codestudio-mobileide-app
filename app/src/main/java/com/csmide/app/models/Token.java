package com.csmide.app.models;

/**
 * Token represents a range (start and end indices) within a text document.
 * It is used for highlighting, searching, and selecting text segments.
 */
public class Token {
    /** The starting character index (inclusive). */
    private final int start;
    /** The ending character index (exclusive). */
    private final int end;

    /**
     * Constructs a new Token.
     *
     * @param start Starting index.
     * @param end   Ending index.
     */
    public Token(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Gets the starting index of the token.
     *
     * @return Start position.
     */
    public int getStart() {
        return start;
    }

    /**
     * Gets the ending index of the token.
     *
     * @return End position.
     */
    public int getEnd() {
        return end;
    }
}
