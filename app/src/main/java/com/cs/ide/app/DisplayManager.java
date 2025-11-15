package com.cs.ide.app;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import org.jetbrains.annotations.Contract;
public class DisplayManager {
    private static final float ADDITIONAL_MARGIN_DP = 1;
    private static int dpToPx(@NonNull Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
    @NonNull
    @Contract("_, _ -> param2")
    public static WindowInsetsCompat setupDynamicMarginHandling(@NonNull View contentView, @NonNull WindowInsetsCompat windowInsets) {
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
        if (params.leftMargin != requiredMarginLeft || params.topMargin != requiredMarginTop || params.rightMargin != requiredMarginRight || params.bottomMargin != requiredMarginBottom) {
            params.leftMargin = requiredMarginLeft;
            params.topMargin = requiredMarginTop;
            params.rightMargin = requiredMarginRight;
            params.bottomMargin = requiredMarginBottom;
            contentView.setLayoutParams(params);
        }
        return windowInsets;
    }
}
