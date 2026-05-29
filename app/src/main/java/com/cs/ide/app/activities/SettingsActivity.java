package com.cs.ide.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.cs.ide.R;
import com.cs.ide.app.utils.DisplayManager;

/**
 * SettingsActivity provides a list of configurable options and sub-activities
 * for the user to manage languages, editor preferences, and view app information.
 */
public class SettingsActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
	private String[] settingsItems;
	private ArrayAdapter<String> settingsAdapter;
	private View rootLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings_code_studio);

		// Define the list of settings options
		settingsItems = new String[]{
				getString(R.string.title_manage_languages),
				getString(R.string.action_open_editor_settings),
				getString(R.string.title_customization),
				getString(R.string.menu_about)
		};

		Toolbar toolbar = findViewById(R.id.toolbar);
		rootLayout = findViewById(R.id.settingsLayout);

		// Handle window insets for edge-to-edge
		if (rootLayout != null) {
			ViewCompat.setOnApplyWindowInsetsListener(rootLayout, DisplayManager::setupDynamicMarginHandling);
		}

		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setTitle(R.string.menu_settings);
		}

		// Setup the settings list
		ListView settingsList = findViewById(R.id.settingName);
		settingsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, settingsItems);
		settingsList.setAdapter(settingsAdapter);
		settingsList.setOnItemClickListener(this);
	}

	/**
	 * Handles clicks on individual settings items to launch corresponding activities.
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		String selectedItem = (String) parent.getItemAtPosition(position);

		if (selectedItem.equals(getString(R.string.title_manage_languages))) {
			startActivity(new Intent(this, ManageLanguagesActivity.class));
		} else if (selectedItem.equals(getString(R.string.action_open_editor_settings))) {
			startActivity(new Intent(this, EditorActivity.class));
		} else if (selectedItem.equals(getString(R.string.title_customization))) {
			startActivity(new Intent(this, CustomizationActivity.class));
		} else if (selectedItem.equals(getString(R.string.menu_about))) {
			startActivity(new Intent(this, AboutActivity.class));
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		// Handle back button in the toolbar
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
