package eu.siacs.conversations.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.Transcoder;
import com.otaliastudios.transcoder.TranscoderListener;
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.TranscoderStrategies;

public class AttachFileToConversationRunnable implements Runnable, TranscoderListener {

    private final XmppConnectionService mXmppConnectionService;
    private final Message message;
    private final Conversation conversation;
    private final Uri uri;
    private final String mimeType;
    private final String type;
    private final UiCallback<Message> callback;
    private final long maxUploadSize;
    private final boolean isVideoMessage;
    private final long originalFileSize;
    private int currentProgress = -1;
    public static String[] isCompressingVideo = new String[]{null,"0"};

    public AttachFileToConversationRunnable(XmppConnectionService xmppConnectionService, Uri uri, String type, Message message, Conversation conversation, UiCallback<Message> callback, long maxUploadSize) {
        this.uri = uri;
        this.type = type;
        this.mXmppConnectionService = xmppConnectionService;
        this.message = message;
        this.conversation = conversation;
        this.callback = callback;
        this.maxUploadSize = maxUploadSize;
        mimeType = MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        this.originalFileSize = FileBackend.getFileSize(mXmppConnectionService, uri);
        this.isVideoMessage = !getFileBackend().useFileAsIs(uri)
                && (mimeType != null && mimeType.startsWith("video/"))
                && originalFileSize > Config.VIDEO_FAST_UPLOAD_SIZE
                && !"uncompressed".equals(getVideoCompression());
    }

    boolean isVideoMessage() {
        return this.isVideoMessage;
    }

