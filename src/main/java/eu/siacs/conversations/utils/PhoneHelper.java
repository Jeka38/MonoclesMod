package eu.siacs.conversations.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;

public class PhoneHelper {

    @SuppressLint("HardwareIds")
    public static String getAndroidId(final Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static Uri getProfilePictureUri(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        final String[] projection = new String[]{Profile._ID, Profile.PHOTO_URI};
        final Cursor cursor;
        try {
            cursor =
                    context.getContentResolver()
                            .query(Profile.CONTENT_URI, projection, null, null, null);
        } catch (Throwable e) {
            return null;
        }
        if (cursor == null) {
            return null;
        }
        final String uri = cursor.moveToFirst() ? cursor.getString(1) : null;
        cursor.close();
        return uri == null ? null : Uri.parse(uri);
    }

    public static String getVersionName(Context context) {
        final String packageName = context == null ? null : context.getPackageName();
        if (packageName != null) {
            try {
                return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            } catch (final PackageManager.NameNotFoundException e) {
                return "unknown";
            } catch (final RuntimeException e) {
                return "unknown";
            }
        } else {
            return "unknown";
        }
    }

    public static String getOSVersion(Context context) {
        return "Android/" + android.os.Build.MODEL + "/" + android.os.Build.VERSION.RELEASE;
    }

    public static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }
}
