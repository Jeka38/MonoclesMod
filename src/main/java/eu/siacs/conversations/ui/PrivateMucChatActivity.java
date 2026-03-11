package eu.siacs.conversations.ui;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPrivateMucChatBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;

public class PrivateMucChatActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate {

    private String conversationUuid;
    private String counterpartJid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(Config.LOGTAG, "PrivateMucChatActivity.onCreate()");
        ActivityPrivateMucChatBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_private_muc_chat);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            conversationUuid = savedInstanceState.getString("uuid");
            counterpartJid = savedInstanceState.getString("counterpart");
        } else {
            conversationUuid = getIntent().getStringExtra("uuid");
            counterpartJid = getIntent().getStringExtra("counterpart");
        }
    }

    @Override
    protected void onBackendConnected() {
        Log.d(Config.LOGTAG, "PrivateMucChatActivity.onBackendConnected()");
        Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
        if (conversation == null) {
            Log.w(Config.LOGTAG, "Conversation not found: " + conversationUuid);
            finish();
            return;
        }

        try {
            Jid counterpart = Jid.of(counterpartJid);
            setTitle(counterpart.getResource());
        } catch (IllegalArgumentException e) {
            setTitle(conversation.getName());
        }

        PrivateMucConversationFragment fragment = (PrivateMucConversationFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            Log.d(Config.LOGTAG, "Creating new PrivateMucConversationFragment for " + counterpartJid);
            fragment = new PrivateMucConversationFragment();
            Bundle args = new Bundle();
            args.putString("counterpart", counterpartJid);
            fragment.setArguments(args);

            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            getFragmentManager().executePendingTransactions();
        }
        if (fragment != null) {
            fragment.reInit(conversation);
        }
    }

    @Override
    protected void refreshUiReal() {
        XmppFragment fragment = (XmppFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            fragment.refresh();
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        if (conversation != null && conversation.getUuid().equals(conversationUuid)) {
            Log.d(Config.LOGTAG, "PrivateMucChatActivity.onConversationArchived()");
            finish();
        }
    }

    @Override
    public void onConversationUpdate(boolean newCaps) {
        Log.d(Config.LOGTAG, "PrivateMucChatActivity.onConversationUpdate(newCaps=" + newCaps + ")");
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        Log.d(Config.LOGTAG, "PrivateMucChatActivity.onAccountUpdate()");
        refreshUi();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(Config.LOGTAG, "PrivateMucChatActivity.onSaveInstanceState()");
        outState.putString("uuid", conversationUuid);
        outState.putString("counterpart", counterpartJid);
    }
}
