package de.monocles.mod;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import eu.siacs.conversations.R;

public class StickerDownloadService extends Service {

    private static final String TAG = "StickerDownloadService";
    private static final String CHANNEL_ID = "sticker_download_channel";
    private static final int NOTIFICATION_ID = 101;

    private AuthorizationHandler authHandler;
    private StickerDownloader downloader;
    private String pendingPackName;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        TdlibManager.getInstance().init(this::handleUpdate, (verbosityLevel, message) -> Log.d(TAG, "TDLib Log (" + verbosityLevel + "): " + message));

        authHandler = new AuthorizationHandler(this, new AuthorizationHandler.AuthorizationListener() {
            @Override
            public void onAuthorizationStateUpdated(TdApi.AuthorizationState state) {
                Log.d(TAG, "Auth state: " + state);
            }

            @Override
            public void onReady() {
                if (pendingPackName != null) {
                    downloader.downloadPack(pendingPackName);
                    pendingPackName = null;
                }
            }

            @Override
            public void onError(String message) {
                updateNotification("Error: " + message, 0);
            }

            @Override
            public void onWaitPhoneNumber() {
                updateNotification("Authorization required: Enter phone number in app", 0);
            }

            @Override
            public void onWaitCode() {
                updateNotification("Authorization required: Enter code in app", 0);
            }

            @Override
            public void onWaitPassword() {
                updateNotification("Authorization required: Enter password in app", 0);
            }

            @Override
            public void onWaitRegistration() {
                updateNotification("Authorization required: Register user in app", 0);
            }
        });

        downloader = new StickerDownloader(new StickerDownloader.DownloadListener() {
            @Override
            public void onProgress(int percentage) {
                updateNotification("Downloading stickers...", percentage);
            }

            @Override
            public void onCompleted(String packName) {
                updateNotification("Download completed: " + packName, 100);
                stopForeground(false);
                stopSelf();
            }

            @Override
            public void onError(String message) {
                updateNotification("Error: " + message, 0);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getData() != null) {
            Uri uri = intent.getData();
            String packName = uri.getQueryParameter("set");
            if (packName != null) {
                pendingPackName = packName;
                startForeground(NOTIFICATION_ID, createNotification("Starting download...", 0));
                // If already ready, start immediately
                TdlibManager.getInstance().send(new TdApi.GetAuthorizationState(), result -> {
                    if (result instanceof TdApi.AuthorizationStateReady) {
                        downloader.downloadPack(pendingPackName);
                        pendingPackName = null;
                    }
                });
            }
        }
        return START_NOT_STICKY;
    }

    private void handleUpdate(TdApi.Object object) {
        if (object instanceof TdApi.Update) {
            authHandler.handleUpdate((TdApi.Update) object);
            downloader.handleUpdate((TdApi.Update) object);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Sticker Download", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String content, int progress) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Telegram Sticker Downloader")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(100, progress, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(progress < 100)
                .build();
    }

    private void updateNotification(String content, int progress) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content, progress));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TdlibManager.getInstance().stop();
    }
}
