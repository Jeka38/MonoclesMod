package de.monocles.chat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.XmppActivity;

import androidx.appcompat.app.ActionBar;

import android.annotation.SuppressLint;
import android.widget.Toast;

public class WebxdcStore extends XmppActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webxdc_store);
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        configureActionBar(actionBar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        WebView webView = findViewById(R.id.web);
        String URL = "https://webxdc.org/apps/";
        if (isNetworkAvailable(this)) {
            webView.loadUrl(URL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.setForceDarkAllowed(true);
            }
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });
            webView.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                    final var intent = new Intent();
                    intent.setData(Uri.parse(url));
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            });
        } else {
            Toast.makeText(this, R.string.account_status_no_internet, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {
    }

    //check for internet connection
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}