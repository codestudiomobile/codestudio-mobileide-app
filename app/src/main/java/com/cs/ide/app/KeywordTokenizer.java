package com.cs.ide.app;
import android.widget.MultiAutoCompleteTextView;
public class KeywordTokenizer implements MultiAutoCompleteTextView.Tokenizer {
    @Override
    public int findTokenStart(CharSequence charSequence, int cursor) {
        final String sequenceStr = charSequence.toString().substring(0, cursor);
        for (int i = cursor - 1; i >= 0; i--) {
            final char c = sequenceStr.charAt(i);
            if (c == ' ' || c == '\n' || c == '(') return i + 1;
        }
        return 0;
    }
    @Override
    public int findTokenEnd(CharSequence charSequence, int cursor) {
        return charSequence.length();
    }
    @Override
    public CharSequence terminateToken(CharSequence charSequence) {
        return charSequence;
    }
}
