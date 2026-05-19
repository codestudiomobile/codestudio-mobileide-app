package com.cs.ide.app.activities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.cs.ide.R;
import com.cs.ide.app.utils.AppPreferences;
import com.cs.ide.app.utils.DisplayManager;

import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Activity for configuring editor settings like text size, line numbers, and syntax highlighting.
 * Includes a live preview of the editor.
 */
public class EditorActivity extends AppCompatActivity {

    // UI Components - Switches
    private SwitchCompat openEditorOnStartup;
    private SwitchCompat openWelcomeScreenOnStartup;
    private SwitchCompat pinchToZoomSwitch;
    private SwitchCompat showLineNumbersSwitch;
    private SwitchCompat autoIndentationSwitch;
    private SwitchCompat syntaxHighlightingSwitch;
    private SwitchCompat wordWrapSwitch;

    // UI Components - Others
    private SeekBar textSizeSeekBar;
    private TextView textSizeValue;
    private CodeEditor editorPreview;
    private View rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor_code_studio);

        setupToolbar();
        initViews();
        loadPreferences();
        
        SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
        setupListeners(prefs);
        setupEditorPreview();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initViews() {
        rootLayout = findViewById(R.id.editorLayout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, DisplayManager::setupDynamicMarginHandling);
        }

        openEditorOnStartup = findViewById(R.id.openEditorOnStartup);
        openWelcomeScreenOnStartup = findViewById(R.id.openWelcomeScreenOnStartup);
        pinchToZoomSwitch = findViewById(R.id.pinchToZoomSwitch);
        showLineNumbersSwitch = findViewById(R.id.showLineNumbersSwitch);
        autoIndentationSwitch = findViewById(R.id.autoIndentationSwitch);
        syntaxHighlightingSwitch = findViewById(R.id.syntaxHighlightingSwitch);
        wordWrapSwitch = findViewById(R.id.wordWrapSwitch);
        
        textSizeSeekBar = findViewById(R.id.textSizeSeekBar);
        textSizeValue = findViewById(R.id.textSizeValue);
        editorPreview = findViewById(R.id.editorPreview);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);

        openEditorOnStartup.setChecked(prefs.getBoolean(AppPreferences.KEY_EDITOR_STARTUP, true));
        openWelcomeScreenOnStartup.setChecked(prefs.getBoolean(AppPreferences.KEY_WELCOME_STARTUP, true));
        pinchToZoomSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_PINCH_TO_ZOOM, true));
        showLineNumbersSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_SHOW_LINE_NUMBERS, true));
        autoIndentationSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_AUTO_INDENTATION, true));
        syntaxHighlightingSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_SYNTAX_HIGHLIGHTING, true));
        wordWrapSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_WORD_WRAP, false));

        int textSize = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
        textSizeSeekBar.setProgress(textSize - 8);
        textSizeValue.setText(String.valueOf(textSize));
        
        editorPreview.setTextSize(textSize);
        editorPreview.setLineNumberEnabled(showLineNumbersSwitch.isChecked());
        editorPreview.setWordwrap(wordWrapSwitch.isChecked());
    }

    private void setupListeners(SharedPreferences prefs) {
        openEditorOnStartup.setOnCheckedChangeListener((v, checked) -> 
            prefs.edit().putBoolean(AppPreferences.KEY_EDITOR_STARTUP, checked).apply());
        
        openWelcomeScreenOnStartup.setOnCheckedChangeListener((v, checked) -> 
            prefs.edit().putBoolean(AppPreferences.KEY_WELCOME_STARTUP, checked).apply());
        
        pinchToZoomSwitch.setOnCheckedChangeListener((v, checked) -> 
            prefs.edit().putBoolean(AppPreferences.KEY_PINCH_TO_ZOOM, checked).apply());

        showLineNumbersSwitch.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(AppPreferences.KEY_SHOW_LINE_NUMBERS, checked).apply();
            editorPreview.setLineNumberEnabled(checked);
        });

        autoIndentationSwitch.setOnCheckedChangeListener((v, checked) -> 
            prefs.edit().putBoolean(AppPreferences.KEY_AUTO_INDENTATION, checked).apply());

        syntaxHighlightingSwitch.setOnCheckedChangeListener((v, checked) -> 
            prefs.edit().putBoolean(AppPreferences.KEY_SYNTAX_HIGHLIGHTING, checked).apply());

        wordWrapSwitch.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(AppPreferences.KEY_WORD_WRAP, checked).apply();
            editorPreview.setWordwrap(checked);
        });

        textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 8;
                textSizeValue.setText(String.valueOf(size));
                prefs.edit().putInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, size).apply();
                editorPreview.setTextSize(size);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupEditorPreview() {
        editorPreview.setText(getString(R.string.editor_preview_text));
        editorPreview.setEditorLanguage(new EmptyLanguage());
        editorPreview.setEditable(false);

        try {
            Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Regular.ttf");
            editorPreview.setTypefaceText(typeface);
            editorPreview.setTypefaceLineNumber(typeface);
        } catch (Exception ignored) {}

        editorPreview.subscribeAlways(ColorSchemeUpdateEvent.class, (event) -> applyPreviewTheme(event.getColorScheme()));
    }

    private void applyPreviewTheme(EditorColorScheme scheme) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, ContextCompat.getColor(this, R.color.ide_background));
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, ContextCompat.getColor(this, R.color.ide_text_primary));
        scheme.setColor(EditorColorScheme.LINE_NUMBER, ContextCompat.getColor(this, R.color.ide_line_number));
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, ContextCompat.getColor(this, R.color.ide_background));
        scheme.setColor(EditorColorScheme.CURRENT_LINE, ContextCompat.getColor(this, R.color.ide_current_line));
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, Color.WHITE);
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, Color.WHITE);
        scheme.setColor(EditorColorScheme.TEXT_SELECTED, Color.GRAY);

        scheme.setColor(EditorColorScheme.KEYWORD, ContextCompat.getColor(this, R.color.syntax_keyword));
        scheme.setColor(EditorColorScheme.LITERAL, ContextCompat.getColor(this, R.color.syntax_string));
        scheme.setColor(EditorColorScheme.COMMENT, ContextCompat.getColor(this, R.color.syntax_comment));
        scheme.setColor(EditorColorScheme.OPERATOR, ContextCompat.getColor(this, R.color.syntax_keyword));
        scheme.setColor(EditorColorScheme.ANNOTATION, ContextCompat.getColor(this, R.color.syntax_type));
        scheme.setColor(EditorColorScheme.FUNCTION_NAME, ContextCompat.getColor(this, R.color.syntax_function));
        scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, ContextCompat.getColor(this, R.color.syntax_function));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
