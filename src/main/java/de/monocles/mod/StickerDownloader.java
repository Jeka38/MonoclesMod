package de.monocles.mod;

import android.os.Environment;
import android.util.Log;
import org.drinkless.tdlib.TdApi;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StickerDownloader {

    private static final String TAG = "StickerDownloader";

    public interface DownloadListener {
        void onProgress(int percentage);
        void onCompleted(String packName);
        void onError(String message);
    }

    private final DownloadListener listener;
    private final ConcurrentHashMap<Integer, DownloadTask> pendingDownloads = new ConcurrentHashMap<>();
    private final AtomicInteger totalStickers = new AtomicInteger(0);
    private final AtomicInteger completedStickers = new AtomicInteger(0);
    private String currentPackTitle;

    private static class DownloadTask {
        final File packDir;
        final String packTitle;
        final String remoteId;

        DownloadTask(File packDir, String packTitle, String remoteId) {
            this.packDir = packDir;
            this.packTitle = packTitle;
            this.remoteId = remoteId;
        }
    }

    public StickerDownloader(DownloadListener listener) {
        this.listener = listener;
    }

    public void downloadPack(String packName) {
        TdlibManager.getInstance().send(new TdApi.SearchStickerSet(packName), result -> {
            if (result instanceof TdApi.StickerSet) {
                TdApi.StickerSet stickerSet = (TdApi.StickerSet) result;
                currentPackTitle = stickerSet.title;
                downloadStickers(stickerSet);
            } else if (result instanceof TdApi.Error) {
                listener.onError(((TdApi.Error) result).message);
            }
        });
    }

    private void downloadStickers(TdApi.StickerSet stickerSet) {
        TdApi.Sticker[] stickers = stickerSet.stickers;
        if (stickers.length == 0) {
            listener.onCompleted(stickerSet.title);
            return;
        }

        totalStickers.set(stickers.length);
        completedStickers.set(0);

        File packDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "monocles mod/Stickers/" + stickerSet.title);
        if (!packDir.exists()) {
            packDir.mkdirs();
        }

        for (TdApi.Sticker sticker : stickers) {
            int fileId = sticker.sticker.id;
            pendingDownloads.put(fileId, new DownloadTask(packDir, stickerSet.title, sticker.sticker.remote.id));

            TdlibManager.getInstance().send(new TdApi.DownloadFile(fileId, 1, 0, 0, false), result -> {
                if (result instanceof TdApi.Error) {
                    Log.e(TAG, "Error initiating download: " + ((TdApi.Error) result).message);
                    handleStickerFinished(fileId);
                }
                // UpdateFile will handle completion
            });
        }
    }

    public void handleUpdate(TdApi.Update update) {
        if (update instanceof TdApi.UpdateFile) {
            TdApi.File file = ((TdApi.UpdateFile) update).file;
            if (file.local.isDownloadingCompleted) {
                DownloadTask task = pendingDownloads.get(file.id);
                if (task != null) {
                    saveFile(file, task);
                    handleStickerFinished(file.id);
                }
            }
        }
    }

    private void saveFile(TdApi.File file, DownloadTask task) {
        if (file.local.path == null || file.local.path.isEmpty()) return;

        File src = new File(file.local.path);
        String extension = task.remoteId.endsWith(".tgs") ? ".tgs" : ".webp";
        File dest = new File(task.packDir, task.remoteId + extension);

        try (FileChannel sourceChannel = new FileInputStream(src).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save sticker file", e);
        }
    }

    private void handleStickerFinished(int fileId) {
        pendingDownloads.remove(fileId);
        int completed = completedStickers.incrementAndGet();
        int total = totalStickers.get();
        if (total > 0) {
            listener.onProgress(completed * 100 / total);
            if (completed >= total) {
                listener.onCompleted(currentPackTitle);
            }
        }
    }
}
