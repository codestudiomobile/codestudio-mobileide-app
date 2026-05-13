package com.cs.ide.app.editor;

import androidx.annotation.NonNull;
import com.cs.ide.app.models.Token;
import java.util.List;

public interface Findable {
    List<Token> findMatches(@NonNull String regex);
    Token findNextMatch();
    Token findPrevMatch();
    void clearMatches();
}