    private void processAsFile() {
        final String path = mXmppConnectionService.getFileBackend().getOriginalPath(uri);
        if ("https".equals(uri.getScheme())) {
            message.getFileParams().url = uri.toString();
            message.getFileParams().setMediaType(mimeType);
            final int encryption = message.getEncryption();
            mXmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message, false, (file) -> {
                message.setEncryption(encryption);
                mXmppConnectionService.sendMessage(message);
                callback.success(message);
            });
        } else if (path != null && !FileBackend.isPathBlacklisted(path)) {
            message.setRelativeFilePath(path);
            mXmppConnectionService.getFileBackend().updateFileParams(message);
            if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                mXmppConnectionService.getPgpEngine().encrypt(message, callback);
            } else {
                mXmppConnectionService.sendMessage(message);
                callback.success(message);
            }
        } else {
            try {
                mXmppConnectionService.getFileBackend().copyFileToPrivateStorage(message, uri, type);
                mXmppConnectionService.getFileBackend().updateFileParams(message);
                if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                    final PgpEngine pgpEngine = mXmppConnectionService.getPgpEngine();
                    if (pgpEngine != null) {
                        pgpEngine.encrypt(message, callback);
                    } else if (callback != null) {
                        callback.error(R.string.unable_to_connect_to_keychain, null);
                    }
                } else {
                    mXmppConnectionService.sendMessage(message);
                    callback.success(message);
                }
            } catch (FileBackend.FileCopyException e) {
                callback.error(e.getResId(), message);
            }
        }
    }

    private void processAsVideo() throws Exception {
        Log.d(Config.LOGTAG, "processing file as video");
        mXmppConnectionService.startForcingForegroundNotification();
        isCompressingVideo = new String[]{conversation.getUuid(),"0"};
        final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        final String filename = "Sent/" + fileDateFormat.format(new Date(message.getTimeSent())) + "_" + message.getUuid().substring(0, 4) + "_komp";
        mXmppConnectionService.getFileBackend().setupRelativeFilePath(message, String.format("%s.%s", filename, "mp4"));
        final DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
        if (Objects.requireNonNull(file.getParentFile()).mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory for video file");
        }
        final ParcelFileDescriptor parcelFileDescriptor = mXmppConnectionService.getContentResolver().openFileDescriptor(uri, "r");
        if (parcelFileDescriptor == null) {
            throw new FileNotFoundException("Parcel File Descriptor was null");
        }
        final FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        final Future<Void> future = getVideoCompression(fileDescriptor, file, maxUploadSize, originalFileSize);
        try {
            future.get();
        } catch (InterruptedException e) {
            mXmppConnectionService.stopForcingForegroundNotification();
            isCompressingVideo = new String[]{null,"0"};;
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error) {
                mXmppConnectionService.stopForcingForegroundNotification();
                isCompressingVideo = new String[]{null,"0"};;
                processAsFile();
            } else {
                Log.d(Config.LOGTAG, "ignoring execution exception. Should get handled by onTranscodeFailed() instead", e);
            }
        }
    }

    private Future<Void> getVideoCompression(final FileDescriptor fileDescriptor, final File file, final long maxUploadSize, final long originalFileSize) throws Exception {
        final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(fileDescriptor);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
        long videoDuration;
        final long estimatedFileSize = maxUploadSize / 2;  // keep estimated filesize half as big as maxUploadSize
        try {
            videoDuration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000; //in seconds
        } catch (NumberFormatException e) {
            videoDuration = -1;
        }
        final int bitrateAfterCompression = safeLongToInt(mXmppConnectionService.getCompressVideoBitratePreference() / 8); //in bytes
        final long sizeAfterCompression = videoDuration * bitrateAfterCompression;

        if (sizeAfterCompression >= originalFileSize && originalFileSize <= Config.VIDEO_FAST_UPLOAD_SIZE) {
            processAsFile();
            onTranscodeCanceled();
            return null;
        } else if (estimatedFileSize >= sizeAfterCompression && sizeAfterCompression <= originalFileSize) {
            return Transcoder.into(file.getAbsolutePath())
                    .addDataSource(mXmppConnectionService, uri)
                    .setVideoTrackStrategy(TranscoderStrategies.VIDEO(mXmppConnectionService.getCompressVideoBitratePreference(), mXmppConnectionService.getCompressVideoResolutionPreference()))
                    .setAudioTrackStrategy(mXmppConnectionService.getCompressAudioPreference())
                    .setListener(this)
                    .transcode();
        } else {
            DefaultAudioStrategy audioStrategy;
            int newBitrate = safeLongToInt((estimatedFileSize / videoDuration) * 8); // in bits/sec
            int newResoloution = 0;
            if (newBitrate <= mXmppConnectionService.getResources().getInteger(R.integer.verylow_video_bitrate)) {
                newResoloution = mXmppConnectionService.getResources().getInteger(R.integer.verylow_video_res);
                audioStrategy = TranscoderStrategies.AUDIO_LQ;
            } else if (newBitrate > mXmppConnectionService.getResources().getInteger(R.integer.verylow_video_bitrate) && newBitrate <= mXmppConnectionService.getResources().getInteger(R.integer.low_video_bitrate)) {
                newResoloution = mXmppConnectionService.getResources().getInteger(R.integer.low_video_res);
                audioStrategy = TranscoderStrategies.AUDIO_LQ;
            } else if (newBitrate > mXmppConnectionService.getResources().getInteger(R.integer.low_video_bitrate) && newBitrate <= mXmppConnectionService.getResources().getInteger(R.integer.mid_video_bitrate)) {
                newResoloution = mXmppConnectionService.getResources().getInteger(R.integer.mid_video_res);
                audioStrategy = TranscoderStrategies.AUDIO_MQ;
            } else if (newBitrate > mXmppConnectionService.getResources().getInteger(R.integer.mid_video_bitrate) && newBitrate <= mXmppConnectionService.getResources().getInteger(R.integer.high_video_bitrate)) {
                newResoloution = mXmppConnectionService.getResources().getInteger(R.integer.high_video_res);
                audioStrategy = TranscoderStrategies.AUDIO_HQ;
            } else {
                newResoloution = mXmppConnectionService.getResources().getInteger(R.integer.high_video_res);
                audioStrategy = TranscoderStrategies.AUDIO_HQ;
            }
            return Transcoder.into(file.getAbsolutePath())
                    .addDataSource(mXmppConnectionService, uri)
                    .setVideoTrackStrategy(TranscoderStrategies.VIDEO(newBitrate, newResoloution))
                    .setAudioTrackStrategy(audioStrategy)
                    .setListener(this)
                    .transcode();
        }
    }

    private static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    @Override
    public void onTranscodeProgress(double progress) {
        final int p = (int) Math.round(progress * 100);
        if (p > currentProgress) {
            currentProgress = p;
            isCompressingVideo = new String[]{conversation.getUuid(), String.valueOf(currentProgress)};
            callback.progress(currentProgress);
            mXmppConnectionService.getHttpConnectionManager().updateConversationUi(false);
        }
    }

    @Override
    public void onTranscodeCompleted(int successCode) {
        mXmppConnectionService.stopForcingForegroundNotification();
        isCompressingVideo = new String[]{null,"100"};
        final File file = mXmppConnectionService.getFileBackend().getFile(message);
        long convertedFileSize = mXmppConnectionService.getFileBackend().getFile(message).getSize();
        Log.d(Config.LOGTAG, "originalFileSize = " + originalFileSize + " convertedFileSize = " + convertedFileSize);
        if (originalFileSize != 0 && convertedFileSize >= originalFileSize) {
            if (file.delete()) {
                Log.d(Config.LOGTAG, "original file size was smaller. Deleting and processing as file");
                processAsFile();
                return;
            } else {
                Log.d(Config.LOGTAG, "unable to delete converted file");
            }
        }
        mXmppConnectionService.getFileBackend().updateFileParams(message);
        if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            mXmppConnectionService.getPgpEngine().encrypt(message, callback);
        } else {
            mXmppConnectionService.sendMessage(message);
            callback.success(message);
        }
    }

    @Override
    public void onTranscodeCanceled() {
        mXmppConnectionService.stopForcingForegroundNotification();
        isCompressingVideo = new String[]{null,"0"};
        processAsFile();
    }

    @Override
    public void onTranscodeFailed(@NonNull @NotNull Throwable exception) {
        mXmppConnectionService.stopForcingForegroundNotification();
        isCompressingVideo = new String[]{null,"0"};
        Log.d(Config.LOGTAG, "video transcoding failed", exception);
        processAsFile();
    }

    @Override
    public void run() {
        if (this.isVideoMessage()) {
            try {
                processAsVideo();
            } catch (Exception e) {
                processAsFile();
                e.printStackTrace();
            }
        } else {
            processAsFile();
        }
    }

    private String getVideoCompression() {
        return getVideoCompression(mXmppConnectionService);
    }

    public static String getVideoCompression(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString("video_compression", context.getResources().getString(R.string.video_compression));
    }

    public FileBackend getFileBackend() {
        return mXmppConnectionService.fileBackend;
    }
}