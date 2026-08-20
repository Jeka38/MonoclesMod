package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityServiceDiscoveryBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ServiceDiscoveryItem;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ServiceDiscoveryAdapter;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class ServiceDiscoveryActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate, ServiceDiscoveryAdapter.OnServiceDiscoveryItemClicked, ServiceDiscoveryAdapter.OnServiceDiscoveryItemLongClicked {

    private ActivityServiceDiscoveryBinding binding;
    private ServiceDiscoveryAdapter adapter;
    private EditText serverField;
    private Account account;
    private Jid currentTarget;
    private final Deque<Jid> backStack = new ArrayDeque<>();
    private List<ServiceDiscoveryItem> currentItems = new ArrayList<>();
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityServiceDiscoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar((androidx.appcompat.widget.Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar(), true);
        setupServerField();
        adapter = new ServiceDiscoveryAdapter(this, this);
        binding.list.setAdapter(adapter);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupServerField() {
        serverField = new EditText(this);
        serverField.setSingleLine(true);
        serverField.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI);
        serverField.setImeOptions(EditorInfo.IME_ACTION_GO);
        serverField.setHint(R.string.service_discovery_server_hint);
        serverField.setBackground(null);
        serverField.setPadding(dp(4), 0, dp(4), 0);
        serverField.setTextAppearance(this, R.style.TextAppearance_Conversations_Title);
        serverField.setSelectAllOnFocus(true);
        serverField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                browse(serverField.getText().toString());
                return true;
            }
            return false;
        });
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(serverField, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void browse(String server) {
        if (server == null || server.trim().isEmpty()) {
            return;
        }
        final Jid jid;
        try {
            jid = Jid.ofDomain(server.trim());
        } catch (final IllegalArgumentException e) {
            replaceToast(getString(R.string.service_discovery_invalid_server));
            return;
        }
        loadServices(jid);
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    public void onBackendConnected() {
        if (xmppConnectionService == null) {
            return;
        }
        account = AccountUtils.getFirstEnabled(xmppConnectionService);
        if (account == null) {
            replaceToast(getString(R.string.please_enable_an_account));
            finish();
            return;
        }
        if (currentTarget == null) {
            final Jid localServer = Jid.ofDomain(account.getJid().getDomain());
            if (serverField != null) {
                serverField.setText(localServer.toEscapedString());
            }
            loadServices(localServer);
        }
    }

    @Override
    public void onAccountUpdate() {
        if (xmppConnectionService == null || account == null || currentTarget == null || loading) {
            return;
        }
        final Account updated = xmppConnectionService.findAccountByJid(account.getJid());
        if (updated != null && updated.getXmppConnection() != null && adapter.getItemCount() == 0) {
            loadServices(currentTarget);
        }
    }

    private void loadServices(@NonNull final Jid target) {
        loadServices(target, null);
    }

    private void loadServices(@NonNull final Jid target, final String node) {
        if (this.account == null || xmppConnectionService == null || loading) {
            return;
        }
        if (this.account.getXmppConnection() == null) {
            showEmpty(R.string.service_discovery_not_connected);
            return;
        }
        loading = true;
        currentTarget = target;
        if (backStack.isEmpty() || !backStack.peek().equals(target)) {
            backStack.push(target);
        }
        if (serverField != null) {
            serverField.setText(target.toEscapedString());
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyText.setVisibility(View.GONE);
        binding.list.setVisibility(View.GONE);
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(target);
        final Element query = packet.query(Namespace.DISCO_ITEMS);
        if (node != null && !node.isEmpty()) {
            query.setAttribute("node", node);
        }
        this.account.getXmppConnection().sendIqPacket(packet, (account1, response) -> runOnUiThread(() -> onServicesLoaded(response)));
    }

    private void onServicesLoaded(final IqPacket response) {
        loading = false;
        binding.progressBar.setVisibility(View.GONE);
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final List<ServiceDiscoveryItem> items = new ArrayList<>();
            final Element query = response.query();
            for (Element child : query.getChildren()) {
                if (child.getName().equals("item")) {
                    final Jid jid = InvalidJid.getNullForInvalid(child.getAttributeAsJid("jid"));
                    if (jid != null) {
                        items.add(new ServiceDiscoveryItem(jid, child.getAttribute("name"), child.getAttribute("node")));
                    }
                }
            }
            items.sort(Comparator.comparing(ServiceDiscoveryItem::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            currentItems = items;
            adapter.submitList(items);
            if (items.isEmpty()) {
                showEmpty(R.string.service_discovery_empty);
            } else {
                binding.emptyText.setVisibility(View.GONE);
                binding.list.setVisibility(View.VISIBLE);
            }
            for (final ServiceDiscoveryItem item : items) {
                loadInfo(item);
            }
        } else {
            showEmpty(R.string.service_discovery_failed);
        }
    }

    private void loadInfo(final ServiceDiscoveryItem item) {
        final Jid jid = item.getJid();
        if (jid == null || xmppConnectionService == null) {
            return;
        }
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(jid);
        final Element query = packet.query(Namespace.DISCO_INFO);
        if (item.getNode() != null && !item.getNode().isEmpty()) {
            query.setAttribute("node", item.getNode());
        }
        xmppConnectionService.sendIqPacket(account, packet, (a, response) -> runOnUiThread(() -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                final Element identity = response.query(Namespace.DISCO_INFO).findChild("identity");
                if (identity != null && identity.getAttribute("name") != null) {
                    item.setIdentityName(identity.getAttribute("name"));
                    final int index = currentItems.indexOf(item);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index);
                    }
                }
            }
        }));
    }

    private void showEmpty(final int stringRes) {
        adapter.submitList(null);
        binding.emptyText.setVisibility(View.VISIBLE);
        binding.emptyText.setText(stringRes);
        binding.list.setVisibility(View.GONE);
    }

    @Override
    public void onServiceDiscoveryItemClicked(ServiceDiscoveryItem item) {
        if (item.getJid() != null) {
            loadServices(item.getJid(), item.getNode());
        }
    }

    @Override
    public void onServiceDiscoveryItemLongClicked(ServiceDiscoveryItem item) {
        if (item.getJid() != null && account != null) {
            ServiceManagementDialog.show(this, account, item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (backStack.size() > 1) {
            backStack.pop();
            loadServices(backStack.peek());
        } else {
            super.onBackPressed();
        }
    }
}