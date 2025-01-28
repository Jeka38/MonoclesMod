package eu.siacs.conversations.ui;

import static android.view.View.VISIBLE;
import static eu.siacs.conversations.ui.SettingsActivity.REQUEST_CREATE_BACKUP;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.readGranted;

import android.Manifest;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.ExportBackupService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.xmpp.XmppConnection;
import me.drakeet.support.toast.ToastCompat;

public class ManageAccountActivity extends XmppActivity implements OnAccountUpdate, KeyChainAliasCallback, XmppConnectionService.OnAccountCreated, AccountAdapter.OnTglAccountState {

    private final String STATE_SELECTED_ACCOUNT = "selected_account";

    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;
    private static final int REQUEST_MICROPHONE = 0x63fb1;

    protected Account selectedAccount = null;
    protected Jid selectedAccountJid = null;

    protected final List<Account> accountList = new ArrayList<>();
    protected ListView accountListView;
    protected AccountAdapter mAccountAdapter;
    protected AtomicBoolean mInvokedAddAccount = new AtomicBoolean(false);
    protected Intent mMicIntent = null;

    protected Pair<Integer, Intent> mPostponedActivityResult = null;

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        synchronized (this.accountList) {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
        }
        ActionBar actionBar = getSupportActionBar();
        boolean showNavBar = findViewById(R.id.bottom_navigation).getVisibility() == VISIBLE;
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(!this.accountList.isEmpty() && !showNavBar);
            actionBar.setDisplayHomeAsUpEnabled(!this.accountList.isEmpty() && !showNavBar);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        invalidateOptionsMenu();
        mAccountAdapter.notifyDataSetChanged();


        findViewById(R.id.phone_accounts).setVisibility(View.GONE);
        findViewById(R.id.phone_accounts).setOnClickListener((View v) -> {
            mMicIntent = new Intent();
            mMicIntent.setComponent(new ComponentName("com.android.server.telecom",
                    "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
            requestMicPermission();
        });
        findViewById(R.id.phone_accounts_settings).setOnClickListener((View v) -> {
            mMicIntent = new Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
            requestMicPermission();
        });

        if (Build.VERSION.SDK_INT < 23) return;
        if (Build.VERSION.SDK_INT >= 33) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM) && !getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return;
        } else {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return;
        }

