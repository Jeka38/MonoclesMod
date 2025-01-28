package de.monocles.chat;

import android.content.ContentValues;
import android.database.Cursor;

import org.json.JSONObject;

import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;

public class WebxdcUpdate {
    protected final Long serial;
    protected final Long maxSerial;
    protected final String conversationId;
    protected final String messageId;
    protected final Jid sender;
    protected final String thread;
    protected final String threadParent;
    protected final String info;
    protected final String document;
    protected final String summary;
    protected final String payload;

    public WebxdcUpdate(final Conversation conversation, final String messageId, final Jid sender, final Element thread, final String info, final String document, final String summary, final String payload) {
        this.serial = null;
        this.maxSerial = null;
        this.conversationId = conversation.getUuid();
        this.messageId = messageId;
        this.sender = sender;
        this.thread = thread.getContent();
        this.threadParent = thread.getAttribute("parent");
        this.info = info;
        this.document = document;
        this.summary = summary;
        this.payload = payload;
    }

    public WebxdcUpdate(final Cursor cursor, long maxSerial) {
        this.maxSerial = maxSerial;
        this.serial = cursor.getLong(cursor.getColumnIndex("serial"));
        this.conversationId = cursor.getString(cursor.getColumnIndex(Message.CONVERSATION));
        this.messageId = cursor.getString(cursor.getColumnIndex("message_id"));
        this.sender = Jid.of(cursor.getString(cursor.getColumnIndex("sender")));
        this.thread = cursor.getString(cursor.getColumnIndex("thread"));
        this.threadParent = cursor.getString(cursor.getColumnIndex("threadParent"));
        this.info = cursor.getString(cursor.getColumnIndex("threadParent"));
        this.document = cursor.getString(cursor.getColumnIndex("document"));
        this.summary = cursor.getString(cursor.getColumnIndex("summary"));
        this.payload = cursor.getString(cursor.getColumnIndex("payload"));
    }

    public String getSummary() {
        return summary;
    }

    public String getDocument() {
        return document;
    }

    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues();
        cv.put(Message.CONVERSATION, conversationId);
        cv.put("message_id", messageId);
        cv.put("sender", sender.toEscapedString());
        cv.put("thread", thread);
        cv.put("threadParent", threadParent);
        if (info != null) cv.put("info", info);
        if (document != null) cv.put("document", document);
        if (summary != null) cv.put("summary", summary);
        if (payload != null) cv.put("payload", payload);
        return cv;
    }

    public String toString() {
        StringBuilder body = new StringBuilder("{\"sender\":");
        body.append(JSONObject.quote(sender.toEscapedString()));
        if (serial != null) {
            body.append(",\"serial\":");
            body.append(serial.toString());
        }
        if (maxSerial != null) {
            body.append(",\"max_serial\":");
            body.append(maxSerial.toString());
        }
        if (info != null) {
            body.append(",\"info\":");
            body.append(JSONObject.quote(info));
        }
        if (document != null) {
            body.append(",\"document\":");
            body.append(JSONObject.quote(document));
        }
        if (summary != null) {
            body.append(",\"summary\":");
            body.append(JSONObject.quote(summary));
        }
        if (payload != null) {
            body.append(",\"payload\":");
            body.append(payload);
        }
        body.append("}");
        return body.toString();
    }
}
