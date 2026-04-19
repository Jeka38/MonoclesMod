package p32929.easypasscodelock.Utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by p32929 on 7/17/2018.
 */

public class EasylockSP {
    private static final String TAG = "EasyLock";
    public static SharedPreferences sharedPreferences;

    //
    public static void init(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences("Lockscreen", Context.MODE_PRIVATE);
        }
    }

    //
    @SuppressLint("ApplySharedPref")
    public static void put(String title, boolean value) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip putBoolean(" + title + ")");
            return;
        }
        sharedPreferences.edit().putBoolean(title, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static void put(String title, float value) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip putFloat(" + title + ")");
            return;
        }
        sharedPreferences.edit().putFloat(title, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static void put(String title, int value) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip putInt(" + title + ")");
            return;
        }
        sharedPreferences.edit().putInt(title, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static void put(String title, long value) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip putLong(" + title + ")");
            return;
        }
        sharedPreferences.edit().putLong(title, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static void put(String title, String value) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip putString(" + title + ")");
            return;
        }
        sharedPreferences.edit().putString(title, value).commit();
    }

    //
    public static boolean getBoolean(String title, boolean defaultValue) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Return default for getBoolean(" + title + ")");
            return defaultValue;
        }
        return sharedPreferences.getBoolean(title, defaultValue);
    }

    public static float getFloat(String title, float defaultValue) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Return default for getFloat(" + title + ")");
            return defaultValue;
        }
        return sharedPreferences.getFloat(title, defaultValue);
    }

    public static int getInt(String title, int defaultValue) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Return default for getInt(" + title + ")");
            return defaultValue;
        }
        return sharedPreferences.getInt(title, defaultValue);
    }

    public static long getLong(String title, long defaultValue) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Return default for getLong(" + title + ")");
            return defaultValue;
        }
        return sharedPreferences.getLong(title, defaultValue);
    }

    public static String getString(String title, String defaultValue) {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Return default for getString(" + title + ")");
            return defaultValue;
        }
        return sharedPreferences.getString(title, defaultValue);
    }

    //
    public static void clearAll() {
        if (sharedPreferences == null) {
            Log.e(TAG, "EasylockSP is not initialized. Skip clearAll()");
            return;
        }
        sharedPreferences.edit().clear().commit();
    }

}
