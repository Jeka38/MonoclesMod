package eu.siacs.conversations.parser;

import android.util.Log;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class PresenceParser extends AbstractParser implements
        OnPresencePacketReceived {

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    public void parseConferencePresence(PresencePacket packet, Account account) {
        final Conversation conversation = packet.getFrom() == null ? null : mXmppConnectionService.find(account, packet.getFrom().asBareJid());
        if (conversation != null) {
            final MucOptions mucOptions = conversation.getMucOptions();
            boolean before = mucOptions.online();
            int count = mucOptions.getUserCount();
            final List<MucOptions.User> tileUserBefore = mucOptions.getUsers(5);
            boolean addedStatusMessage = processConferencePresence(packet, conversation);
            final List<MucOptions.User> tileUserAfter = mucOptions.getUsers(5);
            if (!tileUserAfter.equals(tileUserBefore)) {
                mXmppConnectionService.getAvatarService().clear(mucOptions);
            }
            if (before != mucOptions.online() || (mucOptions.online() && count != mucOptions.getUserCount()) || addedStatusMessage) {
                mXmppConnectionService.updateConversationUi();
            } else if (mucOptions.online()) {
                mXmppConnectionService.updateMucRosterUi();
            }
        }
    }

    private boolean processConferencePresence(PresencePacket packet, Conversation conversation) {
        final Account account = conversation.getAccount();
        final MucOptions mucOptions = conversation.getMucOptions();
        final Jid jid = conversation.getAccount().getJid();
        final Jid from = packet.getFrom();
        boolean addedStatusMessage = false;
        if (!from.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", Namespace.MUC_USER);
            final Element nick = packet.findChild("nick", Namespace.NICK);
            Element hats = packet.findChild("hats", "urn:xmpp:hats:0");
            if (hats == null) {
                hats = packet.findChild("hats", "xmpp:prosody.im/protocol/hats:1");
            }
            if (hats == null) hats = new Element("hats", "urn:xmpp:hats:0");
            final Element occupantId = packet.findChild("occupant-id", "urn:xmpp:occupant-id:0");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !from.isBareJid()) {
                        mucOptions.setError(MucOptions.Error.NONE);
                        MucOptions.User user = parseItem(conversation, item, from, occupantId, nick == null ? null : nick.getContent(), hats);
                        final String itemNick = item.getAttribute("nick");
                        if (itemNick != null) {
                            user.setNick(itemNick);
                        }
                        final boolean isSelf = codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || (codes.contains(MucOptions.STATUS_CODE_ROOM_CREATED) && jid.equals(InvalidJid.getNullForInvalid(item.getAttributeAsJid("jid"))));
                        if (isSelf) {
                            final boolean justWentOnline = mucOptions.setOnline();
                            if (justWentOnline) {
                                conversation.clearStatusMessages();
                                mXmppConnectionService.getAvatarService().clear(mucOptions);
                            }
                            final String oldRole = conversation.getAttribute("role");
                            final String oldAffiliation = conversation.getAttribute("affiliation");
                            if (mucOptions.setSelf(user)) {
                                Log.d(Config.LOGTAG, "role or affiliation changed");
                                mXmppConnectionService.databaseBackend.updateConversation(conversation);
                                final String newRole = conversation.getAttribute("role");
                                final String newAffiliation = conversation.getAttribute("affiliation");
                                final boolean roleChanged = !com.google.common.base.Strings.nullToEmpty(oldRole).equals(newRole);
                                final boolean affiliationChanged = !com.google.common.base.Strings.nullToEmpty(oldAffiliation).equals(newAffiliation);
                                final String roleString = user.getRole() == MucOptions.Role.NONE ? null : mXmppConnectionService.getString(user.getRole().getResId()).toLowerCase(Locale.getDefault());
                                final String affiliationString = user.getAffiliation() == MucOptions.Affiliation.NONE ? null : mXmppConnectionService.getString(user.getAffiliation().getResId()).toLowerCase(Locale.getDefault());
                                String body = null;
                                if (roleChanged && affiliationChanged && roleString != null && affiliationString != null) {
                                    body = mXmppConnectionService.getString(R.string.muc_role_and_affiliation_changed, roleString, affiliationString);
                                } else if (roleChanged && roleString != null) {
                                    body = mXmppConnectionService.getString(R.string.muc_role_changed, roleString);
                                } else if (affiliationChanged && affiliationString != null) {
                                    body = mXmppConnectionService.getString(R.string.muc_affiliation_changed, affiliationString);
                                }
                                if (body != null) {
                                    String prefix = "";
                                    if (roleChanged && affiliationChanged) {
                                        prefix = "MUC_ROLE_AFFILIATION:";
                                    } else if (roleChanged) {
                                        prefix = "MUC_ROLE:";
                                    } else if (affiliationChanged) {
                                        prefix = "MUC_AFFILIATION:";
                                    }
                                    Message statusMessage = Message.createStatusMessage(conversation, prefix + body);
                                    if (mXmppConnectionService.getBooleanPreference("show_muc_status_messages", R.bool.show_muc_status_messages)) {
                                        statusMessage.markUnread();
                                    }
                                    conversation.add(statusMessage);
                                    mXmppConnectionService.getNotificationService().push(statusMessage);
                                    addedStatusMessage = true;
                                }
                            }
                            mXmppConnectionService.persistSelfNick(user);
                            invokeRenameListener(mucOptions, true);
                        } else if (mucOptions.online()) {
                            MucOptions.User oldUser = mucOptions.findUserByFullJid(from);
                            if (oldUser == null && user.getRealJid() != null) {
                                oldUser = mucOptions.findUserByRealJid(user.getRealJid());
                            }
                            if (oldUser != null) {
                                final boolean roleChanged = oldUser.getRole() != user.getRole();
                                final boolean affiliationChanged = oldUser.getAffiliation() != user.getAffiliation();
                                if (roleChanged || affiliationChanged) {
                                    final String roleString = user.getRole() == MucOptions.Role.NONE ? null : mXmppConnectionService.getString(user.getRole().getResId()).toLowerCase(Locale.getDefault());
                                    final String affiliationString = user.getAffiliation() == MucOptions.Affiliation.NONE ? null : mXmppConnectionService.getString(user.getAffiliation().getResId()).toLowerCase(Locale.getDefault());
                                    String body = null;
                                    String name = user.getName();
                                    String prefix = "";
                                    if (roleChanged && affiliationChanged && roleString != null && affiliationString != null) {
                                        body = mXmppConnectionService.getString(R.string.muc_occupant_role_and_affiliation_changed, name, roleString, affiliationString);
                                        prefix = "MUC_ROLE_AFFILIATION:";
                                    } else if (roleChanged && roleString != null) {
                                        body = mXmppConnectionService.getString(R.string.muc_occupant_role_changed, name, roleString);
                                        prefix = "MUC_ROLE:";
                                    } else if (affiliationChanged && affiliationString != null) {
                                        body = mXmppConnectionService.getString(R.string.muc_occupant_affiliation_changed, name, affiliationString);
                                        prefix = "MUC_AFFILIATION:";
                                    }
                                    if (body != null) {
                                        Message statusMessage = Message.createStatusMessage(conversation, prefix + body);
                                        if (mXmppConnectionService.getBooleanPreference("show_muc_status_messages", R.bool.show_muc_status_messages)) {
                                            statusMessage.markUnread();
                                        }
                                        conversation.add(statusMessage);
                                        mXmppConnectionService.getNotificationService().push(statusMessage);
                                        addedStatusMessage = true;
                                    }
                                }
                            }
                        }
                        boolean isNew = mucOptions.updateUser(user);
                        if (isNew && !isSelf && mucOptions.online() && !codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                            String body = mXmppConnectionService.getString(R.string.muc_occupant_joined, from.getResource());
                            Message statusMessage = Message.createStatusMessage(conversation, "MUC_JOINED:" + body);
                            if (mXmppConnectionService.getBooleanPreference("show_join_leave", R.bool.show_join_leave)) {
                                statusMessage.markUnread();
                            }
                            conversation.add(statusMessage);
                            addedStatusMessage = true;
                        }
                        final AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
                        Contact contact = user.getContact();
                        if (isNew
                                && user.getRealJid() != null
                                && mucOptions.isPrivateAndNonAnonymous()
                                && (contact == null || !contact.mutualPresenceSubscription())
                                && axolotlService.hasEmptyDeviceList(user.getRealJid())) {
                            axolotlService.fetchDeviceIds(user.getRealJid());
                        }
                        if (codes.contains(MucOptions.STATUS_CODE_ROOM_CREATED) && mucOptions.autoPushConfiguration()) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid()
                                    + ": room '"
                                    + mucOptions.getConversation().getJid().asBareJid()
                                    + "' created. pushing default configuration");
                            mXmppConnectionService.pushConferenceConfiguration(mucOptions.getConversation(),
                                    IqGenerator.defaultChannelConfiguration(),
                                    null);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(mucOptions.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = from;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                                if (user.getRealJid() != null) {
                                    final Contact c = conversation.getAccount().getRoster().getContact(user.getRealJid());
                                    c.setAvatar(avatar);
                                    mXmppConnectionService.syncRoster(conversation.getAccount());
                                    mXmppConnectionService.getAvatarService().clear(c);
                                    mXmppConnectionService.updateRosterUi(XmppConnectionService.UpdateRosterReason.AVATAR);
                                }
                            } else if (mXmppConnectionService.isDataSaverDisabled()) {
                                mXmppConnectionService.fetchAvatar(mucOptions.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                final boolean fullJidMatches = from.equals(mucOptions.getSelf().getFullJid());
                if (x.hasChild("destroy") && fullJidMatches) {
                    Element destroy = x.findChild("destroy");
                    final Jid alternate = destroy == null ? null : InvalidJid.getNullForInvalid(destroy.getAttributeAsJid("jid"));
                    mucOptions.setError(MucOptions.Error.DESTROYED);
                    if (alternate != null) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": muc destroyed. alternate location " + alternate);
                    }
                } else if (codes.contains(MucOptions.STATUS_CODE_SHUTDOWN) && fullJidMatches) {
                    mucOptions.setError(MucOptions.Error.SHUTDOWN);
                } else if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE)) {
                    if (codes.contains(MucOptions.STATUS_CODE_TECHNICAL_REASONS)) {
                        final boolean wasOnline = mucOptions.online();
                        mucOptions.setError(MucOptions.Error.TECHNICAL_PROBLEMS);
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": received status code 333 in room "
                                        + mucOptions.getConversation().getJid().asBareJid()
                                        + " online="
                                        + wasOnline);
                        if (wasOnline) {
                            mXmppConnectionService.mucSelfPingAndRejoin(conversation);
                        }
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        mucOptions.setError(MucOptions.Error.KICKED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        mucOptions.setError(MucOptions.Error.BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
                    } else if (codes.contains(MucOptions.STATUS_CODE_AFFILIATION_CHANGE)) {
                        mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
                    } else if (codes.contains(MucOptions.STATUS_CODE_SHUTDOWN)) {
                        mucOptions.setError(MucOptions.Error.SHUTDOWN);
                    } else if (!codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        mucOptions.setError(MucOptions.Error.UNKNOWN);
                        Log.d(Config.LOGTAG, "unknown error in conference: " + packet);
                    }
                } else if (!from.isBareJid()) {
                    final boolean fullJidMatchesOther = from.equals(mucOptions.getSelf().getFullJid());
                    Element item = x == null ? null : x.findChild("item");
                    if (item != null) {
                        MucOptions.User user = parseItem(conversation, item, from, occupantId, nick == null ? null : nick.getContent(), hats);
                        final String itemNick = item.getAttribute("nick");
                        if (itemNick != null) {
                            user.setNick(itemNick);
                        }
                        if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                            String newNick = item.getAttribute("nick");
                            if (newNick != null) {
                                String body = mXmppConnectionService.getString(R.string.muc_occupant_changed_nick, from.getResource(), newNick);
                                Message statusMessage = Message.createStatusMessage(conversation, "MUC_NICK:" + body);
                                statusMessage.markUnread();
                                conversation.add(statusMessage);
                                mXmppConnectionService.getNotificationService().push(statusMessage);
                                addedStatusMessage = true;
                                mucOptions.setPendingNickChange(newNick);
                            }
                        } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                            String body = mXmppConnectionService.getString(R.string.muc_occupant_kicked, from.getResource());
                            Message statusMessage = Message.createStatusMessage(conversation, "MUC_KICKED:" + body);
                            statusMessage.markUnread();
                            conversation.add(statusMessage);
                            mXmppConnectionService.getNotificationService().push(statusMessage);
                            addedStatusMessage = true;
                        } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                            String body = mXmppConnectionService.getString(R.string.muc_occupant_banned, from.getResource());
                            Message statusMessage = Message.createStatusMessage(conversation, "MUC_BANNED:" + body);
                            statusMessage.markUnread();
                            conversation.add(statusMessage);
                            mXmppConnectionService.getNotificationService().push(statusMessage);
                            addedStatusMessage = true;
                        }
                        mucOptions.updateUser(user);
                    }
                    MucOptions.User user = mucOptions.deleteUser(from);
                    final boolean isSelf = codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || fullJidMatchesOther;
                    if (user != null && !isSelf && !codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        String body = mXmppConnectionService.getString(R.string.muc_occupant_left, from.getResource());
                        Message statusMessage = Message.createStatusMessage(conversation, "MUC_LEFT:" + body);
                        if (mXmppConnectionService.getBooleanPreference("show_join_leave", R.bool.show_join_leave)) {
                            statusMessage.markUnread();
                        }
                        conversation.add(statusMessage);
                        addedStatusMessage = true;
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                final Element error = packet.findChild("error");
                if (error == null) {
                    return addedStatusMessage;
                }
                final Data captchaForm = Data.parse(error.findChild("x", Namespace.DATA));
                if (captchaForm != null
                        && "urn:xmpp:captcha".equals(captchaForm.getFormType())
                        && captchaForm.getFieldByName("ocr") != null) {
                    final String challenge = error.findChildContent("text");
                    if (mXmppConnectionService.displayMucCaptchaRequest(conversation, captchaForm, challenge)) {
                        return addedStatusMessage;
                    }
                }
                if (error.hasChild("conflict")) {
                    if (mucOptions.online()) {
                        invokeRenameListener(mucOptions, false);
                    } else {
                        mucOptions.setError(MucOptions.Error.NICK_IN_USE);
                    }
                } else if (error.hasChild("not-authorized")) {
                    mucOptions.setError(MucOptions.Error.PASSWORD_REQUIRED);
                } else if (error.hasChild("forbidden")) {
                    mucOptions.setError(MucOptions.Error.BANNED);
                } else if (error.hasChild("registration-required")) {
                    mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
                } else if (error.hasChild("resource-constraint")) {
                    mucOptions.setError(MucOptions.Error.RESOURCE_CONSTRAINT);
                } else if (error.hasChild("remote-server-timeout")) {
                    mucOptions.setError(MucOptions.Error.REMOTE_SERVER_TIMEOUT);
                } else if (error.hasChild("gone")) {
                    final String gone = error.findChildContent("gone");
                    final Jid alternate;
                    if (gone != null) {
                        final XmppUri xmppUri = new XmppUri(gone);
                        if (xmppUri.isValidJid()) {
                            alternate = xmppUri.getJid();
                        } else {
                            alternate = null;
                        }
                    } else {
                        alternate = null;
                    }
                    mucOptions.setError(MucOptions.Error.DESTROYED);
                    if (alternate != null) {
                        Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": muc destroyed. alternate location " + alternate);
                    }
                } else {
                    final String text = error.findChildContent("text");
                    if (text != null && text.contains("attribute 'to'")) {
                        if (mucOptions.online()) {
                            invokeRenameListener(mucOptions, false);
                        } else {
                            mucOptions.setError(MucOptions.Error.INVALID_NICK);
                        }
                    } else {
                        mucOptions.setError(MucOptions.Error.UNKNOWN);
                        Log.d(Config.LOGTAG, "unknown error in conference: " + packet);
                    }
                }
            }
        }
        return addedStatusMessage;
    }

    private static void invokeRenameListener(final MucOptions options, boolean success) {
        if (options.onRenameListener != null) {
            if (success) {
                options.onRenameListener.onSuccess();
            } else {
                options.onRenameListener.onFailure();
            }
            options.onRenameListener = null;
        }
    }

    private static List<String> getStatusCodes(Element x) {
        List<String> codes = new ArrayList<>();
        if (x != null) {
            for (Element child : x.getChildren()) {
                if (child.getName().equals("status")) {
                    String code = child.getAttribute("code");
                    if (code != null) {
                        codes.add(code);
                    }
                }
            }
        }
        return codes;
    }

    private void parseContactPresence(final PresencePacket packet, final Account account) {
        final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
        final Jid from = packet.getFrom();
        if (from == null || from.equals(account.getJid())) {
            return;
        }
        final String type = packet.getAttribute("type");
        final Contact contact = account.getRoster().getContact(from);
        if (type == null) {
            final String resource = from.isBareJid() ? "" : from.getResource();
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            if (avatar != null && (!contact.isSelf() || account.getAvatar() == null)) {
                avatar.owner = from.asBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (avatar.owner.equals(account.getJid().asBareJid())) {
                        account.setAvatar(avatar.getFilename());
                        mXmppConnectionService.databaseBackend.updateAccount(account);
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else {
                        contact.setAvatar(avatar);
                        mXmppConnectionService.syncRoster(account);
                        mXmppConnectionService.getAvatarService().clear(contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi(XmppConnectionService.UpdateRosterReason.AVATAR);
                    }
                } else if (mXmppConnectionService.isDataSaverDisabled()) {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }

            if (mXmppConnectionService.isMuc(account, from)) {
                return;
            }
            int sizeBefore = contact.getPresences().size();

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final String message = packet.findChildContent("status");
            final Presence presence = Presence.parse(show, caps, message);
            contact.updatePresence(resource, presence);
            if (presence.hasCaps()) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            final Element idle = packet.findChild("idle", Namespace.IDLE);
            if (idle != null) {
                try {
                    final String since = idle.getAttribute("since");
                    contact.setLastseen(AbstractParser.parseTimestamp(since));
                    contact.flagInactive();
                } catch (Throwable throwable) {
                    if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                        contact.flagActive();
                    }
                }
            } else {
                if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                    contact.flagActive();
                }
            }

            PgpEngine pgp = mXmppConnectionService.getPgpEngine();
            Element x = packet.findChild("x", "jabber:x:signed");
            if (pgp != null && x != null) {
                final String status = packet.findChildContent("status");
                final long keyId = pgp.fetchKeyId(account, status, x.getContent());
                if (keyId != 0 && contact.setPgpKeyId(keyId)) {
                    Log.d(Config.LOGTAG,account.getJid().asBareJid()+": found OpenPGP key id for "+contact.getJid()+" "+OpenPgpUtils.convertKeyIdToHex(keyId));
                    mXmppConnectionService.syncRoster(account);
                }
            }
            boolean online = sizeBefore < contact.getPresences().size();
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
        } else if (type.equals("unavailable")) {
            if (contact.setLastseen(AbstractParser.parseTimestamp(packet, 0L, true))) {
                contact.flagInactive();
            }
            if (from.isBareJid()) {
                contact.clearPresences();
            } else {
                contact.removePresence(from.getResource());
            }
            if (contact.getShownStatus() == Presence.Status.OFFLINE) {
                contact.flagInactive();
            }
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
        } else if (type.equals("subscribe")) {
            if (contact.isBlocked()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring 'subscribe' presence from blocked "
                                + from);
                return;
            }
            if (contact.setPresenceName(packet.findChildContent("nick", Namespace.NICK))) {
                mXmppConnectionService.syncRoster(account);
                mXmppConnectionService.getAvatarService().clear(contact);
            }
            if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                mXmppConnectionService.sendPresencePacket(account,
                        mPresenceGenerator.sendPresenceUpdatesTo(contact));
            } else {
                contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
                final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
                        account, contact.getJid().asBareJid(), false, false);
                mXmppConnectionService.getNotificationService().pushSubscriptionRequest(conversation);
                final String statusMessage = packet.findChildContent("status");
                if (statusMessage != null
                        && !statusMessage.isEmpty()
                        && conversation.countMessages() == 0) {
                    conversation.add(new Message(
                            conversation,
                            statusMessage,
                            Message.ENCRYPTION_NONE,
                            Message.STATUS_RECEIVED
                    ));
                }
            }
        }
        mXmppConnectionService.updateRosterUi(XmppConnectionService.UpdateRosterReason.PRESENCE, contact);
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        if (packet.hasChild("x", Namespace.MUC_USER)) {
            this.parseConferencePresence(packet, account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            this.parseConferencePresence(packet, account);
        } else if ("error".equals(packet.getAttribute("type")) && mXmppConnectionService.isMuc(account, packet.getFrom())) {
            this.parseConferencePresence(packet, account);
        } else {
            this.parseContactPresence(packet, account);
        }
    }

}
