package com.cs.ide.app;

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

public class WorkspaceInitializer {
    public static final int REQUEST_CODE_SAF = 1001;

    public static void initialize(Activity activity) {
        File codestudio = new File(Environment.getExternalStorageDirectory(), "codestudio");
        if (!codestudio.exists()) codestudio.mkdirs();
        File marker = new File(codestudio, ".visible");
        if (!marker.exists()) {
            try {
                marker.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MediaScannerConnection.scanFile(activity, new String[]{codestudio.getAbsolutePath()}, null, null);
        new Handler().postDelayed(() -> {
            Toast.makeText(activity, "Preparing your workspace… please allow access to continue", Toast.LENGTH_SHORT).show();
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:codestudio");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE_SAF);
        }, 3000);
    }

    public static void handleSafResult(Context context, Intent data) {
        Uri treeUri = data.getData();
        context.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        SharedPreferences prefs = context.getSharedPreferences("codestudio", Context.MODE_PRIVATE);
        prefs.edit().putString("saf_uri", treeUri.toString()).apply();
        EnvironmentManager.setupEnvironment(context);
    }
}
