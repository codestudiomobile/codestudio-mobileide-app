package com.csmide.app.models;

/**
 * Snippet represents a reusable block of code for auto-completion.
 * Implements {@link Code} to provide details for the editor's completion system.
 */
public class Snippet implements Code {
	private final String title;
	private final String prefix;
	private final String body;

	/**
	 * Constructs a Snippet where prefix is same as title.
	 *
	 * @param title Display title for the snippet.
	 * @param body  The code content to insert.
	 */
	public Snippet(String title, String body) {
		this.title = title;
		this.prefix = title;
		this.body = body;
	}

	/**
	 * Constructs a Snippet with a custom prefix.
	 *
	 * @param title  Display title for the snippet.
	 * @param prefix Prefix used for matching.
	 * @param body   The code content to insert.
	 */
	public Snippet(String title, String prefix, String body) {
		this.title = title;
		this.prefix = prefix;
		this.body = body;
	}

	/**
	 * Gets the title displayed in the auto-completion list.
	 *
	 * @return The snippet title.
	 */
	@Override
	public String getCodeTitle() {
		return title;
	}

	/**
	 * Gets the prefix used for matching when the user types.
	 *
	 * @return The snippet prefix.
	 */
	@Override
	public String getCodePrefix() {
		return prefix;
	}

	/**
	 * Gets the code body to be inserted into the editor.
	 *
	 * @return The snippet body.
	 */
	@Override
	public String getCodeBody() {
		return body;
	}
}
