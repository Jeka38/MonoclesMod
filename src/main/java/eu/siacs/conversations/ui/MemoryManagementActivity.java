package eu.siacs.conversations.ui;

import static eu.siacs.conversations.persistance.FileBackend.AUDIOS;
import static eu.siacs.conversations.persistance.FileBackend.FILES;
import static eu.siacs.conversations.persistance.FileBackend.IMAGES;
import static eu.siacs.conversations.persistance.FileBackend.VIDEOS;
import static eu.siacs.conversations.utils.StorageHelper.getAppMediaDirectory;
import static eu.siacs.conversations.utils.StorageHelper.getConversationsDirectory;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.lang.ref.WeakReference;

import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MemoryManagementActivity extends XmppActivity {

    private static TextView disk_storage;
    private static TextView media_usage;
    private ImageButton delete_media;
    private static TextView pictures_usage;
    private ImageButton delete_pictures;
    private static TextView videos_usage;
    private ImageButton delete_videos;
    private static TextView files_usage;
    private ImageButton delete_files;
    private static TextView audios_usage;
    private ImageButton delete_audios;

    static String totalMemory = "...";
    static String mediaUsage = "...";
    static String picturesUsage = "...";
    static String videosUsage = "...";
    static String filesUsage = "...";
    static String audiosUsage = "...";

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.find(this));
        setContentView(R.layout.activity_memory_management);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        disk_storage = findViewById(R.id.disk_storage);
        media_usage = findViewById(R.id.media_usage);
        delete_media = findViewById(R.id.action_delete_media);
        pictures_usage = findViewById(R.id.pictures_usage);
        delete_pictures = findViewById(R.id.action_delete_pictures);
        videos_usage = findViewById(R.id.videos_usage);
        delete_videos = findViewById(R.id.action_delete_videos);
        files_usage = findViewById(R.id.files_usage);
        delete_files = findViewById(R.id.action_delete_files);
        audios_usage = findViewById(R.id.audios_usage);
        delete_audios = findViewById(R.id.action_delete_audios);
    }

    @Override
    protected void onStart() {
        super.onStart();
        new getMemoryUsages(this).execute();
        delete_media.setOnClickListener(view -> {
            deleteMedia(new File(getAppMediaDirectory(this, null)));
        });
        delete_pictures.setOnClickListener(view -> {
            deleteMedia(new File(getConversationsDirectory(this, IMAGES).getAbsolutePath()));
        });
        delete_videos.setOnClickListener(view -> {
            deleteMedia(new File(getConversationsDirectory(this, VIDEOS).getAbsolutePath()));
        });
        delete_files.setOnClickListener(view -> {
            deleteMedia(new File(getConversationsDirectory(this, FILES).getAbsolutePath()));
        });
        delete_audios.setOnClickListener(view -> {
            deleteMedia(new File(getConversationsDirectory(this, AUDIOS).getAbsolutePath()));
        });
    }


    private void deleteMedia(File dir) {
        final String file;
        if (dir.toString().endsWith(IMAGES)) {
            file = getString(R.string.images);
        } else if (dir.toString().endsWith(VIDEOS)) {
            file = getString(R.string.videos);
        } else if (dir.toString().endsWith(FILES)) {
            file = getString(R.string.files);
        } else if (dir.toString().endsWith(AUDIOS)) {
            file = getString(R.string.audios);
        } else {
            file = getString(R.string.all_media_files);
        }
        final androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_files_dialog);
        builder.setMessage(getResources().getString(R.string.delete_files_dialog_msg, file));
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            new Thread(new deleteFilesInDirFinisher(dir, this, xmppConnectionService)).start();
        });
        builder.create().show();
    }

    private static class deleteFilesInDirFinisher implements Runnable {

        private final File dir;
        private final WeakReference<Activity> activityReference;
        private final XmppConnectionService service;

        private deleteFilesInDirFinisher(File dir, Activity activity, XmppConnectionService service) {
            this.dir = dir;
            this.activityReference = new WeakReference<>(activity);
            this.service = service;
        }

        @Override
        public void run() {
            final Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            service.getFileBackend().deleteFilesInDir(dir);
            activity.runOnUiThread(() -> new getMemoryUsages(activity).execute());
        }
    }

    static class getMemoryUsages extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public getMemoryUsages(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            disk_storage.setText(totalMemory);
            media_usage.setText(mediaUsage);
            pictures_usage.setText(picturesUsage);
            videos_usage.setText(videosUsage);
            files_usage.setText(filesUsage);
            audios_usage.setText(audiosUsage);
        }

        @Override
        protected Void doInBackground(Void... params) {
            totalMemory = UIHelper.filesizeToString(FileBackend.getDiskSize());
            mediaUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getAppMediaDirectory(mContext, null))));
            picturesUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getConversationsDirectory(mContext, IMAGES).getAbsolutePath())));
            videosUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getConversationsDirectory(mContext, VIDEOS).getAbsolutePath())));
            filesUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getConversationsDirectory(mContext, FILES).getAbsolutePath())));
            audiosUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getConversationsDirectory(mContext, AUDIOS).getAbsolutePath())));
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            disk_storage.setText(totalMemory);
            media_usage.setText(mediaUsage);
            pictures_usage.setText(picturesUsage);
            videos_usage.setText(videosUsage);
            files_usage.setText(filesUsage);
            audios_usage.setText(audiosUsage);
        }
    }
}