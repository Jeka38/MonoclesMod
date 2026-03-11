package eu.siacs.conversations.ui;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPrivateMucChatBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.Jid;

public class PrivateMucChatActivity extends XmppActivity {

    private ActivityPrivateMucChatBinding binding;
    private String conversationUuid;
    private String counterpartJid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_private_muc_chat);
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
        Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
        if (conversation == null) {
            finish();
            return;
        }

        try {
            Jid counterpart = Jid.of(counterpartJid);
            setTitle(counterpart.getResource());
        } catch (IllegalArgumentException e) {
            setTitle(conversation.getName());
        }

        PrivateMucConversationFragment fragment = new PrivateMucConversationFragment();
        Bundle args = new Bundle();
        args.putString("counterpart", counterpartJid);
        fragment.setArguments(args);
        fragment.reInit(conversation);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    @Override
    protected void refreshUiReal() {
        // Refresh handled by fragment
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
        outState.putString("uuid", conversationUuid);
        outState.putString("counterpart", counterpartJid);
    }
}
