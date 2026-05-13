package com.cs.ide.app.adapters;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.cs.ide.R;
import com.cs.ide.app.activities.MainActivity;
import com.cs.ide.app.fragments.CompileResultFragment;
import com.cs.ide.app.fragments.TextFragment;
import com.cs.ide.app.fragments.WelcomeFragment;
import com.cs.ide.app.utils.AppPreferences;

import java.util.List;
import java.util.ArrayList;

/**
 * ViewPagerAdapter manages the lifecycle of fragments displayed in the main activity's tabbed editor.
 * It maps file URIs and names to their corresponding fragment representations (TextFragment, WelcomeFragment, etc.).
 */
public class ViewPagerAdapter extends FragmentStateAdapter {
	/** Special URI representing the internal welcome screen. */
	public static final Uri WELCOME_URI = Uri.parse("app://com.cs.ide/welcome");
	/** Special URI representing a new, unsaved "Untitled" file. */
	public static final Uri UNTITLED_FILE_URI = Uri.parse("app://com.cs.ide/untitled");
	
	/** List of display names for the currently open tabs. */
	public final List<String> fileNames;
	/** List of URIs for the currently open tabs. */
	public final List<Uri> fileUris;
	
	private final FragmentActivity activity;
	private final SharedPreferences preferences;

	/**
	 * Constructs the adapter.
	 *
	 * @param fragmentActivity The parent activity.
	 * @param fileUris         The initial list of file URIs to open.
	 * @param fileNames        The initial list of file names to display.
	 */
	public ViewPagerAdapter(FragmentActivity fragmentActivity, @NonNull List<Uri> fileUris, List<String> fileNames) {
		super(fragmentActivity);
		this.activity = fragmentActivity;
		this.fileUris = fileUris;
		this.fileNames = fileNames;
		preferences = this.activity.getSharedPreferences(AppPreferences.PREFERENCE_NAME, 0);

		if (fileUris.isEmpty()) {
			setupInitialTabs();
		}
	}

	/**
	 * Configures default tabs based on user preferences (e.g., Welcome screen or Untitled file).
	 */
	private void setupInitialTabs() {
		boolean editorStartup = preferences.getBoolean(AppPreferences.KEY_EDITOR_STARTUP, false);
		boolean welcomeStartup = preferences.getBoolean(AppPreferences.KEY_WELCOME_STARTUP, true);
		if (welcomeStartup) {
			fileUris.add(WELCOME_URI);
			fileNames.add(activity.getString(R.string.welcome));
		}
		if (editorStartup) {
			fileUris.add(UNTITLED_FILE_URI);
			fileNames.add(activity.getString(R.string.untitled));
		}
	}

	/**
	 * Updates the URI and name of an existing tab. Useful when a file is "Saved As" a new name.
	 *
	 * @param oldUri  The current URI of the tab.
	 * @param newUri  The new URI to associate with the tab.
	 * @param newName The new display name.
	 */
	public void updateTabInfo(Uri oldUri, Uri newUri, String newName) {
		int index = fileUris.indexOf(oldUri);
		if (index != -1) {
			fileUris.set(index, newUri);
			fileNames.set(index, newName);
			notifyDataSetChanged();
		}
	}

	/**
	 * Adds a new file tab if it isn't already open.
	 *
	 * @param uri  The URI of the file.
	 * @param name The display name for the tab.
	 */
	public void addFile(Uri uri, String name) {
		for (Uri existingUri : fileUris) {
			if (uri.getPath() != null && uri.getPath().equals(existingUri.getPath())) {
				return;
			}
		}

		if (!fileUris.contains(uri)) {
			// If only the welcome screen is open, replace it with the new file
			if (fileUris.size() == 1 && fileUris.get(0).equals(WELCOME_URI)) {
				fileUris.clear();
				fileNames.clear();
			}
			fileUris.add(uri);
			fileNames.add(name);
			notifyDataSetChanged();
		}
	}

	/**
	 * Removes a tab identified by its URI.
	 *
	 * @param uri The URI of the tab to remove.
	 */
	public void removeTabByUri(Uri uri) {
		int index = fileUris.indexOf(uri);
		if (index != -1) {
			fileUris.remove(index);
			fileNames.remove(index);
			if (fileUris.isEmpty()) {
				addFile(WELCOME_URI, activity.getString(R.string.welcome));
			}
			notifyDataSetChanged();
		}
	}

	/**
	 * Retrieves the fragment instance associated with a specific tab position.
	 *
	 * @param position The index of the tab.
	 * @return The Fragment at that position, or null if not found.
	 */
	public Fragment getFragment(int position) {
		if (position < 0 || position >= getItemCount()) {
			return null;
		}
		long itemId = getItemId(position);
		String fragmentTag = "f" + itemId;
		return activity.getSupportFragmentManager().findFragmentByTag(fragmentTag);
	}

