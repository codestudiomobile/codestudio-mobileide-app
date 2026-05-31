package com.csmide.app.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.csmide.R;
import com.csmide.app.adapters.ViewPagerAdapter;
import com.csmide.app.editor.SoraLanguageManager;
import com.csmide.app.utils.DisplayManager;
import com.csmide.app.views.ExtraKeysView;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Fragment for editing text files.
 * Provides syntax highlighting, auto-saving logic, and extra shortcut keys for coding.
 */
public class TextFragment extends Fragment implements TextWatcher, SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String TAG = "TextFragment";
	private static final String ARG_URI = "file_uri";

	private CodeEditor fileContent;
	private SoraLanguageManager languageManager;
	private boolean isSaved = true;
	private Uri fileUri;
	private String fileName;
	private ScaleGestureDetector scaleGestureDetector;
	private float scaleFactor = 1.0f;
	private int baseTextSize;

	/**
	 * Creates a new instance of TextFragment for the given file URI.
	 *
	 * @param uri The URI of the file to edit.
	 * @return A new TextFragment instance.
	 */
	public static TextFragment newInstance(Uri uri) {
		TextFragment fragment = new TextFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_URI, uri);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			fileUri = getArguments().getParcelable(ARG_URI);
		}
	}

	/**
	 * Checks if the current content has been saved to disk.
	 *
	 * @return True if saved, false otherwise.
	 */
	public boolean isSaved() {
		return isSaved;
	}

	/**
	 * Sets the saved state of the fragment.
	 *
	 * @param saved The new saved state.
	 */
	public void setSaved(boolean saved) {
		this.isSaved = saved;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_text_code_studio, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		fileContent = view.findViewById(R.id.fileContent);
		languageManager = new SoraLanguageManager(requireContext());

		// Apply dynamic margin handling to handle keyboard height correctly
		ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
			WindowInsetsCompat result = DisplayManager.setupDynamicMarginHandling(v, insets);
			if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
				if (params.topMargin != 0) {
					params.topMargin = 0; // Remove redundant top margin (status bar height) to fix the gap above the editor
					v.setLayoutParams(params);
				}
			}
			return result;
		});

		setupEditor();
		setupExtraKeys(view);
		loadFileContent();
	}

	/**
	 * Configures the CodeView editor settings.
	 */
	private void setupEditor() {
		fileContent.setNestedScrollingEnabled(true);
		fileContent.setHighlightCurrentLine(true);

		// Enable auto-closing of brackets and quotes
		fileContent.getProps().symbolPairAutoCompletion = true;

		// Disable deleting multiple spaces at once to fix backspace behavior on indented lines
		fileContent.getProps().deleteMultiSpaces = 0;

		// Apply selected font
		try {
			Typeface typeface = com.csmide.app.utils.FontManager.getTypeface(requireContext());
			fileContent.setTypefaceText(typeface);
			fileContent.setTypefaceLineNumber(typeface);
		} catch (Exception e) {
			Log.e(TAG, "Failed to load font", e);
		}

		// Sora Editor handles highlighting through Language objects
		fileContent.setEditorLanguage(new EmptyLanguage());

		// Subscribe to text change events to track save state
		fileContent.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
			if (event.getAction() != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
				isSaved = false;
			}
		});

		// Ensure colors stay consistent even if color scheme is updated by language plugins
		DisplayManager.applyIdeEditorTheme(requireContext(), fileContent);

		// Implement custom backspace behavior: remove entire indentation before moving to the previous line
		fileContent.setOnKeyListener((v, keyCode, event) -> {
			if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
				Cursor cursor = fileContent.getCursor();
				if (cursor.isSelected()) return false;

				int line = cursor.getLeftLine();
				int column = cursor.getLeftColumn();
				Content content = fileContent.getText();
				if (line < 0 || line >= content.getLineCount()) return false;
				
				String lineText = content.getLineString(line);
				if (column > 0 && column <= lineText.length()) {
					String textBefore = lineText.substring(0, column);
					if (textBefore.trim().isEmpty()) {
						// All characters before cursor are whitespaces.
						// Delete one indent level (default 4 spaces) at a time.
						int tabSize = fileContent.getProps().deleteMultiSpaces;
						if (tabSize <= 0) tabSize = 4;
						
						int toDelete = column % tabSize;
						if (toDelete == 0) toDelete = tabSize;
						
						content.delete(line, column - toDelete, line, column);
						return true;
					}
				}
			}
			return false;
		});

		applyPreferences();
		setupZoomGesture();

		// Robust text selection: Long press to select the whole word (delimited by spaces)
		fileContent.setOnLongClickListener(v -> {
			Cursor cursor = fileContent.getCursor();
			if (cursor != null && !cursor.isSelected()) {
				int line = cursor.getLeftLine();
				int column = cursor.getLeftColumn();
				selectWordAt(line, column);
			}
			return false; // Return false to allow the editor to show the context menu
		});
	}

	/**
	 * Selects the word at the given line and column, defined by whitespace boundaries.
	 * This provides a more professional feel for text selection.
	 */
	private void selectWordAt(int line, int column) {
		Content text = fileContent.getText();
		if (text == null || line < 0 || line >= text.getLineCount()) return;

		String lineText = text.getLineString(line);
		if (column < 0 || column > lineText.length()) return;

		int start = column;
		// Expand left until space or line start
		while (start > 0 && !Character.isWhitespace(lineText.charAt(start - 1))) {
			start--;
		}

		int end = column;
		// Expand right until space or line end
		while (end < lineText.length() && !Character.isWhitespace(lineText.charAt(end))) {
			end++;
		}

		if (start < end) {
			fileContent.getCursor().setLeft(line, start);
			fileContent.getCursor().setRight(line, end);
		}
	}

	private void applyPreferences() {
		if (fileContent == null || !isAdded()) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, android.content.Context.MODE_PRIVATE);

		baseTextSize = prefs.getInt(com.csmide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.csmide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
		updateEditorTextSize();

		boolean showLineNumbers = prefs.getBoolean(com.csmide.app.utils.AppPreferences.KEY_SHOW_LINE_NUMBERS, true);
		fileContent.setLineNumberEnabled(showLineNumbers);

		boolean autoIndentation = prefs.getBoolean(com.csmide.app.utils.AppPreferences.KEY_AUTO_INDENTATION, true);
		fileContent.getProps().autoIndent = autoIndentation;

		boolean wordWrap = prefs.getBoolean(com.csmide.app.utils.AppPreferences.KEY_WORD_WRAP, false);
		fileContent.setWordwrap(wordWrap);

		boolean syntaxHighlighting = prefs.getBoolean(com.csmide.app.utils.AppPreferences.KEY_SYNTAX_HIGHLIGHTING, true);
		if (syntaxHighlighting) {
			detectAndApplyLanguage();
		} else {
			fileContent.setEditorLanguage(new EmptyLanguage());
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		applyPreferences();
		scaleFactor = 1.0f; // Reset zoom on resume to sync with settings
		updateEditorTextSize();
		setupZoomGesture();

		// Robust text selection: Long press to select the whole word (delimited by spaces)
		fileContent.setOnLongClickListener(v -> {
			Cursor cursor = fileContent.getCursor();
			if (cursor != null && !cursor.isSelected()) {
				int line = cursor.getLeftLine();
				int column = cursor.getLeftColumn();
				selectWordAt(line, column);
			}
			return false; // Return false to allow the editor to show the context menu
		});
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (isAdded() && getActivity() != null) {
			getActivity().runOnUiThread(this::applyPreferences);
		}
	}

	/**
	 * Detects the language based on file extension and applies it to the editor.
	 */
	private void detectAndApplyLanguage() {
		if (fileUri == null) return;
		if (fileName == null) {
			// If not yet cached, fetch in background or just use temporary
			new Thread(() -> {
				String name = com.csmide.app.utils.FileUtils.getFileName(requireContext(), fileUri);
				if (isAdded()) {
					getActivity().runOnUiThread(() -> {
						this.fileName = name;
						detectAndApplyLanguage();
					});
				}
			}).start();
			return;
		}

		int lastDotIndex = fileName.lastIndexOf('.');
		String extension = lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";

		languageManager.applyLanguage(fileContent, extension);
	}

	/**
	 * Sets up the listener for the extra keys toolbar.
	 *
	 * @param view The fragment's root view.
	 */
	private void setupExtraKeys(View view) {
		ExtraKeysView extraKeysView = view.findViewById(R.id.editorExtraKeys);
		if (extraKeysView != null) {
			extraKeysView.setOnKeyActionListener(key -> {
				if (fileContent == null) return;
				handleExtraKey(key);
			});
		}
	}

	/**
	 * Handles actions from the extra keys toolbar.
	 *
	 * @param key The key command string.
	 */
	private void handleExtraKey(String key) {
		switch (key) {
			case "UP":
				fileContent.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
				break;
			case "DOWN":
				fileContent.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
				break;
			case "LEFT":
				fileContent.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
				break;
			case "RIGHT":
				fileContent.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
				break;
			case "TAB":
				fileContent.commitText("    ");
				break;
			case "UNDO":
				if (fileContent.canUndo()) fileContent.undo();
				break;
			case "REDO":
				if (fileContent.canRedo()) fileContent.redo();
				break;
			case "{}":
				fileContent.commitText("{}");
				fileContent.moveSelection(io.github.rosemoe.sora.widget.SelectionMovement.LEFT);
				break;
			case "[]":
				fileContent.commitText("[]");
				fileContent.moveSelection(io.github.rosemoe.sora.widget.SelectionMovement.LEFT);
				break;
			case "()":
				fileContent.commitText("()");
				fileContent.moveSelection(io.github.rosemoe.sora.widget.SelectionMovement.LEFT);
				break;
			case "\"\"":
				fileContent.commitText("\"\"");
				fileContent.moveSelection(io.github.rosemoe.sora.widget.SelectionMovement.LEFT);
				break;
			case "''":
				fileContent.commitText("''");
				fileContent.moveSelection(io.github.rosemoe.sora.widget.SelectionMovement.LEFT);
				break;
			default:
				fileContent.commitText(key);
				break;
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	@Override
	public void afterTextChanged(Editable s) {
		isSaved = false;
	}

	/**
	 * Gets the UTF-8 encoded contents of the editor.
	 *
	 * @return Byte array of the content.
	 */
	public byte[] getContents() {
		if (fileContent == null) return null;
		try {
			// Use toString() as writeTo(Writer) is not available in this version of Sora Editor
			return fileContent.getText().toString().getBytes(StandardCharsets.UTF_8);
		} catch (Exception e) {
			Log.e(TAG, "Error getting contents from editor", e);
			return null;
		}
	}

	/**
	 * Gets the editor's text content object.
	 *
	 * @return The Content object.
	 */
	public Content getEditorText() {
		return fileContent != null ? fileContent.getText() : null;
	}

	/**
	 * Asynchronously loads the file content from the given URI.
	 */
	private void loadFileContent() {
		if (fileUri == null || fileUri.toString().startsWith(ViewPagerAdapter.UNTITLED_URI_PREFIX))
			return;
		new Thread(() -> {
			if (com.csmide.app.utils.FileUtils.isBinaryFile(requireContext(), fileUri)) {
				return;
			}
			try (InputStream is = requireContext().getContentResolver().openInputStream(fileUri)) {
				if (is == null) return;
				String content = IOUtils.toString(is, StandardCharsets.UTF_8);
				if (getActivity() != null) {
					getActivity().runOnUiThread(() -> {
						fileContent.setText(content);
						isSaved = true;
					});
				}
			} catch (Exception e) {
				Log.e(TAG, "Error loading file content", e);
			}
		}).start();
	}

	/**
	 * Refreshes the editor content from disk.
	 */
	public void refreshContent() {
		loadFileContent();
	}

	private void setupZoomGesture() {
		scaleGestureDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				SharedPreferences prefs = requireContext().getSharedPreferences(com.csmide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
				if (!prefs.getBoolean(com.csmide.app.utils.AppPreferences.KEY_PINCH_TO_ZOOM, true)) {
					return false;
				}

				scaleFactor *= detector.getScaleFactor();
				// Limit zoom: baseTextSize is 8-38. Let's allow scaling from 0.5x to 2.0x, but cap at absolute 8 and 80.
				float newSize = baseTextSize * scaleFactor;
				if (newSize < 8) {
					newSize = 8;
					scaleFactor = newSize / baseTextSize;
				} else if (newSize > 80) {
					newSize = 80;
					scaleFactor = newSize / baseTextSize;
				}

				updateEditorTextSize();
				return true;
			}
		});

		fileContent.setOnTouchListener((v, event) -> {
			scaleGestureDetector.onTouchEvent(event);
			return false; // Let the editor handle its own scrolling/selection
		});
	}

	private void updateEditorTextSize() {
		if (fileContent != null) {
			fileContent.setTextSize(baseTextSize * scaleFactor);
		}
	}
}
