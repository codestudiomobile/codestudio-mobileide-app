package com.cs.ide.app;

import static com.cs.ide.app.AppPreferences.CURRENT_TAB;
import static com.cs.ide.app.AppPreferences.LAST_FOLDER_URI_KEY;
import static com.cs.ide.app.AppPreferences.PREFERENCE_NAME;
import static com.cs.ide.app.AppPreferences.TAB_NAME_KEY;
import static com.cs.ide.app.AppPreferences.TAB_URI_KEY;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
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
import com.cs.ide.termux.app.TermuxActivity;
import com.cs.ide.termux.app.TermuxInstaller;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener, FilesAdapter.OnFileClickListener, CreateFileDialog.OnFileCreatedListener, TerminalFragment.ConsoleInputListener {
    private static final int REQUEST_CODE_OPEN_FILE_FOR_IMPORT = 1002;
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final int REQUEST_CODE_OPEN_FILE = 2001;
    private static final String TAG = "MainActivity";
    public static Uri currentDirectoryUri = null;
    public static ViewPagerAdapter viewPagerAdapter;
    public final ArrayList<Uri> folderUris = new ArrayList<>();
    public final ArrayList<String> folderNames = new ArrayList<>();
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final int AUTO_SAVE_INTERVAL_MS = 30000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<FileItem> fileItems = new ArrayList<>();
    public Uri currentFileUri;
    public String currentMimeType;
    private FileItem importTargetFolder;
    private Uri rootDirectoryUri = null;
    private SharedPreferences preferences;
    private TerminalFragment terminalFragment;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            int currentTabPosition = viewPager.getCurrentItem();
            if (viewPagerAdapter != null && currentTabPosition != -1 && currentTabPosition < viewPagerAdapter.getItemCount()) {
                Uri uri = viewPagerAdapter.getFileUris().get(currentTabPosition);
                long itemId = viewPagerAdapter.getItemId(currentTabPosition);
                String fragmentTag = "f" + itemId;
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(fragmentTag);
                if (fragment instanceof TextFragment) {
                    TextFragment textFragment = (TextFragment) fragment;
                    byte[] content;
                    if (!uri.equals(ViewPagerAdapter.WELCOME_URI) && !uri.equals(ViewPagerAdapter.UNTITLED_FILE_URI) && !viewPagerAdapter.getFileNames().get(currentTabPosition).startsWith("Run:") && textFragment.isSaved() && (content = textFragment.getContents()) != null) {
                        FilesAdapter.saveFileContentAsync(MainActivity.this, uri, content);
                        textFragment.setSaved(true);
                    }
                }
            }
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
        }
    };
    private DrawerLayout drawerLayout;
    private RecyclerView filesList;
    private FilesAdapter filesAdapter;
    private TextView currentFolderTitle;
    private ImageButton refreshFolder;
    private ImageButton collapseAllFolders;
    private boolean runMenuVisible = false;
    private boolean editMenuVisible = false;
    private boolean stopMenuVisible = false;
    private Uri folderUri = null;
    private FileItem selectedFileItem;
    private ProgressBar progressBar;
    private TabState lastClosedTabState = null;

    private static boolean isIsExternalViewType(String mimeType) {
        boolean isExternalViewType = false;
        if (mimeType != null) {
            String lowerMimeType = mimeType.toLowerCase();
            if (lowerMimeType.startsWith("image/") || lowerMimeType.startsWith("video/") || lowerMimeType.startsWith("audio/") ||
                    lowerMimeType.equals("application/pdf") ||
                    lowerMimeType.contains("msword") ||
                    lowerMimeType.contains("vnd.openxmlformats-officedocument") ||
                    lowerMimeType.contains("zip") ||
                    lowerMimeType.contains("rar") || lowerMimeType.contains("octet-stream")) {
                isExternalViewType = true;
            }
        }
        return isExternalViewType;
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (fileName == null && uri.getLastPathSegment() != null) {
            fileName = uri.getLastPathSegment();
        }
        return (fileName != null && !fileName.isEmpty()) ? fileName : "untitled";
    }

    public static String getFileTypeKey(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "text";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            switch (extension) {
                case "py":
                    return "py";
                case "java":
                    return "java";
                case "c":
                    return "c";
                case "cpp":
                    return "cpp";
                case "js":
                case "ts":
                    return "node";
                case "html":
                    return "html";
                case "sh":
                    return "sh";
                default:
                    return extension;
            }
        }
        return "text";
    }

    public static void handleFileIntent(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            Log.d(TAG, "Handling file intent in static method for URI: " + uri);
            try {
                String fileName = getFileNameFromUri(context, uri);
                String fileTypeKey = getFileTypeKey(fileName);
                Toast.makeText(context, "Opening file: " + fileName + " (Type: " + fileTypeKey + ")", Toast.LENGTH_LONG).show();
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.setAction(Intent.ACTION_VIEW);
                mainIntent.setData(uri);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(mainIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error handling file intent: " + e.getMessage());
                Toast.makeText(context, "Could not open file.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean extensionAllowsRun(Uri fileUri) {
        String last = fileUri.getLastPathSegment();
        if (last == null) return false;
        String lower = last.toLowerCase();
        return lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".html") || lower.endsWith(".xml") || lower.endsWith(".rb") || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".php") || lower.endsWith(".sh") || lower.endsWith(".swift") || lower.endsWith(".kt") || lower.endsWith(".scala") || lower.endsWith(".pl") || lower.endsWith(".lua") || lower.endsWith(".sql") || lower.endsWith(".r") || lower.endsWith(".dart") || lower.endsWith(".cs");
    }

    public void prepareFolderDataForDialog() {
        folderUris.clear();
        folderNames.clear();
        folderUris.add(null);
        folderNames.add("App Storage (Default)");
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Handler(Looper.getMainLooper()).postDelayed(() -> CommandUpdater.checkForUpdates(this), 1000);
        setContentView(R.layout.activity_main_code_studio);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        drawerLayout = findViewById(R.id.drawerLayout);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, DisplayManager::setupDynamicMarginHandling);
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
        });
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        NavigationView leftNavigation = findViewById(R.id.leftNavigation);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        View headerView = leftNavigation.getHeaderView(0);
        currentFolderTitle = headerView.findViewById(R.id.currentFolderTitle);
        refreshFolder = headerView.findViewById(R.id.refreshFilesFolders);
        collapseAllFolders = headerView.findViewById(R.id.collapseAllFolders);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager2);
        viewPager.setUserInputEnabled(false);
        restoreLastFolder();
        TabState state = loadRecentTabs();
        viewPagerAdapter = new ViewPagerAdapter(this, state.uriList, state.namesList);
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.addOnTabSelectedListener(this);
        progressBar = findViewById(R.id.progressBar);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(viewPagerAdapter.fileNames.get(position));
            tab.view.setOnLongClickListener(v -> {
                currentFileUri = viewPagerAdapter.getFileUris().get(position);
                currentMimeType = getMimeType(viewPagerAdapter.fileUris.get(position));
                showPopupMenu(v, position);
                return true;
            });
        }).attach();
        if (state.currentTab != -1 && state.currentTab < viewPagerAdapter.getItemCount()) {
            viewPager.setCurrentItem(state.currentTab, false);
            tabLayout.selectTab(tabLayout.getTabAt(state.currentTab));
        }
        handleIntent(getIntent());
        terminalFragment = null;
        preferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
        boolean showEditor = preferences.getBoolean("openEditorOnStartup", false);
        boolean showWelcome = preferences.getBoolean("openWelcomeScreenOnStartup", true);
        if (viewPagerAdapter.getItemCount() == 0) {
            int welcomeIndex = -1;
            int editorIndex = -1;
            if (showWelcome) {
                welcomeIndex = viewPagerAdapter.addTab(ViewPagerAdapter.WELCOME_URI, "Welcome");
            }
            if (showEditor) {
                editorIndex = viewPagerAdapter.addTab(ViewPagerAdapter.UNTITLED_FILE_URI, "Untitled");
            }
            int defaultIndex = (editorIndex != -1) ? editorIndex : welcomeIndex;
            if (defaultIndex != -1) {
                viewPager.setCurrentItem(defaultIndex, false);
                tabLayout.selectTab(tabLayout.getTabAt(defaultIndex));
            }
        }
    }

    @NonNull
    @Contract(" -> new")
    private TabState loadRecentTabs() {
        String jsonUris = preferences.getString(TAB_URI_KEY, null);
        String jsonNames = preferences.getString(TAB_NAME_KEY, null);
        int currentTab = preferences.getInt(CURRENT_TAB, -1);
        List<Uri> uriList = new ArrayList<>();
        List<String> namesList = new ArrayList<>();
        if (jsonUris == null || jsonNames == null) {
            Log.d(TAG, "No saved tabs found");
            return new TabState(uriList, namesList, -1);
        }
        try {
            Gson gson = new Gson();
            Type listStringType = new TypeToken<List<String>>() {
            }.getType();
            namesList = gson.fromJson(jsonNames, listStringType);
            List<String> uriStringList = gson.fromJson(jsonUris, listStringType);
            for (String uriString : uriStringList) {
                uriList.add(Uri.parse(uriString));
            }
            if (uriList.size() != namesList.size()) {
                Log.d(TAG, "Loaded lists are mismatched ");
                return new TabState(new ArrayList<>(), new ArrayList<>(), -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved tabs", e);
            return new TabState(new ArrayList<>(), new ArrayList<>(), -1);
        }
        return new TabState(uriList, namesList, currentTab);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void showPopupMenu(View view, int position) {
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

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri fileUri = intent.getData();
            if (fileUri != null) {
                String fileName = getFileName(fileUri);
                int tabIndex = viewPagerAdapter.addTab(fileUri, fileName);
                if (tabIndex != -1) {
                    tabLayout.selectTab(tabLayout.getTabAt(tabIndex));
                    viewPager.setCurrentItem(tabIndex);
                }
            }
        }
    }

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
        if (runItem != null) {
            runItem.setVisible(runMenuVisible);
        }
        if (stopExecutionItem != null) {
            stopExecutionItem.setVisible(stopMenuVisible);
        }
        if (editItem != null) {
            editItem.setVisible(editMenuVisible);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.runFile) {
            if (selectedFileItem != null) {
                runFile(selectedFileItem);
                return true;
            } else {
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (id == R.id.openNewTerminal) {
            return openNewTerminal();
        } else if (id == R.id.editFile) {
            int currentTabPos = tabLayout.getSelectedTabPosition();
            if (currentTabPos != -1) {
                String currentTabName = viewPagerAdapter.fileNames.get(currentTabPos);
                if (currentTabName.startsWith("Running")) {
                    String originalFileName = currentTabName.substring("Running".length());
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
            return true;
        } else if (id == R.id.openWelcomeScreen) {
            int newTabIndex = viewPagerAdapter.addTab(ViewPagerAdapter.WELCOME_URI, "Welcome");
            if (newTabIndex != -1) {
                tabLayout.selectTab(tabLayout.getTabAt(newTabIndex));
            }
            return true;
        } else if (id == R.id.saveFiles) {
            List<FilesAdapter.FileContentItem> filesToSave = viewPagerAdapter.getOpenFilesContent();
            if (filesAdapter != null && !filesToSave.isEmpty()) {
                filesAdapter.saveAllFiles(filesToSave);
            }
        } else if (id == R.id.settings) {
            openSettings();
            return true;
        } else if (id == R.id.about) {
            Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
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
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFileLongClick(View view, FileItem fileItem) {
        setSelectedFileItem(fileItem);
        showFileContextMenu(view, fileItem);
    }

    public void showFileContextMenu(View view, @NonNull FileItem fileItem) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.file_menu, popupMenu.getMenu());
        Menu menu = popupMenu.getMenu();
        if (fileItem.isDirectory) {
            menu.findItem(R.id.run_file).setVisible(false);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.new_file_folder) {
                showCreateFileDialog(fileItem);
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
                Toast.makeText(this, "Run file operation is for future implementation.", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void refreshFilesList(@NonNull Uri folderUri) {
        Toast.makeText(this, "File list refresh triggered for: " + folderUri, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private List<FileItem> getChildFolders(Uri parentUri) {
        List<FileItem> folders = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri));
        try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        Uri childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId);
                        FileItem folderItem = new FileItem(this, childUri, displayName, true, 0);
                        folders.add(folderItem);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error listing child folders", e);
        }
        return folders;
    }

    private String getMimeTypeFromExtension(String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            return mimeType != null ? mimeType : "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private String getFileNameFromUri(Uri uri) {
        String displayName = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error getting display name from Uri", e);
        }
        return displayName;
    }

    private FileItem getParentFolderItem(FileItem fileItem) {
        Uri parentUri = fileItem.isDirectory ? fileItem.uri : getSafParentUri(fileItem.uri);
        if (parentUri != null) {
            return new FileItem(parentUri, "Parent Directory", true, fileItem.depth - 1, DocumentsContract.Document.MIME_TYPE_DIR);
        }
        return null;
    }

    private void createDocumentWithConflictHandlingAsync(Uri parentUri, String originalName, boolean isFolder) {
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
        new Thread(() -> {
            Uri newDocumentUri = null;
            int conflictCount = 1;
            boolean success = false;
            String finalNewName = originalName;
            while (!success && conflictCount <= 10) {
                try {
                    if (conflictCount > 1) {
                        finalNewName = getNextConflictName(originalName, conflictCount);
                    }
                    String mimeType = isFolder ? DocumentsContract.Document.MIME_TYPE_DIR : getMimeTypeFromExtension(finalNewName);
                    newDocumentUri = DocumentsContract.createDocument(getContentResolver(), parentUri, mimeType, finalNewName);
                    if (newDocumentUri != null) {
                        success = true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Creation attempt " + conflictCount + " failed: " + e.getMessage());
                }
                conflictCount++;
            }
            final Uri finalNewDocumentUri = newDocumentUri;
            final String finalFinalNewName = finalNewName;
            final int finalConflictCount = conflictCount - 1;
            runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            if (finalNewDocumentUri != null) {
                if (!isFolder) {
                    FilesAdapter.saveFileContentAsync(this, finalNewDocumentUri, "".getBytes());
                }
                runOnUiThread(() -> {
                    String message = (isFolder ? "Folder" : "File") + " created: " + finalFinalNewName;
                    if (finalConflictCount > 1) {
                        message += " (Auto-resolved)";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (!isFolder) {
                        openFileInViewPager(finalNewDocumentUri, finalFinalNewName);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to create " + (isFolder ? "folder" : "file") + ". Name conflict could not be resolved or permission denied.", Toast.LENGTH_LONG).show();
                });
            }
            runOnUiThread(() -> filesAdapter.refresh());
        }).start();
    }

    public void showCreateFileDialog(@NonNull FileItem baseItem) {
        Uri parentUri = baseItem.isDirectory ? baseItem.uri : getSafParentUri(baseItem.uri);
        if (parentUri == null) {
            Toast.makeText(this, "Cannot determine parent folder.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<FileItem> subFolders = getChildFolders(parentUri);
        String currentFolderName = baseItem.isDirectory ? baseItem.displayName : getFileNameFromUri(parentUri);
        currentFolderName = currentFolderName != null ? currentFolderName : "Current Folder";
        List<String> folderNames = new ArrayList<>();
        List<Uri> folderUris = new ArrayList<>();
        folderNames.add("Current: " + currentFolderName);
        folderUris.add(parentUri);
        for (FileItem folder : subFolders) {
            folderNames.add(folder.displayName);
            folderUris.add(folder.uri);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Item");
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
        LinearLayout mainLayout = (LinearLayout) dialogView;
        TextView typeLabel = new TextView(this);
        typeLabel.setText("Item Type:");
        typeLabel.setTextAppearance(this, android.R.style.TextAppearance_Small);
        Spinner typeSpinner = new Spinner(this);
        String[] types = {"File", "Folder"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(0);
        mainLayout.addView(typeLabel, 2);
        mainLayout.addView(typeSpinner, 3);
        EditText input = dialogView.findViewById(R.id.input_name);
        Spinner folderSpinner = dialogView.findViewById(R.id.spinner_folder);
        builder.setView(dialogView);
        ArrayAdapter<String> folderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames);
        folderSpinner.setAdapter(folderAdapter);
        folderSpinner.setSelection(0);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String newNameInput = input.getText().toString().trim();
            if (newNameInput.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            String newName = newNameInput.replaceAll(" ", "_");
            boolean isFolder = typeSpinner.getSelectedItemPosition() == 1;
            int selectedPosition = folderSpinner.getSelectedItemPosition();
            Uri targetFolderUri = folderUris.get(selectedPosition);
            createDocumentWithConflictHandlingAsync(targetFolderUri, newName, isFolder);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private boolean doesFileExist(Uri parentUri, String displayName) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri));
        String[] projection = new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        String selection = DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{displayName};
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, selection, selectionArgs, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking file existence", e);
            return false;
        }
    }

    private String getUniqueFileName(Uri parentUri, String originalName) {
        if (!doesFileExist(parentUri, originalName)) {
            return originalName;
        }
        String name = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
            name = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }
        int counter = 2;
        String newName;
        while (true) {
            newName = name + counter + extension;
            if (!doesFileExist(parentUri, newName)) {
                return newName;
            }
            counter++;
            if (counter > 999) return name + "_" + System.currentTimeMillis() + extension;
        }
    }

    private Uri findChildUriByName(Uri parentUri, String displayName) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri));
        String[] projection = new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID};
        String selection = DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{displayName};
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String documentId = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                return DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error finding child URI by name", e);
        }
        return null;
    }

    private void handleConflict(Uri targetFolderUri, String existingName, boolean isFolder, String sourceUriString, String originalInputName) {
        String operation = (sourceUriString != null) ? "Import" : (isFolder ? "Create Folder" : "Create File");
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(operation + " Conflict").setMessage("The name '" + existingName + "' already exists in this folder. What would you like to do?").setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss()).setPositiveButton("Rename Automatically", (dialog, which) -> {
            String uniqueName = getUniqueFileName(targetFolderUri, existingName);
            if (sourceUriString != null) {
                importFileAsync(Uri.parse(sourceUriString), targetFolderUri, uniqueName);
            } else {
                String mimeType = isFolder ? DocumentsContract.Document.MIME_TYPE_DIR : getMimeTypeFromExtension(uniqueName);
                createDocumentAsync(targetFolderUri, mimeType, uniqueName, isFolder);
            }
        });
        if (!isFolder) {
            builder.setNegativeButton("Overwrite", (dialog, which) -> {
                Uri existingFileUri = findChildUriByName(targetFolderUri, existingName);
                if (existingFileUri != null) {
                    if (sourceUriString != null) {
                        importFileOverwriteAsync(Uri.parse(sourceUriString), existingFileUri, originalInputName);
                    } else {
                        FilesAdapter.saveFileContentAsync(this, existingFileUri, "".getBytes());
                        refreshFilesList(targetFolderUri);
                        Toast.makeText(this, "File '" + existingName + "' overwritten.", Toast.LENGTH_LONG).show();
                        openFileInViewPager(existingFileUri, existingName);
                    }
                } else {
                    Toast.makeText(this, "Failed to locate existing file for overwrite.", Toast.LENGTH_LONG).show();
                }
            });
        }
        builder.show();
    }

    private void createDocumentAsync(Uri parentUri, String mimeType, String displayName, boolean isFolder) {
        new Thread(() -> {
            try {
                Uri newDocumentUri = DocumentsContract.createDocument(getContentResolver(), parentUri, mimeType, displayName);
                if (newDocumentUri != null) {
                    if (!isFolder) {
                        FilesAdapter.saveFileContentAsync(this, newDocumentUri, "".getBytes());
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, (isFolder ? "Folder" : "File") + " created: " + displayName, Toast.LENGTH_LONG).show();
                        refreshFilesList(parentUri);
                        if (!isFolder) {
                            openFileInViewPager(newDocumentUri, displayName);
                        }
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to create " + (isFolder ? "folder" : "file"), Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error creating document: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void renameDocumentAsync(FileItem fileItem, String newName) {
        Uri oldUri = fileItem.uri;
        boolean wasFile = !fileItem.isDirectory;
        final String originalNewName = newName.replaceAll(" ", "_");
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Renaming...", Toast.LENGTH_SHORT).show();
        });
        new Thread(() -> {
            Uri renamedUri = null;
            int conflictCount = 1;
            boolean success = false;
            String finalNewName = originalNewName;
            while (!success && conflictCount <= 10) {
                try {
                    if (conflictCount > 1) {
                        finalNewName = getNextConflictName(originalNewName, conflictCount);
                    }
                    renamedUri = DocumentsContract.renameDocument(getContentResolver(), oldUri, finalNewName);
                    if (renamedUri != null) {
                        success = true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Rename attempt " + conflictCount + " failed: " + e.getMessage());
                }
                conflictCount++;
            }
            final Uri finalRenamedUri = renamedUri;
            final String finalFinalNewName = finalNewName;
            final int finalConflictCount = conflictCount - 1;
            runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            if (finalRenamedUri != null) {
                runOnUiThread(() -> {
                    String message = "Renamed to: " + finalFinalNewName;
                    if (finalConflictCount > 1) {
                        message += " (Auto-resolved)";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    filesAdapter.updateFileItem(oldUri, finalRenamedUri, finalFinalNewName, fileItem.isDirectory, this);
                    if (wasFile) {
                        reopenClosedTab(finalRenamedUri, finalFinalNewName);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to rename '" + fileItem.displayName + "'. File may not exist or name conflict could not be resolved.", Toast.LENGTH_LONG).show();
                    if (wasFile && lastClosedTabState != null) {
                        reopenClosedTab(lastClosedTabState.oldUri, lastClosedTabState.name);
                    }
                    lastClosedTabState = null;
                    filesAdapter.refresh();
                });
            }
        }).start();
    }

    public void closeFileInViewPager(Uri fileUri) {
        if (viewPagerAdapter != null && viewPager != null) {
            int index = viewPagerAdapter.fileUris.indexOf(fileUri);
            if (index != -1) {
                viewPagerAdapter.fileUris.remove(index);
                viewPagerAdapter.fileNames.remove(index);
                viewPagerAdapter.notifyDataSetChanged();
                int newPosition = Math.min(index, viewPagerAdapter.getItemCount() - 1);
                if (newPosition >= 0) {
                    viewPager.setCurrentItem(newPosition);
                }
            }
        }
    }

    public void updateFileInViewPager(Uri oldUri, Uri newUri, String newName) {
        if (viewPagerAdapter != null && viewPager != null) {
            int index = viewPagerAdapter.fileUris.indexOf(oldUri);
            if (index != -1) {
                viewPagerAdapter.fileUris.set(index, newUri);
                viewPagerAdapter.fileNames.set(index, newName);
                viewPagerAdapter.notifyDataSetChanged();
                viewPager.setCurrentItem(index);
            }
        }
    }

    private void importFileAsync(Uri sourceUri, Uri targetFolderUri, String newName) {
        new Thread(() -> {
            Uri newDocumentUri = null;
            try {
                String mimeType = getContentResolver().getType(sourceUri);
                if (mimeType == null) {
                    mimeType = getMimeTypeFromExtension(newName);
                }
                newDocumentUri = DocumentsContract.createDocument(getContentResolver(), targetFolderUri, mimeType,
                        newName);
                if (newDocumentUri != null) {
                    try (java.io.InputStream inputStream = getContentResolver().openInputStream(sourceUri);
                         java.io.OutputStream outputStream = getContentResolver().openOutputStream(newDocumentUri, "w")) {
                        if (inputStream == null) {
                            throw new IOException("Failed to open input stream for source URI.");
                        }
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            assert outputStream != null;
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    Uri finalNewDocumentUri = newDocumentUri;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "File imported successfully: " + newName, Toast.LENGTH_LONG).show();
                        refreshFilesList(targetFolderUri);
                        openFileInViewPager(finalNewDocumentUri, newName);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to create new document for import.", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error during file import: " + e.getMessage(), e);
                if (newDocumentUri != null) {
                    try {
                        DocumentsContract.deleteDocument(getContentResolver(), newDocumentUri);
                    } catch (Exception deleteException) {
                        Log.e("MainActivity", "Failed to delete partially created document: " + deleteException.getMessage());
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "Error during import: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void importFileOverwriteAsync(Uri sourceUri, Uri targetFileUri, String fileName) {
        new Thread(() -> {
            try {
                try (java.io.InputStream inputStream = getContentResolver().openInputStream(sourceUri); java.io.OutputStream outputStream = getContentResolver().openOutputStream(targetFileUri, "wt")) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, "File '" + fileName + "' overwritten successfully.", Toast.LENGTH_LONG).show();
                        Uri parentUri = getSafParentUri(targetFileUri);
                        if (parentUri != null) refreshFilesList(parentUri);
                        openFileInViewPager(targetFileUri, fileName);
                    });
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error during file overwrite: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Error during overwrite: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showRenameDialog(FileItem fileItem) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(fileItem.displayName);
        new AlertDialog.Builder(this).setTitle("Rename").setView(input).setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty() || newName.equals(fileItem.displayName)) {
                return;
            }
            if (!fileItem.isDirectory) {
                closeFileAndSaveState(fileItem.uri);
            }
            renameDocumentAsync(fileItem, newName);
        }).setNegativeButton("Cancel", null).show();
    }

    private String getNextConflictName(String baseName, int conflictCount) {
        String name = baseName;
        String extension = "";
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = baseName.substring(dotIndex);
            name = baseName.substring(0, dotIndex);
        }
        name = name.replaceAll(" ", "_");
        name = name.replaceAll("_\\d+$", "");
        return name + "_" + conflictCount + extension;
    }

    private void showDeleteConfirmationDialog(@NonNull FileItem fileItem) {
        new AlertDialog.Builder(this).setTitle("Delete " + (fileItem.isDirectory ? "Folder" : "File")).setMessage("Are you sure you want to delete '" + fileItem.displayName + "'? This cannot be undone.").setPositiveButton("Delete", (dialog, which) -> {
            if (!fileItem.isDirectory) {
                closeFileAndSaveState(fileItem.uri);
            }
            deleteDocumentAsync(fileItem);
        }).setNegativeButton("Cancel", null).show();
    }

    public void openFilePickerForImport(@NonNull FileItem targetFileItem) {
        FileItem folderToImportInto = targetFileItem.isDirectory ? targetFileItem : getParentFolderItem(targetFileItem);
        if (folderToImportInto == null || folderToImportInto.uri == null) {
            Toast.makeText(this, "Cannot determine target folder for import.", Toast.LENGTH_SHORT).show();
            return;
        }
        this.importTargetFolder = folderToImportInto;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        String[] mimeTypes = {"text/*", "application/json", "application/xml", "application/javascript", "application/x-java-source", "text/x-csrc", "text/x-c++src", "text/x-python", "image/*", "audio/*", "video/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE_FOR_IMPORT);
    }

    private void deleteDocumentAsync(@NonNull FileItem fileItem) {
        Uri fileUri = fileItem.uri;
        new Thread(() -> {
            try {
                boolean success = DocumentsContract.deleteDocument(getContentResolver(), fileUri);
                if (success) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Deleted: " + fileItem.displayName, Toast.LENGTH_LONG).show();
                        lastClosedTabState = null;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> filesAdapter.refresh(), 500);
                    });
                } else {
                    lastClosedTabState = null;
                    runOnUiThread(() -> Toast.makeText(this, "Failed to delete.", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                lastClosedTabState = null;
                Log.e("MainActivity", "Error deleting document: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private Uri getSafParentUri(Uri childUri) {
        if (childUri == null || !DocumentsContract.isDocumentUri(this, childUri)) {
            return null;
        }
        try {
            String childDocumentId = DocumentsContract.getDocumentId(childUri);
            DocumentsContract.Path path = DocumentsContract.findDocumentPath(getContentResolver(), childUri);
            if (path == null) {
                Log.e("MainActivity", "findDocumentPath returned null for " + childDocumentId);
                return null;
            }
            List<String> pathSegments = path.getPath();
            if (pathSegments.size() < 2) {
                return null;
            }
            String parentDocumentId = pathSegments.get(pathSegments.size() - 2);
            String treeId = DocumentsContract.getTreeDocumentId(childUri);
            Uri treeUri = DocumentsContract.buildDocumentUriUsingTree(childUri, treeId);
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId);
        } catch (Exception e) {
            Log.e("MainActivity", "Error finding parent URI: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    private Uri findParentUriCompat(Uri childDocumentUri) {
        if (childDocumentUri == null) {
            return getRootUri();
        }
        Log.w("SAF_COMPAT", "Falling back to root URI for parent selection due to API incompatibility.");
        return getRootUri();
    }

    public void openFile(Uri uri, String displayName) {
        int position = viewPagerAdapter.getFileUris().indexOf(uri);
        if (position != -1) {
            viewPager.setCurrentItem(position);
        } else {
            viewPagerAdapter.addFile(uri, displayName);
            viewPager.setCurrentItem(viewPagerAdapter.getItemCount() - 1);
        }
    }

    private void performCreation(final Uri longPressedUri, final boolean isFolder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = isFolder ? "Create New Folder" : "Create New File";
        builder.setTitle(title);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_file_folder, null);
        builder.setView(dialogView);
        final EditText inputName = dialogView.findViewById(R.id.input_name);
        final Spinner spinnerFolder = dialogView.findViewById(R.id.spinner_folder);
        final Map<String, Uri> folderMap = new HashMap<>();
        Uri defaultSelectionUri = getRootUri();
        FileItem item = findFileItemByUri(longPressedUri);
        if (item != null) {
            if (item.isDirectory) {
                defaultSelectionUri = item.uri;
            } else {
                Uri parentUri = findParentUriCompat(longPressedUri);
                if (parentUri != null) {
                    defaultSelectionUri = parentUri;
                }
            }
        }
        final Uri finalDefaultSelectionUri = defaultSelectionUri;
        new AsyncTask<Void, Void, List<FileItem>>() {
            @Override
            protected List<FileItem> doInBackground(Void... voids) {
                return getAllFoldersInDirectory(getRootUri());
            }

            @Override
            protected void onPostExecute(List<FileItem> allFolders) {
                if (allFolders == null || allFolders.isEmpty()) {
                    if (getRootUri() != null) {
                        FileItem rootItem = new FileItem(MainActivity.this, getRootUri(), "Project Root", true, 0);
                        allFolders = new ArrayList<>();
                        allFolders.add(rootItem);
                    } else {
                        Toast.makeText(MainActivity.this, "Could not load directories for selection.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                List<String> folderNames = new ArrayList<>();
                int initialSelection = 0;
                for (int i = 0; i < allFolders.size(); i++) {
                    FileItem folder = allFolders.get(i);
                    folderNames.add(folder.displayName);
                    folderMap.put(folder.displayName, folder.uri);
                    if (folder.uri.equals(finalDefaultSelectionUri)) {
                        initialSelection = i;
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, folderNames);
                spinnerFolder.setAdapter(adapter);
                spinnerFolder.setSelection(initialSelection);
            }
        }.execute();
        builder.setPositiveButton("Create", (dialog, which) -> {
            String newName = inputName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedFolderName = (String) spinnerFolder.getSelectedItem();
            final Uri destinationUri = folderMap.get(selectedFolderName);
            if (destinationUri == null) return;
            final String mimeType = isFolder ? DocumentsContract.Document.MIME_TYPE_DIR : getMimeTypeFromExtension(newName);
            new Thread(() -> {
                try {
                    Uri resultUri = DocumentsContract.createDocument(getContentResolver(), destinationUri, mimeType, newName);
                    if (resultUri != null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, title + " successful.", Toast.LENGTH_SHORT).show();
                            if (!isFolder) {
                                openFile(resultUri, newName);
                            }
                            refreshAdapterDisplay();
                        });
                    } else {
                        throw new IOException(title + " failed: The document provider denied the creation.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, title + " failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "Error creating: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private Uri getRootUri() {
        return rootDirectoryUri;
    }

    private void refreshAdapterDisplay() {
        if (filesAdapter != null) {
            runOnUiThread(() -> filesAdapter.refresh());
        }
    }

    @NonNull
    private List<FileItem> getAllFoldersInDirectory(Uri parentUri) {
        List<FileItem> allFolders = new ArrayList<>();
        if (parentUri == null) return allFolders;
        if (parentUri.equals(getRootUri())) {
            allFolders.add(new FileItem(this, parentUri, "ROOT DIRECTORY", true, 0));
        }
        try {
            String parentDocumentId = DocumentsContract.getDocumentId(parentUri);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocumentId);
            try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String childDocId = cursor.getString(0);
                        String childName = cursor.getString(1);
                        String mimeType = cursor.getString(2);
                        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, childDocId);
                            FileItem folderItem = new FileItem(childUri, childName, true, 0, mimeType);
                            allFolders.add(folderItem);
                            allFolders.addAll(getAllFoldersInDirectory(childUri));
                        }
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.e("SAF", "Failed to retrieve folder list: " + e.getMessage());
        }
        return allFolders;
    }

    public boolean openNewTerminal() {
        Intent termuxActivity = new Intent(this, TermuxActivity.class);
        startActivity(termuxActivity);
        return true;
    }

    private void openHtmlInBrowser(Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "text/html");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No application found to view HTML jniLibs.", Toast.LENGTH_LONG).show();
        }
    }

    private void runFile(FileItem item) {
        runOnUiThread(() -> {
            if (item == null || item.uri == null) {
                Toast.makeText(this, "Please select a file first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (item.mimeType != null && (item.mimeType.equals("text/html") || item.mimeType.equals("application/xhtml+xml"))) {
                openHtmlInBrowser(item.uri);
                return;
            }
            String absoluteFilePath = getAbsolutePathFromUri(this, item.uri);
            String fileName = item.displayName;
            if (absoluteFilePath == null || fileName == null) {
                Toast.makeText(this, "Execution failed: Cannot resolve file path.", Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "Failed to resolve URI: " + item.uri);
            }
        });
    }

    @Nullable
    public String getAbsolutePathFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        final String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            return uri.getPath();
        }
        if ("content".equalsIgnoreCase(scheme) && !DocumentsContract.isDocumentUri(context, uri)) {
            String[] projection = {MediaStore.Images.Media.DATA};
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to resolve content URI via MediaStore", e);
            }
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            String relativePath = split.length > 1 ? split[1] : "";
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + relativePath;
            }
            File[] externalDirs = context.getExternalFilesDirs(null);
            for (File file : externalDirs) {
                if (file != null && file.getAbsolutePath().contains(type)) {
                    String basePath = file.getAbsolutePath().split("/Android")[0];
                    return basePath + "/" + relativePath;
                }
            }
        }
        return null;
    }

    private String getFileName(@NonNull Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error getting file name from cursor.", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    public void openSettings() {
        Intent settings = new Intent(getApplicationContext(), SettingsActivity.class);
        startActivity(settings);
    }

    public void openDirectory() {
        Intent selectFolder = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(selectFolder, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    public void onTabSelected(@NonNull TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
        int pos = tab.getPosition();
        if (pos < viewPagerAdapter.fileUris.size()) {
            Uri uri = viewPagerAdapter.fileUris.get(pos);
            if (uri != null) {
                FileItem item = FileUtils.getFileItemFromUri(this, uri);
                setSelectedFileItem(item);
                boolean allowedToRun = extensionAllowsRun(uri);
                runMenuVisible = allowedToRun;
            } else {
                runMenuVisible = false;
            }
        } else {
            runMenuVisible = false;
        }
        stopMenuVisible = false;
        editMenuVisible = false;
        invalidateOptionsMenu();
    }

    @Override
    public void onUserInputSubmitted(String input) {
    }

    @Override
    public void onOutputReceived(String output) {
    }

    private TerminalFragment getTerminalFragment() {
        return (TerminalFragment) getSupportFragmentManager().findFragmentByTag("TerminalFragment");
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.e("MainActivity", "Failed to take persistable URI permission.", e);
                Toast.makeText(this, "Failed to get persistent access to folder.", Toast.LENGTH_LONG).show();
                return;
            }
            folderUri = uri;
            currentDirectoryUri = uri;
            rootDirectoryUri = uri;
            saveLastFolder(uri);
            ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
            if (filesAdapter == null) {
                filesList = findViewById(R.id.filesList);
                refreshFolder = findViewById(R.id.refreshFilesFolders);
                collapseAllFolders = findViewById(R.id.collapseAllFolders);
                filesAdapter = new FilesAdapter(MainActivity.this, fileItems, this, rootDirectoryUri);
                filesList.setLayoutManager(new LinearLayoutManager(this));
                filesList.setAdapter(filesAdapter);
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.VISIBLE);
                if (refreshFolder != null) refreshFolder.setVisibility(View.GONE);
                if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.GONE);
                if (collapseAllFolders != null) {
                    collapseAllFolders.setOnClickListener(v -> filesAdapter.collapseAllFolders());
                }
                if (refreshFolder != null) {
                    refreshFolder.setOnClickListener(v -> refreshFileList());
                }
            } else {
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.VISIBLE);
            }
            if (executor != null) {
                executor.execute(() -> {
                    populateFileList(uri, 0);
                    runOnUiThread(() -> {
                        if (filesAdapter != null) {
                            filesAdapter.notifyDataSetChanged();
                        }
                        if (filesLoadingProgressBar != null)
                            filesLoadingProgressBar.setVisibility(View.GONE);
                        if (refreshFolder != null) refreshFolder.setVisibility(View.VISIBLE);
                        if (collapseAllFolders != null)
                            collapseAllFolders.setVisibility(View.VISIBLE);
                        openLeftNavigation();
                    });
                });
            }
        } else if (requestCode == REQUEST_CODE_OPEN_FILE) {
            final int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.e("MainActivity", "Failed to take persistable URI permission for file.", e);
                Toast.makeText(this, "Failed to get persistent read access to file.", Toast.LENGTH_LONG).show();
            }
            String fileName = getFileName(uri);
            if (viewPagerAdapter != null) {
                int position = viewPagerAdapter.addTab(uri, fileName);
                if (viewPager != null) {
                    viewPager.setCurrentItem(position, true);
                }
            }
            closeLeftNavigation();
        } else if (requestCode == WorkspaceInitializer.REQUEST_CODE_SAF && resultCode == RESULT_OK) {
            WorkspaceInitializer.handleSafResult(this, data);
        } else if (requestCode == REQUEST_CODE_OPEN_FILE_FOR_IMPORT) {
            Uri selectedFileUri = data.getData();
            if (selectedFileUri != null && importTargetFolder != null) {
                showImportTargetFolderDialog(selectedFileUri, importTargetFolder);
            } else {
                Toast.makeText(this, "Could not get selected file or target folder.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshFileList() {
        if (folderUri != null && filesAdapter != null) {
            runOnUiThread(() -> {
                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
                refreshFolder.setVisibility(View.GONE);
                collapseAllFolders.setVisibility(View.GONE);
            });
            executor.execute(() -> {
                runOnUiThread(() -> filesAdapter.refresh());
                populateFileList(folderUri, 0);
                runOnUiThread(() -> {
                    filesAdapter.notifyDataSetChanged();
                    ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                    filesLoadingProgressBar.setVisibility(View.GONE);
                    refreshFolder.setVisibility(View.VISIBLE);
                    collapseAllFolders.setVisibility(View.VISIBLE);
                });
            });
        }
    }

    @Override
    public void onFileClicked(Uri fileUri, String fileName) {
        String mimeType = getApplicationContext().getContentResolver().getType(fileUri);
        boolean isExternalViewType = isIsExternalViewType(mimeType);
        if (isExternalViewType) {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            if (mimeType == null) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
                if (extension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                }
            }
            if (mimeType == null) {
                mimeType = "*/*";
            }

            viewIntent.setDataAndType(fileUri, mimeType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No application found to view this file type.", Toast.LENGTH_LONG).show();
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

    public boolean closeFileAndSaveState(Uri fileUri) {
        if (viewPagerAdapter == null || viewPager == null) return false;
        int index = viewPagerAdapter.fileUris.indexOf(fileUri);
        if (index != -1) {
            lastClosedTabState = new TabState(fileUri, viewPagerAdapter.fileNames.get(index), index);
            viewPagerAdapter.fileUris.remove(index);
            viewPagerAdapter.fileNames.remove(index);
            viewPagerAdapter.notifyDataSetChanged();
            int newPosition = Math.min(index, viewPagerAdapter.getItemCount() - 1);
            if (newPosition >= 0) {
                viewPager.setCurrentItem(newPosition);
            }
            return true;
        }
        lastClosedTabState = null;
        return false;
    }

    public void reopenClosedTab(Uri newUri, String newName) {
        if (lastClosedTabState == null) return;
        int insertPosition = lastClosedTabState.position;
        if (insertPosition > viewPagerAdapter.getItemCount()) {
            insertPosition = viewPagerAdapter.getItemCount();
        }
        viewPagerAdapter.fileUris.add(insertPosition, newUri);
        viewPagerAdapter.fileNames.add(insertPosition, newName);
        viewPagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(insertPosition);
        lastClosedTabState = null;
    }

    public String getMimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            }
        }
        return type;
    }

    private void saveLastFolder(Uri uri) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE).edit();
        if (uri != null) {
            editor.putString(LAST_FOLDER_URI_KEY, uri.toString());
        } else {
            editor.remove(LAST_FOLDER_URI_KEY);
        }
        editor.apply();
    }

    private void restoreLastFolder() {
        preferences = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
        String uriString = preferences.getString(LAST_FOLDER_URI_KEY, null);
        if (uriString != null) {
            try {
                Uri lastFolderUri = Uri.parse(uriString);
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(lastFolderUri, takeFlags);
                folderUri = lastFolderUri;
                currentDirectoryUri = lastFolderUri;
                if (filesList == null) {
                    filesList = findViewById(R.id.filesList);
                    filesList.setLayoutManager(new LinearLayoutManager(this));
                    filesAdapter = new FilesAdapter(MainActivity.this, fileItems, this, rootDirectoryUri);
                    filesList.setAdapter(filesAdapter);
                    collapseAllFolders.setOnClickListener(v -> filesAdapter.collapseAllFolders());
                    refreshFolder.setOnClickListener(v -> refreshFileList());
                }
                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
                refreshFolder.setVisibility(View.GONE);
                collapseAllFolders.setVisibility(View.GONE);
                executor.execute(() -> {
                    populateFileList(lastFolderUri, 0);
                    runOnUiThread(() -> {
                        if (filesAdapter != null) {
                            filesAdapter.notifyDataSetChanged();
                        }
                        filesLoadingProgressBar.setVisibility(View.GONE);
                        refreshFolder.setVisibility(View.VISIBLE);
                        collapseAllFolders.setVisibility(View.VISIBLE);
                    });
                });
            } catch (SecurityException e) {
                e.printStackTrace();
                Toast.makeText(this, "Permission to access the last folder was revoked.", Toast.LENGTH_LONG).show();
                saveLastFolder(null);
            }
        }
    }

    private void refreshAll() {
        if (folderUri == null || filesAdapter == null || viewPagerAdapter == null || executor == null)
            return;
        runOnUiThread(() -> {
            ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);

            if (filesLoadingProgressBar != null)
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
            if (refreshFolder != null) refreshFolder.setVisibility(View.GONE);
            if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.GONE);
        });

        executor.execute(() -> {
            List<Uri> openFileUris = new ArrayList<>();
            for (FilesAdapter.FileContentItem file : viewPagerAdapter.getOpenFilesContent()) {
                openFileUris.add(file.getUri());
            }

            List<Uri> expandedFolders = new ArrayList<>();
            for (FileItem item : filesAdapter.fileList) {
                if (item.isDirectory && item.isExpanded) {
                    expandedFolders.add(item.uri);
                }
            }

            filesAdapter.fileList.clear();
            populateFileList(folderUri, 0);

            runOnUiThread(() -> {

                for (int i = 0; i < filesAdapter.fileList.size(); i++) {
                    FileItem item = filesAdapter.fileList.get(i);

                    if (item.isDirectory && expandedFolders.contains(item.uri)) {
                        filesAdapter.holder.expandFolder(i);
                    }

                    if (!item.isDirectory) {
                        String type = item.mimeType != null ? item.mimeType : getMimeType(item.uri);
                    }
                }

                int currentTabPos = tabLayout.getSelectedTabPosition();
                if (currentTabPos != -1) {
                    Fragment currentFragment = viewPagerAdapter.getFragment(currentTabPos);
                    if (currentFragment instanceof TextFragment) {
                        ((TextFragment) currentFragment).refreshContent();
                    }
                }

                filesAdapter.notifyDataSetChanged();

                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.GONE);
                if (refreshFolder != null) refreshFolder.setVisibility(View.VISIBLE);
                if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.VISIBLE);
            });
        });
    }

    public void showImportTargetFolderDialog(Uri sourceUri, FileItem defaultTargetFolder) {
        String sourceFileName = getFileNameFromUri(sourceUri);
        if (sourceFileName == null) {
            Toast.makeText(this, "Could not determine source file name.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri parentUri = defaultTargetFolder.uri;
        List<FileItem> subFolders = getChildFolders(parentUri);

        List<String> folderNames = new ArrayList<>();
        List<Uri> folderUris = new ArrayList<>();

        folderNames.add("Default: " + defaultTargetFolder.displayName);
        folderUris.add(parentUri);

        for (FileItem folder : subFolders) {
            folderNames.add(folder.displayName);
            folderUris.add(folder.uri);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Import File: " + sourceFileName);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_file_folder, null);
        EditText input = dialogView.findViewById(R.id.input_name);
        Spinner folderSpinner = dialogView.findViewById(R.id.spinner_folder);
        builder.setView(dialogView);

        input.setText(sourceFileName);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setSelection(0);

        builder.setPositiveButton("Import", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "File name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            int selectedPosition = folderSpinner.getSelectedItemPosition();
            Uri targetFolderUri = folderUris.get(selectedPosition);

            importFileAsync(sourceUri, targetFolderUri, newName);
            runOnUiThread(() -> filesAdapter.refresh());
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Nullable
    private FileItem findFileItemByUri(Uri uri) {
        if (filesAdapter != null && filesAdapter.fileList != null) {
            for (FileItem item : filesAdapter.fileList) {
                if (item.uri.equals(uri)) {
                    return item;
                }
            }
        }

        if (viewPagerAdapter.fileUris != null && viewPagerAdapter.fileNames != null) {
            for (int i = 0; i < viewPagerAdapter.getFileUris().size(); i++) {
                if (viewPagerAdapter.getFileUris().get(i).equals(uri)) {
                    return new FileItem(this, uri, viewPagerAdapter.getFileNames().get(i), false, 0);
                }
            }
        }

        return null;
    }

    public void populateFileList(final Uri uri, final int depth) {
        new Thread(() -> {
            try {
                String documentId = DocumentsContract.getTreeDocumentId(uri);
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId);

                final List<FileItem> folders = new ArrayList<>();
                final List<FileItem> files = new ArrayList<>();
                final String folderName = DocumentsContract.getTreeDocumentId(uri);
                String folderNameToDisplay = "Storage/" + folderName.substring(8);
                Log.d("FileName", "populateFileList: folder name:" + folderNameToDisplay);
                try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            String childDocId = cursor.getString(0);
                            String childName = cursor.getString(1);
                            String mimeType = cursor.getString(2);
                            boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childDocId);
                            if (isDirectory) {
                                folders.add(new FileItem(childUri, childName, true, depth, mimeType));
                            } else {
                                files.add(new FileItem(childUri, childName, false, depth, mimeType));
                            }
                        } while (cursor.moveToNext());
                    }
                }

                runOnUiThread(() -> {
                    currentFolderTitle.setText(folderNameToDisplay);
                    fileItems.clear();
                    folders.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                    files.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                    fileItems.addAll(folders);
                    fileItems.addAll(files);
                    filesAdapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void openLeftNavigation() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void closeLeftNavigation() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveOpenedTabs();
    }

    private void saveOpenedTabs() {
        List<Uri> uriList = viewPagerAdapter.getFileUris();
        List<String> namesList = viewPagerAdapter.getFileNames();
        int currentTab = tabLayout.getSelectedTabPosition();
        if (uriList.isEmpty() || currentTab == -1) {
            preferences.edit().remove(TAB_URI_KEY).remove(TAB_NAME_KEY).remove(CURRENT_TAB).apply();
            return;
        }
        List<String> uriStringList = new ArrayList<>();
        for (Uri uri : uriList) {
            uriStringList.add(uri.toString());
        }
        Gson gson = new Gson();
        String jsonUris = gson.toJson(uriStringList);
        String jsonNames = gson.toJson(namesList);
        preferences.edit().putString(TAB_URI_KEY, jsonUris).putString(TAB_NAME_KEY, jsonNames).putInt(CURRENT_TAB, currentTab).apply();
        Log.d(TAG, "Tabs saved: " + uriList.size() + " files, current: " + currentTab);
    }

    @Override
    public void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent) {
        if (fileContent != null) {
            saveContentToFile(fileUri, fileContent, fileName);
        } else {
            openFileInViewPager(fileUri, fileName);
        }
    }

    private void saveContentToFile(Uri fileUri, byte[] content, String fileName) {
        new Thread(() -> {
            try {
                try (OutputStream os = getContentResolver().openOutputStream(fileUri)) {
                    if (os != null) {
                        os.write(content);
                    }
                }
                runOnUiThread(() -> {
                    int untitledPos = viewPagerAdapter.findTabPositionByName("Untitled");
                    if (untitledPos != -1) {
                        viewPagerAdapter.removeTab(untitledPos);
                    }
                    openFileInViewPager(fileUri, fileName);
                    Toast.makeText(this, "File saved successfully!", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openFileInViewPager(Uri fileUri, String fileName) {
        int position = viewPagerAdapter.addTab(fileUri, fileName);
        viewPager.setCurrentItem(position, true);
        invalidateOptionsMenu();
    }

    @Override
    public void requestSaveAs(byte[] content) {
        prepareFolderDataForDialog();
        if (folderUris.isEmpty()) {
            Toast.makeText(this, "Please select a folder with write permission first.", Toast.LENGTH_LONG).show();
            return;
        }
        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames, content);
        dialog.show(getSupportFragmentManager(), "SaveAsFileDialog");
    }

    public void openFilePicker() {
        // This intent opens the system's file picker
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Files must be openable (readable)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Set type to text and code jniLibs, or use "*/*" for everything
        intent.setType("*/*");

        // Optionally, suggest specific MIME types for the picker to filter by
        String[] mimeTypes = {"text/*",
                "application/json",
                "application/xml", "application/javascript",
                "application/x-java-source", // Java source jniLibs
                "text/x-csrc", // C source jniLibs
                "text/x-c++src", // C++ source jniLibs
                "text/x-python", // Python source jniLibs
                "image/*", // All image types
                "audio/*", // All audio types
                "video/*" // All video types
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
    }

    public void setSelectedFileItem(FileItem selectedFileItem) {
        this.selectedFileItem = selectedFileItem;
    }

    private static class TabState {
        private final TabState lastClosedTabState = null;
        List<Uri> uriList;
        List<String> namesList;
        int currentTab;
        Uri oldUri;
        String name;
        int position;

        public TabState(List<Uri> uriList, List<String> namesList, int currentTab) {
            this.uriList = uriList;
            this.namesList = namesList;
            this.currentTab = currentTab;
        }

        public TabState(Uri oldUri, String name, int position) {
            this.oldUri = oldUri;
            this.name = name;
            this.position = position;
        }
    }
}
