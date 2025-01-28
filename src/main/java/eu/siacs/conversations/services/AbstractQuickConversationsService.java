package eu.siacs.conversations.services;

import android.content.Intent;

import eu.siacs.conversations.BuildConfig;

public abstract class AbstractQuickConversationsService {


    public static final String SMS_RETRIEVED_ACTION = "com.google.android.gms.auth.api.phone.SMS_RETRIEVED";

    protected final XmppConnectionService service;

    public AbstractQuickConversationsService(XmppConnectionService service) {
        this.service = service;
    }

    public abstract void considerSync();

    public static boolean isQuicksy() {
        return true;
    }

    public static boolean isConversations() {
        return true;
    }

    public abstract void signalAccountStateChange();

    public abstract boolean isSynchronizing();

    public abstract void considerSyncBackground(boolean force);

    public abstract void handleSmsReceived(Intent intent);
}
