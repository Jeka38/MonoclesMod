package eu.siacs.conversations.generator;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;

public abstract class AbstractGenerator {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private final String[] STATIC_FEATURES = {
            Namespace.JINGLE,
            Namespace.JINGLE_APPS_FILE_TRANSFER,
            Namespace.JINGLE_TRANSPORTS_S5B,
            Namespace.JINGLE_TRANSPORTS_IBB,
            Namespace.JINGLE_ENCRYPTED_TRANSPORT,
            Namespace.JINGLE_ENCRYPTED_TRANSPORT_OMEMO,
            "http://jabber.org/protocol/muc",
            "jabber:x:conference",
            Namespace.OOB,
            "http://jabber.org/protocol/caps",
            "http://jabber.org/protocol/disco#info",
            "urn:xmpp:avatar:metadata+notify",
            Namespace.NICK + "+notify",
            "urn:xmpp:ping",
            "jabber:iq:version",
            "http://jabber.org/protocol/chatstates"
    };
    private final String[] MESSAGE_CONFIRMATION_FEATURES = {
            "urn:xmpp:chat-markers:0",
            "urn:xmpp:receipts"
    };
    private final String[] MESSAGE_CORRECTION_FEATURES = {
            "urn:xmpp:message-correct:0"
    };
    private final String[] MESSAGE_RETRACTION_FEATURES = {
            "urn:xmpp:message-retract:0"
    };
    private final String[] PRIVACY_SENSITIVE = {
            "urn:xmpp:time" //XEP-0202: Entity Time leaks time zone
    };
    private final String[] OTR = {
            "urn:xmpp:otr:0"
    };
    private final String[] VOIP_NAMESPACES = {
            Namespace.JINGLE_TRANSPORT_ICE_UDP,
            Namespace.JINGLE_FEATURE_AUDIO,
            Namespace.JINGLE_FEATURE_VIDEO,
            Namespace.JINGLE_APPS_RTP,
            Namespace.JINGLE_APPS_DTLS,
            Namespace.JINGLE_MESSAGE
    };

    protected XmppConnectionService mXmppConnectionService;
    private String mVersion = null;

    AbstractGenerator(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static String getTimestamp(long time) {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        return DATE_FORMAT.format(time);
    }

    String getIdentityVersion() {
        if (mVersion == null) {
            this.mVersion = PhoneHelper.getVersionName(mXmppConnectionService);
        }
        return this.mVersion;
    }

    public String getIdentityName() {
        return mXmppConnectionService.getString(R.string.app_name) + ' ' + getIdentityVersion();
    }

    public String getUserAgent() {
        return System.getProperty("http.agent");
    }

    String getIdentityType() {
        if ("chromium".equals(android.os.Build.BRAND)) {
            return "pc";
        } else {
            return mXmppConnectionService.getString(R.string.default_resource).toLowerCase();
        }
    }

    String getCapHash(final Account account) {
        StringBuilder s = new StringBuilder();
        s.append("client/").append(getIdentityType()).append("//").append(getIdentityName()).append('<');
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        for (String feature : getFeatures(account)) {
            s.append(feature).append('<');
        }
        final byte[] sha1 = md.digest(s.toString().getBytes());
        return Base64.encodeToString(sha1, Base64.NO_WRAP);
    }

    public List<String> getFeatures(Account account) {
        final XmppConnection connection = account.getXmppConnection();
        final ArrayList<String> features = new ArrayList<>(Arrays.asList(STATIC_FEATURES));
        if (Config.MESSAGE_DISPLAYED_SYNCHRONIZATION) {
            features.add(Namespace.MDS_DISPLAYED + "+notify");
        }
        features.add("http://jabber.org/protocol/xhtml-im");
        features.add("urn:xmpp:bob");
        if (mXmppConnectionService.confirmMessages()) {
            features.addAll(Arrays.asList(MESSAGE_CONFIRMATION_FEATURES));
        }
        if (mXmppConnectionService.allowMessageCorrection()) {
            features.addAll(Arrays.asList(MESSAGE_CORRECTION_FEATURES));
        }
        if (mXmppConnectionService.allowMessageRetraction()) {
            features.addAll(Arrays.asList(MESSAGE_RETRACTION_FEATURES));
        }
        if (Config.supportOmemo()) {
            features.add(AxolotlService.PEP_DEVICE_LIST_NOTIFY);
        }
        if (!mXmppConnectionService.useTorToConnect() && !account.isOnion() && !mXmppConnectionService.useI2PToConnect() && !account.isI2P()) {
            features.addAll(Arrays.asList(PRIVACY_SENSITIVE));
            features.addAll(Arrays.asList(VOIP_NAMESPACES));
            features.add(Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL);
        }
        if (Config.supportOtr()) {
            features.addAll(Arrays.asList(OTR));
        }
        if (mXmppConnectionService.broadcastLastActivity()) {
            features.add(Namespace.IDLE);
        }
        if (connection != null && connection.getFeatures().bookmarks2()) {
            features.add(Namespace.BOOKMARKS2 + "+notify");
        } else {
            features.add(Namespace.BOOKMARKS + "+notify");
        }
        Collections.sort(features);
        return features;
    }
}
