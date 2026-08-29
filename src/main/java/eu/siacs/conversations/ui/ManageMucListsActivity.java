package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityManageMucListsBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;

public class ManageMucListsActivity extends XmppActivity implements XmppConnectionService.OnMucRosterUpdate {

    private ActivityManageMucListsBinding binding;
    private String uuid;
    private Conversation mConversation;

    @Override
    protected void refreshUiReal() {
        updateListCounts();
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        final String uuid = intent == null ? null : intent.getStringExtra(MucUsersActivity.EXTRA_UUID);
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (mConversation != null) {
                xmppConnectionService.fetchConferenceMembers(mConversation);
            }
        }
        updateListCounts();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_manage_muc_lists);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar(), true);
        setTitle(R.string.manage_lists_users);

        final Intent intent = getIntent();
        uuid = intent == null ? null : intent.getStringExtra(MucUsersActivity.EXTRA_UUID);

        binding.listOwners.setOnClickListener(v -> openTab("OWNERS"));
        binding.listAdmins.setOnClickListener(v -> openTab("ADMINS"));
        binding.listMembers.setOnClickListener(v -> openTab("MEMBERS"));
        binding.listOutcasts.setOnClickListener(v -> openTab("OUTCASTS"));
    }

    private void openTab(final String tab) {
        if (uuid == null) {
            return;
        }
        final Intent intent = new Intent(this, MucUsersActivity.class);
        intent.putExtra(MucUsersActivity.EXTRA_UUID, uuid);
        intent.putExtra(MucUsersActivity.EXTRA_MANAGE_MODE, true);
        intent.putExtra(MucUsersActivity.EXTRA_INITIAL_TAB, tab);
        startActivity(intent);
    }

    private void updateListCounts() {
        if (mConversation == null || binding == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        binding.ownersSubtitle.setText(countString(mucOptions, MucOptions.Affiliation.OWNER));
        binding.adminsSubtitle.setText(countString(mucOptions, MucOptions.Affiliation.ADMIN));
        binding.membersSubtitle.setText(countString(mucOptions, MucOptions.Affiliation.MEMBER));
        binding.outcastsSubtitle.setText(countString(mucOptions, MucOptions.Affiliation.OUTCAST));
    }

    private String countString(final MucOptions mucOptions, final MucOptions.Affiliation affiliation) {
        int count = 0;
        for (final MucOptions.User user : mucOptions.getUsers(true, true)) {
            if (user.getAffiliation() == affiliation) {
                ++count;
            }
        }
        return getResources().getQuantityString(R.plurals.participants, count, count);
    }

    @Override
    public void onMucRosterUpdate() {
        runOnUiThread(this::updateListCounts);
    }
}