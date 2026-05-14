package eu.siacs.conversations.entities;

import eu.siacs.conversations.xml.Element;

public class Note extends Element {

    public Note(String body, String tag) {
        super("note");
        if (tag != null) {
            this.setAttribute("tag", tag);
        }
        this.setContent(body);
    }

    private Note() {
        super("note");
    }

    public static Note parse(Element element) {
        Note note = new Note();
        note.setAttributes(element.getAttributes());
        note.setChildren(element.getChildren());
        note.setContent(element.getContent());
        return note;
    }

    public String getTag() {
        return this.getAttribute("tag");
    }

    public String getBody() {
        return this.getContent();
    }
}
