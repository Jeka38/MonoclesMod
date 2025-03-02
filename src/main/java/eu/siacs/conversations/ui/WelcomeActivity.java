package eu.siacs.conversations.ui;

import static de.monocles.mod.ui.PermissionsActivity.permissions;
import static eu.siacs.conversations.Config.DISALLOW_REGISTRATION_IN_UI;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.readGranted;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;

import de.monocles.mod.RegisterMonoclesActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.WelcomeBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.IntroHelper;
import eu.siacs.conversations.ui.util.UpdateHelper;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import me.drakeet.support.toast.ToastCompat;

public class WelcomeActivity extends XmppActivity implements XmppConnectionService.OnAccountCreated, KeyChainAliasCallback {

    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 0XD737;

    private XmppUri inviteUri;

    public void onInstallReferrerDiscovered(final Uri referrer) {
        Log.d(Config.LOGTAG, "welcome activity: on install referrer discovered " + referrer);
        if ("xmpp".equalsIgnoreCase(referrer.getScheme())) {
            final XmppUri xmppUri = new XmppUri(referrer);
            runOnUiThread(() -> processXmppUri(xmppUri));
        } else {
            Log.i(Config.LOGTAG, "install referrer was not an XMPP uri");
        }
    }

    private void processXmppUri(final XmppUri xmppUri) {
        if (!xmppUri.isValidJid()) {
            return;
        }
        final String preAuth = xmppUri.getParameter(XmppUri.PARAMETER_PRE_AUTH);
        final Jid jid = xmppUri.getJid();
        final Intent intent;
        if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
            intent = SignupUtils.getTokenRegistrationIntent(this, jid, preAuth, true);
        } else if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParameter(XmppUri.PARAMETER_IBR))) {
            intent = SignupUtils.getTokenRegistrationIntent(this, jid.getDomain(), preAuth);
            intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
        } else {
            intent = null;
        }
        if (intent != null) {
            startActivity(intent);
            finish();
            return;
        }
        this.inviteUri = xmppUri;
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
        // SDK >= 33 permissions
        if (Compatibility.runsThirtyThree()) {
            ActivityCompat.requestPermissions(this,
                    permissions(),
                    1);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
        new InstallReferrerUtils(this);

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            setIntent(intent);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        getPreferences().edit().putStringSet("pstn_gateways", new HashSet<>()).apply();
        WelcomeBinding binding = DataBindingUtil.setContentView(this, R.layout.welcome);
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
        }
        IntroHelper.showIntro(this, false);
        UpdateHelper.showPopup(this);
        if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
            binding.importDatabase.setVisibility(View.VISIBLE);
            binding.importText.setVisibility(View.VISIBLE);
        }
        binding.importDatabase.setOnClickListener(v -> startActivity(new Intent(this, ImportBackupActivity.class)));
        binding.createAccount.setOnClickListener(v -> {
            final Intent intent = new Intent(WelcomeActivity.this, RegisterMonoclesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            addInviteUri(intent);
            startActivity(intent);
        });
        if (DISALLOW_REGISTRATION_IN_UI) {
            binding.createAccount.setVisibility(View.GONE);
        }
        binding.useExistingAccount.setOnClickListener(v -> {
            final List<Account> accounts = xmppConnectionService.getAccounts();
            Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
            if (accounts.size() == 1) {
                intent.putExtra("jid", accounts.get(0).getJid().asBareJid().toString());
                intent.putExtra("init", true);
            } else if (accounts.size() >= 1) {
                intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
            }
            intent.putExtra("existing", true);
            addInviteUri(intent);
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            finish();
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        });
    }

    public void addInviteUri(Intent to) {
        final Intent from = getIntent();
        if (from != null && from.hasExtra(StartConversationActivity.EXTRA_INVITE_URI)) {
            final String invite = from.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI);
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, invite);
        } else if (this.inviteUri != null) {
            Log.d(Config.LOGTAG, "injecting referrer uri into on-boarding flow");
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, this.inviteUri.toString());
        }
    }

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.welcome_menu, menu);
        final MenuItem scan = menu.findItem(R.id.action_scan_qr_code);
        scan.setVisible(Compatibility.hasFeatureCamera(this));
        return super.onCreateOptionsMenu(menu);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this, true);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void alias(final String alias) {
        if (alias != null) {
            xmppConnectionService.createAccountFromKey(alias, this);
        }
    }

    @Override
    public void onAccountCreated(final Account account) {
        final Intent intent = new Intent(this, EditAccountActivity.class);
        intent.putExtra("jid", account.getJid().asBareJid().toEscapedString());
        intent.putExtra("init", true);
        addInviteUri(intent);
        startActivity(intent);
    }

    @Override
    public void informUser(final int r) {
        runOnUiThread(() -> ToastCompat.makeText(this, r, ToastCompat.LENGTH_LONG).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                       // startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else if (!Compatibility.runsThirtyThree() && Arrays.asList(permissions).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ToastCompat.makeText(this, R.string.no_storage_permission, ToastCompat.LENGTH_SHORT).show();
            } else if (Compatibility.runsThirtyThree() && Arrays.asList(permissions).contains(Manifest.permission.READ_MEDIA_IMAGES) && Arrays.asList(permissions).contains(Manifest.permission.READ_MEDIA_VIDEO) && Arrays.asList(permissions).contains(Manifest.permission.READ_MEDIA_AUDIO)) {
                ToastCompat.makeText(this, R.string.no_media_permission, ToastCompat.LENGTH_SHORT).show();
            }
        }
        if (readGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }
}