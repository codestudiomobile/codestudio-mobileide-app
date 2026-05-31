package com.csmide.termux.view;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Scroller;

import androidx.annotation.RequiresApi;

import com.csmide.R;
import com.csmide.termux.terminal.KeyHandler;
import com.csmide.termux.terminal.TerminalEmulator;
import com.csmide.termux.terminal.TerminalSession;

/**
 * View displaying and interacting with a {@link TerminalSession}.
 */
public final class TerminalView extends View {

	public static final int TERMINAL_CURSOR_BLINK_RATE_MIN = 100;
	public static final int TERMINAL_CURSOR_BLINK_RATE_MAX = 2000;
	/**
	 * The {@link KeyEvent} is generated from a virtual keyboard, like manually with
	 * the {@link KeyEvent#KeyEvent(int, int)} constructor.
	 */
	public final static int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD; // -1
	/**
	 * The {@link KeyEvent} is generated from a non-physical device, like if 0 value
	 * is returned by {@link KeyEvent#getDeviceId()}.
	 */
	public final static int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;
	private static final String LOG_TAG = "TerminalView";
	private static final int SELECTION_HANDLE_NONE = 0;
	private static final int SELECTION_HANDLE_LEFT = 1;
	private static final int SELECTION_HANDLE_RIGHT = 2;
	/**
	 * Log terminal view key and IME events.
	 */
	private static boolean TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;
	final GestureAndScaleRecognizer mGestureRecognizer;
	final Scroller mScroller;
	private final boolean mAccessibilityEnabled;
	/**
	 * The currently displayed terminal session, whose emulator is
	 * {@link #mEmulator}.
	 */
	public TerminalSession mTermSession;
	/**
	 * Our terminal emulator whose session is {@link #mTermSession}.
	 */
	public TerminalEmulator mEmulator;
	public TerminalRenderer mRenderer;
	public TerminalViewClient mClient;
	/**
	 * The top row of text to display. Ranges from -activeTranscriptRows to 0.
	 */
	int mTopRow;
	float mScaleFactor = 1.f;
	boolean mCopyMode;
	int mSelX1 = -1, mSelY1 = -1, mSelX2 = -1, mSelY2 = -1;
	/**
	 * What was left in from scrolling movement.
	 */
	float mScrollRemainder;
	/**
	 * If non-zero, this is the last unicode code point received if that was a
	 * combining character.
	 */
	int mCombiningAccent;
	private Drawable mHandleLeft;
	private Drawable mHandleRight;
	private ActionMode mActionMode;
	private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback2() {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			menu.add(Menu.NONE, 1, Menu.NONE, R.string.copy_text);
			menu.add(Menu.NONE, 2, Menu.NONE, R.string.paste_text);
			if (mClient != null && mClient.shouldShowMoreInActionMode()) {
				menu.add(Menu.NONE, 3, Menu.NONE, R.string.text_selection_more);
			}
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case 1:
					String text = getSelectedText();
					if (text != null) {
						mClient.onCopyTextToClipboard(text);
					}
					setCopyMode(false);
					return true;
				case 2:
					mClient.onPasteTextFromClipboard();
					setCopyMode(false);
					return true;
				case 3:
					showContextMenu();
					return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
			setCopyMode(false);
		}

		@Override
		public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
			int x1p = getPointX(mSelX1);
			int y1p = getPointY(mSelY1);
			int x2p = getPointX(mSelX2);
			int y2p = getPointY(mSelY2 + 1);

			if (y1p > y2p || (y1p == y2p && x1p > x2p)) {
				int tmp = x1p;
				x1p = x2p;
				x2p = tmp;
				tmp = y1p;
				y1p = y2p;
				y2p = tmp;
			}

			// Adjust x2 to the right edge of the selection
			x2p += Math.round(mRenderer.mFontWidth);