        outer:
        for (Account account : xmppConnectionService.getAccounts()) {
            for (Contact contact : account.getRoster().getContacts()) {
                if (contact.getPresences().anyIdentity("gateway", "pstn")) {
                    findViewById(R.id.phone_accounts).setVisibility(View.VISIBLE);
                    break outer;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        configureActionBar(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(false);
        if (savedInstanceState != null) {
            String jid = savedInstanceState.getString(STATE_SELECTED_ACCOUNT);
            if (jid != null) {
                try {
                    this.selectedAccountJid = Jid.ofEscaped(jid);
                } catch (IllegalArgumentException e) {
                    this.selectedAccountJid = null;
                }
            }
        }

        accountListView = findViewById(R.id.account_list);
        this.mAccountAdapter = new AccountAdapter(this, accountList);
        accountListView.setAdapter(this.mAccountAdapter);
        accountListView.setOnItemClickListener((arg0, view, position, arg3) -> switchToAccount(accountList.get(position)));
        registerForContextMenu(accountListView);

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {

            switch (item.getItemId()) {
                case R.id.chats -> {
                    startActivity(new Intent(getApplicationContext(), ConversationsActivity.class));
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
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
                    return true;
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + item.getItemId());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.manageaccounts);

        if (getBooleanPreference("show_nav_bar", R.bool.show_nav_bar) && getIntent().getBooleanExtra("show_nav_bar", false)) {
            bottomNavigationView.setVisibility(VISIBLE);
        } else {
            bottomNavigationView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        if (selectedAccount != null) {
            savedInstanceState.putString(STATE_SELECTED_ACCOUNT, selectedAccount.getJid().asBareJid().toEscapedString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ManageAccountActivity.this.getMenuInflater().inflate(
                R.menu.manageaccounts_context, menu);
        AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
        this.selectedAccount = accountList.get(acmi.position);
        if (this.selectedAccount.isEnabled()) {
            menu.findItem(R.id.mgmt_account_announce_pgp).setVisible(Config.supportOpenPgp());
        } else {
            menu.findItem(R.id.mgmt_account_reconnect).setVisible(false);
            menu.findItem(R.id.mgmt_account_announce_pgp).setVisible(false);
            menu.findItem(R.id.mgmt_account_publish_avatar).setVisible(false);
        }
        menu.setHeaderTitle(this.selectedAccount.getJid().asBareJid().toEscapedString());
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.bottom_navigation).getVisibility() == VISIBLE) {
            Intent intent = new Intent(this, ConversationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }

        super.onBackPressed();
    }

    @Override
    protected void onBackendConnected() {
        if (selectedAccountJid != null) {
            this.selectedAccount = xmppConnectionService.findAccountByJid(selectedAccountJid);
        }
        refreshUiReal();
        if (this.mPostponedActivityResult != null) {
            this.onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
        }
        if (Config.X509_VERIFICATION && this.accountList.size() == 0) {
            if (mInvokedAddAccount.compareAndSet(false, true)) {
                addAccountFromKey();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu);
        MenuItem addAccount = menu.findItem(R.id.action_add_account);



        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mgmt_account_publish_avatar:
                publishAvatar(selectedAccount);
                return true;
            case R.id.mgmt_account_reconnect:
                disableAccount(selectedAccount);
                enableAccount(selectedAccount);
                return true;
            case R.id.mgmt_account_delete:
                deleteAccount(selectedAccount);
                return true;
            case R.id.mgmt_account_announce_pgp:
                publishOpenPGPPublicKey(selectedAccount);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_add_account:
                startActivity(new Intent(this, EditAccountActivity.class));
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                break;
            case R.id.action_import_backup:
                if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                    startActivity(new Intent(this, ImportBackupActivity.class));
                }
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                break;
            case R.id.action_create_backup:
                if (hasStoragePermission(REQUEST_CREATE_BACKUP)) {
                    createBackup();
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestMicPermission() {
        final String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            permissions = new String[]{Manifest.permission.RECORD_AUDIO};
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Dialler Integration");
                builder.setMessage("You will be asked to grant microphone permission, which is needed for the dialler integration to function.");
                builder.setPositiveButton("I Understand", (dialog, which) -> {
                    requestPermissions(permissions, REQUEST_MICROPHONE);
                });
                builder.setCancelable(true);
                final AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_MICROPHONE:
                        try {
                            startActivity(mMicIntent);
                        } catch (final android.content.ActivityNotFoundException e) {
                            Toast.makeText(this, "Your OS has blocked dialler integration", Toast.LENGTH_SHORT).show();
                        }
                        mMicIntent = null;
                        return;
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else {
                if (requestCode == REQUEST_MICROPHONE) {
                    Toast.makeText(this, "Microphone access was denied", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (readGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }

    @Override
    public boolean onNavigateUp() {
        if (xmppConnectionService.getConversations().size() == 0) {
            Intent contactsIntent = new Intent(this,
                    StartConversationActivity.class);
            contactsIntent.setFlags(
                    // if activity exists in stack, pop the stack and go back to it
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            // otherwise, make a new task for it
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            // don't use the new activity animation; finish
                            // animation runs instead
                            Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(contactsIntent);
            finish();
            return true;
        } else {
            return super.onNavigateUp();
        }
    }

    @Override
    public void onClickTglAccountState(Account account, boolean enable) {
        if (enable) {
            enableAccount(account);
        } else {
            disableAccount(account);
        }
    }

    private void addAccountFromKey() {
        try {
            KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
        } catch (ActivityNotFoundException e) {
            ToastCompat.makeText(this, R.string.device_does_not_support_certificates, ToastCompat.LENGTH_LONG).show();
        }
    }

    private void publishAvatar(Account account) {
        Intent intent = new Intent(getApplicationContext(),
                PublishProfilePictureActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        startActivity(intent);
        overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
    }

    //TODO: disable and enable all accounts function
    private void disableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isEnabled()) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            disableAccount(account);
        }
    }

    private boolean accountsLeftToDisable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isEnabled()) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean accountsLeftToEnable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isEnabled()) {
                    return true;
                }
            }
            return false;
        }
    }

    private void enableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isEnabled()) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            enableAccount(account);
        }
    }

    private void disableAccount(Account account) {
        Resolver.clearCache();
        account.setOption(Account.OPTION_DISABLED, true);
        if (!xmppConnectionService.updateAccount(account)) {
            ToastCompat.makeText(this, R.string.unable_to_update_account, ToastCompat.LENGTH_SHORT).show();
        }
    }

    private void enableAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, false);
        account.setOption(Account.OPTION_SOFT_DISABLED, false);
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.resetEverything();
        }
        if (!xmppConnectionService.updateAccount(account)) {
            ToastCompat.makeText(this, R.string.unable_to_update_account, ToastCompat.LENGTH_SHORT).show();
        }
    }

