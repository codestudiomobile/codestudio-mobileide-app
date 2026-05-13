package com.cs.ide.app.environment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * WorkspaceInitializer is responsible for setting up the physical workspace on the device's storage
 * and requesting necessary permissions through the Storage Access Framework (SAF).
 */
public class WorkspaceInitializer {
    public static final int REQUEST_CODE_SAF = 1001;

    /**
     * Initializes the workspace by creating the 'codestudio' directory and prompting
     * the user to grant SAF access.
     *
     * @param activity The activity from which the initialization is triggered.
     */
    public static void initialize(Activity activity) {
        File codestudio = new File(Environment.getExternalStorageDirectory(), "codestudio");
        if (!codestudio.exists()) {
            codestudio.mkdirs();
        }
        
        File marker = new File(codestudio, ".visible");
        if (!marker.exists()) {
            try {
                marker.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Scan the directory to make it visible to the system media scanner
        MediaScannerConnection.scanFile(activity, new String[]{codestudio.getAbsolutePath()}, null, null);
        
        // Delay the SAF request to allow some UI feedback
        new Handler().postDelayed(() -> {
            Toast.makeText(activity, "Preparing your workspace… please allow access to continue", Toast.LENGTH_SHORT).show();
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:codestudio");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE_SAF);
        }, 3000);
    }

    /**
     * Handles the result of the SAF directory selection.
     *
     * @param context The application context.
     * @param data    The intent containing the selected directory's URI.
     */
    public static void handleSafResult(Context context, Intent data) {
        Uri treeUri = data.getData();
        if (treeUri != null) {
            try {
                context.getContentResolver().takePersistableUriPermission(treeUri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                android.util.Log.e("WorkspaceInitializer", "Failed to take persistable URI permission", e);
            }
        }
        
        SharedPreferences prefs = context.getSharedPreferences("codestudio", Context.MODE_PRIVATE);
        if (treeUri != null) {
            prefs.edit().putString("saf_uri", treeUri.toString()).apply();
        }
        
        // After setting up the URI, initialize the environment directories and scripts
        EnvironmentManager.setupEnvironment(context);
    }
}
