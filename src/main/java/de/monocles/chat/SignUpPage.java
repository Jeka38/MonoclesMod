package de.monocles.chat;

import static eu.siacs.conversations.ui.StartConversationActivity.addInviteUri;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.EditAccountActivity;
import eu.siacs.conversations.ui.MagicCreateActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.WelcomeActivity;

public class SignUpPage extends RegisterMonoclesActivity {
    final String url = "https://ocean.monocles.eu/apps/registration/";
    private WebView webView;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        webView = (WebView) findViewById(R.id.sign_up_view);
        ProgressBar progressBar = findViewById(R.id.progressbar);

        if(isNetworkAvailable()) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.loadUrl(url);
            webView.setWebViewClient(new WebViewClient() {
                 @Override
                 public boolean shouldOverrideUrlLoading(WebView view, String url) {
                     if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("smsto:") || url.startsWith("mailto:") || url.startsWith("mms:") || url.startsWith("mmsto:")) {
                         Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                         try {
                         startActivity(intent);
                         } catch (android.content.ActivityNotFoundException ex) {
                             Toast.makeText(SignUpPage.this, R.string.no_application_found, Toast.LENGTH_LONG).show();
                         }
                     } else {
                         webView.getSettings().setJavaScriptEnabled(true);
                         webView.getSettings().setPluginState(WebSettings.PluginState.ON);
                         webView.getSettings().setSupportZoom(true);

                         webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
                         webView.setScrollbarFadingEnabled(true);

                         view.loadUrl(url);
                     }
                     return true;
                 }
                // ProgressBar will disappear once page is loaded
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    progressBar.setVisibility(View.GONE);
                }
            });
        } else {
            Toast.makeText(this, R.string.account_status_no_internet, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.sign_up_page, menu);
        final MenuItem logInNow = menu.findItem(R.id.login_in_now);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.login_in_now:
                final List<Account> accounts = xmppConnectionService.getAccounts();
                Intent intent = new Intent(this, EditAccountActivity.class);
                if (accounts.size() == 1) {
                    intent.putExtra("jid", accounts.get(0).getJid().asBareJid().toString());
                    intent.putExtra("init", true);
                } else if (accounts.size() >= 1) {
                    intent = new Intent(this, ManageAccountActivity.class);
                }
                intent.putExtra("existing", true);
                //addInviteUri(intent);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                finish();
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
