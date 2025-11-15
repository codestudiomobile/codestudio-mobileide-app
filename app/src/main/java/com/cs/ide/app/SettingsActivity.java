package com.cs.ide.app;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import com.cs.ide.R;
public class SettingsActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    String[] itemsList = {"Manage libraries", "Editor", "About"};
    ArrayAdapter<String> adapter;
    private View menuContentContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_code_studio);
        Toolbar toolbar = findViewById(R.id.toolbar);
        menuContentContainer = findViewById(R.id.settingsLayout);
        ViewCompat.setOnApplyWindowInsetsListener(menuContentContainer, DisplayManager::setupDynamicMarginHandling);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        ListView settingsList = findViewById(R.id.settingName);
        adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, itemsList);
        settingsList.setAdapter(adapter);
        settingsList.setOnItemClickListener(SettingsActivity.this);
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String selectedItem = (String) parent.getItemAtPosition(position);
        Intent intent;
        if (selectedItem.equals("Manage libraries")) {
            intent = new Intent(getApplicationContext(), ManageLanguagesActivity.class);
            startActivity(intent);
        } else if (selectedItem.equals("Editor")) {
            intent = new Intent(getApplicationContext(), EditorActivity.class);
            startActivity(intent);
        } else if (selectedItem.equals("About")) {
            intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
        }
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
