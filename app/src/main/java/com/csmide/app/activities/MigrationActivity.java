package com.csmide.app.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.csmide.R;
import com.csmide.app.services.MigrationService;
import com.csmide.termux.shared.logger.Logger;
import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.OutputStream;

public class MigrationActivity extends AppCompatActivity {

	private static final String LOG_TAG = "MigrationActivity";
	private static final String INSTRUCTIONS_FILE_NAME = "termux-migration-instructions.txt";
	private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument("text/plain"),
			uri -> {
				if (uri != null) {
					saveInstructionsToUri(uri);
				}
			}
	);
	private TextView tvImportStatus;
	private Button btnCopyScript, btnImportBackup, btnExportInstructions;
	private ProgressBar pbImport;

	private final BroadcastReceiver migrationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (MigrationService.ACTION_PROGRESS_UPDATE.equals(intent.getAction())) {
				String text = intent.getStringExtra(MigrationService.EXTRA_STATUS_TEXT);
				boolean isComplete = intent.getBooleanExtra(MigrationService.EXTRA_IS_COMPLETE, false);
				boolean isSuccess = intent.getBooleanExtra(MigrationService.EXTRA_IS_SUCCESS, false);

				tvImportStatus.setVisibility(View.VISIBLE);
				tvImportStatus.setText(text);
				pbImport.setVisibility(isComplete ? View.GONE : View.VISIBLE);

				if (isComplete) {
					btnImportBackup.setEnabled(true);
					if (isSuccess) {
						Toast.makeText(MigrationActivity.this, R.string.msg_import_success, Toast.LENGTH_LONG).show();
					}
				} else {
					btnImportBackup.setEnabled(false);
				}
			}
		}
	};
	private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					Uri uri = result.getData().getData();
					if (uri != null) {
						String path = uri.getPath();
						if (path != null && !path.endsWith(".tar.gz") && !path.endsWith(".tgz")) {
							Toast.makeText(this, R.string.msg_invalid_backup_file, Toast.LENGTH_SHORT).show();
						}
						importBackup(uri);
					}
				}
			}
	);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_migration);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setTitle(R.string.title_migration);
		}

		TextView tvExportScript = findViewById(R.id.tvExportScript);
		tvImportStatus = findViewById(R.id.tvImportStatus);
		btnCopyScript = findViewById(R.id.btnCopyScript);
		btnExportInstructions = findViewById(R.id.btnExportInstructions);
		btnImportBackup = findViewById(R.id.btnImportBackup);
		pbImport = findViewById(R.id.pbImport);

		String exportScript = """
				termux-setup-storage
				tar -zcvf /sdcard/Download/termux-backup.tar.gz -C /data/data/com.termux/files ./home ./usr""";

		tvExportScript.setText(exportScript);

		btnCopyScript.setOnClickListener(v -> {
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("Termux Backup Script", exportScript);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, R.string.msg_script_copied, Toast.LENGTH_SHORT).show();
		});

		btnExportInstructions.setOnClickListener(v -> createDocumentLauncher.launch(INSTRUCTIONS_FILE_NAME));

		btnImportBackup.setOnClickListener(v -> {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			filePickerLauncher.launch(Intent.createChooser(intent, "Select Backup File"));
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(MigrationService.ACTION_PROGRESS_UPDATE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(migrationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(migrationReceiver, filter);
		}

		// Request current status if service is running
		Intent intent = new Intent(this, MigrationService.class);
		intent.setAction(MigrationService.ACTION_GET_STATUS);
		startService(intent);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(migrationReceiver);
	}

	private void saveInstructionsToUri(Uri uri) {
		String instructions = getString(R.string.migration_instructions_content);

		try (OutputStream out = getContentResolver().openOutputStream(uri)) {
			if (out != null) {
				out.write(instructions.getBytes());
				Toast.makeText(this, R.string.msg_instructions_saved, Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save instructions", e);
			Toast.makeText(this, getString(R.string.msg_failed_save_instructions, e.getMessage()), Toast.LENGTH_LONG).show();
		}
	}

	private void importBackup(Uri uri) {
		View v = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_migration, null);
		Button proceedBtn = v.findViewById(R.id.proceed);
		Button cancelBtn = v.findViewById(R.id.cancel);

		final AlertDialog dialog = new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
				.setView(v)
				.setCancelable(false)
				.create();

		proceedBtn.setOnClickListener(view -> {
			startImportTask(uri);
			dialog.dismiss();
		});

		cancelBtn.setOnClickListener(view -> {
			btnImportBackup.setEnabled(true);
			dialog.dismiss();
		});

		dialog.show();
	}

	private void startImportTask(Uri uri) {
		btnImportBackup.setEnabled(false);
		pbImport.setVisibility(View.VISIBLE);
		tvImportStatus.setVisibility(View.VISIBLE);
		tvImportStatus.setText(R.string.msg_importing);

		Intent intent = new Intent(this, MigrationService.class);
		intent.setAction(MigrationService.ACTION_START_IMPORT);
		intent.putExtra(MigrationService.EXTRA_BACKUP_URI, uri);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(intent);
		} else {
			startService(intent);
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
}
