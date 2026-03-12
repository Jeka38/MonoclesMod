package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucPrivateChatBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;

public class MucPrivateChatActivity extends XmppActivity {

    private ActivityMucPrivateChatBinding binding;
    private String mConversationUuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_private_chat);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        if (savedInstanceState == null) {
            mConversationUuid = getIntent().getStringExtra(EXTRA_CONVERSATION);
        } else {
            mConversationUuid = savedInstanceState.getString(EXTRA_CONVERSATION);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CONVERSATION, mConversationUuid);
    }

    @Override
    protected void onBackendConnected() {
        Conversation conversation = xmppConnectionService.findConversationByUuid(mConversationUuid);
        if (conversation == null) {
            finish();
            return;
        }
        updateActionBar(conversation);
        ConversationFragment fragment = ConversationFragment.get(this);
        if (fragment == null) {
            fragment = new ConversationFragment();
            Bundle args = new Bundle();
            args.putString(EXTRA_CONVERSATION, mConversationUuid);
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        } else {
            fragment.reInit(conversation);
        }
    }

    private void updateActionBar(Conversation conversation) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.ab_title);
        TextView title = actionBar.getCustomView().findViewById(android.R.id.text1);
        title.setText(conversation.getName());
        ImageView avatar = actionBar.getCustomView().findViewById(R.id.toolbar_avatar);
        AvatarWorkerTask.loadAvatar(conversation, avatar, R.dimen.avatar_actionbar);
    }

    @Override
    protected void refreshUiReal() {
        Conversation conversation = xmppConnectionService.findConversationByUuid(mConversationUuid);
        if (conversation != null) {
            updateActionBar(conversation);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_muc_private_chat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
