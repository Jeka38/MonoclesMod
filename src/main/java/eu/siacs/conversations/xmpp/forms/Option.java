package eu.siacs.conversations.xmpp.forms;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import java.util.ArrayList;
import java.util.List;
import eu.siacs.conversations.xml.Element;

public class Option {
    protected final String value;
    protected final String label;
    protected final SVG icon;
    protected final Element iconEl;

    public static List<Option> forField(Element field) {
        List<Option> options = new ArrayList<>();
        for (Element el : field.getChildren()) {
            if (el.getNamespace() == null || !el.getNamespace().equals("jabber:x:data")) continue;
            if (!el.getName().equals("option")) continue;
            options.add(new Option(el));
        }
        return options;
    }

    public Option(final Element option) {
        this(
                option.findChildContent("value", "jabber:x:data"),
                option.getAttribute("label"),
                parseSVG(option.findChild("svg", "http://www.w3.org/2000/svg")),
                option.findChild("svg", "http://www.w3.org/2000/svg")
        );
    }

    public Option(final String value, final String label) {
        this(value, label, null, null);
    }

    public Option(final String value, final String label, final SVG icon, final Element iconEl) {
        this.value = value;
        this.label = label == null ? value : label;
        this.icon = icon;
        this.iconEl = iconEl;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Option)) return false;

        if (value == ((Option) o).value) return true;
        if (value == null || ((Option) o).value == null) return false;
        return value.equals(((Option) o).value);
    }

    public String toString() { return label; }

    public String getValue() { return value; }

    public SVG getIcon() { return icon; }

    public Element getIconEl() { return iconEl; }

    private static SVG parseSVG(final Element svg) {
        if (svg == null) return null;
        try {
            return SVG.getFromString(svg.toString());
        } catch (final SVGParseException e) {
            return null;
        }
    }
}
