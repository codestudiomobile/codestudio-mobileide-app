package com.cs.ide.app;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.cs.ide.R;
public class SplashScreen extends AppCompatActivity {
    public static final String TAG = "SplashScreen";
    private static final long SPLASH_TIME_OUT = 2000L;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen_code_studio);
        final Intent receivedIntent = getIntent();
        new Handler().postDelayed(() -> {
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
