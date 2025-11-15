package com.cs.ide.app;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import com.cs.ide.R;
public class AboutActivity extends AppCompatActivity {
    private View menuContentContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_code_studio);
        Toolbar toolbar = findViewById(R.id.toolbar);
        menuContentContainer = findViewById(R.id.aboutLayout);
        ViewCompat.setOnApplyWindowInsetsListener(menuContentContainer, DisplayManager::setupDynamicMarginHandling);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
