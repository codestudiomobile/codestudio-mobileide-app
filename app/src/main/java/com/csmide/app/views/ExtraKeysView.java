package com.csmide.app.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.csmide.R;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtraKeysView provides a paginated set of shortcut buttons for common coding
 * symbols
 * and navigation keys. It can be used as a toolbar for the editor or terminal.
 */
public class ExtraKeysView extends LinearLayout {

	private OnKeyActionListener listener;
	private ViewPager2 viewPager;
	private View pageIndicator1;
	private View pageIndicator2;

	public ExtraKeysView(Context context) {
		this(context, null);
	}

	public ExtraKeysView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ExtraKeysView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		setOrientation(VERTICAL);

		// ViewPager to hold pages of keys
		viewPager = new ViewPager2(context);
		int heightInPx = (int) (88 * context.getResources().getDisplayMetrics().density);
		viewPager.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, heightInPx));
		addView(viewPager);

		// Page Indicator Container
		LinearLayout indicatorContainer = new LinearLayout(context);
		indicatorContainer.setOrientation(HORIZONTAL);
		indicatorContainer.setGravity(android.view.Gravity.CENTER);
		LayoutParams indicatorParams = new LayoutParams(LayoutParams.MATCH_PARENT, 8);
		indicatorParams.setMargins(0, 4, 0, 4);
		indicatorContainer.setLayoutParams(indicatorParams);

		pageIndicator1 = new View(context);
		LayoutParams p1 = new LayoutParams(40, 4);
		p1.setMargins(8, 0, 8, 0);
		pageIndicator1.setLayoutParams(p1);
		pageIndicator1.setBackgroundColor(context.getColor(R.color.white));

		pageIndicator2 = new View(context);
		LayoutParams p2 = new LayoutParams(40, 4);
		p2.setMargins(8, 0, 8, 0);
		pageIndicator2.setLayoutParams(p2);
		pageIndicator2.setBackgroundColor(context.getColor(R.color.grey_500));

		indicatorContainer.addView(pageIndicator1);
		indicatorContainer.addView(pageIndicator2);
		addView(indicatorContainer);

		setupViewPager();
	}

	/**
	 * Sets the listener for key actions.
	 *
	 * @param listener The listener instance.
	 */
	public void setOnKeyActionListener(OnKeyActionListener listener) {
		this.listener = listener;
	}

	private void setupViewPager() {
		List<List<SymbolItem>> pages = new ArrayList<>();

		// Page 1: Common symbols and navigation
		List<SymbolItem> page1 = new ArrayList<>();
		page1.add(new SymbolItem("TAB", "Tab", R.drawable.ic_symbol_tab));
		page1.add(new SymbolItem("{}", null, R.drawable.ic_symbol_curly_brackets));
		page1.add(new SymbolItem("\"\"", null, R.drawable.ic_symbol_double_quotes));
		page1.add(new SymbolItem(";", null, R.drawable.ic_symbol_semicolon));
		page1.add(new SymbolItem("UNDO", null, R.drawable.ic_symbol_undo));
		page1.add(new SymbolItem("UP", null, R.drawable.ic_symbol_arrow_up));
		page1.add(new SymbolItem("REDO", null, R.drawable.ic_symbol_redo));

		page1.add(new SymbolItem("=", null, R.drawable.ic_symbol_equal));
		page1.add(new SymbolItem("\\", null, R.drawable.ic_symbol_backslash));
		page1.add(new SymbolItem("&", null, R.drawable.ic_symbol_and));
		page1.add(new SymbolItem(",", null, R.drawable.ic_symbol_comma));
		page1.add(new SymbolItem("LEFT", null, R.drawable.ic_symbol_arrow_left));
		page1.add(new SymbolItem("DOWN", null, R.drawable.ic_symbol_arrow_down));
		page1.add(new SymbolItem("RIGHT", null, R.drawable.ic_symbol_arrow_right));
		pages.add(page1);

		// Page 2: Additional symbols
		List<SymbolItem> page2 = new ArrayList<>();
		page2.add(new SymbolItem("+", null, R.drawable.ic_symbol_plus));
		page2.add(new SymbolItem("-", null, R.drawable.ic_symbol_minus));
		page2.add(new SymbolItem("!", null, R.drawable.ic_symbol_exclamation_mark));
		page2.add(new SymbolItem("$", null, R.drawable.ic_symbol_dollar));
		page2.add(new SymbolItem("[]", null, R.drawable.ic_symbol_square_brackets));
		page2.add(new SymbolItem("''", null, R.drawable.ic_symbol_apostrophe));
		page2.add(new SymbolItem("()", null, R.drawable.ic_symbol_parentheses));

		page2.add(new SymbolItem("|", null, R.drawable.ic_symbol_pipe));
		page2.add(new SymbolItem("^", null, R.drawable.ic_symbol_caret));
		page2.add(new SymbolItem(".", null, R.drawable.ic_symbol_dot));
		page2.add(new SymbolItem("#", null, R.drawable.ic_symbol_hash));
		page2.add(new SymbolItem("%", null, R.drawable.ic_symbol_percent));
		page2.add(new SymbolItem("<", null, R.drawable.ic_symbol_angle_brackets_left));
		page2.add(new SymbolItem(">", null, R.drawable.ic_symbol_angle_brackets_right));
		pages.add(page2);

		viewPager.setAdapter(new SymbolsPagerAdapter(pages));
		viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				if (position == 0) {
					pageIndicator1.setBackgroundColor(getContext().getColor(R.color.white));
					pageIndicator2.setBackgroundColor(getContext().getColor(R.color.grey_500));
				} else {
					pageIndicator1.setBackgroundColor(getContext().getColor(R.color.grey_500));
					pageIndicator2.setBackgroundColor(getContext().getColor(R.color.white));
				}
			}
		});
	}

	/**
	 * Interface for receiving key action events.
	 */
	public interface OnKeyActionListener {
		/**
		 * Called when a key is pressed.
		 *
		 * @param key The key string or command associated with the button.
		 */
		void onKeyAction(String key);
	}

	private static class SymbolItem {
		String key;
		String displayText;
		int iconRes;

		SymbolItem(String key, String displayText, int iconRes) {
			this.key = key;
			this.displayText = displayText;
			this.iconRes = iconRes;
		}
	}

	private class SymbolsPagerAdapter extends RecyclerView.Adapter<SymbolsPagerAdapter.PageViewHolder> {
		private final List<List<SymbolItem>> pages;

		public SymbolsPagerAdapter(List<List<SymbolItem>> pages) {
			this.pages = pages;
		}

		@NonNull
		@Override
		public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			RecyclerView recyclerView = new RecyclerView(parent.getContext());
			recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
			recyclerView.setLayoutManager(new GridLayoutManager(parent.getContext(), 7));
			recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
			return new PageViewHolder(recyclerView);
		}

		@Override
		public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
			holder.recyclerView.setAdapter(new SymbolsAdapter(pages.get(position)));
		}

		@Override
		public int getItemCount() {
			return pages.size();
		}

		class PageViewHolder extends RecyclerView.ViewHolder {
			RecyclerView recyclerView;

			PageViewHolder(RecyclerView view) {
				super(view);
				recyclerView = view;
			}
		}
	}

	private class SymbolsAdapter extends RecyclerView.Adapter<SymbolsAdapter.SymbolViewHolder> {
		private final List<SymbolItem> items;

		public SymbolsAdapter(List<SymbolItem> items) {
			this.items = items;
		}

		@NonNull
		@Override
		public SymbolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_symbol_button, parent, false);
			return new SymbolViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull SymbolViewHolder holder, int position) {
			SymbolItem item = items.get(position);
			if (item.iconRes != -1) {
				holder.icon.setVisibility(VISIBLE);
				holder.text.setVisibility(GONE);
				holder.icon.setImageResource(item.iconRes);
			} else {
				holder.icon.setVisibility(GONE);
				holder.text.setVisibility(VISIBLE);
				holder.text.setText(item.displayText);
			}
			holder.itemView.setOnClickListener(v -> {
				if (listener != null) {
					v.post(() -> listener.onKeyAction(item.key));
				}
			});
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		class SymbolViewHolder extends RecyclerView.ViewHolder {
			ImageView icon;
			TextView text;

			SymbolViewHolder(View view) {
				super(view);
				icon = view.findViewById(R.id.symbolIcon);
				text = view.findViewById(R.id.symbolText);
			}
		}
	}
}
