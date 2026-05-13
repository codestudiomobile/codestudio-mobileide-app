package com.cs.ide.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cs.ide.R;
import com.cs.ide.app.activities.EditorActivity;
import com.cs.ide.app.activities.MainActivity;
import com.cs.ide.app.activities.ManageLanguagesActivity;
import com.cs.ide.app.utils.AppPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * WelcomeFragment is the landing page shown to the user when no files are currently open.
 * It provides a quick-access dashboard for common IDE tasks such as opening folders,
 * managing language toolchains, and accessing settings.
 */
public class WelcomeFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

	private final List<TextView> scalableTextViews = new ArrayList<>();

	/**
	 * Static factory method to create a new instance of WelcomeFragment.
	 *
	 * @return A new instance of WelcomeFragment.
	 */
	public static WelcomeFragment newInstance() {
		return new WelcomeFragment();
	}

	/**
	 * Inflates the welcome screen layout.
	 */
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_welcome_code_studio, container, false);
	}

	/**
	 * Called after the view is created. Initializes the interaction logic for the welcome screen.
	 *
	 * @param view The inflated root view.
	 * @param savedInstanceState Fragment state.
	 */
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		initScalableViews(view);
		setupClickListeners(view);
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

	private void applyPreferences() {
		if (!isAdded()) return;
		SharedPreferences prefs = requireContext().getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		int textSize = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
		
		for (TextView tv : scalableTextViews) {
			if (tv != null) {
				tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize + 4); // Slightly larger than editor text
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (AppPreferences.KEY_EDITOR_TEXT_SIZE.equals(key) && isAdded()) {
			applyPreferences();
		}
	}

	/**
	 * Configures click listeners for various interactive elements on the welcome screen.
	 * Each button/text link triggers an action in the MainActivity or opens a specific settings activity.
	 *
	 * @param view The fragment's root view.
	 */
	private void setupClickListeners(View view) {
		// Action: Open a directory picker to load a project/folder
		view.findViewById(R.id.openFolderText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openDirectory();
			}
		});

		// Action: Open the left navigation drawer to browse already loaded files
		view.findViewById(R.id.openFilesText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openLeftNavigation();
			}
		});

		// Action: Navigate to the language toolchain management screen
		view.findViewById(R.id.manageLanguagesSettings).setOnClickListener(v -> {
			startActivity(new Intent(view.getContext(), ManageLanguagesActivity.class));
		});

		// Action: Navigate to editor-specific settings (e.g., startup behavior)
		view.findViewById(R.id.openEditorSettings).setOnClickListener(v -> {
			startActivity(new Intent(view.getContext(), EditorActivity.class));
		});

		// Action: Open general application settings
		view.findViewById(R.id.openSettings).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openSettings();
			}
		});

		// Action: Open a file picker for individual file access
		view.findViewById(R.id.openFileFromInternalStorageText).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openFilePicker();
			}
		});

		// Action: Trigger the creation of a new terminal session
		view.findViewById(R.id.openNewTerminal).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openNewTerminal();
			}
		});
	}
}
