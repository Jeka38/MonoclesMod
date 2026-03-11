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
    private boolean mInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
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
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "PrivateMucChatActivity.onCreate() failed", e);
            finish();
        }
    }

    @Override
    protected void onBackendConnected() {
        try {
            Log.d(Config.LOGTAG, "PrivateMucChatActivity.onBackendConnected() uuid=" + conversationUuid + " counterpart=" + counterpartJid);
            initialize();
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "PrivateMucChatActivity.onBackendConnected() failed", e);
        }
    }

    @Override
    public void onConversationUpdate(boolean newCaps) {
        try {
            Log.d(Config.LOGTAG, "PrivateMucChatActivity.onConversationUpdate(newCaps=" + newCaps + ")");
            if (!mInitialized) {
                initialize();
            }
            refreshUi();
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "PrivateMucChatActivity.onConversationUpdate() failed", e);
        }
    }

    private synchronized void initialize() {
        try {
            if (mInitialized || xmppConnectionService == null) {
                return;
            }

            if (conversationUuid == null) {
                Log.w(Config.LOGTAG, "PrivateMucChatActivity: conversationUuid is null, finishing");
                finish();
                return;
            }

            Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
            if (conversation == null) {
                if (xmppConnectionService.areMessagesInitialized()) {
                    Log.w(Config.LOGTAG, "PrivateMucChatActivity: Conversation not found: " + conversationUuid + ", finishing");
                    finish();
                } else {
                    Log.d(Config.LOGTAG, "PrivateMucChatActivity: Waiting for conversations to be restored...");
                }
                return;
            }

            Log.d(Config.LOGTAG, "PrivateMucChatActivity: Conversation found, initializing fragment");
            mInitialized = true;

            try {
                if (counterpartJid != null) {
                    Jid counterpart = Jid.of(counterpartJid);
                    setTitle(counterpart.getResource());
                } else {
                    setTitle(conversation.getName());
                }
            } catch (Throwable e) {
                Log.e(Config.LOGTAG, "PrivateMucChatActivity: Failed to parse counterpart JID or set title: " + counterpartJid, e);
                setTitle(conversation.getName());
            }

            PrivateMucConversationFragment fragment = (PrivateMucConversationFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment == null) {
                Log.d(Config.LOGTAG, "PrivateMucChatActivity: Creating new PrivateMucConversationFragment for " + counterpartJid);
                fragment = new PrivateMucConversationFragment();
                Bundle args = new Bundle();
                args.putString("counterpart", counterpartJid);
                fragment.setArguments(args);

                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                getFragmentManager().executePendingTransactions();
                // Re-fetch to be sure we have the one currently in the manager
                fragment = (PrivateMucConversationFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
            }

            if (fragment != null) {
                Log.d(Config.LOGTAG, "PrivateMucChatActivity: Calling fragment.reInit() isAdded=" + fragment.isAdded());
                fragment.reInit(conversation);
            } else {
                Log.e(Config.LOGTAG, "PrivateMucChatActivity: Fragment is still null after attempt to create it");
            }
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "PrivateMucChatActivity: Error during initialization", e);
        }
    }

    @Override
    protected void refreshUiReal() {
        try {
            XmppFragment fragment = (XmppFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment != null) {
                fragment.refresh();
            }
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "PrivateMucChatActivity.refreshUiReal() failed", e);
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
