package de.monocles.chat;

import android.app.Application;
import android.graphics.Typeface;
import android.text.ParcelableSpan;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ParagraphStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuggestionSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.view.inputmethod.BaseInputConnection;

import java.util.ArrayList;

import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.utils.StylingHelper;
import io.ipfs.cid.Cid;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.TextNode;

public class SpannedToXHTML {
    private static SpannableStringBuilder cleanSpans(Spanned text) {
        SpannableStringBuilder newText = new SpannableStringBuilder(text);
        ParcelableSpan[] spans = newText.getSpans(0, newText.length(), ParcelableSpan.class);
        for (final var span : spans) {
            final var userFlags = (text.getSpanFlags(span) & Spanned.SPAN_USER) >> Spanned.SPAN_USER_SHIFT;
            if (span instanceof SuggestionSpan || userFlags == StylingHelper.XHTML_IGNORE) newText.removeSpan(span);
        }
        BaseInputConnection.removeComposingSpans(newText);
        return newText;
    }

    public static boolean isPlainText(Spanned text) {
        SpannableStringBuilder cleanText = cleanSpans(text);
        CharacterStyle[] style = cleanText.getSpans(0, cleanText.length(), CharacterStyle.class);
        return style.length < 1;
    }

    public static Element append(Element out, Spanned text) {
        SpannableStringBuilder newText = cleanSpans(text);
        doBlock(out, new ArrayList<>(), newText, 0, newText.length());
        return out;
    }

    private static void doBlock(Element out, ArrayList<Object> parents, Spanned text, int start, int end) {
        int next;
        outer:
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, eu.siacs.conversations.ui.text.QuoteSpan.class);
            eu.siacs.conversations.ui.text.QuoteSpan[] quotes = text.getSpans(i, next, QuoteSpan.class);
            for (final var quote : quotes) {
                if (!parents.contains(quote)) {
                    final var us = new ArrayList<>(parents);
                    us.add(quote);
                    next = text.getSpanEnd(quote);
                    doBlock(out.addChild("blockquote"), us, text, i, next);
                    if (next < text.length() && text.charAt(next) == '\n') next++;
                    continue outer;
                }
            }
            withinParagraph(out, text, i, (next == end && text.charAt(end-1) == '\n') ? next - 1 : next);
        }
    }

    private static void withinParagraph(Element outer, Spanned text, int start, int end) {
        int next;
        outer:
        for (int i = start; i < end; i = next) {
            Element out = outer;
            next = text.nextSpanTransition(i, end, CharacterStyle.class);
            CharacterStyle[] style = text.getSpans(i, next, CharacterStyle.class);
            for (int j = 0; j < style.length; j++) {
                final var userFlags = (text.getSpanFlags(style[j]) & Spanned.SPAN_USER) >> Spanned.SPAN_USER_SHIFT;
                if (userFlags == StylingHelper.XHTML_REMOVE) {
                    continue outer;
                }

                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();
                    if ((s & Typeface.BOLD) != 0) {
                        if (userFlags == StylingHelper.XHTML_EMPHASIS) {
                            out = out.addChild("strong");
                        } else {
                            out = out.addChild("b");
                        }
                    }
                    if ((s & Typeface.ITALIC) != 0) {
                        if (userFlags == StylingHelper.XHTML_EMPHASIS) {
                            out = out.addChild("em");
                        } else {
                            out = out.addChild("i");
                        }
                    }
                }
                if (style[j] instanceof TypefaceSpan) {
                    String s = ((TypefaceSpan) style[j]).getFamily();
                    if ("monospace".equals(s)) {
                        out = out.addChild("tt");
                    }
                }
                if (style[j] instanceof SuperscriptSpan) {
                    out = out.addChild("sup");
                }
                if (style[j] instanceof SubscriptSpan) {
                    out = out.addChild("sub");
                }
                if (style[j] instanceof UnderlineSpan) {
                    out = out.addChild("u");
                }
                if (style[j] instanceof StrikethroughSpan) {
                    out = out.addChild("span");
                    out.setAttribute("style", "text-decoration:line-through;");
                }
                if (style[j] instanceof URLSpan) {
                    out = out.addChild("a");
                    out.setAttribute("href", ((URLSpan) style[j]).getURL());
                }
                if (style[j] instanceof ImageSpan) {
                    String source = ((ImageSpan) style[j]).getSource();
                    if (source != null && source.length() > 0 && source.charAt(0) == 'z') {
                        try {
                            source = BobTransfer.uri(Cid.decode(source)).toString();
                        } catch (final Exception e) { }
                    }
                    out = out.addChild("img");
                    out.setAttribute("src", source);
                    out.setAttribute("alt", text.subSequence(i, next).toString());
                    continue outer;
                }
                if (style[j] instanceof AbsoluteSizeSpan) {
                    try {
                        AbsoluteSizeSpan s = ((AbsoluteSizeSpan) style[j]);
                        float sizeDip = s.getSize();
                        if (!s.getDip()) {
                            Class activityThreadClass = Class.forName("android.app.ActivityThread");
                            Application application = (Application) activityThreadClass.getMethod("currentApplication").invoke(null);
                            sizeDip /= application.getResources().getDisplayMetrics().density;
                        }
                        // px in CSS is the equivalance of dip in Android
                        out = out.addChild("span");
                        out.setAttribute("style", String.format("font-size:%.0fpx;", sizeDip));
                    } catch (final Exception e) { }
                }
                if (style[j] instanceof RelativeSizeSpan) {
                    float sizeEm = ((RelativeSizeSpan) style[j]).getSizeChange();
                    out = out.addChild("span");
                    out.setAttribute("style", String.format("font-size:%.0fem;", sizeEm));
                }
                if (style[j] instanceof ForegroundColorSpan) {
                    int color = ((ForegroundColorSpan) style[j]).getForegroundColor();
                    out = out.addChild("span");
                    out.setAttribute("style", String.format("color:#%06X;", 0xFFFFFF & color));
                }
                if (style[j] instanceof BackgroundColorSpan) {
                    int color = ((BackgroundColorSpan) style[j]).getBackgroundColor();
                    out = out.addChild("span");
                    out.setAttribute("style", String.format("background-color:#%06X;", 0xFFFFFF & color));
                }
            }
            String content = text.subSequence(i, next).toString();
            boolean prevSpace = false;
            for (int c = 0; c < content.length(); c++) {
                if (content.charAt(c) == '\n') {
                    prevSpace = false;
                    out.addChild("br");
                } else if (prevSpace && content.charAt(c) == ' ') {
                    prevSpace = false;
                    out.addChild(new TextNode("\u00A0"));
                } else {
                    prevSpace = content.charAt(c) == ' ';
                    out.addChild(new TextNode("" + content.charAt(c)));
                }
            }
        }
    }
}
