package com.cs.ide.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.cs.ide.R;

/**
 * SplashScreen is the entry point of the application.
 * It displays the app logo for a few seconds and handles incoming file intents
 * before redirecting to the MainActivity.
 */
public class SplashScreen extends AppCompatActivity {
    public static final String TAG = "SplashScreen";
    /** Timeout duration for the splash screen in milliseconds. */
    private static final long SPLASH_TIME_OUT = 2000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen_code_studio);
        
        final Intent receivedIntent = getIntent();
        
        // Delay navigation to MainActivity to show splash logo
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // If we received a file open intent from an external app
            if (Intent.ACTION_VIEW.equals(receivedIntent.getAction()) && receivedIntent.getData() != null) {
                Log.d(TAG, "Received external file intent. Passing to MainActivity handler.");
                MainActivity.handleFileIntent(getApplicationContext(), receivedIntent);
                finish();
            } else {
                Log.d(TAG, "Regular app launch.");
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                finish();
            }
        }, SPLASH_TIME_OUT);
    }
}
