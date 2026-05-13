package com.cs.ide.app.activities;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.cs.ide.R;
import com.cs.ide.app.adapters.FilesAdapter;
import com.cs.ide.app.adapters.ViewPagerAdapter;
import com.cs.ide.app.dialogs.CreateFileDialog;
import com.cs.ide.app.editor.TabManager;
import com.cs.ide.app.execution.CommandUpdater;
import com.cs.ide.app.execution.ExecutionManager;
import com.cs.ide.app.fragments.TerminalFragment;
import com.cs.ide.app.fragments.TextFragment;
import com.cs.ide.app.models.FileItem;
import com.cs.ide.app.utils.AppPreferences;
import com.cs.ide.app.utils.DialogHelper;
import com.cs.ide.app.utils.DisplayManager;
import com.cs.ide.app.utils.FileUtils;
import com.cs.ide.termux.app.TermuxActivity;
import com.cs.ide.termux.app.TermuxInstaller;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main activity for the Code Studio Mobile IDE.
 * Handles the main UI, file management integration, and tab management.
 */
public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener,
		FilesAdapter.OnFileClickListener, CreateFileDialog.OnFileCreatedListener, TerminalFragment.ConsoleInputListener, SharedPreferences.OnSharedPreferenceChangeListener {

	// --- Constants ---
	private static final String TAG = "MainActivity";
	private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
	private static final int REQUEST_CODE_OPEN_FILE = 2001;
	private static final int REQUEST_CODE_OPEN_FILE_FOR_IMPORT = 1002;
	// --- Static State ---
	public static Uri currentDirectoryUri = null;
	public static ViewPagerAdapter viewPagerAdapter;
	private final int AUTO_SAVE_INTERVAL_MS = 10000;
	// --- State & Helpers ---
	private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final List<FileItem> fileItems = new ArrayList<>();
	private final Map<String, String> tabSaveTimes = new HashMap<>();
	private final ArrayList<Uri> folderUris = new ArrayList<>();
	private final ArrayList<String> folderNames = new ArrayList<>();
	public Uri currentFileUri;
	public String currentMimeType;
	// --- UI Elements ---
	private DrawerLayout drawerLayout;
	private TabLayout tabLayout;
	private ViewPager2 viewPager;
	/**
	 * Runnable task for periodic auto-save of open files.
	 */
	private final Runnable autoSaveRunnable = new Runnable() {
		@Override
		public void run() {
			performAutoSave(viewPager.getCurrentItem());
			autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
		}
	};
	private RecyclerView filesList;
	private FilesAdapter filesAdapter;
	private TextView currentFolderTitle;
	private ImageButton refreshFolder;
	private ImageButton collapseAllFolders;
	private ProgressBar progressBar;
	private TabManager tabManager;
	private TabManager.TabState lastClosedTabState = null;
	private FileItem selectedFileItem;
	private FileItem importTargetFolder;
	private Uri rootDirectoryUri = null;
	private Uri folderUri = null;
	private boolean runMenuVisible = false;
	private boolean editMenuVisible = false;

	// --- Listeners & Runnables ---
	private boolean stopMenuVisible = false;

	// --- Static Utility Methods ---

	/**
	 * Handles file intents when the app is opened from an external file manager.
	 */
	public static void handleFileIntent(Context context, Intent intent) {
		if (intent == null) return;
		String action = intent.getAction();
		Uri uri = intent.getData();
		if (Intent.ACTION_VIEW.equals(action) && uri != null) {
			Log.d(TAG, "Handling file intent in static method for URI: " + uri);
			try {
				String fileName = FileUtils.getFileName(context, uri);
				String fileTypeKey = FileUtils.getFileTypeKey(fileName);
				Toast.makeText(context, context.getString(R.string.msg_file_saved_successfully, fileName, fileTypeKey), Toast.LENGTH_LONG).show();
				Intent mainIntent = new Intent(context, MainActivity.class);
				mainIntent.setAction(Intent.ACTION_VIEW);
				mainIntent.setData(uri);
				mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				context.startActivity(mainIntent);
			} catch (Exception e) {
				Log.e(TAG, "Error handling file intent: " + e.getMessage());
			}
		}
	}

	// --- Lifecycle Methods ---

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new Handler(Looper.getMainLooper()).postDelayed(() -> CommandUpdater.checkForUpdates(this), 1000);
		setContentView(R.layout.activity_main_code_studio);

		setupUI();
		setupNavigation();

		tabManager = new TabManager(this);
		restoreLastFolder();

		setupTabs();
		handleIntent(getIntent());
	}

	@Override
	protected void onResume() {
		super.onResume();
		autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
		applyPreferences();
	}

	@Override
	protected void onPause() {
		super.onPause();
		performAutoSave(viewPager.getCurrentItem());
		autoSaveHandler.removeCallbacks(autoSaveRunnable);
		tabManager.saveOpenedTabs(viewPagerAdapter, tabLayout);
		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	private void applyPreferences() {
		SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
		int textSize = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
		
		updateTabLayoutTextSize(textSize);
	}

	private void updateTabLayoutTextSize(int sizeSp) {
		for (int i = 0; i < tabLayout.getTabCount(); i++) {
			TabLayout.Tab tab = tabLayout.getTabAt(i);
			if (tab != null && tab.view != null) {
				updateTabViews(tab.view, sizeSp);
			}
		}
	}

	private void updateTabViews(ViewGroup viewGroup, int sizeSp) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View child = viewGroup.getChildAt(i);
			if (child instanceof TextView) {
				((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
			} else if (child instanceof ViewGroup) {
				updateTabViews((ViewGroup) child, sizeSp);
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (AppPreferences.KEY_EDITOR_TEXT_SIZE.equals(key)) {
			applyPreferences();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		executor.shutdown();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	// --- Initialization & Setup ---

	private void setupUI() {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		drawerLayout = findViewById(R.id.drawerLayout);
		ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, DisplayManager::setupDynamicMarginHandling);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		progressBar = findViewById(R.id.progressBar);
		tabLayout = findViewById(R.id.tabLayout);
		viewPager = findViewById(R.id.viewPager2);
		viewPager.setUserInputEnabled(false);
	}

	private void setupNavigation() {
		NavigationView leftNavigation = findViewById(R.id.leftNavigation);
		Toolbar toolbar = findViewById(R.id.toolbar);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawerLayout.addDrawerListener(toggle);
		toggle.syncState();

		View headerView = leftNavigation.getHeaderView(0);
		currentFolderTitle = headerView.findViewById(R.id.currentFolderTitle);
		refreshFolder = headerView.findViewById(R.id.refreshFilesFolders);
		collapseAllFolders = headerView.findViewById(R.id.collapseAllFolders);

		TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
		});
	}

	private void setupTabs() {
		TabManager.TabState state = tabManager.loadRecentTabs();
		viewPagerAdapter = new ViewPagerAdapter(this, state.uris, state.names);
		viewPager.setAdapter(viewPagerAdapter);
		tabLayout.addOnTabSelectedListener(this);

		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			tab.setText(viewPagerAdapter.fileNames.get(position));
			
			// Apply initial text size
			SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
			int textSize = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
			tab.view.post(() -> updateTabViews(tab.view, textSize));

			tab.view.setOnLongClickListener(v -> {
				currentFileUri = viewPagerAdapter.getFileUris().get(position);
				currentMimeType = getMimeType(viewPagerAdapter.fileUris.get(position));
				showTabPopupMenu(v, position);
				return true;
			});
		}).attach();

		if (state.activeTabIndex != -1 && state.activeTabIndex < viewPagerAdapter.getItemCount()) {
			viewPager.post(() -> {
				viewPager.setCurrentItem(state.activeTabIndex, false);
				TabLayout.Tab savedTab = tabLayout.getTabAt(state.activeTabIndex);
				if (savedTab != null) savedTab.select();
			});
		}
	}

	// --- Menu & Popup Handling ---

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
		MenuItem runItem = menu.findItem(R.id.runFile);
		MenuItem editItem = menu.findItem(R.id.editFile);
		MenuItem stopExecutionItem = menu.findItem(R.id.stopExecution);
		if (runItem != null) runItem.setVisible(runMenuVisible);
		if (stopExecutionItem != null) stopExecutionItem.setVisible(stopMenuVisible);
		if (editItem != null) editItem.setVisible(editMenuVisible);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.runFile) {
			handleRunFile();
			return true;
		} else if (id == R.id.openNewTerminal) {
			return openNewTerminal();
		} else if (id == R.id.editFile) {
			handleEditFile();
			return true;
		} else if (id == R.id.openWelcomeScreen) {
			int newTabIndex = viewPagerAdapter.addTab(ViewPagerAdapter.WELCOME_URI, getString(R.string.welcome));
			if (newTabIndex != -1) tabLayout.selectTab(tabLayout.getTabAt(newTabIndex));
			return true;
		} else if (id == R.id.saveFiles) {
			saveAllOpenFiles();
			return true;
		} else if (id == R.id.settings) {
			openSettings();
			return true;
		} else if (id == R.id.about) {
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		} else if (id == R.id.openFolder) {
			openDirectory();
			return true;
		} else if (id == R.id.refreshFilesFolders) {
			refreshAll();
			return true;
		} else if (id == R.id.openFile) {
			openFilePicker();
			return true;
		} else if (id == R.id.newItem || id == R.id.newFile) {
			showCreateFileDialog(null, 0);
			return true;
		} else if (id == R.id.newFolder) {
			showCreateFileDialog(null, 1);
			return true;
		} else if (id == R.id.saveAs) {
			handleSaveAs();
			return true;
		} else if (id == R.id.openNewFile) {
			int newTabIndex = viewPagerAdapter.addTab(ViewPagerAdapter.UNTITLED_FILE_URI, getString(R.string.untitled));
			if (newTabIndex != -1) {
				tabLayout.selectTab(tabLayout.getTabAt(newTabIndex));
				viewPager.setCurrentItem(newTabIndex);
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showTabPopupMenu(View view, int position) {
		PopupMenu popup = new PopupMenu(this, view);
		popup.getMenuInflater().inflate(R.menu.tab_menu, popup.getMenu());
		popup.setOnMenuItemClickListener(item -> {
			int id = item.getItemId();
			if (id == R.id.close_tab) {
				viewPagerAdapter.removeTab(position);
			} else if (id == R.id.close_other_tabs) {
				viewPagerAdapter.removeOtherTabs(position);
			} else if (id == R.id.close_all_tabs) {
				viewPagerAdapter.removeAllTabs();
			}
			return true;
		});
		popup.show();
	}

	public void showFileContextMenu(View view, @NonNull FileItem fileItem) {
		PopupMenu popupMenu = new PopupMenu(this, view);
		popupMenu.getMenuInflater().inflate(R.menu.file_menu, popupMenu.getMenu());
		if (fileItem.isDirectory) {
			popupMenu.getMenu().findItem(R.id.run_file).setVisible(false);
		}
		popupMenu.setOnMenuItemClickListener(item -> {
			int id = item.getItemId();
			if (id == R.id.new_file_folder) {
				showCreateFileDialog(fileItem, -1);
				return true;
			} else if (id == R.id.rename_file) {
				showRenameDialog(fileItem);
				return true;
			} else if (id == R.id.delete_file) {
				showDeleteConfirmationDialog(fileItem);
				return true;
			} else if (id == R.id.import_file) {
				openFilePickerForImport(fileItem);
				return true;
			} else if (id == R.id.run_file) {
				runFile(fileItem);
				return true;
			}
			return false;
		});
		popupMenu.show();
	}

	// --- Tab Selection Callbacks ---

	@Override
	public void onTabSelected(@NonNull TabLayout.Tab tab) {
		viewPager.setCurrentItem(tab.getPosition());
		updateSubtitleForTab(tab.getPosition());
		Uri uri = (tab.getPosition() < viewPagerAdapter.fileUris.size()) ? viewPagerAdapter.fileUris.get(tab.getPosition()) : null;
		runMenuVisible = (uri != null && extensionAllowsRun(uri));
		if (uri != null) setSelectedFileItem(FileUtils.getFileItemFromUri(this, uri));
		invalidateOptionsMenu();
	}

	@Override
	public void onTabUnselected(TabLayout.Tab tab) {
		performAutoSave(tab.getPosition());
	}

	@Override
	public void onTabReselected(TabLayout.Tab tab) {
	}

	// --- Intent & Result Handling ---

	private void handleIntent(Intent intent) {
		if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
			Uri fileUri = intent.getData();
			if (fileUri != null) {
				try {
					final int takeFlags = intent.getFlags()
							& (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					if (takeFlags != 0 && "content".equals(fileUri.getScheme())) {
						getContentResolver().takePersistableUriPermission(fileUri, takeFlags);
					}
				} catch (Exception e) {
					Log.w(TAG, "Could not persist permissions: " + e.getMessage());
				}

				String fileName = FileUtils.getFileName(this, fileUri);
				int tabIndex = viewPagerAdapter.addTab(fileUri, fileName);
				if (tabIndex != -1) {
					viewPager.post(() -> {
						if (tabIndex < viewPagerAdapter.getItemCount()) {
							viewPager.setCurrentItem(tabIndex, true);
							TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
							if (tab != null) tab.select();
						}
					});
				}
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
		Uri uri = data.getData();

		if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
			handleDirectoryResult(uri, data.getFlags());
		} else if (requestCode == REQUEST_CODE_OPEN_FILE) {
			handleFileOpenResult(uri, data.getFlags());
		} else if (requestCode == REQUEST_CODE_OPEN_FILE_FOR_IMPORT) {
			if (importTargetFolder != null) showImportTargetFolderDialog(uri, importTargetFolder);
		}
	}

	private void handleDirectoryResult(Uri uri, int flags) {
		try {
			getContentResolver().takePersistableUriPermission(uri, flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
		} catch (Exception e) {
			Log.e(TAG, "Failed to take permission: " + e.getMessage());
		}
		folderUri = currentDirectoryUri = rootDirectoryUri = uri;
		saveLastFolder(uri);
		setupFilesAdapter(uri);
	}

	private void handleFileOpenResult(Uri uri, int flags) {
		try {
			getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (Exception e) {
			Log.e(TAG, "Failed to take permission: " + e.getMessage());
		}
		openFileInViewPager(uri, FileUtils.getFileName(this, uri));
		closeLeftNavigation();
	}

	// --- File Operations (Create, Rename, Delete) ---

	public void showCreateFileDialog(@Nullable FileItem baseItem, int initialType) {
		Uri parentUri = null;
		if (baseItem != null) {
			parentUri = baseItem.isDirectory ? baseItem.uri : getSafParentUri(baseItem.uri);
		} else if (rootDirectoryUri != null) {
			parentUri = rootDirectoryUri;
		}

		if (parentUri == null) {
			Toast.makeText(this, R.string.open_folder_first, Toast.LENGTH_LONG).show();
		}

		List<FileItem> subFolders = parentUri != null ? getChildFolders(parentUri) : new ArrayList<>();
		String currentFolderName = (baseItem != null && baseItem.isDirectory) ? baseItem.displayName :
				(parentUri != null ? FileUtils.getFileName(this, parentUri) : getString(R.string.app_storage_default));

		List<String> folderNamesList = new ArrayList<>();
		List<Uri> folderUrisList = new ArrayList<>();

		folderNamesList.add(parentUri != null ? getString(R.string.current_prefix, currentFolderName) : getString(R.string.app_storage_default));
		folderUrisList.add(parentUri);

		for (FileItem folder : subFolders) {
			folderNamesList.add(folder.displayName);
			folderUrisList.add(folder.uri);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.create_new_item);
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
		LinearLayout mainLayout = (LinearLayout) dialogView;

		TextView typeLabel = new TextView(this);
		typeLabel.setText(R.string.item_type);
		typeLabel.setTextAppearance(this, android.R.style.TextAppearance_Small);

		Spinner typeSpinner = new Spinner(this);
		String[] types = {getString(R.string.file), getString(R.string.folder)};
		typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
		if (initialType >= 0 && initialType < types.length) typeSpinner.setSelection(initialType);

		mainLayout.addView(typeLabel, 2);
		mainLayout.addView(typeSpinner, 3);

		EditText input = dialogView.findViewById(R.id.input_name);
		Spinner folderSpinner = dialogView.findViewById(R.id.spinner_folder);
		folderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNamesList));

		builder.setView(dialogView);
		builder.setPositiveButton(R.string.create, (dialog, which) -> {
			String name = input.getText().toString().trim();
			if (name.isEmpty()) {
				Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
				return;
			}
			Uri selectedParentUri = folderUrisList.get(folderSpinner.getSelectedItemPosition());
			if (selectedParentUri == null) {
				createInAppStorage(name, typeSpinner.getSelectedItemPosition() == 1);
			} else {
				createDocumentAsync(selectedParentUri, name, typeSpinner.getSelectedItemPosition() == 1);
			}
		});
		builder.setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel());
		builder.show();
	}

	private void createDocumentAsync(Uri parentUri, String originalName, boolean isFolder) {
		runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
		new Thread(() -> {
			Uri newDocumentUri = null;
			int conflictCount = 1;
			boolean success = false;
			String finalNewName = originalName.replaceAll(" ", "_");

			while (!success && conflictCount <= 10) {
				try {
					String nameToTry = (conflictCount > 1) ? getNextConflictName(finalNewName, conflictCount) : finalNewName;
					String mimeType = isFolder ? DocumentsContract.Document.MIME_TYPE_DIR : MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(nameToTry));
					if (mimeType == null) mimeType = "application/octet-stream";

					newDocumentUri = DocumentsContract.createDocument(getContentResolver(), parentUri, mimeType, nameToTry);
					if (newDocumentUri != null) {
						success = true;
						finalNewName = nameToTry;
					}
				} catch (Exception e) {
					Log.w(TAG, "Creation attempt " + conflictCount + " failed: " + e.getMessage());
				}
				conflictCount++;
			}

			final Uri finalUri = newDocumentUri;
			final String finalName = finalNewName;
			final int count = conflictCount - 1;

			runOnUiThread(() -> {
				progressBar.setVisibility(View.GONE);
				if (finalUri != null) {
					if (!isFolder) FilesAdapter.saveFileContentAsync(this, finalUri, "".getBytes());
					String msg = (isFolder ? getString(R.string.folder_created_msg, finalName) : getString(R.string.file_created_msg, finalName));
					if (count > 1) msg += getString(R.string.auto_resolved_suffix);
					Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
					if (!isFolder) openFileInViewPager(finalUri, finalName);
					new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
				} else {
					Toast.makeText(this, getString(R.string.failed_to_create_item, (isFolder ? getString(R.string.folder) : getString(R.string.file))), Toast.LENGTH_LONG).show();
				}
			});
		}).start();
	}

	private void showRenameDialog(FileItem fileItem) {
		DialogHelper.showRenameDialog(this, fileItem.displayName, newName -> {
			if (!fileItem.isDirectory) closeInViewPager(fileItem.uri);
			renameDocumentAsync(fileItem, newName);
		});
	}

	private void renameDocumentAsync(FileItem fileItem, String newName) {
		Uri oldUri = fileItem.uri;
		final String originalNewName = newName.replaceAll(" ", "_");
		runOnUiThread(() -> {
			progressBar.setVisibility(View.VISIBLE);
			Toast.makeText(this, R.string.renaming, Toast.LENGTH_SHORT).show();
		});

		new Thread(() -> {
			Uri renamedUri = null;
			int conflictCount = 1;
			boolean success = false;
			String finalNewName = originalNewName;

			while (!success && conflictCount <= 10) {
				try {
					String nameToTry = (conflictCount > 1) ? getNextConflictName(originalNewName, conflictCount) : originalNewName;
					renamedUri = DocumentsContract.renameDocument(getContentResolver(), oldUri, nameToTry);
					if (renamedUri != null) {
						success = true;
						finalNewName = nameToTry;
					}
				} catch (Exception e) {
					Log.w(TAG, "Rename attempt " + conflictCount + " failed: " + e.getMessage());
				}
				conflictCount++;
			}

			final Uri finalUri = renamedUri;
			final String finalName = finalNewName;
			final int count = conflictCount - 1;

			runOnUiThread(() -> {
				progressBar.setVisibility(View.GONE);
				if (finalUri != null) {
					String msg = getString(R.string.renamed_to, finalName);
					if (count > 1) msg += getString(R.string.auto_resolved_suffix);
					Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
					filesAdapter.updateFileItem(oldUri, finalUri, finalName, fileItem.isDirectory, this);
					if (!fileItem.isDirectory) reopenClosedTab(finalUri, finalName);
					new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
				} else {
					Toast.makeText(this, getString(R.string.failed_to_rename, fileItem.displayName), Toast.LENGTH_LONG).show();
					if (!fileItem.isDirectory && lastClosedTabState != null)
						reopenClosedTab(lastClosedTabState.uris.get(0), lastClosedTabState.names.get(0));
					filesAdapter.refresh();
				}
			});
		}).start();
	}

	private void showDeleteConfirmationDialog(@NonNull FileItem fileItem) {
		DialogHelper.showDeleteConfirmationDialog(this, fileItem.displayName, () -> {
			if (!fileItem.isDirectory) closeInViewPager(fileItem.uri);
			deleteDocumentAsync(fileItem);
		});
	}

	private void deleteDocumentAsync(@NonNull FileItem fileItem) {
		new Thread(() -> {
			try {
				if (DocumentsContract.deleteDocument(getContentResolver(), fileItem.uri)) {
					runOnUiThread(() -> {
						Toast.makeText(this, getString(R.string.deleted_msg, fileItem.displayName), Toast.LENGTH_LONG).show();
						lastClosedTabState = null;
						new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
					});
				} else {
					runOnUiThread(() -> Toast.makeText(this, R.string.failed_to_delete, Toast.LENGTH_LONG).show());
				}
			} catch (Exception e) {
				Log.e(TAG, "Error deleting: " + e.getMessage());
			}
		}).start();
	}

	// --- File Explorer Management ---

	private void restoreLastFolder() {
		String uriString = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE)
				.getString(AppPreferences.LAST_FOLDER_URI_KEY, null);
		if (uriString != null) {
			try {
				Uri lastFolder = Uri.parse(uriString);
				getContentResolver().takePersistableUriPermission(lastFolder,
						Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
				folderUri = currentDirectoryUri = lastFolder;
				setupFilesAdapter(lastFolder);
			} catch (Exception e) {
				saveLastFolder(null);
			}
		}
	}

	private void saveLastFolder(Uri uri) {
		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE).edit()
				.putString(AppPreferences.LAST_FOLDER_URI_KEY, uri != null ? uri.toString() : null).apply();
	}

	private void setupFilesAdapter(Uri uri) {
		if (filesAdapter == null) {
			filesList = findViewById(R.id.filesList);
			filesAdapter = new FilesAdapter(this, fileItems, this, rootDirectoryUri);
			filesList.setLayoutManager(new LinearLayoutManager(this));
			filesList.setAdapter(filesAdapter);
			collapseAllFolders.setOnClickListener(v -> filesAdapter.collapseAllFolders());
			refreshFolder.setOnClickListener(v -> refreshFileList());
		}
		refreshFileList();
	}

	private void refreshFileList() {
		if (folderUri == null || filesAdapter == null) return;
		ProgressBar loading = findViewById(R.id.filesLoadingProgress);
		loading.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			populateFileList(folderUri, 0);
			runOnUiThread(() -> {
				filesAdapter.notifyDataSetChanged();
				loading.setVisibility(View.GONE);
			});
		});
	}

	public void populateFileList(final Uri uri, final int depth) {
		try {
			String documentId = DocumentsContract.getTreeDocumentId(uri);
			Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId);
			final List<FileItem> folders = new ArrayList<>();
			final List<FileItem> files = new ArrayList<>();

			try (Cursor cursor = getContentResolver().query(childrenUri,
					new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
							DocumentsContract.Document.COLUMN_DISPLAY_NAME,
							DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					do {
						String id = cursor.getString(0);
						String name = cursor.getString(1);
						String mime = cursor.getString(2);
						boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
						Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, id);
						if (isDir) folders.add(new FileItem(childUri, name, true, depth, mime));
						else files.add(new FileItem(childUri, name, false, depth, mime));
					} while (cursor.moveToNext());
				}
			}
			folders.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
			files.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
			runOnUiThread(() -> {
				currentFolderTitle.setText(getString(R.string.label_storage_prefix) + documentId.substring(Math.min(8, documentId.length())));
				fileItems.clear();
				fileItems.addAll(folders);
				fileItems.addAll(files);
				if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
			});
		} catch (Exception e) {
			Log.e(TAG, "Populate error: " + e.getMessage());
		}
	}

	private void refreshAll() {
		refreshFileList();
		int currentTabPos = tabLayout.getSelectedTabPosition();
		if (currentTabPos != -1) {
			Fragment frag = viewPagerAdapter.getFragment(currentTabPos);
			if (frag instanceof TextFragment) ((TextFragment) frag).refreshContent();
		}
	}

	// --- File Execution & Actions ---

	public void runFile(FileItem item) {
		ExecutionManager.runFile(this, item);
		int pos = viewPagerAdapter.getItemCount() - 1;
		viewPager.setCurrentItem(pos, true);
	}

	private void handleRunFile() {
		if (selectedFileItem != null) {
			runFile(selectedFileItem);
		} else if (currentFileUri != null) {
			FileItem fileItem = new FileItem(this, currentFileUri, FileUtils.getFileName(this, currentFileUri), false, 0);
			runFile(fileItem);
		} else {
			Toast.makeText(this, R.string.no_file_selected_to_run, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleEditFile() {
		int currentTabPos = tabLayout.getSelectedTabPosition();
		if (currentTabPos != -1) {
			String currentTabName = viewPagerAdapter.fileNames.get(currentTabPos);
			String runPrefix = getString(R.string.running_prefix, "");
			if (currentTabName.startsWith(runPrefix)) {
				String originalFileName = currentTabName.substring(runPrefix.length());
				int originalFileTabPos = viewPagerAdapter.findTabPositionByName(originalFileName);
				if (originalFileTabPos != -1) {
					tabLayout.selectTab(tabLayout.getTabAt(originalFileTabPos));
					viewPager.setCurrentItem(originalFileTabPos);
					viewPagerAdapter.removeTab(currentTabPos);
				}
			}
		}
		runMenuVisible = true;
		stopMenuVisible = false;
		editMenuVisible = false;
		invalidateOptionsMenu();
	}

	// --- Saving Logic ---

	private void performAutoSave(int position) {
		if (viewPagerAdapter == null || position < 0 || position >= viewPagerAdapter.getItemCount())
			return;
		Uri uri = viewPagerAdapter.getFileUris().get(position);
		if (uri.equals(ViewPagerAdapter.WELCOME_URI) || uri.equals(ViewPagerAdapter.UNTITLED_FILE_URI))
			return;

		Fragment fragment = viewPagerAdapter.getFragment(position);
		if (fragment instanceof TextFragment) {
			TextFragment textFragment = (TextFragment) fragment;
			if (!textFragment.isSaved()) {
				byte[] content = textFragment.getContents();
				if (content != null && !viewPagerAdapter.getFileNames().get(position).startsWith(getString(R.string.run_prefix, ""))) {
					FilesAdapter.saveFileContentAsync(this, uri, content);
					textFragment.setSaved(true);
					tabSaveTimes.put(uri.toString(), new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
				}
			}
		}
		updateSubtitleForTab(position);
	}

	private void updateSubtitleForTab(int position) {
		if (getSupportActionBar() == null || viewPagerAdapter == null || position >= viewPagerAdapter.getItemCount())
			return;
		String lastSave = tabSaveTimes.get(viewPagerAdapter.getFileUris().get(position).toString());
		getSupportActionBar().setSubtitle(lastSave != null ? getString(R.string.autosaved_at, lastSave) : getString(R.string.nothing_changed));
	}

	private void saveAllOpenFiles() {
		try {
			if (viewPagerAdapter != null && filesAdapter != null) {
				List<FilesAdapter.FileContentItem> filesToSave = viewPagerAdapter.getOpenFilesContent();
				if (filesToSave != null && !filesToSave.isEmpty()) {
					filesAdapter.saveAllFiles(filesToSave);
				} else {
					Toast.makeText(this, R.string.no_files_to_save, Toast.LENGTH_SHORT).show();
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error saving files", e);
			Toast.makeText(this, R.string.failed_to_save_files, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleSaveAs() {
		int currentTabPos = viewPager.getCurrentItem();
		if (currentTabPos != -1) {
			Fragment fragment = viewPagerAdapter.getFragment(currentTabPos);
			if (fragment instanceof TextFragment) {
				requestSaveAs(((TextFragment) fragment).getContents());
			} else {
				Toast.makeText(this, R.string.msg_content_cannot_be_saved_save_as, Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(this, R.string.msg_no_tab_open_to_save, Toast.LENGTH_SHORT).show();
		}
	}

	private void saveContentToFile(Uri uri, byte[] content, String name) {
		new Thread(() -> {
			try {
				OutputStream os;
				if (uri.getScheme() != null && uri.getScheme().equals("file")) {
					os = new java.io.FileOutputStream(new java.io.File(uri.getPath()));
				} else {
					os = getContentResolver().openOutputStream(uri);
				}

				if (os != null) {
					os.write(content);
					os.close();
					runOnUiThread(() -> {
						int untitled = viewPagerAdapter.findTabPositionByName(getString(R.string.untitled));
						if (untitled != -1) viewPagerAdapter.removeTab(untitled);
						openFileInViewPager(uri, name);
						Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_SHORT).show();
					});
				}
			} catch (IOException e) {
				runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_saving_file, e.getMessage()), Toast.LENGTH_LONG).show());
			}
		}).start();
	}

	@Override
	public void requestSaveAs(byte[] content) {
		prepareFolderDataForDialog();
		if (currentDirectoryUri == null) {
			Toast.makeText(this, R.string.open_folder_first, Toast.LENGTH_LONG).show();
		}
		if (folderUris.isEmpty()) {
			Toast.makeText(this, R.string.select_folder_with_permission, Toast.LENGTH_LONG).show();
			return;
		}
		CreateFileDialog.newInstance(folderUris, folderNames, content).show(getSupportFragmentManager(), "SaveAsFileDialog");
	}

	// --- Tab Manipulation Helpers ---

	public void openFileInViewPager(Uri uri, String name) {
		int pos = viewPagerAdapter.addTab(uri, name);
		viewPager.setCurrentItem(pos, true);
		invalidateOptionsMenu();
	}

	public void closeInViewPager(Uri fileUri) {
		int index = viewPagerAdapter.fileUris.indexOf(fileUri);
		if (index != -1) {
			lastClosedTabState = new TabManager.TabState(
					java.util.Collections.singletonList(fileUri),
					java.util.Collections.singletonList(viewPagerAdapter.fileNames.get(index)),
					index);
			viewPagerAdapter.removeTab(index);
		}
	}

	public void closeFileInViewPager(Uri fileUri) {
		closeInViewPager(fileUri);
	}

	public void reopenClosedTab(Uri newUri, String newName) {
		if (lastClosedTabState == null) return;
		int pos = Math.min(lastClosedTabState.activeTabIndex, viewPagerAdapter.getItemCount());
		viewPagerAdapter.fileUris.add(pos, newUri);
		viewPagerAdapter.fileNames.add(pos, newName);
		viewPagerAdapter.notifyDataSetChanged();
		viewPager.setCurrentItem(pos);
		lastClosedTabState = null;
	}

	// --- Import Helpers ---

	public void openFilePickerForImport(@NonNull FileItem targetFileItem) {
		FileItem folder = targetFileItem.isDirectory ? targetFileItem : getParentFolderItem(targetFileItem);
		if (folder == null || folder.uri == null) return;
		this.importTargetFolder = folder;

		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		String[] mimeTypes = {"text/*", "application/json", "application/xml", "application/javascript",
				"application/x-java-source", "text/x-csrc", "text/x-c++src", "text/x-python",
				"image/*", "audio/*", "video/*"};
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		startActivityForResult(intent, REQUEST_CODE_OPEN_FILE_FOR_IMPORT);
	}

	public void showImportTargetFolderDialog(Uri sourceUri, FileItem targetFolder) {
		String sourceFileName = FileUtils.getFileName(this, sourceUri);
		List<FileItem> subFolders = getChildFolders(targetFolder.uri);
		List<String> names = new ArrayList<>();
		List<Uri> uris = new ArrayList<>();
		names.add(getString(R.string.default_prefix, targetFolder.displayName));
		uris.add(targetFolder.uri);
		for (FileItem folder : subFolders) {
			names.add(folder.displayName);
			uris.add(folder.uri);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.import_file_title, sourceFileName));
		View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
		EditText input = view.findViewById(R.id.input_name);
		Spinner spinner = view.findViewById(R.id.spinner_folder);
		input.setText(sourceFileName);
		spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
		builder.setView(view);
		builder.setPositiveButton(R.string.import_file, (dialog, which) -> {
			if (input.getText().toString().trim().isEmpty()) return;
			importFileAsync(sourceUri, uris.get(spinner.getSelectedItemPosition()), input.getText().toString().trim());
		});
		builder.setNegativeButton(R.string.action_cancel, null).show();
	}

	private void importFileAsync(Uri source, Uri targetFolder, String name) {
		new Thread(() -> {
			try {
				String mime = getContentResolver().getType(source);
				if (mime == null) {
					mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(name));
				}
				Uri newUri = DocumentsContract.createDocument(getContentResolver(), targetFolder, mime != null ? mime : "application/octet-stream", name);
				if (newUri != null) {
					try (java.io.InputStream is = getContentResolver().openInputStream(source);
					     java.io.OutputStream os = getContentResolver().openOutputStream(newUri)) {
						if (is != null && os != null) {
							byte[] buffer = new byte[4096];
							int read;
							while ((read = is.read(buffer)) != -1) {
								os.write(buffer, 0, read);
							}
						}
					}
					runOnUiThread(() -> {
						Toast.makeText(this, getString(R.string.file_imported_successfully, name), Toast.LENGTH_LONG).show();
						refreshFileList();
						openFileInViewPager(newUri, name);
					});
				}
			} catch (Exception e) {
				Log.e(TAG, "Import error: " + e.getMessage());
			}
		}).start();
	}

	// --- App Settings & Storage Helpers ---

	private void createInAppStorage(String name, boolean isFolder) {
		File appStorageDir = new File(getFilesDir(), "code_studio_files");
		if (!appStorageDir.exists()) {
			appStorageDir.mkdirs();
		}
		File newFile = new File(appStorageDir, name.replaceAll(" ", "_"));

		if (newFile.exists()) {
			Toast.makeText(this, R.string.msg_item_exists_in_app_storage, Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			boolean success = isFolder ? newFile.mkdirs() : newFile.createNewFile();
			if (success) {
				Uri uri = Uri.fromFile(newFile);
				if (!isFolder) openFileInViewPager(uri, name);
				Toast.makeText(this, isFolder ? R.string.msg_folder_created_app_storage : R.string.msg_file_created_app_storage, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, R.string.msg_failed_create_item_app_storage, Toast.LENGTH_LONG).show();
			}
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.msg_error_prefix, e.getMessage()), Toast.LENGTH_LONG).show();
		}
	}

	public void openSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}

	public boolean openNewTerminal() {
		startActivity(new Intent(this, TermuxActivity.class));
		return true;
	}

	// --- Navigation Drawer Helpers ---

	public void openLeftNavigation() {
		if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
	}

	public void closeLeftNavigation() {
		if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
	}

	public void openDirectory() {
		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE_OPEN_DIRECTORY);
	}

	public void openFilePicker() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		String[] mimes = {"text/*", "application/json", "application/xml", "application/javascript",
				"application/x-java-source", "text/x-csrc", "text/x-c++src", "text/x-python",
				"image/*", "audio/*", "video/*"};
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
		startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
	}

	// --- General Data & State Helpers ---

	@Override
	public void onFileClicked(Uri fileUri, String fileName) {
		String mimeType = getMimeType(fileUri);
		if (FileUtils.isExternalViewType(mimeType)) {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(fileUri, mimeType != null ? mimeType : "*/*");
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			try {
				startActivity(intent);
			} catch (Exception e) {
				Toast.makeText(this, R.string.no_app_found_to_view, Toast.LENGTH_LONG).show();
			}
		} else {
			int tabIndex = viewPagerAdapter.addTab(fileUri, fileName);
			if (tabIndex != -1) {
				tabLayout.selectTab(tabLayout.getTabAt(tabIndex));
				viewPager.setCurrentItem(tabIndex);
				drawerLayout.closeDrawer(GravityCompat.START);
			}
		}
	}

	@Override
	public void onFileLongClick(View view, FileItem fileItem) {
		setSelectedFileItem(fileItem);
		showFileContextMenu(view, fileItem);
	}

	@Override
	public void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent) {
		if (fileContent != null) saveContentToFile(fileUri, fileContent, fileName);
		else openFileInViewPager(fileUri, fileName);
	}

	@Override
	public void onUserInputSubmitted(String input) {
	}

	public String getMimeType(Uri uri) {
		return FileUtils.getMimeType(this, uri);
	}

	public void setSelectedFileItem(FileItem item) {
		this.selectedFileItem = item;
	}

	private boolean extensionAllowsRun(Uri fileUri) {
		return ExecutionManager.extensionAllowsRun(fileUri);
	}

	private String getNextConflictName(String baseName, int conflictCount) {
		String name = baseName;
		String extension = "";
		int dotIndex = baseName.lastIndexOf('.');
		if (dotIndex > 0) {
			extension = baseName.substring(dotIndex);
			name = baseName.substring(0, dotIndex);
		}
		return name.replaceAll("_\\d+$", "") + "_" + conflictCount + extension;
	}

	private Uri getSafParentUri(Uri childUri) {
		try {
			DocumentsContract.Path path = DocumentsContract.findDocumentPath(getContentResolver(), childUri);
			if (path == null) return null;
			List<String> segments = path.getPath();
			if (segments.size() < 2) return null;
			String parentId = segments.get(segments.size() - 2);
			return DocumentsContract.buildDocumentUriUsingTree(childUri, parentId);
		} catch (Exception e) {
			Log.e(TAG, "Error finding parent: " + e.getMessage());
			return null;
		}
	}

	private FileItem getParentFolderItem(FileItem fileItem) {
		Uri parentUri = fileItem.isDirectory ? fileItem.uri : getSafParentUri(fileItem.uri);
		if (parentUri != null) {
			return new FileItem(parentUri, getString(R.string.parent_directory), true, fileItem.depth - 1, DocumentsContract.Document.MIME_TYPE_DIR);
		}
		return null;
	}

	@NonNull
	private List<FileItem> getChildFolders(Uri parentUri) {
		List<FileItem> folders = new ArrayList<>();
		String parentDocumentId;
		if (DocumentsContract.isDocumentUri(this, parentUri)) {
			parentDocumentId = DocumentsContract.getDocumentId(parentUri);
		} else {
			parentDocumentId = DocumentsContract.getTreeDocumentId(parentUri);
		}
		Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocumentId);
		try (Cursor cursor = getContentResolver().query(childrenUri,
				new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
						DocumentsContract.Document.COLUMN_DISPLAY_NAME,
						DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
			if (cursor != null && cursor.moveToFirst()) {
				do {
					String documentId = cursor.getString(0);
					String displayName = cursor.getString(1);
					String mimeType = cursor.getString(2);
					if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
						Uri childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId);
						folders.add(new FileItem(this, childUri, displayName, true, 0));
					}
				} while (cursor.moveToNext());
			}
		} catch (Exception e) {
			Log.e(TAG, "Error listing child folders", e);
		}
		return folders;
	}

	public void prepareFolderDataForDialog() {
		folderUris.clear();
		folderNames.clear();
		folderUris.add(null);
		folderNames.add(getString(R.string.app_storage_default));
		if (currentDirectoryUri != null) {
			DocumentFile parentDirectory = DocumentFile.fromTreeUri(this, currentDirectoryUri);
			if (parentDirectory != null && parentDirectory.isDirectory()) {
				for (DocumentFile df : parentDirectory.listFiles()) {
					if (df.isDirectory()) {
						folderUris.add(df.getUri());
						String n = df.getName();
						folderNames.add(n != null ? n : df.getUri().getLastPathSegment());
					}
				}
			}
		}
	}
}
