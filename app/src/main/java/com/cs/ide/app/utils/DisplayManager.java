package com.cs.ide.app.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.cs.ide.R;

import org.jetbrains.annotations.Contract;

import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * DisplayManager provides utility methods for UI layout and display adjustments,
 * particularly for handling system bars (status bar, navigation bar) and display cutouts.
 */
public class DisplayManager {
	/**
	 * Additional margin in DP to apply on top of system insets to prevent UI elements from being too close to the edges. Set to 0 to remove visible borders.
	 */
	private static final float ADDITIONAL_MARGIN_DP = 0;

	/**
	 * Converts density-independent pixels (DP) to actual pixels (PX).
	 *
	 * @param context The context for retrieving display metrics.
	 * @param dp      The value in DP.
	 * @return The equivalent value in pixels.
	 */
	private static int dpToPx(@NonNull Context context, float dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
	}

	/**
	 * Configures dynamic margin handling for a view based on system window insets.
	 * This ensures that the content view is not obscured by system bars or display cutouts (notches).
	 * Typically used as a listener for ViewCompat.setOnApplyWindowInsetsListener.
	 *
	 * @param contentView  The view to which margins should be applied.
	 * @param windowInsets The window insets provided by the system.
	 * @return The same window insets to allow further processing by other listeners.
	 */
	@NonNull
	@Contract("_, _ -> param2")
	public static WindowInsetsCompat setupDynamicMarginHandling(@NonNull View contentView, @NonNull WindowInsetsCompat windowInsets) {
		// Define which types of insets we care about (system bars and cutouts)
		int insetTypes = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
		Insets systemAndCutoutInsets = windowInsets.getInsets(insetTypes);

		ViewGroup.LayoutParams layoutParams = contentView.getLayoutParams();
		if (!(layoutParams instanceof ViewGroup.MarginLayoutParams params)) {
			return windowInsets;
		}

		int additionalMarginPx = dpToPx(contentView.getContext(), ADDITIONAL_MARGIN_DP);

		final int significanceThreshold = 0;
		int requiredMarginLeft = systemAndCutoutInsets.left;
		int requiredMarginTop = systemAndCutoutInsets.top;
		int requiredMarginRight = systemAndCutoutInsets.right;
		int requiredMarginBottom = systemAndCutoutInsets.bottom;

		// Add a small extra padding if the inset is non-zero
		if (requiredMarginLeft > significanceThreshold) {
			requiredMarginLeft += additionalMarginPx;
		}
		if (requiredMarginTop > significanceThreshold) {
			requiredMarginTop += additionalMarginPx;
		}
		if (requiredMarginRight > significanceThreshold) {
			requiredMarginRight += additionalMarginPx;
		}
		if (requiredMarginBottom > significanceThreshold) {
			requiredMarginBottom += additionalMarginPx;
		}

		// Update layout parameters only if they have changed
		if (params.leftMargin != requiredMarginLeft || params.topMargin != requiredMarginTop ||
				params.rightMargin != requiredMarginRight || params.bottomMargin != requiredMarginBottom) {
			params.leftMargin = requiredMarginLeft;
			params.topMargin = requiredMarginTop;
			params.rightMargin = requiredMarginRight;
			params.bottomMargin = requiredMarginBottom;
			contentView.setLayoutParams(params);
		}

		return windowInsets;
	}

	/**
	 * Applies the standard Code Studio editor theme to the given CodeEditor.
	 *
	 * @param context The context for retrieving resources.
	 * @param editor  The editor instance to theme.
	 */
	public static void applyIdeEditorTheme(@NonNull Context context, @NonNull CodeEditor editor) {
		editor.subscribeAlways(ColorSchemeUpdateEvent.class, (event) -> {
			EditorColorScheme scheme = event.getColorScheme();
			scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, ContextCompat.getColor(context, R.color.ide_background));
			scheme.setColor(EditorColorScheme.TEXT_NORMAL, ContextCompat.getColor(context, R.color.ide_text_primary));
			scheme.setColor(EditorColorScheme.TEXT_SELECTED, ContextCompat.getColor(context, R.color.ide_text_selected));
			scheme.setColor(EditorColorScheme.LINE_NUMBER, ContextCompat.getColor(context, R.color.ide_line_number));
			scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, ContextCompat.getColor(context, R.color.ide_background));
			scheme.setColor(EditorColorScheme.CURRENT_LINE, ContextCompat.getColor(context, R.color.ide_current_line));
			scheme.setColor(EditorColorScheme.SELECTION_INSERT, Color.WHITE);
			scheme.setColor(EditorColorScheme.SELECTION_HANDLE, Color.WHITE);
			scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color.parseColor("#40BDBDBD"));

			// Syntax highlighting colors
			scheme.setColor(EditorColorScheme.KEYWORD, ContextCompat.getColor(context, R.color.syntax_keyword));
			scheme.setColor(EditorColorScheme.LITERAL, ContextCompat.getColor(context, R.color.syntax_string));
			scheme.setColor(EditorColorScheme.COMMENT, ContextCompat.getColor(context, R.color.syntax_comment));
			scheme.setColor(EditorColorScheme.OPERATOR, ContextCompat.getColor(context, R.color.syntax_keyword));
			scheme.setColor(EditorColorScheme.ANNOTATION, ContextCompat.getColor(context, R.color.syntax_type));
			scheme.setColor(EditorColorScheme.FUNCTION_NAME, ContextCompat.getColor(context, R.color.syntax_function));
			scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, ContextCompat.getColor(context, R.color.syntax_function));
		});
	}
}
