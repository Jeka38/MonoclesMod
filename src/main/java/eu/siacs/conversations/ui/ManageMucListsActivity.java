package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityManageMucListsBinding;

public class ManageMucListsActivity extends XmppActivity {

    private ActivityManageMucListsBinding binding;
    private String uuid;

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_manage_muc_lists);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar(), true);

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
}
