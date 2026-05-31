package com.csmide.app.activities;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.csmide.R;
import com.csmide.app.utils.AppPreferences;
import com.csmide.app.utils.DisplayManager;
import com.csmide.app.utils.FontManager;

import java.util.List;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Provides an interface for users to customize the code editor's behavior and appearance.
 * Features include toggling line numbers, word wrap, auto-indentation, and a live
 * {@link CodeEditor} preview to visualize changes in real-time.
 */
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
	private Spinner fontFamilySpinner;
	private CodeEditor editorPreview;
	private View rootLayout;
	private ScaleGestureDetector scaleGestureDetector;
	private float previewScaleFactor = 1.0f;
	private int previewBaseTextSize;

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

	@Override
	protected void onResume() {
		super.onResume();
		loadPreferences();
	}

	/**
	 * Configures the activity toolbar with a back navigation button.
	 */
	private void setupToolbar() {
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	/**
	 * Initializes UI components and sets up dynamic layout adjustments for system insets.
	 */
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
		fontFamilySpinner = findViewById(R.id.fontFamilySpinner);
		editorPreview = findViewById(R.id.editorPreview);
	}

	/**
	 * Loads saved settings from SharedPreferences and applies them to the UI components.
	 */
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

		// Load available fonts
		List<String> fonts = FontManager.getAvailableFonts(this);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item_codestudio, fonts);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		fontFamilySpinner.setAdapter(adapter);

		String currentFont = prefs.getString(AppPreferences.KEY_EDITOR_FONT, AppPreferences.DEFAULT_FONT);
		int selection = fonts.indexOf(currentFont);
		if (selection != -1) {
			fontFamilySpinner.setSelection(selection);
		}

		previewBaseTextSize = textSize;
		updatePreviewTextSize();
		editorPreview.setLineNumberEnabled(showLineNumbersSwitch.isChecked());
		editorPreview.setWordwrap(wordWrapSwitch.isChecked());

		FontManager.applyFontToViewHierarchy(getWindow().getDecorView(), FontManager.getTypeface(this));
	}

	/**
	 * Sets up event listeners for all interactive UI elements to persist changes.
	 */
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

		fontFamilySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
				String selectedFont = (String) parent.getItemAtPosition(position);
				String currentFont = prefs.getString(AppPreferences.KEY_EDITOR_FONT, AppPreferences.DEFAULT_FONT);
				if (!selectedFont.equals(currentFont)) {
					FontManager.updateFont(EditorActivity.this, selectedFont);
					updateEditorPreviewFont();
					FontManager.applyFontToViewHierarchy(getWindow().getDecorView(), FontManager.getTypeface(EditorActivity.this));
					Toast.makeText(EditorActivity.this, "Font applied.", Toast.LENGTH_SHORT).show();
				}
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {
			}
		});

		textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int size = progress + 8;
				textSizeValue.setText(String.valueOf(size));
				prefs.edit().putInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, size).apply();
				previewBaseTextSize = size;
				updatePreviewTextSize();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	/**
	 * Configures the Sora Editor preview widget with static text and the selected font.
	 */
	private void setupEditorPreview() {
		editorPreview.setText(getString(R.string.editor_preview_text));
		editorPreview.setEditorLanguage(new EmptyLanguage());
		editorPreview.setEditable(false);

		updateEditorPreviewFont();

		DisplayManager.applyIdeEditorTheme(this, editorPreview);
		setupPreviewZoomGesture();
	}

	private void updateEditorPreviewFont() {
		try {
			Typeface typeface = FontManager.getTypeface(this);
			editorPreview.setTypefaceText(typeface);
			editorPreview.setTypefaceLineNumber(typeface);
		} catch (Exception ignored) {
			// Fallback to system monospace if asset is missing
		}
	}

	private void setupPreviewZoomGesture() {
		scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				if (!pinchToZoomSwitch.isChecked()) {
					return false;
				}
				previewScaleFactor *= detector.getScaleFactor();
				previewScaleFactor = Math.max(0.5f, Math.min(previewScaleFactor, 3.0f));
				updatePreviewTextSize();
				return true;
			}
		});

		editorPreview.setOnTouchListener((v, event) -> {
			scaleGestureDetector.onTouchEvent(event);
			return false;
		});
	}

	private void updatePreviewTextSize() {
		if (editorPreview != null) {
			editorPreview.setTextSize(previewBaseTextSize * previewScaleFactor);
		}
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
