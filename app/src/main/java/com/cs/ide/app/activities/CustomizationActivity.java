package com.cs.ide.app.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.cs.ide.R;
import com.cs.ide.app.utils.AppPreferences;
import com.cs.ide.termux.shared.logger.Logger;
import com.cs.ide.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles terminal console personalization.
 * Allows users to visually configure the ASCII banner and prompt title shown in the integrated terminal.
 * Changes are applied via background shell scripts that modify the Termux environment configuration.
 */
public class CustomizationActivity extends AppCompatActivity {

	private static final String LOG_TAG = "CustomizationActivity";
	private final Map<Character, String[]> asciiMap = new HashMap<>();
	private EditText etTitleText, etBannerText;
	private TextView tvTitlePreview, tvBannerPreview;
	private Button btnApply;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_customization);

		initAsciiMap();

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		etTitleText = findViewById(R.id.etTitleText);
		etBannerText = findViewById(R.id.etBannerText);
		tvTitlePreview = findViewById(R.id.tvTitlePreview);
		tvBannerPreview = findViewById(R.id.tvBannerPreview);
		btnApply = findViewById(R.id.btnApply);

		SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		String currentTitle = prefs.getString(AppPreferences.KEY_TITLE_TEXT, "Code Studio");
		String currentBanner = prefs.getString(AppPreferences.KEY_BANNER_TEXT, getString(R.string.default_banner_text));

		etTitleText.setText(currentTitle);
		etBannerText.setText(currentBanner);

		updatePreview();

		etTitleText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updatePreview();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		etBannerText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updatePreview();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		btnApply.setOnClickListener(v -> applyCustomization());
	}

	private void updatePreview() {
		String title = etTitleText.getText().toString();
		String bannerText = etBannerText.getText().toString();

		tvTitlePreview.setText("Prompt Preview: " + title + " $");
		tvBannerPreview.setText(generateBanner(bannerText));
	}

	private String generateBanner(String text) {
		text = text.toUpperCase();
		String[] words = text.split("\\s+");
		StringBuilder fullBanner = new StringBuilder();

		for (String word : words) {
			if (word.isEmpty()) continue;

			String[] lines = new String[6];
			for (int i = 0; i < 6; i++) lines[i] = "";

			for (char c : word.toCharArray()) {
				String[] charLines = asciiMap.get(c);
				if (charLines != null) {
					for (int i = 0; i < 6; i++) {
						lines[i] += charLines[i];
					}
				}
			}

			for (int i = 0; i < 6; i++) {
				fullBanner.append(lines[i]).append("\n");
			}
			fullBanner.append("\n");
		}

		return fullBanner.toString();
	}

	private void applyCustomization() {
		String title = etTitleText.getText().toString();
		String banner = etBannerText.getText().toString();

		SharedPreferences prefs = getSharedPreferences(AppPreferences.PREFERENCE_NAME, Context.MODE_PRIVATE);
		prefs.edit()
				.putString(AppPreferences.KEY_TITLE_TEXT, title)
				.putString(AppPreferences.KEY_BANNER_TEXT, banner)
				.apply();

		try {
			runScript("apply-banner.sh", banner);
			runScript("apply-title.sh", title);
			Toast.makeText(this, "Customization applied successfully!", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply customization", e);
			Toast.makeText(this, "Failed to apply: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void runScript(String scriptName, String arg) throws Exception {
		File binDir = new File(getFilesDir(), "bin");
		if (!binDir.exists()) binDir.mkdirs();
		File scriptFile = new File(binDir, scriptName);

		try (InputStream in = getAssets().open(scriptName);
		     OutputStream out = new FileOutputStream(scriptFile)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
		scriptFile.setExecutable(true);

		String bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
		String[] command = new String[]{bashPath, scriptFile.getAbsolutePath(), arg};

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
		pb.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
		pb.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
		pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");

		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new Exception("Script " + scriptName + " failed with exit code " + exitCode);
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

	private void initAsciiMap() {
		asciiMap.put('A', new String[]{"░█████═╗░", "██║░░██║░", "███████║░", "██╔═╗██║░", "██║░║██║░", "╚═╝░╚══╝░"});
		asciiMap.put('B', new String[]{"██████╗░░", "██░░░██║░", "██████║░░", "██░░░██║░", "██████╝░░", "╚═════╝░░"});
		asciiMap.put('C', new String[]{"░██████╗░", "██╔════╝░", "██║░░░░░░", "██╚════╗░", "╚██████║░", "░╚═════╝░"});
		asciiMap.put('D', new String[]{"███████═╗░░", "██╔═══╗██║░", "██║░░░║██║░", "██╚═══╝██║░", "███████═╝░░", "╚═════╝░░░░"});
		asciiMap.put('E', new String[]{"░██████╗░", "██╚════╗░", "███████║░", "██╚════╗░", "╚██████║░", "░╚═════╝░"});
		asciiMap.put('F', new String[]{"░██████╗░", "██╚════╗░", "███████║░", "██╔════╝░", "██║░░░░░░", "╚═╝░░░░░░"});
		asciiMap.put('G', new String[]{"░██████╗░", "██╚════╗░", "██ ████║░", "██   ██║░", "╚████╔═╝░", "░╚═══╝░░░"});
		asciiMap.put('H', new String[]{"██╗░░░██╗░", "██║░░░██║░", "████████║░", "██╔══╗██║░", "██║░░║██║░", "╚═╝░░╚══╝░"});
		asciiMap.put('I', new String[]{"████████╗░", "╚══██╔══╝░", "░░░██║░░░░", "░░░██║░░░░", "████████╝░", "╚══════╝░░"});
		asciiMap.put('J', new String[]{"░░╔█████╗░", "░░╚═══██║░", "░░░░░░██║░", "░╔██░░██║░", "░╚╗████╝░░", "░░╚════╝░░"});
		asciiMap.put('K', new String[]{"██╗░░██╗░", "██║░██╝░░", "████╝░░░░", "██║░██═╗░", "██║░░██║░", "╚═╝░░╚═╝░"});
		asciiMap.put('L', new String[]{"██╗░░░░░░", "██║░░░░░░", "██║░░░░░░", "██╚════╗░", "╚██████║░", "░╚═════╝░"});
		asciiMap.put('M', new String[]{"████╗░████╗░", "██░████░██║░", "██╔╗██╔═██║░", "██║╚══╝░██║░", "██║░░░░░██║░", "╚═╝░░░░░╚═╝░"});
		asciiMap.put('N', new String[]{"████╗░░░██╗░", "██░██╗░░██║░", "██╔╗██╗░██║░", "██║╚═██░██║░", "██║░░░████║░", "╚═╝░░░░░╚═╝░"});
		asciiMap.put('O', new String[]{"░░██████╗░░░", "██╔════╗██╗░", "██║░░░░║██║░", "██╚════╝██║░", "░╚███████╝░░", "░░╚═════╝░░░"});
		asciiMap.put('P', new String[]{"██████═╗░", "██╔═╗██║░", "█████╔═╝░", "██╔══╝░░░", "██║░░░░░░", "╚═╝░░░░░░"});
		asciiMap.put('Q', new String[]{"░░███████═╗░░", "░█░░░░░░░█╚╗░", "██░░██░░░██║░", "░█░░░██░░█═╝░", "░░███░██░░░░░", "░░░░░░░░██░░░"});
		asciiMap.put('R', new String[]{"██████═╗░░", "██╔═╗██╝░░", "█████░░░░░", "██╔═╝██═╗░", "██║░░░██║░", "╚═╝░░░╚═╝░"});
		asciiMap.put('S', new String[]{"░░██████╗░", "░██╔════╝░", "░╚█████╗░░", "░░░╚═══██╗", "░╚██████╔╝", "░░╚═════╝░"});
		asciiMap.put('T', new String[]{"████████╗░", "╚══██╔══╝░", "░░░██║░░░░", "░░░██║░░░░", "░░░██║░░░░", "░░░╚═╝░░░░"});
		asciiMap.put('U', new String[]{"██╗░░██╗░", "██║░░██║░", "██║░░██║░", "██║░░██║░", "███████║░", "╚══════╝░"});
		asciiMap.put('V', new String[]{"██╗░░░░░░██╗░", "╚██╗░░░░██╔╝░", "░╚██╗░░██╔╝░░", "░░╚██╗██╔╝░░░", "░░░╚███╔╝░░░░", "░░░░╚══╝░░░░░"});
		asciiMap.put('W', new String[]{"██░░░░░░██╗░", "██░╔══╗░██║░", "██╔╝██╚╗██║░", "██║████║██║░", "████╝░████║░", "╚══╝░░╚══╝░░"});
		asciiMap.put('X', new String[]{"██╗░░░ ██╗░", "░ ██╗ ██╔╝░", "░░╔╝██╚═╗░░", "╔╝██╝ ██╚╗░", "██╔╝░ ░██║░", "╚═╝░░░ ╚═╝░"});
		asciiMap.put('Y', new String[]{"░██╗░░░██╗░", "░╚██╗░██╔╝░", "░░╚████╔╝░░", "░░░░██╔╝░░░", "░░░░██║░░░░", "░░░░╚═╝░░░░"});
		asciiMap.put('Z', new String[]{"████████╗░", "░░░░░██╔╝░", "░░░██╔═╝░░", "░██══╝░░░░", "████████╗░", "╚═══════╝░"});
	}
}
