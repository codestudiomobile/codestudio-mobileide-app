package com.csmide.app.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

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
		new AlertDialog.Builder(context)
				.setTitle(context.getString(R.string.delete) + " " + itemName)
				.setMessage(context.getString(R.string.delete_confirm_msg, itemName))
				.setPositiveButton(R.string.delete, (dialog, which) -> onConfirm.run())
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	/**
	 * Shows a simple rename dialog with an EditText.
	 *
	 * @param context     The context to show the dialog in.
	 * @param currentName The current name of the item.
	 * @param onRename    Callback to handle the new name.
	 */
	public static void showRenameDialog(Context context, String currentName, OnInputListener onRename) {
		final EditText input = new EditText(context);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		input.setText(currentName);
		new AlertDialog.Builder(context)
				.setTitle(R.string.rename)
				.setView(input)
				.setPositiveButton(R.string.rename, (dialog, which) -> {
					String newName = input.getText().toString().trim();
					if (!newName.isEmpty() && !newName.equals(currentName)) {
						onRename.onInput(newName);
					}
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
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
