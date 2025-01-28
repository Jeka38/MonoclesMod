package eu.siacs.conversations.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.NumberParseException;

public class PhoneNumberContact extends AbstractPhoneContact {

    private final String phoneNumber;
    private final String typeLabel;
    private final Collection<String> groups;


    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public Collection<String> getTags() {
        Collection<String> tags = new ArrayList(groups);
        tags.add(typeLabel);
        return tags;
    }

    public Collection<String> getGroups() {
        return groups;
    }

    private PhoneNumberContact(Context context, Cursor cursor, Collection<String> groups) throws IllegalArgumentException {
        super(cursor);
        try {
            this.phoneNumber = PhoneNumberUtilWrapper.normalize(context, cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
            this.typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.getResources(),
                    cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)),
                    cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL))
            ).toString();
            this.groups = groups;
        } catch (NumberParseException | NullPointerException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static ImmutableMap<String, PhoneNumberContact> load(Context context) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ImmutableMap.of();
        }

        final HashMap<String, String> groupTitles = new HashMap<>();
        try (final Cursor groups = context.getContentResolver().query(
                ContactsContract.Groups.CONTENT_URI,
                new String[]{
                        ContactsContract.Groups._ID,
                        ContactsContract.Groups.TITLE
                }, null, null, null
        )) {
            while (groups != null && groups.moveToNext()) {
                groupTitles.put(groups.getString(0), groups.getString(1));
            }
        } catch (final Exception e) {
            return ImmutableMap.of();
        }

        final Multimap<String, String> contactGroupMap = HashMultimap.create();
        try(final Cursor contactGroups = context.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
                },
                ContactsContract.Data.MIMETYPE + "=?",
                new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE},
                null
        )) {
            while (contactGroups != null && contactGroups.moveToNext()) {
                final String groupTitle = groupTitles.get(contactGroups.getString(1));
                if (groupTitle != null) contactGroupMap.put(contactGroups.getString(0), groupTitle);
            }
        } catch (final Exception e) {
            return ImmutableMap.of();
        }

        final String[] PROJECTION = new String[]{
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.PHOTO_URI,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        final HashMap<String, PhoneNumberContact> contacts = new HashMap<>();
        try (final Cursor cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, PROJECTION, null, null, null)){
            while (cursor != null && cursor.moveToNext()) {
                try {
                    final PhoneNumberContact contact = new PhoneNumberContact(context, cursor, contactGroupMap.get(cursor.getString(1)));
                    final PhoneNumberContact preexisting = contacts.get(contact.getPhoneNumber());
                    if (preexisting == null || preexisting.rating() < contact.rating()) {
                        contacts.put(contact.getPhoneNumber(), contact);
                    }
                } catch (final IllegalArgumentException ignored) {

                }
            }
        } catch (final Exception e) {
            return ImmutableMap.of();
        }
        return ImmutableMap.copyOf(contacts);
    }

    public static PhoneNumberContact findByUriOrNumber(Collection<PhoneNumberContact> haystack, Uri uri, String number) {
        final PhoneNumberContact byUri = findByUri(haystack, uri);
        return byUri != null || number == null ? byUri : findByNumber(haystack, number);
    }

    public static PhoneNumberContact findByUri(Collection<PhoneNumberContact> haystack, Uri needle) {
        for (PhoneNumberContact contact : haystack) {
            if (needle.equals(contact.getLookupUri())) {
                return contact;
            }
        }
        return null;
    }

    private static PhoneNumberContact findByNumber(Collection<PhoneNumberContact> haystack, String needle) {
        for (PhoneNumberContact contact : haystack) {
            if (needle.equals(contact.getPhoneNumber())) {
                return contact;
            }
        }
        return null;
    }
}
