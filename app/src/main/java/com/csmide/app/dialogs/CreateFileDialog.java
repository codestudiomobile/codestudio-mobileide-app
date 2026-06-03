package com.csmide.app.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.csmide.R;

import java.util.ArrayList;

/**
 * CreateFileDialog is a DialogFragment that handles the creation of new files and the "Save As" operation.
 * It allows the user to specify a filename and select a destination folder from a list of accessible locations.
 */
public class CreateFileDialog extends DialogFragment {
	private static final String ARG_FOLDER_URIS = "folder_uris";
	private static final String ARG_FOLDER_NAMES = "folder_names";
	private static final String ARG_FILE_CONTENT = "file_content";
	private static final String ARG_CURRENT_FILE_NAME = "current_file_name";
	private static final String ARG_CURRENT_FILE_URI = "current_file_uri";

	private OnFileCreatedListener listener;
	private ArrayList<Uri> folderUris;
	private ArrayList<String> folderNames;
	private byte[] fileContent;
	private String currentFileName;
	private Uri currentFileUri;

	public static CreateFileDialog newInstance(ArrayList<Uri> folderUris, ArrayList<String> folderNames) {
		return newInstance(folderUris, folderNames, null, null, null);
	}

	public static CreateFileDialog newInstance(ArrayList<Uri> folderUris, ArrayList<String> folderNames, @Nullable byte[] content, @Nullable String currentName, @Nullable Uri currentUri) {
		CreateFileDialog fragment = new CreateFileDialog();
		Bundle args = new Bundle();
		args.putParcelableArrayList(ARG_FOLDER_URIS, folderUris != null ? folderUris : new ArrayList<>());
		args.putStringArrayList(ARG_FOLDER_NAMES, folderNames != null ? folderNames : new ArrayList<>());
		if (content != null) {
			args.putByteArray(ARG_FILE_CONTENT, content);
		}
		if (currentName != null) {
			args.putString(ARG_CURRENT_FILE_NAME, currentName);
		}
		if (currentUri != null) {
			args.putParcelable(ARG_CURRENT_FILE_URI, currentUri);
		}
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		try {
			listener = (OnFileCreatedListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context + " must implement OnFileCreatedListener");
		}
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			folderUris = getArguments().getParcelableArrayList(ARG_FOLDER_URIS);
			folderNames = getArguments().getStringArrayList(ARG_FOLDER_NAMES);
			fileContent = getArguments().getByteArray(ARG_FILE_CONTENT);
			currentFileName = getArguments().getString(ARG_CURRENT_FILE_NAME);
			currentFileUri = getArguments().getParcelable(ARG_CURRENT_FILE_URI);
		}
		if (folderUris == null) folderUris = new ArrayList<>();
		if (folderNames == null) folderNames = new ArrayList<>();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_create_file_code_studio, container, false);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		return dialog;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		setupDialog(view);
	}

	private void setupDialog(View view) {
		TextView titleTextView = view.findViewById(R.id.dialogTitle);
		EditText fileNameEditText = view.findViewById(R.id.fileName);
		Spinner locationSpinner = view.findViewById(R.id.locationSpinner);
		Button createButton = view.findViewById(R.id.create);
		Button cancelButton = view.findViewById(R.id.cancel);

		boolean isSaveAsMode = fileContent != null;
		if (titleTextView != null) {
			titleTextView.setText(isSaveAsMode ? "Save Untitled File" : "Create New File");
		}
		createButton.setText(isSaveAsMode ? "Save" : "Create");

		if (isSaveAsMode && currentFileName != null) {
			fileNameEditText.setText(currentFileName);
			fileNameEditText.setSelection(currentFileName.lastIndexOf('.') != -1 ? currentFileName.lastIndexOf('.') : currentFileName.length());
		}

		if (!folderNames.isEmpty()) {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item_codestudio, folderNames);
			adapter.setDropDownViewResource(R.layout.spinner_item_codestudio);
			locationSpinner.setAdapter(adapter);
		} else {
			Toast.makeText(requireContext(), "No accessible folders available.", Toast.LENGTH_LONG).show();
			createButton.setEnabled(false);
		}

		cancelButton.setOnClickListener(v -> dismiss());
		createButton.setOnClickListener(v -> handleCreate(fileNameEditText, locationSpinner));
	}

	private void handleCreate(EditText fileNameEditText, Spinner locationSpinner) {
		String fileName = fileNameEditText.getText().toString().trim();
		if (fileName.isEmpty()) {
			Toast.makeText(requireContext(), R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
			return;
		}

		int selectedPosition = locationSpinner.getSelectedItemPosition();
		if (selectedPosition < 0 || selectedPosition >= folderUris.size()) return;

		Uri folderUri = folderUris.get(selectedPosition);

		// For Save As, check if name and location are actually modified
		if (fileContent != null && currentFileUri != null && currentFileName != null) {
			if (fileName.equals(currentFileName)) {
				// Name matches, check location
				Uri parentUriOfCurrent = getSafParentUri(currentFileUri);
				if (folderUri != null && folderUri.equals(parentUriOfCurrent)) {
					Toast.makeText(requireContext(), R.string.msg_no_changes_detected, Toast.LENGTH_SHORT).show();
					return;
				}
				// Also check app storage case
				if (folderUri == null && currentFileUri.toString().startsWith("file://") && currentFileUri.getPath().contains("code_studio_files")) {
					Toast.makeText(requireContext(), R.string.msg_no_changes_detected, Toast.LENGTH_SHORT).show();
					return;
				}
			}
		}

		String extension = getFileExtension(fileName);
		if (extension.isEmpty()) {
			fileName += ".txt";
			extension = "txt";
		}

		String mimeType = resolveMimeType(extension);
		if (mimeType == null) return;

		if (folderUri == null || folderUri.toString().equals("app://com.csmide/internal_storage")) {
			createInAppStorage(fileName);
		} else if ("file".equals(folderUri.getScheme())) {
			createInFilePath(folderUri, fileName, mimeType);
		} else {
			createInSaf(folderUri, fileName, mimeType);
		}
	}

	private Uri getSafParentUri(Uri childUri) {
		if ("file".equalsIgnoreCase(childUri.getScheme())) {
			java.io.File parent = new java.io.File(childUri.getPath()).getParentFile();
			return parent != null ? Uri.fromFile(parent) : null;
		}
		try {
			DocumentsContract.Path path = DocumentsContract.findDocumentPath(requireContext().getContentResolver(), childUri);
			if (path == null || path.getPath().size() < 2) return null;
			return DocumentsContract.buildDocumentUriUsingTree(childUri, path.getPath().get(path.getPath().size() - 2));
		} catch (Exception e) {
			return null;
		}
	}

	private void createInFilePath(Uri folderUri, String fileName, String mimeType) {
		java.io.File dir = new java.io.File(folderUri.getPath());
		if (!dir.exists() || !dir.isDirectory()) {
			Toast.makeText(requireContext(), "Target directory does not exist", Toast.LENGTH_SHORT).show();
			return;
		}
		java.io.File newFile = new java.io.File(dir, fileName);
		if (newFile.exists()) {
			Toast.makeText(requireContext(), "File already exists", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			if (newFile.createNewFile()) {
				listener.onFileCreated(fileName, Uri.fromFile(newFile), fileContent);
				dismiss();
			} else {
				Toast.makeText(requireContext(), "Failed to create file", Toast.LENGTH_LONG).show();
			}
		} catch (java.io.IOException e) {
			Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private String getFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
			return fileName.substring(dotIndex + 1).toLowerCase();
		}
		return "";
	}

	private String resolveMimeType(String extension) {
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		if (mimeType == null) {
			String[] allowedExtensions = {
					"txt", "java", "xml", "html", "css", "js", "json", "md", "py", "c", "cpp", "kt",
					"cs", "rs", "go", "rb", "php", "lua", "pl", "sh", "swift", "dart", "yaml", "yml", "make", "ps1", "bat", "cmd"
			};
			for (String ext : allowedExtensions) {
				if (extension.equals(ext)) {
					return "text/plain";
				}
			}
			Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_LONG).show();
			return null;
		} else if (!mimeType.startsWith("text/") && !mimeType.equals("application/json") && !mimeType.equals("application/xml")) {
			// Some common extensions might resolve to application/* but are still text-based for us
			if (extension.equals("js") || extension.equals("dart") || extension.equals("json") || extension.equals("yaml") || extension.equals("yml")) {
				return mimeType;
			}
			Toast.makeText(requireContext(), "Unsupported MIME type: " + mimeType, Toast.LENGTH_LONG).show();
			return null;
		}
		return mimeType;
	}

	private void createInAppStorage(String fileName) {
		java.io.File appStorageDir = new java.io.File(requireContext().getFilesDir(), "code_studio_files");
		if (!appStorageDir.exists()) appStorageDir.mkdirs();
		java.io.File newFile = new java.io.File(appStorageDir, fileName);
		if (newFile.exists()) {
			Toast.makeText(requireContext(), "File already exists in app storage", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			if (newFile.createNewFile()) {
				listener.onFileCreated(fileName, Uri.fromFile(newFile), fileContent);
				dismiss();
			} else {
				Toast.makeText(requireContext(), "Failed to create file in app storage", Toast.LENGTH_LONG).show();
			}
		} catch (java.io.IOException e) {
			Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void createInSaf(Uri folderUri, String fileName, String mimeType) {
		try {
			Uri docUri = folderUri;
			if (DocumentsContract.isTreeUri(folderUri) && !DocumentsContract.isDocumentUri(requireContext(), folderUri)) {
				docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri));
			}

			Uri newFileUri = DocumentsContract.createDocument(requireContext().getContentResolver(), docUri, mimeType, fileName);
			if (newFileUri != null) {
				// Get the actual name assigned by the provider
				String finalName = fileName;
				try (android.database.Cursor c = requireContext().getContentResolver().query(newFileUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
					if (c != null && c.moveToFirst()) {
						finalName = c.getString(0);
					}
				}
				listener.onFileCreated(finalName, newFileUri, fileContent);
				dismiss();
			} else {
				Toast.makeText(requireContext(), "Failed to create file: The provider returned null.", Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			Log.e("CreateFileDialog", "Error creating file", e);
			Toast.makeText(requireContext(), "Failed to create file: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Interface to listen for file creation events.
	 */
	public interface OnFileCreatedListener {
		/**
		 * Called when a file is successfully created.
		 *
		 * @param fileName    The name of the created file.
		 * @param fileUri     The URI of the created file.
		 * @param fileContent Optional content to be written to the file (used for "Save As").
		 */
		void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent);

		/**
		 * Requests the host to show the Save As dialog for the given content.
		 *
		 * @param content The byte array content to be saved.
		 */
		void requestSaveAs(byte[] content);
	}
}
