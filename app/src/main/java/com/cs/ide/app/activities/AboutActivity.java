package com.cs.ide.app.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.cs.ide.R;
import com.cs.ide.app.utils.DisplayManager;

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
