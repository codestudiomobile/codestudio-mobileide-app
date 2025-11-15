package com.cs.ide.app;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ScaleGestureDetector;
import android.widget.MultiAutoCompleteTextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class CodeView extends AppCompatMultiAutoCompleteTextView implements Findable, Replaceable {
    public static final String KEY_TEXT_SCALE_FACTOR = "text_scale_factor";
    private final static int LINE_HIGHLIGHT_DEFAULT_COLOR = Color.DKGRAY;
    private static final Pattern PATTERN_LINE = Pattern.compile("(^.+$)+", Pattern.MULTILINE);
    private static final Pattern PATTERN_TRAILING_WHITE_SPACE = Pattern.compile("[\\t ]+$", Pattern.MULTILINE);
    private static final String ARG_URI = "file_uri";
    private static final String TAG = "FileContents";
    private final Set<Character> indentationStarts = new HashSet<>();
    private final Set<Character> indentationEnds = new HashSet<>();
    private final List<Token> matchedTokens = new ArrayList<>();
    private final Map<Character, Character> mPairCompleteMap = new HashMap<>();
    private final Handler mUpdateHandler = new Handler();
    private final SortedMap<Integer, Integer> mErrorHashSet = new TreeMap<>();
    private final Map<Pattern, Integer> mSyntaxPatternMap = new HashMap<>();
    private final float MIN_SCALE = 0.5f;
    private final float MAX_SCALE = 3.0f;
    private final float DEFAULT_BASE_SIZE_SP = 14f;
    private float baseSizeSp = DEFAULT_BASE_SIZE_SP;
    private int tabWidth = 0;
    private int tabLength = 0;
    private int tabWidthInCharacters;
    private int mUpdateDelayTime = 500;
    private boolean modified = true;
    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            Editable source = getText();
            highlightWithoutChange(source);
        }
    };
    private boolean highlightWhileTextChanging = true;
    private boolean hasErrors = false;
    private boolean mRemoveErrorsWhenTextChanged = true;
    private Rect lineNumberRect;
    private Paint lineNumberPaint;
    private boolean enableLineNumber = false;
    private boolean enableRelativeLineNumber = false;
    private Rect lineBounds;
    private Paint highlightLinePaint;
    private boolean enableHighlightCurrentLine = false;
    private int currentIndentation = 0;
    private boolean enableAutoIndentation = false;
    private final OnKeyListener mOnKeyListener = (v, keyCode, event) -> {
        if (!enableAutoIndentation) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                currentIndentation++;
                break;
            case KeyEvent.KEYCODE_DEL:
                if (currentIndentation > 0)
                    currentIndentation--;
                break;
        }
        return false;
    };
    private final InputFilter mInputFilter = (source, start, end, dest, dStart, dEnd) -> {
        if (modified && enableAutoIndentation && start < source.length()) {
            if (source.charAt(start) == '\n') {
                if (dest.length() == dEnd) return applyIndentation(source, currentIndentation);
                int indentation = calculateSourceIndentation(dest.subSequence(0, dStart));
                if (indentationEnds.contains(dest.charAt(dEnd))) indentation -= tabLength;
                return applyIndentation(source, indentation);
            }
        }
        return source;
    };
    private int currentMatchedIndex = -1;
    private int matchingColor = Color.YELLOW;
    private CharacterStyle currentMatchedToken;
    private int maxNumberOfSuggestions = Integer.MAX_VALUE;
    private int autoCompleteItemHeightInDp = (int) (50 * Resources.getSystem().getDisplayMetrics().density);
    private boolean enablePairComplete = false;
    private boolean enablePairCompleteCenterCursor = false;
    private final TextWatcher mEditorTextWatcher = new TextWatcher() {
        private int start;
        private int count;
        @Contract(mutates = "this")
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {
            this.start = start;
            this.count = count;
        }
        @Override
        public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
            if (!modified) return;
            if (highlightWhileTextChanging && mSyntaxPatternMap.size() > 0) {
                convertTabs(getEditableText(), start, count);
                mUpdateHandler.postDelayed(mUpdateRunnable, mUpdateDelayTime);
            }
            if (mRemoveErrorsWhenTextChanged) removeAllErrorLines();
            if (count == 1 && (enableAutoIndentation || enablePairComplete)) {
                char currentChar = charSequence.charAt(start);
                if (enableAutoIndentation) {
                    if (indentationStarts.contains(currentChar))
                        currentIndentation += tabLength;
                    else if (indentationEnds.contains(currentChar))
                        currentIndentation -= tabLength;
                }
                if (enablePairComplete) {
                    Character pairValue = mPairCompleteMap.get(currentChar);
                    if (pairValue != null) {
                        modified = false;
                        int selectionEnd = getSelectionEnd();
                        getText().insert(selectionEnd, pairValue.toString());
                        if (enablePairCompleteCenterCursor) setSelection(selectionEnd);
                        if (enableAutoIndentation) {
                            if (indentationStarts.contains(pairValue))
                                currentIndentation += tabLength;
                            else if (indentationEnds.contains(pairValue))
                                currentIndentation -= tabLength;
                        }
                        modified = true;
                    }
                }
            }
        }
        @Override
        public void afterTextChanged(Editable editable) {
            if (!highlightWhileTextChanging && modified) {
                cancelHighlighterRender();
                if (mSyntaxPatternMap.size() > 0) {
                    convertTabs(getEditableText(), start, count);
                    mUpdateHandler.postDelayed(mUpdateRunnable, mUpdateDelayTime);
                }
            }
        }
    };
    private MultiAutoCompleteTextView.Tokenizer mAutoCompleteTokenizer;
    private float scaleFactor = 1f;
    private ScaleGestureDetector scaleDetector;
    public CodeView(Context context) {
        super(context);
        initEditorView();
    }
    public CodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initEditorView();
    }
    public CodeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initEditorView();
    }
    private void saveScaleFactor(float factor) {
        getContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_TEXT_SCALE_FACTOR, factor).apply();
    }
    public void applyTextSize(float finalSizeSp) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, finalSizeSp);
        float lineNumberSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, finalSizeSp, getResources().getDisplayMetrics());
        setLineNumberTextSize(lineNumberSizePx);
    }
    private void initEditorView() {
        if (mAutoCompleteTokenizer == null)
            mAutoCompleteTokenizer = new KeywordTokenizer();
        setTokenizer(mAutoCompleteTokenizer);
        setHorizontallyScrolling(true);
        setFilters(new InputFilter[]{mInputFilter});
        addTextChangedListener(mEditorTextWatcher);
        setOnKeyListener(mOnKeyListener);
        lineNumberRect = new Rect();
        lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineNumberPaint.setStyle(Paint.Style.FILL);
        lineBounds = new Rect();
        highlightLinePaint = new Paint();
        highlightLinePaint.setColor(LINE_HIGHLIGHT_DEFAULT_COLOR);
        scaleFactor = getContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE).getFloat(KEY_TEXT_SCALE_FACTOR, 1.0f);
        baseSizeSp = getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        applyTextSize(baseSizeSp * scaleFactor); 
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));
                applyTextSize(baseSizeSp * scaleFactor);
                saveScaleFactor(scaleFactor);
                return true;
            }
        });
        setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            return v.onTouchEvent(event);
        });
        setEnableLineNumber(true);
        setEnableAutoIndentation(true);
        setTabLength(4);
        setEnableHighlightCurrentLine(true);
        setHighlightCurrentLineColor(Color.rgb(160, 200, 255));
        setMatchingHighlightColor(Color.rgb(191, 232, 245));
        setLineNumberTextColor(Color.GRAY);
        setTextColor(Color.GRAY);
        Typeface codeTypeface = Typeface.createFromAsset(getContext().getAssets(), "fonts/JetBrainsMono-Regular.ttf");
        setTypeface(codeTypeface);
    }
    protected void onDraw(Canvas canvas) {
        if (enableLineNumber || enableHighlightCurrentLine) {
            final Editable fullText = getText();
            final Layout layout = getLayout();
            final int lineCount = getLineCount();
            if (lineCount <= 0) {
                super.onDraw(canvas);
                return;
            }
            final int selectionStart = Selection.getSelectionStart(fullText);
            final int cursorLine = (selectionStart >= 0) ? layout.getLineForOffset(selectionStart) : 0;
            if (enableHighlightCurrentLine) {
                getLineBounds(cursorLine, lineBounds);
                canvas.drawRect(getScrollX(), lineBounds.top, getScrollX() + getWidth(), lineBounds.bottom, highlightLinePaint);
            }
            if (enableLineNumber) {
                final float DENSITY = getResources().getDisplayMetrics().density;
                final int LINE_NUMBER_BUFFER_DP = 8;
                final int lineNumberBufferPx = (int) (LINE_NUMBER_BUFFER_DP * DENSITY);
                final String maxLineNumber = String.valueOf(lineCount);
                final float textWidth = lineNumberPaint.measureText(maxLineNumber);
                final int requiredPaddingLeft = (int) (textWidth + lineNumberBufferPx);
                if (getPaddingLeft() != requiredPaddingLeft) {
                    setPadding(requiredPaddingLeft, getPaddingTop(), getPaddingRight(), getPaddingBottom());
                }
                final int scrollX = getScrollX();
                canvas.save();
                canvas.translate(-scrollX, 0);
                float xPos = requiredPaddingLeft - (LINE_NUMBER_BUFFER_DP * DENSITY / 2.0f);
                int startLine = layout.getLineForVertical(getScrollY());
                int endLine = layout.getLineForVertical(getScrollY() + getHeight());
                for (int i = startLine; i <= endLine; ++i) {
                    if (i == 0 || (layout.getLineStart(i) > 0 && fullText.charAt(layout.getLineStart(i) - 1) == '\n')) {
                        final int baseline = getLineBounds(i, null);
                        int currentCursorLine = (cursorLine >= 0 && cursorLine < lineCount) ? cursorLine : 0;
                        int lineNumber = (i == currentCursorLine || !enableRelativeLineNumber) ? i + 1 : Math.abs(currentCursorLine - i);
                        String lineNumberStr = String.valueOf(lineNumber);
                        canvas.drawText(lineNumberStr,
                                xPos - lineNumberPaint.measureText(lineNumberStr),
                                baseline, lineNumberPaint);
                    }
                }
                canvas.restore(); 
            }
        }
        super.onDraw(canvas);
    }
    @Override
    public List<Token> findMatches(@NonNull String regex) {
        matchedTokens.clear();
        if (regex.isEmpty()) return matchedTokens;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(getText());
        while (matcher.find()) matchedTokens.add(new Token(matcher.start(), matcher.end()));
        return matchedTokens;
    }
    @Override
    public Token findNextMatch() {
        if (matchedTokens.isEmpty()) return null;
        currentMatchedIndex++;
        if (currentMatchedIndex >= matchedTokens.size()) currentMatchedIndex = 0;
        Token currentMatch = matchedTokens.get(currentMatchedIndex);
        clearHighlightingMatchingToken();
        highlightMatchingToken(currentMatch);
        return currentMatch;
    }
    @Override
    public Token findPrevMatch() {
        if (matchedTokens.isEmpty()) return null;
        currentMatchedIndex--;
        if (currentMatchedIndex < 0) currentMatchedIndex = 0;
        Token currentMatch = matchedTokens.get(currentMatchedIndex);
        clearHighlightingMatchingToken();
        highlightMatchingToken(currentMatch);
        return currentMatch;
    }
    @Override
    public void clearMatches() {
        clearHighlightingMatchingToken();
        currentMatchedToken = null;
        currentMatchedIndex = -1;
        matchedTokens.clear();
    }
    @Override
    public void replaceFirstMatch(String regex, String replacement) {
        Pattern pattern = Pattern.compile(regex);
        String text = pattern.matcher(getText().toString()).replaceFirst(replacement);
        setTextHighlighted(text);
    }
    @Override
    public void replaceAllMatches(String regex, String replacement) {
        Pattern pattern = Pattern.compile(regex);
        String text = pattern.matcher(getText().toString()).replaceAll(replacement);
        setTextHighlighted(text);
    }
    private void highlightSyntax(Editable editable) {
        if (mSyntaxPatternMap.isEmpty()) return;
        Set<Map.Entry<Pattern, Integer>> syntaxSet = mSyntaxPatternMap.entrySet();
        for (Map.Entry<Pattern, Integer> syntax : syntaxSet) {
            Matcher matcher = syntax.getKey().matcher(editable);
            while (matcher.find()) {
                createForegroundColorSpan(editable, matcher, syntax.getValue());
            }
        }
    }
    private void highlightErrorLines(Editable editable) {
        if (mErrorHashSet.isEmpty()) return;
        int maxErrorLineValue = mErrorHashSet.lastKey();
        int lineNumber = 0;
        Matcher matcher = PATTERN_LINE.matcher(editable);
        while (matcher.find()) {
            if (mErrorHashSet.containsKey(lineNumber)) {
                int color = mErrorHashSet.get(lineNumber);
                createBackgroundColorSpan(editable, matcher, color);
            }
            lineNumber = lineNumber + 1;
            if (lineNumber > maxErrorLineValue) break;
        }
    }
    private void createForegroundColorSpan(@NonNull Editable editable, @NonNull Matcher matcher, @ColorInt int color) {
        editable.setSpan(new ForegroundColorSpan(color),
                matcher.start(), matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void createBackgroundColorSpan(@NonNull Editable editable, @NonNull Matcher matcher, @ColorInt int color) {
        editable.setSpan(new BackgroundColorSpan(color),
                matcher.start(), matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void highlightMatchingToken(@NonNull Token token) {
        currentMatchedToken = new BackgroundColorSpan(matchingColor);
        getEditableText().setSpan(currentMatchedToken,
                token.getStart(), token.getEnd(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void clearHighlightingMatchingToken() {
        if (currentMatchedToken == null) return;
        getEditableText().removeSpan(currentMatchedToken);
    }
    @NonNull
    @Contract("_ -> param1")
    private Editable highlight(@NonNull Editable editable) {
        if (editable.length() == 0) return editable;
        try {
            clearSpans(editable);
            highlightErrorLines(editable);
            highlightSyntax(editable);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        return editable;
    }
    private void highlightWithoutChange(Editable editable) {
        modified = false;
        highlight(editable);
        modified = true;
    }
    public void setTextHighlighted(CharSequence text) {
        if (text == null || text.length() == 0) return;
        cancelHighlighterRender();
        removeAllErrorLines();
        modified = false;
        setText(highlight(new SpannableStringBuilder(text)));
        modified = true;
    }
    public void setTabLength(int length) {
        tabLength = length;
    }
    public void setTabWidth(int characters) {
        if (tabWidthInCharacters == characters) return;
        tabWidthInCharacters = characters;
        tabWidth = Math.round(getPaint().measureText("m") * characters);
    }
    private void clearSpans(@NonNull Editable editable) {
        int length = editable.length();
        ForegroundColorSpan[] foregroundSpans = editable.getSpans(
                0, length, ForegroundColorSpan.class);
        for (int i = foregroundSpans.length; i-- > 0; )
            editable.removeSpan(foregroundSpans[i]);
        BackgroundColorSpan[] backgroundSpans = editable.getSpans(
                0, length, BackgroundColorSpan.class);
        for (int i = backgroundSpans.length; i-- > 0; )
            editable.removeSpan(backgroundSpans[i]);
    }
    public void cancelHighlighterRender() {
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
    }
    private void convertTabs(Editable editable, int start, int count) {
        if (tabWidth < 1) return;
        String s = editable.toString();
        for (int stop = start + count;
             (start = s.indexOf("\t", start)) > -1 && start < stop;
             ++start) {
            editable.setSpan(new CodeView.TabWidthSpan(),
                    start,
                    start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    public void setSyntaxPatternsMap(Map<Pattern, Integer> syntaxPatterns) {
        if (!mSyntaxPatternMap.isEmpty()) mSyntaxPatternMap.clear();
        mSyntaxPatternMap.putAll(syntaxPatterns);
    }
    public void addSyntaxPattern(Pattern pattern, @ColorInt int Color) {
        mSyntaxPatternMap.put(pattern, Color);
    }
    public void removeSyntaxPattern(Pattern pattern) {
        mSyntaxPatternMap.remove(pattern);
    }
    public int getSyntaxPatternsSize() {
        return mSyntaxPatternMap.size();
    }
    public void resetSyntaxPatternList() {
        mSyntaxPatternMap.clear();
    }
    public void resetHighlighter() {
        clearSpans(getText());
    }
    public void setEnableAutoIndentation(boolean enableAutoIndentation) {
        this.enableAutoIndentation = enableAutoIndentation;
    }
    public void setIndentationStarts(Set<Character> characters) {
        indentationStarts.clear();
        indentationStarts.addAll(characters);
    }
    public void setIndentationEnds(Set<Character> characters) {
        indentationEnds.clear();
        indentationEnds.addAll(characters);
    }
    public void addErrorLine(int lineNum, int color) {
        mErrorHashSet.put(lineNum, color);
        hasErrors = true;
    }
    public void removeErrorLine(int lineNum) {
        mErrorHashSet.remove(lineNum);
        hasErrors = mErrorHashSet.size() > 0;
    }
    public void removeAllErrorLines() {
        mErrorHashSet.clear();
        hasErrors = false;
    }
    public int getErrorsSize() {
        return mErrorHashSet.size();
    }
    public String getTextWithoutTrailingSpace() {
        return PATTERN_TRAILING_WHITE_SPACE
                .matcher(getText())
                .replaceAll("");
    }
    public void setAutoCompleteTokenizer(MultiAutoCompleteTextView.Tokenizer tokenizer) {
        mAutoCompleteTokenizer = tokenizer;
    }
    public void setRemoveErrorsWhenTextChanged(boolean removeErrors) {
        mRemoveErrorsWhenTextChanged = removeErrors;
    }
    public void reHighlightSyntax() {
        highlightSyntax(getEditableText());
    }
    public void reHighlightErrors() {
        highlightErrorLines(getEditableText());
    }
    public boolean isHasError() {
        return hasErrors;
    }
    public int getUpdateDelayTime() {
        return mUpdateDelayTime;
    }
    public void setUpdateDelayTime(int time) {
        mUpdateDelayTime = time;
    }
    public void setHighlightWhileTextChanging(boolean updateWhileTextChanging) {
        this.highlightWhileTextChanging = updateWhileTextChanging;
    }
    public void setEnableLineNumber(boolean enableLineNumber) {
        this.enableLineNumber = enableLineNumber;
    }
    public boolean isLineNumberEnabled() {
        return enableLineNumber;
    }
    public void setEnableRelativeLineNumber(boolean enableRelativeLineNumber) {
        this.enableRelativeLineNumber = enableRelativeLineNumber;
    }
    public boolean isLineRelativeNumberEnabled() {
        return enableRelativeLineNumber;
    }
    public void setEnableHighlightCurrentLine(boolean enableHighlightCurrentLine) {
        this.enableHighlightCurrentLine = enableHighlightCurrentLine;
    }
    public boolean isHighlightCurrentLineEnabled() {
        return enableHighlightCurrentLine;
    }
    public void setHighlightCurrentLineColor(int color) {
        highlightLinePaint.setColor(color);
    }
    public void setLineNumberTextColor(int color) {
        lineNumberPaint.setColor(color);
    }
    public void setLineNumberTextSize(float size) {
        lineNumberPaint.setTextSize(size);
    }
    public void setMatchingHighlightColor(int color) {
        matchingColor = color;
    }
    public void setLineNumberTypeface(Typeface typeface) {
        lineNumberPaint.setTypeface(typeface);
    }
    public void setMaxSuggestionsSize(int maxSuggestions) {
        maxNumberOfSuggestions = maxSuggestions;
    }
    public void setAutoCompleteItemHeightInDp(int height) {
        autoCompleteItemHeightInDp = (int) (height * Resources.getSystem().getDisplayMetrics().density);
    }
    public void enablePairComplete(boolean enable) {
        enablePairComplete = enable;
    }
    public void enablePairCompleteCenterCursor(boolean enable) {
        enablePairCompleteCenterCursor = enable;
    }
    public void setPairCompleteMap(Map<Character, Character> map) {
        mPairCompleteMap.clear();
        mPairCompleteMap.putAll(map);
    }
    public void addPairCompleteItem(char key, char value) {
        mPairCompleteMap.put(key, value);
    }
    public void removePairCompleteItem(char key) {
        mPairCompleteMap.remove(key);
    }
    public void clearPairCompleteMap() {
        mPairCompleteMap.clear();
    }
    @Override
    public void showDropDown() {
        final Layout layout = getLayout();
        final int position = getSelectionStart();
        final int line = layout.getLineForOffset(position);
        final int lineButton = layout.getLineBottom(line);
        int numberOfMatchedItems = getAdapter().getCount();
        if (numberOfMatchedItems > maxNumberOfSuggestions) {
            numberOfMatchedItems = maxNumberOfSuggestions;
        }
        int dropDownHeight = getDropDownHeight();
        int modifiedDropDownHeight = numberOfMatchedItems * autoCompleteItemHeightInDp;
        if (dropDownHeight != modifiedDropDownHeight) {
            dropDownHeight = modifiedDropDownHeight;
        }
        final Rect displayFrame = new Rect();
        getGlobalVisibleRect(displayFrame);
        int displayFrameHeight = displayFrame.height();
        int verticalOffset = lineButton + dropDownHeight;
        if (verticalOffset > displayFrameHeight) {
            verticalOffset = displayFrameHeight - autoCompleteItemHeightInDp;
        }
        setDropDownHeight(dropDownHeight);
        setDropDownVerticalOffset(verticalOffset - displayFrameHeight - dropDownHeight);
        setDropDownHorizontalOffset((int) layout.getPrimaryHorizontal(position));
        super.showDropDown();
    }
    @NonNull
    private @Unmodifiable CharSequence applyIndentation(CharSequence source, int indentation) {
        StringBuilder sourceCode = new StringBuilder();
        sourceCode.append(source);
        for (int i = 0; i < indentation; i++) sourceCode.append(" ");
        return sourceCode.toString();
    }
    private int calculateSourceIndentation(@NonNull CharSequence source) {
        int indentation = 0;
        String[] lines = source.toString().split("\n");
        for (String line : lines) {
            indentation += calculateExtraIndentation(line);
        }
        return indentation;
    }
    private int calculateExtraIndentation(@NonNull String line) {
        if (line.isEmpty()) return 0;
        char firstChar = line.charAt(line.length() - 1);
        if (indentationStarts.contains(firstChar)) return tabLength;
        else if (indentationEnds.contains(firstChar)) return -tabLength;
        return 0;
    }
    private final class TabWidthSpan extends ReplacementSpan {
        @Contract(pure = true)
        @Override
        public int getSize(@NonNull Paint paint, CharSequence text,
                           int start, int end, Paint.FontMetricsInt fm) {
            return tabWidth;
        }
        @Contract(pure = true)
        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text,
                         int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
        }
    }
}
