/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui;

import static de.monocles.mod.ui.PermissionsActivity.permissions;
import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_DECRYPT_PGP;
import static eu.siacs.conversations.ui.SettingsActivity.HIDE_MEMORY_WARNING;
import static eu.siacs.conversations.ui.SettingsActivity.MIN_ANDROID_SDK21_SHOWN;
import static eu.siacs.conversations.utils.AccountUtils.MANAGE_ACCOUNT_ACTIVITY;
import static eu.siacs.conversations.utils.StorageHelper.getAppMediaDirectory;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import de.monocles.mod.DownloadDefaultStickers;

import net.java.otr4j.session.SessionStatus;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.databinding.DataBindingUtil;

import org.openintents.openpgp.util.OpenPgpApi;

import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.Compatibility;
import io.michaelrocks.libphonenumber.android.NumberParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.util.ActionBarUtil;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.IntroHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.ui.util.UpdateHelper;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import me.drakeet.support.toast.ToastCompat;
import eu.siacs.conversations.utils.ThemeHelper;
import p32929.easypasscodelock.Utils.EasyLock;

import com.google.android.material.bottomnavigation.BottomNavigationView;


public class ConversationsActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnAffiliationChanged, XmppConnectionService.OnRoomDestroy {

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
    public static final String EXTRA_AS_QUOTE = "eu.siacs.conversations.as_quote";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_IS_PRIVATE_MESSAGE = "pm";
    public static final String EXTRA_DO_NOT_APPEND = "do_not_append";
    public static final String EXTRA_POST_INIT_ACTION = "post_init_action";
    public static final String POST_ACTION_RECORD_VOICE = "record_voice";
    public static final String ACTION_DESTROY_MUC = "eu.siacs.conversations.DESTROY_MUC";
    public static final int REQUEST_OPEN_MESSAGE = 0x9876;
    public static final int REQUEST_PLAY_PAUSE = 0x5432;
    public static final int REQUEST_MICROPHONE = 0x5432f;
    public static final int DIALLER_INTEGRATION = 0x5432ff;
    public static final int REQUEST_DOWNLOAD_STICKERS = 0xbf8702;
    public static final int REQUEST_CODE_ULOCK = 0xbf8706;
    public static final String EXTRA_THREAD = "threadId";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NODE = "node";
    public static final String EXTRA_JID = "jid";

    public static BottomNavigationView bottomNavigationView;

    private static final List<String> VIEW_AND_SHARE_ACTIONS = Arrays.asList(
            ACTION_VIEW_CONVERSATION,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    );

    private boolean showLastSeen;

    AlertDialog memoryWarningDialog;

    long FirstStartTime = -1;
    String PREF_FIRST_START = "FirstStart";

