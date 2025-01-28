package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucContactDetailsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.xmpp.Jid;

public class ConferenceContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    private Conversation mConversation;
    ActivityMucContactDetailsBinding binding;
    private Jid accountJid;
    private Jid contactJid;
    private MucOptions.User user = null;

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.ofEscaped(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.ofEscaped(getIntent().getExtras().getString("user"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_contact_details);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    private void populateView() {
        if (getSupportActionBar() != null) {
            final ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setCustomView(R.layout.ab_title);
                ab.setDisplayShowCustomEnabled(true);
                TextView abtitle = findViewById(android.R.id.text1);
                TextView absubtitle = findViewById(android.R.id.text2);
                abtitle.setText(R.string.contact_details);
                abtitle.setSelected(true);
                abtitle.setClickable(false);
                absubtitle.setVisibility(View.GONE);
                absubtitle.setClickable(false);
            }
        }
        if (user == null) {
            return;
        }
        binding.contactDisplayName.setText(user.getName());
        binding.jid.setText(IrregularUnicodeDetector.style(this, contactJid));
        String account = accountJid.asBareJid().toEscapedString();
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        AvatarWorkerTask.loadAvatar(user, binding.detailsContactBadge, R.dimen.avatar_on_details_screen_size);
        binding.detailsContactBadge.setOnLongClickListener(v -> {
            ShowAvatarPopup(ConferenceContactDetailsActivity.this, user);
            return true;
        });
        if (xmppConnectionService.multipleAccounts()) {
            binding.detailsAccount.setVisibility(View.VISIBLE);
        } else {
            binding.detailsAccount.setVisibility(View.GONE);
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.mConversation = xmppConnectionService.findConversation(account, contactJid, false);
            final MucOptions mucOptions = ((Conversation) this.mConversation).getMucOptions();
            this.user = mucOptions.findUserByFullJid(contactJid);
            populateView();
        }
    }
}
