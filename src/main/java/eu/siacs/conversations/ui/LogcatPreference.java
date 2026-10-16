package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

public class LogcatPreference extends Preference {

    public LogcatPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public LogcatPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        super.onClick();
        getContext().startActivity(new Intent(getContext(), LogcatActivity.class));
    }
}
