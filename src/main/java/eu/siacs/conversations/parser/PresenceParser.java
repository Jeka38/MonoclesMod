package eu.siacs.conversations.parser;

import android.content.SharedPreferences;
import android.util.Log;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
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
            processConferencePresence(packet, conversation);
            final List<MucOptions.User> tileUserAfter = mucOptions.getUsers(5);
            if (!tileUserAfter.equals(tileUserBefore)) {
                mXmppConnectionService.getAvatarService().clear(mucOptions);
            }
            if (before != mucOptions.online() || (mucOptions.online() && count != mucOptions.getUserCount())) {
                mXmppConnectionService.updateConversationUi();
            } else if (mucOptions.online()) {
                mXmppConnectionService.updateMucRosterUi();
            }
        }
    }

    private void processConferencePresence(PresencePacket packet, Conversation conversation) {
        final Account account = conversation.getAccount();
        final MucOptions mucOptions = conversation.getMucOptions();
        final Jid jid = conversation.getAccount().getJid();
        final Jid from = packet.getFrom();

        // Проверяем настройку показа входа/выхода и изменений
        SharedPreferences preferences = mXmppConnectionService.getPreferences();
        boolean showJoinLeave = preferences.getBoolean("show_join_leave", true);

        // Формат времени
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());

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

                        // Ищем существующего пользователя по полному JID
                        MucOptions.User existingUser = null;
                        for (MucOptions.User u : mucOptions.getUsers()) {
                            if (u.getFullJid() != null && u.getFullJid().equals(from)) {
                                existingUser = u;
                                break;
                            }
                        }

                        if (showJoinLeave) {
                            String displayName = getDisplayName(mucOptions, user);

                            if (existingUser == null) {
                                String affiliation = user.getAffiliation() != null ? user.getAffiliation().toString().toLowerCase() : "unknown";
                                String role = user.getRole() != null ? user.getRole().toString().toLowerCase() : "unknown";
                                String joinMessageText = displayName + " " +
                                        mXmppConnectionService.getString(R.string.user_joined_as) + " " +
                                        role + " " + mXmppConnectionService.getString(R.string.and) + " " +
                                        affiliation + " " + currentTime;

                                Message joinMessage = new Message(conversation, joinMessageText, Message.ENCRYPTION_NONE);
                                joinMessage.setType(Message.TYPE_STATUS);
                                joinMessage.setTime(System.currentTimeMillis());
                                joinMessage.setCounterpart(null);
                                conversation.add(joinMessage);
                                Log.d("PresenceParser", "User joined: " + joinMessageText);
                            } else {
                                // Проверка изменения аффилиации
                                if (!Objects.equals(existingUser.getAffiliation(), user.getAffiliation()) |  !Objects.equals(existingUser.getRole(), user.getRole())) {
                                    String newRole = user.getRole() != null ? user.getRole().toString().toLowerCase() : "unknown";
                                    String newAffiliation = user.getAffiliation() != null ? user.getAffiliation().toString().toLowerCase() : "unknown";
                                    String affiliationMessageText = displayName + " " +
                                            mXmppConnectionService.getString(R.string.affiliation_changed_to) + " " +
                                            newRole + " " + mXmppConnectionService.getString(R.string.and) + " " +
                                            newAffiliation + " " + currentTime;

                                    Message affiliationMessage = new Message(conversation, affiliationMessageText, Message.ENCRYPTION_NONE);
                                    affiliationMessage.setType(Message.TYPE_STATUS);
                                    affiliationMessage.setTime(System.currentTimeMillis());
                                    affiliationMessage.setCounterpart(null);
                                    conversation.add(affiliationMessage);
                                    Log.d("PresenceParser", "Affiliation changed: " + affiliationMessageText);
                                }
                            }
                        }

                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                                (codes.contains(MucOptions.STATUS_CODE_ROOM_CREATED) &&
                                        jid.equals(InvalidJid.getNullForInvalid(item.getAttributeAsJid("jid"))))) {
                            if (mucOptions.setOnline()) {
                                mXmppConnectionService.getAvatarService().clear(mucOptions);
                            }
                            if (mucOptions.setSelf(user)) {
                                Log.d(Config.LOGTAG, "role or affiliation changed");
                                mXmppConnectionService.databaseBackend.updateConversation(conversation);
                            }
                            mXmppConnectionService.persistSelfNick(user);
                            invokeRenameListener(mucOptions, true);
                        }
                        mucOptions.updateUser(user);

                        if (codes.contains(MucOptions.STATUS_CODE_ROOM_CREATED) && mucOptions.autoPushConfiguration()) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid()
                                    + ": room '"
                                    + mucOptions.getConversation().getJid().asBareJid()
                                    + "' created. pushing default configuration");
                            mXmppConnectionService.pushConferenceConfiguration(mucOptions.getConversation(),
                                    IqGenerator.defaultChannelConfiguration(),
                                    null);
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
                    Element item = x.findChild("item");
                    if (item != null) {
                        mucOptions.updateUser(parseItem(conversation, item, from, occupantId, nick == null ? null : nick.getContent(), hats));
                    }
                    MucOptions.User user = mucOptions.deleteUser(from);
                    if (user != null && showJoinLeave) {
                        String displayName = getDisplayName(mucOptions, user);
                        String leaveMessageText = displayName + " " +
                                mXmppConnectionService.getString(R.string.user_left) + " " +
                                currentTime;
                        Message leaveMessage = new Message(conversation, leaveMessageText, Message.ENCRYPTION_NONE);
                        leaveMessage.setType(Message.TYPE_STATUS);
                        leaveMessage.setTime(System.currentTimeMillis());
                        leaveMessage.setCounterpart(null);
                        conversation.add(leaveMessage);
                        Log.d("PresenceParser", "User left: " + leaveMessageText);

                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                final Element error = packet.findChild("error");
                if (error == null) {
                    return;
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
    }

    private static String getDisplayName(MucOptions mucOptions, MucOptions.User user) {
        boolean isSelfAdminOrHigher = mucOptions.getSelf().getRole() != null &&
                (mucOptions.getSelf().getRole() == MucOptions.Role.MODERATOR ||
                        mucOptions.getSelf().getAffiliation() == MucOptions.Affiliation.ADMIN ||
                        mucOptions.getSelf().getAffiliation() == MucOptions.Affiliation.OWNER);

        // Формат имени в зависимости от вашей роли
        return isSelfAdminOrHigher && user.getRealJid() != null
                ? user.getName() + " (" + user.getRealJid() + ")"
                : user.getName();
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