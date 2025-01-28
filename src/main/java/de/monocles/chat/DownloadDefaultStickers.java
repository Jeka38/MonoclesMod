package de.monocles.chat;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.common.io.ByteStreams;

import eu.siacs.conversations.services.XmppConnectionService;
import io.ipfs.cid.Cid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.MimeUtils;

public class DownloadDefaultStickers extends Service {

    private static final int NOTIFICATION_ID = 20;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private DatabaseBackend mDatabaseBackend;
    private NotificationManager notificationManager;
    private File mStickerDir;
    private OkHttpClient http = null;
    private final HashSet<Uri> pendingPacks = new HashSet<Uri>();
    public final XmppConnectionService xmppConnectionService = new XmppConnectionService();


    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mStickerDir = stickerDir();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (http == null) {
            http = HttpConnectionManager.newBuilder(intent == null ? getResources().getBoolean(R.bool.use_tor)  : intent.getBooleanExtra("tor", getResources().getBoolean(R.bool.use_tor)), intent != null && intent.getBooleanExtra("i2p", getResources().getBoolean(R.bool.use_i2p))).build();
        }
        synchronized(pendingPacks) {
            pendingPacks.add(intent == null || intent.getData() == null ? Uri.parse("https://stickers.cheogram.com/index.json") : intent.getData());
        }
        if (RUNNING.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    download();
                } catch (final Exception e) {
                    Log.d(Config.LOGTAG, "unable to download stickers", e);
                }
                stopForeground(true);
                RUNNING.set(false);
                stopSelf();
            }).start();
            return START_STICKY;
        } else {
            Log.d(Config.LOGTAG, "DownloadDefaultStickers. ignoring start command because already running");
        }
        xmppConnectionService.LoadStickers();
        return START_NOT_STICKY;
    }

    private void oneSticker(JSONObject sticker) throws Exception {
        Response r = http.newCall(new Request.Builder().url(sticker.getString("url")).build()).execute();
        File file = null;
        try {
            file = new File(mStickerDir.getAbsolutePath() + "/" + sticker.getString("pack") + "/" + sticker.getString("name") + "." + MimeUtils.guessExtensionFromMimeType(r.headers().get("content-type")));
            Objects.requireNonNull(file.getParentFile()).mkdirs();
            OutputStream os = new FileOutputStream(file);
            if (r.body() != null) {
                ByteStreams.copy(r.body().byteStream(), os);
            }
            os.close();
        } catch (final Exception e) {
            file = null;
            Log.d(de.monocles.chat.Config.LOGTAG, Objects.requireNonNull(e.getMessage()));
        }

        JSONArray cids = sticker.getJSONArray("cids");
        for (int i = 0; i < cids.length(); i++) {
            Cid cid = Cid.decode(cids.getString(i));
            mDatabaseBackend.saveCid(cid, file, sticker.getString("url"));
        }

        if (file != null) {
            MediaScannerConnection.scanFile(
                    getBaseContext(),
                    new String[] { file.getAbsolutePath() },
                    null,
                    new MediaScannerConnection.MediaScannerConnectionClient() {
                        @Override
                        public void onMediaScannerConnected() {}

                        @Override
                        public void onScanCompleted(String path, Uri uri) {}
                    }
            );
        }

        try {
            File copyright = new File(mStickerDir.getAbsolutePath() + "/" + sticker.getString("pack") + "/copyright.txt");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(copyright, true), "utf-8");
            w.write(sticker.getString("pack"));
            w.write('/');
            w.write(sticker.getString("name"));
            w.write(": ");
            w.write(sticker.getString("copyright"));
            w.write('\n');
            w.close();
        } catch (final Exception e) { }
    }

    private void download() throws Exception {
        Uri jsonUri;
        synchronized(pendingPacks) {
            if (pendingPacks.iterator().hasNext()) {
                jsonUri = pendingPacks.iterator().next();
            } else {
                return;
            }
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle("Downloading Stickers")
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(1, 0, false);
        startForeground(NOTIFICATION_ID, mBuilder.build());

        Response r = http.newCall(new Request.Builder().url(jsonUri.toString()).build()).execute();
        JSONArray stickers = new JSONArray(r.body().string());

        final Progress progress = new Progress(mBuilder, 1, 0);
        for (int i = 0; i < stickers.length(); i++) {
            try {
                oneSticker(stickers.getJSONObject(i));
            } catch (final Exception e) {
                e.printStackTrace();
            }

            final int percentage = i * 100 / stickers.length();
            notificationManager.notify(NOTIFICATION_ID, progress.build(percentage));
        }

        synchronized(pendingPacks) {
            pendingPacks.remove(jsonUri);
        }
        download();
    }

    private File stickerDir() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        final String dir = p.getString("sticker_directory", "Stickers");
        if (dir.startsWith("content://")) {
            Uri uri = Uri.parse(dir);
            uri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            return new File(FileUtils.getPath(getBaseContext(), uri));
        } else {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + dir);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class Progress {
        private final NotificationCompat.Builder builder;
        private final int max;
        private final int count;

        private Progress(NotificationCompat.Builder builder, int max, int count) {
            this.builder = builder;
            this.max = max;
            this.count = count;
        }

        private Notification build(int percentage) {
            builder.setProgress(max * 100, count * 100 + percentage, false);
            return builder.build();
        }
    }
}
