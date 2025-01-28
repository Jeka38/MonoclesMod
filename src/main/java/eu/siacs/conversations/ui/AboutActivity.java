package eu.siacs.conversations.ui;

import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.CustomTab;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.utils.ThemeHelper;
import me.drakeet.support.toast.ToastCompat;

public class AboutActivity extends XmppActivity {

    private TextView aboutmessage;
    private TextView libraries;

    @Override
    protected void refreshUiReal() {
        showText();
    }

    @Override
    protected void onBackendConnected() {
        showText();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.find(this));
        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        aboutmessage = findViewById(R.id.aboutmessage);
        libraries = findViewById(R.id.libraries);
        Button privacyButton = findViewById(R.id.show_privacy_policy);
        privacyButton.setOnClickListener(view -> {
            try {
                final Uri uri = Uri.parse(Config.privacyURL);
                CustomTab.openTab(this, uri, isDarkTheme());
            } catch (Exception e) {
                ToastCompat.makeText(this, R.string.no_application_found_to_open_link, ToastCompat.LENGTH_SHORT).show();
            }
        });
        Button termsOfUseButton = findViewById(R.id.show_terms_of_use);
        termsOfUseButton.setOnClickListener(view -> {
            try {
                final Uri uri = Uri.parse(Config.termsOfUseURL);
                CustomTab.openTab(this, uri, isDarkTheme());
            } catch (Exception e) {
                ToastCompat.makeText(this, R.string.no_application_found_to_open_link, ToastCompat.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        showText();
    }

    private void showText() {
        final String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        SpannableStringBuilder aboutMessage = new SpannableStringBuilder(getString(R.string.pref_about_message, year));
        MyLinkify.addLinks(aboutMessage, false);
        aboutmessage.setText(aboutMessage);
        aboutmessage.setAutoLinkMask(0);

        SpannableStringBuilder libs = new SpannableStringBuilder(getString(R.string.pref_about_libraries));
        MyLinkify.addLinks(libs, false);
        libraries.setText(libs);
        libraries.setAutoLinkMask(0);
    }
}