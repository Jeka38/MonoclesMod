package eu.siacs.conversations.entities;

import androidx.annotation.DrawableRes;

import com.google.common.base.Strings;

import eu.siacs.conversations.R;

public class RtpSessionStatus {

    public final boolean successful;
    public final long duration;


    public RtpSessionStatus(boolean successful, long duration) {
        this.successful = successful;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return successful + ":" + duration;
    }

    public static RtpSessionStatus of(final String body) {
        final String[] parts = Strings.nullToEmpty(body).split(":", 2);
        long duration = 0;
        if (parts.length == 2) {
            try {
                duration = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                //do nothing
            }
        }
        boolean made;
        try {
            made = Boolean.parseBoolean(parts[0]);
        } catch (Exception e) {
            made = false;
        }
        return new RtpSessionStatus(made, duration);
    }

    public static @DrawableRes int getDrawable(final boolean received, final boolean successful, final boolean darkTheme) {
        if (received) {
            if (successful) {
                return darkTheme ? R.drawable.round_call_received_18 : R.drawable.round_call_received_18;
            } else {
                return darkTheme ? R.drawable.round_call_missed_18 : R.drawable.round_call_missed_18;
            }
        } else {
            if (successful) {
                return darkTheme ? R.drawable.round_call_made_18 : R.drawable.round_call_made_18;
            } else {
                return darkTheme ? R.drawable.round_call_missed_outgoing_18 : R.drawable.round_call_missed_outgoing_18;
            }
        }
    }
}
