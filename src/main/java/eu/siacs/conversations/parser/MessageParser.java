package eu.siacs.conversations.parser;

import android.util.Log;
import android.util.Pair;
import android.net.Uri;


import de.monocles.mod.BobTransfer;
import de.monocles.mod.WebxdcUpdate;
import java.io.File;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.siacs.conversations.xmpp.pep.Avatar;
import io.ipfs.cid.Cid;

import android.os.Build;
import android.text.Html;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.crypto.OtrService;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.BrokenSessionException;
import eu.siacs.conversations.crypto.axolotl.NotEncryptedForThisDeviceException;
import eu.siacs.conversations.crypto.axolotl.OutdatedSenderException;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Edit;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.ReceiptRequest;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;



public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

    private static final List<String> CLIENTS_SENDING_HTML_IN_OTR = Arrays.asList("Pidgin", "Adium", "Trillian");

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    private static final List<String> JINGLE_MESSAGE_ELEMENT_NAMES =
            Arrays.asList("accept", "propose", "proceed", "reject", "retract", "ringing", "finish");

    public MessageParser(XmppConnectionService service) {
        super(service);
    }

    private static String extractStanzaId(Element packet, boolean isTypeGroupChat, Conversation conversation) {
        final Jid by;
        final boolean safeToExtract;
        if (isTypeGroupChat) {
            by = conversation.getJid().asBareJid();
            safeToExtract = conversation.getMucOptions().hasFeature(Namespace.STANZA_IDS);
        } else {
            Account account = conversation.getAccount();
            by = account.getJid().asBareJid();
            safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        }
        return safeToExtract ? extractStanzaId(packet, by) : null;
    }

    private static String extractStanzaId(Account account, Element packet) {
        final boolean safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        return safeToExtract ? extractStanzaId(packet, account.getJid().asBareJid()) : null;
    }

    private static String extractStanzaId(Element packet, Jid by) {
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("stanza-id")
                    && Namespace.STANZA_IDS.equals(child.getNamespace())
                    && by.equals(InvalidJid.getNullForInvalid(child.getAttributeAsJid("by")))) {
                return child.getAttribute("id");
            }
        }
        return null;
    }

    private static Jid getTrueCounterpart(Element mucUserElement, Jid fallback) {
        final Element item = mucUserElement == null ? null : mucUserElement.findChild("item");
        Jid result = item == null ? null : InvalidJid.getNullForInvalid(item.getAttributeAsJid("jid"));
        return result != null ? result : fallback;
    }

    private static boolean clientMightSendHtml(Account account, Jid from) {
        String resource = from.getResource();
        if (resource == null) {
            return false;
        }
        Presence presence = account.getRoster().getContact(from).getPresences().getPresencesMap().get(resource);
        ServiceDiscoveryResult disco = presence == null ? null : presence.getServiceDiscoveryResult();
        if (disco == null) {
            return false;
        }
        return hasIdentityKnowForSendingHtml(disco.getIdentities());
    }

    private static boolean hasIdentityKnowForSendingHtml(List<ServiceDiscoveryResult.Identity> identities) {
        for (ServiceDiscoveryResult.Identity identity : identities) {
            if (identity.getName() != null) {
                if (CLIENTS_SENDING_HTML_IN_OTR.contains(identity.getName())) {
                    return true;
                }
            }
        }
        return false;
    }


    private boolean extractChatState(Conversation c, final boolean isTypeGroupChat, final MessagePacket packet) {
        ChatState state = ChatState.parse(packet);
        if (state != null && c != null) {
            final Account account = c.getAccount();
            final Jid from = packet.getFrom();
            if (from.asBareJid().equals(account.getJid().asBareJid())) {
                c.setOutgoingChatState(state);
                if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
                    if (c.getContact().isSelf()) {
                        return false;
                    }
                    mXmppConnectionService.markRead(c);
                    activateGracePeriod(account);
                }
                return false;
            } else {
                if (isTypeGroupChat) {
                    MucOptions.User user = c.getMucOptions().findUserByFullJid(from);
                    if (user != null) {
                        return user.setChatState(state);
                    } else {
                        return false;
                    }
                } else {
                    return c.setIncomingChatState(state);
                }
            }
        }
        return false;
    }

    private Message parseOtrChat(String body, Jid from, String id, Conversation conversation) {
        String presence;
        if (from.isBareJid()) {
            presence = "";
        } else {
            presence = from.getResource();
        }
        if (body.matches("^\\?OTRv\\d{1,2}\\?.*")) {
            conversation.endOtrIfNeeded();
        }
        if (!conversation.hasValidOtrSession()) {
            conversation.startOtrSession(presence, false);
        } else {
            String foreignPresence = conversation.getOtrSession().getSessionID().getUserID();
            if (!foreignPresence.equals(presence)) {
                conversation.endOtrIfNeeded();
                conversation.startOtrSession(presence, false);
            }
        }
        try {
            conversation.setLastReceivedOtrMessageId(id);
            Session otrSession = conversation.getOtrSession();
            body = otrSession.transformReceiving(body);
            SessionStatus status = otrSession.getSessionStatus();
            if (body == null && status == SessionStatus.ENCRYPTED) {
                mXmppConnectionService.onOtrSessionEstablished(conversation);
                return null;
            } else if (body == null && status == SessionStatus.FINISHED) {
                conversation.resetOtrSession();
                mXmppConnectionService.updateConversationUi();
                return null;
            } else if (body == null || (body.isEmpty())) {
                return null;
            }
            if (body.startsWith(CryptoHelper.FILETRANSFER)) {
                String key = body.substring(CryptoHelper.FILETRANSFER.length());
                conversation.setSymmetricKey(CryptoHelper.hexToBytes(key));
                return null;
            }
            if (clientMightSendHtml(conversation.getAccount(), from)) {
                Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": received OTR message from bad behaving client. escaping HTML…");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    body = Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString();
                } else {
                    body = Html.fromHtml(body).toString();
                }
            }

            final OtrService otrService = conversation.getAccount().getOtrService();
            Message finishedMessage = new Message(conversation, body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
            finishedMessage.setFingerprint(otrService.getFingerprint(otrSession.getRemotePublicKey()));
            conversation.setLastReceivedOtrMessageId(null);

            return finishedMessage;
        } catch (Exception e) {
            conversation.resetOtrSession();
            return null;
        }
    }


    private Message parseAxolotlChat(Element axolotlMessage, Jid from, Conversation conversation, int status, final boolean checkedForDuplicates, boolean postpone) {
        final AxolotlService service = conversation.getAccount().getAxolotlService();
        final XmppAxolotlMessage xmppAxolotlMessage;
        try {
            xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.asBareJid());
        } catch (Exception e) {
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": invalid omemo message received " + e.getMessage());
            return null;
        }
        if (xmppAxolotlMessage.hasPayload()) {
            final XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage;
            try {
                plaintextMessage = service.processReceivingPayloadMessage(xmppAxolotlMessage, postpone);
            } catch (BrokenSessionException e) {
                if (checkedForDuplicates) {
                    if (service.trustedOrPreviouslyResponded(from.asBareJid())) {
                        service.reportBrokenSessionException(e, postpone);
                        return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    } else {
                        Log.d(Config.LOGTAG, "ignoring broken session exception because contact was not trusted");
                        return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    }
                } else {
                    Log.d(Config.LOGTAG, "ignoring broken session exception because checkForDuplicates failed");
                    return null;
                }
            } catch (NotEncryptedForThisDeviceException e) {
                return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE, status);
            } catch (OutdatedSenderException e) {
                return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
            }
            if (plaintextMessage != null) {
                Message finishedMessage = new Message(conversation, plaintextMessage.getPlaintext(), Message.ENCRYPTION_AXOLOTL, status);
                finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount()) + " Received Message with session fingerprint: " + plaintextMessage.getFingerprint());
                return finishedMessage;
            }
        } else {
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": received OMEMO key transport message");
            service.processReceivingKeyTransportMessage(xmppAxolotlMessage, postpone);
        }
        return null;
    }

    private Invite extractInvite(final Element message) {
        final Element mucUser = message.findChild("x", Namespace.MUC_USER);
        if (mucUser != null) {
            final Element invite = mucUser.findChild("invite");
            if (invite != null) {
                final String password = mucUser.findChildContent("password");
                final Jid from = InvalidJid.getNullForInvalid(invite.getAttributeAsJid("from"));
                final Jid to = InvalidJid.getNullForInvalid(invite.getAttributeAsJid("to"));
                if (to != null && from == null) {
                    Log.d(Config.LOGTAG,"do not parse outgoing mediated invite "+message);
                    return null;
                }
                final Jid room = InvalidJid.getNullForInvalid(message.getAttributeAsJid("from"));
                if (room == null) {
                    return null;
                }
                return new Invite(room, password, false, from);
            }
        }
        final Element conference = message.findChild("x", "jabber:x:conference");
        if (conference != null) {
            Jid from = InvalidJid.getNullForInvalid(message.getAttributeAsJid("from"));
            Jid room = InvalidJid.getNullForInvalid(conference.getAttributeAsJid("jid"));
            if (room == null) {
                return null;
            }
            return new Invite(room, conference.getAttribute("password"), true, from);
        }
        return null;
    }

    private void parseEvent(final Element event, final Jid from, final Account account) {
        Element items = event.findChild("items");
        String node = items == null ? null : items.getAttribute("node");
        if ("urn:xmpp:avatar:metadata".equals(node)) {
            Avatar avatar = Avatar.parseMetadata(items);
            if (avatar != null) {
                avatar.owner = from.asBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (account.getJid().asBareJid().equals(from)) {
                        if (account.setAvatar(avatar.getFilename())) {
                            mXmppConnectionService.databaseBackend.updateAccount(account);
                            mXmppConnectionService.notifyAccountAvatarHasChanged(account);
                        }
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else {
                        final Contact contact = account.getRoster().getContact(from);
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
        } else if (Namespace.NICK.equals(node)) {
            final Element i = items.findChild("item");
            final String nick = i == null ? null : i.findChildContent("nick", Namespace.NICK);
            if (nick != null) {
                setNick(account, from, nick);
            }
        } else if (AxolotlService.PEP_DEVICE_LIST.equals(node)) {
            Element item = items.findChild("item");
            Set<Integer> deviceIds = mXmppConnectionService.getIqParser().deviceIds(item);
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received PEP device list " + deviceIds + " update from " + from + ", processing... ");
            AxolotlService axolotlService = account.getAxolotlService();
            axolotlService.registerDevices(from, deviceIds);
        } else if (Namespace.BOOKMARKS.equals(node) && account.getJid().asBareJid().equals(from)) {
            if (account.getXmppConnection().getFeatures().bookmarksConversion()) {
                final Element i = items.findChild("item");
                final Element storage = i == null ? null : i.findChild("storage", Namespace.BOOKMARKS);
                Map<Jid, Bookmark> bookmarks = Bookmark.parseFromStorage(storage, account);
                mXmppConnectionService.processBookmarksInitial(account, bookmarks, true);
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": processing bookmark PEP event");
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring bookmark PEP event because bookmark conversion was not detected");
            }
        } else if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            final Element item = items.findChild("item");
            final Element retract = items.findChild("retract");
            if (item != null) {
                final Bookmark bookmark = Bookmark.parseFromItem(item, account);
                if (bookmark != null) {
                    account.putBookmark(bookmark);
                    mXmppConnectionService.processModifiedBookmark(bookmark);
                    mXmppConnectionService.updateConversationUi();
                }
            }
            if (retract != null) {
                final Jid id = InvalidJid.getNullForInvalid(retract.getAttributeAsJid("id"));
                if (id != null) {
                    account.removeBookmark(id);
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted bookmark for " + id);
                    mXmppConnectionService.processDeletedBookmark(account, id);
                    mXmppConnectionService.updateConversationUi();
                }
            }
        } else if (Config.MESSAGE_DISPLAYED_SYNCHRONIZATION
                && Namespace.MDS_DISPLAYED.equals(node)
                && account.getJid().asBareJid().equals(from)) {
            final Element item = items.findChild("item");
            mXmppConnectionService.processMdsItem(account, item);
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + " received pubsub notification for node="
                            + node);
        }
    }

    private void parseDeleteEvent(final Element event, final Jid from, final Account account) {
        final Element delete = event.findChild("delete");
        final String node = delete == null ? null : delete.getAttribute("node");
        if (Namespace.NICK.equals(node)) {
            Log.d(Config.LOGTAG, "parsing nick delete event from " + from);
            setNick(account, from, null);
        } else if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            account.setBookmarks(Collections.emptyMap());
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted bookmarks node");
        } else if (Namespace.AVATAR_METADATA.equals(node) && account.getJid().asBareJid().equals(from)) {
            Log.d(Config.LOGTAG,account.getJid().asBareJid()+": deleted avatar metadata node");
        }
    }

    private void parsePurgeEvent(final Element event, final Jid from, final Account account) {
        final Element purge = event.findChild("purge");
        final String node = purge == null ? null : purge.getAttribute("node");
        if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            account.setBookmarks(Collections.emptyMap());
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": purged bookmarks");
        }
    }

    private void setNick(Account account, Jid user, String nick) {
        if (user.asBareJid().equals(account.getJid().asBareJid())) {
            account.setDisplayName(nick);
        } else {
            Contact contact = account.getRoster().getContact(user);
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.syncRoster(account);
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
        mXmppConnectionService.updateConversationUi();
        mXmppConnectionService.updateAccountUi();
    }

    private boolean handleErrorMessage(final Account account, final MessagePacket packet) {
        if (packet.getType() == MessagePacket.TYPE_ERROR) {
            if (packet.fromServer(account)) {
                final Pair<MessagePacket, Long> forwarded = packet.getForwardedMessagePacket("received", Namespace.CARBONS);
                if (forwarded != null) {
                    return handleErrorMessage(account, forwarded.first);
                }
            }
            final Jid from = packet.getFrom();
            final String id = packet.getId();
            if (from != null && id != null) {
                final Message message = mXmppConnectionService.markMessage(account,
                        from.asBareJid(),
                        packet.getId(),
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                    final String sessionId = id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                    mXmppConnectionService.getJingleConnectionManager()
                            .updateProposedSessionDiscovered(account, from, sessionId, JingleConnectionManager.DeviceDiscoveryState.FAILED);
                    return true;
                }
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX)) {
                    final String sessionId = id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX.length());
                    mXmppConnectionService.getJingleConnectionManager().failProceed(account, from, sessionId, extractErrorMessage(packet)); //TODO: Test it and check this changes again!
                    return true;
                }
                mXmppConnectionService.markMessage(account,
                        from.asBareJid(),
                        id,
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                final Element error = packet.findChild("error");
                final boolean pingWorthyError = error != null && (error.hasChild("not-acceptable") || error.hasChild("remote-server-timeout") || error.hasChild("remote-server-not-found"));
                if (pingWorthyError) {
                    Conversation conversation = mXmppConnectionService.find(account, from);
                    if (conversation != null && conversation.getMode() == Conversational.MODE_MULTI) {
                        if (conversation.getMucOptions().online()) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received ping worthy error for seemingly online muc at " + from);
                            mXmppConnectionService.mucSelfPingAndRejoin(conversation);
                        }
                    }
                }

                if (message != null) {
                    if (message.getEncryption() == Message.ENCRYPTION_OTR) {
                        Conversation conversation = (Conversation) message.getConversation();
                        conversation.endOtrIfNeeded();
                    }
                }


            }
            return true;
        }
        return false;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket original) {
        if (handleErrorMessage(account, original)) {
            return;
        }
        final MessagePacket packet;
        Long timestamp = null;
        final boolean isForwarded;
        boolean isCarbon = false;
        String serverMsgId = null;
        final Element fin = original.findChild("fin", MessageArchiveService.Version.MAM_0.namespace);
        if (fin != null) {
            mXmppConnectionService.getMessageArchiveService().processFinLegacy(fin, original.getFrom());
            return;
        }
        final Element result = MessageArchiveService.Version.findResult(original);
        final String queryId = result == null ? null : result.getAttribute("queryid");
        final MessageArchiveService.Query query = queryId == null ? null : mXmppConnectionService.getMessageArchiveService().findQuery(queryId);
        final boolean offlineMessagesRetrieved = account.getXmppConnection().isOfflineMessagesRetrieved();
        if (query != null && query.validFrom(original.getFrom())) {
            final Pair<MessagePacket, Long> f = original.getForwardedMessagePacket("result", query.version.namespace);
            if (f == null) {
                return;
            }
            timestamp = f.second;
            packet = f.first;
            isForwarded = true;
            serverMsgId = result.getAttribute("id");
            query.incrementMessageCount();
            if (handleErrorMessage(account, packet)) {
                return;
            }
            final var contact = packet.getFrom() == null || packet.getFrom() instanceof InvalidJid ? null : account.getRoster().getContact(packet.getFrom());
            if (contact != null && contact.isBlocked()) {
                Log.d(Config.LOGTAG, "Got MAM result from blocked contact, ignoring...");
                return;
            }
        } else if (query != null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received mam result with invalid from (" + original.getFrom() + ") or queryId (" + queryId + ")");
            return;
        } else if (original.fromServer(account)) {
            Pair<MessagePacket, Long> f;
            f = original.getForwardedMessagePacket("received", Namespace.CARBONS);
            f = f == null ? original.getForwardedMessagePacket("sent", Namespace.CARBONS) : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
            isForwarded = isCarbon;
        } else {
            packet = original;
            isForwarded = false;
        }

        if (timestamp == null) {
            timestamp = AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }
        final Element mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        final String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");
        Element replaceElement = packet.findChild("replace", "urn:xmpp:message-correct:0");
        Set<Message.FileParams> attachments = new LinkedHashSet<>();
        for (Element child : packet.getChildren()) {
            // SIMS first so they get preference in the set
            if (child.getName().equals("reference") && child.getNamespace().equals("urn:xmpp:reference:0")) {
                if (child.findChild("media-sharing", "urn:xmpp:sims:1") != null) {
                    attachments.add(new Message.FileParams(child));
                }
            }
        }
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("x") && child.getNamespace().equals(Namespace.OOB)) {
                attachments.add(new Message.FileParams(child));
            }
        }
        String replacementId = replaceElement == null ? null : replaceElement.getAttribute("id");
        if (replacementId == null) {
            final Element fasten = packet.findChild("apply-to", "urn:xmpp:fasten:0");
            if (fasten != null) {
                replaceElement = fasten.findChild("retract", "urn:xmpp:message-retract:0");
                if (replaceElement == null) replaceElement = fasten.findChild("moderated", "urn:xmpp:message-moderate:0");
                if (replaceElement != null) {
                    final String reason = replaceElement.findChildContent("reason", "urn:xmpp:message-moderate:0");
                    replacementId = fasten.getAttribute("id");
                    packet.setBody(reason == null ? "" : reason);
                }
            }
        }
        final Element applyToElement = packet.findChild("apply-to", "urn:xmpp:fasten:0");
        final String retractId = applyToElement != null && applyToElement.findChild("retract", "urn:xmpp:message-retract:0") != null ? applyToElement.getAttribute("id") : null;

        if (packet.getBody() == null && retractId != null) {   //It's RECOMMENDED that you include a Fallback Indication (XEP-0428) [6] tag with fallback text in the <body/>, so that older clients can still indicate the intent to retract and so that older servers will archive the retraction.
            //Otherwhise the following code will not execute the retraction, because it searchs for body content!
            packet.setBody("This person attempted to retract a previous message, but it's unsupported by your client.");
        }

        LocalizedContent body = packet.getBody();

        final Element axolotlEncrypted = packet.findChildEnsureSingle(XmppAxolotlMessage.CONTAINERTAG, AxolotlService.PEP_PREFIX);
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
        final String remoteMsgId;
        if (originId != null && originId.getAttribute("id") != null) {
            remoteMsgId = originId.getAttribute("id");
        } else {
            remoteMsgId = packet.getId();
        }
        boolean notify = false;
        Element html = packet.findChild("html", "http://jabber.org/protocol/xhtml-im");
        if (html != null && html.findChild("body", "http://www.w3.org/1999/xhtml") == null) {
            html = null;
        }
        if (from == null || !InvalidJid.isValid(from) || !InvalidJid.isValid(to)) {
            Log.e(Config.LOGTAG, "encountered invalid message from='" + from + "' to='" + to + "'");
            return;
        }

        boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        if (query != null && !query.muc() && isTypeGroupChat) {
            Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": received room (" + from + ") message on regular MAM request. skipping");
            return;
        }
        boolean isProperlyAddressed = (to != null) && (!to.isBareJid() || account.countPresences() == 0);
        boolean isMucStatusMessage = InvalidJid.hasValidFrom(packet) && from.isBareJid() && mucUserElement != null && mucUserElement.hasChild("status");
        boolean selfAddressed;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            selfAddressed = to == null || account.getJid().asBareJid().equals(to.asBareJid());
            if (selfAddressed) {
                counterpart = from;
            } else {
                counterpart = to;
            }
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
            selfAddressed = false;
        }

        final Invite invite = extractInvite(packet);
        if (invite != null) {
            if (invite.jid.asBareJid().equals(account.getJid().asBareJid())) {
                Log.d(Config.LOGTAG,account.getJid().asBareJid()+": ignore invite to "+invite.jid+" because it matches account");
            } else if (isTypeGroupChat) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring invite to " + invite.jid + " because it was received as group chat");
            } else if (invite.direct && (mucUserElement != null || invite.inviter == null || mXmppConnectionService.isMuc(account, invite.inviter))) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring direct invite to " + invite.jid + " because it was received in MUC");
            } else {
                invite.execute(account, packet.getBody());
                return;
            }
        }

        final Element reactions = packet.findChild("reactions", "urn:xmpp:reactions:0");
        if (body == null && html == null) {
            if (reactions != null && reactions.getAttribute("id") != null) {
                final Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
                if (conversation != null) {
                    final Message reactionTo = conversation.findMessageWithRemoteIdAndCounterpart(reactions.getAttribute("id"), null);
                    if (reactionTo != null) {
                        String bodyS = reactionTo.reply().getBody();
                        for (Element el : reactions.getChildren()) {
                            if (el.getName().equals("reaction") && el.getNamespace().equals("urn:xmpp:reactions:0")) {
                                bodyS += el.getContent();
                            }
                        }
                        body = new LocalizedContent(bodyS, "en", 1);
                        final Message previousReaction = conversation.findMessageReactingTo(reactions.getAttribute("id"), counterpart);
                        if (previousReaction != null) replacementId = previousReaction.replyId();
                    }
                }
            }
        }

        final boolean conversationIsProbablyMuc = isTypeGroupChat || mucUserElement != null || account.getXmppConnection().getMucServersWithholdAccount().contains(counterpart.getDomain().toEscapedString());
        final Element webxdc = packet.findChild("x", "urn:xmpp:webxdc:0");
        final Element thread = packet.findChild("thread");
        if (webxdc != null && thread != null) {
            final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), conversationIsProbablyMuc, false, query, false);
            Jid webxdcSender = counterpart.asBareJid();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if(conversation.getMucOptions().nonanonymous()) {
                    webxdcSender = conversation.getMucOptions().getTrueCounterpart(counterpart);
                } else {
                    webxdcSender = counterpart;
                }
            }
            final var document = webxdc.findChildContent("document", "urn:xmpp:webxdc:0");
            final var summary = webxdc.findChildContent("summary", "urn:xmpp:webxdc:0");
            final var payload = webxdc.findChildContent("json", "urn:xmpp:json:0");
            if (document != null || summary != null || payload != null) {
                mXmppConnectionService.insertWebxdcUpdate(new WebxdcUpdate(
                        conversation,
                        remoteMsgId,
                        counterpart,
                        thread,
                        body == null ? null : body.content,
                        document,
                        summary,
                        payload
                ));
            }

            final var realtime = webxdc.findChildContent("data", "urn:xmpp:webxdc:0");
            if (realtime != null) conversation.webxdcRealtimeData(thread, realtime);

            mXmppConnectionService.updateConversationUi();
        }

        if ((body != null || pgpEncrypted != null || (axolotlEncrypted != null && axolotlEncrypted.hasChild("payload")) || !attachments.isEmpty() || html != null || (packet.hasChild("subject") && packet.hasChild("thread"))) && !isMucStatusMessage) {
            final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), conversationIsProbablyMuc, false, query, false);
            final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;

            if (serverMsgId == null) {
                serverMsgId = extractStanzaId(packet, isTypeGroupChat, conversation);
            }

            if (selfAddressed) {
                if (mXmppConnectionService.markMessage(conversation, remoteMsgId, Message.STATUS_SEND_RECEIVED, serverMsgId)) {
                    return;
                }
                status = Message.STATUS_RECEIVED;
                if (remoteMsgId != null && conversation.findMessageWithRemoteId(remoteMsgId, counterpart) != null) {
                    return;
                }
            }

            if (isTypeGroupChat) {
                if (conversation.getMucOptions().isSelf(counterpart)) {
                    status = Message.STATUS_SEND_RECEIVED;
                    isCarbon = true; //not really carbon but received from another resource
                    if (mXmppConnectionService.markMessage(conversation, remoteMsgId, status, serverMsgId, body, html, packet.findChildContent("subject"), packet.findChild("thread"), attachments)) {
                        return;
                    } else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
                        if (body != null) {
                            Message message = conversation.findSentMessageWithBody(body.content);
                            if (message != null) {
                                mXmppConnectionService.markMessage(message, status);
                                return;
                            }
                        }
                    }
                } else {
                    status = Message.STATUS_RECEIVED;
                }
            }
            final Message message;
            if (body != null && body.content.startsWith("?OTR") && Config.supportOtr()) {
                if (!isForwarded && !isTypeGroupChat && isProperlyAddressed && !conversationMultiMode) {
                    message = parseOtrChat(body.content, from, remoteMsgId, conversation);
                    if (message == null) {
                        return;
                    }
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring OTR message from " + from + " isForwarded=" + Boolean.toString(isForwarded) + ", isProperlyAddressed=" + Boolean.valueOf(isProperlyAddressed));
                    message = new Message(conversation, body.content, Message.ENCRYPTION_NONE, status);
                    if (body.count > 1) {
                        message.setBodyLanguage(body.language);
                    }
                }
            } else if (pgpEncrypted != null && Config.supportOpenPgp()) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlEncrypted != null && Config.supportOmemo()) {
                Jid origin;
                Set<Jid> fallbacksBySourceId = Collections.emptySet();
                if (conversationMultiMode) {
                    final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                    origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                    if (origin == null) {
                        try {
                            fallbacksBySourceId = account.getAxolotlService().findCounterpartsBySourceId(XmppAxolotlMessage.parseSourceId(axolotlEncrypted));
                        } catch (IllegalArgumentException e) {
                            //ignoring
                        }
                    }
                    if (origin == null && fallbacksBySourceId.size() == 0) {
                        Log.d(Config.LOGTAG, "axolotl message in anonymous conference received and no possible fallbacks");
                        return;
                    }
                } else {
                    fallbacksBySourceId = Collections.emptySet();
                    origin = from;
                }

                final boolean liveMessage = query == null && !isTypeGroupChat && mucUserElement == null;
                final boolean checkedForDuplicates = liveMessage || (serverMsgId != null && remoteMsgId != null && !conversation.possibleDuplicate(serverMsgId, remoteMsgId));

                if (origin != null) {
                    message = parseAxolotlChat(axolotlEncrypted, origin, conversation, status, checkedForDuplicates, query != null);
                } else {
                    Message trial = null;
                    for (Jid fallback : fallbacksBySourceId) {
                        trial = parseAxolotlChat(axolotlEncrypted, fallback, conversation, status, checkedForDuplicates && fallbacksBySourceId.size() == 1, query != null);
                        if (trial != null) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": decoded muc message using fallback");
                            origin = fallback;
                            break;
                        }
                    }
                    message = trial;
                }
                if (message == null) {
                    if (query == null && extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet)) {
                        mXmppConnectionService.updateConversationUi();
                    }
                    if (query != null && status == Message.STATUS_SEND && remoteMsgId != null) {
                        Message previouslySent = conversation.findSentMessageWithUuid(remoteMsgId);
                        if (previouslySent != null && previouslySent.getServerMsgId() == null && serverMsgId != null) {
                            previouslySent.setServerMsgId(serverMsgId);
                            mXmppConnectionService.databaseBackend.updateMessage(previouslySent, false);
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": encountered previously sent OMEMO message without serverId. updating...");
                        }
                    }
                    return;
                }
                if (conversationMultiMode) {
                    message.setTrueCounterpart(origin);
                }
            } else if (body == null && !attachments.isEmpty()) {
                message = new Message(conversation, "", Message.ENCRYPTION_NONE, status);
            } else {
                message = new Message(conversation, body == null ? null : body.content, Message.ENCRYPTION_NONE, status);
                if (body != null && body.count > 1) {
                    message.setBodyLanguage(body.language);
                }
            }

            Element addresses = packet.findChild("addresses", "http://jabber.org/protocol/address");
            if (status == Message.STATUS_RECEIVED && addresses != null) {
                for (Element address : addresses.getChildren()) {
                    if (!address.getName().equals("address") || !address.getNamespace().equals("http://jabber.org/protocol/address")) continue;

                    if (address.getAttribute("type").equals("ofrom") && address.getAttribute("jid") != null) {
                        Jid ofrom = address.getAttributeAsJid("jid");
                        if (InvalidJid.isValid(ofrom) && ofrom.getDomain().equals(counterpart.getDomain()) &&
                                conversation.getAccount().getRoster().getContact(counterpart.getDomain()).getPresences().anySupport("http://jabber.org/protocol/address")) {

                            message.setTrueCounterpart(ofrom);
                        }
                    }
                }
            }
            if (html != null) message.addPayload(html);
            message.setSubject(packet.findChildContent("subject"));
            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);
            if (!attachments.isEmpty()) {
                message.setFileParams(attachments.iterator().next());
                if (CryptoHelper.isPgpEncryptedUrl(message.getFileParams().url)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            }
            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
            if (reactions != null) message.addPayload(reactions);
            for (Element el : packet.getChildren()) {
                if ((el.getName().equals("query") && el.getNamespace().equals("http://jabber.org/protocol/disco#items") && el.getAttribute("node").equals("http://jabber.org/protocol/commands")) ||
                        (el.getName().equals("fallback") && el.getNamespace().equals("urn:xmpp:fallback:0"))) {
                    message.addPayload(el);
                }
                if (el.getName().equals("thread") && (el.getNamespace() == null || el.getNamespace().equals("jabber:client"))) {
                    el.setAttribute("xmlns", "jabber:client");
                    message.addPayload(el);
                }
                if (el.getName().equals("reply") && el.getNamespace() != null && el.getNamespace().equals("urn:xmpp:reply:0")) {
                    message.addPayload(el);
                    try {
                        for (final var parent : mXmppConnectionService.getMessageFuzzyIds(conversation, List.of(el.getAttribute("id"))).entrySet()) {
                            message.setInReplyTo(parent.getValue());
                        }
                    } catch (NullPointerException nullPointerException) {
                        Log.d("NullPointerException", "Null");
                    }
                }
                if (el.getName().equals("attention") && el.getNamespace() != null && el.getNamespace().equals("urn:xmpp:attention:0")) {
                    message.addPayload(el);
                }
            }
            if (conversationMultiMode) {
                message.setMucUser(conversation.getMucOptions().findUserByFullJid(counterpart));
                final Element occupantId = packet.findChild("occupant-id", "urn:xmpp:occupant-id:0");
                if (occupantId != null) message.setOccupantId(occupantId.getAttribute("id"));
                final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                Jid trueCounterpart;
                if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                    trueCounterpart = message.getTrueCounterpart();
                } else if (query != null && query.safeToExtractTrueCounterpart()) {
                    trueCounterpart = getTrueCounterpart(mucUserElement, fallback);
                } else {
                    trueCounterpart = fallback;
                }
                if (trueCounterpart != null && isTypeGroupChat) {
                    if (trueCounterpart.asBareJid().equals(account.getJid().asBareJid())) {
                        status = isTypeGroupChat ? Message.STATUS_SEND_RECEIVED : Message.STATUS_SEND;
                    } else {
                        status = Message.STATUS_RECEIVED;
                        message.setCarbon(false);
                    }
                }
                message.setStatus(status);
                message.setTrueCounterpart(trueCounterpart);
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            } else {
                updateLastseen(account, from);
            }

            if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
                Message replacedMessage = conversation.findMessageWithRemoteIdAndCounterpart(replacementId, counterpart);

                if (replacedMessage == null) {
                    replacedMessage = conversation.findSentMessageWithUuidOrRemoteId(replacementId, true, true);
                }
                if (replacedMessage != null) {
                    final boolean fingerprintsMatch = replacedMessage.getFingerprint() == null
                            || replacedMessage.getFingerprint().equals(message.getFingerprint());
                    final boolean trueCountersMatch = replacedMessage.getTrueCounterpart() != null
                            && message.getTrueCounterpart() != null
                            && replacedMessage.getTrueCounterpart().asBareJid().equals(message.getTrueCounterpart().asBareJid());
                    final boolean mucUserMatches = query == null && replacedMessage.sameMucUser(message); //can not be checked when using mam
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    if (fingerprintsMatch && (trueCountersMatch || !conversationMultiMode || mucUserMatches || counterpart.isBareJid()) && !duplicate) {
                        Log.d(Config.LOGTAG, "replaced message '" + replacedMessage.getBody() + "' with '" + message.getBody() + "'");
                        synchronized (replacedMessage) {
                            final String uuid = replacedMessage.getUuid();

                            replacedMessage.putEdited(replacedMessage.getRemoteMsgId() != null ? replacedMessage.getRemoteMsgId() : replacedMessage.getUuid(), replacedMessage.getServerMsgId(), replacedMessage.getBody(), replacedMessage.getTimeSent());

                            replacedMessage.setUuid(UUID.randomUUID().toString());
                            replacedMessage.setBody(message.getBody());
                            replacedMessage.setSubject(message.getSubject());
                            replacedMessage.setThread(message.getThread());
                            replacedMessage.setRemoteMsgId(remoteMsgId);
                            if (replaceElement != null && !replaceElement.getName().equals("replace")) {
                                mXmppConnectionService.getFileBackend().deleteFile(replacedMessage);
                                mXmppConnectionService.evictPreview(message.getUuid());
                                List<Element> thumbs = replacedMessage.getFileParams() != null ? replacedMessage.getFileParams().getThumbnails() : null;
                                if (thumbs != null && !thumbs.isEmpty()) {
                                    for (Element thumb : thumbs) {
                                        Uri uri = Uri.parse(thumb.getAttribute("uri"));
                                        if (uri.getScheme().equals("cid")) {
                                            Cid cid = BobTransfer.cid(uri);
                                            if (cid == null) continue;
                                            DownloadableFile f = mXmppConnectionService.getFileForCid(cid);
                                            if (f != null) {
                                                mXmppConnectionService.evictPreview(f);
                                                f.delete();
                                            }
                                        }
                                    }
                                }
                                replacedMessage.clearPayloads();
                                replacedMessage.setFileParams(null);
                                replacedMessage.addPayload(replaceElement);
                            }
                            if (replacedMessage.getServerMsgId() == null || message.getServerMsgId() != null) {
                                replacedMessage.setServerMsgId(message.getServerMsgId());
                            }
                            replacedMessage.setEncryption(message.getEncryption());
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
                                replacedMessage.markUnread();
                            }
                            extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet);
                            mXmppConnectionService.updateMessage(replacedMessage, uuid);
                            if (mXmppConnectionService.confirmMessages()
                                    && replacedMessage.getStatus() == Message.STATUS_RECEIVED
                                    && (replacedMessage.trusted() || replacedMessage.isPrivateMessage()) //TODO do we really want to send receipts for all PMs?
                                    && remoteMsgId != null
                                    && !selfAddressed
                                    && !isTypeGroupChat) {
                                processMessageReceipts(account, packet, remoteMsgId, query);
                            }
                            if (replacedMessage.getEncryption() == Message.ENCRYPTION_PGP) {
                                conversation.getAccount().getPgpDecryptionService().discard(replacedMessage);
                                conversation.getAccount().getPgpDecryptionService().decrypt(replacedMessage, false);
                            }
                        }
                        mXmppConnectionService.getNotificationService().updateNotification();
                        return;
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received message correction but verification didn't check out");
                    }
                } else if (message.getBody() == null || message.getBody().equals("") || message.getBody().equals(" ")) {
                    return;
                }
                else if (message.getBody() == null || message.getBody().equals("") || message.getBody().equals(" ")) {
                    return;
                }
            } else if (replacementId != null && !mXmppConnectionService.allowMessageCorrection() && (message.hasDeletedBody())) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received deleted message but LMC is deactivated");
                return;
            }

            if (retractId != null && mXmppConnectionService.allowMessageRetraction()) {
                final Message retractedMessage = conversation.findSentMessageWithUuidOrRemoteId(retractId, true, true);
                if (retractedMessage != null) {
                    final boolean fingerprintsMatch = retractedMessage.getFingerprint() == null
                            || message.getFingerprint() == null
                            || retractedMessage.getFingerprint().equals(message.getFingerprint());
                    final boolean trueCountersMatch = retractedMessage.getTrueCounterpart() != null
                            && message.getTrueCounterpart() != null
                            && retractedMessage.getTrueCounterpart().asBareJid().equals(message.getTrueCounterpart().asBareJid());
                    final boolean mucUserMatches = query == null && retractedMessage.sameMucUser(message); //can not be checked when using mam
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    List<Account> lAcc = mXmppConnectionService.getAccounts();
                    boolean activeSelf = false;
                    if (message.getTrueCounterpart() != null) {
                        for (Account a : lAcc) {
                            if (a.getJid() != null && a.isOnlineAndConnected() && a.getJid().asBareJid().equals(message.getTrueCounterpart().asBareJid())) {
                                activeSelf = true;
                                break;
                            }
                        }
                    }
                    if (fingerprintsMatch && ((trueCountersMatch || !conversationMultiMode || mucUserMatches || (isCarbon && activeSelf) && !duplicate) || conversationMultiMode)) {
                        Log.d(Config.LOGTAG, "retracted message '" + retractedMessage.getBody() + "' with '" + message.getBody() + "'");
                        synchronized (retractedMessage) {

                            retractedMessage.setBody(mXmppConnectionService.getString(R.string.message_deleted));
                            retractedMessage.setRetractId(retractId);

                            extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet);

                            message.setMessageDeleted(true);
                            message.setRetractId(retractId);

                            if (message.getStatus() > Message.STATUS_RECEIVED) {
                                retractedMessage.setMessageDeleted(true);
                            }

                            for (Edit itm : retractedMessage.getEditedList()) {
                                Message tmpRetractedMessage = conversation.findMessageWithUuidOrRemoteId(itm.getEditedId());
                                if (tmpRetractedMessage != null) {
                                    tmpRetractedMessage.setRetractId(retractId);
                                    mXmppConnectionService.updateMessage(tmpRetractedMessage, tmpRetractedMessage.getUuid());
                                }
                            }
                            mXmppConnectionService.updateMessage(retractedMessage, retractedMessage.getUuid());
                            mXmppConnectionService.databaseBackend.createMessage(message);
                            if (mXmppConnectionService.confirmMessages()
                                    && retractedMessage.getStatus() == Message.STATUS_RECEIVED
                                    && (retractedMessage.trusted() || retractedMessage.isPrivateMessage()) //TODO do we really want to send receipts for all PMs?
                                    && remoteMsgId != null
                                    && !selfAddressed
                                    && !isTypeGroupChat) {
                                processMessageReceipts(account, packet, remoteMsgId, query);
                            }
                        }
                        mXmppConnectionService.getNotificationService().updateNotification();
                        return;
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received message retraction but checks are not valid");
                    }
                } else {
                    //we deleted a carbon from ourself and the dialog allready removed it from ui
                    message.setMessageDeleted(true);
                    message.setRetractId(retractId);
                    mXmppConnectionService.databaseBackend.createMessage(message);
                    return;
                }
                if (replaceElement != null && !replaceElement.getName().equals("replace")) return;
            }

            boolean checkForDuplicates = (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                    || message.isPrivateMessage()
                    || message.getServerMsgId() != null
                    || (query == null && mXmppConnectionService.getMessageArchiveService().isCatchupInProgress(conversation));
            if (checkForDuplicates) {
                final Message duplicate = conversation.findDuplicateMessage(message);
                if (duplicate != null) {
                    final boolean serverMsgIdUpdated;
                    if (duplicate.getStatus() != Message.STATUS_RECEIVED
                            && duplicate.getUuid().equals(message.getRemoteMsgId())
                            && duplicate.getServerMsgId() == null
                            && message.getServerMsgId() != null) {
                        duplicate.setServerMsgId(message.getServerMsgId());
                        if (mXmppConnectionService.databaseBackend.updateMessage(duplicate, false)) {
                            serverMsgIdUpdated = true;
                        } else {
                            serverMsgIdUpdated = false;
                            Log.e(Config.LOGTAG, "failed to update message");
                        }
                    } else {
                        serverMsgIdUpdated = false;
                    }
                    Log.d(Config.LOGTAG, "skipping duplicate message with " + message.getCounterpart() + ". serverMsgIdUpdated=" + serverMsgIdUpdated);
                    return;
                }
            }

            if (query != null && query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
                conversation.prepend(query.getActualInThisQuery(), message);
            } else {
                conversation.add(message);
            }
            if (query != null) {
                query.incrementActualMessageCount();
            }

            if (query == null || query.isCatchup()) { //either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                notify = conversation.getAccount().getPgpDecryptionService().decrypt(message, notify);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE || message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                notify = false;
            }

            if (query == null) {
                extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet);
                mXmppConnectionService.updateConversationUi();
            }

            if (mXmppConnectionService.confirmMessages()
                    && message.getStatus() == Message.STATUS_RECEIVED
                    && (message.trusted() || message.isPrivateMessage())
                    && remoteMsgId != null
                    && !selfAddressed
                    && !isTypeGroupChat) {
                processMessageReceipts(account, packet, remoteMsgId, query);
            }

            if (message.getStatus() == Message.STATUS_RECEIVED
                    && conversation.getOtrSession() != null
                    && !conversation.getOtrSession().getSessionID().getUserID()
                    .equals(message.getCounterpart().getResource())) {
                conversation.endOtrIfNeeded();
            }


            if (message.getFileParams() != null) {
                for (Cid cid : message.getFileParams().getCids()) {
                    File f = mXmppConnectionService.getFileForCid(cid);
                    if (f != null && f.canRead()) {
                        message.setRelativeFilePath(f.getAbsolutePath());
                        mXmppConnectionService.getFileBackend().updateFileParams(message, null, false);
                        break;
                    }
                }
            }
            mXmppConnectionService.databaseBackend.createMessage(message);

            final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();

            if ((mXmppConnectionService.easyDownloader() || message.getRelativeFilePath() == null && message.trusted()) && message.treatAsDownloadable() && manager.getAutoAcceptFileSize() > 0) {
                if (message.getOob() != null && "cid".equalsIgnoreCase(message.getOob().getScheme())) {
                    try {
                        BobTransfer transfer = new BobTransfer.ForMessage(message, mXmppConnectionService);
                        message.setTransferable(transfer);
                        transfer.start();
                    } catch (URISyntaxException e) {
                        Log.d(Config.LOGTAG, "BobTransfer failed to parse URI");
                    }
                } else {
                    manager.createNewDownloadConnection(message);
                }
            } else if (notify) {
                if (query != null && query.isCatchup()) {
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                } else {
                    mXmppConnectionService.getNotificationService().push(message);
                }
            }
        } else if (!packet.hasChild("body")) { //no body
            final Conversation conversation = mXmppConnectionService.find(account, from.asBareJid());
            if (axolotlEncrypted != null) {
                Jid origin;
                if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                    final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                    origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                    if (origin == null) {
                        Log.d(Config.LOGTAG, "omemo key transport message in anonymous conference received");
                        return;
                    }
                } else if (isTypeGroupChat) {
                    return;
                } else {
                    origin = from;
                }
                try {
                    final XmppAxolotlMessage xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlEncrypted, origin.asBareJid());
                    account.getAxolotlService().processReceivingKeyTransportMessage(xmppAxolotlMessage, query != null);
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": omemo key transport message received from " + origin);
                } catch (Exception e) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": invalid omemo key transport message received " + e.getMessage());
                    return;
                }
            }

            if (query == null && extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet)) {
                mXmppConnectionService.updateConversationUi();
            }

            if (isTypeGroupChat) {
                if (packet.hasChild("subject") && !packet.hasChild("thread")) { // already know it has no body from above
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        final LocalizedContent subject = packet.findInternationalizedChildContentInDefaultNamespace("subject");
                        if (subject != null && conversation.getMucOptions().setSubject(subject.content)) {
                            mXmppConnectionService.updateConversation(conversation);
                        }
                        mXmppConnectionService.updateConversationUi();
                        return;
                    }
                }
            }
            if (conversation != null && mucUserElement != null && InvalidJid.hasValidFrom(packet) && from.isBareJid()) {
                for (Element child : mucUserElement.getChildren()) {
                    if ("status".equals(child.getName())) {
                        try {
                            int code = Integer.parseInt(child.getAttribute("code"));
                            if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
                                mXmppConnectionService.fetchConferenceConfiguration(conversation);
                                break;
                            }
                        } catch (Exception e) {
                            //ignored
                        }
                    } else if ("item".equals(child.getName())) {
                        MucOptions.User user = AbstractParser.parseItem(conversation, child);
                        Log.d(Config.LOGTAG, account.getJid() + ": changing affiliation for "
                                + user.getRealJid() + " to " + user.getAffiliation() + " in "
                                + conversation.getJid().asBareJid());
                        if (!user.realJidMatchesAccount()) {
                            boolean isNew = conversation.getMucOptions().updateUser(user);
                            mXmppConnectionService.getAvatarService().clear(conversation);
                            mXmppConnectionService.updateMucRosterUi();
                            mXmppConnectionService.updateConversationUi();
                            Contact contact = user.getContact();
                            if (!user.getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
                                Jid jid = user.getRealJid();
                                List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
                                if (cryptoTargets.remove(user.getRealJid())) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": removed " + jid + " from crypto targets of " + conversation.getName());
                                    conversation.setAcceptedCryptoTargets(cryptoTargets);
                                    mXmppConnectionService.updateConversation(conversation);
                                }
                            } else if (isNew
                                    && user.getRealJid() != null
                                    && conversation.getMucOptions().isPrivateAndNonAnonymous()
                                    && (contact == null || !contact.mutualPresenceSubscription())
                                    && account.getAxolotlService().hasEmptyDeviceList(user.getRealJid())) {
                                account.getAxolotlService().fetchDeviceIds(user.getRealJid());
                            }
                        }
                    }
                }
            }
            if (!isTypeGroupChat) {
                for (Element child : packet.getChildren()) {
                    if (Namespace.JINGLE_MESSAGE.equals(child.getNamespace()) && JINGLE_MESSAGE_ELEMENT_NAMES.contains(child.getName())) {
                        final String action = child.getName();
                        final String sessionId = child.getAttribute("id");
                        if (sessionId == null) {
                            break;
                        }
                        if (query == null && offlineMessagesRetrieved) {
                            if (serverMsgId == null) {
                                serverMsgId = extractStanzaId(account, packet);
                            }
                            mXmppConnectionService
                                    .getJingleConnectionManager()
                                    .deliverMessage(
                                            account,
                                            packet.getTo(),
                                            packet.getFrom(),
                                            child,
                                            remoteMsgId,
                                            serverMsgId,
                                            timestamp);
                            final Contact contact = account.getRoster().getContact(from);
                            if (mXmppConnectionService.confirmMessages()
                                    && !contact.isSelf()
                                    && remoteMsgId != null
                                    && contact.showInContactList()) {
                                processMessageReceipts(account, packet, remoteMsgId, null);
                            }
                        } else if ((query != null && query.isCatchup()) || !offlineMessagesRetrieved) {
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace =
                                        description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c =
                                            mXmppConnectionService.findOrCreateConversation(
                                                    account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage =
                                            c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message =
                                            new Message(
                                                    c, status, Message.TYPE_RTP_SESSION, sessionId);
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(false, 0).toString());
                                    c.add(message);
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            } else if ("proceed".equals(action)) {
                                // status needs to be flipped to find the original propose
                                final Conversation c =
                                        mXmppConnectionService.findOrCreateConversation(
                                                account, counterpart.asBareJid(), false, false);
                                final int s =
                                        packet.fromAccount(account)
                                                ? Message.STATUS_RECEIVED
                                                : Message.STATUS_SEND;
                                final Message message = c.findRtpSession(sessionId, s);
                                if (message != null) {
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (serverMsgId != null) {
                                        message.setServerMsgId(serverMsgId);
                                    }
                                    message.setTime(timestamp);
                                    mXmppConnectionService.updateMessage(message, true);
                                } else {
                                    Log.d(
                                            Config.LOGTAG,
                                            "unable to find original rtp session message for received propose");
                                }

                            } else if ("finish".equals(action)) {
                                Log.d(
                                        Config.LOGTAG,
                                        "received JMI 'finish' during MAM catch-up. Can be used to update success/failure and duration");
                            }
                        } else {
                            //MAM reloads (non catchups
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace = description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage = c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message = new Message(
                                            c,
                                            status,
                                            Message.TYPE_RTP_SESSION,
                                            sessionId
                                    );
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
                                        c.prepend(query.getActualInThisQuery(), message);
                                    } else {
                                        c.add(message);
                                    }
                                    query.incrementActualMessageCount();
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        if (received != null) {
            String id = received.getAttribute("id");
            if (packet.fromAccount(account)) {
                if (query != null && id != null && packet.getTo() != null) {
                    query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
                }
            } else if (id != null) {
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                    final String sessionId = id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                    mXmppConnectionService.getJingleConnectionManager()
                            .updateProposedSessionDiscovered(account, from, sessionId, JingleConnectionManager.DeviceDiscoveryState.DISCOVERED);
                } else {
                    mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_RECEIVED);
                }
            }
        }
        final Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            final String id = displayed.getAttribute("id");
            final Jid sender = InvalidJid.getNullForInvalid(displayed.getAttributeAsJid("sender"));
            if (packet.fromAccount(account) && !selfAddressed) {
                final Conversation c =
                        mXmppConnectionService.find(account, counterpart.asBareJid());
                final Message message =
                        (c == null || id == null) ? null : c.findReceivedWithRemoteId(id);
                if (message != null && (query == null || query.isCatchup())) {
                    mXmppConnectionService.markReadUpTo(c, message);
                }
                if (query == null) {
                    activateGracePeriod(account);
                }
            } else if (isTypeGroupChat) {
                final Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
                Message message;
                if (conversation != null && id != null) {
                    if (sender != null) {
                        message = conversation.findMessageWithRemoteId(id, sender);
                    } else {
                        message = conversation.findMessageWithServerMsgId(id);
                    }
                } else {
                    message = null;
                }
                if (message != null) {
                    final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                    final Jid trueJid = getTrueCounterpart((query != null && query.safeToExtractTrueCounterpart()) ? mucUserElement : null, fallback);
                    final boolean trueJidMatchesAccount = account.getJid().asBareJid().equals(trueJid == null ? null : trueJid.asBareJid());
                    if (trueJidMatchesAccount || conversation.getMucOptions().isSelf(counterpart)) {
                        if (!message.isRead() && (query == null || query.isCatchup())) { //checking if message is unread fixes race conditions with reflections
                            mXmppConnectionService.markReadUpTo(conversation, message);
                        }
                    } else if (!counterpart.isBareJid() && trueJid != null) {
                        final ReadByMarker readByMarker = ReadByMarker.from(counterpart, trueJid);
                        if (message.addReadByMarker(readByMarker)) {
                            final Message displayedMessage = message;
                            if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
                                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                            }
                            mXmppConnectionService.updateMessage(message, false);
                            message = displayedMessage == null ? null : displayedMessage.prev();
                            while (message != null
                                    && message.getStatus() >= Message.STATUS_SEND_RECEIVED
                                    && message.getTimeSent() < displayedMessage.getTimeSent()) {
                                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                                mXmppConnectionService.updateMessage(message, false);
                                message = message.prev();
                            }
                        }
                    }
                }
            } else {
                final Message displayedMessage = mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
                Message message = displayedMessage == null ? null : displayedMessage.prev();
                while (message != null
                        && message.getStatus() == Message.STATUS_SEND_RECEIVED
                        && message.getTimeSent() < displayedMessage.getTimeSent()) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                    message = message.prev();
                }
                if (displayedMessage != null && selfAddressed) {
                    dismissNotification(account, counterpart, query, id);
                }
            }
        }

        final Element event = original.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != null && InvalidJid.hasValidFrom(original) && original.getFrom().isBareJid()) {
            if (event.hasChild("items")) {
                parseEvent(event, original.getFrom(), account);
            } else if (event.hasChild("delete")) {
                parseDeleteEvent(event, original.getFrom(), account);
            } else if (event.hasChild("purge")) {
                parsePurgeEvent(event, original.getFrom(), account);
            }
        }

        final String nick = packet.findChildContent("nick", Namespace.NICK);
        if (nick != null && InvalidJid.hasValidFrom(original)) {
            if (mXmppConnectionService.isMuc(account, from)) {
                return;
            }
            final Contact contact = account.getRoster().getContact(from);
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.syncRoster(account);
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
    }

    private void updateReadMarker(Account account, Jid from, String id, boolean selfAddressed, Jid counterpart, MessageArchiveService.Query query) {
        final Message displayedMessage = mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
        Message m = displayedMessage == null ? null : displayedMessage.prev();
        while (m != null
                && m.getStatus() == Message.STATUS_SEND_RECEIVED
                && m.getTimeSent() < displayedMessage.getTimeSent()) {
            mXmppConnectionService.markMessage(m, Message.STATUS_SEND_DISPLAYED);
            m = m.prev();
        }
        if (displayedMessage != null && selfAddressed) {
            dismissNotification(account, counterpart, query, id);
        }
    }

    private void dismissNotification(Account account, Jid counterpart, MessageArchiveService.Query query, final String id) {
        final Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            final String displayableId = conversation.findMostRecentRemoteDisplayableId();
            if (displayableId != null && displayableId.equals(id)) {
                mXmppConnectionService.markRead(conversation);
            } else {
                Log.w(Config.LOGTAG, account.getJid().asBareJid() + ": received dismissing display marker that did not match our last id in that conversation");
            }
        }
    }

    private void processMessageReceipts(final Account account, final MessagePacket packet, final String remoteMsgId, MessageArchiveService.Query query) {
        final boolean markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
        final boolean request = packet.hasChild("request", "urn:xmpp:receipts");
        if (query == null) {
            final ArrayList<String> receiptsNamespaces = new ArrayList<>();
            if (markable) {
                receiptsNamespaces.add("urn:xmpp:chat-markers:0");
            }
            if (request) {
                receiptsNamespaces.add("urn:xmpp:receipts");
            }
            if (receiptsNamespaces.size() > 0) {
                final MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
                        packet.getFrom(),
                        remoteMsgId,
                        receiptsNamespaces,
                        packet.getType());
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        } else if (query.isCatchup()) {
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), remoteMsgId));
            }
        }
    }

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getLongPreference("grace_period_length", R.integer.grace_period) * 1000;
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": activating grace period till " + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }

    private class Invite {
        final Jid jid;
        final String password;
        final boolean direct;
        final Jid inviter;

        Invite(Jid jid, String password, boolean direct, Jid inviter) {
            this.jid = jid;
            this.password = password;
            this.direct = direct;
            this.inviter = inviter;
        }


        public boolean execute(final Account account, LocalizedContent body) {
            if (this.jid == null) {
                return false;
            }
            final Contact contact =
                    this.inviter != null ? account.getRoster().getContact(this.inviter) : null;
            if (contact != null && contact.isBlocked()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignore invite from "
                                + contact.getJid()
                                + " because contact is blocked");
                return false;
            }
            if ((contact != null && contact.showInContactList())
                    || mXmppConnectionService.getBooleanPreference("accept_invites_from_strangers", R.bool.accept_invites_from_strangers)) {
                final Conversation conversation =
                        mXmppConnectionService.findOrCreateConversation(account, jid, true, false);
                if (conversation.getMucOptions().online()) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": received invite to "
                                    + jid
                                    + " but muc is considered to be online");
                    mXmppConnectionService.mucSelfPingAndRejoin(conversation);
                } else {
                    conversation.getMucOptions().setPassword(password);
                    mXmppConnectionService.databaseBackend.updateConversation(conversation);
                    mXmppConnectionService.joinMuc(
                            conversation, contact != null && contact.showInContactList());
                    mXmppConnectionService.updateConversationUi();
                    if (body != null) {
                        mXmppConnectionService.showInvitationNotification(conversation, contact, body);
                    }
                }
                return true;
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring invite from "
                                + this.inviter
                                + " because we are not accepting invites from strangers. direct="
                                + direct);
                return false;
            }
        }
    }
}
