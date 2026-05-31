package com.csmide.app.editor;

import androidx.annotation.NonNull;

import com.csmide.app.models.Token;

import java.util.List;

public interface Findable {
	List<Token> findMatches(@NonNull String regex);

	Token findNextMatch();

	Token findPrevMatch();

	void clearMatches();
}
