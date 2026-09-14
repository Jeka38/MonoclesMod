package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;

/**
 * XEP-0272 Multiparty Jingle (Muji) element.
 *
 * <p>Used in two places:
 *
 * <ul>
 *   <li>inside a MUC presence stanza to advertise (preparing / content descriptions) the streams a
 *       participant is willing to provide for a conference
 *   <li>inside a {@code <jingle>} session-initiate to reference the MUC room the session belongs to
 * </ul>
 */
public class Muji extends Element {

    private Muji() {
        super("muji", Namespace.JINGLE_MUJI);
    }

    public static Muji empty() {
        return new Muji();
    }

    public static Muji preparing() {
        final Muji muji = new Muji();
        muji.addChild("preparing");
        return muji;
    }

    public static Muji ofRoom(final Jid room) {
        Preconditions.checkArgument(room != null && room.isBareJid(), "room must be a bare JID");
        final Muji muji = new Muji();
        muji.setAttribute("room", room.toEscapedString());
        return muji;
    }

    public static Muji upgrade(final Element element) {
        Preconditions.checkArgument("muji".equals(element.getName()));
        final Muji muji = new Muji();
        muji.setAttributes(element.getAttributes());
        muji.setChildren(element.getChildren());
        return muji;
    }

    public boolean isPreparing() {
        return hasChild("preparing");
    }

    public Jid getRoom() {
        return getAttributeAsJid("room");
    }

    public List<MujiContent> getContents() {
        final ImmutableList.Builder<MujiContent> builder = ImmutableList.builder();
        for (final Element child : getChildren()) {
            if ("content".equals(child.getName())) {
                builder.add(MujiContent.upgrade(child));
            }
        }
        return builder.build();
    }

    public void addContent(final MujiContent content) {
        addChild(content);
    }

    public boolean hasContents() {
        for (final Element child : getChildren()) {
            if ("content".equals(child.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return getChildren().isEmpty();
    }
}
