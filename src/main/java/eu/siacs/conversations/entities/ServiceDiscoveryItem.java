package eu.siacs.conversations.entities;

import eu.siacs.conversations.xmpp.Jid;

public class ServiceDiscoveryItem {

    private final Jid jid;
    private final String name;
    private final String node;

    private String identityName;

    public ServiceDiscoveryItem(Jid jid, String name, String node) {
        this.jid = jid;
        this.name = name;
        this.node = node;
    }

    public Jid getJid() {
        return jid;
    }

    public String getName() {
        return name;
    }

    public String getNode() {
        return node;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public String getDisplayName() {
        return name != null && !name.isEmpty() ? name : (jid == null ? "" : jid.toEscapedString());
    }
}