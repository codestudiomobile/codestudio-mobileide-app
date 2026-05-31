package com.csmide.app.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.csmide.R;
import com.csmide.app.models.LanguagePack;

import java.util.List;

/**
 * Adapter for displaying language packs in a ListView.
 * This adapter manages the visual representation of language packs, including
 * their installation status and associated color coding.
 */
public class LanguagePackAdapter extends ArrayAdapter<LanguagePack> {

	/**
	 * Constructs a new LanguagePackAdapter.
	 *
	 * @param context The application context.
	 * @param packs   The list of language packs to display.
	 */
	public LanguagePackAdapter(@NonNull Context context, @NonNull List<LanguagePack> packs) {
		super(context, android.R.layout.simple_list_item_2, android.R.id.text1, packs);
	}

	/**
	 * Provides a view for an AdapterView (ListView) at the specified position.
	 *
	 * @param position    The position of the item within the adapter's data set.
	 * @param convertView The old view to reuse, if possible.
	 * @param parent      The parent that this view will eventually be attached to.
	 * @return A View corresponding to the data at the specified position.
	 */
	@NonNull
	@Override
	public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
		// Use super.getView to inflate simple_list_item_2 which has text1 and text2
		View view = super.getView(position, convertView, parent);
		LanguagePack pack = getItem(position);
		if (pack == null) return view;

		TextView text1 = view.findViewById(android.R.id.text1);
		TextView text2 = view.findViewById(android.R.id.text2);

		// Set the primary label to the language pack name
		text1.setText(pack.name);

		int color;
		String statusText;
		// Determine the status text and its color based on the package status
		switch (pack.status) {
			case LanguagePack.STATUS_INSTALLED:
				color = ContextCompat.getColor(getContext(), R.color.progress_ok);
				statusText = getContext().getString(R.string.status_installed);
				break;
			case LanguagePack.STATUS_INSTALLING:
				color = ContextCompat.getColor(getContext(), R.color.progress_warn);
				statusText = getContext().getString(R.string.status_in_progress);
				break;
			case LanguagePack.STATUS_AVAILABLE:
			default:
				color = ContextCompat.getColor(getContext(), R.color.progress_error);
				statusText = getContext().getString(R.string.status_available);
				break;
		}

		// Apply status color and text to the second line of the list item
		text2.setTextColor(color);
		text2.setText(statusText);

		return view;
	}
}
