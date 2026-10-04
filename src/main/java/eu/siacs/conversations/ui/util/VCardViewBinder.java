package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.VCard;

/**
 * Renders a {@link VCard} as a list of label/value rows for the contact details screens, using the
 * same field order and icons as Psi's info dialog.
 */
public final class VCardViewBinder {

    private VCardViewBinder() {
    }

    /**
     * Appends one row per populated field to {@code container}.
     *
     * @param onUri called when the user taps a row that carries an addressable value (email, phone,
     *     website, …); the row is only clickable when it has one.
     */
    public static void bind(
            final Context context,
            final LinearLayout container,
            final VCard vcard,
            @Nullable final OnVCardValueClick onUri) {
        container.removeAllViews();
        if (vcard == null) {
            return;
        }

        final String birthday = vcard.getDisplayableBirthday();
        add(context, container, R.string.vcard_full_name, vcard.fullName, R.attr.icon_contact, null, onUri);
        add(context, container, R.string.vcard_nickname, vcard.nickName, R.attr.icon_contact, null, onUri);
        add(context, container, R.string.vcard_birthday, birthday, R.attr.icon_birthday, null, onUri);
        add(context, container, R.string.vcard_organization, vcard.organization, R.attr.icon_org, null, onUri);
        add(context, container, R.string.vcard_organization_unit, vcard.organizationUnit, R.attr.icon_org, null, onUri);
        add(context, container, R.string.vcard_title, vcard.title, R.attr.icon_org, null, onUri);
        add(context, container, R.string.vcard_role, vcard.role, R.attr.icon_org, null, onUri);
        add(
                context,
                container,
                R.string.vcard_email,
                vcard.email,
                R.attr.icon_email,
                vcard.email == null ? null : Uri.parse("mailto:" + vcard.email),
                onUri);
        add(
                context,
                container,
                R.string.vcard_phone,
                vcard.phone,
                R.attr.icon_call,
                vcard.phone == null ? null : Uri.parse("tel:" + vcard.phone),
                onUri);
        add(
                context,
                container,
                R.string.vcard_homepage,
                vcard.url,
                R.attr.icon_link,
                normalizeUrl(vcard.url),
                onUri);

        final String address = joinComma(
                vcard.country, vcard.region, vcard.city, vcard.street, vcard.extAddress, vcard.postalCode);
        add(context, container, R.string.vcard_address, address, R.attr.icon_gps_fixed, null, onUri);
        add(context, container, R.string.vcard_note, vcard.note, R.attr.icon_help, null, onUri);
    }

    private static void add(
            final Context context,
            final LinearLayout container,
            @androidx.annotation.StringRes final int label,
            @Nullable final String value,
            @AttrRes final int iconAttr,
            @Nullable final Uri uri,
            @Nullable final OnVCardValueClick callback) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        final LinearLayout row = new LinearLayout(context);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final Drawable icon = resolveDrawable(context, iconAttr);
        if (icon != null) {
            final android.widget.ImageView imageView = new android.widget.ImageView(context);
            final int size = dp(context, 24);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            imageView.setImageDrawable(icon);
            imageView.setContentDescription(null);
            row.addView(imageView);
        }

        final LinearLayout texts = new LinearLayout(context);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        texts.setOrientation(LinearLayout.VERTICAL);
        final int start = dp(context, 16);
        texts.setPaddingRelative(start, 0, 0, 0);

        final TextView caption = new TextView(context);
        caption.setText(label);
        caption.setTextAppearance(context, R.style.TextAppearance_Conversations_Caption);
        caption.setTextColor(resolveColor(context, R.attr.button_text_color_disabled));
        texts.addView(caption);

        final TextView text = new TextView(context);
        text.setText(value);
        text.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1);
        text.setTextColor(resolveColor(context, R.attr.text_Color_Main));
        text.setTextIsSelectable(true);
        texts.addView(text);
        row.addView(texts);
        container.addView(row);

        if (uri != null && callback != null) {
            row.setBackgroundResource(resolveSelectableBackground(context));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> callback.onVCardValueClicked(uri));
        }
    }

    @Nullable
    private static Uri normalizeUrl(@Nullable final String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        final String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Uri.parse(trimmed);
        }
        return Uri.parse("http://" + trimmed);
    }

    @Nullable
    private static String joinComma(@Nullable final String... parts) {
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(part.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    @Nullable
    private static Drawable resolveDrawable(final Context context, @AttrRes final int attr) {
        final TypedArray array = context.obtainStyledAttributes(new int[] {attr});
        try {
            return array.getDrawable(0);
        } catch (final Exception e) {
            return null;
        } finally {
            array.recycle();
        }
    }

    private static int resolveColor(final Context context, @AttrRes final int attr) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            return context.getResources().getColor(value.resourceId);
        }
        return resolveColor(context, android.R.attr.textColorPrimary);
    }

    private static int resolveSelectableBackground(final Context context) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private static int dp(final Context context, final int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public interface OnVCardValueClick {
        void onVCardValueClicked(Uri uri);
    }
}
