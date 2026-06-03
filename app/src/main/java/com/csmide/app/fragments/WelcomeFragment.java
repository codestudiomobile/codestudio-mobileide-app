package com.csmide.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.csmide.R;
import com.csmide.app.activities.EditorActivity;
import com.csmide.app.activities.MainActivity;
import com.csmide.app.activities.ManageLanguagesActivity;
import com.csmide.app.utils.AppPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * WelcomeFragment is the landing page shown to the user when no files are currently open.
 * It provides a quick-access dashboard for common IDE tasks.
 */
public class WelcomeFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

	private final List<TextView> scalableTextViews = new ArrayList<>();
	private ScaleGestureDetector scaleGestureDetector;
	private float scaleFactor = 1.0f;
	private int baseTextSizeSp;

	/**
	 * Static factory method to create a new instance of WelcomeFragment.
	 */
	public static WelcomeFragment newInstance() {
		return new WelcomeFragment();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_welcome_code_studio, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// yet to complete: hide manage languages
		View manageLanguages = view.findViewById(R.id.manageLanguagesSettings);
		if (manageLanguages != null) {
			manageLanguages.setVisibility(View.GONE);
		}

		initScalableViews(view);
		setupClickListeners(view);
		setupZoomGesture(view);
		applyPreferences();
	}

	@Override
	public void onStart() {
		super.onStart();
		requireContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		requireContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	private void initScalableViews(View view) {
		scalableTextViews.clear();
		scalableTextViews.add(view.findViewById(R.id.openFolderText));
		scalableTextViews.add(view.findViewById(R.id.openFilesText));
		scalableTextViews.add(view.findViewById(R.id.openFileFromInternalStorageText));
		scalableTextViews.add(view.findViewById(R.id.openNewTerminal));
		scalableTextViews.add(view.findViewById(R.id.openEditorSettings));
		scalableTextViews.add(view.findViewById(R.id.manageLanguagesSettings));
		scalableTextViews.add(view.findViewById(R.id.openSettings));
	}

	private void setupZoomGesture(View view) {
		scaleGestureDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				boolean pinchToZoom = requireContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE)
						.getBoolean(AppPreferences.KEY_PINCH_TO_ZOOM, true);

				if (pinchToZoom) {
					scaleFactor *= detector.getScaleFactor();
					scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
					updateTextSize();
					return true;
				}
				return false;
			}
		});

		view.findViewById(R.id.welcomeRoot).setOnTouchListener((v, event) -> {
			scaleGestureDetector.onTouchEvent(event);
			return true;
		});
	}

	private void updateTextSize() {
		for (TextView tv : scalableTextViews) {
			if (tv != null) {
				tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (baseTextSizeSp + 4) * scaleFactor);
			}
		}
	}

	private void applyPreferences() {
		if (!isAdded()) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		baseTextSizeSp = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
		scaleFactor = 1.0f; // Reset scale on manual preference change
		updateTextSize();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (AppPreferences.KEY_EDITOR_TEXT_SIZE.equals(key) && isAdded()) {
			applyPreferences();
		}
	}

	private void setupClickListeners(View view) {
		view.findViewById(R.id.openFolderText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity)
				((MainActivity) getActivity()).openDirectory();
		});

		view.findViewById(R.id.openFilesText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity)
				((MainActivity) getActivity()).openLeftNavigation();
		});

		view.findViewById(R.id.manageLanguagesSettings).setOnClickListener(v -> {
			// yet to complete
			android.widget.Toast.makeText(getContext(), "Language Management is coming soon!", android.widget.Toast.LENGTH_SHORT).show();
			// startActivity(new Intent(view.getContext(), ManageLanguagesActivity.class));
		});

		view.findViewById(R.id.openEditorSettings).setOnClickListener(v -> {
			startActivity(new Intent(view.getContext(), EditorActivity.class));
		});

		view.findViewById(R.id.openSettings).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity)
				((MainActivity) getActivity()).openSettings();
		});

		view.findViewById(R.id.openFileFromInternalStorageText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity)
				((MainActivity) getActivity()).openFilePicker();
		});

		view.findViewById(R.id.openNewTerminal).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity)
				((MainActivity) getActivity()).openNewTerminal();
		});
	}
}