	/**
	 * Factory method that creates the appropriate fragment instance for a given tab position.
	 *
	 * @param position The index of the tab.
	 * @return A new Fragment instance (WelcomeFragment, CompileResultFragment, or TextFragment).
	 */
	@NonNull
	@Override
	public Fragment createFragment(int position) {
		Uri fileUri = fileUris.get(position);
		if (activity instanceof MainActivity) {
			((MainActivity) activity).currentFileUri = fileUri;
			((MainActivity) activity).currentMimeType = ((MainActivity) activity).getMimeType(fileUri);
		}
		if (fileUri.equals(WELCOME_URI)) {
			return new WelcomeFragment();
		}
		if (fileUri.toString().startsWith("app://com.cs.ide/compile")) {
			String command = fileUri.getQueryParameter("command");
			String cwd = fileUri.getQueryParameter("cwd");
			return CompileResultFragment.newInstance(command, cwd, fileUri);
		}
		if (fileUri.equals(UNTITLED_FILE_URI)) {
			return TextFragment.newInstance(UNTITLED_FILE_URI);
		}
		try {
			return TextFragment.newInstance(fileUri);
		} catch (Exception e) {
			Log.e("ViewPagerAdapter", "Error opening file", e);
			Toast.makeText(activity, "Error opening file for editing.", Toast.LENGTH_SHORT).show();
			return new WelcomeFragment();
		}
	}

	@Override
	public int getItemCount() {
		return fileUris.size();
	}

	public List<Uri> getFileUris() {
		return fileUris;
	}

	public List<String> getFileNames() {
		return fileNames;
	}

	/**
	 * Adds a tab and returns its new position.
	 *
	 * @param uri      The URI to add.
	 * @param fileName The name to display.
	 * @return The index of the added or existing tab.
	 */
	public int addTab(Uri uri, String fileName) {
		String runPrefix = activity.getString(R.string.run_prefix, "");
		for (int i = 0; i < fileUris.size(); i++) {
			if (fileUris.get(i).equals(uri) && !fileName.startsWith(runPrefix)) {
				return i;
			}
		}
		fileUris.add(uri);
		fileNames.add(fileName);
		notifyItemInserted(fileUris.size() - 1);
		return fileUris.size() - 1;
	}

	/**
	 * Finds the index of a tab by its display name.
	 *
	 * @param name The name to search for.
	 * @return The index, or -1 if not found.
	 */
	public int findTabPositionByName(String name) {
		for (int i = 0; i < fileNames.size(); i++) {
			if (fileNames.get(i).equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Removes the tab at the specified position.
	 *
	 * @param position Index of the tab.
	 */
	public void removeTab(int position) {
		if (position >= 0 && position < fileUris.size()) {
			fileUris.remove(position);
			fileNames.remove(position);
			if (fileUris.isEmpty()) {
				setupInitialTabs();
			}
			notifyDataSetChanged();
		}
	}

	/**
	 * Generates a stable ID for a tab based on its URI hash code.
	 *
	 * @param position Index of the tab.
	 * @return A unique stable ID.
	 */
	@Override
	public long getItemId(int position) {
		return fileUris.get(position).toString().hashCode();
	}

	@Override
	public boolean containsItem(long itemId) {
		for (Uri uri : fileUris) {
			if ((long) uri.toString().hashCode() == itemId) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Closes all open tabs and restores default initial tabs.
	 */
	public void removeAllTabs() {
		fileUris.clear();
		fileNames.clear();
		setupInitialTabs();
		notifyDataSetChanged();
	}

	/**
	 * Closes all tabs except for the one at the given position.
	 *
	 * @param currentPosition The index of the tab to keep.
	 */
	public void removeOtherTabs(int currentPosition) {
		if (currentPosition < 0 || currentPosition >= fileUris.size()) {
			return;
		}
		Uri currentUri = fileUris.get(currentPosition);
		String currentName = fileNames.get(currentPosition);
		fileUris.clear();
		fileNames.clear();
		fileUris.add(currentUri);
		fileNames.add(currentName);
		notifyDataSetChanged();
	}

	/**
	 * Collects content from all open text fragments that have unsaved changes.
	 *
	 * @return A list of FileContentItem objects containing URI and pending content.
	 */
	public List<FilesAdapter.FileContentItem> getOpenFilesContent() {
		byte[] content;
		List<FilesAdapter.FileContentItem> filesToSave = new ArrayList<>();
		String runPrefix = activity.getString(R.string.run_prefix, "");
		for (int i = 0; i < getItemCount(); i++) {
			Uri uri = fileUris.get(i);
			if (!uri.equals(WELCOME_URI) && !uri.equals(UNTITLED_FILE_URI) && !fileNames.get(i).startsWith(runPrefix)) {
				Fragment fragment = getFragment(i);
				if (fragment instanceof TextFragment) {
					TextFragment textFragment = (TextFragment) fragment;
					if (!textFragment.isSaved() && (content = textFragment.getContents()) != null) {
						filesToSave.add(new FilesAdapter.FileContentItem(uri, content));
					}
				}
			}
		}
		return filesToSave;
	}
}
