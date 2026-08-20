package eu.siacs.conversations.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ServiceManagementDialogBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ServiceDiscoveryItem;
import eu.siacs.conversations.ui.forms.FormWrapper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import me.drakeet.support.toast.ToastCompat;

public class ServiceManagementDialog {

    private static final String ACTIONS_FIELD = "http://jabber.org/protocol/commands#actions";

    private final XmppActivity activity;
    private final Account account;
    private final ServiceDiscoveryItem item;
    private final Jid serviceJid;

    private ServiceManagementDialogBinding binding;
    private AlertDialog dialog;

    private FormWrapper formWrapper;
    private Data commandForm;

    private String commandNode;
    private String commandSessionId;

    private boolean searchLegacy = false;
    private Data searchFormData;
    private Jid selectedSearchJid;
    private Jid firstSearchResultJid;

    private enum State { ACTIONS, SEARCH_FORM, SEARCH_RESULTS, COMMAND_LIST, COMMAND_FORM, RESULT, MESSAGE, PROGRESS }

    private State state = State.ACTIONS;

    private final Set<String> features = new HashSet<>();
    private final List<String> identities = new ArrayList<>();
    private boolean featuresKnown = false;

    private Runnable negativeAction;
    private Runnable neutralAction;
    private Runnable positiveAction;

    public static void show(XmppActivity activity, Account account, ServiceDiscoveryItem item) {
        new ServiceManagementDialog(activity, account, item).show();
    }

    private ServiceManagementDialog(XmppActivity activity, Account account, ServiceDiscoveryItem item) {
        this.activity = activity;
        this.account = account;
        this.item = item;
        this.serviceJid = item.getJid();
    }