    private void publishOpenPGPPublicKey(Account account) {
        if (ManageAccountActivity.this.hasPgp()) {
            announcePgp(selectedAccount, null, null, onOpenPGPKeyPublished);
        } else {
            this.showInstallPgpDialog();
        }
    }

    private void deleteAccount(final Account account) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mgmt_account_are_you_sure));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.mgmt_account_delete_confirm_message));
        builder.setPositiveButton(getString(R.string.delete),
                (dialog, which) -> {
                    xmppConnectionService.deleteAccount(account);
                    selectedAccount = null;
                    if (xmppConnectionService.getAccounts().size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
                        WelcomeActivity.launch(this);
                    }
                });

        builder.setNegativeButton(getString(R.string.delete_from_server),
                (dialog, which) -> {
                    if (account.isOnlineAndConnected()) {
                        xmppConnectionService.deleteAccountFromServer(account);
                        selectedAccount = null;
                        if (xmppConnectionService.getAccounts().size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
                            WelcomeActivity.launch(this);
                        }
                    } else {
                        informUser(R.string.go_online_to_delete);
                    }
                });

        builder.setNeutralButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (xmppConnectionServiceBound) {
                if (requestCode == REQUEST_CHOOSE_PGP_ID) {
                    if (data.getExtras().containsKey(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                        selectedAccount.setPgpSignId(data.getExtras().getLong(OpenPgpApi.EXTRA_SIGN_KEY_ID));
                        announcePgp(selectedAccount, null, null, onOpenPGPKeyPublished);
                    } else {
                        choosePgpSignId(selectedAccount);
                    }
                } else if (requestCode == REQUEST_ANNOUNCE_PGP) {
                    announcePgp(selectedAccount, null, data, onOpenPGPKeyPublished);
                }
                this.mPostponedActivityResult = null;
            } else {
                this.mPostponedActivityResult = new Pair<>(requestCode, data);
            }
        }
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
        intent.putExtra("jid", account.getJid().asBareJid().toString());
        intent.putExtra("init", true);
        startActivity(intent);
        overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
    }

    @Override
    public void informUser(final int r) {
        runOnUiThread(() -> ToastCompat.makeText(ManageAccountActivity.this, r, ToastCompat.LENGTH_LONG).show());
    }

    private void createBackup() {
        createBackup(true, true);
    }

    private void createBackup(boolean notify, boolean withmonoclesDb) {
        Intent intent = new Intent(this, ExportBackupService.class);
        intent.putExtra("monocles_db", withmonoclesDb);
        intent.putExtra("NOTIFY_ON_BACKUP_COMPLETE", notify);
        ContextCompat.startForegroundService(this, intent);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }
}
