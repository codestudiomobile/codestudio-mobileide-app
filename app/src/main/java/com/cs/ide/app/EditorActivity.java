package com.cs.ide.app;
import static com.cs.ide.app.AppPreferences.KEY_EDITOR_STARTUP;
import static com.cs.ide.app.AppPreferences.KEY_WELCOME_STARTUP;
import static com.cs.ide.app.AppPreferences.PREFERENCE_NAME;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import com.cs.ide.R;
public class EditorActivity extends AppCompatActivity {
    private SwitchCompat openEditorOnStartup;
    private SwitchCompat openWelcomeScreenOnStartup;
    private View menuContentContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor_code_studio);
        Toolbar toolbar = findViewById(R.id.toolbar);
        menuContentContainer = findViewById(R.id.editorLayout);
        ViewCompat.setOnApplyWindowInsetsListener(menuContentContainer, DisplayManager::setupDynamicMarginHandling);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        openEditorOnStartup = findViewById(R.id.openEditorOnStartup);
        openWelcomeScreenOnStartup = findViewById(R.id.openWelcomeScreenOnStartup);
        SharedPreferences preferences = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        boolean editorStartup = preferences.getBoolean(KEY_EDITOR_STARTUP, false);
        boolean welcomeStartup = preferences.getBoolean(KEY_WELCOME_STARTUP, true);
        openEditorOnStartup.setChecked(editorStartup);
        openWelcomeScreenOnStartup.setChecked(welcomeStartup);
        openEditorOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = preferences.edit();
            if (!isChecked && !openWelcomeScreenOnStartup.isChecked()) {
                editor.putBoolean(KEY_WELCOME_STARTUP, true);
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_EDITOR_STARTUP, isChecked);
            }
            editor.apply();
        });
        openWelcomeScreenOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = preferences.edit();
            if (!isChecked && !openEditorOnStartup.isChecked()) {
                editor.putBoolean(KEY_EDITOR_STARTUP, false);
                editor.putBoolean(KEY_WELCOME_STARTUP, true);
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_WELCOME_STARTUP, isChecked);
            }
            editor.apply();
        });
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
