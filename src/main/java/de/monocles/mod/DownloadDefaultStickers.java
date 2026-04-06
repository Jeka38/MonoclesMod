package de.monocles.mod;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.common.io.ByteStreams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.MimeUtils;
import io.ipfs.cid.Cid;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadDefaultStickers extends Service {

    private static final int NOTIFICATION_ID = 20;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Pattern STICKER_URL_PATTERN = Pattern.compile("(https?://[^\\\"'\\s>]+\\.(?:webp|png|jpg|jpeg))", Pattern.CASE_INSENSITIVE);
    private DatabaseBackend mDatabaseBackend;
    private NotificationManager notificationManager;
    private File mStickerDir;
    private OkHttpClient http = null;
    private final HashSet<Uri> pendingPacks = new HashSet<>();
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
            http = HttpConnectionManager.newBuilder(intent == null ? getResources().getBoolean(R.bool.use_tor) : intent.getBooleanExtra("tor", getResources().getBoolean(R.bool.use_tor)), intent != null && intent.getBooleanExtra("i2p", getResources().getBoolean(R.bool.use_i2p))).build();
        }
        final Set<Uri> normalized = normalizeSourceUris(intent == null ? null : intent.getData());
        if (normalized.isEmpty()) {
            Log.d(Config.LOGTAG, "DownloadDefaultStickers: no sticker source provided");
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (pendingPacks) {
            pendingPacks.addAll(normalized);
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

    private Set<Uri> normalizeSourceUris(final Uri source) {
        final Set<Uri> result = new HashSet<>();
        if (source == null) return result;

        final String host = source.getHost() == null ? "" : source.getHost().toLowerCase(Locale.US);
        final String path = source.getPath() == null ? "" : source.getPath();

        if ("stickers.cheogram.com".equals(host)) {
            result.add(source);
            return result;
        }

        if (("tlgrm.ru".equals(host) || "www.tlgrm.ru".equals(host)) && path.startsWith("/stickers/")) {
            final String slug = path.substring("/stickers/".length()).split("/")[0];
            if (!slug.isEmpty()) {
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug));
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug + ".json"));
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug + "/index.json"));
                result.add(source);
            }
        }

        if (("t.me".equals(host) || "telegram.me".equals(host)) && path.startsWith("/addstickers/")) {
            final String slug = path.substring("/addstickers/".length()).split("/")[0];
            if (!slug.isEmpty()) {
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug));
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug + ".json"));
                result.add(Uri.parse("https://stickers.cheogram.com/telegram/" + slug + "/index.json"));
            }
        }

        return result;
    }

    private void oneSticker(JSONObject sticker) throws Exception {
        Response r = http.newCall(new Request.Builder().url(sticker.getString("url")).build()).execute();
        File file = null;
        try {
            final String ext = MimeUtils.guessExtensionFromMimeType(r.headers().get("content-type"));
            file = new File(mStickerDir.getAbsolutePath() + "/" + sticker.getString("pack") + "/" + sticker.getString("name") + "." + (ext == null ? "webp" : ext));
            Objects.requireNonNull(file.getParentFile()).mkdirs();
            OutputStream os = new FileOutputStream(file);
            if (r.body() != null) {
                ByteStreams.copy(r.body().byteStream(), os);
            }
            os.close();
        } catch (final Exception e) {
            file = null;
            Log.d(de.monocles.mod.Config.LOGTAG, Objects.requireNonNull(e.getMessage()));
        }

        JSONArray cids = sticker.optJSONArray("cids");
        if ((cids == null || cids.length() == 0) && file != null) {
            try (FileInputStream fis = new FileInputStream(file)) {
                final Cid[] generated = CryptoHelper.cid(fis, new String[]{"SHA-256", "SHA-1", "SHA-512"});
                cids = new JSONArray();
                for (final Cid generatedCid : generated) {
                    cids.put(generatedCid.toString());
                }
            } catch (final Exception ignored) {
            }
        }
        if (cids != null) {
            for (int i = 0; i < cids.length(); i++) {
                Cid cid = Cid.decode(cids.getString(i));
                mDatabaseBackend.saveCid(cid, file, sticker.getString("url"));
            }
        }

        if (file != null) {
            MediaScannerConnection.scanFile(
                    getBaseContext(),
                    new String[]{file.getAbsolutePath()},
                    null,
                    new MediaScannerConnection.MediaScannerConnectionClient() {
                        @Override
                        public void onMediaScannerConnected() {
                        }

                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                        }
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
            w.write(sticker.optString("copyright", "tlgrm.ru"));
            w.write('\n');
            w.close();
        } catch (final Exception e) {
        }
    }

    private JSONArray parseStickersFromResponse(final Uri sourceUri, final String responseBody) {
        try {
            return new JSONArray(responseBody);
        } catch (final Exception ignored) {
        }

        final String host = sourceUri.getHost() == null ? "" : sourceUri.getHost().toLowerCase(Locale.US);
        if (!host.contains("tlgrm.ru")) {
            return null;
        }
        final String path = sourceUri.getPath() == null ? "" : sourceUri.getPath();
        if (!path.startsWith("/stickers/")) {
            return null;
        }
        final String pack = path.substring("/stickers/".length()).split("/")[0];
        if (pack.isEmpty()) {
            return null;
        }

        final JSONArray stickers = new JSONArray();
        final HashSet<String> seenUrls = new HashSet<>();
        final Matcher matcher = STICKER_URL_PATTERN.matcher(responseBody);
        int index = 0;
        while (matcher.find()) {
            final String stickerUrl = matcher.group(1);
            if (stickerUrl == null || !seenUrls.add(stickerUrl)) continue;
            try {
                final JSONObject one = new JSONObject();
                one.put("url", stickerUrl.replace("\\/", "/"));
                one.put("pack", pack);
                one.put("name", "sticker_" + (++index));
                one.put("copyright", "tlgrm.ru");
                one.put("cids", new JSONArray());
                stickers.put(one);
            } catch (final Exception ignored) {
            }
        }

        return stickers.length() == 0 ? null : stickers;
    }

    private void download() throws Exception {
        Uri jsonUri;
        synchronized (pendingPacks) {
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

        JSONArray stickers = null;
        try {
            Response r = http.newCall(new Request.Builder().url(jsonUri.toString()).build()).execute();
            final String responseBody = r.body() == null ? "" : r.body().string();
            stickers = parseStickersFromResponse(jsonUri, responseBody);
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "failed sticker source " + jsonUri + ": " + e.getMessage());
        }

        if (stickers == null) {
            synchronized (pendingPacks) {
                pendingPacks.remove(jsonUri);
            }
            download();
            return;
        }

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

        synchronized (pendingPacks) {
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