    //secondary fragment (when holding the conversation, must be initialized before refreshing the overview fragment
    private static final @IdRes
    int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.secondary_fragment, R.id.main_fragment};
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private ActivityConversationsBinding binding;
    private boolean mActivityPaused = true;
    private AtomicBoolean mRedirectInProcess = new AtomicBoolean(false);
    private boolean refreshForNewCaps = false;
    private Set<Jid> newCapsJids = new HashSet<>();
    private int mRequestCode = -1;


    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null && VIEW_AND_SHARE_ACTIONS.contains(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    protected void refreshUiReal() {
        invalidateActionBarTitle();
        invalidateOptionsMenu();
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
        refreshForNewCaps = false;
        newCapsJids.clear();

        // Показ значка для непрочитанных сообщений в нижней навигации
        int unreadCount = xmppConnectionService.unreadCount();
        BottomNavigationView bottomnav = findViewById(R.id.bottom_navigation);
        var bottomBadge = bottomnav.getOrCreateBadge(R.id.chats);
        bottomBadge.setNumber(unreadCount);
        bottomBadge.setVisible(unreadCount > 0);
        bottomBadge.setHorizontalOffset(20);
    }

    @Override
    protected void onBackendConnected() {
        if (performRedirectIfNecessary(true)) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationsActivity onBackendConnected(): setIsInForeground = true");
        xmppConnectionService.getNotificationService().setIsInForeground(true);

        final Intent FirstStartIntent = getIntent();
        final Bundle extras = FirstStartIntent.getExtras();
        if (extras != null && extras.containsKey(PREF_FIRST_START)) {
            FirstStartTime = extras.getLong(PREF_FIRST_START);
            Log.d(Config.LOGTAG, "Get first start time from StartUI: " + FirstStartTime);
        }

        final Intent intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                if (binding.secondaryFragment != null) {
                    notifyFragmentOfBackendConnected(R.id.main_fragment);
                }
                return;
            }
        }

        if (FirstStartTime == 0) {
            Log.d(Config.LOGTAG, "First start time: " + FirstStartTime + ", restarting App");
            //write first start timestamp to file
            FirstStartTime = System.currentTimeMillis();
            SharedPreferences FirstStart = getApplicationContext().getSharedPreferences(PREF_FIRST_START, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = FirstStart.edit();
            editor.putLong(PREF_FIRST_START, FirstStartTime);
            editor.commit();
            // restart if storage not accessable
            if (FileBackend.getDiskSize() > 0) {
                return;
            } else {
                Intent restartintent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                restartintent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                restartintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(restartintent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                System.exit(0);
            }
        }

        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            notifyFragmentOfBackendConnected(id);
        }

        final ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }

        if (binding.secondaryFragment != null && ConversationFragment.getConversation(this) == null) {
            Conversation conversation = ConversationsOverviewFragment.getSuggestion(this);
            if (conversation != null) {
                openConversation(conversation, null);
            }
        }

        // SDK >= 33 permissions
        if (Compatibility.runsThirtyThree()) {
            ActivityCompat.requestPermissions(this,
                    permissions(),
                    1);
        }

        invalidateActionBarTitle();
        showDialogsIfMainIsOverview();
    }

    private boolean performRedirectIfNecessary(boolean noAnimation) {
        return performRedirectIfNecessary(null, noAnimation);
    }

    private boolean performRedirectIfNecessary(final Conversation ignore, final boolean noAnimation) {
        if (xmppConnectionService == null) {
            return false;
        }
        boolean isConversationsListEmpty = xmppConnectionService.isConversationsListEmpty(ignore);
        if (isConversationsListEmpty && mRedirectInProcess.compareAndSet(false, true)) {
            final Intent intent = SignupUtils.getRedirectionIntent(this);
            if (noAnimation) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            }
            runOnUiThread(() -> {
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                if (noAnimation) {
                    overridePendingTransition(0, 0);
                }
            });
        }
        return mRedirectInProcess.get();
    }

    private void showDialogsIfMainIsOverview() {
        if (xmppConnectionService == null) {
            return;
        }
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {

            if (offerToSetupDiallerIntegration()) return;
            if (offerToDownloadStickers()) return;
            openBatteryOptimizationDialogIfNeeded();
            new showMemoryWarning(this).execute();
            showOutdatedVersionWarning();
            xmppConnectionService.rescanStickers();
        }

    }

    private void showOutdatedVersionWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || getPreferences().getBoolean(MIN_ANDROID_SDK21_SHOWN, false)) {
            Log.d(Config.LOGTAG, "Device is running Android >= SDK 21");
            return;
        }
        Log.d(Config.LOGTAG, "Device is running Android < SDK 21");
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.oldAndroidVersion);
        builder.setMessage(R.string.oldAndroidVersionMessage);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            getPreferences().edit().putBoolean(MIN_ANDROID_SDK21_SHOWN, true).apply();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private String getBatteryOptimizationPreferenceKey() {
        @SuppressLint("HardwareIds")
        String device = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "show_battery_optimization" + (device == null ? "" : device);
    }

    private void setNeverAskForBatteryOptimizationsAgain() {
        getPreferences().edit().putBoolean(getBatteryOptimizationPreferenceKey(), false).apply();
    }

    public boolean getAttachmentChoicePreference() {
        return getBooleanPreference(SettingsActivity.QUICK_SHARE_ATTACHMENT_CHOICE, R.bool.quick_share_attachment_choice);
    }

    public boolean warnUnecryptedChat() {
        return getBooleanPreference(SettingsActivity.WARN_UNENCRYPTED_CHAT, R.bool.warn_unencrypted_chat);
    }

    private void openBatteryOptimizationDialogIfNeeded() {
        if (isOptimizingBattery()
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && getPreferences().getBoolean(getBatteryOptimizationPreferenceKey(), true)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.battery_optimizations_enabled);
            builder.setMessage(R.string.battery_optimizations_enabled_dialog);
            builder.setPositiveButton(R.string.next, (dialog, which) -> {
                final Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                final Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                try {
                    startActivityForResult(intent, REQUEST_BATTERY_OP);
                } catch (ActivityNotFoundException e) {
                    ToastCompat.makeText(this, R.string.device_does_not_support_battery_op, ToastCompat.LENGTH_SHORT).show();
                }
            });
            builder.setOnDismissListener(dialog -> setNeverAskForBatteryOptimizationsAgain());
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    private boolean offerToDownloadStickers() {
        int offered = getPreferences().getInt("default_stickers_offered", 0);
        if (offered > 0) return false;
        getPreferences().edit().putInt("default_stickers_offered", 1).apply();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_sticker);
        builder.setMessage(R.string.download_sticker_summary);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            if (hasStoragePermission(REQUEST_DOWNLOAD_STICKERS)) {
                downloadStickers();
            }
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            showDialogsIfMainIsOverview();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return true;
    }

    private boolean offerToSetupDiallerIntegration() {
        if (mRequestCode == DIALLER_INTEGRATION) {
            mRequestCode = -1;
            return true;
        }
        if (Build.VERSION.SDK_INT < 23) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM) && !getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return false;
        } else {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return false;
        }

        Set<String> pstnGateways = new HashSet<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            for (Contact contact : account.getRoster().getContacts()) {
                if (contact.getPresences().anyIdentity("gateway", "pstn")) {
                    pstnGateways.add(contact.getJid().asBareJid().toEscapedString());
                }
            }
        }

        if (pstnGateways.size() < 1) return false;
        Set<String> fromPrefs = getPreferences().getStringSet("pstn_gateways", Set.of("UPGRADE"));
        getPreferences().edit().putStringSet("pstn_gateways", pstnGateways).apply();
        pstnGateways.removeAll(fromPrefs);
        if (pstnGateways.size() < 1) return false;

        if (fromPrefs.contains("UPGRADE")) return false;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Dialler Integration");
        builder.setMessage("monocles mod is able to integrate with your system's dialler app to allow dialling calls via your configured gateway " + String.join(", ", pstnGateways) + ".\n\nEnabling this integration will require granting microphone permission to the app.  Would you like to enable it now?");
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            final String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT};
            } else {
                permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            }
            requestPermissions(permissions, REQUEST_MICROPHONE);
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            showDialogsIfMainIsOverview();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return true;
    }

    public class showMemoryWarning extends AsyncTask<Void, Void, Void> {

        ConversationsActivity activity;
        long totalMemory = 0;
        long mediaUsage = 0;
        double relativeUsage = 0;
        String percentUsage = "0%";
        boolean force = false;
        // normal warning: more or equals 20 % or 10 GiB and automatic file deletion is disabled
        double normalWarningRelative = 0.2f; // 20%
        double normalWarningAbsolute = 10f * 1024 * 1024 * 1024; // 10 GiB
        // force warning: usage is more than 50%
        double forceWarningRelative = 0.5f; // 50%

        public showMemoryWarning(ConversationsActivity conversationsActivity) {
            activity = conversationsActivity;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                totalMemory = FileBackend.getDiskSize();
                mediaUsage = FileBackend.getDirectorySize(new File(getAppMediaDirectory(activity, null)));
                relativeUsage = ((double) mediaUsage / (double) totalMemory);
                try {
                    percentUsage = String.format(Locale.getDefault(),"%.2f", relativeUsage * 100) + " %";
                } catch (Exception e) {
                    e.printStackTrace();
                    percentUsage = String.format(Locale.ENGLISH,"%.2f", relativeUsage * 100) + " %";
                }
                force = relativeUsage > forceWarningRelative;
            }   catch (Exception e) {
                e.printStackTrace();
                relativeUsage = 0;
            }
            return null;
        }

        private boolean showWarning(boolean force) {
            if (force) {
                SharedPreferences preferences = getPreferences();
                preferences.edit().putBoolean(HIDE_MEMORY_WARNING, false).apply();
                return true;
            }
            return !xmppConnectionService.hideMemoryWarning() && (relativeUsage > normalWarningRelative || mediaUsage >= normalWarningAbsolute) & xmppConnectionService.getAutomaticAttachmentDeletionDate() == 0;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            Log.d(Config.LOGTAG, "Memory management: using " + UIHelper.filesizeToString(mediaUsage) + " from " + UIHelper.filesizeToString(totalMemory) + " (" + percentUsage + ")");
            if (showWarning(force) && activity != null) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    try {
                        final Intent intent = new Intent(activity, SettingsActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.addCategory("android.intent.category.PREFERENCE");
                        intent.putExtra("page", "security");
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }
                });
                if (!force) {
                    builder.setNeutralButton(R.string.hide_warning, (dialog, which) -> {
                        SharedPreferences preferences = getPreferences();
                        preferences.edit().putBoolean(HIDE_MEMORY_WARNING, true).apply();
                    });
                }
                memoryWarningDialog = builder.create();
                memoryWarningDialog.setTitle(R.string.title_memory_management);
                if (force) {
                    memoryWarningDialog.setMessage(getResources().getString(R.string.memory_warning_force, UIHelper.filesizeToString(mediaUsage), percentUsage));
                } else {
                    memoryWarningDialog.setMessage(getResources().getString(R.string.memory_warning, UIHelper.filesizeToString(mediaUsage), percentUsage));
                }
                memoryWarningDialog.setCanceledOnTouchOutside(false);
                memoryWarningDialog.show();
            }
        }
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected) {
            ((OnBackendConnected) fragment).onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof XmppFragment) {
            ((XmppFragment) fragment).refresh();
            if (refreshForNewCaps) ((XmppFragment) fragment).refreshForNewCaps(newCapsJids);
        }
    }

    private boolean processViewIntent(Intent intent) {
        Log.d(Config.LOGTAG, "process view intent");
        final String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        final Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
            return false;
        }
        openConversation(conversation, intent.getExtras());
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_OPEN_MESSAGE:
                        refreshUiReal();
                        ConversationFragment.openPendingMessage(this);
                        break;
                    case REQUEST_PLAY_PAUSE:
                        ConversationFragment.startStopPending(this);
                        break;
                    case REQUEST_MICROPHONE:
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName("com.android.server.telecom",
                                "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
                        startActivityForResult(intent, DIALLER_INTEGRATION);
                        break;
                    case REQUEST_DOWNLOAD_STICKERS:
                        downloadStickers();
                        break;
                }
            } else {
                showDialogsIfMainIsOverview();
            }
        } else {
            showDialogsIfMainIsOverview();
        }
    }

    private void downloadStickers() {
        Intent intent = new Intent(this, DownloadDefaultStickers.class);
        intent.putExtra("tor", xmppConnectionService.useTorToConnect());
        intent.putExtra("i2p", xmppConnectionService.useI2PToConnect());
        ContextCompat.startForegroundService(this, intent);
        displayToast("Sticker download started");
        showDialogsIfMainIsOverview();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIALLER_INTEGRATION) {
            mRequestCode = requestCode;
            try {
                startActivity(new Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
            } catch (ActivityNotFoundException e) {
                displayToast("Dialler integration not available on your OS");
            }
            return;
        }

        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    private void handleActivityResult(final ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
        if (activityResult.requestCode == REQUEST_BATTERY_OP) {
            // the result code is always 0 even when battery permission were granted
            requestNotificationPermissionIfNeeded();
            XmppConnectionService.toggleForegroundService(xmppConnectionService);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATION);
        }
    }

    private void handleNegativeActivityResult(int requestCode) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                if (conversation == null) {
                    break;
                }
                conversation.getAccount().getPgpDecryptionService().giveUpCurrentDecryption();
                break;
            case REQUEST_BATTERY_OP:
                setNeverAskForBatteryOptimizationsAgain();
                break;
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        Log.d(Config.LOGTAG, "positive activity result");
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        if (conversation == null) {
            Log.d(Config.LOGTAG, "conversation not found");
            return;
        }
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                conversation.getAccount().getPgpDecryptionService().continueDecryption(data);
                break;
            case REQUEST_CHOOSE_PGP_ID:
                long id = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0);
                if (id != 0) {
                    conversation.getAccount().setPgpSignId(id);
                    announcePgp(conversation.getAccount(), null, null, onOpenPGPKeyPublished);
                } else {
                    choosePgpSignId(conversation.getAccount());
                }
                break;
            case REQUEST_ANNOUNCE_PGP:
                announcePgp(conversation.getAccount(), conversation, data, onOpenPGPKeyPublished);
                break;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // Check if lock is set
        if (getBooleanPreference("app_lock_enabled", R.bool.app_lock_enabled)) {
            EasyLock.setBackgroundColor(getColor(R.color.primary_black));
            EasyLock.checkPassword(this);
            EasyLock.forgotPassword(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast.makeText(ConversationsActivity.this, R.string.app_lock_forgot_password, Toast.LENGTH_LONG).show();
                }
            });
        }
        super.onCreate(savedInstanceState);
        OmemoSetting.load(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewOrShareIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }

        UpdateHelper.showPopup(this);


        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {

            switch (item.getItemId()) {
                case R.id.chats -> {
                    return true;
                }
                case R.id.contactslist -> {
                    Intent i = new Intent(getApplicationContext(), StartConversationActivity.class);
                    i.putExtra("show_nav_bar", true);
                    startActivity(i);

                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    return true;
                }
                case R.id.manageaccounts -> {
                    Intent i = new Intent(getApplicationContext(), MANAGE_ACCOUNT_ACTIVITY);
                    i.putExtra("show_nav_bar", true);
                    startActivity(i);
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    return true;
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + item.getItemId());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        final MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        final MenuItem inviteUser = menu.findItem(R.id.action_invite_user);
        final MenuItem markAllChatsAsRead = menu.findItem(R.id.action_mark_all_chats_as_read); // Новый пункт меню
        if (qrCodeScanMenuItem != null) {
            if (isCameraFeatureAvailable()) {
                Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
                boolean visible = getResources().getBoolean(R.bool.show_qr_code_scan)
                        && fragment instanceof ConversationsOverviewFragment;
                qrCodeScanMenuItem.setVisible(visible);
            } else {
                qrCodeScanMenuItem.setVisible(false);
            }
        }
        if (xmppConnectionServiceBound && xmppConnectionService.getAccounts().size() > 0) {
            inviteUser.setVisible(true);
            markAllChatsAsRead.setVisible(xmppConnectionService.getConversations().stream().anyMatch(c -> c.unreadCount() > 0));
        } else {
            inviteUser.setVisible(false);
            markAllChatsAsRead.setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }


    // Новый метод для отметки всех чатов как прочитанных
    private void markAllChatsAsRead() {
        if (xmppConnectionService != null) {
            List<Conversation> conversations = xmppConnectionService.getConversations();
            for (Conversation conv : conversations) {
                if (conv.unreadCount() > 0) {
                    xmppConnectionService.markRead(conv);
                }
            }
            // Обновляем UI
            refreshUiReal();
            ToastCompat.makeText(this, R.string.marked_all_chats_as_read, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        clearPendingViewIntent();
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(Config.LOGTAG, "ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }


    public boolean navigationBarVisible() {
        return findViewById(R.id.bottom_navigation).getVisibility() == View.VISIBLE;
    }

    public boolean showNavigationBar() {
        if (!getBooleanPreference("show_nav_bar", R.bool.show_nav_bar)) {
            findViewById(R.id.bottom_navigation).setVisibility(View.GONE);
            return false;
        }

        findViewById(R.id.bottom_navigation).setVisibility(View.VISIBLE);
        return true;
    }

    public void hideNavigationBar() {
        findViewById(R.id.bottom_navigation).setVisibility(View.GONE);
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> ToastCompat.makeText(ConversationsActivity.this, msg, ToastCompat.LENGTH_SHORT).show());
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toEscapedString()));
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        final FragmentManager fragmentManager = getFragmentManager();
        executePendingTransactions(fragmentManager);
        ConversationFragment conversationFragment = (ConversationFragment) fragmentManager.findFragmentById(R.id.secondary_fragment);
        xmppConnectionService.updateNotificationChannels();
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.setCustomAnimations(
                        R.animator.fade_right_in, R.animator.fade_right_out,
                        R.animator.fade_right_in, R.animator.fade_right_out
                );
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                try {
                    fragmentTransaction.commit();
                } catch (IllegalStateException e) {
                    Log.w(Config.LOGTAG, "sate loss while opening conversation", e);
                    //allowing state loss is probably fine since view intents et all are already stored and a click can probably be 'ignored'
                    return;
                }
            }
        } else {
            mainNeedsRefresh = true;
        }
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        } else {
            invalidateActionBarTitle();
        }
        IntroHelper.showIntro(this, conversation.getMode() == Conversational.MODE_MULTI);
    }

    static void executePendingTransactions(final FragmentManager fragmentManager) {
        try {
            fragmentManager.executePendingTransactions();
        } catch (final Exception e) {
            Log.e(Config.LOGTAG,"unable to execute pending fragment transactions");
        }
    }

    public boolean onXmppUriClicked(Uri uri) {
        XmppUri xmppUri = new XmppUri(uri);
        if (xmppUri.isValidJid() && !xmppUri.hasFingerprints()) {
            final Conversation conversation = xmppConnectionService.findUniqueConversationByJid(xmppUri);
            if (conversation != null) {
                if (xmppUri.getParameter("password") != null) {
                    xmppConnectionService.providePasswordForMuc(conversation, xmppUri.getParameter("password"));
                }
                if (xmppUri.isAction("command")) {
                    startCommand(conversation.getAccount(), xmppUri.getJid(), xmppUri.getParameter("node"));
                } else {
                    Bundle extras = new Bundle();
                    extras.putString(Intent.EXTRA_TEXT, xmppUri.getBody());
                    if (xmppUri.isAction("message")) extras.putString(EXTRA_POST_INIT_ACTION, "message");
                    openConversation(conversation, extras);
                }
                return true;
            }
        }
        return false;
    }

    public boolean onTelUriClicked(Uri uri, Account acct) {
        final String tel;
        try {
            tel = PhoneNumberUtilWrapper.normalize(this, uri.getSchemeSpecificPart());
        } catch (final IllegalArgumentException | NumberParseException | NullPointerException e) {
            return false;
        }

        Set<String> gateways = new HashSet<>();
        for (Account account : (acct == null ? xmppConnectionService.getAccounts() : List.of(acct))) {
            for (Contact contact : account.getRoster().getContacts()) {
                if (contact.getPresences().anyIdentity("gateway", "pstn") || contact.getPresences().anyIdentity("gateway", "sms")) {
                    if (acct == null) acct = account;
                    gateways.add(contact.getJid().asBareJid().toEscapedString());
                }
            }
        }

        for (String gateway : gateways) {
            if (onXmppUriClicked(Uri.parse("xmpp:" + tel + "@" + gateway))) return true;
        }

        if (gateways.size() == 1 && acct != null) {
            openConversation(xmppConnectionService.findOrCreateConversation(acct, Jid.ofLocalAndDomain(tel, gateways.iterator().next()), false, true), null);
            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    Fragment f = fm.getFragments().get(fm.getFragments().size() - 1);
                    if (f != null && f instanceof ConversationFragment) {
                        if (((ConversationFragment) f).onBackPressed()) {
                            return true;
                        }
                    }
                }
                if (fm.getBackStackEntryCount() > 0) {
                    try {
                        fm.popBackStack();
                    } catch (IllegalStateException e) {
                        Log.w(Config.LOGTAG, "Unable to pop back stack after pressing home button");
                    }
                    return true;
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                return true;
            case R.id.action_search_all_conversations:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_search_this_conversation:
                final Conversation conversation = ConversationFragment.getConversation(this);
                if (conversation == null) {
                    return true;
                }
                final Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
                startActivity(intent);
                return true;
            case R.id.action_invite_user:
                inviteUser();
                break;
            case R.id.action_mark_all_chats_as_read:
                markAllChatsAsRead();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && keyEvent.isCtrlPressed()) {
            final ConversationFragment conversationFragment = ConversationFragment.get(this);
            if (conversationFragment != null && conversationFragment.onArrowUpCtrlPressed()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        final Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme || !this.mCustomColors.equals(ThemeHelper.applyCustomColors(this))) {
            this.mSkipBackgroundBinding = true;
            recreate();
        } else {
            this.mSkipBackgroundBinding = false;
        }
        mRedirectInProcess.set(false);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showLastSeen = preferences.getBoolean("last_activity", getResources().getBoolean(R.bool.last_activity));

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.chats);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (isViewOrShareIntent(intent)) {
            if (xmppConnectionService != null) {
                clearPendingViewIntent();
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        } else if (intent != null && ACTION_DESTROY_MUC.equals(intent.getAction())) {
            try {
                final Bundle extras = intent.getExtras();
                if (extras != null && extras.containsKey("MUC_UUID")) {
                    Log.d(Config.LOGTAG, "Get " + intent.getAction() + " intent for " + extras.getString("MUC_UUID"));
                    Conversation conversation = xmppConnectionService.findConversationByUuid(extras.getString("MUC_UUID"));
                    ConversationsActivity.this.xmppConnectionService.clearConversationHistory(conversation);
                    xmppConnectionService.destroyRoom(conversation, ConversationsActivity.this);
                    endConversation(conversation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        setIntent(createLauncherIntent(this));
    }

    public void endConversation(Conversation conversation) {
        xmppConnectionService.archiveConversation(conversation);
        onConversationArchived(conversation);
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
        hideMemoryWarningDialog();
    }

    private void hideMemoryWarningDialog() {
        if (memoryWarningDialog != null && memoryWarningDialog.isShowing()) {
            memoryWarningDialog.cancel();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        invalidateActionBarTitle();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        Fragment secondaryFragment = getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            Log.d(Config.LOGTAG, "initializeFragment(). main fragment exists");
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG, "gained secondary fragment. moving...");
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG, "lost secondary fragment. moving...");
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }

        if (binding.secondaryFragment != null && secondaryFragment == null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
            if (conversation != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                final View view = getLayoutInflater().inflate(R.layout.ab_title, null);
                getSupportActionBar().setCustomView(view);
                actionBar.setIcon(null);
                actionBar.setBackgroundDrawable(new ColorDrawable(StyledAttributes.getColor(this, R.attr.color_background_secondary)));
                actionBar.setDisplayShowCustomEnabled(true);
                TextView abtitle = findViewById(android.R.id.text1);
                TextView absubtitle = findViewById(android.R.id.text2);
                final View avatartoolbar = view.findViewById(R.id.toolbar_avatar);
                abtitle.setText(conversation.getName());
                abtitle.setSelected(true);

                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    if (!conversation.withSelf()) {
                        ChatState state = conversation.getIncomingChatState();
                        if (state == ChatState.COMPOSING) {
                            absubtitle.setText(getString(R.string.is_typing));
                            absubtitle.setVisibility(View.VISIBLE);
                            absubtitle.setTypeface(null, Typeface.BOLD_ITALIC);
                            absubtitle.setSelected(true);
                        } else {
                            if (showLastSeen && conversation.getContact().getLastseen() > 0 && conversation.getContact().getPresences().allOrNonSupport(Namespace.IDLE)) {
                                absubtitle.setText(UIHelper.lastseen(getApplicationContext(), conversation.getContact().isActive(), conversation.getContact().getLastseen()));
                                absubtitle.setVisibility(View.VISIBLE);
                            } else {
                                absubtitle.setText(null);
                                absubtitle.setVisibility(View.GONE);
                            }
                            absubtitle.setSelected(true);
                        }
                    } else {
                        absubtitle.setText(null);
                        absubtitle.setVisibility(View.GONE);
                    }
                } else {
                    List<MucOptions.User> onlineUsers = new ArrayList<>();
                    for (MucOptions.User user : conversation.getMucOptions().getUsers(true)) {
                        if (user.isOnline()) {
                            onlineUsers.add(user);
                        }
                    }

                    int onlineCount = onlineUsers.size();
                    if (onlineCount > 0) {
                        String subtitle = getResources().getQuantityString(R.plurals.participants, onlineCount, onlineCount);
                        absubtitle.setText(subtitle);
                        absubtitle.setVisibility(View.VISIBLE);
                    } else {
                        absubtitle.setText(null);
                        absubtitle.setVisibility(View.GONE);
                    }
                    absubtitle.setSelected(true);
                }

                AvatarWorkerTask.loadAvatar(conversation, (ImageView) avatartoolbar, R.dimen.avatar_actionbar);
                findViewById(R.id.toolbar_avatar).setVisibility(View.VISIBLE);
                ActionBarUtil.setCustomActionBarOnClickListener(
                        binding.toolbar.getRoot(),
                        (v) -> openConversationDetails(conversation)
                );
                return;
            }
        }

        Typeface font = ResourcesCompat.getFont(this, R.font.notosanssemibold);
        SpannableStringBuilder app_title = new SpannableStringBuilder("monocles mod");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int end = Math.min(13, app_title.length());
            app_title.setSpan(new TypefaceSpan(font), 0, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }

        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setTitle(app_title);
        actionBar.setDisplayHomeAsUpEnabled(false);
        ActionBarUtil.resetCustomActionBarOnClickListeners(binding.toolbar.getRoot());
    }


    private void openConversationDetails(final Conversation conversation) {
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ConferenceDetailsActivity.open(this, conversation);
        } else {
            final Contact contact = conversation.getContact();
            if (contact.isSelf()) {
                switchToAccount(conversation.getAccount());
            } else {
                switchToContactDetails(contact);
            }
        }
    }
    public void verifyOtrSessionDialog(final Conversation conversation, View view) {
        if (!conversation.hasValidOtrSession() || conversation.getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
            ToastCompat.makeText(this, R.string.otr_session_not_started, Toast.LENGTH_LONG).show();
            return;
        }
        if (view == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, view);
        popup.inflate(R.menu.verification_choices);
        popup.setOnMenuItemClickListener(menuItem -> {
            Intent intent = new Intent(ConversationsActivity.this, VerifyOTRActivity.class);
            intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
            intent.putExtra("contact", conversation.getContact().getJid().asBareJid().toString());
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            switch (menuItem.getItemId()) {
                case R.id.ask_question:
                    intent.putExtra("mode", VerifyOTRActivity.MODE_ASK_QUESTION);
                    break;
            }
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            return true;
        });
        popup.show();
    }
    @Override
    public void onConversationArchived(Conversation conversation) {
        if (performRedirectIfNecessary(conversation, false)) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            try {
                fragmentManager.popBackStack();
            } catch (final IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while popping back state after archiving conversation", e);
                //this usually means activity is no longer active; meaning on the next open we will run through this again
            }
            return;
        }
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion = ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        Log.d(Config.LOGTAG, "override");
        openConversation(conversation, null);
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (xmppConnectionService != null && !mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + Boolean.toString(mActivityPaused));
        }
    }

    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate(boolean newCaps) {
        if (performRedirectIfNecessary(false)) {
            return;
        }
        refreshForNewCaps = newCaps;
        this.refreshUi();
    }

    @Override
    public void onRosterUpdate(final XmppConnectionService.UpdateRosterReason reason, final Contact contact) {
        if (reason != XmppConnectionService.UpdateRosterReason.AVATAR) {
            refreshForNewCaps = true;
            if (contact != null) newCapsJids.add(contact.getJid().asBareJid());
        }
        this.refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(OnUpdateBlocklist.Status status) {
        this.refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        runOnUiThread(() -> ToastCompat.makeText(this, resId, ToastCompat.LENGTH_SHORT).show());
    }

    protected void AppUpdate(String Store) {
        String PREFS_NAME = "UpdateTimeStamp";
        SharedPreferences UpdateTimeStamp = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdateTime = UpdateTimeStamp.getLong("lastUpdateTime", 0);
        Log.d(Config.LOGTAG, "AppUpdater: LastUpdateTime: " + lastUpdateTime);
        if ((lastUpdateTime + (Config.UPDATE_CHECK_TIMER * 1000)) < System.currentTimeMillis()) {
            lastUpdateTime = System.currentTimeMillis();
            SharedPreferences.Editor editor = UpdateTimeStamp.edit();
            editor.putLong("lastUpdateTime", lastUpdateTime);
            editor.apply();
            Log.d(Config.LOGTAG, "AppUpdater: CurrentTime: " + lastUpdateTime);
            openInstallFromUnknownSourcesDialogIfNeeded(false);
        } else {
            Log.d(Config.LOGTAG, "AppUpdater stopped");
        }
    }

    @Override
    public void onRoomDestroySucceeded() {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        final boolean groupChat = conversation != null && conversation.isPrivateAndNonAnonymous();
        displayToast(getString(groupChat ? R.string.destroy_room_succeed : R.string.destroy_channel_succeed));
    }

    @Override
    public void onRoomDestroyFailed() {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        final boolean groupChat = conversation != null && conversation.isPrivateAndNonAnonymous();
        displayToast(getString(groupChat ? R.string.destroy_room_failed : R.string.destroy_channel_failed));
    }
}