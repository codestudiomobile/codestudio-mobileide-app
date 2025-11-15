package com.cs.ide.app;
import java.util.List;
public interface Findable {
    List<Token> findMatches(String regex);
    Token findNextMatch();
    Token findPrevMatch();
    void clearMatches();
}