			outRect.set(x1p, y1p, x2p, y2p);
		}
	};
	private int mDraggingHandle = SELECTION_HANDLE_NONE;
	private Handler mTerminalCursorBlinkerHandler;
	private TerminalCursorBlinkerRunnable mTerminalCursorBlinkerRunnable;
	private int mTerminalCursorBlinkerRate;
	private boolean mCursorInvisibleIgnoreOnce;

	private final Handler mAutoScrollHandler = new Handler();
	private final Runnable mAutoScrollRunnable = new Runnable() {
		@Override
		public void run() {
			if (mDraggingHandle != SELECTION_HANDLE_NONE) {
				int scrollAmount = 0;
				float density = getContext().getResources().getDisplayMetrics().density;
				int scrollEdge = Math.round(40 * density);
				if (mLastMoveY < scrollEdge) {
					scrollAmount = -1;
				} else if (mLastMoveY > getHeight() - scrollEdge) {
					scrollAmount = 1;
				}

				if (scrollAmount != 0) {
					scrollDuringSelection(scrollAmount);
					updateSelectionOnScroll(scrollAmount);
					mAutoScrollHandler.postDelayed(this, 50);
				}
			}
		}
	};
	private float mLastMoveY;
	/**
	 * Keep track of where mouse touch event started which we report as mouse
	 * scroll.
	 */
	private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;
	/**
	 * Keep track of the time when a touch event leading to sending mouse scroll
	 * events started.
	 */
	private long mMouseStartDownTime = -1;
	/**
	 * The current AutoFill type returned for {@link View#getAutofillType()} by
	 * {@link #getAutofillType()}.
	 * <p>
	 * The default is {@link #AUTOFILL_TYPE_NONE} so that AutoFill UI, like toolbar
	 * above keyboard
	 * is not shown automatically, like on Activity starts/View create. This value
	 * should be updated
	 * to required value, like {@link #AUTOFILL_TYPE_TEXT} before calling
	 * {@link AutofillManager#requestAutofill(View)} so that AutoFill UI shows. The
	 * updated value
	 * set will automatically be restored to {@link #AUTOFILL_TYPE_NONE} in
	 * {@link #autofill(AutofillValue)} so that AutoFill UI isn't shown anymore by
	 * calling
	 * {@link #resetAutoFill()}.
	 */
	@RequiresApi(api = Build.VERSION_CODES.O)
	private int mAutoFillType = AUTOFILL_TYPE_NONE;
	/**
	 * The current AutoFill type returned for {@link View#getImportantForAutofill()}
	 * by
	 * {@link #getImportantForAutofill()}.
	 * <p>
	 * The default is {@link #IMPORTANT_FOR_AUTOFILL_NO} so that view is not
	 * considered important
	 * for AutoFill. This value should be updated to required value, like
	 * {@link #IMPORTANT_FOR_AUTOFILL_YES} before calling
	 * {@link AutofillManager#requestAutofill(View)}
	 * so that Android and apps consider the view as important for AutoFill to
	 * process the request.
	 * The updated value set will automatically be restored to
	 * {@link #IMPORTANT_FOR_AUTOFILL_NO} in
	 * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
	 */
	@RequiresApi(api = Build.VERSION_CODES.O)
	private int mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
	/**
	 * The current AutoFill hints returned for {@link View#getAutofillHints()} ()}
	 * by {@link #getAutofillHints()} ()}.
	 * <p>
	 * The default is an empty `string[]`. This value should be updated to required
	 * value. The
	 * updated value set will automatically be restored an empty `string[]` in
	 * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
	 */
	private String[] mAutoFillHints = new String[0];

	public TerminalView(Context context) {
		this(context, null);
	}

	public TerminalView(Context context, AttributeSet attributes) {
		this(context, attributes, 0);
	}

	public TerminalView(Context context, AttributeSet attributes, int defStyleAttr) {
		super(context, attributes, defStyleAttr);
		TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textSelectHandleLeft, android.R.attr.textSelectHandleRight});
		mHandleLeft = a.getDrawable(0);
		mHandleRight = a.getDrawable(1);
		a.recycle();

		if (mHandleLeft == null) {
			mHandleLeft = context.getDrawable(R.drawable.ic_text_select_handle_left_mtrl_alpha);
		}
		if (mHandleRight == null) {
			mHandleRight = context.getDrawable(R.drawable.ic_text_select_handle_right_mtrl_alpha);
		}

		// Tint handles to blue to match Termux selection style
		int handleColor = 0xFF1A73E8;
		if (mHandleLeft != null) mHandleLeft.setTint(handleColor);
		if (mHandleRight != null) mHandleRight.setTint(handleColor);
		mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {

			boolean scrolledWithFinger;

			@Override
			public boolean onUp(MotionEvent event) {
				mScrollRemainder = 0.0f;
				if (mEmulator != null && mEmulator.isMouseTrackingActive()
						&& !event.isFromSource(InputDevice.SOURCE_MOUSE) && !scrolledWithFinger) {
					// Quick event processing when mouse tracking is active - do not wait for check
					// of double tapping
					// for zooming.
					sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
					sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
					return true;
				}
				scrolledWithFinger = false;
				return false;
			}

			@Override
			public boolean onSingleTapUp(MotionEvent event) {
				if (mEmulator == null)
					return true;

				if (mCopyMode) {
					setCopyMode(false);
					return true;
				}

				requestFocus();
				InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
				if (imm != null) {
					imm.showSoftInput(TerminalView.this, InputMethodManager.SHOW_IMPLICIT);
				}

				if (mClient != null) {
					mClient.onSingleTapUp(event);
				}
				return true;
			}

			@Override
			public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
				if (mEmulator == null)
					return true;
				if (mEmulator.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
					// If moving with mouse pointer while pressing button, report that instead of
					// scroll.
					// This means that we never report moving with button press-events for touch
					// input,
					// since we cannot just start sending these events without a starting press
					// event,
					// which we do not do for touch input, only mouse in onTouchEvent().
					sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
				} else {
					scrolledWithFinger = true;
					float newDistanceY = distanceY + mScrollRemainder;
					int deltaRows = (int) (newDistanceY / mRenderer.mFontLineSpacing);
					mScrollRemainder = newDistanceY - deltaRows * mRenderer.mFontLineSpacing;
					doScroll(e, deltaRows);
				}
				return true;
			}

			@Override
			public boolean onScale(float focusX, float focusY, float scale) {
				if (mEmulator == null)
					return true;
				mScaleFactor *= scale;
				if (mClient != null) {
					mScaleFactor = mClient.onScale(mScaleFactor);
				}
				return true;
			}

			@Override
			public boolean onFling(final MotionEvent e2, float velocityX, float velocityY) {
				if (mEmulator == null)
					return true;
				// Do not start scrolling until last fling has been taken care of:
				if (!mScroller.isFinished())
					return true;

				final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
				float SCALE = 0.25f;
				if (mouseTrackingAtStartOfFling) {
					mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.mRows / 2,
							mEmulator.mRows / 2);
				} else {
					mScroller.fling(0, mTopRow, 0, -(int) (velocityY * SCALE), 0, 0,
							-mEmulator.getScreen().getActiveTranscriptRows(), 0);
				}

				post(new Runnable() {
					private int mLastY = 0;

					@Override
					public void run() {
						if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
							mScroller.abortAnimation();
							return;
						}
						if (mScroller.isFinished())
							return;
						boolean more = mScroller.computeScrollOffset();
						int newY = mScroller.getCurrY();
						int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
						doScroll(e2, diff);
						mLastY = newY;
						if (more)
							post(this);
					}
				});

				return true;
			}

			@Override
			public boolean onDown(float x, float y) {
				return false;
			}

			@Override
			public boolean onDoubleTap(MotionEvent event) {
				return false;
			}

			@Override
			public void onLongPress(MotionEvent event) {
				if (mGestureRecognizer.isInProgress())
					return;
				if (mClient != null && mClient.onLongPress(event))
					return;
				if (!mCopyMode) {
					int[] columnAndRow = getColumnAndRow(event, true);
					int[] boundaries = mEmulator.getScreen().getWordBoundariesAt(columnAndRow[0], columnAndRow[1]);
					mSelX1 = boundaries[0];
					mSelY1 = boundaries[1];
					mSelX2 = boundaries[2];
					mSelY2 = boundaries[3];
					setCopyMode(true);
				}
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			}
		});
		mScroller = new Scroller(context);
		setFocusable(true);
		setFocusableInTouchMode(true);
		AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
		mAccessibilityEnabled = am.isEnabled();

		float dipInPixels = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 1,
				getResources().getDisplayMetrics());
		int defaultFontSize = Math.round(14 * dipInPixels);
		mRenderer = new TerminalRenderer(defaultFontSize, Typeface.MONOSPACE);
	}

	public void setTerminalViewClient(TerminalViewClient client) {
		this.mClient = client;
	}

	public void setIsTerminalViewKeyLoggingEnabled(boolean value) {
		TERMINAL_VIEW_KEY_LOGGING_ENABLED = value;
	}

	public boolean attachSession(TerminalSession session) {
		if (session == mTermSession)
			return false;
		mTopRow = 0;

		mTermSession = session;
		mEmulator = null;
		mCombiningAccent = 0;

		updateSize();

		setVerticalScrollBarEnabled(true);

		return true;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		if (mClient == null) {
			return null;
		}

		if (mClient.isTerminalViewSelected()) {
			if (mClient.shouldEnforceCharBasedInput()) {
				outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
						| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			} else {
				outAttrs.inputType = InputType.TYPE_NULL;
			}
		} else {
			outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
		}

		outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;

		return new BaseInputConnection(this, true) {

			@Override
			public boolean finishComposingText() {
				if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
					mClient.logInfo(LOG_TAG, "IME: finishComposingText()");
				super.finishComposingText();

				sendTextToTerminal(getEditable());
				getEditable().clear();
				return true;
			}

			@Override
			public boolean commitText(CharSequence text, int newCursorPosition) {
				if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
					mClient.logInfo(LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
				}
				super.commitText(text, newCursorPosition);

				if (mEmulator == null)
					return true;

				Editable content = getEditable();
				sendTextToTerminal(content);
				content.clear();
				return true;
			}

			@Override
			public boolean deleteSurroundingText(int leftLength, int rightLength) {
				if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
					mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
				}
				KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
				for (int i = 0; i < leftLength; i++)
					sendKeyEvent(deleteKey);
				return super.deleteSurroundingText(leftLength, rightLength);
			}

			void sendTextToTerminal(CharSequence text) {
				final int textLengthInChars = text.length();
				for (int i = 0; i < textLengthInChars; i++) {
					char firstChar = text.charAt(i);
					int codePoint;
					if (Character.isHighSurrogate(firstChar)) {
						if (++i < textLengthInChars) {
							codePoint = Character.toCodePoint(firstChar, text.charAt(i));
						} else {
							codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
						}
					} else {
						codePoint = firstChar;
					}

					if (mClient.readShiftKey())
						codePoint = Character.toUpperCase(codePoint);

					boolean ctrlHeld = false;
					if (codePoint <= 31 && codePoint != 27) {
						if (codePoint == '\n') {
							codePoint = '\r';
						}

						ctrlHeld = true;
						switch (codePoint) {
							case 31:
								codePoint = '_';
								break;
							case 30:
								codePoint = '^';
								break;
							case 29:
								codePoint = ']';
								break;
							case 28:
								codePoint = '\\';
								break;
							default:
								codePoint += 96;
								break;
						}
					}

					inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
				}
			}

		};
	}

	@Override
	protected int computeVerticalScrollRange() {
		return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
	}

	@Override
	protected int computeVerticalScrollExtent() {
		return mEmulator == null ? 1 : mEmulator.mRows;
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
	}

	public void onScreenUpdated() {
		onScreenUpdated(false);
	}

	public void onScreenUpdated(boolean skipScrolling) {
		if (mEmulator == null)
			return;

		int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
		if (mTopRow < -rowsInHistory)
			mTopRow = -rowsInHistory;

		if (mEmulator.isAutoScrollDisabled()) {
			int rowShift = mEmulator.getScrollCounter();
			if (-mTopRow + rowShift > rowsInHistory) {
				if (mEmulator.isAutoScrollDisabled()) {
					mTopRow = -rowsInHistory;
					skipScrolling = true;
				}
			} else {
				skipScrolling = true;
				mTopRow -= rowShift;
			}
		}

		if (!skipScrolling && mTopRow != 0) {
			if (mTopRow < -3) {
				awakenScrollBars();
			}
			mTopRow = 0;
		}

		mEmulator.clearScrollCounter();

		invalidate();
		if (mAccessibilityEnabled)
			setContentDescription(getText());
	}

	public void onContextMenuClosed(Menu menu) {
	}

	public void setTextSize(int textSize) {
		mRenderer = new TerminalRenderer(textSize, mRenderer == null ? Typeface.MONOSPACE : mRenderer.mTypeface);
		updateSize();
	}

	public void setTypeface(Typeface newTypeface) {
		mRenderer = new TerminalRenderer(mRenderer.mTextSize, newTypeface);
		updateSize();
		invalidate();
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return true;
	}

	@Override
	public boolean isOpaque() {
		return true;
	}

	public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
		int column = (int) (event.getX() / mRenderer.mFontWidth);
		int row = (int) ((event.getY() - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
		if (relativeToScroll) {
			row += mTopRow;
		}
		return new int[]{column, row};
	}

	void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
		int[] columnAndRow = getColumnAndRow(e, false);
		int x = columnAndRow[0] + 1;
		int y = columnAndRow[1] + 1;
		if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
				|| button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
			if (mMouseStartDownTime == e.getDownTime()) {
				x = mMouseScrollStartX;
				y = mMouseScrollStartY;
			} else {
				mMouseStartDownTime = e.getDownTime();
				mMouseScrollStartX = x;
				mMouseScrollStartY = y;
			}
		}
		mEmulator.sendMouseEvent(button, x, y, pressed);
	}

	void doScroll(MotionEvent event, int rowsDown) {
		boolean up = rowsDown < 0;
		int amount = Math.abs(rowsDown);
		for (int i = 0; i < amount; i++) {
			if (mEmulator.isMouseTrackingActive()) {
				sendMouseEventCode(event,
						up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
			} else if (mEmulator.isAlternateBufferActive()) {
				handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
			} else {
				mTopRow = Math.min(0,
						Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
				if (!awakenScrollBars())
					invalidate();
			}
		}
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE)
				&& event.getAction() == MotionEvent.ACTION_SCROLL) {
			boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
			doScroll(event, up ? -3 : 3);
			return true;
		}
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	@RequiresApi(23)
	public boolean onTouchEvent(MotionEvent event) {
		if (mEmulator == null)
			return true;
		final int action = event.getAction();

		if (mCopyMode) {
			int x = (int) event.getX();
			int y = (int) event.getY();

			if (action == MotionEvent.ACTION_DOWN) {
				// Normalize selection so (X1, Y1) is always start and (X2, Y2) is end
				if (mSelY1 > mSelY2 || (mSelY1 == mSelY2 && mSelX1 > mSelX2)) {
					int tmpX = mSelX1;
					mSelX1 = mSelX2;
					mSelX2 = tmpX;
					int tmpY = mSelY1;
					mSelY1 = mSelY2;
					mSelY2 = tmpY;
				}

				int hx1 = getPointX(mSelX1);
				int hy1 = getPointY(mSelY1 + 1);
				int hx2 = getPointX(mSelX2) + Math.round(mRenderer.mFontWidth);
				int hy2 = getPointY(mSelY2 + 1);

				float density = getContext().getResources().getDisplayMetrics().density;
				int handleHeight = Math.round(22 * density);
				int handleWidth = handleHeight * 2;

				Rect leftHandleRect = new Rect(hx1 - handleWidth * 3 / 4, hy1, hx1 + handleWidth / 4, hy1 + handleHeight);
				Rect rightHandleRect = new Rect(hx2 - handleWidth / 4, hy2, hx2 + handleWidth * 3 / 4, hy2 + handleHeight);

				// Add slop for easier grabbing
				leftHandleRect.inset(-40, -40);
				rightHandleRect.inset(-40, -40);

				if (leftHandleRect.contains(x, y)) {
					mDraggingHandle = SELECTION_HANDLE_LEFT;
				} else if (rightHandleRect.contains(x, y)) {
					mDraggingHandle = SELECTION_HANDLE_RIGHT;
				} else {
					mDraggingHandle = SELECTION_HANDLE_NONE;
					setCopyMode(false);
				}
				} else if (action == MotionEvent.ACTION_MOVE && mDraggingHandle != SELECTION_HANDLE_NONE) {
					mLastMoveY = event.getY();
					int[] columnAndRow = getColumnAndRow(event, true);
					int newX = columnAndRow[0];
					int newY = columnAndRow[1];

					if (mDraggingHandle == SELECTION_HANDLE_LEFT) {
						// Ensure we don't move start past end
						if (newY < mSelY2 || (newY == mSelY2 && newX <= mSelX2)) {
							mSelX1 = newX;
							mSelY1 = newY;
						} else {
							// Snap to end or swap? Standard behavior is to swap.
							mSelX1 = mSelX2;
							mSelY1 = mSelY2;
							mSelX2 = newX;
							mSelY2 = newY;
							mDraggingHandle = SELECTION_HANDLE_RIGHT;
						}
					} else if (mDraggingHandle == SELECTION_HANDLE_RIGHT) {
						// Ensure we don't move end before start
						if (newY > mSelY1 || (newY == mSelY1 && newX >= mSelX1)) {
							mSelX2 = newX;
							mSelY2 = newY;
						} else {
							mSelX2 = mSelX1;
							mSelY2 = mSelY1;
							mSelX1 = newX;
							mSelY1 = newY;
							mDraggingHandle = SELECTION_HANDLE_LEFT;
						}
					}

					// Trigger auto-scroll if near edges
					float density = getContext().getResources().getDisplayMetrics().density;
					int scrollEdge = Math.round(40 * density);
					if (mLastMoveY < scrollEdge || mLastMoveY > getHeight() - scrollEdge) {
						mAutoScrollHandler.removeCallbacks(mAutoScrollRunnable);
						mAutoScrollHandler.post(mAutoScrollRunnable);
					} else {
						mAutoScrollHandler.removeCallbacks(mAutoScrollRunnable);
					}
				}

				if (mCopyMode) {
					invalidate();
					if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
						mDraggingHandle = SELECTION_HANDLE_NONE;
						mAutoScrollHandler.removeCallbacks(mAutoScrollRunnable);
						updateFloatingToolbarVisibility(event);
					}
					return true;
				}
		}

		if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
			if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
				if (action == MotionEvent.ACTION_DOWN)
					showContextMenu();
				return true;
			} else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
				ClipboardManager clipboardManager = (ClipboardManager) getContext()
						.getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clipData = clipboardManager.getPrimaryClip();
				if (clipData != null) {
					ClipData.Item clipItem = clipData.getItemAt(0);
					if (clipItem != null) {
						CharSequence text = clipItem.coerceToText(getContext());
						if (!TextUtils.isEmpty(text))
							mEmulator.paste(text.toString());
					}
				}
			} else if (mEmulator.isMouseTrackingActive()) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_UP:
						sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON,
								event.getAction() == MotionEvent.ACTION_DOWN);
						break;
					case MotionEvent.ACTION_MOVE:
						sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
						break;
				}
			}
		}

		mGestureRecognizer.onTouchEvent(event);
		return true;
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (mClient == null) return super.onKeyPreIme(keyCode, event);

		if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
			mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mCopyMode) {
				setCopyMode(false);
				return true;
			}
			cancelRequestAutoFill();
			if (mClient.shouldBackButtonBeMappedToEscape()) {
				switch (event.getAction()) {
					case KeyEvent.ACTION_DOWN:
						return onKeyDown(keyCode, event);
					case KeyEvent.ACTION_UP:
						return onKeyUp(keyCode, event);
				}
			}
		} else if (mClient.shouldUseCtrlSpaceWorkaround() &&
				keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
			return onKeyDown(keyCode, event);
		}
		return super.onKeyPreIme(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mClient == null) return true;

		if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
			mClient.logInfo(LOG_TAG,
					"onKeyDown(keyCode=" + keyCode + ", isSystem()=" + event.isSystem() + ", event=" + event + ")");
		if (mEmulator == null)
			return true;

		if (mClient.onKeyDown(keyCode, event, mTermSession)) {
			invalidate();
			return true;
		} else if (event.isSystem()
				&& (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
			return super.onKeyDown(keyCode, event);
		} else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
			mTermSession.write(event.getCharacters());
			return true;
		}

		final int metaState = event.getMetaState();
		final boolean controlDown = event.isCtrlPressed() || mClient.readControlKey();
		final boolean leftAltDown = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || mClient.readAltKey();
		final boolean shiftDown = event.isShiftPressed() || mClient.readShiftKey();
		final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

		int keyMod = 0;
		if (controlDown)
			keyMod |= KeyHandler.KEYMOD_CTRL;
		if (event.isAltPressed() || leftAltDown)
			keyMod |= KeyHandler.KEYMOD_ALT;
		if (shiftDown)
			keyMod |= KeyHandler.KEYMOD_SHIFT;
		if (event.isNumLockOn())
			keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
		if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
			if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
				mClient.logInfo(LOG_TAG, "handleKeyCode() took key event");
			return true;
		}

		int bitsToClear = KeyEvent.META_CTRL_MASK;
		if (rightAltDownFromEvent) {
		} else {
			bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
		}
		int effectiveMetaState = event.getMetaState() & ~bitsToClear;

		if (shiftDown)
			effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
		if (mClient.readFnKey())
			effectiveMetaState |= KeyEvent.META_FUNCTION_ON;

		int result = event.getUnicodeChar(effectiveMetaState);
		if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
			mClient.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
		if (result == 0) {
			return false;
		}

		int oldCombiningAccent = mCombiningAccent;
		if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			if (mCombiningAccent != 0)
				inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
			mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
		} else {
			if (mCombiningAccent != 0) {
				int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
				if (combinedChar > 0)
					result = combinedChar;
				mCombiningAccent = 0;
			}
			inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
		}

		if (mCombiningAccent != oldCombiningAccent)
			invalidate();

		return true;
	}

	public void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent,
	                           boolean leftAltDownFromEvent) {
		if (mClient != null && TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
			mClient.logInfo(LOG_TAG,
					"inputCodePoint(eventSource=" + eventSource + ", codePoint=" + codePoint + ", controlDownFromEvent="
							+ controlDownFromEvent + ", leftAltDownFromEvent="
							+ leftAltDownFromEvent + ")");
		}

		if (mTermSession == null)
			return;

		if (mEmulator != null)
			mEmulator.setCursorBlinkState(true);

		boolean controlDown = controlDownFromEvent;
		boolean altDown = leftAltDownFromEvent;
		if (mClient != null) {
			controlDown = controlDownFromEvent || mClient.readControlKey();
			altDown = leftAltDownFromEvent || mClient.readAltKey();

			if (mClient.onCodePoint(codePoint, controlDown, mTermSession))
				return;
		}

		if (controlDown) {
			if (codePoint >= 'a' && codePoint <= 'z') {
				codePoint = codePoint - 'a' + 1;
			} else if (codePoint >= 'A' && codePoint <= 'Z') {
				codePoint = codePoint - 'A' + 1;
			} else if (codePoint == ' ' || codePoint == '2') {
				codePoint = 0;
			} else if (codePoint == '[' || codePoint == '3') {
				codePoint = 27;
			} else if (codePoint == '\\' || codePoint == '4') {
				codePoint = 28;
			} else if (codePoint == ']' || codePoint == '5') {
				codePoint = 29;
			} else if (codePoint == '^' || codePoint == '6') {
				codePoint = 30;
			} else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
				codePoint = 31;
			} else if (codePoint == '8') {
				codePoint = 127;
			}
		}

		if (codePoint > -1) {
			if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
				switch (codePoint) {
					case 0x02DC:
						codePoint = 0x007E;
						break;
					case 0x02CB:
						codePoint = 0x0060;
						break;
					case 0x02C6:
						codePoint = 0x005E;
						break;
				}
			}

			mTermSession.writeCodePoint(altDown, codePoint);
		}
	}

	public boolean handleKeyCode(int keyCode, int keyMod) {
		if (mEmulator != null)
			mEmulator.setCursorBlinkState(true);

		if (handleKeyCodeAction(keyCode, keyMod))
			return true;

		TerminalEmulator term = mTermSession.getEmulator();
		String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(),
				term.isKeypadApplicationMode());
		if (code == null)
			return false;
		mTermSession.write(code);
		return true;
	}

	public boolean handleKeyCodeAction(int keyCode, int keyMod) {
		boolean shiftDown = (keyMod & KeyHandler.KEYMOD_SHIFT) != 0;

		switch (keyCode) {
			case KeyEvent.KEYCODE_PAGE_UP:
			case KeyEvent.KEYCODE_PAGE_DOWN:
				if (shiftDown) {
					long time = SystemClock.uptimeMillis();
					MotionEvent motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
					doScroll(motionEvent, keyCode == KeyEvent.KEYCODE_PAGE_UP ? -mEmulator.mRows : mEmulator.mRows);
					motionEvent.recycle();
					return true;
				}
		}

		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (mClient != null && TERMINAL_VIEW_KEY_LOGGING_ENABLED)
			mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");

		if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK)
			return true;

		if (mClient != null && mClient.onKeyUp(keyCode, event)) {
			invalidate();
			return true;
		} else if (event.isSystem()) {
			return super.onKeyUp(keyCode, event);
		}

		return true;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		updateSize();
	}

	public void updateSize() {
		int viewWidth = getWidth();
		int viewHeight = getHeight();
		if (viewWidth == 0 || viewHeight == 0 || mTermSession == null)
			return;

		int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
		int newRows = Math.max(4, (viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);

		if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
			mTermSession.updateSize(newColumns, newRows, (int) mRenderer.getFontWidth(),
					mRenderer.getFontLineSpacing());
			mEmulator = mTermSession.getEmulator();
			if (mClient != null) {
				mClient.onEmulatorSet();
			}

			if (mTerminalCursorBlinkerRunnable != null)
				mTerminalCursorBlinkerRunnable.setEmulator(mEmulator);

			mTopRow = 0;
			scrollTo(0, 0);
			invalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mEmulator == null) {
			canvas.drawColor(0XFF000000);
		} else {
			int x1 = mSelX1, y1 = mSelY1, x2 = mSelX2, y2 = mSelY2;
			if (mCopyMode && x1 != -1 && y1 != -1 && x2 != -1 && y2 != -1) {
				if (y1 > y2 || (y1 == y2 && x1 > x2)) {
					x1 = mSelX2;
					y1 = mSelY2;
					x2 = mSelX1;
					y2 = mSelY1;
				}
				mRenderer.render(mEmulator, canvas, mTopRow, y1, y2, x1, x2);

				// Draw handles
				int hx1 = getPointX(x1);
				int hy1 = getPointY(y1 + 1);
				int hx2 = getPointX(x2) + Math.round(mRenderer.mFontWidth);
				int hy2 = getPointY(y2 + 1);

				float density = getContext().getResources().getDisplayMetrics().density;
				int handleHeight = Math.round(22 * density);
				int handleWidth = handleHeight * 2; // 2:1 aspect ratio to accommodate built-in transparent padding in the vector drawables

				// Position handles asymmetrically to align standard teardrops perfectly with touch boundaries
				mHandleLeft.setBounds(hx1 - handleWidth * 3 / 4, hy1, hx1 + handleWidth / 4, hy1 + handleHeight);
				mHandleRight.setBounds(hx2 - handleWidth / 4, hy2, hx2 + handleWidth * 3 / 4, hy2 + handleHeight);

				mHandleLeft.draw(canvas);
				mHandleRight.draw(canvas);
			} else {
				mRenderer.render(mEmulator, canvas, mTopRow, -1, -1, -1, -1);
			}
		}
	}

	public String getSelectedText() {
		if (mSelY1 == -1 || mEmulator == null)
			return null;
		int x1 = mSelX1, y1 = mSelY1, x2 = mSelX2, y2 = mSelY2;
		if (y1 > y2 || (y1 == y2 && x1 > x2)) {
			x1 = mSelX2;
			y1 = mSelY2;
			x2 = mSelX1;
			y2 = mSelY1;
		}
		return mEmulator.getScreen().getSelectedText(x1, y1, x2, y2);
	}

	public void setCopyMode(boolean copyMode) {
		if (mCopyMode != copyMode) {
			mCopyMode = copyMode;
			if (!copyMode) {
				mSelX1 = mSelY1 = mSelX2 = mSelY2 = -1;
				if (mActionMode != null) {
					mActionMode.finish();
					mActionMode = null;
				}
			}
			if (mClient != null) {
				mClient.copyModeChanged(copyMode);
			}
			invalidate();
		}
	}

	public TerminalSession getCurrentSession() {
		return mTermSession;
	}

	private CharSequence getText() {
		return mEmulator.getScreen().getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
	}

	public int getCursorX(float x) {
		return (int) (x / mRenderer.mFontWidth);
	}

	public int getCursorY(float y) {
		return (int) (((y - 40) / mRenderer.mFontLineSpacing) + mTopRow);
	}

	public int getPointX(int cx) {
		if (cx > mEmulator.mColumns) {
			cx = mEmulator.mColumns;
		}
		return Math.round(cx * mRenderer.mFontWidth);
	}

	public int getPointY(int cy) {
		return Math.round((cy - mTopRow) * mRenderer.mFontLineSpacing);
	}

	public int getTopRow() {
		return mTopRow;
	}

	public void setTopRow(int mTopRow) {
		this.mTopRow = mTopRow;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public void autofill(AutofillValue value) {
		if (value.isText()) {
			mTermSession.write(value.getTextValue().toString());
		}

		resetAutoFill();
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public int getAutofillType() {
		return mAutoFillType;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public String[] getAutofillHints() {
		return mAutoFillHints;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public AutofillValue getAutofillValue() {
		return AutofillValue.forText("");
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public int getImportantForAutofill() {
		return mAutoFillImportance;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	private synchronized void resetAutoFill() {
		mAutoFillType = AUTOFILL_TYPE_NONE;
		mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
		mAutoFillHints = new String[0];
	}

	public AutofillManager getAutoFillManagerService() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			return null;

		try {
			Context context = getContext();
			if (context == null)
				return null;
			return context.getSystemService(AutofillManager.class);
		} catch (Exception e) {
			mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e);
			return null;
		}
	}

	public boolean isAutoFillEnabled() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			return false;

		try {
			AutofillManager autofillManager = getAutoFillManagerService();
			return autofillManager != null && autofillManager.isEnabled();
		} catch (Exception e) {
			mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e);
			return false;
		}
	}

	public synchronized void requestAutoFillUsername() {
		requestAutoFill(
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_USERNAME} : null);
	}

	public synchronized void requestAutoFillPassword() {
		requestAutoFill(
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_PASSWORD} : null);
	}

	public synchronized void requestAutoFill(String[] autoFillHints) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			return;
		if (autoFillHints == null || autoFillHints.length < 1)
			return;

		try {
			AutofillManager autofillManager = getAutoFillManagerService();
			if (autofillManager != null && autofillManager.isEnabled()) {
				mAutoFillType = AUTOFILL_TYPE_TEXT;
				mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES;
				mAutoFillHints = autoFillHints;
				autofillManager.requestAutofill(this);
			}
		} catch (Exception e) {
			mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e);
		}
	}

	public synchronized void cancelRequestAutoFill() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			return;
		if (mAutoFillType == AUTOFILL_TYPE_NONE)
			return;

		try {
			AutofillManager autofillManager = getAutoFillManagerService();
			if (autofillManager != null && autofillManager.isEnabled()) {
				resetAutoFill();
				autofillManager.cancel();
			}
		} catch (Exception e) {
			mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e);
		}
	}

	public synchronized boolean setTerminalCursorBlinkerRate(int blinkRate) {
		boolean result;

		if (blinkRate != 0
				&& (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
			mClient.logError(LOG_TAG, "The cursor blink rate must be in between " + TERMINAL_CURSOR_BLINK_RATE_MIN + "-"
					+ TERMINAL_CURSOR_BLINK_RATE_MAX + ": " + blinkRate);
			mTerminalCursorBlinkerRate = 0;
			result = false;
		} else {
			mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to " + blinkRate);
			mTerminalCursorBlinkerRate = blinkRate;
			result = true;
		}

		if (mTerminalCursorBlinkerRate == 0) {
			mClient.logVerbose(LOG_TAG, "Cursor blinker disabled");
			stopTerminalCursorBlinker();
		}

		return result;
	}

	public synchronized void setTerminalCursorBlinkerState(boolean start, boolean startOnlyIfCursorEnabled) {
		stopTerminalCursorBlinker();

		if (mEmulator == null)
			return;

		mEmulator.setCursorBlinkingEnabled(false);

		if (start) {
			if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN
					|| mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
				return;
			else if (startOnlyIfCursorEnabled && !mEmulator.isCursorEnabled()) {
				if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
					mClient.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled");
				return;
			}

			if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
				mClient.logVerbose(LOG_TAG,
						"Starting cursor blinker with the blink rate " + mTerminalCursorBlinkerRate);
			if (mTerminalCursorBlinkerHandler == null)
				mTerminalCursorBlinkerHandler = new Handler(Looper.getMainLooper());
			mTerminalCursorBlinkerRunnable = new TerminalCursorBlinkerRunnable(mEmulator, mTerminalCursorBlinkerRate);
			mEmulator.setCursorBlinkingEnabled(true);
			mTerminalCursorBlinkerRunnable.run();
		}
	}

	public void stopTerminalCursorBlinker() {
		if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
			if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
				mClient.logVerbose(LOG_TAG, "Stopping cursor blinker");
			mTerminalCursorBlinkerHandler.removeCallbacks(mTerminalCursorBlinkerRunnable);
		}
	}

	private void scrollDuringSelection(int rowsDown) {
		boolean up = rowsDown < 0;
		int amount = Math.abs(rowsDown);
		for (int i = 0; i < amount; i++) {
			mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
		}
		if (!awakenScrollBars()) invalidate();
	}

	private void updateSelectionOnScroll(int scrollAmount) {
		if (mDraggingHandle == SELECTION_HANDLE_LEFT) {
			mSelY1 += scrollAmount;
		} else if (mDraggingHandle == SELECTION_HANDLE_RIGHT) {
			mSelY2 += scrollAmount;
		}
		invalidate();
	}

	public void updateFloatingToolbarVisibility(MotionEvent event) {
		if (mCopyMode) {
			if (mActionMode == null) {
				mActionMode = startActionMode(mActionModeCallback, ActionMode.TYPE_FLOATING);
			} else {
				mActionMode.invalidate();
			}
		} else {
			if (mActionMode != null) {
				mActionMode.finish();
				mActionMode = null;
			}
		}
	}

	private class TerminalCursorBlinkerRunnable implements Runnable {

		private final int mBlinkRate;
		boolean mCursorVisible = false;
		private TerminalEmulator mEmulator;

		public TerminalCursorBlinkerRunnable(TerminalEmulator emulator, int blinkRate) {
			mEmulator = emulator;
			mBlinkRate = blinkRate;
		}

		public void setEmulator(TerminalEmulator emulator) {
			mEmulator = emulator;
		}

		public void run() {
			try {
				if (mEmulator != null) {
					mCursorVisible = !mCursorVisible;
					mEmulator.setCursorBlinkState(mCursorVisible);
					invalidate();
				}
			} finally {
				mTerminalCursorBlinkerHandler.postDelayed(this, mBlinkRate);
			}
		}
	}

}
