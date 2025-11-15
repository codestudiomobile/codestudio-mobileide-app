package com.cs.ide.app;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
public class LanguagePackAdapter extends ArrayAdapter<LanguagePack> {
    private final Context mContext;
    private final int mResource;
    public LanguagePackAdapter(@NonNull Context context, @NonNull List<LanguagePack> objects) {
        super(context, android.R.layout.simple_list_item_2, objects);
        this.mContext = context;
        this.mResource = android.R.layout.simple_list_item_2;
    }
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(mResource, parent, false);
        }
        LanguagePack pack = getItem(position);
        if (pack != null) {
            TextView nameTextView = convertView.findViewById(android.R.id.text1);
            if (nameTextView != null) {
                nameTextView.setText(pack.name);
            }
            TextView statusTextView = convertView.findViewById(android.R.id.text2);
            if (statusTextView != null) {
                statusTextView.setText("Key: " + pack.key + " | Status: " + pack.status);
            }
        }
        return convertView;
    }
}
