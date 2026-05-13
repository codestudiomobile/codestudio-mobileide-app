package com.cs.ide.app.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.cs.ide.R;
import com.cs.ide.app.adapters.LanguagePackAdapter;
import com.cs.ide.app.execution.CommandFetcher;
import com.cs.ide.app.models.LanguagePack;
import com.cs.ide.app.services.AptBackgroundService;
import com.cs.ide.app.utils.DisplayManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * ManageLanguagesActivity handles the installation and uninstallation of various
 * language environments and tools (e.g., Python, C++, Node.js).
 * It uses a background service to perform installations without blocking the UI.
 */
public class ManageLanguagesActivity extends AppCompatActivity {
    private static final String TAG = "ManageLanguagesActivity";

    // --- State and Helpers ---
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<LanguagePack> allPacks = new ArrayList<>();
    private final List<LanguagePack> filteredPacks = new ArrayList<>();
    private CommandFetcher commandFetcher;
    private LanguagePackAdapter adapter;

    // --- UI Elements ---
    private View rootLayout;
    private ListView packagesList;
    private EditText searchBar;
    private View progressContainer;
    private TextView progressStatusText;
    private ProgressBar installProgressBar;

    /**
     * BroadcastReceiver to listen for progress updates from AptBackgroundService.
     */
    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AptBackgroundService.ACTION_PROGRESS.equals(intent.getAction())) {
                int percent = intent.getIntExtra(AptBackgroundService.EXTRA_PROGRESS_PERCENT, 0);
                String text = intent.getStringExtra(AptBackgroundService.EXTRA_PROGRESS_TEXT);

                progressContainer.setVisibility(View.VISIBLE);
                progressStatusText.setText(text);
                installProgressBar.setProgress(percent);

                if (percent == 100) {
                    // Hide progress container after a short delay on completion
                    mainHandler.postDelayed(() -> progressContainer.setVisibility(View.GONE), 3000);
                    checkAllPackageStatuses(); // Refresh status of all packages
                }
            }
        }
    };

    // --- Lifecycle Methods ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_languages_code_studio);
        
        setupToolbar();
        setupViews();
        setupSearch();
        
        commandFetcher = new CommandFetcher(this);
        adapter = new LanguagePackAdapter(this, filteredPacks);
        packagesList.setAdapter(adapter);
        
        loadLanguagePacks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the progress receiver
        IntentFilter filter = new IntentFilter(AptBackgroundService.ACTION_PROGRESS);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(progressReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(progressReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (commandFetcher != null) {
            commandFetcher.shutdown();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- UI Setup ---

    /**
     * Configures the activity toolbar.
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_manage_languages);
        }
    }

    /**
     * Initializes UI component references.
     */
    private void setupViews() {
        rootLayout = findViewById(R.id.manageLanguagesLayout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, DisplayManager::setupDynamicMarginHandling);
        }
        
        packagesList = findViewById(R.id.packagesList);
        searchBar = findViewById(R.id.searchBar);
        progressContainer = findViewById(R.id.progressContainer);
        progressStatusText = findViewById(R.id.progressStatusText);
        installProgressBar = findViewById(R.id.installProgressBar);
    }

    /**
     * Sets up the search bar functionality to filter language packs.
     */
    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPacks(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // --- Data Management ---

    /**
     * Loads the list of available language packs asynchronously.
     */
    private void loadLanguagePacks() {
        Future<List<LanguagePack>> future = commandFetcher.loadAllLanguagePacksAsync();
        new Thread(() -> {
            try {
                List<LanguagePack> packs = future.get();
                mainHandler.post(() -> {
                    allPacks.clear();
                    allPacks.addAll(packs);
                    checkAllPackageStatuses();
                    filterPacks(searchBar.getText().toString());
                });
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error loading language packs", e);
            }
        }).start();
    }

    /**
     * Checks the installation status of all loaded language packs.
     */
    private void checkAllPackageStatuses() {
        for (LanguagePack pack : allPacks) {
            checkPackageStatus(pack);
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Checks if a specific package is installed on the system.
     *
     * @param pack The language pack to check.
     */
    private void checkPackageStatus(LanguagePack pack) {
        if (pack.checkCommand == null || pack.checkCommand.isEmpty()) {
            pack.status = LanguagePack.STATUS_AVAILABLE;
            return;
        }
        
        try {
            String binPath = getFilesDir().getPath() + "/usr/bin/sh";
            Process process = Runtime.getRuntime().exec(new String[]{binPath, "-c", "command -v " + pack.checkCommand});
            int exitCode = process.waitFor();
            pack.status = (exitCode == 0) ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE;
        } catch (Exception e) {
            Log.w(TAG, "Failed to check status for " + pack.name, e);
            pack.status = LanguagePack.STATUS_AVAILABLE;
        }
    }

    /**
     * Filters the language packs based on the provided query.
     *
     * @param query The search query.
     */
    private void filterPacks(String query) {
        filteredPacks.clear();
        if (query.isEmpty()) {
            filteredPacks.addAll(allPacks);
        } else {
            String lowerQuery = query.toLowerCase();
            for (LanguagePack pack : allPacks) {
                if (pack.name.toLowerCase().contains(lowerQuery)) {
                    filteredPacks.add(pack);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Initiates the installation of a language pack.
     *
     * @param pack The package to install.
     */
    public void installPackage(LanguagePack pack) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_install_pkg, pack.name))
                .setMessage(getString(R.string.msg_confirm_install, pack.name))
                .setPositiveButton(R.string.label_installation, (dialog, which) -> {
                    Intent serviceIntent = new Intent(this, AptBackgroundService.class);
                    serviceIntent.setAction(AptBackgroundService.ACTION_INSTALL);
                    serviceIntent.putExtra(AptBackgroundService.EXTRA_PACKAGE, pack.key);
                    startService(serviceIntent);
                    Toast.makeText(this, R.string.msg_starting_installation, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
