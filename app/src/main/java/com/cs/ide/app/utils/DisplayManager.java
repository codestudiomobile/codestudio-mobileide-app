package com.cs.ide.app.utils;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import org.jetbrains.annotations.Contract;

/**
 * DisplayManager provides utility methods for UI layout and display adjustments,
 * particularly for handling system bars (status bar, navigation bar) and display cutouts.
 */
public class DisplayManager {
    /** Additional margin in DP to apply on top of system insets to prevent UI elements from being too close to the edges. */
    private static final float ADDITIONAL_MARGIN_DP = 1;

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
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return windowInsets;
        }
        
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutParams;
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
}
