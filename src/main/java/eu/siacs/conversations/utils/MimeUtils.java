/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.siacs.conversations.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import com.google.common.collect.ImmutableList;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Arrays;
import java.util.Collection;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.ExportBackupService;

/**
 * Utilities for dealing with MIME types.
 * Used to implement java.net.URLConnection and android.webkit.MimeTypeMap.
 */
public final class MimeUtils {
    public static final List<String> AMBIGUOUS_CONTAINER_FORMATS = ImmutableList.of(
            "application/ogg",
            "video/3gpp", // .3gp files can contain audio, video or both
            "video/3gpp2"
    );

    private static final Map<String, String> mimeTypeToExtensionMap = new HashMap<>();
    private static final Map<String, String> extensionToMimeTypeMap = new HashMap<>();

    static {
        // The following table is based on /etc/mime.types data minus
        // chemical/* MIME types and MIME types that don't map to any
        // file extensions. We also exclude top-level domain names to
        // deal with cases like:
        //
        // mail.google.com/a/google.com
        //
        // and "active" MIME types (due to potential security issues).
        // Note that this list is _not_ in alphabetical order and must not be sorted.
        // The "most popular" extension must come first, so that it's the one returned
        // by guessExtensionFromMimeType.
        add("application/andrew-inset", "ez");
        add("application/dsptype", "tsp");
        add("application/json", "json");
        add("application/epub+zip", "epub");
        add("application/gpx+xml", "gpx");
        add("application/hta", "hta");
        add("application/mac-binhex40", "hqx");
        add("application/mathematica", "nb");
        add("application/msaccess", "mdb");
        add("application/oda", "oda");
        add("application/pdf", "pdf");
        add("application/pgp-keys", "key");
        add("application/pgp-signature", "pgp");
        add("application/pics-rules", "prf");
        add("application/pkix-cert", "cer");
        add("application/rar", "rar");
        add("application/rdf+xml", "rdf");
        add("application/rss+xml", "rss");
        add("application/zip", "zip");
        add("application/gzip", "gz");
        add("application/gzip", "tgz");
        add("application/vnd.amazon.mobi8-ebook", "azw3");
        add("application/vnd.amazon.mobi8-ebook", "azw");
        add("application/vnd.amazon.mobi8-ebook", "kfx");
        add("application/vnd.android.package-archive", "apk");
        add("application/vnd.cinderella", "cdy");
        add(ExportBackupService.MIME_TYPE, "ceb");
        add("application/vnd.ms-pki.stl", "stl");
        add("application/vnd.oasis.opendocument.database", "odb");
        add("application/vnd.oasis.opendocument.formula", "odf");
        add("application/vnd.oasis.opendocument.graphics", "odg");
        add("application/vnd.oasis.opendocument.graphics-template", "otg");
        add("application/vnd.oasis.opendocument.image", "odi");
        add("application/vnd.oasis.opendocument.spreadsheet", "ods");
        add("application/vnd.oasis.opendocument.spreadsheet-template", "ots");
        add("application/vnd.oasis.opendocument.text", "odt");
        add("application/vnd.oasis.opendocument.text-master", "odm");
        add("application/vnd.oasis.opendocument.text-template", "ott");
        add("application/vnd.oasis.opendocument.text-web", "oth");
        add("application/vnd.google-earth.kml+xml", "kml");
        add("application/vnd.google-earth.kmz", "kmz");
        add("application/msword", "doc");
        add("application/msword", "dot");
        add("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        add("application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx");
        add("application/vnd.ms-excel", "xls");
        add("application/vnd.ms-excel", "xlt");
        add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        add("application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx");
        add("application/vnd.ms-powerpoint", "ppt");
        add("application/vnd.ms-powerpoint", "pot");
        add("application/vnd.ms-powerpoint", "pps");
        add("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        add("application/vnd.openxmlformats-officedocument.presentationml.template", "potx");
        add("application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx");
        add("application/vnd.rim.cod", "cod");
        add("application/vnd.smaf", "mmf");
        add("application/vnd.stardivision.calc", "sdc");
        add("application/vnd.stardivision.draw", "sda");
        add("application/vnd.stardivision.impress", "sdd");
        add("application/vnd.stardivision.impress", "sdp");
        add("application/vnd.stardivision.math", "smf");
        add("application/vnd.stardivision.writer", "sdw");
        add("application/vnd.stardivision.writer", "vor");
        add("application/vnd.stardivision.writer-global", "sgl");
        add("application/vnd.sun.xml.calc", "sxc");
        add("application/vnd.sun.xml.calc.template", "stc");
        add("application/vnd.sun.xml.draw", "sxd");
        add("application/vnd.sun.xml.draw.template", "std");
        add("application/vnd.sun.xml.impress", "sxi");
        add("application/vnd.sun.xml.impress.template", "sti");
        add("application/vnd.sun.xml.math", "sxm");
        add("application/vnd.sun.xml.writer", "sxw");
        add("application/vnd.sun.xml.writer.global", "sxg");
        add("application/vnd.sun.xml.writer.template", "stw");
        add("application/vnd.visio", "vsd");
        add("application/x-abiword", "abw");
        add("application/x-apple-diskimage", "dmg");
        add("application/x-bcpio", "bcpio");
        add("application/x-bittorrent", "torrent");
        add("application/x-cdf", "cdf");
        add("application/x-cdlink", "vcd");
        add("application/x-chess-pgn", "pgn");
        add("application/x-cpio", "cpio");
        add("application/x-debian-package", "deb");
        add("application/x-debian-package", "udeb");
        add("application/x-director", "dcr");
        add("application/x-director", "dir");
        add("application/x-director", "dxr");
        add("application/x-dms", "dms");
        add("application/x-doom", "wad");
        add("application/x-dvi", "dvi");
        add("application/x-font", "pfa");
        add("application/x-font", "pfb");
        add("application/x-font", "gsf");
        add("application/x-font", "pcf");
        add("application/x-font", "pcf.Z");
        add("application/x-freemind", "mm");
        // application/futuresplash isn't IANA, so application/x-futuresplash should come first.
        add("application/x-futuresplash", "spl");
        add("application/futuresplash", "spl");
        add("application/x-gnumeric", "gnumeric");
        add("application/x-go-sgf", "sgf");
        add("application/x-graphing-calculator", "gcf");
        add("application/x-gtar", "tgz");
        add("application/x-gtar", "gtar");
        add("application/x-gtar", "taz");
        add("application/x-hdf", "hdf");
        add("application/x-ica", "ica");
        add("application/x-internet-signup", "ins");
        add("application/x-internet-signup", "isp");
        add("application/x-iphone", "iii");
        add("application/x-iso9660-image", "iso");
        add("application/x-jmol", "jmz");
        add("application/x-kchart", "chrt");
        add("application/x-killustrator", "kil");
        add("application/x-koan", "skp");
        add("application/x-koan", "skd");
        add("application/x-koan", "skt");
        add("application/x-koan", "skm");
        add("application/x-kpresenter", "kpr");
        add("application/x-kpresenter", "kpt");
        add("application/x-kspread", "ksp");
        add("application/x-kword", "kwd");
        add("application/x-kword", "kwt");
        add("application/x-latex", "latex");
        add("application/x-lha", "lha");
        add("application/x-lzh", "lzh");
        add("application/x-lzx", "lzx");
        add("application/x-maker", "frm");
        add("application/x-maker", "maker");
        add("application/x-maker", "frame");
        add("application/x-maker", "fb");
        add("application/x-maker", "book");
        add("application/x-maker", "fbdoc");
        add("application/x-mif", "mif");
        add("application/x-mobipocket-ebook", "mobi");
        add("application/x-ms-wmd", "wmd");
        add("application/x-ms-wmz", "wmz");
        add("application/x-msi", "msi");
        add("application/x-ns-proxy-autoconfig", "pac");
        add("application/x-nwc", "nwc");
        add("application/x-object", "o");
        add("application/x-oz-application", "oza");
        add("application/x-pem-file", "pem");
        add("application/x-pkcs12", "p12");
        add("application/x-pkcs12", "pfx");
        add("application/x-pkcs7-certreqresp", "p7r");
        add("application/x-pkcs7-crl", "crl");
        add("application/x-quicktimeplayer", "qtl");
        add("application/x-shar", "shar");
        add("application/x-shockwave-flash", "swf");
        add("application/x-stuffit", "sit");
        add("application/x-sv4cpio", "sv4cpio");
        add("application/x-sv4crc", "sv4crc");
        add("application/x-tar", "tar");
        add("application/x-texinfo", "texinfo");
        add("application/x-texinfo", "texi");
        add("application/x-troff", "t");
        add("application/x-troff", "roff");
        add("application/x-troff-man", "man");
        add("application/x-ustar", "ustar");
        add("application/x-wais-source", "src");
        add("application/x-wingz", "wz");
        add("application/x-webarchive", "webarchive");
        add("application/x-webarchive-xml", "webarchivexml");
        add("application/x-x509-ca-cert", "crt");
        add("application/x-x509-user-cert", "crt");
        add("application/x-x509-server-cert", "crt");
        add("application/x-xcf", "xcf");
        add("application/x-xfig", "fig");
        add("application/webxdc+zip", "xdc");
        add("application/xhtml+xml", "xhtml");
        add("video/3gpp", "3gpp");
        add("video/3gpp", "3gp");
        add("video/3gpp2", "3gpp2");
        add("video/3gpp2", "3g2");
        add("audio/3gpp", "3gpp");
        add("audio/3gpp", "3gp");
        add("audio/aac", "aac");
        add("audio/aac-adts", "aac");
        add("audio/amr", "amr");
        add("audio/amr-wb", "awb");
        add("audio/basic", "snd");
        add("audio/flac", "flac");
        add("audio/imelody", "imy");
        add("audio/midi", "mid");
        add("audio/midi", "midi");
        add("audio/midi", "ota");
        add("audio/midi", "kar");
        add("audio/midi", "rtttl");
        add("audio/midi", "xmf");
        add("audio/mobile-xmf", "mxmf");
        // add ".mp3" first so it will be the default for guessExtensionFromMimeType
        add("audio/mpeg", "mp3");
        add("audio/mpeg", "mpga");
        add("audio/mpeg", "mpega");
        add("audio/mpeg", "mp2");
        add("audio/mp4", "m4a");
        add("audio/x-m4b", "m4b");
        add("audio/mpegurl", "m3u");
        add("audio/ogg", "ogg");
        add("audio/ogg", "oga");
        add("audio/opus", "opus");
        add("audio/prs.sid", "sid");
        add("audio/x-aiff", "aif");
        add("audio/x-aiff", "aiff");
        add("audio/x-aiff", "aifc");
        add("audio/x-gsm", "gsm");
        add("audio/x-matroska", "mka");
        add("audio/x-mpegurl", "m3u");
        add("audio/x-ms-wma", "wma");
        add("audio/x-ms-wax", "wax");
        add("audio/x-pn-realaudio", "ra");
        add("audio/x-pn-realaudio", "rm");
        add("audio/x-pn-realaudio", "ram");
        add("audio/x-realaudio", "ra");
        add("audio/x-scpls", "pls");
        add("audio/x-sd2", "sd2");
        add("audio/x-wav", "wav");
        // image/bmp isn't IANA, so image/x-ms-bmp should come first.
        add("image/x-ms-bmp", "bmp");
        add("image/bmp", "bmp");
        add("image/gif", "gif");
        // image/ico isn't IANA, so image/x-icon should come first.
        add("image/x-icon", "ico");
        add("image/ico", "cur");
        add("image/ico", "ico");
        add("image/ief", "ief");
        add("image/heic", "heic");
        add("image/heif", "heif");
        add("image/avif", "avif");
        // add ".jpg" first so it will be the default for guessExtensionFromMimeType
        add("image/jpeg", "jpg");
        add("image/jpeg", "jpeg");
        add("image/jpeg", "jpe");
        add("image/jpeg", "jfif");
        add("image/jpeg", "jif");
        add("image/pcx", "pcx");
        add("image/png", "png");
        add("image/svg+xml", "svg");
        add("image/svg+xml", "svgz");
        add("image/tiff", "tiff");
        add("image/tiff", "tif");
        add("image/vnd.djvu", "djvu");
        add("image/vnd.djvu", "djv");
        add("image/vnd.wap.wbmp", "wbmp");
        add("image/webp", "webp");
        add("image/x-cmu-raster", "ras");
        add("image/x-coreldraw", "cdr");
        add("image/x-coreldrawpattern", "pat");
        add("image/x-coreldrawtemplate", "cdt");
        add("image/x-corelphotopaint", "cpt");
        add("image/x-jg", "art");
        add("image/x-jng", "jng");
        add("image/x-photoshop", "psd");
        add("image/x-portable-anymap", "pnm");
        add("image/x-portable-bitmap", "pbm");
        add("image/x-portable-graymap", "pgm");
        add("image/x-portable-pixmap", "ppm");
        add("image/x-rgb", "rgb");
        add("image/x-xbitmap", "xbm");
        add("image/x-xpixmap", "xpm");
        add("image/x-xwindowdump", "xwd");
        add("model/iges", "igs");
        add("model/iges", "iges");
        add("model/mesh", "msh");
        add("model/mesh", "mesh");
        add("model/mesh", "silo");
        add("text/calendar", "ics");
        add("text/calendar", "icz");
        add("text/comma-separated-values", "csv");
        add("text/css", "css");
        add("text/html", "htm");
        add("text/html", "html");
        add("text/h323", "323");
        add("text/iuls", "uls");
        add("text/javascript", "js");
        add("text/mathml", "mml");
        // add ".txt" first so it will be the default for guessExtensionFromMimeType
        add("text/plain", "txt");
        add("text/plain", "asc");
        add("text/plain", "text");
        add("text/plain", "diff");
        add("text/plain", "po");     // reserve "pot" for vnd.ms-powerpoint
        add("text/plain", "ass");
        add("text/richtext", "rtx");
        add("text/rtf", "rtf");
        add("text/text", "phps");
        add("text/tab-separated-values", "tsv");
        add("text/xml", "xml");
        add("text/x-bibtex", "bib");
        add("text/x-boo", "boo");
        add("text/x-c++hdr", "hpp");
        add("text/x-c++hdr", "h++");
        add("text/x-c++hdr", "hxx");
        add("text/x-c++hdr", "hh");
        add("text/x-c++src", "cpp");
        add("text/x-c++src", "c++");
        add("text/x-c++src", "cc");
        add("text/x-c++src", "cxx");
        add("text/x-chdr", "h");
        add("text/x-component", "htc");
        add("text/x-csh", "csh");
        add("text/x-csrc", "c");
        add("text/x-dsrc", "d");
        add("text/x-haskell", "hs");
        add("text/x-java", "java");
        add("text/x-literate-haskell", "lhs");
        add("text/x-moc", "moc");
        add("text/x-pascal", "p");
        add("text/x-pascal", "pas");
        add("text/x-pcs-gcd", "gcd");
        add("text/x-setext", "etx");
        add("text/x-tcl", "tcl");
        add("text/x-tex", "tex");
        add("text/x-tex", "ltx");
        add("text/x-tex", "sty");
        add("text/x-tex", "cls");
        add("text/x-vcalendar", "vcs");
        add("text/x-vcard", "vcf");
        add("video/avi", "avi");
        add("video/dl", "dl");
        add("video/dv", "dif");
        add("video/dv", "dv");
        add("video/fli", "fli");
        add("video/m4v", "m4v");
        add("video/mp2ts", "ts");
        add("video/ogg", "ogv");
        add("video/mpeg", "mpeg");
        add("video/mpeg", "mpg");
        add("video/mpeg", "mpe");
        add("video/mp4", "mp4");
        add("video/mp4", "gifv");
        add("video/mpeg", "VOB");
        add("video/quicktime", "qt");
        add("video/quicktime", "mov");
        add("video/vnd.mpegurl", "mxu");
        add("video/webm", "webm");
        add("video/x-la-asf", "lsf");
        add("video/x-la-asf", "lsx");
        add("video/x-matroska", "mkv");
        add("video/x-mng", "mng");
        add("video/x-ms-asf", "asf");
        add("video/x-ms-asf", "asx");
        add("video/x-ms-wm", "wm");
        add("video/x-ms-wmv", "wmv");
        add("video/x-ms-wmx", "wmx");
        add("video/x-ms-wvx", "wvx");
        add("video/x-sgi-movie", "movie");
        add("video/x-webex", "wrf");
        add("x-conference/x-cooltalk", "ice");
        add("x-epoc/x-sisx-app", "sisx");
        applyOverrides();
    }

    private static final List<String> DOCUMENT_MIMES = Arrays.asList(
            "application/pdf",
            "application/vnd.oasis.opendocument.text",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/x-tex",
            "text/plain"
    );

    // mime types that are more reliant by path
    private static final Collection<String> PATH_PRECEDENCE_MIME_TYPE = Arrays.asList("audio/x-m4b");

    private static void add(String mimeType, String extension) {
        // If we have an existing x -> y mapping, we do not want to
        // override it with another mapping x -> y2.
        // If a mime type maps to several extensions
        // the first extension added is considered the most popular
        // so we do not want to overwrite it later.
        if (!mimeTypeToExtensionMap.containsKey(mimeType)) {
            mimeTypeToExtensionMap.put(mimeType, extension);
        }
        if (!extensionToMimeTypeMap.containsKey(extension)) {
            extensionToMimeTypeMap.put(extension, mimeType);
        }
    }

    private static InputStream getContentTypesPropertiesStream() {
        // User override?
        String userTable = System.getProperty("content.types.user.table");
        if (userTable != null) {
            File f = new File(userTable);
            if (f.exists()) {
                try {
                    return new FileInputStream(f);
                } catch (IOException ignored) {
                }
            }
        }
        // Standard location?
        File f = new File(System.getProperty("java.home"), "lib" + File.separator + "content-types.properties");
        if (f.exists()) {
            try {
                return new FileInputStream(f);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /**
     * This isn't what the RI does. The RI doesn't have hard-coded defaults, so supplying your
     * own "content.types.user.table" means you don't get any of the built-ins, and the built-ins
     * come from "$JAVA_HOME/lib/content-types.properties".
     */
    private static void applyOverrides() {
        // Get the appropriate InputStream to read overrides from, if any.
        InputStream stream = getContentTypesPropertiesStream();
        if (stream == null) {
            return;
        }
        try {
            try {
                // Read the properties file...
                Properties overrides = new Properties();
                overrides.load(stream);
                // And translate its mapping to ours...
                for (Map.Entry<Object, Object> entry : overrides.entrySet()) {
                    String extension = (String) entry.getKey();
                    String mimeType = (String) entry.getValue();
                    add(mimeType, extension);
                }
            } finally {
                stream.close();
            }
        } catch (IOException ignored) {
        }
    }

    private MimeUtils() {
    }

    /**
     * Returns true if the given MIME type has an entry in the map.
     *
     * @param mimeType A MIME type (i.e. text/plain)
     * @return True iff there is a mimeType entry in the map.
     */
    public static boolean hasMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        return mimeTypeToExtensionMap.containsKey(mimeType);
    }

    /**
     * Returns the MIME type for the given extension.
     *
     * @param extension A file extension without the leading '.'
     * @return The MIME type for the given extension or null if there is none.
     */
    public static String guessMimeTypeFromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        return extensionToMimeTypeMap.get(extension.toLowerCase());
    }

    /**
     * Returns true if the given extension has a registered MIME type.
     *
     * @param extension A file extension without the leading '.'
     * @return True if there is an extension entry in the map.
     */
    public static boolean hasExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return extensionToMimeTypeMap.containsKey(extension);
    }

    /**
     * Returns the registered extension for the given MIME type. Note that some
     * MIME types map to multiple extensions. This call will return the most
     * common extension for the given MIME type.
     *
     * @param mimeType A MIME type (i.e. text/plain)
     * @return The extension for the given MIME type or null if there is none.
     */
    public static String guessExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        return mimeTypeToExtensionMap.get(mimeType.split(";")[0]);
    }

    public static String guessMimeTypeFromUriAndMime(final Context context, final Uri uri, final String mime) {
        Log.d(Config.LOGTAG, "guessMimeTypeFromUriAndMime(" + uri + "," + mime+")");
        final String mimeFromUri = guessMimeTypeFromUri(context, uri);
        Log.d(Config.LOGTAG,"mimeFromUri:"+mimeFromUri);
        if (PATH_PRECEDENCE_MIME_TYPE.contains(mimeFromUri)) {
            return mimeFromUri;
        } else if (mime == null || mime.equals("application/octet-stream")) {
            return mimeFromUri;
        } else {
            return mime;
        }
    }

    public static String guessMimeTypeFromUri(final Context context, final Uri uri) {
        if ("data".equals(uri.getScheme())) {
            String[] parts = uri.getSchemeSpecificPart().split(",", 2);
            String[] parts2 = parts[0].split(";", 2);
            if (parts2.length > 0) return parts2[0];
        }
        final String mimeTypeContentResolver = guessFromContentResolver(context, uri);
        final String mimeTypeFromQueryParameter = uri.isHierarchical() ? uri.getQueryParameter("mimeType") : null;
        final String name = "content".equals(uri.getScheme()) ? getDisplayName(context, uri) : null;
        final String mimeTypeFromName = Strings.isNullOrEmpty(name) ? null : guessFromPath(name);
        final String path = uri.getPath();
        final String mimeTypeFromPath = Strings.isNullOrEmpty(path) ? null : guessFromPath(path);
        if (PATH_PRECEDENCE_MIME_TYPE.contains(mimeTypeFromName)) {
            return mimeTypeFromName;
        }
        if (PATH_PRECEDENCE_MIME_TYPE.contains(mimeTypeFromPath)) {
            return mimeTypeFromPath;
        }
        if (mimeTypeContentResolver != null && !"application/octet-stream".equals(mimeTypeContentResolver)) {
            return mimeTypeContentResolver;
        }
        if (mimeTypeFromName != null) {
            return mimeTypeFromName;
        }
        if (mimeTypeFromQueryParameter != null) {
            return mimeTypeFromQueryParameter;
        }
        return mimeTypeFromPath;
    }

    private static String guessFromContentResolver(final Context context, final Uri uri) {
        try {
            return context.getContentResolver().getType(uri);
        } catch (final Throwable e) {
            return null;
        }
    }

    private static String getDisplayName(final Context context, final Uri uri) {
        try (final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index == -1) {
                    return null;
                }
                return cursor.getString(index);
            }
        } catch (final Exception e) {
            return null;
        }
        return null;
    }

    public static String guessFromPath(final String path) {
        final int start = path.lastIndexOf('.') + 1;
        if (start < path.length()) {
            return MimeUtils.guessMimeTypeFromExtension(path.substring(start));
        }
        return null;
    }

    public static String extractRelevantExtension(final String path) {
        return extractRelevantExtension(path, false);
    }

    public static String extractRelevantExtension(final String path, final boolean ignoreCryptoExtension) {
        if (Strings.isNullOrEmpty(path)) {
            return null;
        }

        final String filenameQueryAnchor = path.substring(path.lastIndexOf('/') + 1);
        final String filenameQuery = cutBefore(filenameQueryAnchor, '#');
        final String filename = cutBefore(filenameQuery, '?');
        final int dotPosition = filename.lastIndexOf('.');

        if (dotPosition == -1) {
            return null;
        }
        final String extension = filename.substring(dotPosition + 1);
        // we want the real file extension, not the crypto one
        if (ignoreCryptoExtension && Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
            return extractRelevantExtension(filename.substring(0, dotPosition));
        } else {
            return extension;
        }
    }

    private static String cutBefore(final String input, final char c) {
        final int position = input.indexOf(c);
        if (position > 0) {
            return input.substring(0, position);
        } else {
            return input;
        }
    }

    public static String getMimeTypeEmoji(Context context, String mime){
        String lm;
        if (mime == null) {
            lm = context.getString(R.string.unknown);
        } else if (mime.startsWith("audio/")) {
            lm = "\uD83C\uDF99"; // studio microphone emoji
        } else if (mime.equals("text/calendar") || (mime.equals("text/x-vcalendar"))) {
            lm = "\uD83D\uDCC6"; // tear-off calendar emoji
        } else if (mime.equals("text/x-vcard")) {
            lm = "\uD83D\uDC64"; // silhouette emoji
        } else if (mime.equals("application/vnd.android.package-archive")) {
            lm = "\uD83D\uDCF1"; // cell phone emoji
        } else if (mime.equals("application/zip") || mime.equals("application/rar")) {
            lm = "\uD83D\uDDC4️"; // filing cabinet emoji
        } else if (mime.equals("application/epub+zip") || mime.equals("application/vnd.amazon.mobi8-ebook")) {
            lm = "\uD83D\uDCD6"; // open book emoji
        } else if (mime.equals(ExportBackupService.MIME_TYPE)) {
            lm = "\uD83D\uDCBE"; // diskette emoji
        } else if (DOCUMENT_MIMES.contains(mime)) {
            lm = "\uD83D\uDCC4"; // page emoji
        } else if (mime.startsWith("image/")) {
            lm = "\uD83D\uDDBC️"; // painting emoji
        } else if (mime.startsWith("video/")) {
            lm = "\uD83C\uDFAC"; // clapper board emoji
        } else {
            lm = "\uD83D\uDCC4"; // page emoji
        }
        return lm;
    }
}
