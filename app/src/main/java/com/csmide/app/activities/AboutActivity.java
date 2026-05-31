package com.csmide.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.csmide.R;
import com.csmide.app.utils.DisplayManager;

/**
 * AboutActivity displays information about the Code Studio Mobile IDE,
 * such as version, description, and links.
 */
public class AboutActivity extends AppCompatActivity {
	private View rootLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about_code_studio);

		Toolbar toolbar = findViewById(R.id.toolbar);
		rootLayout = findViewById(R.id.aboutLayout);

		// Handle dynamic window insets for edge-to-edge display
		if (rootLayout != null) {
			ViewCompat.setOnApplyWindowInsetsListener(rootLayout, DisplayManager::setupDynamicMarginHandling);
		}

		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setTitle(R.string.menu_about);
		}

		setupClickListeners();
	}

	private void setupClickListeners() {
		View layoutMail = findViewById(R.id.layout_mail);
		View layoutShare = findViewById(R.id.layout_share);

		if (layoutMail != null) {
			layoutMail.setOnClickListener(v -> {
				Intent intent = new Intent(Intent.ACTION_SENDTO);
				intent.setData(Uri.parse("mailto:codestudiomobile@gmail.com"));
				intent.putExtra(Intent.EXTRA_SUBJECT, "Code Studio Mobile IDE - Feedback");
				try {
					startActivity(Intent.createChooser(intent, "Send Email"));
				} catch (android.content.ActivityNotFoundException ex) {
					Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show();
				}
			});
		}

		if (layoutShare != null) {
			layoutShare.setOnClickListener(v -> {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "Check out Code Studio Mobile IDE: https://github.com/codestudiomobile/codestudio-mobileide");
				startActivity(Intent.createChooser(intent, "Share via"));
			});
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		// Handle back button click in the toolbar
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
