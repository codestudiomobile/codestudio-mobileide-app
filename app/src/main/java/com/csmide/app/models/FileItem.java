package com.csmide.app.models;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.csmide.R;

/**
 * Represents a file or folder item in the file explorer.
 * Holds metadata and UI-related information like icons and expansion state.
 */
public class FileItem {
	/**
	 * Tag for logging.
	 */
	private static final String TAG = "FileItem";

	/**
	 * URI of the file or folder.
	 */
	public Uri uri;
	/**
	 * Name displayed in the file list.
	 */
	public String displayName;
	/**
	 * True if this item is a directory.
	 */
	public boolean isDirectory;
	/**
	 * True if the folder is currently expanded in the UI.
	 */
	public boolean isExpanded;
	/**
	 * Indentation level in the hierarchical list.
	 */
	public int depth;
	/**
	 * MIME type of the file.
	 */
	public String mimeType;
	/**
	 * Resource ID of the icon representing the file type.
	 */
	public int iconResource;

	/**
	 * Constructor for FileItem.
	 *
	 * @param context     Context to resolve MIME type.
	 * @param uri         URI of the item.
	 * @param displayName Name to display.
	 * @param isDirectory True if it's a folder.
	 * @param depth       Hierarchical depth.
	 */
	public FileItem(Context context, Uri uri, String displayName, boolean isDirectory, int depth) {
		this.uri = uri;
		this.displayName = displayName;
		this.isDirectory = isDirectory;
		this.isExpanded = false;
		this.depth = depth;
		this.mimeType = resolveMimeType(context, uri);
		updateIconResource(this.mimeType);
	}

	/**
	 * Constructor for FileItem when MIME type is already known.
	 *
	 * @param uri         URI of the item.
	 * @param displayName Name to display.
	 * @param isDirectory True if it's a folder.
	 * @param depth       Hierarchical depth.
	 * @param mimeType    Pre-resolved MIME type.
	 */
	public FileItem(Uri uri, String displayName, boolean isDirectory, int depth, String mimeType) {
		this.uri = uri;
		this.displayName = displayName;
		this.isDirectory = isDirectory;
		this.isExpanded = false;
		this.depth = depth;
		this.mimeType = mimeType;
		updateIconResource(this.mimeType);
	}

	/**
	 * Constructor for FileItem with optional pre-resolved MIME type.
	 *
	 * @param context     Context to resolve MIME type if needed.
	 * @param uri         URI of the item.
	 * @param displayName Name to display.
	 * @param mimeType    Optional pre-resolved MIME type.
	 * @param isDirectory True if it's a folder.
	 * @param depth       Hierarchical depth.
	 */
	public FileItem(Context context, Uri uri, String displayName, String mimeType, boolean isDirectory, int depth) {
		this.uri = uri;
		this.displayName = displayName;
		this.isDirectory = isDirectory;
		this.isExpanded = false;
		this.depth = depth;
		this.mimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : resolveMimeType(context, uri);
		updateIconResource(this.mimeType);
	}

	/**
	 * Resolves the MIME type of a URI using ContentResolver or file extension.
	 *
	 * @param context Context for ContentResolver.
	 * @param uri     URI to resolve.
	 * @return Resolved MIME type or "application/octet-stream" if unknown.
	 */
	public static String resolveMimeType(Context context, Uri uri) {
		String type = null;
		try {
			type = context.getContentResolver().getType(uri);
		} catch (Exception e) {
			Log.e(TAG, "Error getting MIME type: " + uri, e);
		}

		if (type == null) {
			String url = uri.toString();
			int queryIndex = url.indexOf('?');
			if (queryIndex != -1) url = url.substring(0, queryIndex);

			String extension = MimeTypeMap.getFileExtensionFromUrl(url);
			if (extension != null && !extension.isEmpty()) {
				type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.trim().toLowerCase());
			}
		}
		return type != null ? type : "application/octet-stream";
	}

	/**
	 * Updates the icon resource based on the MIME type.
	 */
	public void updateIconResource(String mimeType) {
		if (isDirectory) {
			this.iconResource = isExpanded ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed;
			return;
		}

		if (mimeType == null) {
			this.iconResource = R.drawable.ic_unsupported_file;
		} else if (mimeType.startsWith("image/")) {
			this.iconResource = R.drawable.ic_image_file;
		} else if (mimeType.startsWith("audio/")) {
			this.iconResource = R.drawable.ic_audio_file;
		} else if (mimeType.startsWith("video/")) {
			this.iconResource = R.drawable.ic_video_file;
		} else if (mimeType.startsWith("text/") || mimeType.equals("application/json") || mimeType.equals("application/xml")) {
			this.iconResource = R.drawable.ic_text_file;
		} else {
			this.iconResource = R.drawable.ic_unsupported_file;
		}
	}
}
