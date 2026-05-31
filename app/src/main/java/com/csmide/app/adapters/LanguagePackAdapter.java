package com.csmide.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

	private final OnPackActionListener listener;

	/**
	 * Constructs a new LanguagePackAdapter.
	 *
	 * @param context  The application context.
	 * @param packs    The list of language packs to display.
	 * @param listener Listener for actions.
	 */
	public LanguagePackAdapter(@NonNull Context context, @NonNull List<LanguagePack> packs, OnPackActionListener listener) {
		super(context, R.layout.item_package_codestudio, packs);
		this.listener = listener;
	}

	@NonNull
	@Override
	public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
		View rowView;
		if (convertView == null) {
			rowView = LayoutInflater.from(getContext()).inflate(R.layout.item_package_codestudio, parent, false);
		} else {
			rowView = convertView;
		}

		LanguagePack pack = getItem(position);
		if (pack == null) return rowView;

		TextView nameText = rowView.findViewById(R.id.packageName);
		Button actionButton = rowView.findViewById(R.id.packageAction);

		nameText.setText(pack.name);

		switch (pack.status) {
			case LanguagePack.STATUS_INSTALLED:
				actionButton.setText(R.string.label_uninstallation);
				actionButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.progress_error));
				actionButton.setEnabled(true);
				actionButton.setOnClickListener(v -> listener.onUninstallClicked(pack));
				break;
			case LanguagePack.STATUS_INSTALLING:
				actionButton.setText(R.string.status_in_progress);
				actionButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.progress_warn));
				actionButton.setEnabled(false);
				actionButton.setOnClickListener(null);
				break;
			case LanguagePack.STATUS_PROBING:
				actionButton.setText(R.string.status_checking);
				actionButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.progress_warn));
				actionButton.setEnabled(false);
				actionButton.setOnClickListener(null);
				break;
			case LanguagePack.STATUS_READY_TO_INSTALL: {
				String btnInstallText = getContext().getString(R.string.label_installation);
				if (pack.installSize != null) {
					btnInstallText += " (" + pack.installSize + ")";
				}
				actionButton.setText(btnInstallText);
				actionButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.progress_ok));
				actionButton.setEnabled(true);
				actionButton.setOnClickListener(v -> listener.onInstallClicked(pack));
				break;
			}
			case LanguagePack.STATUS_AVAILABLE:
			default:
				actionButton.setText(R.string.action_check_size);
				actionButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.cc_accent));
				actionButton.setEnabled(true);
				actionButton.setOnClickListener(v -> listener.onInstallClicked(pack));
				break;
		}

		return rowView;
	}

	public interface OnPackActionListener {
		void onInstallClicked(LanguagePack pack);

		void onUninstallClicked(LanguagePack pack);
	}
}
