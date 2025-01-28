package eu.siacs.conversations.ui.util;

import static eu.siacs.conversations.ui.IntroActivity.ACTIVITY;
import static eu.siacs.conversations.ui.IntroActivity.MULTICHAT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.lang.ref.WeakReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.IntroActivity;

public class IntroHelper {
  public static void showIntro(Activity activity, boolean mode_multi) {
    new Thread(new showIntoFinisher(activity, mode_multi)).start();
  }

  private static class showIntoFinisher implements Runnable {

    private final WeakReference<Activity> activityReference;
    private final boolean mode_multi;

    private showIntoFinisher(Activity activity, boolean mode_multi) {
      this.activityReference = new WeakReference<>(activity);
      this.mode_multi = mode_multi;
    }

    @Override
    public void run() {
      final Activity activity = activityReference.get();
      if (activity == null) {
        return;
      }
      activity.runOnUiThread(
              () -> {
                SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
                String activityname = activity.getClass().getSimpleName();
                String INTRO = "intro_shown_on_activity_" + activityname + "_MultiMode_" + mode_multi;
                boolean SHOW_INTRO = getPrefs.getBoolean(INTRO, true);


                if (SHOW_INTRO && Config.SHOW_INTRO) {
                  final Intent i = new Intent(activity, IntroActivity.class);
                  i.putExtra(ACTIVITY, activityname);
                  i.putExtra(MULTICHAT, mode_multi);
                  activity.runOnUiThread(() -> activity.startActivity(i));
                }
              });
    }
  }

  public static void SaveIntroShown(Context context, String activity, boolean mode_multi) {
    SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    String INTRO = "intro_shown_on_activity_" + activity + "_MultiMode_" + mode_multi;
    SharedPreferences.Editor e = getPrefs.edit();
    e.putBoolean(INTRO, false);
    e.apply();
  }
}
