package com.cs.ide.app.activities;

import static com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_STARTUP;
import static com.cs.ide.app.utils.AppPreferences.KEY_WELCOME_STARTUP;
import static com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE;
import static com.cs.ide.app.utils.AppPreferences.KEY_PINCH_TO_ZOOM;
import static com.cs.ide.app.utils.AppPreferences.KEY_SHOW_LINE_NUMBERS;
import static com.cs.ide.app.utils.AppPreferences.KEY_AUTO_INDENTATION;
import static com.cs.ide.app.utils.AppPreferences.KEY_SYNTAX_HIGHLIGHTING;
import static com.cs.ide.app.utils.AppPreferences.KEY_WORD_WRAP;
import static com.cs.ide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE;
import static com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.cs.ide.R;
import com.cs.ide.app.editor.CodeView;
import com.cs.ide.app.utils.DisplayManager;

public class EditorActivity extends AppCompatActivity {
    private SwitchCompat openEditorOnStartup;
    private SwitchCompat openWelcomeScreenOnStartup;
    private SwitchCompat pinchToZoomSwitch;
    private SwitchCompat showLineNumbersSwitch;
    private SwitchCompat autoIndentationSwitch;
    private SwitchCompat syntaxHighlightingSwitch;
    private SwitchCompat wordWrapSwitch;
    private SeekBar textSizeSeekBar;
    private TextView textSizeValue;
    private CodeView editorPreview;
    private View rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor_code_studio);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        rootLayout = findViewById(R.id.editorLayout);
        
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, DisplayManager::setupDynamicMarginHandling);
        }
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.action_open_editor_settings);
        }
        
        initViews();
        loadPreferences();
    }

    private void initViews() {
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

        // Preview setup - non-editable
        editorPreview.setFocusable(false);
        editorPreview.setClickable(false);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        
        openEditorOnStartup.setChecked(prefs.getBoolean(KEY_EDITOR_STARTUP, false));
        openWelcomeScreenOnStartup.setChecked(prefs.getBoolean(KEY_WELCOME_STARTUP, true));
        pinchToZoomSwitch.setChecked(prefs.getBoolean(KEY_PINCH_TO_ZOOM, true));
        showLineNumbersSwitch.setChecked(prefs.getBoolean(KEY_SHOW_LINE_NUMBERS, true));
        autoIndentationSwitch.setChecked(prefs.getBoolean(KEY_AUTO_INDENTATION, true));
        syntaxHighlightingSwitch.setChecked(prefs.getBoolean(KEY_SYNTAX_HIGHLIGHTING, true));
        wordWrapSwitch.setChecked(prefs.getBoolean(KEY_WORD_WRAP, false));

        int textSize = prefs.getInt(KEY_EDITOR_TEXT_SIZE, DEFAULT_TEXT_SIZE);
        textSizeSeekBar.setProgress(textSize - 8); // Range 8 to 38
        textSizeValue.setText(String.valueOf(textSize));
        editorPreview.applyTextSize(textSize);

        setupListeners(prefs);
    }

    private void setupListeners(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();

        openEditorOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && !openWelcomeScreenOnStartup.isChecked()) {
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_EDITOR_STARTUP, isChecked).apply();
            }
        });
        
        openWelcomeScreenOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && !openEditorOnStartup.isChecked()) {
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_WELCOME_STARTUP, isChecked).apply();
            }
        });

        pinchToZoomSwitch.setOnCheckedChangeListener((v, isChecked) -> editor.putBoolean(KEY_PINCH_TO_ZOOM, isChecked).apply());
        showLineNumbersSwitch.setOnCheckedChangeListener((v, isChecked) -> {
            editor.putBoolean(KEY_SHOW_LINE_NUMBERS, isChecked).apply();
            editorPreview.setEnableLineNumber(isChecked);
            editorPreview.invalidate();
        });
        autoIndentationSwitch.setOnCheckedChangeListener((v, isChecked) -> editor.putBoolean(KEY_AUTO_INDENTATION, isChecked).apply());
        syntaxHighlightingSwitch.setOnCheckedChangeListener((v, isChecked) -> editor.putBoolean(KEY_SYNTAX_HIGHLIGHTING, isChecked).apply());
        wordWrapSwitch.setOnCheckedChangeListener((v, isChecked) -> editor.putBoolean(KEY_WORD_WRAP, isChecked).apply());

        textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 8;
                textSizeValue.setText(String.valueOf(size));
                editorPreview.applyTextSize(size);
                editor.putInt(KEY_EDITOR_TEXT_SIZE, size).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
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
