package com.cs.ide.termux.app.activities;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		WebView webView = new WebView(this);
		setContentView(webView);
		webView.loadUrl("https://wiki.termux.com/wiki/Main_Page");
	}

}
