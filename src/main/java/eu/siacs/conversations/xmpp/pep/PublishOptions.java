package eu.siacs.conversations.xmpp.pep;

import android.os.Bundle;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class PublishOptions {

    private PublishOptions() {

    }

    public static Bundle openAccess() {
        final Bundle options = new Bundle();
        options.putString("pubsub#access_model", "open");
        return options;
    }

    public static Bundle persistentWhitelistAccess() {
        final Bundle options = new Bundle();
        options.putString("pubsub#persist_items", "true");
        options.putString("pubsub#access_model", "whitelist");
        return options;
    }

    public static Bundle persistentWhitelistAccessMaxItems() {
        final Bundle options = new Bundle();
        options.putString("pubsub#persist_items", "true");
        options.putString("pubsub#access_model", "whitelist");
        options.putString("pubsub#send_last_published_item", "never");
        options.putString("pubsub#max_items", "max");
        options.putString("pubsub#notify_delete", "true");
        options.putString("pubsub#notify_retract", "true"); //one could also set notify=true on the retract
        return options;
    }

    public static boolean preconditionNotMet(IqPacket response) {
        final Element error = response.getType() == IqPacket.TYPE.ERROR ? response.findChild("error") : null;
        return error != null && error.hasChild("precondition-not-met", Namespace.PUBSUB_ERROR);
    }

}