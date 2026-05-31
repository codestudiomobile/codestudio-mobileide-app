package com.csmide.app.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
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

import com.csmide.R;
import com.csmide.app.adapters.LanguagePackAdapter;
import com.csmide.app.execution.CommandFetcher;
import com.csmide.app.models.LanguagePack;
import com.csmide.app.services.AptBackgroundService;
import com.csmide.app.services.LanguageManagerService;
import com.csmide.app.utils.DisplayManager;
import com.csmide.termux.app.TermuxActivity;
import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Activity for managing language packs in the IDE.
 * Allows users to search, install, and uninstall language support packages (e.g., compilers, utilities)
 * through an APT-based background service ({@link AptBackgroundService}).
 */
public class ManageLanguagesActivity extends AppCompatActivity implements LanguagePackAdapter.OnPackActionListener {
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
	private TextView extractStatusText;
	private ProgressBar extractProgressBar;
	private AlertDialog progressDialog;
	private final Runnable hideProgressRunnable = () -> {
		progressContainer.setVisibility(View.GONE);
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
	};
	private TextView dialogStatusText;
	private ProgressBar dialogProgressBar;
	/**
	 * BroadcastReceiver to listen for progress updates from AptBackgroundService.
	 */
	private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (AptBackgroundService.ACTION_PROGRESS.equals(action)) {
				int percent = intent.getIntExtra(AptBackgroundService.EXTRA_PROGRESS_PERCENT, 0);
				String text = intent.getStringExtra(AptBackgroundService.EXTRA_PROGRESS_TEXT);
				updateProgressFromService(percent, -1, text);
			} else if (LanguageManagerService.ACTION_PROGRESS_UPDATE.equals(action)) {
				int percent = intent.getIntExtra(LanguageManagerService.EXTRA_PROGRESS, 0);
				int extractPercent = intent.getIntExtra(LanguageManagerService.EXTRA_EXTRACT_PROGRESS, -1);
				String text = intent.getStringExtra(LanguageManagerService.EXTRA_STATUS_TEXT);
				updateProgressFromService(percent, extractPercent, text);
			} else if (LanguageManagerService.ACTION_REQUEST_CONFIRM.equals(action)) {
				String downloadSize = intent.getStringExtra(LanguageManagerService.EXTRA_DOWNLOAD_SIZE);
				String installSize = intent.getStringExtra(LanguageManagerService.EXTRA_INSTALL_SIZE);
				String pkgName = intent.getStringExtra(LanguageManagerService.EXTRA_PACKAGE_NAME);
				showConfirmationDialog(pkgName, downloadSize, installSize, true);
			} else if (AptBackgroundService.ACTION_REQUEST_CONFIRM.equals(action)) {
				String downloadSize = intent.getStringExtra(AptBackgroundService.EXTRA_DOWNLOAD_SIZE);
				String installSize = intent.getStringExtra(AptBackgroundService.EXTRA_INSTALL_SIZE);
				showConfirmationDialog("Tool", downloadSize, installSize, false);
			}
		}
	};

	private void updateProgressFromService(int percent, int extractPercent, String text) {
		mainHandler.removeCallbacks(hideProgressRunnable);
		updateProgress(percent, extractPercent, text);

		if (percent == 100 && (extractPercent == -1 || extractPercent == 100)) {
			if (text != null && (text.contains("successfully") || text.contains("complete"))) {
				Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
			}
			// Hide progress container after a short delay on completion
			mainHandler.postDelayed(hideProgressRunnable, 3000);
			checkAllPackageStatuses(); // Refresh status of all packages
		}
	}

	private void updateProgress(int percent, int extractPercent, String text) {
		progressContainer.setVisibility(View.VISIBLE);
		progressStatusText.setText(text);
		installProgressBar.setProgress(percent);

		if (extractPercent != -1) {
			extractStatusText.setVisibility(View.VISIBLE);
			extractProgressBar.setVisibility(View.VISIBLE);
			extractProgressBar.setProgress(extractPercent);
		} else {
			extractStatusText.setVisibility(View.GONE);
			extractProgressBar.setVisibility(View.GONE);
		}

		if (progressDialog != null && progressDialog.isShowing()) {
			dialogStatusText.setText(text);
			dialogProgressBar.setProgress(percent);
			dialogProgressBar.setIndeterminate(percent == 0);
			// Optional: we could add the extract bar to the dialog too, but for now let's stick to the main UI
		}
	}

	private void showConfirmationDialog(String pkgName, String downloadSize, String installSize, boolean isPackageService) {
		new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
				.setTitle("Confirm Installation")
				.setMessage("Package: " + pkgName + "\nDownload size: " + downloadSize + "\nDisk space needed: " + installSize + "\n\nDo you want to continue?")
				.setPositiveButton("Continue", (dialog, which) -> {
					Intent confirmIntent = new Intent(isPackageService ? LanguageManagerService.ACTION_CONFIRM : AptBackgroundService.ACTION_CONFIRM);
					sendBroadcast(confirmIntent);
					showProgressDialog();
				})
				.setNegativeButton("Abort", (dialog, which) -> {
					Intent cancelIntent = new Intent(isPackageService ? LanguageManagerService.ACTION_CANCEL : AptBackgroundService.ACTION_CANCEL);
					sendBroadcast(cancelIntent);
				})
				.setCancelable(false)
				.show();
	}

	private void showProgressDialog() {
		if (progressDialog != null && progressDialog.isShowing()) return;

		View view = getLayoutInflater().inflate(R.layout.dialog_installation_progress, null);
		dialogStatusText = view.findViewById(R.id.progressStatus);
		dialogStatusText.setTextColor(android.graphics.Color.WHITE);
		dialogProgressBar = view.findViewById(R.id.progressBar);

		progressDialog = new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
				.setTitle("Installing Package")
				.setView(view)
				.setPositiveButton("Background", (dialog, which) -> dialog.dismiss())
				.setNegativeButton("Cancel", (dialog, which) -> {
					Intent cancelIntent = new Intent(AptBackgroundService.ACTION_CANCEL);
					sendBroadcast(cancelIntent);
					dialog.dismiss();
				})
				.setCancelable(false)
				.show();
	}

	// --- Lifecycle Methods ---

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_manage_languages_code_studio);

		setupToolbar();
		setupViews();
		setupSearch();

		commandFetcher = new CommandFetcher(this);
		adapter = new LanguagePackAdapter(this, filteredPacks, this);
		packagesList.setAdapter(adapter);
		packagesList.setOnItemClickListener(null); // Click handled by button in adapter

		loadLanguagePacks();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Register the progress receiver
		IntentFilter filter = new IntentFilter();
		filter.addAction(AptBackgroundService.ACTION_PROGRESS);
		filter.addAction(AptBackgroundService.ACTION_REQUEST_CONFIRM);
		filter.addAction(LanguageManagerService.ACTION_PROGRESS_UPDATE);
		filter.addAction(LanguageManagerService.ACTION_REQUEST_CONFIRM);
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
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 1002, 0, "Fix Package Manager")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else if (item.getItemId() == 1002) {
			startTerminalInstallation("dpkg --configure -a && pkg update", "Fix Package Manager");
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
		progressStatusText.setTextColor(android.graphics.Color.WHITE);
		installProgressBar = findViewById(R.id.installProgressBar);
		extractStatusText = findViewById(R.id.extractStatusText);
		extractProgressBar = findViewById(R.id.extractProgressBar);
	}

	/**
	 * Sets up the search bar functionality to filter language packs.
	 */
	private void setupSearch() {
		searchBar.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterPacks(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
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
	 * Checks the installation status of all loaded language packs efficiently.
	 */
	private void checkAllPackageStatuses() {
		new Thread(() -> {
			java.util.Set<String> installedPackages = new java.util.HashSet<>();
			try {
				String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
				String binPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";

				// Use dpkg-query to get all installed packages at once
				ProcessBuilder pb = new ProcessBuilder(binPath, "-c", "dpkg-query -W -f='${Package}\\n'");
				pb.environment().put("PREFIX", prefix);
				pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
				pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));

				Process process = pb.start();
				java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
				String lineStr;
				while ((lineStr = reader.readLine()) != null) {
					installedPackages.add(lineStr.trim());
				}
				process.waitFor();
			} catch (Exception e) {
				Log.e(TAG, "Failed to get installed packages list", e);
			}

			mainHandler.post(() -> {
				for (LanguagePack pack : allPacks) {
					if (pack.checkCommand != null && pack.checkCommand.startsWith("check_suggestion:")) {
						String lang = pack.checkCommand.substring("check_suggestion:".length());
						File langDir = new File(getFilesDir(), "languages/" + lang);
						boolean installed = langDir.exists() && langDir.list() != null && langDir.list().length > 0;
						pack.status = installed ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE;
					} else if (pack.key != null && installedPackages.contains(pack.key)) {
						pack.status = LanguagePack.STATUS_INSTALLED;
					} else {
						// Fallback to individual check if key is not found or it's a special command
						checkPackageStatusIndividual(pack);
					}
				}
				adapter.notifyDataSetChanged();
			});
		}).start();
	}

	/**
	 * Checks if a specific package is installed on the system (individual fallback).
	 *
	 * @param pack The language pack to check.
	 */
	private void checkPackageStatusIndividual(LanguagePack pack) {
		if (pack.checkCommand == null || pack.checkCommand.isEmpty()) {
			pack.status = LanguagePack.STATUS_AVAILABLE;
			return;
		}

		try {
			String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
			String binPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";

			ProcessBuilder pb = new ProcessBuilder(binPath, "-c", pack.checkCommand);
			pb.environment().put("PREFIX", prefix);
			pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
			pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));

			Process process = pb.start();
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

	public void uninstallPackage(LanguagePack pack) {
		progressContainer.setVisibility(View.VISIBLE);
		progressStatusText.setText("Checking freed space...");
		installProgressBar.setIndeterminate(true);

		new Thread(() -> {
			String freedSpace = "unknown";
			try {
				String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
				String binPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";
				String command = "apt-get remove -s " + pack.key;

				ProcessBuilder pb = new ProcessBuilder(binPath, "-c", command);
				pb.environment().put("PREFIX", prefix);
				pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
				pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));
				pb.redirectErrorStream(true);

				Process process = pb.start();
				java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
				String line;
				StringBuilder output = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				process.waitFor();

				java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d.,]+\\s*[kMG]B) disk space will be freed").matcher(output.toString());
				if (m.find()) freedSpace = m.group(1);
			} catch (Exception e) {
				Log.e(TAG, "Failed to check freed space", e);
			}

			final String finalFreedSpace = freedSpace;
			mainHandler.post(() -> {
				progressContainer.setVisibility(View.GONE);
				installProgressBar.setIndeterminate(false);

				new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
						.setTitle(getString(R.string.title_uninstall_pkg, pack.name))
						.setMessage("Disk space that will be freed: " + finalFreedSpace + "\n\n" + getString(R.string.msg_confirm_uninstall, pack.name))
						.setPositiveButton(R.string.label_uninstallation, (dialog, which) -> {
							startTerminalInstallation(getUninstallCommandString(pack), "uninstall " + pack.name.toLowerCase());
						})
						.setNegativeButton(R.string.action_cancel, null)
						.show();
			});
		}).start();
	}

	private String getUninstallCommandString(LanguagePack pack) {
		if (pack.uninstallCommand != null && pack.uninstallCommand.startsWith("uninstall_suggestion:")) {
			String lang = pack.uninstallCommand.substring("uninstall_suggestion:".length());
			File langDir = new File(getFilesDir(), "languages/" + lang);
			return "rm -rf \"" + langDir.getAbsolutePath() + "\"";
		} else {
			return "apt-get remove " + pack.key;
		}
	}

	@Override
	public void onInstallClicked(LanguagePack pack) {
		if (pack.status == LanguagePack.STATUS_AVAILABLE) {
			probePackageSizes(pack);
		} else if (pack.status == LanguagePack.STATUS_READY_TO_INSTALL) {
			showConfirmationDialog(pack);
		}
	}

	@Override
	public void onUninstallClicked(LanguagePack pack) {
		uninstallPackage(pack);
	}

	private void probePackageSizes(LanguagePack pack) {
		pack.status = LanguagePack.STATUS_PROBING;
		adapter.notifyDataSetChanged();

		new Thread(() -> {
			String downloadSize = "unknown";
			String installSize = "unknown";
			try {
				String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
				String binPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";
				String command = "apt-get install -s " + pack.key;

				ProcessBuilder pb = new ProcessBuilder(binPath, "-c", command);
				pb.environment().put("PREFIX", prefix);
				pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
				pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));
				pb.redirectErrorStream(true);

				Process process = pb.start();
				java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
				String line;
				StringBuilder output = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				process.waitFor();

				java.util.regex.Matcher m = java.util.regex.Pattern.compile("Need to get ([\\d.,]+\\s*[kMG]B)").matcher(output.toString());
				if (m.find()) downloadSize = m.group(1);

				m = java.util.regex.Pattern.compile("After this operation, ([\\d.,]+\\s*[kMG]B)").matcher(output.toString());
				if (m.find()) installSize = m.group(1);
			} catch (Exception e) {
				Log.e(TAG, "Failed to check installation sizes", e);
			}

			final String finalDownloadSize = downloadSize;
			final String finalInstallSize = installSize;
			mainHandler.post(() -> {
				pack.downloadSize = finalDownloadSize;
				pack.installSize = finalInstallSize;
				pack.status = LanguagePack.STATUS_READY_TO_INSTALL;
				adapter.notifyDataSetChanged();
			});
		}).start();
	}

	private void showConfirmationDialog(LanguagePack pack) {
		String message = "Download size: " + pack.downloadSize + "\nAdditional disk space: " + pack.installSize + "\n\nDo you want to proceed?";

		LanguagePack companion = findPackByKey(pack.companionKey);
		boolean showCheckbox = companion != null && companion.status != LanguagePack.STATUS_INSTALLED;

		if (showCheckbox) {
			android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
			layout.setOrientation(android.widget.LinearLayout.VERTICAL);
			layout.setPadding(50, 20, 50, 20);

			TextView msgView = new TextView(this);
			msgView.setText(message);
			msgView.setTextColor(android.graphics.Color.WHITE);
			layout.addView(msgView);

			android.widget.CheckBox companionCheckBox = new android.widget.CheckBox(this);
			companionCheckBox.setText("Install the other package for full experience");
			companionCheckBox.setTextColor(android.graphics.Color.WHITE);
			companionCheckBox.setButtonTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
			companionCheckBox.setChecked(pack.type == LanguagePack.TYPE_RUNTIME);
			layout.addView(companionCheckBox);

			new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
					.setTitle("Install " + pack.name)
					.setView(layout)
					.setPositiveButton("Proceed", (dialog, which) -> {
						installPackageInBackground(pack);
						if (companionCheckBox.isChecked()) {
							installPackageInBackground(companion);
						}
					})
					.setNegativeButton(R.string.action_cancel, null)
					.show();
		} else {
			new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
					.setTitle("Install " + pack.name)
					.setMessage(message)
					.setPositiveButton("Proceed", (dialog, which) -> {
						installPackageInBackground(pack);
					})
					.setNegativeButton(R.string.action_cancel, null)
					.show();
		}
	}

	private void installPackageInBackground(LanguagePack pack) {
		pack.status = LanguagePack.STATUS_INSTALLING;
		adapter.notifyDataSetChanged();

		Intent serviceIntent = new Intent(this, LanguageManagerService.class);
		serviceIntent.setAction(LanguageManagerService.ACTION_INSTALL_PACKAGE);
		serviceIntent.putExtra(LanguageManagerService.EXTRA_PACKAGE_KEY, pack.key);
		serviceIntent.putExtra(LanguageManagerService.EXTRA_PACKAGE_NAME, pack.name);
		serviceIntent.putExtra(LanguageManagerService.EXTRA_COMMAND, getInstallCommandString(pack));
		startService(serviceIntent);
	}

	private void startTerminalInstallation(String command, String sessionName) {
		Intent intent = new Intent(this, TermuxActivity.class);
		intent.setAction(Intent.ACTION_RUN);
		intent.setPackage(getPackageName());
		intent.putExtra("run_command", command);
		intent.putExtra("session_name", sessionName);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private String getInstallCommandString(LanguagePack pack) {
		if (pack.installCommand != null && pack.installCommand.startsWith("download:")) {
			String url = pack.installCommand.substring("download:".length());
			String lang = pack.key.replace("_suggestions", "");
			File langDir = new File(getFilesDir(), "languages/" + lang);
			langDir.mkdirs();

			if (url.endsWith(".zip")) {
				return "curl -L \"" + url + "\" -o \"" + langDir.getAbsolutePath() + "/pack.zip\" && unzip -o \"" + langDir.getAbsolutePath() + "/pack.zip\" -d \"" + langDir.getAbsolutePath() + "\" && rm \"" + langDir.getAbsolutePath() + "/pack.zip\"";
			} else {
				String fileName = url.substring(url.lastIndexOf("/") + 1);
				return "curl -L \"" + url + "\" -o \"" + langDir.getAbsolutePath() + "/" + fileName + "\"";
			}
		} else {
			return pack.installCommand != null && !pack.installCommand.isEmpty() ? pack.installCommand : "pkg install -y " + pack.key;
		}
	}

	private LanguagePack findPackByKey(String key) {
		if (key == null) return null;
		for (LanguagePack p : allPacks) {
			if (key.equals(p.key)) return p;
		}
		return null;
	}
}
