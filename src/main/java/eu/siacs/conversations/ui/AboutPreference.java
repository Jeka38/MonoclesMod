package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;

import com.google.common.base.Strings;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.PhoneHelper;

public class AboutPreference extends Preference {
    public AboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummary();
    }

    public AboutPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), AboutActivity.class);
        getContext().startActivity(intent);
    }

    private void setSummary() {
        setSummary(String.format("%s%s %s (%s)", getContext().getString(R.string.app_name), " " + BuildConfig.VERSION_NAME, im.conversations.webrtc.BuildConfig.WEBRTC_VERSION, Strings.nullToEmpty(Build.DEVICE)));
    }
}

