package com.cs.ide.app;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.cs.ide.R;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.FileViewHolder> {
    private static final String TAG = "FilesAdapter";
    public final List<FileItem> fileList;
    private final Context context;
    private final OnFileClickListener onFileClickListener;
    private final Uri rootFolderUri;
    public FileViewHolder holder;
    public FilesAdapter(Context context, List<FileItem> fileList, OnFileClickListener onFileClickListener, Uri rootFolderUri) {
        this.context = context;
        this.fileList = fileList;
        this.onFileClickListener = onFileClickListener;
        this.rootFolderUri = rootFolderUri;
    }
    public static void saveFileContentAsync(Context context, Uri uri, byte[] content) {
        new Thread(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream for URI: " + uri);
                    return;
                }
                outputStream.write(content);
                outputStream.flush();
                Log.d(TAG, "File saved successfully: " + uri);
            } catch (IOException e) {
                Log.e(TAG, "Error saving file: " + uri, e);
            }
        }).start();
    }
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
        this.holder = holder;
        int paddingStart = item.depth * 24;
        ViewCompat.setPaddingRelative(holder.itemView, paddingStart, holder.itemView.getPaddingTop(), holder.itemView.getPaddingEnd(), holder.itemView.getPaddingBottom());
    }
    @Override
    public int getItemCount() {
        return fileList.size();
    }
    public void refresh() {
        if (rootFolderUri == null) {
            Log.e(TAG, "Cannot refresh: rootFolderUri is null.");
            return;
        }
        fileList.clear();
        notifyDataSetChanged();
        FileItem rootItem = new FileItem(context, rootFolderUri, "Project Root", true, 0);
        fileList.add(rootItem);
        notifyItemInserted(0);
        loadChildren(rootItem, 0);
    }
    private void loadChildren(FileItem folder, int position) {
        if (!folder.isDirectory) return;
        folder.isExpanded = true;
        ((Activity) context).runOnUiThread(() -> notifyItemChanged(position));
        new Thread(() -> {
            final List<FileItem> newItems = new ArrayList<>();
            ((Activity) context).runOnUiThread(() -> {
                if (!newItems.isEmpty()) {
                    fileList.addAll(position + 1, newItems);
                    notifyItemRangeInserted(position + 1, newItems.size());
                }
            });
        }).start();
    }
    public void saveAllFiles(List<FileContentItem> filesToSave) {
        new Thread(() -> {
            if (filesToSave == null || filesToSave.isEmpty()) {
                return;
            }
            int savedCount = 0;
            for (FileContentItem file : filesToSave) {
                try {
                    try (OutputStream os = context.getContentResolver().openOutputStream(file.getUri())) {
                        if (os != null) {
                            os.write(file.getContent());
                            savedCount++;
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error saving file " + file.getUri().toString() + ": " + e.getMessage());
                }
            }
        }).start();
    }
    public void collapseAllFolders() {
        ((MainActivity) context).runOnUiThread(() -> {
            for (int i = fileList.size() - 1; i >= 0; i--) {
                FileItem item = fileList.get(i);
                if (item.depth > 0) {
                    fileList.remove(i);
                }
                if (item.isDirectory) {
                    item.isExpanded = false;
                }
            }
            notifyDataSetChanged();
        });
    }
    private int findInsertPosition(Uri parentUri) {
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).uri.equals(parentUri)) {
                return i + 1;
            }
        }
        return fileList.size(); 
    }
    public void expandFolderPreserveThreadSafe(final int position) {
        final FileItem folder = fileList.get(position);
        folder.isExpanded = true;
        notifyItemChanged(position);
        new Thread(() -> {
            List<FileItem> folders = new ArrayList<>();
            List<FileItem> files = new ArrayList<>();
            String documentId = DocumentsContract.getDocumentId(folder.uri);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, documentId);
            try (Cursor cursor = context.getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String childDocId = cursor.getString(0);
                        String childName = cursor.getString(1);
                        String mimeType = cursor.getString(2);
                        boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                        Uri childUri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, childDocId);
                        if (!isDirectory && mimeType == null) {
                            String ext = MimeTypeMap.getFileExtensionFromUrl(childUri.toString());
                            if (ext != null) {
                                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
                            }
                        }
                        FileItem childItem = new FileItem(childUri, childName, isDirectory, folder.depth + 1, mimeType);
                        if (isDirectory) folders.add(childItem);
                        else files.add(childItem);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            folders.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
            files.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
            List<FileItem> newItems = new ArrayList<>();
            newItems.addAll(folders);
            newItems.addAll(files);
            ((Activity) context).runOnUiThread(() -> {
                if (!newItems.isEmpty()) {
                    fileList.addAll(position + 1, newItems);
                    notifyItemRangeInserted(position + 1, newItems.size());
                }
            });
        }).start();
    }
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
    public interface OnFileClickListener {
        void onOutputReceived(String output);
        void onFileClicked(Uri fileUri, String fileName);
        void onFileLongClick(View view, FileItem fileItem);
    }
    public static class FileContentItem {
        private final Uri uri;
        private final byte[] content;
        public FileContentItem(Uri uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }
        public Uri getUri() {
            return uri;
        }
        public byte[] getContent() {
            return content;
        }
    }
    class FileViewHolder extends RecyclerView.ViewHolder {
        private final ImageView fileIcon;
        private final TextView fileName;
        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            itemView.setOnClickListener(v -> handleItemClick());
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FileItem clickedItem = fileList.get(position);
                    onFileClickListener.onFileLongClick(v, clickedItem);
                    return true; 
                }
                return false;
            });
        }
        public void bind(@NonNull FileItem item) {
            fileName.setText(item.displayName);
            if (item.isDirectory) {
                fileIcon.setImageResource(item.isExpanded ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed);
                return;
            }
            String mime = item.mimeType != null ? item.mimeType : "";
            if (mime.startsWith("image/")) {
                fileIcon.setImageResource(R.drawable.ic_image_file);
            } else if (mime.startsWith("audio/")) {
                fileIcon.setImageResource(R.drawable.ic_audio_file);
            } else if (mime.startsWith("video/")) {
                fileIcon.setImageResource(R.drawable.ic_video_file);
            } else if (mime.startsWith("text/") || mime.equals("application/json")) {
                fileIcon.setImageResource(R.drawable.ic_text_file);
            } else {
                fileIcon.setImageResource(R.drawable.ic_unsupported_file);
            }
        }
        private void handleItemClick() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            FileItem clickedItem = fileList.get(position);
            if (clickedItem.isDirectory) {
                if (clickedItem.isExpanded) {
                    collapseFolder(position);
                } else {
                    expandFolder(position);
                }
            } else {
                if (onFileClickListener != null) {
                    ((MainActivity) context).closeLeftNavigation();
                    onFileClickListener.onFileClicked(clickedItem.uri, clickedItem.displayName);
                }
            }
        }
        public void expandFolder(final int position) {
            final FileItem folder = fileList.get(position);
            folder.isExpanded = true;
            notifyItemChanged(position);
            new Thread(() -> {
                String documentId = DocumentsContract.getDocumentId(folder.uri);
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, documentId);
                final List<FileItem> folders = new ArrayList<>();
                final List<FileItem> files = new ArrayList<>();
                try (Cursor cursor = context.getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            String childDocId = cursor.getString(0);
                            String childName = cursor.getString(1);
                            String mimeType = cursor.getString(2);
                            boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, childDocId);
                            FileItem childItem = new FileItem(childUri, childName, isDirectory, folder.depth + 1, mimeType);
                            if (isDirectory) {
                                folders.add(childItem);
                            } else {
                                files.add(childItem);
                            }
                        } while (cursor.moveToNext());
                    }
                }
                Collections.sort(folders, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                Collections.sort(files, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                final List<FileItem> newItems = new ArrayList<>();
                newItems.addAll(folders);
                newItems.addAll(files);
                ((Activity) context).runOnUiThread(() -> {
                    if (!newItems.isEmpty()) {
                        fileList.addAll(position + 1, newItems);
                        notifyItemRangeInserted(position + 1, newItems.size());
                    }
                });
            }).start();
        }
        private void collapseFolder(int position) {
            FileItem folder = fileList.get(position);
            folder.isExpanded = false;
            notifyItemChanged(position);
            int startPosition = position + 1;
            int count = 0;
            while (startPosition < fileList.size() && fileList.get(startPosition).depth > folder.depth) {
                fileList.remove(startPosition);
                count++;
            }
            if (count > 0) {
                notifyItemRangeRemoved(startPosition, count);
            }
        }
    }
}
