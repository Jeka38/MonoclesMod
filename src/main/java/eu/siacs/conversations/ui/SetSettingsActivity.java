package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.SettingsActivity.BROADCAST_LAST_ACTIVITY;
import static eu.siacs.conversations.ui.SettingsActivity.CHAT_STATES;
import static eu.siacs.conversations.ui.SettingsActivity.CONFIRM_MESSAGES;
import static eu.siacs.conversations.ui.SettingsActivity.EASY_DOWNLOADER;
import static eu.siacs.conversations.ui.SettingsActivity.FORBID_SCREENSHOTS;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_LINKS_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_MAPS_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.USE_INNER_STORAGE;
import static eu.siacs.conversations.ui.SettingsActivity.USE_INVIDIOUS;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySetSettingsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.FirstStartManager;
import eu.siacs.conversations.utils.ThemeHelper;

public class SetSettingsActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate {
    ActivitySetSettingsBinding binding;
    Account account;
    static final int FORDBIDSCREENSHOTS = 1;
    static final int SHOWWEBLINKS = 2;
    static final int SHOWMAPPREVIEW = 3;
    static final int CHATSTATES = 4;
    static final int CONFIRMMESSAGES = 5;
    static final int LASTSEEN = 6;
    static final int INVIDIOUS = 7;
    static final int INNER_STORAGE = 8;
    static final int EASY_DOWNLOADER_ATTACHMENTS = 9;

    @Override
    protected void refreshUiReal() {
        createInfoMenu();
    }

    @Override
    protected void onBackendConnected() {
        this.account = AccountUtils.getFirst(xmppConnectionService);
        refreshUi();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_set_settings);
        setSupportActionBar((Toolbar) this.binding.toolbar.getRoot());
        this.binding.next.setOnClickListener(this::next);
        createInfoMenu();
        getDefaults();
        setTheme(ThemeHelper.find(this));
    }

    private void createInfoMenu() {
        this.binding.actionInfoForbidScreenshots.setOnClickListener(string -> showInfo(FORDBIDSCREENSHOTS));
        this.binding.actionInfoShowWeblinks.setOnClickListener(string -> showInfo(SHOWWEBLINKS));
        this.binding.actionInfoShowMapPreviews.setOnClickListener(string -> showInfo(SHOWMAPPREVIEW));
        this.binding.actionInfoChatStates.setOnClickListener(string -> showInfo(CHATSTATES));
        this.binding.actionInfoConfirmMessages.setOnClickListener(string -> showInfo(CONFIRMMESSAGES));
        this.binding.actionInfoLastSeen.setOnClickListener(string -> showInfo(LASTSEEN));
        this.binding.actionInfoInvidious.setOnClickListener(string -> showInfo(INVIDIOUS));
        this.binding.actionInfoUsingInnerStorage.setOnClickListener(string -> showInfo(INNER_STORAGE));
        this.binding.actionInfoEasyDownloader.setOnClickListener(string -> showInfo(EASY_DOWNLOADER_ATTACHMENTS));
    }

    private void getDefaults() {
        this.binding.forbidScreenshots.setChecked(getResources().getBoolean(R.bool.screen_security));
        this.binding.showLinks.setChecked(getResources().getBoolean(R.bool.show_links_inside));
        this.binding.showMappreview.setChecked(getResources().getBoolean(R.bool.show_maps_inside));
        this.binding.chatStates.setChecked(getResources().getBoolean(R.bool.chat_states));
        this.binding.confirmMessages.setChecked(getResources().getBoolean(R.bool.confirm_messages));
        this.binding.lastSeen.setChecked(getResources().getBoolean(R.bool.last_activity));
        this.binding.invidious.setChecked(getResources().getBoolean(R.bool.use_invidious));
        this.binding.usingInnerStorage.setChecked(getResources().getBoolean(R.bool.use_inner_storage));
        this.binding.easyDownloader.setChecked(getResources().getBoolean(R.bool.easy_downloader));
    }

    private void next(View view) {
        setSettings();
        FirstStartManager firstStartManager = new FirstStartManager(this);
        firstStartManager.setFirstTimeLaunch(false);
        if (account != null) {
            Intent intent = new Intent(this, PublishProfilePictureActivity.class);
            intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
            intent.putExtra("setup", true);
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
        finish();
    }

    private void showInfo(int setting) {
        String title;
        String message;
        switch (setting) {
            case FORDBIDSCREENSHOTS:
                title = getString(R.string.pref_screen_security);
                message = getString(R.string.pref_screen_security_summary);
                break;
            case SHOWWEBLINKS:
                title = getString(R.string.pref_show_links_inside);
                message = getString(R.string.pref_show_links_inside_summary);
                break;
            case SHOWMAPPREVIEW:
                title = getString(R.string.pref_show_mappreview_inside);
                message = getString(R.string.pref_show_mappreview_inside_summary);
                break;
            case CHATSTATES:
                title = getString(R.string.pref_chat_states);
                message = getString(R.string.pref_chat_states_summary);
                break;
            case CONFIRMMESSAGES:
                title = getString(R.string.pref_confirm_messages);
                message = getString(R.string.pref_confirm_messages_summary);
                break;
            case LASTSEEN:
                title = getString(R.string.pref_broadcast_last_activity);
                message = getString(R.string.pref_broadcast_last_activity_summary);
                break;
            case INVIDIOUS:
                title = getString(R.string.pref_use_invidious);
                message = getString(R.string.pref_use_invidious_summary);
                break;
            case INNER_STORAGE:
                title = getString(R.string.pref_use_inner_storage);
                message = getString(R.string.pref_use_inner_storage_summary);
                break;
            case EASY_DOWNLOADER_ATTACHMENTS:
                title = getString(R.string.pref_easy_downloader);
                message = getString(R.string.pref_easy_downloader_summary);
                break;
            default:
                title = getString(R.string.error);
                message = getString(R.string.error);
        }
        Log.d(Config.LOGTAG, "STRING value " + title);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNeutralButton(getString(R.string.ok), null);
        builder.create().show();
    }


    private void setSettings() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putBoolean(FORBID_SCREENSHOTS, this.binding.forbidScreenshots.isChecked()).apply();
        preferences.edit().putBoolean(SHOW_LINKS_INSIDE, this.binding.showLinks.isChecked()).apply();
        preferences.edit().putBoolean(SHOW_MAPS_INSIDE, this.binding.showMappreview.isChecked()).apply();
        preferences.edit().putBoolean(CHAT_STATES, this.binding.chatStates.isChecked()).apply();
        preferences.edit().putBoolean(CONFIRM_MESSAGES, this.binding.confirmMessages.isChecked()).apply();
        preferences.edit().putBoolean(BROADCAST_LAST_ACTIVITY, this.binding.lastSeen.isChecked()).apply();
        preferences.edit().putBoolean(USE_INVIDIOUS, this.binding.invidious.isChecked()).apply();
        preferences.edit().putBoolean(USE_INNER_STORAGE, this.binding.usingInnerStorage.isChecked()).apply();
        preferences.edit().putBoolean(EASY_DOWNLOADER, this.binding.easyDownloader.isChecked()).apply();
        FileBackend.switchStorage(xmppConnectionService.usingInnerStorage());
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}
