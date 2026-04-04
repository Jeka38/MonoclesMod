package eu.siacs.conversations.entities;

import android.graphics.Bitmap;
import eu.siacs.conversations.xmpp.forms.Data;

import eu.siacs.conversations.xmpp.Jid;

public class CaptchaRequest {
    private final Account account;
    private final String id;
    private final Data data;
    private final Bitmap bitmap;
    private final String stanzaId;
    private final Jid target;

    public CaptchaRequest(Account account, String id, Data data, Bitmap bitmap, String stanzaId, Jid target) {
        this.account = account;
        this.id = id;
        this.data = data;
        this.bitmap = bitmap;
        this.stanzaId = stanzaId;
        this.target = target;
    }

    public Account getAccount() {
        return account;
    }

    public String getId() {
        return id;
    }

    public Data getData() {
        return data;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public String getStanzaId() {
        return stanzaId;
    }

    public Jid getTarget() {
        return target;
    }
}
