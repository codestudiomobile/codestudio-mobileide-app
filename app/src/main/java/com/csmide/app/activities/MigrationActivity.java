package com.csmide.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.csmide.R;
import com.csmide.termux.shared.logger.Logger;
import com.csmide.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MigrationActivity extends AppCompatActivity {

	private static final String LOG_TAG = "MigrationActivity";
	private static final String BACKUP_FILE_NAME = "termux-backup.tar.gz";
	private static final String INSTRUCTIONS_FILE_NAME = "termux-migration-instructions.txt";
	private static final String HOME_OLD = "home.old";
	private static final String USR_OLD = "usr.old";
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
		new AlertDialog.Builder(this, R.style.CodeStudio_AlertDialog)
				.setTitle(R.string.title_import_backup)
				.setMessage(R.string.msg_confirm_import)
				.setPositiveButton(R.string.action_proceed, (dialog, which) -> startImportTask(uri))
				.setNegativeButton(R.string.action_cancel, (dialog, which) -> btnImportBackup.setEnabled(true))
				.setCancelable(false)
				.show();
	}

	private void startImportTask(Uri uri) {
		btnImportBackup.setEnabled(false);
		pbImport.setVisibility(View.VISIBLE);
		tvImportStatus.setVisibility(View.VISIBLE);
		tvImportStatus.setText(R.string.msg_importing);

		new Thread(() -> {
			try {
				// 1. Copy URI to a temporary file in app storage
				File tempFile = new File(getCacheDir(), BACKUP_FILE_NAME);
				try (InputStream in = getContentResolver().openInputStream(uri);
				     OutputStream out = new FileOutputStream(tempFile)) {
					byte[] buffer = new byte[8192];
					int read;
					if (in != null) {
						while ((read = in.read(buffer)) != -1) {
							out.write(buffer, 0, read);
						}
					}
				}

				// 3. Find tar before moving directories
				String tarPath = null;
				File termuxTar = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tar");
				if (termuxTar.exists()) {
					tarPath = termuxTar.getAbsolutePath();
				}

				// 2. Backup existing home and usr (Don't delete backups until success)
				File homeDir = TermuxConstants.TERMUX_HOME_DIR;
				File usrDir = TermuxConstants.TERMUX_PREFIX_DIR;

				File homeOld = new File(homeDir.getParent(), HOME_OLD);
				File usrOld = new File(usrDir.getParent(), USR_OLD);

				// If previous backups exist and current dirs exist, we must decide.
				// To be safe, let's keep one level of backup and rename existing old to .bak if they exist
				File homeBak = new File(homeDir.getParent(), HOME_OLD + ".bak");
				File usrBak = new File(usrDir.getParent(), USR_OLD + ".bak");
				if (homeOld.exists()) {
					deleteRecursive(homeBak);
					homeOld.renameTo(homeBak);
				}
				if (usrOld.exists()) {
					deleteRecursive(usrBak);
					usrOld.renameTo(usrBak);
				}

				// Move current to old
				boolean homeMoved = false;
				boolean usrMoved = false;
				if (homeDir.exists()) {
					homeMoved = homeDir.renameTo(homeOld);
					if (!homeMoved) {
						Logger.logWarn(LOG_TAG, "Failed to rename home directory to backup.");
					}
				}
				if (usrDir.exists()) {
					usrMoved = usrDir.renameTo(usrOld);
					if (!usrMoved) {
						Logger.logWarn(LOG_TAG, "Failed to rename usr directory to backup.");
					}
				}

				if (!homeDir.exists()) homeDir.mkdirs();
				if (!usrDir.exists()) usrDir.mkdirs();

				// Update tarPath if it was in usrDir and now moved to usrOld
				if (tarPath != null && tarPath.startsWith(usrDir.getAbsolutePath())) {
					tarPath = usrOld.getAbsolutePath() + tarPath.substring(usrDir.getAbsolutePath().length());
				}

				if (tarPath == null || !new File(tarPath).exists()) {
					if (new File("/system/bin/tar").exists()) {
						tarPath = "/system/bin/tar";
					} else if (new File("/system/xbin/tar").exists()) {
						tarPath = "/system/xbin/tar";
					} else {
						// ROLLBACK
						if (homeMoved) {
							deleteRecursive(homeDir);
							homeOld.renameTo(homeDir);
						}
						if (usrMoved) {
							deleteRecursive(usrDir);
							usrOld.renameTo(usrDir);
						}
						throw new Exception(getString(R.string.msg_error_tar_not_found));
					}
				}

				ProcessBuilder pb = new ProcessBuilder(
						tarPath, "-zxpf", tempFile.getAbsolutePath(), "-C", TermuxConstants.TERMUX_FILES_DIR_PATH
				);

				// Set environment variables for Termux tar to find its libraries and helper tools (like gzip) after move
				if (tarPath.startsWith(usrOld.getAbsolutePath())) {
					String binPath = usrOld.getAbsolutePath() + "/bin";
					String libPath = usrOld.getAbsolutePath() + "/lib";
					pb.environment().put("LD_LIBRARY_PATH", libPath);
					pb.environment().put("PATH", binPath + ":" + System.getenv("PATH"));
				}

				pb.redirectErrorStream(true);
				Process process = pb.start();

				StringBuilder tarOutput = new StringBuilder();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						tarOutput.append(line).append("\n");
					}
				}

				int exitCode = process.waitFor();

				if (exitCode == 0) {
					// SUCCESS: Now it's safe to delete old backups if we want, or keep them.
					// Let's keep them for now, but delete .bak
					deleteRecursive(homeBak);
					deleteRecursive(usrBak);

					runOnUiThread(() -> {
						tvImportStatus.setText(R.string.msg_import_success);
						pbImport.setVisibility(View.GONE);
						Toast.makeText(this, R.string.msg_import_success, Toast.LENGTH_LONG).show();
					});
				} else {
					// ROLLBACK on failure
					if (homeMoved) {
						deleteRecursive(homeDir);
						homeOld.renameTo(homeDir);
					}
					if (usrMoved) {
						deleteRecursive(usrDir);
						usrOld.renameTo(usrDir);
					}

					throw new Exception("Tar exited with code " + exitCode + (tarOutput.length() > 0 ? ": " + tarOutput.toString().trim() : ""));
				}

			} catch (Exception e) {
				Logger.logStackTraceWithMessage(LOG_TAG, "Import failed", e);
				runOnUiThread(() -> {
					tvImportStatus.setText(getString(R.string.msg_import_failed, e.getMessage()));
					pbImport.setVisibility(View.GONE);
					btnImportBackup.setEnabled(true);
				});
			} finally {
				File tempFile = new File(getCacheDir(), BACKUP_FILE_NAME);
				if (tempFile.exists()) tempFile.delete();
			}
		}).start();
	}

	private void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] children = fileOrDirectory.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		fileOrDirectory.delete();
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
