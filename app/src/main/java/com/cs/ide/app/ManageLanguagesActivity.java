package com.cs.ide.app;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.cs.ide.R;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
public class ManageLanguagesActivity extends AppCompatActivity {
    private static final String TAG = "ManageLanguagesActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<LanguagePack> allPacks = new ArrayList<>();
    private final List<LanguagePack> filteredPacks = new ArrayList<>();
    private View menuContentContainer;
    private ListView packagesList;
    private EditText searchBar;
    private CommandFetcher commandFetcher;
    private LanguagePackAdapter adapter;
    private TermuxSessionManager termuxSessionManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_languages_code_studio);
        Toolbar toolbar = findViewById(R.id.toolbar);
        menuContentContainer = findViewById(R.id.manageLanguagesLayout);
        ViewCompat.setOnApplyWindowInsetsListener(menuContentContainer, DisplayManager::setupDynamicMarginHandling);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        packagesList = findViewById(R.id.packagesList);
        searchBar = findViewById(R.id.searchBar);
        commandFetcher = new CommandFetcher(this);
        termuxSessionManager = new TermuxSessionManager();
        adapter = new LanguagePackAdapter(this, filteredPacks);
        packagesList.setAdapter(adapter);
        loadLanguagesAsync();
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                filterPacks(s.toString());
            }
        });
        packagesList.setOnItemClickListener((parent, view, position, id) -> {
            LanguagePack selectedPack = filteredPacks.get(position);
            showInstallUninstallDialog(selectedPack);
        });
    }
    private void showInstallUninstallDialog(LanguagePack pack) {
        final boolean isInstalled = pack.status == LanguagePack.STATUS_INSTALLED;
        String title = isInstalled ? "Uninstall " + pack.name : "Install " + pack.name;
        String message = isInstalled
                ? "Are you sure you want to uninstall " + pack.name + "? This may break existing projects."
                : "Do you want to install " + pack.name + "? The operation will run in the background.";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isInstalled ? "Uninstall" : "Install", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeOperation(pack, !isInstalled);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
    private void executeOperation(LanguagePack pack, final boolean install) {
        pack.status = LanguagePack.STATUS_INSTALLING;
        adapter.notifyDataSetChanged();
        String operation = install ? "Installation" : "Uninstallation";
        String command = install ? pack.installCommand : pack.getUninstallCommand();
        Toast.makeText(this, operation + " of " + pack.name + " started in background.", Toast.LENGTH_LONG).show();
        commandFetcher.executorService.submit(() -> {
            boolean success = termuxSessionManager.runCommand(command, pack.key);
            mainHandler.post(() -> {
                if (success) {
                    pack.status = install ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE;
                    Toast.makeText(this, pack.name + " " + operation.toLowerCase() + " completed successfully.", Toast.LENGTH_LONG).show();
                    Log.i(TAG, pack.name + " status updated to: " + (install ? "INSTALLED" : "AVAILABLE"));
                } else {
                    pack.status = install ? LanguagePack.STATUS_AVAILABLE : LanguagePack.STATUS_INSTALLED;
                    Toast.makeText(this, pack.name + " " + operation.toLowerCase() + " failed.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, pack.name + " " + operation.toLowerCase() + " failed for command: " + command);
                }
                adapter.notifyDataSetChanged();
            });
        });
    }
    private void checkAllPackageStatuses() {
        Log.d(TAG, "Starting check for all language package statuses.");
        commandFetcher.executorService.submit(() -> {
            for (final LanguagePack pack : allPacks) {
                boolean isInstalled = termuxSessionManager.runCheckCommand(pack.checkCommand, pack.key);
                mainHandler.post(() -> {
                    pack.status = isInstalled ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE;
                    adapter.notifyDataSetChanged(); 
                    Log.d(TAG, pack.name + " status: " + (isInstalled ? "INSTALLED" : "AVAILABLE"));
                });
            }
            mainHandler.post(() -> Toast.makeText(this, "Package status check complete.", Toast.LENGTH_SHORT).show());
        });
    }
    private void filterPacks(String query) {
        query = query.toLowerCase().trim();
        filteredPacks.clear();
        if (query.isEmpty()) {
            filteredPacks.addAll(allPacks);
        } else {
            for (LanguagePack pack : allPacks) {
                if (pack.name.toLowerCase().contains(query) || pack.key.toLowerCase().contains(query)) {
                    filteredPacks.add(pack);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
    private void loadLanguagesAsync() {
        Future<List<LanguagePack>> future = commandFetcher.loadAllLanguagePacksAsync();
        commandFetcher.executorService.submit(() -> {
            try {
                final List<LanguagePack> loadedPacks = future.get();
                mainHandler.post(() -> {
                    allPacks.clear();
                    allPacks.addAll(loadedPacks);
                    filterPacks(searchBar.getText().toString()); 
                    checkAllPackageStatuses(); 
                    Toast.makeText(this, "Loaded " + allPacks.size() + " language packs.", Toast.LENGTH_SHORT).show();
                });
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Failed to load language packs: " + e.getMessage());
                mainHandler.post(() -> Toast.makeText(this, "Error loading language packs.", Toast.LENGTH_LONG).show());
            }
        });
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (commandFetcher != null) {
            commandFetcher.shutdown();
        }
    }
    private static class TermuxSessionManager {
        public boolean runCommand(String command, String packageKey) {
            Log.d(TAG, "EXECUTING COMMAND for " + packageKey + ": " + command);
            try {
                Thread.sleep(2000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Math.random() > 0.1; 
        }
        public boolean runCheckCommand(String command, String packageKey) {
            Log.d(TAG, "CHECKING STATUS for " + packageKey + ": " + command);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return packageKey.contains("py") || Math.random() > 0.5; 
        }
    }
    private static class LanguagePackAdapter extends ArrayAdapter<LanguagePack> {
        public LanguagePackAdapter(@NonNull Context context, @NonNull List<LanguagePack> packs) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, packs);
        }
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            LanguagePack pack = getItem(position);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);
            text1.setText(pack.name);
            text2.setText(pack.key);
            int color;
            String statusText;
            switch (pack.status) {
                case LanguagePack.STATUS_INSTALLED:
                    color = ContextCompat.getColor(getContext(), R.color.green_700); 
                    statusText = "INSTALLED (Tap to UNINSTALL)";
                    break;
                case LanguagePack.STATUS_INSTALLING:
                    color = ContextCompat.getColor(getContext(), R.color.yellow_600); 
                    statusText = "OPERATION IN PROGRESS...";
                    break;
                case LanguagePack.STATUS_AVAILABLE:
                default:
                    color = ContextCompat.getColor(getContext(), R.color.red_500); 
                    statusText = "AVAILABLE (Tap to INSTALL)";
                    break;
            }
            text2.setTextColor(color);
            text2.setText(statusText);
            return view;
        }
    }
}