    public void show() {
        binding = ServiceManagementDialogBinding.inflate(LayoutInflater.from(activity));
        binding.btnNegative.setOnClickListener(v -> run(negativeAction));
        binding.btnNeutral.setOnClickListener(v -> run(neutralAction));
        binding.btnPositive.setOnClickListener(v -> run(positiveAction));
        dialog = new AlertDialog.Builder(activity)
                .setView(binding.getRoot())
                .create();
        dialog.setCanceledOnTouchOutside(true);
        final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ServiceManagementDialog.this.onBackPressed();
            }
        };
        dialog.getOnBackPressedDispatcher().addCallback(backCallback);
        dialog.setOnDismissListener(d -> backCallback.remove());
        dialog.show();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadFeatures();
    }

    private void loadFeatures() {
        if (!isConnected()) {
            return;
        }
        commandNode = null;
        commandSessionId = null;
        clearContent();
        binding.title.setText(item.getDisplayName());
        showProgress(getString(R.string.service_loading_features));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(serviceJid);
        final Element query = packet.query(Namespace.DISCO_INFO);
        if (item.getNode() != null && !item.getNode().isEmpty()) {
            query.setAttribute("node", item.getNode());
        }
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onFeaturesLoaded(response)));
    }

    private void onFeaturesLoaded(IqPacket response) {
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final Element query = response.query(Namespace.DISCO_INFO);
            for (Element child : query.getChildren()) {
                if ("feature".equals(child.getName())) {
                    final String var = child.getAttribute("var");
                    if (var != null) {
                        features.add(var);
                    }
                } else if ("identity".equals(child.getName())) {
                    final String category = child.getAttribute("category");
                    final String type = child.getAttribute("type");
                    if (category != null) {
                        identities.add(category + "/" + (type != null ? type : ""));
                    }
                }
            }
        }
        featuresKnown = true;
        showActions();
    }

    private void showActions() {
        state = State.ACTIONS;
        commandNode = null;
        commandSessionId = null;
        clearContent();
        binding.title.setText(item.getDisplayName());
        if (!featuresKnown || features.contains(Namespace.REGISTER)) {
            addAction(getString(R.string.register), this::register);
        }
        if (!featuresKnown || features.contains(Namespace.SEARCH)) {
            addAction(getString(R.string.service_search), this::search);
        }
        if (!featuresKnown || features.contains(Namespace.COMMANDS)) {
            addAction(getString(R.string.commands), this::commands);
        }
        if (!featuresKnown || hasIdentityCategory("client") || hasIdentityCategory("account")) {
            addAction(getString(R.string.open_chat), this::openChat);
        }
        if (!featuresKnown || features.contains(Namespace.MUC) || identities.contains("conference/text") || identities.contains("conference/irc")) {
            addAction(getString(R.string.join_room), this::joinRoom);
        }
        addAction(getString(R.string.copy_jid), this::copyJid);
        hideButtons();
    }

    private boolean hasIdentityCategory(String category) {
        for (String identity : identities) {
            if (identity.startsWith(category + "/")) {
                return true;
            }
        }
        return false;
    }

    private void register() {
        if (!isConnected()) {
            return;
        }
        showProgress(getString(R.string.service_loading_registration_form));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(serviceJid);
        packet.query(Namespace.REGISTER);
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onRegisterForm(response)));
    }

    private void onRegisterForm(IqPacket response) {
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final Element query = response.query(Namespace.REGISTER);
            if (query.findChild("registered") != null) {
                showMessage(R.string.service_already_registered);
                return;
            }
            Data form = Data.parse(query.findChild("x", Namespace.DATA));
            if (form == null) {
                form = synthesizeRegistrationForm(query);
            }
            if (form.getFields().isEmpty()) {
                showMessage(R.string.service_no_registration_fields);
                return;
            }
            showRegistrationForm(form);
        } else {
            showIqError(response, R.string.service_registration_failed);
        }
    }

    private Data synthesizeRegistrationForm(Element query) {
        final Data data = new Data();
        data.setAttribute("type", "form");
        for (Element child : query.getChildren()) {
            final String name = child.getName();
            if (name.equals("instructions") || name.equals("x") || name.equals("registered")) {
                continue;
            }
            final Field field = new Field(name);
            field.setAttribute("label", titleCase(name));
            field.setAttribute("type", name.equals("password") ? "text-private" : "text-single");
            final String value = child.getContent();
            if (value != null && !value.isEmpty()) {
                field.setValue(value);
            }
            data.addChild(field);
        }
        return data;
    }

    private void showRegistrationForm(Data form) {
        clearContent();
        binding.title.setText(getString(R.string.register_on_service));
        if (form.getTitle() != null) {
            addText(form.getTitle());
        }
        formWrapper = FormWrapper.createInLayout(activity, binding.content, form);
        setButtons(
                getString(R.string.cancel), this::showActions,
                null, null,
                getString(R.string.submit), this::submitRegistration);
    }

    private void submitRegistration() {
        if (formWrapper == null) {
            return;
        }
        if (!formWrapper.validates()) {
            return;
        }
        final Data form = formWrapper.submit();
        showProgress(getString(R.string.service_submitting_registration));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(serviceJid);
        packet.query(Namespace.REGISTER).addChild(form);
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                showMessage(R.string.service_registration_success);
            } else {
                showIqError(response, R.string.service_registration_failed);
            }
        }));
    }

    private void search() {
        if (!isConnected()) {
            return;
        }
        showProgress(getString(R.string.service_loading_search_form));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(serviceJid);
        packet.query(Namespace.SEARCH);
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onSearchForm(response)));
    }

    private void onSearchForm(IqPacket response) {
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final Element query = response.query(Namespace.SEARCH);
            final Element dataForm = query.findChild("x", Namespace.DATA);
            searchLegacy = dataForm == null;
            Data form = Data.parse(dataForm);
            if (form == null) {
                form = synthesizeRegistrationForm(query);
            }
            if (form.getFields().isEmpty()) {
                showMessage(R.string.service_no_search_fields);
                return;
            }
            searchFormData = form;
            showSearchForm();
        } else {
            showIqError(response, R.string.service_search_failed);
        }
    }

    private void showSearchForm() {
        if (searchFormData == null) {
            showActions();
            return;
        }
        state = State.SEARCH_FORM;
        clearContent();
        binding.title.setText(getString(R.string.service_search));
        if (searchFormData.getTitle() != null) {
            addText(searchFormData.getTitle());
        }
        formWrapper = FormWrapper.createInLayout(activity, binding.content, searchFormData);
        setButtons(
                getString(R.string.cancel), this::showActions,
                null, null,
                getString(R.string.submit), this::submitSearch);
    }

    private void submitSearch() {
        if (formWrapper == null) {
            return;
        }
        if (!formWrapper.validates()) {
            return;
        }
        final Data form = formWrapper.submit();
        showProgress(getString(R.string.service_searching));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(serviceJid);
        final Element query = packet.query(Namespace.SEARCH);
        if (searchLegacy) {
            for (Field field : form.getFields()) {
                for (String value : field.getValues()) {
                    query.addChild(field.getFieldName()).setContent(value);
                }
            }
        } else {
            query.addChild(form);
        }
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onSearchResult(response)));
    }

    private void onSearchResult(IqPacket response) {
        if (response.getType() != IqPacket.TYPE.RESULT) {
            showIqError(response, R.string.service_search_failed);
            return;
        }
        final Element query = response.query(Namespace.SEARCH);
        final Data form = Data.parse(query.findChild("x", Namespace.DATA));
        clearContent();
        binding.title.setText(getString(R.string.service_search_results));
        state = State.SEARCH_RESULTS;
        selectedSearchJid = null;
        firstSearchResultJid = null;
        int count = 0;
        if (form != null && form.getChildren().stream().anyMatch(child -> "item".equals(child.getName()))) {
            for (Element item : form.getChildren()) {
                if (!"item".equals(item.getName()) || !Namespace.DATA.equals(item.getNamespace())) {
                    continue;
                }
                count++;
                addResultRow(formatDataItem(item), extractDataItemJid(item));
            }
        } else {
            for (Element item : query.getChildren()) {
                if ("item".equals(item.getName())) {
                    count++;
                    addResultRow(formatLegacyItem(item), extractLegacyItemJid(item));
                }
            }
        }
        if (count == 0) {
            addText(getString(R.string.service_search_no_results));
            setButtons(
                    getString(R.string.back), this::onBackPressed,
                    null, null,
                    getString(R.string.close), this::dismiss);
        } else {
            setButtons(
                    getString(R.string.back), this::onBackPressed,
                    null, null,
                    getString(R.string.service_add_to_roster),
                    () -> addToRoster(selectedSearchJid != null ? selectedSearchJid : firstSearchResultJid));
        }
    }

    private String formatDataItem(Element item) {
        final StringBuilder builder = new StringBuilder();
        for (Element fieldEl : item.getChildren()) {
            if (!"field".equals(fieldEl.getName())) {
                continue;
            }
            final String label = fieldEl.getAttribute("label");
            final String var = fieldEl.getAttribute("var");
            final String value = fieldEl.findChildContent("value");
            final String trimmed = value == null ? "" : value.trim();
            if (label != null || var != null) {
                builder.append(label != null ? label : var).append(": ").append(trimmed).append("\n");
            } else if (!trimmed.isEmpty()) {
                builder.append(trimmed).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String formatLegacyItem(Element item) {
        final StringBuilder builder = new StringBuilder();
        final String itemJid = item.getAttribute("jid");
        if (itemJid != null) {
            builder.append("JID: ").append(itemJid.trim()).append("\n");
        }
        for (Element child : item.getChildren()) {
            if ("instructions".equals(child.getName())) {
                continue;
            }
            builder.append(titleCase(child.getName())).append(": ").append(child.getContent().trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private String extractDataItemJid(Element item) {
        for (Element fieldEl : item.getChildren()) {
            if ("field".equals(fieldEl.getName()) && "jid".equals(fieldEl.getAttribute("var"))) {
                final String value = fieldEl.findChildContent("value");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return item.getAttribute("jid");
    }

    private String extractLegacyItemJid(Element item) {
        final String attr = item.getAttribute("jid");
        if (attr != null) {
            return attr;
        }
        return item.findChildContent("jid");
    }

    private void addResultRow(String text, String jid) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(8), dp(8));
        final TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        final TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textView.setLayoutParams(textParams);
        row.addView(textView);

        if (jid != null && !jid.isEmpty()) {
            final Jid parsed = tryParseJid(jid);
            if (parsed != null) {
                if (firstSearchResultJid == null) {
                    firstSearchResultJid = parsed;
                }
                row.setOnClickListener(v -> {
                    selectSearchRow(row, parsed);
                    addToRoster(parsed);
                });
            }
        }
        binding.content.addView(row);
    }

    private void selectSearchRow(final LinearLayout row, final Jid jid) {
        selectedSearchJid = jid;
        final TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, outValue, true);
        row.setBackgroundColor(outValue.data);
    }

    private void addToRoster(final Jid jid) {
        final Contact contact = account.getRoster().getContact(jid.asBareJid());
        activity.xmppConnectionService.createContact(contact, true);
        ToastCompat.makeText(activity, R.string.added_to_contacts, ToastCompat.LENGTH_SHORT).show();
    }

    private Jid tryParseJid(String jid) {
        try {
            return Jid.of(jid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void commands() {
        if (!isConnected()) {
            return;
        }
        showProgress(getString(R.string.service_loading_commands));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(serviceJid);
        packet.query(Namespace.DISCO_ITEMS).setAttribute("node", Namespace.COMMANDS);
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onCommandList(response)));
    }

    private void onCommandList(IqPacket response) {
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final List<Element> items = new ArrayList<>();
            for (Element child : response.query().getChildren()) {
                if (child.getName().equals("item")) {
                    items.add(child);
                }
            }
            if (items.isEmpty()) {
                showMessage(R.string.service_no_commands);
                return;
            }
            clearContent();
            state = State.COMMAND_LIST;
            addText(getString(R.string.service_select_command));
            for (Element item : items) {
                final String node = item.getAttribute("node");
                if (node == null) {
                    continue;
                }
                final String name = item.getAttribute("name");
                addAction(name != null ? name : node, () -> executeCommand(node));
            }
            setButtons(
                    getString(R.string.back), this::onBackPressed,
                    null, null,
                    null, null);
        } else {
            showIqError(response, R.string.service_no_commands);
        }
    }

    private void executeCommand(String node) {
        commandNode = node;
        commandSessionId = null;
        commandForm = null;
        formWrapper = null;
        showProgress(getString(R.string.service_executing_command));
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(serviceJid);
        final Element command = packet.command();
        command.setAttribute("xmlns", Namespace.COMMANDS);
        command.setAttribute("node", node);
        command.setAttribute("action", "execute");
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onCommandResult(response)));
    }

    private void onCommandResult(IqPacket response) {
        if (response.getType() != IqPacket.TYPE.RESULT) {
            showIqError(response, R.string.service_command_failed);
            return;
        }
        final Element command = response.command();
        commandNode = command.getAttribute("node");
        commandSessionId = command.getAttribute("sessionid");
        final String status = command.getAttribute("status");
        final Data form = Data.parse(command.findChild("x", Namespace.DATA));
        final String note = command.findChildContent("note", Namespace.COMMANDS);

        if ("completed".equals(status) || "canceled".equals(status)) {
            commandForm = null;
            formWrapper = null;
            if (form != null && "result".equals(form.getAttribute("type"))) {
                showResult(form);
            } else if (note != null) {
                showMessage(note);
            } else if ("canceled".equals(status)) {
                showMessage(R.string.service_command_canceled);
            } else {
                showMessage(R.string.service_command_completed);
            }
            return;
        }

        if (form != null) {
            showCommandForm(form);
        } else if (note != null) {
            showMessage(note);
        } else {
            showMessage(R.string.service_command_failed);
        }
    }

    private void showCommandForm(Data form) {
        clearContent();
        state = State.COMMAND_FORM;
        commandForm = form;
        formWrapper = null;
        final Field actionsField = form.getFieldByName(ACTIONS_FIELD);
        final List<String> actions = actionsField == null ? Collections.emptyList() : actionsField.getValues();
        final Data viewForm = new Data();
        for (Field field : form.getFields()) {
            if ("hidden".equals(field.getType())) {
                continue;
            }
            viewForm.addChild(field);
        }
        clearContent();
        binding.title.setText(getString(R.string.service_command));
        final String title = form.getTitle();
        if (title != null) {
            addText(title);
        }
        if (viewForm.getFields().isEmpty()) {
            addText(getString(R.string.service_command_no_fields));
        } else {
            formWrapper = FormWrapper.createInLayout(activity, binding.content, viewForm);
        }

        final boolean canCancel = actions.contains("cancel");
        final boolean canNext = actions.contains("next");
        final boolean canPrev = actions.contains("prev");
        String positiveActionName = "submit";
        if (viewForm.getFields().isEmpty()) {
            if (actions.contains("execute")) {
                positiveActionName = "execute";
            } else if (actions.contains("complete")) {
                positiveActionName = "complete";
            }
        }
        final String positiveActionNameFinal = positiveActionName;
        setButtons(
                canCancel ? getString(R.string.cancel) : null,
                canCancel ? () -> submitCommand("cancel") : null,
                canNext ? getString(R.string.next) : canPrev ? getString(R.string.previous) : null,
                canNext ? () -> submitCommand("next") : canPrev ? () -> submitCommand("prev") : null,
                getString(R.string.submit),
                () -> submitCommand(positiveActionNameFinal));
    }

    private void submitCommand(String action) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(serviceJid);
        final Element command = packet.command();
        command.setAttribute("xmlns", Namespace.COMMANDS);
        if (commandNode != null) {
            command.setAttribute("node", commandNode);
        }
        if (commandSessionId != null) {
            command.setAttribute("sessionid", commandSessionId);
        }
        command.setAttribute("action", action);
        if (formWrapper != null && commandForm != null) {
            formWrapper.submit();
            commandForm.submit();
            command.addChild(commandForm);
        }
        showProgress(getString(R.string.service_executing_command));
        activity.xmppConnectionService.sendIqPacket(account, packet, (a, response) -> activity.runOnUiThread(() -> onCommandResult(response)));
    }

    private void showResult(Data form) {
        clearContent();
        state = State.RESULT;
        binding.title.setText(getString(R.string.service_result));
        final StringBuilder builder = new StringBuilder();
        for (Element item : form.getChildren()) {
            if (!"item".equals(item.getName()) || !Namespace.DATA.equals(item.getNamespace())) {
                continue;
            }
            for (Element fieldEl : item.getChildren()) {
                if (!"field".equals(fieldEl.getName())) {
                    continue;
                }
                final String label = fieldEl.getAttribute("label");
                final String var = fieldEl.getAttribute("var");
                if (label != null || var != null) {
                    builder.append(label != null ? label : var).append(": ");
                }
                for (Element value : fieldEl.getChildren()) {
                    if ("value".equals(value.getName())) {
                        builder.append(value.getContent());
                    }
                }
                builder.append("\n");
            }
            builder.append("\n");
        }
        final String result = builder.toString().trim();
        addText(result.isEmpty() ? getString(R.string.service_command_completed) : result);
        setButtons(
                getString(R.string.back), this::onBackPressed,
                null, null,
                getString(R.string.close), this::dismiss);
    }

    private void showIqError(IqPacket response, int fallbackRes) {
        final String condition = response.getErrorCondition();
        final int res;
        if ("not-allowed".equals(condition)) {
            res = R.string.service_registration_not_allowed;
        } else if ("conflict".equals(condition)) {
            res = R.string.service_registration_conflict;
        } else if ("service-unavailable".equals(condition)) {
            res = R.string.service_unavailable;
        } else if ("feature-not-implemented".equals(condition)) {
            res = R.string.service_feature_not_implemented;
        } else if ("forbidden".equals(condition)) {
            res = R.string.service_forbidden;
        } else {
            res = fallbackRes;
        }
        showMessage(res);
    }

    private boolean isConnected() {
        if (account.getXmppConnection() != null) {
            return true;
        }
        showMessage(R.string.service_discovery_not_connected);
        return false;
    }

    private void showMessage(int res) {
        showMessage(getString(res));
    }

    private void showMessage(String message) {
        clearContent();
        state = State.MESSAGE;
        addText(message);
        setButtons(
                getString(R.string.back), this::onBackPressed,
                null, null,
                getString(R.string.close), this::dismiss);
    }

    private void showProgress(String message) {
        clearContent();
        state = State.PROGRESS;
        addText(message);
        final ProgressBar progressBar = new ProgressBar(activity);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(16);
        progressBar.setLayoutParams(params);
        binding.content.addView(progressBar);
        hideButtons();
    }

    private void openChat() {
        dismiss();
        final Conversation conversation = activity.xmppConnectionService.findOrCreateConversation(account, serviceJid.asBareJid(), false, false);
        activity.switchToConversation(conversation);
    }

    private void joinRoom() {
        dismiss();
        final Conversation conversation = activity.xmppConnectionService.findOrCreateConversation(account, serviceJid.asBareJid(), true, true, true);
        activity.switchToConversation(conversation);
    }

    private void copyJid() {
        final ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("JID", serviceJid.toEscapedString()));
        ToastCompat.makeText(activity, R.string.jabber_id_copied_to_clipboard, ToastCompat.LENGTH_SHORT).show();
    }

    private void addAction(String text, Runnable action) {
        final TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setPadding(dp(16), dp(16), dp(16), dp(16));
        textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        final TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);
        textView.setClickable(true);
        textView.setOnClickListener(v -> run(action));
        binding.content.addView(textView);
    }

    private void addText(String text) {
        final TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setTextIsSelectable(true);
        textView.setPadding(dp(4), dp(8), dp(4), dp(8));
        binding.content.addView(textView);
    }

    private void clearContent() {
        binding.content.removeAllViews();
        formWrapper = null;
        commandForm = null;
    }

    private void setButtons(String negativeText, Runnable negativeAction, String neutralText, Runnable neutralAction, String positiveText, Runnable positiveAction) {
        this.negativeAction = negativeAction;
        this.neutralAction = neutralAction;
        this.positiveAction = positiveAction;
        configureButton(binding.btnNegative, negativeText);
        configureButton(binding.btnNeutral, neutralText);
        configureButton(binding.btnPositive, positiveText);
    }

    private void configureButton(Button button, String text) {
        if (text == null) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setText(text);
        button.setVisibility(View.VISIBLE);
    }

    private void hideButtons() {
        setButtons(null, null, null, null, null, null);
    }

    private void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private void onBackPressed() {
        switch (state) {
            case SEARCH_RESULTS:
                showSearchForm();
                break;
            case SEARCH_FORM:
            case COMMAND_LIST:
            case RESULT:
            case MESSAGE:
                showActions();
                break;
            case COMMAND_FORM:
                commands();
                break;
            default:
                dismiss();
        }
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private String getString(int res) {
        return activity.getString(res);
    }

    private int dp(int value) {
        return (int) (activity.getResources().getDisplayMetrics().density * value);
    }

    private static String titleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}