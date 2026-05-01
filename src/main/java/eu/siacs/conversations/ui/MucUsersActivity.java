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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

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
    private final UserAdapter[] adapters = new UserAdapter[Tab.values().length];

    private Conversation mConversation = null;

    private EditText mSearchEditText;

    private ArrayList<MucOptions.User> allUsers = new ArrayList<>();

    public enum Tab {
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
        updateTabsVisibility();
        updateFabVisibility();
        loadAndSubmitUsers();
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (mConversation != null) {
                xmppConnectionService.fetchConferenceMembers(mConversation);
                updateTabsVisibility();
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
        for (Tab tab : Tab.values()) {
            submitFilteredList(tab, search);
        }
    }

    private void submitFilteredList(Tab tab, final String search) {
        UserAdapter userAdapter = adapters[tab.ordinal()];
        if (userAdapter == null) {
            return;
        }

        List<MucOptions.User> filteredUsers = new ArrayList<>(allUsers);

        switch (tab) {
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
        if (item.getItemId() == R.id.action_add) {
            showAddJidDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        UserAdapter adapter = adapters[mSelectedTab.ordinal()];
        if (adapter == null) {
            return super.onContextItemSelected(item);
        }
        if (!MucDetailsContextMenuHelper.onContextItemSelected(item, adapter.getSelectedUser(), this, null, mSelectedTab != Tab.OCCUPANTS)) {
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

        binding.viewPager.setAdapter(new MucUsersPagerAdapter());
        binding.viewPager.setOffscreenPageLimit(Tab.values().length);
        binding.tabLayout.setupWithViewPager(binding.viewPager);

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(binding.viewPager) {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                super.onTabSelected(tab);
                mSelectedTab = Tab.values()[tab.getPosition()];
                updateFabVisibility();
                invalidateOptionsMenu();
            }
        });

        binding.fab.setOnClickListener(v -> showAddJidDialog());
    }

    private class MucUsersPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            if (mConversation == null) {
                return 0;
            }
            final MucOptions.Affiliation affiliation = mConversation.getMucOptions().getSelf().getAffiliation();
            if (affiliation.ranks(MucOptions.Affiliation.ADMIN)) {
                return Tab.values().length;
            } else {
                return 1;
            }
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            RecyclerView recyclerView = new RecyclerView(container.getContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(container.getContext()));
            Tab tab = Tab.values()[position];
            UserAdapter adapter = new UserAdapter(getPreferences().getBoolean("advanced_muc_mode", false));
            adapter.setAffiliationList(tab != Tab.OCCUPANTS);
            adapters[position] = adapter;
            recyclerView.setAdapter(adapter);
            container.addView(recyclerView);
            submitFilteredList(tab, mSearchEditText != null ? mSearchEditText.getText().toString() : null);
            return recyclerView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
            adapters[position] = null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(Tab.values()[position].resId);
        }
    }

    private void updateTabsVisibility() {
        if (mConversation == null) {
            binding.tabLayout.setVisibility(View.GONE);
            return;
        }
        if (binding.viewPager.getAdapter() != null) {
            binding.viewPager.getAdapter().notifyDataSetChanged();
        }
        final MucOptions.Affiliation affiliation = mConversation.getMucOptions().getSelf().getAffiliation();
        if (affiliation.ranks(MucOptions.Affiliation.ADMIN)) {
            binding.tabLayout.setVisibility(View.VISIBLE);
        } else {
            binding.tabLayout.setVisibility(View.GONE);
            mSelectedTab = Tab.OCCUPANTS;
            binding.viewPager.setCurrentItem(Tab.OCCUPANTS.ordinal());
        }
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
        updateTabsVisibility();
        updateFabVisibility();
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

        final MenuItem addAction = menu.findItem(R.id.action_add);
        if (mConversation != null) {
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
            addAction.setVisible(canManage);
        } else {
            addAction.setVisible(false);
        }

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