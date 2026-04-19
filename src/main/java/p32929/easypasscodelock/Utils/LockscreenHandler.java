package p32929.easypasscodelock.Utils;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Created by p32929 on 11/16/17.
 */

public class LockscreenHandler extends AppCompatActivity implements ComponentCallbacks2 {
    private static boolean WentToBackground = false;
    private final String TAG = "EasyLock";
    private String packageName = "";

    @Override
    public void onTrimMemory(int i) {
        super.onTrimMemory(i);
        try {
            ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
            if (am == null) {
                return;
            }
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            if (taskInfo == null || taskInfo.isEmpty() || taskInfo.get(0).topActivity == null) {
                return;
            }
            Log.d("Activity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());

            ComponentName componentInfo = taskInfo.get(0).topActivity;
            packageName = componentInfo.getPackageName();
            if (!packageName.equals(getPackageName()) && i == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                // We're in the Background
                WentToBackground = true;
                Log.d(TAG, "WentToBackground: " + WentToBackground);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "EasyLock onTrimMemory error", e);
            Toast.makeText(this, "EasyLock error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            EasylockSP.init(getApplicationContext());

            if (WentToBackground && EasylockSP.getString("password", null) != null) {
                // We're in the foreground & password != null
                WentToBackground = false;
                Log.d(TAG, "WentToBackground: " + WentToBackground);

                EasyLock.checkPassword(this);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "EasyLock onResume error", e);
            Toast.makeText(this, "EasyLock error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
