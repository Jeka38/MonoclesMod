package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.Media;

/**
 * A single {@code <content>} element inside a Muji {@code <muji>} element (XEP-0272).
 *
 * <p>The content carries an RTP description but (unlike a Jingle content) no transport, since it is
 * only used to advertise which streams a participant is willing to provide and to agree on a global
 * payload type mapping.
 */
public class MujiContent extends Element {

    private MujiContent() {
        super("content", Namespace.JINGLE_MUJI);
    }

    public MujiContent(final String creator, final String name, final RtpDescription description) {
        super("content", Namespace.JINGLE_MUJI);
        this.setAttribute("creator", creator);
        this.setAttribute("name", name);
        this.addChild(description);
    }

    public static MujiContent of(final String name, final Media media) {
        return new MujiContent("initiator", name, RtpDescription.stub(media));
    }

    public String getContentName() {
        return getAttribute("name");
    }

    public String getCreator() {
        return getAttribute("creator");
    }

    public RtpDescription getDescription() {
        final Element description = findChild("description", Namespace.JINGLE_APPS_RTP);
        return description == null ? null : RtpDescription.upgrade(description);
    }

    public Media getMedia() {
        final RtpDescription description = getDescription();
        return description == null ? Media.UNKNOWN : description.getMedia();
    }

    public static MujiContent upgrade(final Element element) {
        Preconditions.checkArgument("content".equals(element.getName()));
        final MujiContent content = new MujiContent();
        content.setAttributes(element.getAttributes());
        content.setChildren(element.getChildren());
        return content;
    }
}
