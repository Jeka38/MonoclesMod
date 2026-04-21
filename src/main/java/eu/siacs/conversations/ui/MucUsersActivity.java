package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucUsersBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.UserAdapter;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.xmpp.Jid;
import me.drakeet.support.toast.ToastCompat;

public class MucUsersActivity extends XmppActivity implements XmppConnectionService.OnMucRosterUpdate, XmppConnectionService.OnAffiliationChanged, MenuItem.OnActionExpandListener, TextWatcher {

    private ActivityMucUsersBinding binding;
    private UserAdapter userAdapter;

    private Conversation mConversation = null;

    private EditText mSearchEditText;

    private ArrayList<MucOptions.User> allUsers = new ArrayList<>();

    private enum Tab {
        OCCUPANTS(R.string.participants),
        OWNERS(R.string.owner),
        ADMINS(R.string.admin),
        MODERATORS(R.string.moderator),
        MEMBERS(R.string.member),
        OUTCASTS(R.string.outcast);

        final int resId;

        Tab(int resId) {
            this.resId = resId;
        }
    }

    private Tab mSelectedTab = Tab.OCCUPANTS;

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (mConversation != null) {
                xmppConnectionService.fetchConferenceMembers(mConversation);
                updateFabVisibility();
            }
        }
        loadAndSubmitUsers();
    }


    private void loadAndSubmitUsers() {
        if (mConversation != null) {
            allUsers = mConversation.getMucOptions().getUsers(true, true);
            submitFilteredList(mSearchEditText != null ? mSearchEditText.getText().toString() : null);
        }
    }

    private void submitFilteredList(final String search) {
        List<MucOptions.User> filteredUsers = new ArrayList<>(allUsers);

        switch (mSelectedTab) {
            case OCCUPANTS:
                filteredUsers.removeIf(user -> !user.isOnline());
                break;
            case OWNERS:
                filteredUsers.removeIf(user -> user.getAffiliation() != MucOptions.Affiliation.OWNER);
                break;
            case ADMINS:
                filteredUsers.removeIf(user -> user.getAffiliation() != MucOptions.Affiliation.ADMIN);
                break;
            case MODERATORS:
                filteredUsers.removeIf(user -> user.getRole() != MucOptions.Role.MODERATOR);
                break;
            case MEMBERS:
                filteredUsers.removeIf(user -> user.getAffiliation() != MucOptions.Affiliation.MEMBER);
                break;
            case OUTCASTS:
                filteredUsers.removeIf(user -> user.getAffiliation() != MucOptions.Affiliation.OUTCAST);
                break;
        }

        if (TextUtils.isEmpty(search)) {
            userAdapter.submitList(Ordering.natural().immutableSortedCopy(filteredUsers));
        } else {
            final String needle = search.toLowerCase(Locale.getDefault());
            userAdapter.submitList(
                    Ordering.natural()
                            .immutableSortedCopy(
                                    Collections2.filter(
                                            filteredUsers,
                                            user -> {
                                                final String name = user.getName();
                                                final Contact contact = user.getContact();
                                                return (name != null && name.toLowerCase(Locale.getDefault()).contains(needle))
                                                        || (contact != null && contact.getDisplayName().toLowerCase(Locale.getDefault()).contains(needle));
                                            })));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }



    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (!MucDetailsContextMenuHelper.onContextItemSelected(item, userAdapter.getSelectedUser(), this, null, mSelectedTab != Tab.OCCUPANTS)) {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_users);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar(), true);
        this.userAdapter = new UserAdapter(getPreferences().getBoolean("advanced_muc_mode", false));
        binding.list.setAdapter(this.userAdapter);

        for (Tab tab : Tab.values()) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tab.resId));
        }

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mSelectedTab = Tab.values()[tab.getPosition()];
                userAdapter.setAffiliationList(mSelectedTab != Tab.OCCUPANTS);
                updateFabVisibility();
                loadAndSubmitUsers();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.fab.setOnClickListener(v -> showAddJidDialog());
    }

    private void updateFabVisibility() {
        if (mConversation == null) {
            binding.fab.hide();
            return;
        }
        final MucOptions.Affiliation affiliation = mConversation.getMucOptions().getSelf().getAffiliation();
        boolean canManage = false;
        switch (mSelectedTab) {
            case OWNERS:
            case ADMINS:
                canManage = affiliation == MucOptions.Affiliation.OWNER;
                break;
            case MODERATORS:
                canManage = false;
                break;
            case MEMBERS:
            case OUTCASTS:
                canManage = affiliation.ranks(MucOptions.Affiliation.ADMIN);
                break;
            default:
                canManage = false;
                break;
        }
        if (canManage) {
            binding.fab.show();
        } else {
            binding.fab.hide();
        }
    }

    private void showAddJidDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.account_settings_jabber_id);
        new AlertDialog.Builder(this)
                .setTitle(R.string.add)
                .setView(input)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    final String jidString = input.getText().toString().trim();
                    try {
                        final Jid jid = Jid.of(jidString);
                        MucOptions.Affiliation affiliation = MucOptions.Affiliation.NONE;
                        switch (mSelectedTab) {
                            case OWNERS: affiliation = MucOptions.Affiliation.OWNER; break;
                            case ADMINS: affiliation = MucOptions.Affiliation.ADMIN; break;
                            case MEMBERS: affiliation = MucOptions.Affiliation.MEMBER; break;
                            case OUTCASTS: affiliation = MucOptions.Affiliation.OUTCAST; break;
                        }
                        if (affiliation != MucOptions.Affiliation.NONE) {
                            xmppConnectionService.changeAffiliationInConference(mConversation, jid, affiliation, this);
                        } else if (mSelectedTab == Tab.MODERATORS) {
                            xmppConnectionService.changeRoleInConference(mConversation, jid.toString(), MucOptions.Role.MODERATOR);
                        }
                    } catch (IllegalArgumentException e) {
                        displayToast(getString(R.string.invalid_jid));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Override
    public void onMucRosterUpdate() {
        loadAndSubmitUsers();
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> ToastCompat.makeText(this, msg, ToastCompat.LENGTH_SHORT).show());
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        loadAndSubmitUsers();
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toString()));
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.muc_users_activity, menu);

        final MenuItem menuSearchView = menu.findItem(R.id.action_search);
        final View mSearchView = menuSearchView.getActionView();
        mSearchEditText = mSearchView.findViewById(R.id.search_field);
        mSearchEditText.addTextChangedListener(this);
        mSearchEditText.setHint(R.string.search_participants);
        menuSearchView.setOnActionExpandListener(this);

        return true;
    }



    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        mSearchEditText.post(() -> {
            mSearchEditText.requestFocus();
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
        });
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        mSearchEditText.setText("");
        submitFilteredList("");
        return true;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        submitFilteredList(s.toString());
    }
}