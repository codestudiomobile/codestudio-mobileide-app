package com.cs.ide.app;
public interface Replaceable {
    void replaceFirstMatch(String regex, String replacement);
    void replaceAllMatches(String regex, String replacement);
}
