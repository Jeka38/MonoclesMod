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
        final Jid jid = account.getJid();
        final Jid from = packet.getFrom();

        // Формат времени
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());

        // Проверяем настройку показа входа/выхода и изменений
        SharedPreferences preferences = mXmppConnectionService.getPreferences();
        boolean showJoinLeave = preferences.getBoolean("show_join_leave", true);

        // Пропускаем обработку, если это bare JID, но логируем для отладки
        if (from.isBareJid()) {
            Log.d("PresenceParser", "Skipping presence from bare JID: " + from);
            return;
        }

        final String type = packet.getAttribute("type");
        final Element x = packet.findChild("x", Namespace.MUC_USER);
        final Element nick = packet.findChild("nick", Namespace.NICK);
        Element hats = packet.findChild("hats", "urn:xmpp:hats:0");
        if (hats == null) {
            hats = packet.findChild("hats", "xmpp:prosody.im/protocol/hats:1");
        }
        if (hats == null) hats = new Element("hats", "urn:xmpp:hats:0");
        final Element occupantId = packet.findChild("occupant-id", "urn:xmpp:occupant-id:0");
        String occupantIdValue = occupantId != null ? occupantId.getAttribute("id") : null;
        Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
        final List<String> codes = getStatusCodes(x);

        Log.d("PresenceParser", "Processing packet: type=" + type + ", from=" + from + ", codes=" + codes + ", occupantId=" + occupantIdValue);

        if (type == null) { // Пользователь онлайн
            if (x != null) {
                Element item = x.findChild("item");
                if (item != null) {
                    mucOptions.setError(MucOptions.Error.NONE);
                    MucOptions.User user = parseItem(conversation, item, from, occupantId, nick == null ? null : nick.getContent(), hats);
                    Log.d("PresenceParser", "Parsed user (online): fullJid=" + user.getFullJid() +
                            ", occupantId=" + user.getOccupantId() +
                            ", nick=" + user.getName() +
                            ", realJid=" + user.getRealJid() +
                            ", role=" + user.getRole() +
                            ", affiliation=" + user.getAffiliation());

                    // Ищем существующего пользователя
                    MucOptions.User existingUser = null;
                    if (user.getRealJid() != null) {
                        existingUser = mucOptions.findUserByRealJid(user.getRealJid());
                        Log.d("PresenceParser", "Checked by realJid: " + user.getRealJid() + ", found=" + (existingUser != null));
                    }
                    if (existingUser == null && occupantIdValue != null) {
                        existingUser = mucOptions.findUserByOccupantId(occupantIdValue);
                        Log.d("PresenceParser", "Checked by occupantId: " + occupantIdValue + ", found=" + (existingUser != null));
                    }
                    if (existingUser == null) {
                        existingUser = mucOptions.findUserByFullJid(from);
                        Log.d("PresenceParser", "Checked by fullJid: " + from + ", found=" + (existingUser != null));
                    }

                    // Логируем данные для диагностики
                    if (existingUser != null) {
                        Log.d("PresenceParser", "Existing user: fullJid=" + existingUser.getFullJid() +
                                ", role=" + existingUser.getRole() +
                                ", affiliation=" + existingUser.getAffiliation());
                    }

                    // Проверяем смену роли или аффилиации
                    if (showJoinLeave && existingUser != null &&
                            (!Objects.equals(existingUser.getAffiliation(), user.getAffiliation()) ||
                                    !Objects.equals(existingUser.getRole(), user.getRole()))) {
                        String displayName = getDisplayName(mucOptions, user);
                        String newRole = user.getRole() != null ? user.getRole().toString().toLowerCase() : "unknown";
                        String newAffiliation = user.getAffiliation() != null ? user.getAffiliation().toString().toLowerCase() : "unknown";
                        String affiliationMessageText = displayName + " " +
                                mXmppConnectionService.getString(R.string.affiliation_changed_to) + " " +
                                newRole + " " + mXmppConnectionService.getString(R.string.and) + " " +
                                newAffiliation + " " + currentTime;

                        addStatusMessage(conversation, affiliationMessageText);
                        Log.d("PresenceParser", "Affiliation/role changed: " + affiliationMessageText);
                        mXmppConnectionService.updateConversationUi(); // Обновляем UI
                    }

                    // Обработка нового пользователя
                    if (showJoinLeave && existingUser == null) {
                        String displayName = getDisplayName(mucOptions, user);
                        String affiliation = user.getAffiliation() != null ? user.getAffiliation().toString().toLowerCase() : "unknown";
                        String role = user.getRole() != null ? user.getRole().toString().toLowerCase() : "unknown";
                        String joinMessageText = displayName + " " +
                                mXmppConnectionService.getString(R.string.user_joined_as) + " " +
                                role + " " + mXmppConnectionService.getString(R.string.and) + " " +
                                affiliation + " " + currentTime;

                        addStatusMessage(conversation, joinMessageText);
                        Log.d("PresenceParser", "User joined: " + joinMessageText);
                        mXmppConnectionService.updateConversationUi();
                    }

                    // Обработка собственного присутствия и создания комнаты
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
                    if (avatar != null) {
                        avatar.owner = from;
                        if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                            if (user.setAvatar(avatar)) {
                                mXmppConnectionService.getAvatarService().clear(user);
                                mXmppConnectionService.updateMucRosterUi();
                            }
                        } else if (mXmppConnectionService.isDataSaverDisabled()) {
                            mXmppConnectionService.fetchAvatar(account, avatar);
                        }
                    }
                }
            }
        } else if (type.equals("unavailable")) { // Пользователь оффлайн
            MucOptions.User user = mucOptions.findUserByFullJid(from);

            if (x != null) {
                boolean fullJidMatches = from.equals(mucOptions.getSelf().getFullJid());
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
                    handleSelfPresenceError(mucOptions, account, codes, conversation);
                } else {
                    Element item = x.findChild("item");
                    String newNick = nick != null ? nick.getContent() : (item != null ? item.getAttribute("nick") : null);

                    if (user != null && showJoinLeave) {
                        String displayName = getDisplayName(mucOptions, user);

                        // Проверяем, был ли пользователь кикнут
                        if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                            String kickMessageText = displayName + " " +
                                    mXmppConnectionService.getString(R.string.user_was_kicked) + " " + currentTime;
                            addStatusMessage(conversation, kickMessageText);
                            Log.d("PresenceParser", "User was kicked: " + kickMessageText);
                            mucOptions.deleteUser(from);
                        }
                        // Проверяем, был ли пользователь забанен
                        else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                            String banMessageText = displayName + " " +
                                    mXmppConnectionService.getString(R.string.user_was_banned) + " " + currentTime;
                            addStatusMessage(conversation, banMessageText);
                            Log.d("PresenceParser", "User was banned: " + banMessageText);
                            mucOptions.deleteUser(from);
                        }
                        // Проверяем, сменил ли пользователь ник
                        else if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                            if (newNick != null) {
                                String nickChangeMessageText = displayName + " " +
                                        mXmppConnectionService.getString(R.string.user_changed_nick) + " " + newNick + " " + currentTime;
                                addStatusMessage(conversation, nickChangeMessageText);
                                Log.d("PresenceParser", "User changed nick: " + nickChangeMessageText);

                                // Удаляем старую запись
                                mucOptions.deleteUser(from);

                                // Создаём нового пользователя с новым ником
                                Jid newFullJid = Jid.of(from.getLocal(), from.getDomain(), newNick);
                                MucOptions.User updatedUser = item != null ?
                                        parseItem(conversation, item, newFullJid, occupantId, newNick, hats) :
                                        new MucOptions.User(mucOptions, newFullJid, user.getOccupantId(), newNick, user.getHats());
                                if (item != null) {
                                    updatedUser.setRealJid(item.getAttributeAsJid("jid"));
                                    updatedUser.setRole(item.getAttribute("role"));
                                    updatedUser.setAffiliation(item.getAttribute("affiliation"));
                                } else {
                                    updatedUser.setRealJid(user.getRealJid());
                                    updatedUser.setRole(user.getRole().toString());
                                    updatedUser.setAffiliation(user.getAffiliation().toString());
                                }
                                mucOptions.updateUser(updatedUser);
                                Log.d("PresenceParser", "Updated user after nick change: fullJid=" + updatedUser.getFullJid() + ", occupantId=" + updatedUser.getOccupantId() + ", realJid=" + updatedUser.getRealJid());
                                mXmppConnectionService.updateConversationUi();
                            } else {
                                Log.w("PresenceParser", "Nick change detected but new nick is null for " + from);
                            }
                        }
                        // Сообщение о выходе только если это не кик, не бан и не смена ника
                        else {
                            String leaveMessageText = displayName + " " +
                                    mXmppConnectionService.getString(R.string.user_left) + " " + currentTime;
                            addStatusMessage(conversation, leaveMessageText);
                            Log.d("PresenceParser", "User left: " + leaveMessageText);
                            mucOptions.deleteUser(from);
                        }

                        if (!codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                            mXmppConnectionService.getAvatarService().clear(user);
                        }
                    }
                }
            } else if (user != null && showJoinLeave) {
                // Сообщение о выходе, если нет элемента <x>
                String displayName = getDisplayName(mucOptions, user);
                String leaveMessageText = displayName + " " +
                        mXmppConnectionService.getString(R.string.user_left) + " " + currentTime;
                addStatusMessage(conversation, leaveMessageText);
                Log.d("PresenceParser", "User left: " + leaveMessageText);
                mucOptions.deleteUser(from);
                mXmppConnectionService.getAvatarService().clear(user);
            }
        } else if (type.equals("error")) {
            handlePresenceError(packet, mucOptions, account, conversation);
        }
    }
    private void addStatusMessage(Conversation conversation, String text) {
        Message message = new Message(conversation, text, Message.ENCRYPTION_NONE);
        message.setType(Message.TYPE_STATUS);
        message.setTime(System.currentTimeMillis());
        message.setCounterpart(null);
        message.markUnread(); // Помечаем как непрочитанное
        conversation.add(message);
    }

    // Обработка ошибок собственного присутствия
    private void handleSelfPresenceError(MucOptions mucOptions, Account account, List<String> codes, Conversation conversation) {
        if (codes.contains(MucOptions.STATUS_CODE_TECHNICAL_REASONS)) {
            boolean wasOnline = mucOptions.online();
            mucOptions.setError(MucOptions.Error.TECHNICAL_PROBLEMS);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received status code 333 in room " +
                    mucOptions.getConversation().getJid().asBareJid() + " online=" + wasOnline);
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
            Log.d(Config.LOGTAG, "unknown error in conference");
        }
    }

    // Обработка ошибок присутствия
    private void handlePresenceError(PresencePacket packet, MucOptions mucOptions, Account account, Conversation conversation) {
        final Element error = packet.findChild("error");
        if (error == null) return;

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
            final Jid alternate = gone != null && new XmppUri(gone).isValidJid() ? new XmppUri(gone).getJid() : null;
            mucOptions.setError(MucOptions.Error.DESTROYED);
            if (alternate != null) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": muc destroyed. alternate location " + alternate);
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