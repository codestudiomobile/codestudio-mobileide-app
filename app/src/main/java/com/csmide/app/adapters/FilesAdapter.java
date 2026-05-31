package com.csmide.app.adapters;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.csmide.R;
import com.csmide.app.activities.MainActivity;
import com.csmide.app.models.FileItem;
import com.csmide.app.utils.FontManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for the file explorer in the navigation drawer.
 * Supports hierarchical folder expansion and file click listeners.
 */
public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.FileViewHolder> {

	// --- Constants ---
	private static final String TAG = "FilesAdapter";

	// --- Fields ---
	public final List<FileItem> fileList;
	private final Context context;
	private final OnFileClickListener onFileClickListener;
	private final Uri rootFolderUri;

	// --- Constructor ---

	public FilesAdapter(Context context, List<FileItem> fileList, OnFileClickListener onFileClickListener, Uri rootFolderUri) {
		this.context = context;
		this.fileList = fileList;
		this.onFileClickListener = onFileClickListener;
		this.rootFolderUri = rootFolderUri;
	}

	// --- RecyclerView Overrides ---

	@NonNull
	@Override
	public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_list_code_studio, parent, false);
		return new FileViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
		FileItem item = fileList.get(position);
		holder.bind(item);
		int paddingStart = item.depth * 24;
		ViewCompat.setPaddingRelative(holder.itemView, paddingStart, holder.itemView.getPaddingTop(),
				holder.itemView.getPaddingEnd(), holder.itemView.getPaddingBottom());
	}

	// --- Public Adapter Methods ---

	@Override
	public int getItemCount() {
		return fileList.size();
	}

	public void refresh() {
		if (rootFolderUri == null) return;
		fileList.clear();
		FileItem rootItem = new FileItem(context, rootFolderUri, context.getString(R.string.project_root), true, 0);
		fileList.add(rootItem);
		notifyDataSetChanged();
	}

	public void collapseAllFolders() {
		if (context instanceof Activity) {
			((Activity) context).runOnUiThread(() -> {
				for (int i = fileList.size() - 1; i >= 0; i--) {
					FileItem item = fileList.get(i);
					if (item.depth > 0) fileList.remove(i);
					if (item.isDirectory) item.isExpanded = false;
				}
				notifyDataSetChanged();
			});
		}
	}

	// --- Persistence & Saving Logic ---

	public void updateFileItem(Uri oldUri, Uri newUri, String newName, boolean isDirectory, Context context) {
		for (int i = 0; i < fileList.size(); i++) {
			FileItem item = fileList.get(i);
			if (item.uri.equals(oldUri)) {
				item.uri = newUri;
				item.displayName = newName;
				item.isDirectory = isDirectory;
				item.mimeType = FileItem.resolveMimeType(context, newUri);
				item.updateIconResource(item.mimeType);
				notifyItemChanged(i);
				break;
			}
		}
	}

	public void saveAllFiles(List<FileContentItem> filesToSave) {
		if (filesToSave == null || filesToSave.isEmpty()) return;
		new Thread(() -> {
			for (FileContentItem file : filesToSave) {
				try (OutputStream os = context.getContentResolver().openOutputStream(file.uri())) {
					if (os != null) os.write(file.content());
				} catch (IOException e) {
					Log.e(TAG, "Error saving file " + file.uri(), e);
				}
			}
		}).start();
	}

	// --- Inner Classes & Interfaces ---

	/**
	 * Interface for receiving file click and long-press events.
	 */
	public interface OnFileClickListener {
		void onFileClicked(Uri fileUri, String fileName);

		void onFileLongClick(View view, FileItem fileItem);
	}

	/**
	 * Model representing a file's URI and content for bulk saving.
	 */
	public record FileContentItem(Uri uri, byte[] content) {


	}

	/**
	 * ViewHolder class for file and folder items in the explorer.
	 */
	class FileViewHolder extends RecyclerView.ViewHolder {
		private final ImageView fileIcon;
		private final TextView fileName;

		public FileViewHolder(@NonNull View itemView) {
			super(itemView);
			fileIcon = itemView.findViewById(R.id.fileIcon);
			fileName = itemView.findViewById(R.id.fileName);
			itemView.setOnClickListener(v -> handleItemClick());
			itemView.setOnLongClickListener(v -> {
				int pos = getAdapterPosition();
				if (pos != RecyclerView.NO_POSITION) {
					onFileClickListener.onFileLongClick(v, fileList.get(pos));
					return true;
				}
				return false;
			});
		}

		public void bind(@NonNull FileItem item) {
			fileName.setText(item.displayName);
			fileName.setTypeface(FontManager.getTypeface(context));
			if (item.isDirectory) {
				fileIcon.setImageResource(item.isExpanded ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed);
			} else {
				updateFileIcon(item.mimeType);
			}
		}

		private void updateFileIcon(String mime) {
			String m = (mime != null) ? mime : "";
			if (m.startsWith("image/")) fileIcon.setImageResource(R.drawable.ic_image_file);
			else if (m.startsWith("audio/")) fileIcon.setImageResource(R.drawable.ic_audio_file);
			else if (m.startsWith("video/")) fileIcon.setImageResource(R.drawable.ic_video_file);
			else if (m.startsWith("text/") || m.equals("application/json"))
				fileIcon.setImageResource(R.drawable.ic_text_file);
			else fileIcon.setImageResource(R.drawable.ic_unsupported_file);
		}

		private void handleItemClick() {
			int pos = getAdapterPosition();
			if (pos == RecyclerView.NO_POSITION) return;
			FileItem item = fileList.get(pos);
			if (item.isDirectory) {
				if (item.isExpanded) collapseFolder(pos);
				else {
					expandFolder(pos);
				}
			} else if (onFileClickListener != null) {
				if (context instanceof MainActivity) ((MainActivity) context).closeLeftNavigation();
				onFileClickListener.onFileClicked(item.uri, item.displayName);
			}
		}

		private void expandFolder(final int position) {
			final FileItem folder = fileList.get(position);
			folder.isExpanded = true;
			notifyItemChanged(position);
			new Thread(() -> {
				List<FileItem> folders = new ArrayList<>();
				List<FileItem> files = new ArrayList<>();

				if ("file".equalsIgnoreCase(folder.uri.getScheme())) {
					File dir = new File(folder.uri.getPath());
					File[] children = dir.listFiles();
					if (children != null) {
						for (File child : children) {
							boolean isDir = child.isDirectory();
							String mime = isDir ? DocumentsContract.Document.MIME_TYPE_DIR : FileItem.resolveMimeType(context, Uri.fromFile(child));
							FileItem item = new FileItem(Uri.fromFile(child), child.getName(), isDir, folder.depth + 1, mime);
							if (isDir) folders.add(item);
							else files.add(item);
						}
					}
				} else {
					String parentDocumentId;
					try {
						if (DocumentsContract.isDocumentUri(context, folder.uri)) {
							parentDocumentId = DocumentsContract.getDocumentId(folder.uri);
						} else {
							parentDocumentId = DocumentsContract.getTreeDocumentId(folder.uri);
						}
						Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, parentDocumentId);
						try (Cursor cursor = context.getContentResolver().query(childrenUri,
								new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
										DocumentsContract.Document.COLUMN_DISPLAY_NAME,
										DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
							if (cursor != null && cursor.moveToFirst()) {
								do {
									String id = cursor.getString(0);
									String name = cursor.getString(1);
									String mime = cursor.getString(2);
									boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
									FileItem child = new FileItem(DocumentsContract.buildDocumentUriUsingTree(folder.uri, id),
											name, isDir, folder.depth + 1, mime);
									if (isDir) folders.add(child);
									else files.add(child);
								} while (cursor.moveToNext());
							}
						}
					} catch (Exception e) {
						Log.e(TAG, "Expand error (SAF)", e);
					}
				}

				Collections.sort(folders, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
				Collections.sort(files, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
				List<FileItem> newItems = new ArrayList<>(folders);
				newItems.addAll(files);
				if (context instanceof Activity) {
					((Activity) context).runOnUiThread(() -> {
						if (!newItems.isEmpty()) {
							fileList.addAll(position + 1, newItems);
							notifyItemRangeInserted(position + 1, newItems.size());
						}
					});
				}
			}).start();
		}

		private void collapseFolder(int position) {
			FileItem folder = fileList.get(position);
			folder.isExpanded = false;
			notifyItemChanged(position);
			int start = position + 1;
			int count = 0;
			while (start < fileList.size() && fileList.get(start).depth > folder.depth) {
				fileList.remove(start);
				count++;
			}
			if (count > 0) notifyItemRangeRemoved(start, count);
		}
	}
}
