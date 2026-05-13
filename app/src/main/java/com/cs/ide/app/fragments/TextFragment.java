package com.cs.ide.app.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cs.ide.R;
import com.cs.ide.app.adapters.ViewPagerAdapter;
import com.cs.ide.app.editor.CodeView;
import com.cs.ide.app.views.ExtraKeysView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Fragment for editing text files.
 * Provides syntax highlighting, auto-saving logic, and extra shortcut keys for coding.
 */
public class TextFragment extends Fragment implements TextWatcher, SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String TAG = "TextFragment";
	private static final String ARG_URI = "file_uri";

	private CodeView fileContent;
	private boolean isSaved = true;
	private Uri fileUri;

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

		setupEditor();
		setupExtraKeys(view);
		loadFileContent();
	}

	/**
	 * Configures the CodeView editor settings.
	 */
	private void setupEditor() {
		fileContent.setNestedScrollingEnabled(true);
		fileContent.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ide_background));
		fileContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.ide_text_primary));
		fileContent.setLineNumberTextColor(ContextCompat.getColor(requireContext(), R.color.ide_line_number));
		fileContent.setHighlightCurrentLineColor(ContextCompat.getColor(requireContext(), R.color.ide_current_line));

		applyPreferences();
		addSyntaxPatterns();

		fileContent.addTextChangedListener(this);
	}

	private void applyPreferences() {
		if (!isAdded() || fileContent == null) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, android.content.Context.MODE_PRIVATE);
		
		int textSize = prefs.getInt(com.cs.ide.app.utils.AppPreferences.KEY_EDITOR_TEXT_SIZE, com.cs.ide.app.utils.AppPreferences.DEFAULT_TEXT_SIZE);
		fileContent.applyTextSize(textSize);

		boolean showLineNumbers = prefs.getBoolean(com.cs.ide.app.utils.AppPreferences.KEY_SHOW_LINE_NUMBERS, true);
		fileContent.setEnableLineNumber(showLineNumbers);

		boolean autoIndentation = prefs.getBoolean(com.cs.ide.app.utils.AppPreferences.KEY_AUTO_INDENTATION, true);
		fileContent.setEnableAutoIndentation(autoIndentation);

		boolean wordWrap = prefs.getBoolean(com.cs.ide.app.utils.AppPreferences.KEY_WORD_WRAP, false);
		fileContent.setHorizontallyScrolling(!wordWrap);

		boolean syntaxHighlighting = prefs.getBoolean(com.cs.ide.app.utils.AppPreferences.KEY_SYNTAX_HIGHLIGHTING, true);
		fileContent.resetSyntaxPatternList();
		if (syntaxHighlighting) {
			addSyntaxPatterns();
		}
		fileContent.reHighlightSyntax();
	}

	@Override
	public void onStart() {
		super.onStart();
		requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		requireContext().getSharedPreferences(com.cs.ide.app.utils.AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		applyPreferences();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (isAdded() && getActivity() != null) {
			getActivity().runOnUiThread(this::applyPreferences);
		}
	}

	/**
	 * Adds regex patterns for syntax highlighting to the editor.
	 */
	private void addSyntaxPatterns() {
		int typeColor = ContextCompat.getColor(requireContext(), R.color.syntax_type);
		int keywordColor = ContextCompat.getColor(requireContext(), R.color.syntax_keyword);
		int stringColor = ContextCompat.getColor(requireContext(), R.color.syntax_string);
		int preprocessorColor = ContextCompat.getColor(requireContext(), R.color.syntax_preprocessor);
		int numberColor = ContextCompat.getColor(requireContext(), R.color.syntax_number);
		int commentColor = ContextCompat.getColor(requireContext(), R.color.syntax_comment);

		// Standard types
		fileContent.addSyntaxPattern(Pattern.compile("\\b(int|float|double|char|void|long|short|unsigned|signed|bool|struct|union|enum|typedef|class|auto|val|var|fun|public|private|protected|static|final|volatile|transient|native|synchronized|abstract|interface|extends|implements)\\b"), typeColor);
		// Keywords
		fileContent.addSyntaxPattern(Pattern.compile("\\b(for|while|do|if|else|return|break|continue|switch|case|default|goto|sizeof|try|catch|finally|throw|throws|package|import|new|this|super|instanceof|assert|enum|in|is|when|as)\\b"), keywordColor);
		// Strings
		fileContent.addSyntaxPattern(Pattern.compile("\".*?\"|'.*?'"), stringColor);
		// Preprocessor directives
		fileContent.addSyntaxPattern(Pattern.compile("#[a-zA-Z]+\\b"), preprocessorColor);
		// Numbers
		fileContent.addSyntaxPattern(Pattern.compile("\\b\\d+\\b"), numberColor);
		// Comments (single-line and multi-line)
		fileContent.addSyntaxPattern(Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"), commentColor);
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
		int start = fileContent.getSelectionStart();
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
				fileContent.getText().insert(start, "    ");
				break;
			case "UNDO":
				fileContent.onTextContextMenuItem(android.R.id.undo);
				break;
			case "REDO":
				fileContent.onTextContextMenuItem(android.R.id.redo);
				break;
			case "{}":
				fileContent.getText().insert(start, "{}");
				fileContent.setSelection(start + 1);
				break;
			case "[]":
				fileContent.getText().insert(start, "[]");
				fileContent.setSelection(start + 1);
				break;
			case "()":
				fileContent.getText().insert(start, "()");
				fileContent.setSelection(start + 1);
				break;
			case "\"\"":
				fileContent.getText().insert(start, "\"\"");
				fileContent.setSelection(start + 1);
				break;
			case "''":
				fileContent.getText().insert(start, "''");
				fileContent.setSelection(start + 1);
				break;
			default:
				fileContent.getText().insert(start, key);
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
		return fileContent.getText().toString().getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Asynchronously loads the file content from the given URI.
	 */
	private void loadFileContent() {
		if (fileUri == null || fileUri.equals(ViewPagerAdapter.UNTITLED_FILE_URI)) return;
		new Thread(() -> {
			try (InputStream is = requireContext().getContentResolver().openInputStream(fileUri)) {
				if (is == null) return;
				byte[] buffer = new byte[is.available()];
				is.read(buffer);
				String content = new String(buffer, StandardCharsets.UTF_8);
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
}
