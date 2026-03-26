package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class StanzaHistory {
    private static final int MAX_STANZAS = 200;

    public static class Stanza {
        public enum Direction {
            SENT, RECEIVED
        }

        private final long timestamp;
        private final Direction direction;
        private final String content;

        public Stanza(Direction direction, String content) {
            this.timestamp = System.currentTimeMillis();
            this.direction = direction;
            this.content = content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Direction getDirection() {
            return direction;
        }

        public String getContent() {
            return content;
        }
    }

    private final List<Stanza> stanzas = Collections.synchronizedList(new ArrayList<>());

    public void add(Stanza.Direction direction, AbstractStanza stanza) {
        add(new Stanza(direction, stanza.toString()));
    }

    public void add(Stanza.Direction direction, String content) {
        add(new Stanza(direction, content));
    }

    private void add(Stanza stanza) {
        synchronized (stanzas) {
            stanzas.add(stanza);
            while (stanzas.size() > MAX_STANZAS) {
                stanzas.remove(0);
            }
        }
    }

    public List<Stanza> getStanzas() {
        synchronized (stanzas) {
            return new ArrayList<>(stanzas);
        }
    }

    public void clear() {
        stanzas.clear();
    }
}
