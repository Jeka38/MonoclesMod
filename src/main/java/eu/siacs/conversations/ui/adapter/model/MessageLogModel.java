package eu.siacs.conversations.ui.adapter.model;

public class MessageLogModel {

    String body;
    long timeSent;

    public MessageLogModel(String body, long timeSent) {

        this.body = body;
        this.timeSent = timeSent;
    }

    public String getBody() {
        return body;
    }

    public long getTimeSent() {
        return timeSent;
    }
}