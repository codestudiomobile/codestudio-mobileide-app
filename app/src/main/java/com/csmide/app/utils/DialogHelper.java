package com.csmide.app.utils;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.csmide.R;

/**
 * Helper class for showing common dialogs used throughout the application.
 */
public class DialogHelper {

	/**
	 * Shows a confirmation dialog for deleting a file or folder.
	 *
	 * @param context   The context to show the dialog in.
	 * @param itemName  The name of the item to be deleted.
	 * @param onConfirm Runnable to execute if the user confirms deletion.
	 */
	public static void showDeleteConfirmationDialog(Context context, String itemName, Runnable onConfirm) {
		View view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_delete_code_studio, null);
		TextView messageView = view.findViewById(R.id.deleteMessage);
		android.widget.Button deleteBtn = view.findViewById(R.id.delete);
		android.widget.Button cancelBtn = view.findViewById(R.id.cancel);

		messageView.setText(context.getString(R.string.delete_confirm_msg, itemName));

		final AlertDialog dialog = new AlertDialog.Builder(context, R.style.CodeStudio_AlertDialog)
				.setView(view)
				.create();

		deleteBtn.setOnClickListener(v -> {
			onConfirm.run();
			dialog.dismiss();
		});

		cancelBtn.setOnClickListener(v -> dialog.dismiss());

		dialog.show();
	}

	/**
	 * Shows a custom rename dialog.
	 *
	 * @param context     The context to show the dialog in.
	 * @param currentName The current name of the item.
	 * @param onRename    Callback to handle the new name.
	 */
	public static void showRenameDialog(Context context, String currentName, OnInputListener onRename) {
		View view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_rename_code_studio, null);
		final EditText input = view.findViewById(R.id.newName);
		final android.widget.Button renameBtn = view.findViewById(R.id.rename);
		final android.widget.Button cancelBtn = view.findViewById(R.id.cancel);

		input.setText(currentName);
		// Select filename without extension
		int dotIndex = currentName.lastIndexOf('.');
		if (dotIndex > 0) {
			input.setSelection(0, dotIndex);
		} else {
			input.setSelection(0, currentName.length());
		}

		final AlertDialog dialog = new AlertDialog.Builder(context, R.style.CodeStudio_AlertDialog)
				.setView(view)
				.create();

		renameBtn.setOnClickListener(v -> {
			String newName = input.getText().toString().trim();
			if (newName.isEmpty()) {
				android.widget.Toast.makeText(context, R.string.name_cannot_be_empty, android.widget.Toast.LENGTH_SHORT).show();
				return;
			}
			if (!newName.equals(currentName)) {
				onRename.onInput(newName);
			}
			dialog.dismiss();
		});

		cancelBtn.setOnClickListener(v -> dialog.dismiss());

		dialog.show();
	}

	/**
	 * Interface for handling text input results from dialogs.
	 */
	public interface OnInputListener {
		/**
		 * Called when the user has provided input.
		 *
		 * @param input The text entered by the user.
		 */
		void onInput(String input);
	}
}
