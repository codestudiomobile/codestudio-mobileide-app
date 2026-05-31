package com.csmide.app.editor;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;

/**
 * TextMateLanguageWithSnippets wraps a TextMateLanguage and adds a CompletionProvider for snippets.
 */
public class TextMateLanguageWithSnippets implements Language {
	private final Language base;
	private CompletionProvider autoCompleteProvider;

	public TextMateLanguageWithSnippets(Language base) {
		this.base = base;
	}

	public CompletionProvider getAutoCompleteProvider() {
		return autoCompleteProvider;
	}

	public void setAutoCompleteProvider(CompletionProvider provider) {
		this.autoCompleteProvider = provider;
	}

	public Language getBase() {
		return base;
	}

	@NonNull
	@Override
	public AnalyzeManager getAnalyzeManager() {
		return base.getAnalyzeManager();
	}

	@NonNull
	@Override
	public Formatter getFormatter() {
		return base.getFormatter();
	}

	@Override
	public io.github.rosemoe.sora.widget.SymbolPairMatch getSymbolPairs() {
		return base.getSymbolPairs();
	}

	@Override
	public NewlineHandler[] getNewlineHandlers() {
		return base.getNewlineHandlers();
	}

	@Override
	public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position, @NonNull CompletionPublisher publisher, @NonNull Bundle extraArguments) {
		base.requireAutoComplete(content, position, publisher, extraArguments);
		if (autoCompleteProvider != null) {
			autoCompleteProvider.requireAutoComplete(content, position, publisher, extraArguments);
		}
	}

	@Override
	public int getInterruptionLevel() {
		return base.getInterruptionLevel();
	}

	@Override
	public void destroy() {
		base.destroy();
	}

	@Override
	public boolean useTab() {
		return base.useTab();
	}

	@Override
	public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
		return base.getIndentAdvance(content, line, column);
	}

	@Override
	public int getIndentAdvance(@NonNull ContentReference content, int line, int column, int cursorLine, int cursorColumn) {
		return base.getIndentAdvance(content, line, column, cursorLine, cursorColumn);
	}

	@Nullable
	@Override
	public QuickQuoteHandler getQuickQuoteHandler() {
		return base.getQuickQuoteHandler();
	}
}
