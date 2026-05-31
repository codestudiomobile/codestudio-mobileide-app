package com.csmide.app.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.csmide.R;

public class HtmlPreviewFragment extends Fragment {

	private static final String ARG_URI = "uri";
	private Uri mUri;
	private WebView mWebView;

	public static HtmlPreviewFragment newInstance(Uri uri) {
		HtmlPreviewFragment fragment = new HtmlPreviewFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_URI, uri);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			mUri = getArguments().getParcelable(ARG_URI);
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_html_preview, container, false);
		mWebView = view.findViewById(R.id.webView);

		WebSettings settings = mWebView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setAllowFileAccess(true);
		settings.setAllowContentAccess(true);
		settings.setAllowFileAccessFromFileURLs(true);
		settings.setAllowUniversalAccessFromFileURLs(true);
		settings.setLoadWithOverviewMode(true);
		settings.setUseWideViewPort(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setSupportZoom(true);
		settings.setGeolocationEnabled(true);
		settings.setCacheMode(WebSettings.LOAD_DEFAULT);

		mWebView.setWebViewClient(new WebViewClient());

		if (mUri != null) {
			mWebView.loadUrl(mUri.toString());
		}

		return view;
	}

	public boolean canGoBack() {
		return mWebView != null && mWebView.canGoBack();
	}

	public void goBack() {
		if (mWebView != null) {
			mWebView.goBack();
		}
	}
}
