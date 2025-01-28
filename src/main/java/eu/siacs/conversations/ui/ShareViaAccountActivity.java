package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;

public class ShareViaAccountActivity extends XmppActivity {
    public static final String EXTRA_CONTACT = "contact";
    public static final String EXTRA_BODY = "body";

    protected final List<Account> accountList = new ArrayList<>();
    protected ListView accountListView;
    protected AccountAdapter mAccountAdapter;

    @Override
    protected void refreshUiReal() {
        synchronized (this.accountList) {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
        }
        mAccountAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_manage_accounts);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        accountListView = findViewById(R.id.account_list);
        this.mAccountAdapter = new AccountAdapter(this, accountList, false);
        accountListView.setAdapter(this.mAccountAdapter);
        accountListView.setOnItemClickListener((arg0, view, position, arg3) -> {
            final Account account = accountList.get(position);
            final String action = getAction();
            if (action != null && action.equals("command")) {
                startCommand(account, getJid(), new XmppUri(getIntent().getData()).getParameter("node"));
            } else {
                switchToConversation(getConversation(account), getBody(), false, null, false, false, action);
            }

            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    @Override
    protected void onBackendConnected() {
        final int numAccounts = xmppConnectionService.getAccounts().size();

        if (numAccounts == 1) {
            final Account account = xmppConnectionService.getAccounts().get(0);

            final String action = getAction();
            if (action != null && action.equals("command")) {
                startCommand(account, getJid(), new XmppUri(getIntent().getData()).getParameter("node"));
            } else {
                switchToConversation(getConversation(account), getBody(), false, null, false, false, action);
            }

            finish();
        } else {
            refreshUiReal();
        }
    }

    protected Conversation getConversation(Account account) {
        try {
            return xmppConnectionService.findOrCreateConversation(account, getJid(), false, false);
        } catch (IllegalArgumentException e) { }
        return null;
    }

    protected String getAction() {
        if (getIntent().getData() == null) return null;
        XmppUri xmppUri = new XmppUri(getIntent().getData());
        if (xmppUri.isAction("message")) return "message";
        if (xmppUri.isAction("command")) return "command";
        return null;
    }

    protected Jid getJid() {
        if (getIntent().getData() == null) {
            return Jid.of(getIntent().getStringExtra(EXTRA_CONTACT));
        } else {
            return new XmppUri(getIntent().getData()).getJid();
        }
    }

    protected String getBody() {
        if (getIntent().getData() == null) {
            return getIntent().getStringExtra(EXTRA_BODY);
        } else {
            return new XmppUri(getIntent().getData()).getBody();
        }
    }
}