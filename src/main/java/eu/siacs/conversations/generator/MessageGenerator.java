package eu.siacs.conversations.generator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageGenerator extends AbstractGenerator {
    public static final String OTR_FALLBACK_MESSAGE = "I would like to start a private (OTR encrypted) conversation but your client doesn’t seem to support that";
    private static final String OMEMO_FALLBACK_MESSAGE = "I sent you an OMEMO encrypted message but your client doesn’t seem to support that. Find more information on https://conversations.im/omemo";
    private static final String PGP_FALLBACK_MESSAGE = "I sent you a PGP encrypted message but your client doesn’t seem to support that.";

    public MessageGenerator(XmppConnectionService service) {
        super(service);
    }

    private MessagePacket preparePacket(Message message, boolean legacyEncryption) {
        Conversation conversation = (Conversation) message.getConversation();
        Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        final boolean isWithSelf = conversation.getContact().isSelf();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            packet.setTo(message.getCounterpart());
            packet.setType(MessagePacket.TYPE_CHAT);
            if (this.mXmppConnectionService.indicateReceived() && !isWithSelf) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else if (message.isPrivateMessage()) {
            packet.setTo(message.getCounterpart());
            packet.setType(MessagePacket.TYPE_CHAT);
            packet.addChild("x", "http://jabber.org/protocol/muc#user");
            if (this.mXmppConnectionService.indicateReceived()) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else {
            packet.setTo(message.getCounterpart().asBareJid());
            packet.setType(MessagePacket.TYPE_GROUPCHAT);
        }
        if (conversation.isSingleOrPrivateAndNonAnonymous() && !message.isPrivateMessage()) {
            packet.addChild("markable", "urn:xmpp:chat-markers:0");
        }
        packet.setFrom(account.getJid());
        packet.setId(message.getUuid());
        if (conversation.getMode() == Conversational.MODE_SINGLE || message.isPrivateMessage() || !conversation.getMucOptions().stableId()) {
            packet.addChild("origin-id", Namespace.STANZA_IDS).setAttribute("id", message.getUuid());
        }
        if (message.edited() && !message.isMessageDeleted()) {
            packet.addChild("replace", "urn:xmpp:message-correct:0").setAttribute("id", message.getEditedIdWireFormat());
        }
        else if (message.isMessageDeleted()) {
            Element apply = packet.addChild("apply-to", "urn:xmpp:fasten:0").setAttribute("id", (message.getRetractId() != null ? message.getRetractId() : (message.getRemoteMsgId() != null ? message.getRemoteMsgId() : (message.getEditedIdWireFormat() != null ? message.getEditedIdWireFormat() : message.getUuid()))));
            apply.addChild("retract", "urn:xmpp:message-retract:0");
            packet.addChild("fallback", "urn:xmpp:fallback:0");
            packet.addChild("store", "urn:xmpp:hints");
            packet.setBody("This person attempted to retract a previous message, but it's unsupported by your client.");
        }
        if (!legacyEncryption) {
            if (message.getSubject() != null && message.getSubject().length() > 0) packet.addChild("subject").setContent(message.getSubject());
            // Legacy encryption can't handle advanced payloads
            for (Element el : message.getPayloads()) {
                packet.addChild(el);
            }
        } else {
            for (Element el : message.getPayloads()) {
                if ("thread".equals(el.getName())) packet.addChild(el);
            }
        }
        return packet;
    }

    public void addDelay(MessagePacket packet, long timestamp) {
        final SimpleDateFormat mDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Element delay = packet.addChild("delay", "urn:xmpp:delay");
        Date date = new Date(timestamp);
        delay.setAttribute("stamp", mDateFormat.format(date));
    }

    public MessagePacket generateAxolotlChat(Message message, XmppAxolotlMessage axolotlMessage) {
        MessagePacket packet = preparePacket(message, true);
        if (axolotlMessage == null) {
            return null;
        }
        packet.setAxolotlMessage(axolotlMessage.toElement());
        packet.setBody(OMEMO_FALLBACK_MESSAGE);
        packet.addChild("store", "urn:xmpp:hints");
        packet.addChild("encryption", "urn:xmpp:eme:0")
                .setAttribute("name", "OMEMO")
                .setAttribute("namespace", AxolotlService.PEP_PREFIX);
        return packet;
    }

    public MessagePacket generateKeyTransportMessage(Jid to, XmppAxolotlMessage axolotlMessage) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(to);
        packet.setAxolotlMessage(axolotlMessage.toElement());
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }


    public static void addMessageHints(MessagePacket packet) {
        packet.addChild("private", "urn:xmpp:carbons:2");
        packet.addChild("no-copy", "urn:xmpp:hints");
        packet.addChild("no-permanent-store", "urn:xmpp:hints");
        packet.addChild("no-permanent-storage", "urn:xmpp:hints"); //do not copy this. this is wrong. it is *store*
    }


    public MessagePacket generateOtrChat(Message message) {
        Conversation conversation = (Conversation) message.getConversation();
        Session otrSession = conversation.getOtrSession();
        if (otrSession == null) {
            return null;
        }
        MessagePacket packet = preparePacket(message, true);
        addMessageHints(packet);
        try {
            String content;
            if (message.hasFileOnRemoteHost()) {
                content = message.getFileParams().url.toString();
            } else {
                content = message.getBody();
            }
            packet.setBody(otrSession.transformSending(content)[0]);
            packet.addChild("encryption", "urn:xmpp:eme:0").setAttribute("namespace", "urn:xmpp:otr:0");
            return packet;
        } catch (OtrException e) {
            return null;
        }
    }

    public MessagePacket generateChat(Message message) {
        MessagePacket packet = preparePacket(message, false);
        if (message.hasFileOnRemoteHost()) {
            final Message.FileParams fileParams = message.getFileParams();

            if (message.getFallbacks(Namespace.OOB).isEmpty()) {
                if (message.getBody().equals("")) {
                    message.setBody(fileParams.url);
                    final var fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", Namespace.OOB);
                    fallback.addChild("body", "urn:xmpp:fallback:0");
                    message.addPayload(fallback);
                } else {
                    long start = message.getRawBody().codePointCount(0, message.getRawBody().length());
                    message.appendBody(fileParams.url);
                    final var fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", Namespace.OOB);
                    fallback.addChild("body", "urn:xmpp:fallback:0")
                            .setAttribute("start", String.valueOf(start))
                            .setAttribute("end", String.valueOf(start + fileParams.url.length()));
                    message.addPayload(fallback);
                }
            }
            packet = preparePacket(message, false);
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(fileParams.url);
        }
        if (message.getRawBody() != null) packet.setBody(message.getRawBody());
        return packet;
    }

    public MessagePacket generatePgpChat(Message message) {
        MessagePacket packet = preparePacket(message, true);
        if (message.hasFileOnRemoteHost()) {
            Message.FileParams fileParams = message.getFileParams();
            final String url = fileParams.url;
            packet.setBody(url);
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(url);
            packet.addChild("fallback", "urn:xmpp:fallback:0").setAttribute("for", Namespace.OOB)
                    .addChild("body", "urn:xmpp:fallback:0");
        } else {
            if (Config.supportUnencrypted()) {
                packet.setBody(PGP_FALLBACK_MESSAGE);
            }
            if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.getEncryptedBody());
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.getBody());
            }
            packet.addChild("encryption", "urn:xmpp:eme:0")
                    .setAttribute("namespace", "jabber:x:encrypted");
        }
        return packet;
    }

    public MessagePacket generateChatState(Conversation conversation) {
        final Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        packet.setType(conversation.getMode() == Conversation.MODE_MULTI ? MessagePacket.TYPE_GROUPCHAT : MessagePacket.TYPE_CHAT);
        packet.setTo(conversation.getJid().asBareJid());
        packet.setFrom(account.getJid());
        packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
        packet.addChild("no-store", "urn:xmpp:hints");
        packet.addChild("no-storage", "urn:xmpp:hints"); //wrong! don't copy this. Its *store*
        return packet;
    }

    public MessagePacket confirm(final Message message) {
        final boolean groupChat = message.getConversation().getMode() == Conversational.MODE_MULTI;
        final Jid to = message.getCounterpart();
        final MessagePacket packet = new MessagePacket();
        packet.setType(groupChat ? MessagePacket.TYPE_GROUPCHAT : MessagePacket.TYPE_CHAT);
        packet.setTo(groupChat ? to.asBareJid() : to);
        final Element displayed = packet.addChild("displayed", "urn:xmpp:chat-markers:0");
        if (groupChat) {
            final String stanzaId = message.getServerMsgId();
            if (stanzaId != null) {
                displayed.setAttribute("id", stanzaId);
            } else {
                displayed.setAttribute("sender", to.toString());
                displayed.setAttribute("id", message.getRemoteMsgId());
            }
        } else {
            displayed.setAttribute("id", message.getRemoteMsgId());
        }
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket conferenceSubject(Conversation conversation, String subject) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_GROUPCHAT);
        packet.setTo(conversation.getJid().asBareJid());
        packet.addChild("subject").setContent(subject);
        packet.setFrom(conversation.getAccount().getJid().asBareJid());
        return packet;
    }

    public MessagePacket directInvite(final Conversation conversation, final Jid contact) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_NORMAL);
        packet.setTo(contact);
        packet.setFrom(conversation.getAccount().getJid());
        Element x = packet.addChild("x", "jabber:x:conference");
        x.setAttribute("jid", conversation.getJid().asBareJid());
        String password = conversation.getMucOptions().getPassword();
        if (password != null) {
            x.setAttribute("password", password);
        }
        if (contact.isFullJid()) {
            packet.addChild("no-store", "urn:xmpp:hints");
            packet.addChild("no-copy", "urn:xmpp:hints");
        }
        return packet;
    }

    public MessagePacket invite(final Conversation conversation, final Jid contact) {
        final MessagePacket packet = new MessagePacket();
        packet.setTo(conversation.getJid().asBareJid());
        packet.setFrom(conversation.getAccount().getJid());
        Element x = new Element("x");
        x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
        Element invite = new Element("invite");
        invite.setAttribute("to", contact.asBareJid());
        x.addChild(invite);
        packet.addChild(x);
        return packet;
    }

    public MessagePacket received(Account account, final Jid from, final String id, ArrayList<String> namespaces, int type) {
        final MessagePacket receivedPacket = new MessagePacket();
        receivedPacket.setType(type);
        receivedPacket.setTo(from);
        receivedPacket.setFrom(account.getJid());
        for (final String namespace : namespaces) {
            receivedPacket.addChild("received", namespace).setAttribute("id", id);
        }
        receivedPacket.addChild("store", "urn:xmpp:hints");
        return receivedPacket;
    }

    public MessagePacket received(Account account, Jid to, String id) {
        MessagePacket packet = new MessagePacket();
        packet.setFrom(account.getJid());
        packet.setTo(to);
        packet.addChild("received", "urn:xmpp:receipts").setAttribute("id", id);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket generateOtrError(Jid to, String id, String errorText) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_ERROR);
        packet.setAttribute("id", id);
        packet.setTo(to);
        Element error = packet.addChild("error");
        error.setAttribute("code", "406");
        error.setAttribute("type", "modify");
        error.addChild("not-acceptable", "urn:ietf:params:xml:ns:xmpp-stanzas");
        error.addChild("text").setContent("?OTR Error:" + errorText);
        return packet;
    }

    public MessagePacket sessionFinish(
            final Jid with, final String sessionId, final Reason reason) {
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(with);
        final Element finish = packet.addChild("finish", Namespace.JINGLE_MESSAGE);
        finish.setAttribute("id", sessionId);
        final Element reasonElement = finish.addChild("reason", Namespace.JINGLE);
        reasonElement.addChild(reason.toString());
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket sessionProposal(final JingleConnectionManager.RtpSessionProposal proposal) {
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT); //we want to carbon copy those
        packet.setTo(proposal.with);
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX + proposal.sessionId);
        final Element propose = packet.addChild("propose", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", proposal.sessionId);
        for (final Media media : proposal.media) {
            propose.addChild("description", Namespace.JINGLE_APPS_RTP).setAttribute("media", media.toString());
        }

        packet.addChild("request", "urn:xmpp:receipts");
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket sessionRetract(final JingleConnectionManager.RtpSessionProposal proposal) {
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT); //we want to carbon copy those
        packet.setTo(proposal.with);
        final Element propose = packet.addChild("retract", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", proposal.sessionId);
        propose.addChild("description", Namespace.JINGLE_APPS_RTP);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket sessionReject(final Jid with, final String sessionId) {
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT); //we want to carbon copy those
        packet.setTo(with);
        final Element propose = packet.addChild("reject", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", sessionId);
        propose.addChild("description", Namespace.JINGLE_APPS_RTP);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }
}
