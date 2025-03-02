package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Element;

public class IbbTransportInfo extends GenericTransportInfo {

    private IbbTransportInfo(final String name, final String xmlns) {
        super(name, xmlns);
    }

    public IbbTransportInfo(final String transportId, final int blockSize) {
        super("transport", Namespace.JINGLE_TRANSPORTS_IBB);
        Preconditions.checkNotNull(transportId, "Transport ID can not be null");
        Preconditions.checkArgument(blockSize > 0, "Block size must be larger than 0");
        this.setAttribute("block-size", blockSize);
        this.setAttribute("sid", transportId);
    }

    public String getTransportId() {
        return this.getAttribute("sid");
    }

    public Long getBlockSize() {
        final String blockSize = this.getAttribute("block-size");
        return Strings.isNullOrEmpty(blockSize) ? null : Longs.tryParse(blockSize);
    }

    public static IbbTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument("transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(Namespace.JINGLE_TRANSPORTS_IBB.equals(element.getNamespace()), "Element does not match ibb transport namespace");
        final IbbTransportInfo transportInfo = new IbbTransportInfo("transport", Namespace.JINGLE_TRANSPORTS_IBB);
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }
}
