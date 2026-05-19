package com.cs.ide.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cs.ide.app.models.FileItem;

import java.io.File;

/**
 * Utility class for file-related operations, including MIME type detection,
 * file name extraction, and identifying external viewer requirements.
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * Gets the MIME type of the given URI. It first attempts to use ContentResolver,
     * then falls back to extension-based detection for common programming languages.
     *
     * @param context The application context.
     * @param uri     The URI of the file.
     * @return The detected MIME type string.
     */
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        try {
            mimeType = context.getContentResolver().getType(uri);
        } catch (Exception e) {
            Log.e(TAG, "Error getting MIME type", e);
        }
        if (mimeType == null || mimeType.equals("text/plain") || mimeType.equals("application/octet-stream")) {
            String extension = null;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String name = cursor.getString(nameIndex);
                            if (name != null && name.contains(".")) {
                                extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error querying MIME type", e);
                }
            } else {
                extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            }
            if (extension != null) {
                switch (extension) {
                    case "java":
                        return "text/x-java-source";
                    case "py":
                        return "text/x-python";
                    case "c":
                        return "text/x-c";
                    case "cpp":
                    case "cxx":
                    case "cc":
                        return "text/x-cpp";
                    case "js":
                        return "application/javascript";
                    case "php":
                        return "text/x-php";
                    case "rb":
                        return "text/x-ruby";
                    case "go":
                        return "text/x-go";
                    case "kt":
                        return "text/x-kotlin";
                    case "sh":
                    case "bash":
                        return "text/x-shellscript";
                    case "cs":
                        return "text/x-csharp";
                    case "pl":
                        return "text/x-perl";
                    case "lua":
                        return "text/x-lua";
                    default:
                        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            }
        }
        return mimeType;
    }

    /**
     * Extracts the display name for a file from its URI.
     *
     * @param context The application context.
     * @param uri     The URI of the file.
     * @return The file name string, or "untitled" if not found.
     */
    public static String getFileName(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from Uri: " + uri, e);
            }
        }
        if (fileName == null && uri.getLastPathSegment() != null) {
            fileName = uri.getLastPathSegment();
        }
        return (fileName != null && !fileName.isEmpty()) ? fileName : "untitled";
    }

    /**
     * Creates a FileItem model object representing the file at the given URI.
     *
     * @param context The application context.
     * @param uri     The URI of the file.
     * @return A new FileItem instance.
     */
    public static FileItem getFileItemFromUri(Context context, Uri uri) {
        String name = getFileName(context, uri);
        String mimeType = getMimeType(context, uri);
        return new FileItem(context, uri, name, mimeType, true, 0);
    }

    /**
     * Determines if a MIME type should be handled by an external application
     * (e.g., images, videos, PDFs) instead of the internal text editor.
     *
     * @param mimeType The MIME type string.
     * @return True if it requires an external viewer, false otherwise.
     */
    public static boolean isExternalViewType(String mimeType) {
        if (mimeType == null) return false;
        String lowerMimeType = mimeType.toLowerCase();
        return lowerMimeType.startsWith("image/") || lowerMimeType.startsWith("video/") || lowerMimeType.startsWith("audio/") ||
                lowerMimeType.equals("application/pdf") ||
                lowerMimeType.contains("msword") ||
                lowerMimeType.contains("vnd.openxmlformats-officedocument") ||
                lowerMimeType.contains("zip") ||
                lowerMimeType.contains("rar") || lowerMimeType.contains("octet-stream");
    }

    /**
     * Maps a file name extension to a simplified internal type key.
     *
     * @param fileName The name of the file.
     * @return A short string key representing the file type.
     */
    public static String getFileTypeKey(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "txt";
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "java": return "java";
            case "py": return "python";
            case "c": return "c";
            case "cpp": return "cpp";
            case "js": return "javascript";
            case "html": return "html";
            case "css": return "css";
            case "sh": return "shell";
            default: return extension;
        }
    }

    /**
     * Checks if a URI represents a directory using DocumentsContract.
     *
     * @param context The application context.
     * @param uri     The URI to check.
     * @return True if it is a directory.
     */
    public static boolean isDirectory(Context context, Uri uri) {
        if (uri == null) return false;
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String type = null;
                try (Cursor cursor = context.getContentResolver().query(uri, 
                        new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        type = cursor.getString(0);
                    }
                }
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if URI is directory: " + uri, e);
        }
        return false;
    }

    /**
     * Attempts to resolve a URI to an absolute file path.
     *
     * @param context The application context.
     * @param uri     The URI to resolve.
     * @return The absolute path string, or null if resolution fails.
     */
    @Nullable
    public static String getAbsolutePathFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Special handling for our own document provider
            if ("com.cs.ide.documents".equals(uri.getAuthority())) {
                return DocumentsContract.getDocumentId(uri);
            }

            if (DocumentsContract.isDocumentUri(context, uri) || DocumentsContract.isTreeUri(uri)) {
                // ExternalStorageProvider
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    final String docId = DocumentsContract.isTreeUri(uri) ? 
                            DocumentsContract.getTreeDocumentId(uri) : DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                        if (split.length > 1) {
                            path += "/" + split[1];
                        }
                        return path;
                    }
                }
                // DownloadsProvider
                else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    if (id != null && id.startsWith("raw:")) {
                        return id.substring(4);
                    }
                }
            }
            
            // Try querying the data column as a fallback
            return getDataColumn(context, uri, null, null);
        }

        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        final String column = "_data";
        final String[] projection = {column};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndex(column);
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting data column", e);
        }
        return null;
    }
}
