package com.cs.ide.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
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
import com.cs.ide.termux.shared.android.PermissionUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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
 * The core orchestration layer for Code Studio Mobile IDE.
 * This activity manages the primary user interface, integrating a tabbed editor (via {@link ViewPager2}),
 * a terminal environment, and a hierarchical file explorer.
 * <p>
 * Performance Optimized: File system operations and heavy tasks are offloaded from the UI thread.
 */
public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener,
		FilesAdapter.OnFileClickListener, CreateFileDialog.OnFileCreatedListener, TerminalFragment.ConsoleInputListener, SharedPreferences.OnSharedPreferenceChangeListener {

	public static final Uri INTERNAL_STORAGE_URI = Uri.parse("app://com.cs.ide/internal_storage");
	private static final String TAG = "MainActivity";
	private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
	private static final int REQUEST_CODE_OPEN_FILE = 2001;
	private static final int REQUEST_CODE_OPEN_FILE_FOR_IMPORT = 1002;
	private static final int AUTO_SAVE_INTERVAL_MS = 10000;
	public static Uri currentDirectoryUri = null;
	public static ViewPagerAdapter viewPagerAdapter;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor = Executors.newFixedThreadPool(4); // Increased pool for better parallelism
	private final List<FileItem> fileItems = new ArrayList<>();
	private final Map<String, String> tabSaveTimes = new HashMap<>();
	private final ArrayList<Uri> folderUris = new ArrayList<>();
	private final ArrayList<String> folderNames = new ArrayList<>();
	public Uri currentFileUri;
	public String currentMimeType;
	public ViewPager2 viewPager;
	private long lastBackPressTime = 0;
	// --- UI Elements ---
	private DrawerLayout drawerLayout;
	private TabLayout tabLayout;
	private RecyclerView filesList;
	private FilesAdapter filesAdapter;
	private TextView currentFolderTitle;
	private ImageButton refreshFolder;
	private ImageButton collapseAllFolders;
	private ProgressBar progressBar;
	private ProgressBar filesLoadingProgress;

	// --- Component Managers ---
	private TabManager tabManager;
	private TabManager.TabState lastClosedTabState = null;
	private FileItem selectedFileItem;
	private FileItem importTargetFolder;
	private Uri rootDirectoryUri = null;
	private Uri folderUri = null;
	private Uri saveAsSourceUri = null;

	// --- UI State ---
	private boolean runMenuVisible = false;
	private boolean editMenuVisible = false;
	private boolean stopMenuVisible = false;
	private boolean isAutoSaveActive = false;

	private final Runnable autoSaveRunnable = new Runnable() {
		@Override
		public void run() {
			if (isAutoSaveActive) {
				performAutoSave(viewPager.getCurrentItem());
				mainHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
			}
		}
	};

	// --- Static Utility Methods ---

	public static void handleFileIntent(Context context, Intent intent) {
		if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
		Uri uri = intent.getData();
		if (uri == null) return;

		Log.d(TAG, "Handling file intent for URI: " + uri);
		try {
			String fileName = FileUtils.getFileName(context, uri);
			String fileTypeKey = FileUtils.getFileTypeKey(fileName);
			Toast.makeText(context, context.getString(R.string.msg_file_saved_successfully, fileName, fileTypeKey), Toast.LENGTH_LONG).show();

			Intent mainIntent = new Intent(context, MainActivity.class);
			mainIntent.setAction(Intent.ACTION_VIEW);
			mainIntent.setData(uri);
			mainIntent.putExtra("is_private", true);
			mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			context.startActivity(mainIntent);
		} catch (Exception e) {
			Log.e(TAG, "Error handling file intent: " + e.getMessage());
		}
	}

	// --- Lifecycle Methods ---

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_code_studio);

		initializeUI();
		initializeNavigation();
		initializeComponentManagers();
		initializeTabs();

		handleIntent(getIntent());
		setupBackPressHandling();

		mainHandler.postDelayed(() -> {
			CommandUpdater.checkForUpdates(this);
		}, 1000);
	}

	@Override
	protected void onResume() {
		super.onResume();
		isAutoSaveActive = true;
		mainHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(this);
		applyPreferences();
	}

	@Override
	protected void onPause() {
		super.onPause();
		isAutoSaveActive = false;
		mainHandler.removeCallbacks(autoSaveRunnable);
		performAutoSave(viewPager.getCurrentItem());

		executor.execute(() -> tabManager.saveOpenedTabs(this, viewPagerAdapter, tabLayout));

		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (isFinishing()) {
			closePrivateTabs();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		closePrivateTabs();
		executor.shutdown();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	// --- Initialization & Setup ---

	private void initializeUI() {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		drawerLayout = findViewById(R.id.drawerLayout);
		ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, DisplayManager::setupDynamicMarginHandling);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		progressBar = findViewById(R.id.progressBar);
		tabLayout = findViewById(R.id.tabLayout);
		viewPager = findViewById(R.id.viewPager2);
		viewPager.setUserInputEnabled(false);
		viewPager.setOffscreenPageLimit(3); // Cache a few pages for smoother transitions
	}

	private void initializeNavigation() {
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
		filesLoadingProgress = findViewById(R.id.filesLoadingProgress);

		TermuxInstaller.setupBootstrapIfNeeded(this, () -> requestStoragePermission(false));
	}

	private void initializeComponentManagers() {
		tabManager = new TabManager(this);
		restoreLastFolder();
	}

	private void initializeTabs() {
		executor.execute(() -> {
			TabManager.TabState state = tabManager.loadRecentTabs(this);
			runOnUiThread(() -> {
				viewPagerAdapter = new ViewPagerAdapter(this, state.uris(), state.names());
				viewPager.setAdapter(viewPagerAdapter);
				tabLayout.addOnTabSelectedListener(this);

				new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
					if (position < viewPagerAdapter.fileNames.size()) {
						tab.setText(viewPagerAdapter.fileNames.get(position));
					}
					applyTabPreferences(tab);
					setupTabLongClick(tab);
				}).attach();

				restoreActiveTab(state);
			});
		});
	}

	private void applyTabPreferences(TabLayout.Tab tab) {
		SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
		int textSize = prefs.getInt(AppPreferences.KEY_EDITOR_TEXT_SIZE, AppPreferences.DEFAULT_TEXT_SIZE);
		tab.view.post(() -> updateTabViews(tab.view, textSize));
	}

	private void setupTabLongClick(TabLayout.Tab tab) {
		tab.view.setOnLongClickListener(v -> {
			int currentPos = tab.getPosition();
			if (currentPos != -1 && currentPos < viewPagerAdapter.getItemCount()) {
				List<Uri> uris = viewPagerAdapter.getFileUris();
				if (uris != null && currentPos < uris.size()) {
					currentFileUri = uris.get(currentPos);
					currentMimeType = getMimeType(currentFileUri);
					showTabPopupMenu(v, currentPos);
				}
			}
			return true;
		});
	}

	private void restoreActiveTab(TabManager.TabState state) {
		if (state.activeTabIndex() != -1 && state.activeTabIndex() < viewPagerAdapter.getItemCount()) {
			viewPager.post(() -> {
				viewPager.setCurrentItem(state.activeTabIndex(), false);
				TabLayout.Tab savedTab = tabLayout.getTabAt(state.activeTabIndex());
				if (savedTab != null) savedTab.select();
			});
		}
	}

	// --- Preferences & Styling ---

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

	private void closePrivateTabs() {
		if (viewPagerAdapter != null) {
			for (int i = viewPagerAdapter.getItemCount() - 1; i >= 0; i--) {
				boolean isPrivate = (i < viewPagerAdapter.isPrivateTab.size() && viewPagerAdapter.isPrivateTab.get(i));
				Uri uri = viewPagerAdapter.fileUris.get(i);
				boolean isCompile = (uri != null && uri.toString().startsWith("app://com.cs.ide/compile"));
				if (isPrivate || isCompile) {
					viewPagerAdapter.removeTab(i);
				}
			}
		}
	}

	// --- Permissions ---

	public void requestStoragePermission(boolean isPermissionCallback) {
		executor.execute(() -> {
			int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
			if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(this, requestCode, !isPermissionCallback)) {
				if (isPermissionCallback) {
					runOnUiThread(() -> Toast.makeText(this, R.string.msg_storage_permission_granted_on_request, Toast.LENGTH_SHORT).show());
				}
				TermuxInstaller.setupStorageSymlinks(this);
			} else if (isPermissionCallback) {
				runOnUiThread(() -> Toast.makeText(this, R.string.msg_storage_permission_not_granted_on_request, Toast.LENGTH_SHORT).show());
			}
		});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
			requestStoragePermission(true);
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
			openSpecialTab(ViewPagerAdapter.WELCOME_URI, getString(R.string.welcome));
			return true;
		} else if (id == R.id.saveFiles) {
			handleSave();
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
		} else if (id == R.id.newFile) {
			showCreateFileDialog(null, 0);
			return true;
		} else if (id == R.id.newFolder) {
			showCreateFileDialog(null, 1);
			return true;
		} else if (id == R.id.saveAs) {
			handleSaveAs();
			return true;
		} else if (id == R.id.openNewFile) {
			openNewUntitledFile();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void openNewUntitledFile() {
		int count = 1;
		String baseName = getString(R.string.untitled);
		String name = baseName;
		Uri uri = Uri.parse("app://com.cs.ide/untitled/" + System.currentTimeMillis());

		// Find a unique name like Untitled 1, Untitled 2...
		while (viewPagerAdapter.findTabPositionByName(name) != -1) {
			name = baseName + " " + count++;
		}

		int index = viewPagerAdapter.addTab(uri, name);
		if (index != -1) {
			tabLayout.selectTab(tabLayout.getTabAt(index));
			viewPager.setCurrentItem(index, false);
		}
	}

	private void openSpecialTab(Uri uri, String name) {
		int index = viewPagerAdapter.addTab(uri, name);
		if (index != -1) {
			tabLayout.selectTab(tabLayout.getTabAt(index));
			viewPager.setCurrentItem(index, false);
		}
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
			} else if (id == R.id.rename_file) {
				showRenameDialog(fileItem);
			} else if (id == R.id.delete_file) {
				showDeleteConfirmationDialog(fileItem);
			} else if (id == R.id.import_file) {
				openFilePickerForImport(fileItem);
			} else if (id == R.id.run_file) {
				runFile(fileItem);
			} else {
				return false;
			}
			return true;
		});
		popupMenu.show();
	}

	// --- Tab Selection Callbacks ---

	@Override
	public void onTabSelected(@NonNull TabLayout.Tab tab) {
		int position = tab.getPosition();
		viewPager.setCurrentItem(position, false); // Disable animation to prevent back-and-forth glitch
		updateSubtitleForTab(position);

		Uri uri = (position < viewPagerAdapter.fileUris.size()) ? viewPagerAdapter.fileUris.get(position) : null;
		runMenuVisible = (uri != null && extensionAllowsRun(uri));

		if (uri != null) {
			setSelectedFileItem(FileUtils.getFileItemFromUri(this, uri));
			updateOpenedFolder(uri);
		}

		boolean isPrivate = (position < viewPagerAdapter.isPrivateTab.size() && viewPagerAdapter.isPrivateTab.get(position));
		drawerLayout.setDrawerLockMode(isPrivate ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);

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
		if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;

		Uri fileUri = intent.getData();
		if (fileUri == null) return;

		executor.execute(() -> {
			boolean isBinary = FileUtils.isBinaryFile(this, fileUri);
			runOnUiThread(() -> {
				if (isBinary) {
					Toast.makeText(this, R.string.msg_cannot_open_binary_file, Toast.LENGTH_SHORT).show();
					return;
				}

				boolean isPrivate = intent.getBooleanExtra("is_private", true);
				if (!isPrivate) {
					try {
						int takeFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
						if (takeFlags != 0 && "content".equals(fileUri.getScheme())) {
							getContentResolver().takePersistableUriPermission(fileUri, takeFlags);
						}
					} catch (Exception e) {
						Log.w(TAG, "Could not persist permissions: " + e.getMessage());
					}
				}

				String fileName = FileUtils.getFileName(this, fileUri);
				if (isPrivate) fileName += " " + getString(R.string.private_tab_suffix);

				int tabIndex = viewPagerAdapter.addTab(fileUri, fileName, isPrivate);
				if (tabIndex != -1) {
					viewPager.post(() -> {
						if (tabIndex < viewPagerAdapter.getItemCount()) {
							viewPager.setCurrentItem(tabIndex, false);
							TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
							if (tab != null) tab.select();
						}
					});
				}
				if (isPrivate) closeLeftNavigation();
			});
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
			requestStoragePermission(true);
			return;
		}

		if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
		Uri uri = data.getData();

		switch (requestCode) {
			case REQUEST_CODE_OPEN_DIRECTORY:
				handleDirectoryResult(uri, data.getFlags());
				break;
			case REQUEST_CODE_OPEN_FILE:
				handleFileOpenResult(uri, data.getFlags());
				break;
			case REQUEST_CODE_OPEN_FILE_FOR_IMPORT:
				if (importTargetFolder != null)
					showImportTargetFolderDialog(uri, importTargetFolder);
				break;
		}
	}

	private void handleDirectoryResult(Uri uri, int flags) {
		executor.execute(() -> {
			try {
				getContentResolver().takePersistableUriPermission(uri, flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
			} catch (Exception e) {
				Log.e(TAG, "Failed to take permission: " + e.getMessage());
			}
			folderUri = currentDirectoryUri = rootDirectoryUri = uri;
			saveLastFolder(uri);
			runOnUiThread(() -> setupFilesAdapter(uri));
		});
	}

	private void handleFileOpenResult(Uri uri, int flags) {
		executor.execute(() -> {
			try {
				getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} catch (Exception e) {
				Log.e(TAG, "Failed to take permission: " + e.getMessage());
			}
			String name = FileUtils.getFileName(this, uri);
			runOnUiThread(() -> {
				openFileInViewPager(uri, name);
				closeLeftNavigation();
			});
		});
	}

	// --- File Operations ---

	public void showCreateFileDialog(@Nullable FileItem baseItem, int initialType) {
		executor.execute(() -> {
			Uri parentUri = null;
			if (baseItem != null) {
				parentUri = baseItem.isDirectory ? baseItem.uri : getSafParentUri(baseItem.uri);
			} else if (rootDirectoryUri != null) {
				parentUri = rootDirectoryUri;
			}

			prepareFolderDataForDialog(parentUri);

			runOnUiThread(() -> {
				showCreateFileDialogInternal(new ArrayList<>(folderNames), new ArrayList<>(folderUris), initialType);
			});
		});
	}

	private void showCreateFileDialogInternal(List<String> names, List<Uri> uris, int initialType) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.create_new_item);
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
		LinearLayout mainLayout = (LinearLayout) dialogView;

		TextView typeLabel = new TextView(this);
		typeLabel.setText(R.string.item_type);
		typeLabel.setTextColor(ContextCompat.getColor(this, R.color.white));
		typeLabel.setTextSize(14);

		Spinner typeSpinner = new Spinner(this);
		String[] types = {getString(R.string.file), getString(R.string.folder)};
		ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_codestudio, types);
		typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		typeSpinner.setAdapter(typeAdapter);
		if (initialType >= 0) typeSpinner.setSelection(initialType);

		mainLayout.addView(typeLabel, 2);
		mainLayout.addView(typeSpinner, 3);

		EditText input = dialogView.findViewById(R.id.input_name);
		Spinner folderSpinner = dialogView.findViewById(R.id.spinner_folder);
		folderSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item_codestudio, names));

		builder.setView(dialogView);
		builder.setPositiveButton(R.string.create, (dialog, which) -> {
			String name = input.getText().toString().trim();
			if (name.isEmpty()) return;
			Uri selectedParentUri = uris.get(folderSpinner.getSelectedItemPosition());
			if (selectedParentUri == null)
				createInAppStorage(name, typeSpinner.getSelectedItemPosition() == 1);
			else
				createDocumentAsync(selectedParentUri, name, typeSpinner.getSelectedItemPosition() == 1);
		});
		builder.setNegativeButton(R.string.action_cancel, null).show();
	}

	private void createDocumentAsync(Uri parentUri, String originalName, boolean isFolder) {
		progressBar.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			Uri newDocumentUri = null;
			String finalNewName = originalName.replaceAll(" ", "_");
			Uri docUri = parentUri;

			try {
				// Check if we are dealing with a standard file URI (file://)
				if ("file".equalsIgnoreCase(parentUri.getScheme())) {
					File parentFile = new File(parentUri.getPath());
					File newFile = new File(parentFile, finalNewName);
					if (newFile.exists()) {
						throw new IOException("Item already exists at this location.");
					}

					boolean success = isFolder ? newFile.mkdirs() : newFile.createNewFile();
					if (success) {
						newDocumentUri = Uri.fromFile(newFile);
					} else {
						throw new IOException("Failed to create " + (isFolder ? "folder" : "file") + " using standard File API.");
					}
				} else {
					// Handling for SAF content URIs (content://)
					if (DocumentsContract.isTreeUri(parentUri) && !DocumentsContract.isDocumentUri(this, parentUri)) {
						docUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, DocumentsContract.getTreeDocumentId(parentUri));
					}

					String extension = "";
					int dotIndex = finalNewName.lastIndexOf('.');
					if (dotIndex >= 0) extension = finalNewName.substring(dotIndex + 1);

					String mimeType = isFolder ? DocumentsContract.Document.MIME_TYPE_DIR : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
					if (mimeType == null) mimeType = "application/octet-stream";

					newDocumentUri = DocumentsContract.createDocument(getContentResolver(), docUri, mimeType, finalNewName);
				}
			} catch (Exception e) {
				Log.e(TAG, "Creation failed for " + finalNewName + " at " + docUri, e);
				final String errorMessage = e.getMessage();
				final Uri failedDocUri = docUri;
				runOnUiThread(() -> {
					String path = FileUtils.getAbsolutePathFromUri(this, failedDocUri);
					if (path == null) path = FileUtils.getFileName(this, failedDocUri);
					new AlertDialog.Builder(this)
							.setTitle(R.string.title_creation_failed)
							.setMessage(getString(R.string.failed_to_create_item, finalNewName, path) + "\n\nError: " + errorMessage)
							.setPositiveButton(android.R.string.ok, null)
							.show();
				});
			}

			final Uri resultUri = newDocumentUri;
			final String resultName = finalNewName;
			runOnUiThread(() -> {
				progressBar.setVisibility(View.GONE);
				if (resultUri != null) {
					if (!isFolder)
						FilesAdapter.saveFileContentAsync(this, resultUri, "".getBytes());
					Toast.makeText(this, getString(isFolder ? R.string.folder_created_msg : R.string.file_created_msg, resultName), Toast.LENGTH_SHORT).show();
					if (!isFolder) openFileInViewPager(resultUri, resultName);
					mainHandler.postDelayed(this::refreshFileList, 500);
				}
			});
		});
	}

	private void showRenameDialog(FileItem fileItem) {
		DialogHelper.showRenameDialog(this, fileItem.displayName, newName -> {
			if (!fileItem.isDirectory) closeInViewPager(fileItem.uri);
			renameDocumentAsync(fileItem, newName);
		});
	}

	private void renameDocumentAsync(FileItem fileItem, String newName) {
		progressBar.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			Uri renamedUri = null;
			String cleanedName = newName.replaceAll(" ", "_");
			try {
				renamedUri = DocumentsContract.renameDocument(getContentResolver(), fileItem.uri, cleanedName);
			} catch (Exception e) {
				Log.e(TAG, "Rename failed", e);
			}

			final Uri resultUri = renamedUri;
			runOnUiThread(() -> {
				progressBar.setVisibility(View.GONE);
				if (resultUri != null) {
					Toast.makeText(this, R.string.renamed_to, Toast.LENGTH_SHORT).show();
					filesAdapter.updateFileItem(fileItem.uri, resultUri, cleanedName, fileItem.isDirectory, this);
					if (!fileItem.isDirectory) reopenClosedTab(resultUri, cleanedName);
					mainHandler.postDelayed(this::refreshFileList, 500);
				} else {
					Toast.makeText(this, R.string.failed_to_rename, Toast.LENGTH_SHORT).show();
					if (!fileItem.isDirectory && lastClosedTabState != null)
						reopenClosedTab(lastClosedTabState.uris().get(0), lastClosedTabState.names().get(0));
					refreshFileList();
				}
			});
		});
	}

	private void showDeleteConfirmationDialog(@NonNull FileItem fileItem) {
		DialogHelper.showDeleteConfirmationDialog(this, fileItem.displayName, () -> {
			if (!fileItem.isDirectory) closeInViewPager(fileItem.uri);
			deleteDocumentAsync(fileItem);
		});
	}

	private void deleteDocumentAsync(@NonNull FileItem fileItem) {
		executor.execute(() -> {
			try {
				if (DocumentsContract.deleteDocument(getContentResolver(), fileItem.uri)) {
					runOnUiThread(() -> {
						Toast.makeText(this, R.string.deleted_msg, Toast.LENGTH_SHORT).show();
						lastClosedTabState = null;
						mainHandler.postDelayed(this::refreshFileList, 500);
					});
				}
			} catch (Exception e) {
				Log.e(TAG, "Delete failed", e);
			}
		});
	}

	// --- File Explorer Management ---

	private void restoreLastFolder() {
		android.content.SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE);
		String uriString = prefs.getString(AppPreferences.LAST_FOLDER_URI_KEY, null);
		String pathString = prefs.getString(AppPreferences.LAST_FOLDER_PATH_KEY, null);

		executor.execute(() -> {
			boolean restored = false;
			if (uriString != null) {
				try {
					Uri lastFolder = Uri.parse(uriString);
					getContentResolver().takePersistableUriPermission(lastFolder,
							Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					folderUri = currentDirectoryUri = lastFolder;
					runOnUiThread(() -> setupFilesAdapter(lastFolder));
					restored = true;
				} catch (Exception e) {
					Log.w(TAG, "Could not restore folder via URI permission, likely lost on reinstall. Trying path...");
				}
			}

			if (!restored && pathString != null) {
				File folder = new File(pathString);
				if (folder.exists() && folder.isDirectory()) {
					Uri folderUriFromPath = Uri.fromFile(folder);
					folderUri = currentDirectoryUri = folderUriFromPath;
					runOnUiThread(() -> setupFilesAdapter(folderUriFromPath));
				}
			}
		});
	}

	public void updateOpenedFolder(Uri uri) {
		if (uri == null || uri.equals(ViewPagerAdapter.WELCOME_URI) || uri.toString().startsWith(ViewPagerAdapter.UNTITLED_URI_PREFIX))
			return;
		executor.execute(() -> {
			Uri folderToSave = FileUtils.isDirectory(this, uri) ? uri : getSafParentUri(uri);
			if (folderToSave != null) {
				saveLastFolder(folderToSave);
				String path = FileUtils.getAbsolutePathFromUri(this, folderToSave);
				if (path != null) {
					getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE).edit()
							.putString(AppPreferences.LAST_FOLDER_PATH_KEY, path).apply();
				}
			}
		});
	}

	private void saveLastFolder(Uri uri) {
		getSharedPreferences(AppPreferences.PREFERENCE_NAME, MODE_PRIVATE).edit()
				.putString(AppPreferences.LAST_FOLDER_URI_KEY, uri != null ? uri.toString() : null).apply();
	}

	private void setupFilesAdapter(Uri uri) {
		rootDirectoryUri = uri;
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
		filesLoadingProgress.setVisibility(View.VISIBLE);
		executor.execute(() -> populateFileList(folderUri, 0));
	}

	public void populateFileList(final Uri uri, final int depth) {
		try {
			final List<FileItem> folders = new ArrayList<>();
			final List<FileItem> files = new ArrayList<>();
			String documentId = "root";

			if ("file".equalsIgnoreCase(uri.getScheme())) {
				File dir = new File(uri.getPath());
				documentId = dir.getName();
				File[] children = dir.listFiles();
				if (children != null) {
					for (File child : children) {
						boolean isDir = child.isDirectory();
						String mime = isDir ? DocumentsContract.Document.MIME_TYPE_DIR : FileItem.resolveMimeType(this, Uri.fromFile(child));
						FileItem item = new FileItem(Uri.fromFile(child), child.getName(), isDir, depth, mime);
						if (isDir) folders.add(item);
						else files.add(item);
					}
				}
			} else if ("content".equalsIgnoreCase(uri.getScheme()) && DocumentsContract.isTreeUri(uri)) {
				documentId = DocumentsContract.getTreeDocumentId(uri);
				Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId);
				try (Cursor cursor = getContentResolver().query(childrenUri,
						new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
								DocumentsContract.Document.COLUMN_DISPLAY_NAME,
								DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
					if (cursor != null && cursor.moveToFirst()) {
						do {
							String id = cursor.getString(0);
							String name = cursor.getString(1);
							String mime = cursor.getString(2);
							Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, id);
							if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))
								folders.add(new FileItem(childUri, name, true, depth, mime));
							else files.add(new FileItem(childUri, name, false, depth, mime));
						} while (cursor.moveToNext());
					}
				}
			}
			final String fullPath = FileUtils.getAbsolutePathFromUri(this, uri);
			final String finalId = documentId;
			folders.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
			files.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

			runOnUiThread(() -> {
				String displayPath;
				if (fullPath != null) {
					displayPath = fullPath;
				} else {
					displayPath = finalId.startsWith("primary:") ? finalId.substring(8) : finalId;
					displayPath = getString(R.string.label_storage_prefix) + displayPath;
				}
				currentFolderTitle.setText(displayPath);
				fileItems.clear();
				fileItems.addAll(folders);
				fileItems.addAll(files);
				filesAdapter.notifyDataSetChanged();
				filesLoadingProgress.setVisibility(View.GONE);
			});
		} catch (Exception e) {
			Log.e(TAG, "Populate error", e);
			runOnUiThread(() -> filesLoadingProgress.setVisibility(View.GONE));
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

	// --- File Execution ---

	public void runFile(FileItem item) {
		int currentTabPos = viewPager.getCurrentItem();
		if (currentTabPos != -1) {
			performAutoSave(currentTabPos);
		}
		ExecutionManager.runFile(this, item);
	}

	private void handleRunFile() {
		if (selectedFileItem != null) runFile(selectedFileItem);
		else if (currentFileUri != null)
			runFile(new FileItem(this, currentFileUri, FileUtils.getFileName(this, currentFileUri), false, 0));
		else Toast.makeText(this, R.string.no_file_selected_to_run, Toast.LENGTH_SHORT).show();
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
		Uri uri = viewPagerAdapter.fileUris.get(position);
		if (uri.equals(ViewPagerAdapter.WELCOME_URI) || uri.toString().startsWith(ViewPagerAdapter.UNTITLED_URI_PREFIX))
			return;

		Fragment fragment = viewPagerAdapter.getFragment(position);
		if (fragment instanceof TextFragment textFragment && !textFragment.isSaved()) {
			// Fetch contents on UI thread but process saving in background
			byte[] content = textFragment.getContents();
			if (content != null && !viewPagerAdapter.fileNames.get(position).startsWith(getString(R.string.run_prefix, ""))) {
				executor.execute(() -> {
					FilesAdapter.saveFileContentAsync(this, uri, content);
					runOnUiThread(() -> {
						textFragment.setSaved(true);
						tabSaveTimes.put(uri.toString(), new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
						updateSubtitleForTab(position);
					});
				});
			}
		} else {
			updateSubtitleForTab(position);
		}
	}

	private void updateSubtitleForTab(int position) {
		if (getSupportActionBar() == null || viewPagerAdapter == null || position >= viewPagerAdapter.getItemCount())
			return;
		String lastSave = tabSaveTimes.get(viewPagerAdapter.fileUris.get(position).toString());
		getSupportActionBar().setSubtitle(lastSave != null ? getString(R.string.autosaved_at, lastSave) : getString(R.string.nothing_changed));
	}

	private void handleSave() {
		int currentTabPos = viewPager.getCurrentItem();
		if (currentTabPos != -1) {
			Fragment fragment = viewPagerAdapter.getFragment(currentTabPos);
			if (fragment instanceof TextFragment textFragment) {
				Uri uri = viewPagerAdapter.fileUris.get(currentTabPos);
				if (uri.toString().startsWith(ViewPagerAdapter.UNTITLED_URI_PREFIX)) {
					handleSaveAs();
				} else {
					byte[] content = textFragment.getContents();
					if (content != null) {
						FilesAdapter.saveFileContentAsync(this, uri, content);
						textFragment.setSaved(true);
						Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_SHORT).show();
					}
				}
			} else {
				Toast.makeText(this, R.string.msg_content_cannot_be_saved, Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(this, R.string.msg_no_tab_open_to_save, Toast.LENGTH_SHORT).show();
		}
	}

	private void saveAllOpenFiles() {
		if (viewPagerAdapter == null) return;
		boolean savedAny = false;
		for (int i = 0; i < viewPagerAdapter.getItemCount(); i++) {
			Fragment fragment = viewPagerAdapter.getFragment(i);
			if (fragment instanceof TextFragment textFragment && !textFragment.isSaved()) {
				byte[] content = textFragment.getContents();
				Uri uri = viewPagerAdapter.fileUris.get(i);
				if (content != null && uri != null && !uri.equals(ViewPagerAdapter.WELCOME_URI) && !uri.toString().startsWith(ViewPagerAdapter.UNTITLED_URI_PREFIX)) {
					FilesAdapter.saveFileContentAsync(this, uri, content);
					textFragment.setSaved(true);
					savedAny = true;
				}
			}
		}
		if (savedAny) {
			Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleSaveAs() {
		int currentTabPos = viewPager.getCurrentItem();
		if (currentTabPos != -1) {
			Fragment fragment = viewPagerAdapter.getFragment(currentTabPos);
			if (fragment instanceof TextFragment) {
				saveAsSourceUri = viewPagerAdapter.fileUris.get(currentTabPos);
				requestSaveAs(((TextFragment) fragment).getContents());
			} else
				Toast.makeText(this, R.string.msg_content_cannot_be_saved_save_as, Toast.LENGTH_SHORT).show();
		} else Toast.makeText(this, R.string.msg_no_tab_open_to_save, Toast.LENGTH_SHORT).show();
	}

	private void saveContentToFile(Uri uri, byte[] content, String name) {
		executor.execute(() -> {
			try (OutputStream os = ("file".equals(uri.getScheme())) ? new java.io.FileOutputStream(uri.getPath()) : getContentResolver().openOutputStream(uri)) {
				if (os != null) {
					os.write(content);
					runOnUiThread(() -> {
						if (saveAsSourceUri != null) {
							viewPagerAdapter.updateTabInfo(saveAsSourceUri, uri, name);
							saveAsSourceUri = null;
						} else {
							int untitled = viewPagerAdapter.findTabPositionByName(getString(R.string.untitled));
							if (untitled != -1) viewPagerAdapter.removeTab(untitled);
							openFileInViewPager(uri, name);
						}
						refreshFileList();
						Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_SHORT).show();
					});
				}
			} catch (IOException e) {
				runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_saving_file, e.getMessage()), Toast.LENGTH_LONG).show());
			}
		});
	}

	@Override
	public void requestSaveAs(byte[] content) {
		int currentTabPos = viewPager.getCurrentItem();
		if (currentTabPos == -1) return;

		Uri currentTabUri = viewPagerAdapter.fileUris.get(currentTabPos);
		String currentTabName = viewPagerAdapter.fileNames.get(currentTabPos);

		progressBar.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			prepareFolderDataForDialog();
			runOnUiThread(() -> {
				progressBar.setVisibility(View.GONE);
				if (currentDirectoryUri == null)
					Toast.makeText(this, R.string.open_folder_first, Toast.LENGTH_LONG).show();
				else if (folderUris.isEmpty())
					Toast.makeText(this, R.string.select_folder_with_permission, Toast.LENGTH_LONG).show();
				else
					CreateFileDialog.newInstance(folderUris, folderNames, content, currentTabName, currentTabUri).show(getSupportFragmentManager(), "SaveAsFileDialog");
			});
		});
	}

	// --- Tab Helpers ---

	public void openFileInViewPager(Uri uri, String name) {
		executor.execute(() -> {
			boolean isBinary = FileUtils.isBinaryFile(this, uri);
			runOnUiThread(() -> {
				if (isBinary) {
					Toast.makeText(this, R.string.msg_cannot_open_binary_file, Toast.LENGTH_SHORT).show();
					return;
				}
				int pos = viewPagerAdapter.addTab(uri, name);
				if (pos != -1) {
					viewPager.setCurrentItem(pos, false);
					invalidateOptionsMenu();
				}
			});
		});
	}

	public void closeInViewPager(Uri fileUri) {
		int index = viewPagerAdapter.fileUris.indexOf(fileUri);
		if (index != -1) {
			lastClosedTabState = new TabManager.TabState(java.util.Collections.singletonList(fileUri),
					java.util.Collections.singletonList(viewPagerAdapter.fileNames.get(index)), index);
			viewPagerAdapter.removeTab(index);
		}
	}

	public void closeFileInViewPager(Uri fileUri) {
		closeInViewPager(fileUri);
	}

	public void switchToTabByName(String name) {
		if (viewPagerAdapter == null) return;
		int pos = viewPagerAdapter.findTabPositionByName(name);
		if (pos != -1) {
			viewPager.setCurrentItem(pos, false);
			TabLayout.Tab tab = tabLayout.getTabAt(pos);
			if (tab != null) tab.select();
		}
	}

	public void reopenClosedTab(Uri newUri, String newName) {
		if (lastClosedTabState == null) return;
		int pos = Math.min(lastClosedTabState.activeTabIndex(), viewPagerAdapter.getItemCount());
		viewPagerAdapter.fileUris.add(pos, newUri);
		viewPagerAdapter.fileNames.add(pos, newName);
		viewPagerAdapter.notifyDataSetChanged();
		viewPager.setCurrentItem(pos);
		lastClosedTabState = null;
	}

	// --- Import ---

	public void openFilePickerForImport(@NonNull FileItem targetFileItem) {
		executor.execute(() -> {
			FileItem folder = targetFileItem.isDirectory ? targetFileItem : getParentFolderItem(targetFileItem);
			if (folder == null || folder.uri == null) return;
			this.importTargetFolder = folder;

			runOnUiThread(() -> {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("*/*");
				startActivityForResult(intent, REQUEST_CODE_OPEN_FILE_FOR_IMPORT);
			});
		});
	}

	public void showImportTargetFolderDialog(Uri sourceUri, FileItem targetFolder) {
		executor.execute(() -> {
			String sourceFileName = FileUtils.getFileName(this, sourceUri);
			List<FileItem> subFolders = getChildFolders(targetFolder.uri);
			List<String> names = new ArrayList<>();
			List<Uri> uris = new ArrayList<>();
			names.add(getString(R.string.default_prefix, targetFolder.displayName));
			uris.add(targetFolder.uri);
			for (FileItem f : subFolders) {
				names.add(f.displayName);
				uris.add(f.uri);
			}

			runOnUiThread(() -> {
				AlertDialog.Builder b = new AlertDialog.Builder(this);
				b.setTitle(getString(R.string.import_file_title, sourceFileName));
				View v = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
				EditText input = v.findViewById(R.id.input_name);
				Spinner s = v.findViewById(R.id.spinner_folder);
				input.setText(sourceFileName);
				s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
				b.setView(v);
				b.setPositiveButton(R.string.import_file, (d, w) -> importFileAsync(sourceUri, uris.get(s.getSelectedItemPosition()), input.getText().toString().trim()));
				b.setNegativeButton(R.string.action_cancel, null).show();
			});
		});
	}

	private void importFileAsync(Uri source, Uri targetFolder, String name) {
		executor.execute(() -> {
			try {
				String mime = getContentResolver().getType(source);
				if (mime == null)
					mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(name));
				Uri newUri = DocumentsContract.createDocument(getContentResolver(), targetFolder, mime != null ? mime : "application/octet-stream", name);
				if (newUri != null) {
					try (java.io.InputStream is = getContentResolver().openInputStream(source);
					     java.io.OutputStream os = getContentResolver().openOutputStream(newUri)) {
						byte[] buffer = new byte[8192];
						int read;
						while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
					}
					runOnUiThread(() -> {
						Toast.makeText(this, R.string.file_imported_successfully, Toast.LENGTH_SHORT).show();
						refreshFileList();
						openFileInViewPager(newUri, name);
					});
				}
			} catch (Exception e) {
				Log.e(TAG, "Import error", e);
			}
		});
	}

	// --- Storage Helpers ---

	private void createInAppStorage(String name, boolean isFolder) {
		executor.execute(() -> {
			File appStorageDir = new File(getFilesDir(), "code_studio_files");
			if (!appStorageDir.exists()) appStorageDir.mkdirs();
			File newFile = new File(appStorageDir, name.replaceAll(" ", "_"));

			if (newFile.exists()) {
				runOnUiThread(() -> Toast.makeText(this, R.string.msg_item_exists_in_app_storage, Toast.LENGTH_SHORT).show());
				return;
			}

			try {
				if (isFolder ? newFile.mkdirs() : newFile.createNewFile()) {
					Uri uri = Uri.fromFile(newFile);
					runOnUiThread(() -> {
						if (!isFolder) openFileInViewPager(uri, name);
						Toast.makeText(this, R.string.file_created_msg, Toast.LENGTH_SHORT).show();
					});
				}
			} catch (IOException e) {
				Log.e(TAG, "App storage creation failed", e);
			}
		});
	}

	public void openSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}

	public boolean openNewTerminal() {
		startActivity(new Intent(this, TermuxActivity.class));
		return true;
	}

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
		startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
	}

	// --- Callbacks ---

	@Override
	public void onFileClicked(Uri fileUri, String fileName) {
		executor.execute(() -> {
			boolean isBinary = FileUtils.isBinaryFile(this, fileUri);
			runOnUiThread(() -> {
				if (isBinary) {
					Toast.makeText(this, R.string.msg_cannot_open_binary_file, Toast.LENGTH_SHORT).show();
					return;
				}
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
					openFileInViewPager(fileUri, fileName);
					closeLeftNavigation();
				}
			});
		});
	}

	@Override
	public void onFileLongClick(View view, FileItem fileItem) {
		setSelectedFileItem(fileItem);
		showFileContextMenu(view, fileItem);
	}

	@Override
	public void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent) {
		if (fileContent != null) {
			saveContentToFile(fileUri, fileContent, fileName);
		} else {
			saveAsSourceUri = null; // Ensure we don't accidentally update a tab for a new file
			openFileInViewPager(fileUri, fileName);
		}
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
		return ExecutionManager.extensionAllowsRun(this, fileUri);
	}

	private Uri getSafParentUri(Uri childUri) {
		if ("file".equalsIgnoreCase(childUri.getScheme())) {
			File parent = new File(childUri.getPath()).getParentFile();
			return parent != null ? Uri.fromFile(parent) : null;
		}
		try {
			DocumentsContract.Path path = DocumentsContract.findDocumentPath(getContentResolver(), childUri);
			if (path == null || path.getPath().size() < 2) return null;
			return DocumentsContract.buildDocumentUriUsingTree(childUri, path.getPath().get(path.getPath().size() - 2));
		} catch (Exception e) {
			return null;
		}
	}

	private FileItem getParentFolderItem(FileItem fileItem) {
		Uri parentUri = fileItem.isDirectory ? fileItem.uri : getSafParentUri(fileItem.uri);
		return parentUri != null ? new FileItem(parentUri, getString(R.string.parent_directory), true, fileItem.depth - 1, DocumentsContract.Document.MIME_TYPE_DIR) : null;
	}

	@NonNull
	private List<FileItem> getChildFolders(Uri parentUri) {
		List<FileItem> folders = new ArrayList<>();
		if (parentUri == null || !"content".equals(parentUri.getScheme()) || !DocumentsContract.isTreeUri(parentUri)) {
			return folders;
		}
		String parentId = DocumentsContract.isDocumentUri(this, parentUri) ? DocumentsContract.getDocumentId(parentUri) : DocumentsContract.getTreeDocumentId(parentUri);
		Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId);
		try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
			if (cursor != null && cursor.moveToFirst()) {
				do {
					if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)))
						folders.add(new FileItem(this, DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(0)), cursor.getString(1), true, 0));
				} while (cursor.moveToNext());
			}
		} catch (Exception e) {
			Log.e(TAG, "Error listing child folders", e);
		}
		return folders;
	}

	private void setupBackPressHandling() {
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawer(GravityCompat.START);
				} else {
					if (System.currentTimeMillis() - lastBackPressTime < 2000) {
						finish();
					} else {
						lastBackPressTime = System.currentTimeMillis();
						Toast.makeText(MainActivity.this, R.string.msg_press_back_again_to_exit, Toast.LENGTH_SHORT).show();
					}
				}
			}
		});
	}

	public void prepareFolderDataForDialog() {
		prepareFolderDataForDialog(null);
	}

	public void prepareFolderDataForDialog(@Nullable Uri baseUri) {
		folderUris.clear();
		folderNames.clear();

		Uri topUri = baseUri != null ? baseUri : currentDirectoryUri;
		if (topUri == null) topUri = INTERNAL_STORAGE_URI;

		// 1. Add the primary/active folder at the top
		folderUris.add(topUri);
		if (topUri.equals(INTERNAL_STORAGE_URI)) {
			folderNames.add(getString(R.string.app_storage_default));
		} else {
			folderNames.add(getString(R.string.current_prefix, FileUtils.getFileName(this, topUri)));
		}

		// 2. Add App Storage as an option if it wasn't the top one
		if (!topUri.equals(INTERNAL_STORAGE_URI)) {
			folderUris.add(INTERNAL_STORAGE_URI);
			folderNames.add(getString(R.string.app_storage_default));
		}

		// 3. If there's an opened root folder different from topUri, add it
		if (currentDirectoryUri != null && !currentDirectoryUri.equals(topUri) && !currentDirectoryUri.equals(INTERNAL_STORAGE_URI)) {
			folderUris.add(currentDirectoryUri);
			folderNames.add(getString(R.string.current_prefix, FileUtils.getFileName(this, currentDirectoryUri)));
		}

		// 4. Add subfolders of the opened root folder
		Uri folderToScan = currentDirectoryUri != null ? currentDirectoryUri : (topUri.equals(INTERNAL_STORAGE_URI) ? null : topUri);
		if (folderToScan != null) {
			if ("content".equals(folderToScan.getScheme()) && DocumentsContract.isTreeUri(folderToScan)) {
				try {
					String parentId = DocumentsContract.isDocumentUri(this, folderToScan) ? DocumentsContract.getDocumentId(folderToScan) : DocumentsContract.getTreeDocumentId(folderToScan);
					Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderToScan, parentId);
					try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
						if (cursor != null && cursor.moveToFirst()) {
							do {
								if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
									Uri childUri = DocumentsContract.buildDocumentUriUsingTree(folderToScan, cursor.getString(0));
									if (!folderUris.contains(childUri)) {
										folderUris.add(childUri);
										folderNames.add(cursor.getString(1));
									}
								}
							} while (cursor.moveToNext());
						}
					}
				} catch (Exception e) {
					Log.e(TAG, "Error preparing subfolders from SAF", e);
				}
			} else if ("file".equals(folderToScan.getScheme())) {
				try {
					File dir = new File(folderToScan.getPath());
					if (dir.exists() && dir.isDirectory()) {
						File[] children = dir.listFiles();
						if (children != null) {
							for (File child : children) {
								if (child.isDirectory()) {
									Uri childUri = Uri.fromFile(child);
									if (!folderUris.contains(childUri)) {
										folderUris.add(childUri);
										folderNames.add(child.getName());
									}
								}
							}
						}
					}
				} catch (Exception e) {
					Log.e(TAG, "Error preparing subfolders from File API", e);
				}
			}
		}
	}
}
