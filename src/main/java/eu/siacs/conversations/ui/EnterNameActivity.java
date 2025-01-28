package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNameBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.FirstStartManager;

public class EnterNameActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate {

    private ActivityEnterNameBinding binding;
    private Account account;
    private boolean mExisting = false;
    private AtomicBoolean setNick = new AtomicBoolean(false);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_name);
        setSupportActionBar((Toolbar) this.binding.toolbar.getRoot());
        this.binding.next.setOnClickListener(this::next);
        this.binding.skip.setOnClickListener(this::skip);
        updateNextButton();
        this.setNick.set(savedInstanceState != null && savedInstanceState.getBoolean("set_nick", false));
    }

    private void updateNextButton() {
        if (account != null && (account.getStatus() == Account.State.CONNECTING || account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL)) {
            this.binding.next.setEnabled(false);
            this.binding.next.setText(R.string.account_status_connecting);
        } else if (account != null && (account.getStatus() == Account.State.ONLINE)) {
            this.binding.next.setEnabled(true);
            this.binding.next.setText(R.string.next);
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        final Intent intent = getIntent();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else if (intent != null) {
            boolean existing = intent.getBooleanExtra("existing", false);
            this.mExisting = existing;
        }
    }

    private void next(View view) {
        FirstStartManager firstStartManager = new FirstStartManager(this);
        if (account != null) {
            String name = this.binding.name.getText().toString().trim();
            account.setDisplayName(name);
            xmppConnectionService.publishDisplayName(account);
            if (firstStartManager.isFirstTimeLaunch()) {
                Intent intent = new Intent(this, SetSettingsActivity.class);
                intent.putExtra("setup", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            } else {
                Intent intent = new Intent(this, PublishProfilePictureActivity.class);
                intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
                intent.putExtra("setup", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
        }
        finish();
    }
    private void skip(View view) {
        if (account != null) {
            String name = this.binding.name.getText().toString().trim();
            account.setDisplayName(name);
            xmppConnectionService.publishDisplayName(account);
            final Intent intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1) {
                intent.putExtra("init", true);
            }
            StartConversationActivity.addInviteUri(intent, getIntent());
            intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            finish();
        }
        finish();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("set_nick", this.setNick.get());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void refreshUiReal() {
        checkSuggestPreviousNick();
        updateNextButton();
    }

    @Override
    protected void onBackendConnected() {
        this.account = extractAccount(getIntent());
        if (this.account != null) {
            checkSuggestPreviousNick();
        }
        updateNextButton();
    }

    private void checkSuggestPreviousNick() {
        String displayName = this.account == null ? null : this.account.getDisplayName();
        if (displayName != null) {
            if (setNick.compareAndSet(false, true) && this.binding.name.getText().length() == 0) {
                this.binding.name.getText().append(displayName);
            }
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}