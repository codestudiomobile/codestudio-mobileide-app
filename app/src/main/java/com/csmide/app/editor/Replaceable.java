package com.csmide.app.editor;

public interface Replaceable {
	void replaceFirstMatch(String regex, String replacement);

	void replaceAllMatches(String regex, String replacement);
}
