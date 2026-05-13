package com.cs.ide.app.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cs.ide.app.utils.AppPreferences;

public class ZoomLayout extends FrameLayout {
    private float mScaleFactor = 1.0f;
    private ScaleGestureDetector mScaleDetector;

    public ZoomLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                boolean pinchToZoom = getContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.KEY_PINCH_TO_ZOOM, true);
                
                if (pinchToZoom) {
                    mScaleFactor *= detector.getScaleFactor();
                    mScaleFactor = Math.max(0.5f, Math.min(mScaleFactor, 3.0f));
                    setScaleX(mScaleFactor);
                    setScaleY(mScaleFactor);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        if (mScaleDetector.isInProgress()) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
