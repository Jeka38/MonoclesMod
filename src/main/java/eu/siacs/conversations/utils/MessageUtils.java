/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import com.google.common.base.Strings;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.AesGcmURL;
import eu.siacs.conversations.http.URL;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.xmpp.Jid;

public class MessageUtils {

    private static final Pattern LTR_RTL = Pattern.compile("(\\u200E[^\\u200F]*\\u200F){3,}");
    public static final String EMPTY_STRING = "";

    public static String prepareQuote(Message message) {
        final StringBuilder builder = new StringBuilder();
        String body;
        if (message.hasMeCommand()) {
            final String nick;
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                if (message.getConversation().getMode() == Conversational.MODE_MULTI) {
                    nick = Strings.nullToEmpty(message.getCounterpart().getResource());
                } else {
                    final Contact contact = message.getContact();
                    nick = contact == null ? "" : Strings.nullToEmpty(contact.getPublicDisplayName());
                }
            } else {
                nick = UIHelper.getMessageDisplayName(message);
            }
            final String quoteable = message.getQuoteableBody();
            final int meIndex = message.getMeCommandIndex();
            // The index is computed on the raw body, but getQuoteableBody() may have stripped
            // fallbacks, so guard against the command falling outside the quoteable body.
            if (meIndex >= 0 && meIndex + Message.ME_COMMAND.length() <= quoteable.length()) {
                body = nick + " " + quoteable.substring(meIndex + Message.ME_COMMAND.length());
            } else {
                body = quoteable;
            }
        } else {
            body = message.getQuoteableBody();
        }
        if (body == null) {
            body = EMPTY_STRING;
        }
        for (String line : body.split("\n")) {
            if (!(line.length() <= 0) && QuoteHelper.isNestedTooDeeply(line)) {
                continue;
            }
            if (builder.length() != 0) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
        return builder.toString();
    }

    /**
     * Date and time of the message being quoted. Uses the device locale and time zone so the header
     * matches what the sender sees in the chat bubble.
     */
    public static String quoteTimestamp(final Message message) {
        if (message == null || message.getTimeSent() <= 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(new Date(message.getTimeSent()));
    }

    /** Sender to show in a quote header; MUC nick, own nick, or the peer's JID local part. */
    public static String quoteSender(final Message message) {
        if (message == null) {
            return EMPTY_STRING;
        }
        String sender = message.getConversation() == null ? null : message.getAvatarName();
        if (sender == null || sender.trim().isEmpty()) {
            final Jid counterpart = message.getCounterpart();
            if (counterpart != null) {
                sender = counterpart.getLocal();
                if (sender == null || sender.isEmpty()) {
                    sender = counterpart.asBareJid().toString();
                }
            }
        }
        if (sender == null) {
            return EMPTY_STRING;
        }
        sender = sender.replace('\n', ' ').replace('\r', ' ').trim();
        // A nick starting with '>' would be eaten as a quotation marker by QuoteHelper.quote().
        while (sender.startsWith(">")) {
            sender = sender.substring(1).trim();
        }
        return sender;
    }

    /** Header of the quoted block: sender and, when enabled, the original message's date/time. */
    public static String quoteHeader(final Message message, final boolean showDate) {
        final String sender = quoteSender(message);
        final String timestamp = showDate ? quoteTimestamp(message) : EMPTY_STRING;
        if (sender.isEmpty()) {
            return timestamp;
        }
        if (timestamp.isEmpty()) {
            return sender;
        }
        return sender + " \u00B7 " + timestamp;
    }

    /**
     * Builds an XEP-0393 quotation: every line starts with {@code > } (existing quote markers gain
     * one level, so a quote-in-quote is preserved), and the first line carries the original sender
     * plus the original date/time.
     */
    public static String quote(final Message message, final boolean showDate) {
        final StringBuilder builder = new StringBuilder();
        final String header = quoteHeader(message, showDate);
        if (!header.isEmpty()) {
            builder.append(header).append('\n');
        }
        builder.append(prepareQuote(message));
        return QuoteHelper.quote(builder.toString());
    }

    public static boolean treatAsDownloadable(final String body, final boolean oob) {
        if (oob) return true;

        final String[] lines = body.split("\n");
        if (lines.length == 0) {
            return false;
        }
        for (final String line : lines) {
            if (line.contains("\\s+")) {
                return false;
            }
        }
        final URI uri;
        try {
            uri = new URI(lines[0]);
        } catch (final URISyntaxException e) {
            return false;
        }
        if (!URL.WELL_KNOWN_SCHEMES.contains(uri.getScheme())) {
            return false;
        }
        final String ref = uri.getFragment();
        final String protocol = uri.getScheme();
        final boolean encrypted = ref != null && AesGcmURL.IV_KEY.matcher(ref).matches();
        final boolean followedByDataUri = lines.length == 2 && lines[1].startsWith("data:");
        final boolean validAesGcm = AesGcmURL.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted && (lines.length == 1 || followedByDataUri);
        final boolean validProtocol = "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
        final boolean validOob = validProtocol && (oob || encrypted) && lines.length == 1;
        return validAesGcm || validOob;
    }

    public static String aesgcmDownloadable(final String body) {
        final String[] lines = body.split("\n");
        if (lines.length == 0) {
            return null;
        }
        for (final String line : lines) {
            if (line.contains("\\s+")) {
                return null;
            }
        }
        final URI uri;
        try {
            uri = new URI(lines[0]);
        } catch (final URISyntaxException e) {
            return null;
        }
        if (!URL.WELL_KNOWN_SCHEMES.contains(uri.getScheme())) {
            return null;
        }
        final String ref = uri.getFragment();
        final String protocol = uri.getScheme();
        final boolean encrypted = ref != null && AesGcmURL.IV_KEY.matcher(ref).matches();
        final boolean followedByDataUri = lines.length == 2 && lines[1].startsWith("data:");
        final boolean validAesGcm = AesGcmURL.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted && (lines.length == 1 || followedByDataUri);
        return validAesGcm ? lines[0] : null;
    }

    public static String filterLtrRtl(String body) {
        return LTR_RTL.matcher(body).replaceFirst(EMPTY_STRING);
    }

    public static boolean unInitiatedButKnownSize(Message message) {
        return message.getType() == Message.TYPE_TEXT && message.getTransferable() == null && message.isOOb() && message.getFileParams().url != null &&
                (message.getFileParams().size != null || (message.getOob() != null && message.getOob().getScheme() != null && message.getOob().getScheme().equalsIgnoreCase("cid")));
    }

    public static boolean fileWithKnownSize(Message message) {
        return message.getType() == Message.TYPE_TEXT && message.isOOb() && message.getFileParams().size != null && message.getFileParams().size > 0 && message.getFileParams().url != null;
    }

    /** SIMS sources of the form {@code xmpp:user@host/resource?jingle-ft} need a Jingle request. */
    public static boolean isJingleFtSource(final String uri) {
        if (uri == null) return false;
        final String lower = uri.toLowerCase(Locale.ROOT);
        return lower.startsWith("xmpp:") && lower.contains("jingle-ft");
    }
}