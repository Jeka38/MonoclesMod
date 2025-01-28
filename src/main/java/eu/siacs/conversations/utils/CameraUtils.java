package eu.siacs.conversations.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;
import fr.xgouchet.axml.CompressedXmlParser;

public class CameraUtils {

    public PackageInfo packageInfo;
    public List<ComponentName> componentNames;

    public CameraUtils(PackageInfo packageInfo, List<ComponentName> componentNames) {
        this.packageInfo = packageInfo;
        this.componentNames = componentNames;
    }


    public static List<CameraUtils> getCameraApps(Context context) {
        //Step 1 - Get apps with Camera permission
        List<PackageInfo> cameraPermissionPackages = getPackageInfosWithCameraPermission(context);
        //Step 2 - Filter out apps with the correct intent-filter(s)
        List<CameraUtils> cameraApps = new ArrayList<CameraUtils>();
        for (PackageInfo somePackage : cameraPermissionPackages) {
            try {
                //Step 2a - Get the AndroidManifest.xml
                Document doc = readAndroidManifestFromPackageInfo(somePackage);
                //Step 2b - Get Camera ComponentNames from Manifest
                List<ComponentName> componentNames = getCameraComponentNamesFromDocument(doc);
                if (componentNames.size() == 0) {
                    continue; //This is not a Camera app
                }
                //Step 2c - Create CameraAppModel
                CameraUtils cameraApp = new CameraUtils(somePackage, componentNames);
                //Step 3 - check if app is enabled
                ApplicationInfo ai = context.getPackageManager().getApplicationInfo(cameraApp.packageInfo.packageName, 0);
                if (ai.enabled) {
                    cameraApps.add(cameraApp);
                }
            } catch (Exception e) {
                //ignore
            }
        }
        return cameraApps;
    }

    public static List<PackageInfo> getPackageInfosWithCameraPermission(Context context) {
        //Get a list of compatible apps
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        ArrayList<PackageInfo> cameraPermissionPackages = new ArrayList<PackageInfo>();
        //filter out only camera apps
        for (PackageInfo somePackage : installedPackages) {
            //- A camera app should have the Camera permission
            boolean hasCameraPermission = false;
            if (somePackage.requestedPermissions == null || somePackage.requestedPermissions.length == 0) {
                continue;
            }
            for (String requestPermission : somePackage.requestedPermissions) {
                if (requestPermission.equals(Manifest.permission.CAMERA)) {
                    //Ask for Camera permission, now see if it's granted.
                    if (pm.checkPermission(Manifest.permission.CAMERA, somePackage.packageName) == PackageManager.PERMISSION_GRANTED) {
                        hasCameraPermission = true;
                        break;
                    }
                }
            }
            if (hasCameraPermission) {
                cameraPermissionPackages.add(somePackage);
            }
        }
        return cameraPermissionPackages;
    }

    public static Document readAndroidManifestFromPackageInfo(PackageInfo packageInfo) throws IOException {
        File apkFile = new File(packageInfo.applicationInfo.publicSourceDir);
        //Get AndroidManifest.xml from APK
        ZipFile apkZipFile = new ZipFile(apkFile, ZipFile.OPEN_READ);
        ZipEntry manifestEntry = apkZipFile.getEntry("AndroidManifest.xml");
        InputStream manifestInputStream = apkZipFile.getInputStream(manifestEntry);
        try {
            return new CompressedXmlParser().parseDOM(manifestInputStream);
        } catch (Exception e) {
            throw new IOException("Error reading AndroidManifest", e);
        }
    }

    public static List<ComponentName> getCameraComponentNamesFromDocument(Document doc) {
        @SuppressLint("InlinedApi")
        String[] correctActions = {MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_IMAGE_CAPTURE_SECURE, MediaStore.ACTION_VIDEO_CAPTURE};
        ArrayList<ComponentName> componentNames = new ArrayList<ComponentName>();
        Element manifestElement = (Element) doc.getElementsByTagName("manifest").item(0);
        String packageName = manifestElement.getAttribute("package");
        Element applicationElement = (Element) manifestElement.getElementsByTagName("application").item(0);
        NodeList activities = applicationElement.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activityElement = (Element) activities.item(i);
            String activityName = activityElement.getAttribute("android:name");
            NodeList intentFiltersList = activityElement.getElementsByTagName("intent-filter");
            for (int j = 0; j < intentFiltersList.getLength(); j++) {
                Element intentFilterElement = (Element) intentFiltersList.item(j);
                NodeList actionsList = intentFilterElement.getElementsByTagName("action");
                for (int k = 0; k < actionsList.getLength(); k++) {
                    Element actionElement = (Element) actionsList.item(k);
                    String actionName = actionElement.getAttribute("android:name");
                    for (String correctAction : correctActions) {
                        if (actionName.equals(correctAction)) {
                            //this activity has an intent filter with a correct action, add this to the list.
                            componentNames.add(new ComponentName(packageName, activityName));
                        }
                    }
                }
            }
        }
        return componentNames;
    }

    public static void showCameraChooser(final Activity activity, final List<CameraUtils> cameraAppModels) {
        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_camera_chooser);
        dialog.setCancelable(true);
        ListView listView = dialog.findViewById(R.id.chooserDialogListView);
        listView.setAdapter(new CameraAppListViewAdapter(activity, cameraAppModels));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
            p.edit().putInt(SettingsActivity.CAMERA_CHOICE, position).apply();
            dialog.dismiss();
        });
        dialog.show();
    }

    public static ComponentName getCameraApp(CameraUtils cameraApp) {
        Log.d(Config.LOGTAG, "CameraApp " + cameraApp.componentNames.get(0));
        return cameraApp.componentNames.get(0);
    }

    static class CameraAppListViewAdapter extends ArrayAdapter<CameraUtils> {
        private Activity activity;
        private PackageManager pm;

        CameraAppListViewAdapter(Activity activity, List<CameraUtils> cameraApps) {
            super(activity, R.layout.camera_chooser_item, cameraApps);
            this.activity = activity;
            this.pm = activity.getPackageManager();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            //Get info
            CameraUtils cameraApp = getItem(position);
            ComponentName componentName = cameraApp.componentNames.get(0);
            CharSequence appName = pm.getApplicationLabel(cameraApp.packageInfo.applicationInfo);
            CharSequence appDesc = null; //cameraApp.componentNames.get(0).getShortClassName();
            Drawable appIcon = null;
            try {
                appIcon = pm.getActivityIcon(componentName);
            } catch (Exception e) {
                //ignore
            }

            //Set UI
            if (convertView == null) {
                convertView = activity.getLayoutInflater().inflate(R.layout.camera_chooser_item, parent, false);
            }
            TextView firstLine = convertView.findViewById(R.id.firstLine);
            firstLine.setText(appName);
            TextView secondLine = convertView.findViewById(R.id.secondLine);
            if (appDesc == null) {
                secondLine.setVisibility(View.GONE);
                secondLine.setText("");
            } else {
                secondLine.setVisibility(View.VISIBLE);
                secondLine.setText(appDesc);
            }
            ImageView imageView = convertView.findViewById(R.id.icon);
            imageView.setImageResource(R.drawable.ic_android_black_48dp); //reset
            if (appIcon != null) {
                imageView.setImageDrawable(appIcon);
            }
            return convertView;
        }
    }

}
