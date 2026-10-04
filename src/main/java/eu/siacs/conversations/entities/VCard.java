package eu.siacs.conversations.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * A user (or chat room) profile card.
 *
 * <p>Two wire formats are supported, mirroring what Psi does:
 *
 * <ul>
 *   <li>{@code vcard-temp} (XEP-0054) — the format Conversations/Gajim and every Android client
 *       speaks; also the one stored for MUC rooms.
 *   <li>vCard 4 (RFC 6350, XEP-0292 PEP node {@code urn:xmpp:vcard4}) — used by newer Psi versions.
 * </ul>
 *
 * <p>{@link #parse(Element)} understands both and {@link #toElement()} emits {@code vcard-temp}
 * while {@link #toVCard4Element()} emits a vCard 4 {@code <vcard/>}. Following Psi, a photo in
 * vCard 4 is carried as a {@code data:} URI so a single string holds mime type and bytes.
 */
public class VCard {

    /** Raw string; vCard allows reduced/partial dates such as {@code --12-24}. */
    public String birthday;
    public String fullName;
    public String givenName;
    public String middleName;
    public String familyName;
    public String prefix;
    public String suffix;
    public String nickName;
    public String email;
    public String emailType;
    public boolean emailPreferred;
    public String phone;
    public String phoneType;
    public String url;
    public String street;
    public String extAddress;
    public String city;
    public String region;
    public String postalCode;
    public String country;
    public String organization;
    public String organizationUnit;
    public String title;
    public String role;
    public String note;
    /** Photo mime type, e.g. {@code image/png}. */
    public String photoType;
    /** Base64 encoded photo bytes (no data URI prefix). */
    public String photoBase64;
    /** External photo reference ({@code <EXTVAL/>}), used when there are no inline bytes. */
    public String photoExternal;

    public boolean hasPhoto() {
        return isSet(photoBase64) || isSet(photoExternal);
    }

    public boolean isEmpty() {
        return !isSet(fullName)
                && !isSet(givenName)
                && !isSet(middleName)
                && !isSet(familyName)
                && !isSet(nickName)
                && !isSet(birthday)
                && !isSet(email)
                && !isSet(phone)
                && !isSet(url)
                && !isSet(street)
                && !isSet(extAddress)
                && !isSet(city)
                && !isSet(region)
                && !isSet(postalCode)
                && !isSet(country)
                && !isSet(organization)
                && !isSet(organizationUnit)
                && !isSet(title)
                && !isSet(role)
                && !isSet(note)
                && !hasPhoto();
    }

    /** {@code FN}, falling back to the structured name, then the nickname. */
    @Nullable
    public String getDisplayName() {
        if (isSet(fullName)) {
            return fullName;
        }
        final StringBuilder builder = new StringBuilder();
        append(builder, givenName);
        append(builder, middleName);
        append(builder, familyName);
        if (builder.length() > 0) {
            return builder.toString();
        }
        return isSet(nickName) ? nickName : null;
    }

    /**
     * Parses a {@code <vCard/>} ({@code vcard-temp}) or {@code <vcard/>} (vCard 4) element.
     * Unknown children are ignored so foreign extensions round-trip harmlessly.
     */
    @NonNull
    public static VCard parse(@Nullable final Element element) {
        final VCard vcard = new VCard();
        if (element == null) {
            return vcard;
        }
        if ("vcard".equals(element.getName())) {
            vcard.parseVCard4(element);
        } else {
            vcard.parseVCardTemp(element);
        }
        return vcard;
    }

    private void parseVCardTemp(@NonNull final Element element) {
        fullName = trim(element.findChildContent("FN"));
        nickName = trim(element.findChildContent("NICKNAME"));
        birthday = trim(element.findChildContent("BDAY"));
        url = trim(element.findChildContent("URL"));
        organization = trim(element.findChildContent("ORG"));
        title = trim(element.findChildContent("TITLE"));
        role = trim(element.findChildContent("ROLE"));
        note = firstSet(element.findChildContent("NOTE"), element.findChildContent("DESC"));

        final Element structuredName = element.findChild("N");
        if (structuredName != null) {
            givenName = trim(structuredName.findChildContent("GIVEN"));
            middleName = trim(structuredName.findChildContent("MIDDLE"));
            familyName = trim(structuredName.findChildContent("FAMILY"));
            prefix = trim(structuredName.findChildContent("PREFIX"));
            suffix = trim(structuredName.findChildContent("SUFFIX"));
        }

        final Element org = element.findChild("ORG");
        if (org != null) {
            organization = trim(org.findChildContent("ORGNAME"));
            organizationUnit = trim(org.findChildContent("ORGUNIT"));
        }

        parseTempAddress(element.findChild("ADR"));
        parseTempEmail(element.findChild("EMAIL"));
        parseTempPhone(element.findChild("TEL"));
        parseTempPhoto(element.findChild("PHOTO"));
    }

    private void parseTempAddress(@Nullable final Element address) {
        if (address == null) {
            return;
        }
        street = trim(address.findChildContent("STREET"));
        extAddress = trim(address.findChildContent("EXTADR"));
        city = trim(address.findChildContent("LOCALITY"));
        region = trim(address.findChildContent("REGION"));
        postalCode = trim(address.findChildContent("PCODE"));
        country = trim(address.findChildContent("CTRY"));
    }

    private void parseTempEmail(@Nullable final Element emailElement) {
        if (emailElement == null) {
            return;
        }
        email = trim(emailElement.findChildContent("USERID"));
        emailPreferred = emailElement.hasChild("PREF");
        emailType = firstSet(
                emailElement.hasChild("WORK") ? "work" : null,
                emailElement.hasChild("HOME") ? "home" : null);
    }

    private void parseTempPhone(@Nullable final Element phoneElement) {
        if (phoneElement == null) {
            return;
        }
        phone = trim(phoneElement.findChildContent("NUMBER"));
        phoneType = phoneElement.hasChild("WORK")
                ? "work"
                : phoneElement.hasChild("CELL")
                        ? "cell"
                        : phoneElement.hasChild("HOME") ? "home" : null;
    }

    private void parseTempPhoto(@Nullable final Element photo) {
        if (photo == null) {
            return;
        }
        photoType = trim(photo.findChildContent("TYPE"));
        photoBase64 = stripWhitespace(photo.findChildContent("BINVAL"));
        photoExternal = trim(photo.findChildContent("EXTVAL"));
    }

    private void parseVCard4(@NonNull final Element vcard) {
        fullName = firstSet(v4Text(vcard, "fn"), fullName);

        final Element structuredName = vcard.findChild("n");
        if (structuredName != null) {
            familyName = firstSet(v4Text(structuredName, "surname"), familyName);
            givenName = firstSet(v4Text(structuredName, "given"), givenName);
            middleName = firstSet(v4Text(structuredName, "additional"), middleName);
            prefix = firstSet(v4Text(structuredName, "prefix"), prefix);
            suffix = firstSet(v4Text(structuredName, "suffix"), suffix);
        }

        nickName = firstSet(v4Text(vcard, "nickname"), nickName);
        birthday = firstSet(v4Text(vcard, "bday"), birthday);
        email = firstSet(v4Text(vcard, "email"), email);
        phone = firstSet(v4Text(vcard, "tel"), phone);
        url = firstSet(v4Text(vcard, "url"), url);
        title = firstSet(v4Text(vcard, "title"), title);
        role = firstSet(v4Text(vcard, "role"), role);
        note = firstSet(v4Text(vcard, "note"), note);

        final Element organizationElement = vcard.findChild("organization");
        if (organizationElement != null) {
            organization = firstSet(v4Text(organizationElement, "text"), organization);
            organizationUnit = firstSet(v4Text(organizationElement, "unit"), organizationUnit);
        }

        final Element address = vcard.findChild("adr");
        if (address != null) {
            street = firstSet(v4Text(address, "street"), street);
            extAddress = firstSet(v4Text(address, "ext"), extAddress);
            city = firstSet(v4Text(address, "locality"), city);
            region = firstSet(v4Text(address, "region"), region);
            postalCode = firstSet(v4Text(address, "code"), postalCode);
            country = firstSet(v4Text(address, "country"), country);
        }

        final Element photo = vcard.findChild("photo");
        if (photo != null) {
            final String uri = firstSet(v4Text(photo, "uri"), photo.getContent());
            applyPhotoUri(uri);
        }
    }

    /** Splits a {@code data:image/png;base64,…} URI the way Psi writes vCard 4 photos. */
    private void applyPhotoUri(@Nullable final String uri) {
        if (uri == null) {
            return;
        }
        final String value = uri.trim();
        if (!value.startsWith("data:")) {
            photoExternal = value;
            return;
        }
        final int comma = value.indexOf(',');
        if (comma == -1) {
            return;
        }
        final String meta = value.substring(5, comma);
        final int semicolon = meta.indexOf(';');
        photoType = trim(semicolon == -1 ? meta : meta.substring(0, semicolon));
        if (photoType == null) {
            photoType = "image/*";
        }
        photoBase64 = stripWhitespace(value.substring(comma + 1));
    }

    /** Builds a {@code <vCard xmlns='vcard-temp'/>} ready to be sent inside an {@code <iq/>}. */
    @NonNull
    public Element toElement() {
        final Element vcard = new Element("vCard", Namespace.VCARD_TEMP);
        addText(vcard, "VERSION", "3.0");
        addText(vcard, "FN", fullName);
        addText(vcard, "NICKNAME", nickName);
        addText(vcard, "BDAY", birthday);
        addText(vcard, "URL", url);
        addText(vcard, "TITLE", title);
        addText(vcard, "ROLE", role);
        addText(vcard, "NOTE", note);

        if (isSet(familyName) || isSet(givenName) || isSet(middleName) || isSet(prefix) || isSet(suffix)) {
            final Element n = vcard.addChild("N");
            addText(n, "FAMILY", familyName);
            addText(n, "GIVEN", givenName);
            addText(n, "MIDDLE", middleName);
            addText(n, "PREFIX", prefix);
            addText(n, "SUFFIX", suffix);
        }

        if (isSet(organization) || isSet(organizationUnit)) {
            final Element org = vcard.addChild("ORG");
            addText(org, "ORGNAME", organization);
            addText(org, "ORGUNIT", organizationUnit);
        }

        if (isSet(street)
                || isSet(extAddress)
                || isSet(city)
                || isSet(region)
                || isSet(postalCode)
                || isSet(country)) {
            final Element adr = vcard.addChild("ADR");
            adr.addChild("HOME");
            addText(adr, "STREET", street);
            addText(adr, "EXTADR", extAddress);
            addText(adr, "LOCALITY", city);
            addText(adr, "REGION", region);
            addText(adr, "PCODE", postalCode);
            addText(adr, "CTRY", country);
        }

        if (isSet(email)) {
            final Element emailElement = vcard.addChild("EMAIL");
            if (emailPreferred) {
                emailElement.addChild("PREF");
            }
            if ("work".equals(emailType)) {
                emailElement.addChild("WORK");
            } else if ("home".equals(emailType)) {
                emailElement.addChild("HOME");
            }
            emailElement.addChild("INTERNET");
            addText(emailElement, "USERID", email);
        }

        if (isSet(phone)) {
            final Element phoneElement = vcard.addChild("TEL");
            if ("work".equals(phoneType)) {
                phoneElement.addChild("WORK");
            } else if ("cell".equals(phoneType)) {
                phoneElement.addChild("CELL");
            } else if ("home".equals(phoneType)) {
                phoneElement.addChild("HOME");
            }
            phoneElement.addChild("VOICE");
            addText(phoneElement, "NUMBER", phone);
        }

        if (isSet(photoBase64)) {
            final Element photo = vcard.addChild("PHOTO");
            addText(photo, "TYPE", isSet(photoType) ? photoType : "image/*");
            addText(photo, "BINVAL", photoBase64);
        } else if (isSet(photoExternal)) {
            final Element photo = vcard.addChild("PHOTO");
            addText(photo, "EXTVAL", photoExternal);
        }

        return vcard;
    }

    /** Builds a vCard 4 {@code <vcard xmlns='urn:ietf:params:xml:ns:vcard-4.0'/>}. */
    @NonNull
    public Element toVCard4Element() {
        final Element vcard = new Element("vcard", Namespace.VCARD4);
        addV4Text(vcard, "fn", fullName);
        addV4Text(vcard, "nickname", nickName);
        addV4Text(vcard, "bday", birthday);
        addV4Text(vcard, "email", email);
        addV4Text(vcard, "tel", phone);
        addV4Uri(vcard, "url", url);
        addV4Text(vcard, "title", title);
        addV4Text(vcard, "role", role);
        addV4Text(vcard, "note", note);

        if (isSet(familyName) || isSet(givenName) || isSet(middleName) || isSet(prefix) || isSet(suffix)) {
            final Element n = vcard.addChild("n");
            addV4Text(n, "surname", familyName);
            addV4Text(n, "given", givenName);
            addV4Text(n, "additional", middleName);
            addV4Text(n, "prefix", prefix);
            addV4Text(n, "suffix", suffix);
        }

        if (isSet(organization) || isSet(organizationUnit)) {
            final Element org = vcard.addChild("organization");
            addV4Text(org, "text", organization);
            addV4Text(org, "unit", organizationUnit);
        }

        if (isSet(street)
                || isSet(extAddress)
                || isSet(city)
                || isSet(region)
                || isSet(postalCode)
                || isSet(country)) {
            final Element adr = vcard.addChild("adr");
            addV4Text(adr, "street", street);
            addV4Text(adr, "ext", extAddress);
            addV4Text(adr, "locality", city);
            addV4Text(adr, "region", region);
            addV4Text(adr, "code", postalCode);
            addV4Text(adr, "country", country);
        }

        if (isSet(photoBase64)) {
            final Element photo = vcard.addChild("photo");
            final String type = isSet(photoType) ? photoType : "image/*";
            photo.addChild("uri").setContent(
                    "data:" + type + ";base64," + stripWhitespace(photoBase64));
        } else if (isSet(photoExternal)) {
            final Element photo = vcard.addChild("photo");
            photo.addChild("uri").setContent(photoExternal);
        }

        return vcard;
    }

    @Nullable
    private static String v4Text(@NonNull final Element parent, @NonNull final String name) {
        final Element element = parent.findChild(name);
        if (element == null) {
            return null;
        }
        // vCard 4 wraps the value in a type-specific child: <text/>, <uri/>, <date/>, <date-time/>
        for (final String type : new String[] {"text", "uri", "date-time", "date", "time"}) {
            final Element typed = element.findChild(type);
            if (typed != null) {
                return trim(typed.getContent());
            }
        }
        return trim(element.getContent());
    }

    private static void addV4Text(
            @NonNull final Element parent, @NonNull final String name, @Nullable final String value) {
        addV4Value(parent, name, "text", value);
    }

    private static void addV4Uri(
            @NonNull final Element parent, @NonNull final String name, @Nullable final String value) {
        addV4Value(parent, name, "uri", value);
    }

    private static void addV4Value(
            @NonNull final Element parent,
            @NonNull final String name,
            @NonNull final String valueName,
            @Nullable final String value) {
        if (!isSet(value)) {
            return;
        }
        final Element element = parent.addChild(name);
        // In vCard 4 XML the value element repeats the property name when both are the same
        // (e.g. <organization><text>…</text></organization>), otherwise it names the value type.
        if (name.equals(valueName)) {
            element.setContent(value);
        } else {
            element.addChild(valueName).setContent(value);
        }
    }

    private static void addText(
            @NonNull final Element parent, @NonNull final String name, @Nullable final String value) {
        if (!isSet(value)) {
            return;
        }
        parent.addChild(name).setContent(value);
    }

    private static void append(@NonNull final StringBuilder builder, @Nullable final String value) {
        if (!isSet(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private static boolean isSet(@Nullable final String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Nullable
    private static String firstSet(@Nullable final String first, @Nullable final String second) {
        return isSet(first) ? first : second;
    }

    @Nullable
    private static String trim(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String stripWhitespace(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String stripped = value.replaceAll("\\s", "");
        return stripped.isEmpty() ? null : stripped;
    }

    /** Normalises a user supplied birthday ({@code dd.mm.yyyy}, {@code yyyy-mm-dd}, …). */
    @Nullable
    public static String normalizeBirthday(@Nullable final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        final String value = raw.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return value;
        }
        final String[] parts = value.split("[.\\-/]");
        if (parts.length == 3 && parts[0].length() <= 2 && parts[2].length() == 4) {
            return String.format(
                    Locale.US, "%s-%02d-%02d", parts[2],
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        return value;
    }

    /** Human readable birthday for display, tolerating the reduced vCard date forms. */
    @Nullable
    public String getDisplayableBirthday() {
        if (!isSet(birthday)) {
            return null;
        }
        final String value = birthday.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            final String[] parts = value.split("-");
            return parts[2] + "." + parts[1] + "." + parts[0];
        }
        return value;
    }
}
