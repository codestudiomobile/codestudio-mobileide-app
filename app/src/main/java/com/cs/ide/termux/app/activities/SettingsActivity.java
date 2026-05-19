package com.cs.ide.termux.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cs.ide.R;
import com.cs.ide.termux.app.fragments.settings.TermuxPreferencesFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new TermuxPreferencesFragment())
                .commit();
        }
    }

}
