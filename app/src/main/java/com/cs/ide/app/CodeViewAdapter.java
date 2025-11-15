package com.cs.ide.app;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import org.jetbrains.annotations.Unmodifiable;
import java.util.ArrayList;
import java.util.List;
public class CodeViewAdapter extends BaseAdapter implements Filterable {
    private final LayoutInflater layoutInflater;
    private final int codeViewLayoutId;
    private final int codeViewTextViewId;
    private final List<Code> originalCodes;
    private List<Code> currentSuggestions;
    private final Filter codeFilter = new Filter() {
        @NonNull
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<Code> suggestions = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                results.values = originalCodes;
                results.count = originalCodes.size();
                return results;
            }
            String filterPattern = constraint.toString().toLowerCase().trim();
            for (Code item : originalCodes) {
                if (item.getCodePrefix().toLowerCase().contains(filterPattern)) {
                    suggestions.add(item);
                }
            }
            results.values = suggestions;
            results.count = suggestions.size();
            return results;
        }
        @Override
        protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
            currentSuggestions = (List<Code>) results.values;
            notifyDataSetChanged();
        }
        @Override
        public @Unmodifiable CharSequence convertResultToString(Object resultValue) {
            return ((Code) resultValue).getCodeBody();
        }
    };
    public CodeViewAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull List<Code> codes) {
        this.originalCodes = codes;
        this.currentSuggestions = new ArrayList<>();
        this.layoutInflater = LayoutInflater.from(context);
        this.codeViewLayoutId = resource;
        this.codeViewTextViewId = textViewResourceId;
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = layoutInflater.inflate(codeViewLayoutId, parent, false);
        }
        TextView textViewName = convertView.findViewById(codeViewTextViewId);
        Code currentCode = currentSuggestions.get(position);
        if (currentCode != null) {
            textViewName.setText(currentCode.getCodeTitle());
        }
        return convertView;
    }
    @Override
    public int getCount() {
        return currentSuggestions.size();
    }
    @Override
    public Object getItem(int position) {
        return currentSuggestions.get(position);
    }
    @Override
    public long getItemId(int position) {
        return position;
    }
    public void updateCodes(List<Code> newCodeList) {
        currentSuggestions.clear();
        originalCodes.clear();
        originalCodes.addAll(newCodeList);
        notifyDataSetChanged();
    }
    public void clearCodes() {
        originalCodes.clear();
        currentSuggestions.clear();
        notifyDataSetChanged();
    }
    @Override
    public Filter getFilter() {
        return codeFilter;
    }
}
