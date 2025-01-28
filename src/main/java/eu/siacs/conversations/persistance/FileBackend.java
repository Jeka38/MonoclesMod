package eu.siacs.conversations.persistance;

import static eu.siacs.conversations.utils.StorageHelper.getConversationsDirectory;
import static eu.siacs.conversations.utils.StorageHelper.getGlobalAudiosPath;
import static eu.siacs.conversations.utils.StorageHelper.getGlobalDocumentsPath;
import static eu.siacs.conversations.utils.StorageHelper.getGlobalPicturesPath;
import static eu.siacs.conversations.utils.StorageHelper.getGlobalVideosPath;

import android.content.res.AssetFileDescriptor;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.StructStat;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;


import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.documentfile.provider.DocumentFile;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

import com.wolt.blurhashkt.BlurHashDecoder;

import org.tomlj.Toml;

import de.monocles.chat.BobTransfer;
import de.monocles.chat.ThumbHash;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AttachFileToConversationRunnable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.FileWriterException;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.xmpp.pep.Avatar;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import io.ipfs.cid.Cid;
import me.drakeet.support.toast.ToastCompat;
import eu.siacs.conversations.xml.Element;

public class FileBackend {

    private static final Object THUMBNAIL_LOCK = new Object();

    private static final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);

    private static final String FILE_PROVIDER = ".files";
    public static final String APP_DIRECTORY = "monocles chat";
    public static final String FILES = "Files";
    public static final String SENT_FILES = "Files" + File.separator + "Sent";
    public static final String AUDIOS = "Audios";
    public static final String SENT_AUDIOS = "Audios" + File.separator + "Sent";
    public static final String IMAGES = "Images";
    public static final String SENT_IMAGES = "Images" + File.separator + "Sent";
    public static final String VIDEOS = "Videos";
    public static final String SENT_VIDEOS = "Videos" + File.separator + "Sent";

    public static final AtomicInteger STORAGE_INDEX = new AtomicInteger(0);
    private static final float IGNORE_PADDING = 0.15f;

    private final XmppConnectionService mXmppConnectionService;

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static void switchStorage(boolean checked) {
        STORAGE_INDEX.set(checked ? 1 : 0);
    }

    private static void createNoMedia(Context context) {
        final File nomedia_files = new File(getConversationsDirectory(context, FILES) + File.separator + ".nomedia");
        final File nomedia_audios = new File(getConversationsDirectory(context, AUDIOS) + File.separator + ".nomedia");
        final File nomedia_videos_sent = new File(getConversationsDirectory(context, SENT_VIDEOS) + File.separator + ".nomedia");
        final File nomedia_files_sent = new File(getConversationsDirectory(context, SENT_FILES) + File.separator + ".nomedia");
        final File nomedia_audios_sent = new File(getConversationsDirectory(context, SENT_AUDIOS) + File.separator + ".nomedia");
        final File nomedia_images_sent = new File(getConversationsDirectory(context, SENT_IMAGES) + File.separator + ".nomedia");
        if (!nomedia_files.exists()) {
            try {
                nomedia_files.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for files directory");
            }
        }
        if (!nomedia_audios.exists()) {
            try {
                nomedia_audios.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for audio directory");
            }
        }
        if (!nomedia_videos_sent.exists()) {
            try {
                nomedia_videos_sent.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for videos sent directory");
            }
        }
        if (!nomedia_files_sent.exists()) {
            try {
                nomedia_files_sent.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for files sent directory");
            }
        }
        if (!nomedia_audios_sent.exists()) {
            try {
                nomedia_audios_sent.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for audios sent directory");
            }
        }
        if (!nomedia_images_sent.exists()) {
            try {
                nomedia_images_sent.createNewFile();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file for images sent directory");
            }
        }
    }

    public static Uri getMediaUri(Context context, File file) {
        final String filePath = file.getAbsolutePath();
        try (final Cursor
            cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID},
                    MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int id =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            } else {
                return null;
            }
        } catch (final Exception e) {
            return null;
        }
    }

    public static void updateFileParams(Message message, String url, long size) {
        Message.FileParams fileParams = new Message.FileParams();
        fileParams.url = url;
        fileParams.size = size;
        message.setFileParams(fileParams);
    }

    private void createNoMedia(File diretory) {
        final File noMedia = new File(diretory, ".nomedia");
        if (!noMedia.exists()) {
            try {
                if (!noMedia.createNewFile()) {
                    Log.d(Config.LOGTAG, "created nomedia file " + noMedia.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file");
            }
        }
    }

    public static void updateMediaScanner(XmppConnectionService mXmppConnectionService, File file) {
        updateMediaScanner(mXmppConnectionService, file, null);
    }

    public static void updateMediaScanner(XmppConnectionService mXmppConnectionService, File file, final Runnable callback) {
        if ((file.getAbsolutePath().startsWith(getConversationsDirectory(mXmppConnectionService, IMAGES).getAbsolutePath())
                || file.getAbsolutePath().startsWith(getConversationsDirectory(mXmppConnectionService, VIDEOS).getAbsolutePath()))
                && !file.getAbsolutePath().toLowerCase(Locale.US).contains("sent")) {
            MediaScannerConnection.scanFile(mXmppConnectionService, new String[]{file.getAbsolutePath()}, null, new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {

                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                    if (callback != null && file.getAbsolutePath().equals(path)) {
                        callback.run();
                    } else {
                        Log.d(Config.LOGTAG, "media scanner scanned wrong file");
                        if (callback != null) {
                            callback.run();
                        }
                    }
                }
            });
            Log.d(Config.LOGTAG, "media scanner broadcasts file scan: " + file.getAbsolutePath());
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(Uri.fromFile(new File(file.getAbsolutePath())));
            mXmppConnectionService.sendBroadcast(intent);
        } else {
            createNoMedia(mXmppConnectionService);
        }
        if (callback != null) {
            callback.run();
        }
    }

    public boolean deleteFile(File file) {
        return file.delete();
    }

    public boolean deleteFile(Message message) {
        File file = getFile(message);
        message.setDeleted(true);
        return deleteFile(file);
    }

    public void expireOldFiles(File dir, long timestamp) {
        try {
            long start = SystemClock.elapsedRealtime();
            int num = 0;
            if (dir == null) {
                return;
            }
            Stack<File> dirlist = new Stack<File>();
            dirlist.clear();
            dirlist.push(dir);
            while (!dirlist.isEmpty()) {
                File dirCurrent = dirlist.pop();
                File[] fileList = dirCurrent.listFiles();
                if (fileList != null) {
                    for (File file : fileList) {
                        if (file.isDirectory()) {
                            dirlist.push(file);
                        } else {
                            if (file.exists() && !file.getName().equalsIgnoreCase(".nomedia")) {
                                long lastModified = file.lastModified();
                                if (lastModified < timestamp) {
                                    num++;
                                    deleteFile(file);
                                }
                            }
                        }
                    }
                }
            }
            Log.d(Config.LOGTAG, "deleted " + num + " expired files in " + (SystemClock.elapsedRealtime() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteOldBackups(File dir, List<Account> mAccounts) {
        try {
            long start = SystemClock.elapsedRealtime();
            int num = 0;
            if (dir == null) {
                return;
            }
            Stack<File> dirlist = new Stack<File>();
            dirlist.clear();
            dirlist.push(dir);
            File dirCurrent = dirlist.pop();
            File[] fileList = dirCurrent.listFiles();
            while (!dirlist.isEmpty()) {
                if (fileList != null) {
                    for (File file : fileList) {
                        if (file.isDirectory()) {
                            dirlist.push(file);
                        }
                    }
                }
            }
            if (fileList != null) {
                ArrayList<File> fileListByAccount = new ArrayList<File>();
                ArrayList<File> simpleFileList = new ArrayList<File>(Arrays.asList(fileList));
                for (Account account : mAccounts) {
                    String jid = account.getJid().asBareJid().toString();
                    for (int i = 0; i < simpleFileList.size(); i++) {
                        File currentFile = simpleFileList.get(i);
                        String fileName = currentFile.getName();
                        if (fileName.startsWith(jid) && fileName.endsWith(".ceb")) {
                            fileListByAccount.add(currentFile);
                            simpleFileList.remove(currentFile);
                            i--;
                        }
                    }
                    if (fileListByAccount.size() > 2) {
                        num += expireOldBackups(fileListByAccount);
                    }
                    fileListByAccount.clear();
                }
            } else {
                return;
            }
            Log.d(Config.LOGTAG, "deleted " + num + " old backup files in " + (SystemClock.elapsedRealtime() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int expireOldBackups(ArrayList<File> fileListByAccount) {
        int num = 0;
        try {
            Collections.sort(fileListByAccount, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
            fileListByAccount.subList(0, 2).clear();
            for (File currentFile : fileListByAccount) {
                if (currentFile.delete()) {
                    num++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return num;
    }

    public void deleteFilesInDir(File dir) {
        long start = SystemClock.elapsedRealtime();
        int num = 0;
        if (dir == null) {
            return;
        }
        Stack<File> dirlist = new Stack<>();
        dirlist.push(dir);
        while (!dirlist.isEmpty()) {
            File dirCurrent = dirlist.pop();
            File[] fileList = dirCurrent.listFiles();
            if (fileList != null && fileList.length > 0) {
                for (File file : fileList) {
                    if (file.isDirectory()) {
                        dirlist.push(file);
                    } else {
                        if (file.exists() && !file.getName().equalsIgnoreCase(".nomedia")) {
                            num++;
                            deleteFile(file);
                        }
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "deleted " + num + " files in " + dir + " in " + (SystemClock.elapsedRealtime() - start) + "ms");
    }

    public DownloadableFile getFile(Message message) {
        return getFile(message, true);
    }

    public DownloadableFile getFileForPath(String path) {
        return getFileForPath(path, MimeUtils.guessMimeTypeFromExtension(MimeUtils.extractRelevantExtension(path)));
    }

    private DownloadableFile getFileForPath(String path, String mime) {
        DownloadableFile file = null;
        if (path.startsWith(File.separator)) {
            file = new DownloadableFile(path);
        } else {
            if (mime != null && mime.startsWith("image")) {
                file = new DownloadableFile(getConversationsDirectory(mXmppConnectionService, IMAGES) + File.separator + path);
            } else if (mime != null && mime.startsWith("video")) {
                file = new DownloadableFile(getConversationsDirectory(mXmppConnectionService, VIDEOS) + File.separator + path);
            } else if (mime != null && mime.startsWith("audio")) {
                file = new DownloadableFile(getConversationsDirectory(mXmppConnectionService, AUDIOS) + File.separator + path);
            } else {
                file = new DownloadableFile(getConversationsDirectory(mXmppConnectionService, FILES) + File.separator + path);
            }
        }
        return file;
    }

    public boolean isInternalFile(final File file) {
        final File internalFile = getFileForPath(file.getName());
        return file.getAbsolutePath().equals(internalFile.getAbsolutePath());
    }

    public DownloadableFile getFile(Message message, boolean decrypted) {
        final boolean encrypted =
                !decrypted
                        && (message.getEncryption() == Message.ENCRYPTION_PGP
                        || message.getEncryption() == Message.ENCRYPTION_DECRYPTED);
        String path = message.getRelativeFilePath();
        if (path == null) {
            path = fileDateFormat.format(new Date(message.getTimeSent())) + "_" + message.getUuid().substring(0, 4);
        }
        final DownloadableFile file = getFileForPath(path, message.getMimeType());
        if (encrypted) {
            return new DownloadableFile(
                    mXmppConnectionService.getCacheDir(),
                    String.format("%s.%s", file.getName(), "pgp"));
        } else {
            return file;
        }
    }

    public static long getFileSize(Context context, Uri uri) {
        try {
            final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                @SuppressLint("Range") long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                cursor.close();
                return size;
            } else {
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public static long getDirectorySize(final File file) {
        try {
            if (file == null || !file.exists() || !file.isDirectory())
                return 0;
            final List<File> dirs = new LinkedList<>();
            dirs.add(file);
            long result = 0;
            while (!dirs.isEmpty()) {
                final File dir = dirs.remove(0);
                if (!dir.exists()) {
                    continue;
                }
                final File[] listFiles = dir.listFiles();
                if (listFiles == null || listFiles.length == 0) {
                    continue;
                }
                for (final File child : listFiles) {
                    result += child.length();
                    if (child.isDirectory()) {
                        dirs.add(child);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static long getDiskSize() {
        try {
            StatFs external = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            return (long) external.getBlockCount() * (long) external.getBlockSize();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean allFilesUnderSize(Context context, List<Attachment> attachments, long max) {
        final boolean compressVideo = !AttachFileToConversationRunnable.getVideoCompression(context).equals("uncompressed");
        if (max <= 0) {
            Log.d(Config.LOGTAG, "server did not report max file size for http upload");
            return true; //exception to be compatible with HTTP Upload < v0.2
        }
        for (Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.FILE) {
                continue;
            }
            String mime = attachment.getMime();
            if (mime != null && mime.startsWith("video/") && compressVideo) {
                try {
                    Dimensions dimensions = FileBackend.getVideoDimensions(context, attachment.getUri());
                    if (dimensions.getMin() >= 720) {
                        Log.d(Config.LOGTAG, "do not consider video file with min width larger or equal than 720 for size check");
                        continue;
                    }
                } catch (NotAVideoFile notAVideoFile) {
                    //ignore and fall through
                }
            }
            if (FileBackend.getFileSize(context, attachment.getUri()) > max) {
                Log.d(Config.LOGTAG, "not all files are under " + max + " bytes. suggesting falling back to jingle");
                return false;
            }
        }
        return true;
    }

    public List<Attachment> convertToAttachments(final List<DatabaseBackend.FilePath> relativeFilePaths) {
        final List<Attachment> attachments = new ArrayList<>();
        for (final DatabaseBackend.FilePath relativeFilePath : relativeFilePaths) {
            final String mime = MimeUtils.guessMimeTypeFromExtension(MimeUtils.extractRelevantExtension(relativeFilePath.path));
            final File file = getFileForPath(relativeFilePath.path, mime);
            if (file.exists()) {
                attachments.add(Attachment.of(relativeFilePath.uuid, file, mime));
            }
        }
        return attachments;
    }

    public static String getFileType(File file) {
        String extension = MimeUtils.extractRelevantExtension(file.getAbsolutePath(), true);
        String mime = MimeUtils.guessMimeTypeFromExtension(extension);
        if (mime.toLowerCase(Locale.US).contains("image")) {
            return IMAGES;
        } else if (mime.toLowerCase(Locale.US).contains("video")) {
            return VIDEOS;
        } else if (mime.toLowerCase(Locale.US).contains("audio")) {
            return AUDIOS;
        } else {
            return FILES;
        }
    }

    private Bitmap resize(final Bitmap originalBitmap, int size) throws IOException {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Decoded bitmap reported bounds smaller 0");
        } else if (Math.max(w, h) > size) {
            int scalledW;
            int scalledH;
            if (w <= h) {
                scalledW = Math.max((int) (w / ((double) h / size)), 1);
                scalledH = size;
            } else {
                scalledW = size;
                scalledH = Math.max((int) (h / ((double) w / size)), 1);
            }
            final Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    private static Bitmap rotate(final Bitmap bitmap, final int degree) {
        if (degree == 0) {
            return bitmap;
        }
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    public long getUriSize(final Uri uri) {
        Cursor cursor = null;
        try {
            cursor = mXmppConnectionService.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                return cursor.getLong(sizeIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0; // Return 0 if the size information is not available
    }

    public boolean useImageAsIs(final Uri uri) {
        try {
            for (Cid cid : calculateCids(uri)) {
                if (mXmppConnectionService.getUrlForCid(cid) != null) return true;
            }

            long fsize = getUriSize(uri);
            if (fsize == 0 || fsize >= mXmppConnectionService.getResources().getInteger(R.integer.auto_accept_filesize_mobile)) {
                return false;
            }

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source source = ImageDecoder.createSource(mXmppConnectionService.getContentResolver(), uri);
                int[] size = new int[] { 0, 0 };
                boolean[] animated = new boolean[] { false };
                String[] mimeType = new String[] { null };
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    mimeType[0] = info.getMimeType();
                    animated[0] = info.isAnimated();
                    size[0] = info.getSize().getWidth();
                    size[1] = info.getSize().getHeight();
                });

                return animated[0] || (size[0] <= Config.IMAGE_SIZE && size[1] <= Config.IMAGE_SIZE);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            final InputStream inputStream =
                    mXmppConnectionService.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            close(inputStream);
            if (options.outMimeType == null || options.outHeight <= 0 || options.outWidth <= 0) {
                return false;
            }
            return (options.outWidth <= Config.IMAGE_SIZE && options.outHeight <= Config.IMAGE_SIZE);
        } catch (final IOException e) {
            Log.d(Config.LOGTAG, "unable to get image dimensions", e);
            return false;
        }
    }

    public boolean useFileAsIs(Uri uri) {
        String path = getOriginalPath(uri);
        if (path == null) {
            Log.d(Config.LOGTAG, "File path = null");
            return false;
        }
        if (path.contains(getConversationsDirectory(mXmppConnectionService, "null").getAbsolutePath())) {
            Log.d(Config.LOGTAG, "File " + path + " is in our directory");
            return true;
        }
        Log.d(Config.LOGTAG, "File " + path + " is not in our directory");
        return false;
    }

    public static boolean isPathBlacklisted(String path) {
        Environment.getDataDirectory();
        final String androidDataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Android" + File.separator + "data" + File.separator + "";
        final File f = new File(path);
        return path.startsWith(androidDataPath) || !f.canRead();
    }

    public String getOriginalPath(final Uri uri) {
        return FileUtils.getPath(mXmppConnectionService, uri);
    }


    public void copyFileToDocumentFile(Context ctx, File file, DocumentFile df) throws FileCopyException {
        Log.d(
                Config.LOGTAG,
                "copy file (" + file + ") to " + df);
        try (final InputStream is = new FileInputStream(file);
             final OutputStream os =
                     mXmppConnectionService.getContentResolver().openOutputStream(df.getUri())) {
            if (is == null) {
                throw new FileCopyException(R.string.error_file_not_found);
            }
            try {
                ByteStreams.copy(is, os);
                os.flush();
            } catch (IOException e) {
                throw new FileWriterException(file);
            }
        } catch (final FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final FileWriterException e) {
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        } catch (final SecurityException | IllegalStateException e) {
            throw new FileCopyException(R.string.error_security_exception);
        } catch (final IOException e) {
            throw new FileCopyException(R.string.error_io_exception);
        }
    }

    private InputStream openInputStream(Uri uri) throws IOException {
        if (uri != null && "data".equals(uri.getScheme())) {
            String[] parts = uri.getSchemeSpecificPart().split(",", 2);
            byte[] data;
            if (Arrays.asList(parts[0].split(";")).contains("base64")) {
                String[] parts2 = parts[0].split(";", 2);
                parts[0] = parts2[0];
                data = Base64.decode(parts[1], 0);
            } else {
                try {
                    data = parts[1].getBytes("UTF-8");
                } catch (final IOException e) {
                    data = new byte[0];
                }
            }
            return new ByteArrayInputStream(data);
        }
        final InputStream is = mXmppConnectionService.getContentResolver().openInputStream(uri);
        if (is == null) throw new FileNotFoundException("File not found");
        return is;
    }

    private void copyFileToPrivateStorage(File file, Uri uri) throws FileCopyException {
        Log.d(Config.LOGTAG, "copy file (" + uri.toString() + ") to private storage " + file.getAbsolutePath());
        file.getParentFile().mkdirs();
        try {
            if (!file.createNewFile() && file.length() > 0) {
                if (file.canRead() && file.getName().startsWith("zb2")) return; // We have this content already
                throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
            }
        } catch (IOException e) {
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        }
        try (final OutputStream os = new FileOutputStream(file);
             final InputStream is = openInputStream(uri)) {
            if (is == null) {
                throw new FileCopyException(R.string.error_file_not_found);
            }
            try {
                ByteStreams.copy(is, os);
            } catch (Exception e) {
                throw new FileWriterException(file);
            }
            try {
                os.flush();
            } catch (IOException e) {
                throw new FileWriterException(file);
            }
        } catch (final FileNotFoundException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final FileWriterException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        } catch (final SecurityException | IllegalStateException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_security_exception);
        } catch (final IOException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_io_exception);
        }
    }

    public void copyFileToPrivateStorage(Message message, Uri uri, String type) throws FileCopyException {
        String mime = MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage (mime=" + mime + ")");
        String extension = MimeUtils.guessExtensionFromMimeType(mime);
        if (extension == null) {
            Log.d(Config.LOGTAG, "extension from mime type was null");
            extension = getExtensionFromUri(uri);
        }
        if ("ogg".equals(extension) && type != null && type.startsWith("audio/")) {
            extension = "oga";
        }
        try {
            setupRelativeFilePath(message, uri, extension);
            copyFileToPrivateStorage(mXmppConnectionService.getFileBackend().getFile(message), uri);
            final String name = getDisplayNameFromUri(uri);
            if (name != null) {
                message.getFileParams().setName(name);
            }
        } catch (final XmppConnectionService.BlockedMediaException e) {
            message.setRelativeFilePath(null);
            message.setDeleted(true);
        }
    }

    private String getDisplayNameFromUri(final Uri uri) {
        final String[] projection = {OpenableColumns.DISPLAY_NAME};
        String filename = null;
        try (final Cursor cursor =
                     mXmppConnectionService
                             .getContentResolver()
                             .query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                filename = cursor.getString(0);
            }
        } catch (final Exception e) {
            filename = null;
        }
        return filename;
    }

    public static void moveDirectory(XmppConnectionService mXmppConnectionService, File sourceLocation, File targetLocation) throws Exception {
        if (sourceLocation.isDirectory()) {
            Log.d(Config.LOGTAG, "Migration: copy from " + sourceLocation.getAbsolutePath() + " to " + targetLocation.getAbsolutePath());
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
                Log.d(Config.LOGTAG, "Migration: creating target dir " + targetLocation.getAbsolutePath());
            }
            String[] children = sourceLocation.list();
            for (String child : children) {
                try {
                    Log.d(Config.LOGTAG, "Migration: iterating in dir " + child);
                    moveDirectory(mXmppConnectionService, new File(sourceLocation, child), new File(targetLocation, child));
                } finally {
                    sourceLocation.delete();
                }
            }
        } else {
            try {
                Log.d(Config.LOGTAG, "Migration: copy " + sourceLocation.getName() + " to target dir " + targetLocation.getAbsolutePath());
                InputStream in = new FileInputStream(sourceLocation);
                OutputStream out = new FileOutputStream(targetLocation);
                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } finally {
                updateMediaScanner(mXmppConnectionService, targetLocation);
                sourceLocation.delete();
            }
        }
    }

    private String getExtensionFromUri(final Uri uri) {
        final String[] projection = {MediaStore.MediaColumns.DATA};
        String filename = null;
        try (final Cursor cursor = mXmppConnectionService.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                filename = cursor.getString(0);
            }
        } catch (final SecurityException | IllegalArgumentException e) {
            filename = null;
        }
        if (filename == null) {
            final List<String> segments = uri.getPathSegments();
            if (segments.size() > 0) {
                filename = segments.get(segments.size() - 1);
            }
        }
        final int pos = filename == null ? -1 : filename.lastIndexOf('.');
        return pos > 0 ? filename.substring(pos + 1) : null;
    }

    private void copyImageToPrivateStorage(File file, Uri image, int sampleSize) throws FileCopyException, ImageCompressionException {
        final File parent = file.getParentFile();
        if (parent != null && parent.mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory");
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
            }
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                throw new FileCopyException(R.string.error_not_an_image_file);
            }
            final Bitmap originalBitmap;
            final BitmapFactory.Options options = new BitmapFactory.Options();
            final int inSampleSize = (int) Math.pow(2, sampleSize);
            Log.d(Config.LOGTAG, "reading bitmap with sample size " + inSampleSize);
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new ImageCompressionException("Source file was not an image");
            }
            if (!"image/jpeg".equals(options.outMimeType) && hasAlpha(originalBitmap)) {
                originalBitmap.recycle();
                throw new ImageCompressionException("Source file had alpha channel");
            }
            int size;
            if (mXmppConnectionService.getCompressImageResolutionPreference() == 0) {
                int height = originalBitmap.getHeight();
                int width = originalBitmap.getWidth();
                size = height > width ? height : width;
            } else {
                size = mXmppConnectionService.getCompressImageResolutionPreference();
            }
            Bitmap scaledBitmap = resize(originalBitmap, size);
            final int rotation = getRotation(image);
            scaledBitmap = rotate(scaledBitmap, rotation);
            boolean targetSizeReached = false;
            int quality = Config.IMAGE_QUALITY;
            while (!targetSizeReached) {
                os = new FileOutputStream(file);
                boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, quality, os);
                if (!success) {
                    throw new FileCopyException(R.string.error_compressing_image);
                }
                os.flush();
                targetSizeReached = (file.length() <= mXmppConnectionService.getCompressImageSizePreference() && mXmppConnectionService.getCompressImageSizePreference() != 0) || quality <= 50;
                quality -= 5;
            }
            scaledBitmap.recycle();
        } catch (final FileNotFoundException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final IOException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_io_exception);
        } catch (SecurityException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_security_exception_during_image_copy);
        } catch (final OutOfMemoryError e) {
            ++sampleSize;
            if (sampleSize <= 3) {
                copyImageToPrivateStorage(file, image, sampleSize);
            } else {
                throw new FileCopyException(R.string.error_out_of_memory);
            }
        } finally {
            close(os);
            close(is);
        }
    }

    private static void cleanup(final File file) {
        try {
            file.delete();
        } catch (Exception e) {

        }
    }

    public void copyImageToPrivateStorage(File file, Uri image) throws FileCopyException, ImageCompressionException {
        Log.d(Config.LOGTAG, "copy image (" + image.toString() + ") to private storage " + file.getAbsolutePath());
        copyImageToPrivateStorage(file, image, 0);
    }

    public void copyImageToPrivateStorage(Message message, Uri image) throws FileCopyException, ImageCompressionException {
        String filename;
        String file = "Sent" + File.separator + fileDateFormat.format(new Date(message.getTimeSent())) + "_" + message.getUuid().substring(0, 4);
        switch (Config.IMAGE_FORMAT) {
            case JPEG:
                filename = String.format("%s.%s", file, ".jpg");
                break;
            case PNG:
                filename = String.format("%s.%s", file, ".png");
                break;
            case WEBP:
                filename = String.format("%s.%s", file, ".webp");
                break;
            default:
                throw new IllegalStateException("Unknown image format");
        }
        setupRelativeFilePath(message, filename);
        final File tmp = getFile(message);
        copyImageToPrivateStorage(tmp, image);
        final String extension = MimeUtils.extractRelevantExtension(filename);
        try {
            setupRelativeFilePath(message, new FileInputStream(tmp), extension);
        } catch (final FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final IOException e) {
            throw new FileCopyException(R.string.error_io_exception);
        } catch (final XmppConnectionService.BlockedMediaException e) {
            tmp.delete();
            message.setRelativeFilePath(null);
            message.setDeleted(true);
            return;
        }
        tmp.renameTo(getFile(message));
        updateFileParams(message, null, false);
    }

    public void setupRelativeFilePath(final Message message, final Uri uri, final String extension) throws FileCopyException, XmppConnectionService.BlockedMediaException {
        try {
            setupRelativeFilePath(message, openInputStream(uri), extension);
        } catch (final FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final IOException e) {
            throw new FileCopyException(R.string.error_io_exception);
        }
    }

    public void setupRelativeFilePath(final Message message, final InputStream is, final String extension) throws IOException, XmppConnectionService.BlockedMediaException {
        message.setRelativeFilePath(getStorageLocation(is, extension).getAbsolutePath());
    }

    public void setupRelativeFilePath(final Message message, final String filename) {
        final String extension = MimeUtils.extractRelevantExtension(filename);
        final String mime = MimeUtils.guessMimeTypeFromExtension(extension);
        setupRelativeFilePath(message, filename, mime);
    }

    public File getStorageLocation(final InputStream is, final String extension) throws IOException, XmppConnectionService.BlockedMediaException {
        final String mime = MimeUtils.guessMimeTypeFromExtension(extension);
        Cid[] cids = calculateCids(is);
        String base = cids[0].toString();

        File file = null;
        while (file == null || (file.exists() && !file.canRead())) {
            file = getStorageLocation(String.format("%s.%s", base, extension), mime);
            base += "_";
        }
        for (int i = 0; i < cids.length; i++) {
            try {
                mXmppConnectionService.saveCid(cids[i], file);
            } catch (XmppConnectionService.BlockedMediaException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }

    public File getStorageLocation(final String filename, final String mime) {
        final File parentDirectory;
        if (Strings.isNullOrEmpty(mime)) {
            parentDirectory = getConversationsDirectory(mXmppConnectionService, FILES);
        } else if (mime.startsWith("image/")) {
            parentDirectory = getConversationsDirectory(mXmppConnectionService, IMAGES);
        } else if (mime.startsWith("video/")) {
            parentDirectory = getConversationsDirectory(mXmppConnectionService, VIDEOS);
        } else if (mime.startsWith("audio/")) {
            parentDirectory = getConversationsDirectory(mXmppConnectionService, AUDIOS);
        } else {
            parentDirectory = getConversationsDirectory(mXmppConnectionService, FILES);
        }
        return new File(parentDirectory, filename);
    }

    public void setupRelativeFilePath(final Message message, final String filename, final String mime) {
        final File file = getStorageLocation(filename, mime);
        message.setRelativeFilePath(file.getAbsolutePath());
    }

    public void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        Log.d(Config.LOGTAG, "Copy " + sourceFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
            updateMediaScanner(mXmppConnectionService, destFile);
        }
    }

    public static boolean copyStream(InputStream sourceFile, OutputStream destFile) throws IOException {
        byte[] buf = new byte[4096];
        int len;
        while ((len = sourceFile.read(buf)) > 0) {
            Thread.yield();
            destFile.write(buf, 0, len);
        }
        destFile.close();
        Log.d(Config.LOGTAG, "Copy stream from " + sourceFile + " to " + destFile);
        return true;
    }

    public boolean unusualBounds(final Uri image) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            final InputStream inputStream = mXmppConnectionService.getContentResolver().openInputStream(image);
            BitmapFactory.decodeStream(inputStream, null, options);
            close(inputStream);
            float ratio = (float) options.outHeight / options.outWidth;
            return ratio > (21.0f / 9.0f) || ratio < (9.0f / 21.0f);
        } catch (final Exception e) {
            Log.w(Config.LOGTAG, "unable to detect image bounds", e);
            return false;
        }
    }

    private int getRotation(final File file) {
        try (final InputStream inputStream = new FileInputStream(file)) {
            return getRotation(inputStream);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getRotation(final Uri image) {
        try (final InputStream is = mXmppConnectionService.getContentResolver().openInputStream(image)) {
            return is == null ? 0 : getRotation(is);
        } catch (final Exception e) {
            return 0;
        }
    }

    public static int getRotation(final InputStream inputStream) throws IOException {
        final ExifInterface exif = new ExifInterface(inputStream);
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public Bitmap getThumbnailBitmap(Message message, Resources res, int size) throws IOException {
        final Drawable drawable = getThumbnail(message, res, size, false);
        if (drawable == null) return null;
        return drawDrawable(drawable);
    }

    public Bitmap getThumbnailBitmap(DownloadableFile file, Resources res, int size, String cacheKey) throws IOException {
        final Drawable drawable = getThumbnail(file, res, size, false, cacheKey);
        if (drawable == null) return null;
        return drawDrawable(drawable);
    }

    public static Bitmap drawDrawable(Drawable drawable) {
        if (drawable == null) return null;

        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) return bitmap;
        }

        Rect bounds = drawable.getBounds();
        int width = drawable.getIntrinsicWidth();
        if (width < 1) width = bounds == null || bounds.right < 1 ? 256 : bounds.right;
        int height = drawable.getIntrinsicHeight();
        if (height < 1) height = bounds == null || bounds.bottom < 1 ? 256 : bounds.bottom;

        if (width < 1) {
            Log.w(Config.LOGTAG, "Drawable with no width: " + drawable);
            width = 48;
        }
        if (height < 1) {
            Log.w(Config.LOGTAG, "Drawable with no height: " + drawable);
            height = 48;
        }

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private Bitmap getPDFPreview(final File file, int size) {
        try {
            final ParcelFileDescriptor mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (mFileDescriptor == null) {
                return null;
            }
            final PdfRenderer renderer = new PdfRenderer(mFileDescriptor);
            final PdfRenderer.Page page = renderer.openPage(0);
            final Dimensions dimensions = scalePdfDimensions(new Dimensions(page.getHeight(), page.getWidth()));
            final Bitmap bitmap = Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            drawOverlay(bitmap, R.drawable.show_pdf, 0.75f);
            page.close();
            renderer.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            final Bitmap placeholder = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            placeholder.eraseColor(Color.WHITE);
            drawOverlay(placeholder, R.drawable.show_pdf, 0.75f);
            return placeholder;
        }
    }

    private Dimensions scalePdfDimensions(Dimensions in) {
        final DisplayMetrics displayMetrics =
                mXmppConnectionService.getResources().getDisplayMetrics();
        final int target = (int) (displayMetrics.density * 288);
        return scalePdfDimensions(in, target, true);
    }

    private Bitmap getFullsizeImagePreview(File file, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calcSampleSize(file, size);
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
    }

    public void drawOverlay(final Bitmap bitmap, final int resource, final float factor) {
        drawOverlay(bitmap, resource, factor, false);
    }

    public void drawOverlay(final Bitmap bitmap, final int resource, final float factor, final boolean corner) {
        Bitmap overlay = BitmapFactory.decodeResource(mXmppConnectionService.getResources(), resource);
        Canvas canvas = new Canvas(bitmap);
        float targetSize = Math.min(canvas.getWidth(), canvas.getHeight()) * factor;
        Log.d(Config.LOGTAG, "target size overlay: " + targetSize + " overlay bitmap size was " + overlay.getHeight());
        float left;
        float top;
        if (corner) {
            left = canvas.getWidth() - targetSize;
            top = canvas.getHeight() - targetSize;
        } else {
            left = (canvas.getWidth() - targetSize) / 2.0f;
            top = (canvas.getHeight() - targetSize) / 2.0f;
        }
        RectF dst = new RectF(left, top, left + targetSize - 1, top + targetSize - 1);
        canvas.drawBitmap(overlay, null, dst, createAntiAliasingPaint());
    }

    public void drawOverlayFromDrawable(final Drawable drawable, final int resource, final float factor) {
        Bitmap overlay = BitmapFactory.decodeResource(mXmppConnectionService.getResources(), resource);
        Bitmap original = drawableToBitmap(drawable);
        Canvas canvas = new Canvas(original);
        float targetSize = Math.min(canvas.getWidth(), canvas.getHeight()) * factor;
        Log.d(Config.LOGTAG, "target size overlay: " + targetSize + " overlay bitmap size was " + overlay.getHeight());
        float left = (canvas.getWidth() - targetSize) / 2.0f;
        float top = (canvas.getHeight() - targetSize) / 2.0f;
        RectF dst = new RectF(left, top, left + targetSize - 1, top + targetSize - 1);
        canvas.drawBitmap(overlay, null, dst, createAntiAliasingPaint());
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = null;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static Paint createAntiAliasingPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        return paint;
    }

    private Bitmap cropCenterSquareVideo(Uri uri, int size) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(mXmppConnectionService, uri);
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            return cropCenterSquare(frame, size);
        } catch (Exception e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
            return frame;
        }
    }

    private Bitmap getVideoPreview(final File file, final int size) {
        final MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            frame = resize(frame, size);
        } catch (IOException e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
        } catch (RuntimeException e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
        }
        drawOverlay(frame, R.drawable.play_video, 0.75f);
        return frame;
    }

    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    public static String formatTime(int ms) {
        return String.format(Locale.ENGLISH, "%d:%02d", ms / 60000, Math.min(Math.round((ms % 60000) / 1000f), 59));
    }

    private static String getTakeFromCameraPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "Camera" + File.separator;
    }

    public Uri getTakePhotoUri() {
        final String filename = String.format("IMG_%s.%s", fileDateFormat.format(new Date()), "jpg");
        final File directory;
        if (STORAGE_INDEX.get() == 1) {
            directory = new File(mXmppConnectionService.getCacheDir(), "Camera");
        } else {
            directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        }
        final File file = new File(directory, filename);
        file.getParentFile().mkdirs();
        return getUriForFile(mXmppConnectionService, file);
    }

    public static Uri getUriForUri(Context context, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return getUriForFile(context, new File(uri.getPath()));
        } else {
            return uri;
        }
    }

    public static Uri getUriForFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SettingsActivity.USE_INNER_STORAGE, context.getResources().getBoolean(R.bool.use_inner_storage))) {
            try {
                return FileProvider.getUriForFile(context, getAuthority(context), file);
            } catch (IllegalArgumentException e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    throw new SecurityException(e);
                } else {
                    return Uri.fromFile(file);
                }
            }
        } else {
            return Uri.fromFile(file);
        }
    }

    public static String getAuthority(Context context) {
        return context.getPackageName() + FILE_PROVIDER;
    }

    public Uri getTakeVideoUri() {
        File file = new File(getTakeFromCameraPath() + "VID_" + fileDateFormat.format(new Date()) + ".mp4");
        file.getParentFile().mkdirs();
        return getUriForFile(mXmppConnectionService, file);
    }

    private static boolean hasAlpha(final Bitmap bitmap) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int yStep = Math.max(1, w / 100);
        final int xStep = Math.max(1, h / 100);
        for (int x = 0; x < w; x += xStep) {
            for (int y = 0; y < h; y += yStep) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {

        final Pair<Avatar,Boolean> uncompressAvatar = getUncompressedAvatar(image);
        if (uncompressAvatar != null && uncompressAvatar.first != null &&
                (uncompressAvatar.first.image.length() <= Config.AVATAR_CHAR_LIMIT || uncompressAvatar.second)) {
            return uncompressAvatar.first;
        }
        if (uncompressAvatar != null && uncompressAvatar.first != null) {
            Log.d(
                    Config.LOGTAG,
                    "uncompressed avatar exceeded char limit by "
                            + (uncompressAvatar.first.image.length() - Config.AVATAR_CHAR_LIMIT));
        }

        Bitmap bm = cropCenterSquare(image, size);
        if (bm == null) {
            return null;
        }
        if (hasAlpha(bm)) {
            Log.d(Config.LOGTAG, "alpha in avatar detected; uploading as PNG");
            bm.recycle();
            bm = cropCenterSquare(image, 96);
            return getPepAvatar(bm, Bitmap.CompressFormat.PNG, 100);
        }
        return getPepAvatar(bm, format, 100);
    }

    private Pair<Avatar,Boolean> getUncompressedAvatar(Uri uri) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source source = ImageDecoder.createSource(mXmppConnectionService.getContentResolver(), uri);
                int[] size = new int[] { 0, 0 };
                boolean[] animated = new boolean[] { false };
                String[] mimeType = new String[] { null };
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    mimeType[0] = info.getMimeType();
                    animated[0] = info.isAnimated();
                    size[0] = info.getSize().getWidth();
                    size[1] = info.getSize().getHeight();
                });

                if (animated[0]) {
                    Avatar avatar = getPepAvatar(uri, size[0], size[1], mimeType[0]);
                    if (avatar != null) return new Pair(avatar, true);
                }

                return new Pair(getPepAvatar(drawDrawable(drawable), Bitmap.CompressFormat.PNG, 100), false);
            } else {
                Bitmap bitmap =
                        BitmapFactory.decodeStream(
                                mXmppConnectionService.getContentResolver().openInputStream(uri));
                return new Pair(getPepAvatar(bitmap, Bitmap.CompressFormat.PNG, 100), false);
            }
        } catch (Exception e) {
            try {
                final SVG svg = SVG.getFromInputStream(mXmppConnectionService.getContentResolver().openInputStream(uri));
                return new Pair(getPepAvatar(uri, (int) svg.getDocumentWidth(), (int) svg.getDocumentHeight(), "image/svg+xml"), true);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private Avatar getPepAvatar(Uri uri, int width, int height, final String mimeType) throws IOException, NoSuchAlgorithmException {
        AssetFileDescriptor fd = mXmppConnectionService.getContentResolver().openAssetFileDescriptor(uri, "r");
        if (fd.getLength() > 100000) return null; // Too big to use raw file

        ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
        Base64OutputStream mBase64OutputStream =
                new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        DigestOutputStream mDigestOutputStream =
                new DigestOutputStream(mBase64OutputStream, digest);

        ByteStreams.copy(fd.createInputStream(), mDigestOutputStream);
        mDigestOutputStream.flush();
        mDigestOutputStream.close();

        final Avatar avatar = new Avatar();
        avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
        avatar.image = new String(mByteArrayOutputStream.toByteArray());
        avatar.type = mimeType;
        avatar.width = width;
        avatar.height = height;
        return avatar;
    }

    private Avatar getPepAvatar(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        try {
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputStream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(mBase64OutputStream, digest);
            if (!bitmap.compress(format, quality, mDigestOutputStream)) {
                return null;
            }
            mDigestOutputStream.flush();
            mDigestOutputStream.close();
            long chars = mByteArrayOutputStream.size();
            if (format != Bitmap.CompressFormat.PNG && quality >= 50 && chars >= Config.AVATAR_CHAR_LIMIT) {
                int q = quality - 2;
                Log.d(Config.LOGTAG, "avatar char length was " + chars + " reducing quality to " + q);
                return getPepAvatar(bitmap, format, q);
            }
            Log.d(Config.LOGTAG, "settled on char length " + chars + " with quality=" + quality);
            final Avatar avatar = new Avatar();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = mByteArrayOutputStream.toString();
            if (format.equals(Bitmap.CompressFormat.WEBP)) {
                avatar.type = "image/webp";
            } else if (format.equals(Bitmap.CompressFormat.JPEG)) {
                avatar.type = "image/jpeg";
            } else if (format.equals(Bitmap.CompressFormat.PNG)) {
                avatar.type = "image/png";
            }
            avatar.width = bitmap.getWidth();
            avatar.height = bitmap.getHeight();
            return avatar;
        } catch (OutOfMemoryError e) {
            Log.d(Config.LOGTAG, "unable to convert avatar to base64 due to low memory");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Avatar getStoredPepAvatar(String hash) {
        if (hash == null) {
            return null;
        }
        Avatar avatar = new Avatar();
        final File file = getAvatarFile(hash);
        FileInputStream is = null;
        try {
            avatar.size = file.length();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            is = new FileInputStream(file);
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputStream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream os = new DigestOutputStream(mBase64OutputStream, digest);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            os.close();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = mByteArrayOutputStream.toString();
            avatar.height = options.outHeight;
            avatar.width = options.outWidth;
            avatar.type = options.outMimeType;
            return avatar;
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public boolean isAvatarCached(Avatar avatar) {
        final File file = getAvatarFile(avatar.getFilename());
        return file.exists();
    }

    public boolean deleteAvatar(final String avatarFilename) {
        final File file = getAvatarFile(avatarFilename);
        return deleteAvatar(file);
    }

    public boolean deleteAvatar(final Avatar avatar) {
        final File file = getAvatarFile(avatar.getFilename());
        return deleteAvatar(file);
    }

    public boolean deleteAvatar(final File avatar) {
        if (avatar.exists()) {
            return avatar.delete();
        }
        return false;
    }

    public boolean save(final Avatar avatar) {
        File file;
        if (isAvatarCached(avatar)) {
            file = getAvatarFile(avatar.getFilename());
            avatar.size = file.length();
        } else {
            file = new File(mXmppConnectionService.getCacheDir().getAbsolutePath() + File.separator + UUID.randomUUID().toString());
            if (file.getParentFile().mkdirs()) {
                Log.d(Config.LOGTAG, "created cache directory");
            }
            OutputStream os = null;
            try {
                if (!file.createNewFile()) {
                    Log.d(Config.LOGTAG, "unable to create temporary file " + file.getAbsolutePath());
                }
                os = new FileOutputStream(file);
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.reset();
                DigestOutputStream mDigestOutputStream = new DigestOutputStream(os, digest);
                final byte[] bytes = avatar.getImageAsBytes();
                mDigestOutputStream.write(bytes);
                mDigestOutputStream.flush();
                mDigestOutputStream.close();
                String sha1sum = CryptoHelper.bytesToHex(digest.digest());
                if (sha1sum.equals(avatar.sha1sum)) {
                    final File outputFile = getAvatarFile(avatar.getFilename());
                    if (outputFile.getParentFile().mkdirs()) {
                        Log.d(Config.LOGTAG, "created avatar directory");
                    }
                    final File avatarFile = getAvatarFile(avatar.getFilename());
                    if (!file.renameTo(avatarFile)) {
                        Log.d(Config.LOGTAG, "unable to rename " + file.getAbsolutePath() + " to " + outputFile);
                        return false;
                    }
                } else {
                    Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
                    if (!file.delete()) {
                        Log.d(Config.LOGTAG, "unable to delete temporary file");
                    }
                    return false;
                }
                avatar.size = bytes.length;
            } catch (IllegalArgumentException e) {
                return false;
            } catch (IOException e) {
                return false;
            } catch (NoSuchAlgorithmException e) {
                return false;
            } finally {
                close(os);
            }
        }
        return true;
    }

    public void deleteHistoricAvatarPath() {
        delete(getHistoricAvatarPath());
    }

    private void delete(final File file) {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (final File f : files) {
                    delete(f);
                }
            }
        }
        if (file.delete()) {
            Log.d(Config.LOGTAG, "deleted " + file.getAbsolutePath());
        }
    }

    private File getHistoricAvatarPath() {
        return new File(mXmppConnectionService.getFilesDir(), File.separator + "avatars" + File.separator);
    }

    public File getAvatarFile(String avatar) {
        return new File(mXmppConnectionService.getCacheDir(), File.separator + "avatars" + File.separator + avatar);
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.fromFile(getAvatarFile(avatar));
    }

    public Drawable cropCenterSquareDrawable(Uri image, int size) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(mXmppConnectionService.getContentResolver(), image);
                return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    Rect r = rectForSize(w, h, size);
                    decoder.setTargetSize(r.width(), r.height());

                    int newSize = Math.min(r.width(), r.height());
                    int left = (r.width() - newSize) / 2;
                    int top = (r.height() - newSize) / 2;
                    decoder.setCrop(new Rect(left, top, left + newSize, top + newSize));
                });
            } catch (final IOException e) {
                return getSVGSquare(image, size);
            }
        } else {
            Bitmap bitmap = cropCenterSquare(image, size);
            return bitmap == null ? null : new BitmapDrawable(bitmap);
        }
    }

    public Bitmap cropCenterSquare(Uri image, int size) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                input = rotate(input, getRotation(image));
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException e) {
            Log.d(Config.LOGTAG, "unable to open file " + image, e);
            return null;
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "unable to open file " + image, e);
            return null;
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, Math.max(newHeight, newWidth));
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap source = BitmapFactory.decodeStream(is, null, options);
            if (source == null) {
                return null;
            }
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float xScale = (float) newWidth / sourceWidth;
            float yScale = (float) newHeight / sourceHeight;
            float scale = Math.max(xScale, yScale);
            float scaledWidth = scale * sourceWidth;
            float scaledHeight = scale * sourceHeight;
            float left = (newWidth - scaledWidth) / 2;
            float top = (newHeight - scaledHeight) / 2;

            RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, createAntiAliasingPaint());
            if (source.isRecycled()) {
                source.recycle();
            }
            return dest;
        } catch (SecurityException | FileNotFoundException e) {
            return null; //android 6.0 with revoked permissions for example
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenterSquare(Bitmap input, int size) {
        int w = input.getWidth();
        int h = input.getHeight();

        float scale = Math.max((float) size / h, (float) size / w);

        float outWidth = scale * w;
        float outHeight = scale * h;
        float left = (size - outWidth) / 2;
        float top = (size - outHeight) / 2;
        RectF target = new RectF(left, top, left + outWidth, top + outHeight);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, createAntiAliasingPaint());
        if (!input.isRecycled()) {
            input.recycle();
        }
        return output;
    }

    private int calcSampleSize(Uri image, int size) throws FileNotFoundException, SecurityException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        final InputStream inputStream = mXmppConnectionService.getContentResolver().openInputStream(image);
        BitmapFactory.decodeStream(inputStream, null, options);
        close(inputStream);
        return calcSampleSize(options, size);
    }

    private static int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }

    private static int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size
                    && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public void updateFileParams(Message message) {
        updateFileParams(message, null);
    }
    public void updateFileParams(final Message message, final String url) {
        updateFileParams(message, url, true);
    }

    public void updateFileParams(final Message message, String url, boolean updateCids) {
        final boolean encrypted =
                message.getEncryption() == Message.ENCRYPTION_PGP
                        || message.getEncryption() == Message.ENCRYPTION_DECRYPTED;
        final DownloadableFile file = getFile(message);
        final String mime = file.getMimeType();
        final boolean image =
                message.getType() == Message.TYPE_IMAGE
                        || (mime != null && mime.startsWith("image/"));
        final boolean isGif = image & (mime != null && mime.equalsIgnoreCase("image/gif"));
        final boolean privateMessage = message.isPrivateMessage();
        /* file params:
         1  |    2     |   3   |    4    |    5    |     6           |
                       | image/video/pdf | a/v/gif | vcard/apk/audio |
        url | filesize | width | height  | runtime | name            |
        */
        Message.FileParams fileParams = message.getFileParams();
        if (fileParams == null) fileParams = new Message.FileParams();
        Cid[] cids = new Cid[0];
        try {
            cids = calculateCids(new FileInputStream(file));
            fileParams.setCids(List.of(cids));
        } catch (final IOException | NoSuchAlgorithmException e) { }
        if (url == null) {
            for (Cid cid : cids) {
                url = mXmppConnectionService.getUrlForCid(cid);
                if (url != null) {
                    fileParams.url = url;
                    break;
                }
            }
        } else {
            fileParams.url = url;
        }
        fileParams.setName(file.getName());
        fileParams.setMediaType(mime);
        if (encrypted && !file.exists()) {
            Log.d(Config.LOGTAG, "skipping updateFileParams because file is encrypted");
            final DownloadableFile encryptedFile = getFile(message, false);
            if (encryptedFile.canRead()) fileParams.size = encryptedFile.getSize();
        } else {
            Log.d(Config.LOGTAG, "running updateFileParams");
            final boolean ambiguous = MimeUtils.AMBIGUOUS_CONTAINER_FORMATS.contains(mime);
            final boolean video = mime != null && mime.startsWith("video/");
            final boolean audio = mime != null && mime.startsWith("audio/");
            final boolean vcard = mime != null && mime.contains("vcard");
            final boolean apk = mime != null && mime.equals("application/vnd.android.package-archive");
            final boolean pdf = "application/pdf".equals(mime);
            if (file.canRead()) fileParams.size = file.getSize();
            if (ambiguous) {
                try {
                    final Dimensions dimensions = getVideoDimensions(file);
                    if (dimensions.valid()) {
                        Log.d(Config.LOGTAG, "ambiguous file " + mime + " is video");
                        fileParams.width = dimensions.width;
                        fileParams.height = dimensions.height;
                    } else {
                        Log.d(Config.LOGTAG, "ambiguous file " + mime + " is audio");
                        fileParams.runtime = getMediaRuntime(file, false);
                    }
                } catch (final NotAVideoFile e) {
                    Log.d(Config.LOGTAG, "ambiguous file " + mime + " is audio");
                    fileParams.runtime = getMediaRuntime(file, false);
                }
            } else if (image || video || pdf) {
                try {
                    final Dimensions dimensions;
                    if (video) {
                        dimensions = getVideoDimensions(file);
                    } else if (pdf) {
                        dimensions = getPDFDimensions(file);
                    } else if ("image/svg+xml".equals(mime)) {
                        SVG svg = SVG.getFromInputStream(new FileInputStream(file));
                        dimensions = new Dimensions((int) svg.getDocumentHeight(), (int) svg.getDocumentWidth());
                    } else {
                        dimensions = getImageDimensions(file);
                    }
                    if (dimensions.valid()) {
                        fileParams.width = dimensions.width;
                        fileParams.height = dimensions.height;
                        if (isGif || video) {
                            fileParams.runtime = getMediaRuntime(file, isGif);
                        }
                    }
                } catch (final IOException | SVGParseException | NotAVideoFile notAVideoFile) {
                    Log.d(Config.LOGTAG, "file with mime type " + file.getMimeType() + " was not a video file, trying to handle it as audio file");
                    try {
                        fileParams.runtime = getMediaRuntime(file, false);
                    } catch (Exception e) {
                        Log.d(Config.LOGTAG, "file with mime type " + file.getMimeType() + " was neither a video file nor an audio file");
                        //fall threw
                    }
                }
            } else if (audio) {
                fileParams.runtime = getMediaRuntime(file, false);
            } else if (vcard) {
           //     body.append("|0|0|0|") // 3, 4, 5
           //             .append(getVCard(file)); // 6 TODO: Add VCARD later again
            } else if (apk) {
             //   body.append("|0|0|0|") // 3, 4, 5
             //           .append(getAPK(file, mXmppConnectionService.getApplicationContext())); // 6  TODO: Add apk later again
            }
            if ("application/webxdc+zip".equals(mime)) {
                try {
                    final var zip = new ZipFile(file);
                    final ZipEntry manifestEntry = zip == null ? null : zip.getEntry("manifest.toml");
                    if (manifestEntry != null) {
                        final var manifest = Toml.parse(zip.getInputStream(manifestEntry));
                        if (manifest != null) {
                            final var name = manifest.getString("name");
                            if (name != null) fileParams.setName(name);
                        }
                    }
                } catch (final IOException e2) { }
            }
        }
        message.setFileParams(fileParams);
        message.setFileDeleted(false);
        message.setType(privateMessage ? Message.TYPE_PRIVATE_FILE : (image ? Message.TYPE_IMAGE : Message.TYPE_FILE));
        if (updateCids) {
            try {
                for (int i = 0; i < cids.length; i++) {
                    mXmppConnectionService.saveCid(cids[i], file);
                }
            } catch (XmppConnectionService.BlockedMediaException e) { }
        }
    }

    private Dimensions getPDFDimensions(final File file) {
        final ParcelFileDescriptor fileDescriptor;
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (fileDescriptor == null) {
                return new Dimensions(0, 0);
            }
        } catch (FileNotFoundException e) {
            return new Dimensions(0, 0);
        }
        try {
            final PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
            final PdfRenderer.Page page = pdfRenderer.openPage(0);
            final int height = page.getHeight();
            final int width = page.getWidth();
            page.close();
            pdfRenderer.close();
            return scalePdfDimensions(new Dimensions(height, width));
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to get dimensions for pdf document", e);
            return new Dimensions(0, 0);
        }
    }


    public int getMediaRuntime(final File file, final boolean isGif) {
        if (isGif) {
            try {
                final InputStream inputStream = mXmppConnectionService.getContentResolver().openInputStream(getUriForFile(mXmppConnectionService, file));
                final Movie movie = Movie.decodeStream(inputStream);
                int duration = movie.duration();
                close(inputStream);
                return duration;
            } catch (FileNotFoundException e) {
                Log.d(Config.LOGTAG, "unable to get image dimensions", e);
                return 0;
            }
        } else {
            try {
                final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(file.toString());
                final String value =
                        mediaMetadataRetriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (Strings.isNullOrEmpty(value)) {
                    return 0;
                }
                return Integer.parseInt(value);
            } catch (final Exception e) {
                return 0;
            }
        }
    }

    private String getAudioTitleArtist(final File file) {
        String artist;
        String title;
        StringBuilder builder = new StringBuilder();
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(file.toString());
            artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist == null) {
                artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            }
            if (artist == null) {
                artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
            }
            title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            mediaMetadataRetriever.release();
            boolean separator = false;
            if (artist != null && artist.length() > 0) {
                builder.append(artist);
                separator = true;
            }
            if (title != null && title.length() > 0) {
                if (separator) {
                    builder.append(" - ");
                }
                builder.append(title);
            }
            final String s = builder.substring(0, Math.min(128, builder.length()));
            final byte[] data = s.trim().getBytes(StandardCharsets.UTF_8);
            return Base64.encodeToString(data, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getAPK(File file, Context context) {
        String APKName;
        final PackageManager pm = context.getPackageManager();
        final PackageInfo pi = pm.getPackageArchiveInfo(file.toString(), 0);
        String AppName;
        String AppVersion;
        try {
            pi.applicationInfo.sourceDir = file.toString();
            pi.applicationInfo.publicSourceDir = file.toString();
            AppName = (String) pi.applicationInfo.loadLabel(pm);
            AppVersion = pi.versionName;
            Log.d(Config.LOGTAG, "APK name: " + AppName);
            APKName = " (" + AppName + " " + AppVersion + ")";
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(Config.LOGTAG, "no APK name detected");
            APKName = "";
        }

        byte[] data = APKName.getBytes(StandardCharsets.UTF_8);
        APKName = Base64.encodeToString(data, Base64.DEFAULT);
        return APKName;
    }

    private String getVCard(File file) {
        VCard VCard = new VCard();
        String VCardName = "";
        try {
            VCard = Ezvcard.parse(file).first();
            if (VCard != null) {
                final String version = VCard.getVersion().toString();
                Log.d(Config.LOGTAG, "VCard version: " + version);
                final String name = VCard.getFormattedName().getValue();
                VCardName = " (" + name + ")";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] data = VCardName.getBytes(StandardCharsets.UTF_8);
        VCardName = Base64.encodeToString(data, Base64.DEFAULT);

        return VCardName;
    }

    private Dimensions getImageDimensions(File file) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        final int rotation = getRotation(file);
        final boolean rotated = rotation == 90 || rotation == 270;
        final int imageHeight = rotated ? options.outWidth : options.outHeight;
        final int imageWidth = rotated ? options.outHeight : options.outWidth;
        return new Dimensions(imageHeight, imageWidth);
    }

    private Dimensions getVideoDimensions(File file) throws NotAVideoFile {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(metadataRetriever);
    }

    public Bitmap getPreviewForUri(Attachment attachment, int size, boolean cacheOnly) {
        final String key = "attachment_" + attachment.getUuid().toString() + "_" + size;
        final LruCache<String, Drawable> cache = mXmppConnectionService.getDrawableCache();
        Drawable drawable = cache.get(key);
        if (drawable != null || cacheOnly) {
            return drawDrawable(drawable);
        }
        Bitmap bitmap = null;
        DownloadableFile file = new DownloadableFile(attachment.getUri().getPath());
        if ("application/pdf".equals(attachment.getMime())) {
            bitmap = cropCenterSquare(getPDFPreview(file, size), size);
        } else if (attachment.getMime() != null && attachment.getMime().startsWith("video/")) {
            bitmap = cropCenterSquareVideo(attachment.getUri(), size);
            drawOverlay(bitmap, R.drawable.play_video, 0.75f);
        } else {
            bitmap = cropCenterSquare(attachment.getUri(), size);
            if (bitmap != null && "image/gif".equals(attachment.getMime())) {
                Bitmap withGifOverlay = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                drawOverlay(withGifOverlay, R.drawable.play_gif, 1.0f);
                bitmap.recycle();
                bitmap = withGifOverlay;
            }
        }
        if (key != null && bitmap != null) {
            cache.put(key, new BitmapDrawable(bitmap));
        }
        return bitmap;
    }

    private static Dimensions getVideoDimensions(Context context, Uri uri) throws NotAVideoFile {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            try {
                mediaMetadataRetriever.setDataSource(context, uri);
            } catch (RuntimeException e) {
                throw new NotAVideoFile(e);
            }
        } catch (Exception e) {
            throw new NotAVideoFile();
        }
        return getVideoDimensions(mediaMetadataRetriever);
    }

    private static Dimensions getVideoDimensionsOfFrame(MediaMetadataRetriever mediaMetadataRetriever) {
        Bitmap bitmap = null;
        try {
            bitmap = mediaMetadataRetriever.getFrameAtTime();
            return new Dimensions(bitmap.getHeight(), bitmap.getWidth());
        } catch (Exception e) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static Dimensions getVideoDimensions(MediaMetadataRetriever metadataRetriever) throws NotAVideoFile {
        String hasVideo = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (hasVideo == null) {
            throw new NotAVideoFile();
        }
        Dimensions dimensions = getVideoDimensionsOfFrame(metadataRetriever);
        if (dimensions != null) {
            return dimensions;
        }
        final int rotation;
        rotation = extractRotationFromMediaRetriever(metadataRetriever);
        boolean rotated = rotation == 90 || rotation == 270;
        int height;
        try {
            String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            height = Integer.parseInt(h);
        } catch (Exception e) {
            height = -1;
        }
        int width;
        try {
            String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            width = Integer.parseInt(w);
        } catch (Exception e) {
            width = -1;
        }
        try {
            metadataRetriever.release();
        } catch (final IOException e) {
            throw new NotAVideoFile();
        }
        Log.d(Config.LOGTAG, "extracted video dims " + width + "x" + height);
        return rotated ? new Dimensions(width, height) : new Dimensions(height, width);
    }

    private static int extractRotationFromMediaRetriever(MediaMetadataRetriever metadataRetriever) {
        String r = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            return Integer.parseInt(r);
        } catch (Exception e) {
            return 0;
        }
    }

    public Cid[] calculateCids(final Uri uri) throws IOException {
        return calculateCids(mXmppConnectionService.getContentResolver().openInputStream(uri));
    }

    public Cid[] calculateCids(final InputStream is) throws IOException {
        try {
            return CryptoHelper.cid(is, new String[]{"SHA-256", "SHA-1", "SHA-512"});
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public void updateMediaScanner(File file) {
        updateMediaScanner(file, null);
    }

    public void updateMediaScanner(File file, final Runnable callback) {
        MediaScannerConnection.scanFile(
                mXmppConnectionService,
                new String[] {file.getAbsolutePath()},
                null,
                new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {}

                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        if (callback != null && file.getAbsolutePath().equals(path)) {
                            callback.run();
                        } else {
                            Log.d(Config.LOGTAG, "media scanner scanned wrong file");
                            if (callback != null) {
                                callback.run();
                            }
                        }
                    }
                });
    }

    public void drawOverlay(Drawable bm, int pencil_overlay, float factor, boolean corner) {
    }

    private static class Dimensions {
        public final int width;
        public final int height;

        Dimensions(int height, int width) {
            this.width = width;
            this.height = height;
        }

        public int getMin() {
            return Math.min(width, height);
        }

        public boolean valid() {
            return width > 0 && height > 0;
        }
    }

    private static class NotAVideoFile extends Exception {
        public NotAVideoFile(Throwable t) {
            super(t);
        }

        public NotAVideoFile() {
            super();
        }
    }

    public static class ImageCompressionException extends Exception {

        ImageCompressionException(String message) {
            super(message);
        }
    }


    public static class FileCopyException extends Exception {
        private final int resId;

        private FileCopyException(@StringRes int resId) {
            this.resId = resId;
        }

        public @StringRes
        int getResId() {
            return resId;
        }
    }

    public Drawable getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getAvatarFile(avatar));
                return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    Rect r = rectForSize(w, h, size);
                    decoder.setTargetSize(r.width(), r.height());

                    int newSize = Math.min(r.width(), r.height());
                    int left = (r.width() - newSize) / 2;
                    int top = (r.height() - newSize) / 2;
                    decoder.setCrop(new Rect(left, top, left + newSize, top + newSize));
                });
            } catch (final IOException e) {
                return null;            //TODO: Temporary fix until animated SVG comes
            }
        } else {
            Bitmap bm = cropCenter(getAvatarUri(avatar), size, size);
            return bm == null ? null : new BitmapDrawable(bm);
        }
    }

    public Drawable getSVGSquare(Uri uri, int size) {
        try {
            SVG svg = SVG.getFromInputStream(mXmppConnectionService.getContentResolver().openInputStream(uri));
            svg.setDocumentPreserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.FULLSCREEN);

            float w = svg.getDocumentWidth();
            float h = svg.getDocumentHeight();
            float scale = Math.max((float) size / h, (float) size / w);
            float outWidth = scale * w;
            float outHeight = scale * h;
            float left = (size - outWidth) / 2;
            float top = (size - outHeight) / 2;
            RectF target = new RectF(left, top, left + outWidth, top + outHeight);
            if (svg.getDocumentViewBox() == null) svg.setDocumentViewBox(0, 0, w, h);
            svg.setDocumentWidth("100%");
            svg.setDocumentHeight("100%");

            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            svg.renderToCanvas(canvas, target);

            return new SVGDrawable(output);
        } catch (final IOException | SVGParseException e) {
            return null;
        }
    }

    public Drawable getSVG(File file, int size) {
        try {
            SVG svg = SVG.getFromInputStream(new FileInputStream(file));
            return drawSVG(svg, size);
        } catch (final IOException | SVGParseException | IllegalArgumentException e) {
            Log.w(Config.LOGTAG, "Could not parse SVG: " + e);
            return null;
        }
    }

    public Drawable drawSVG(SVG svg, int size) {
        try {
            svg.setDocumentPreserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.LETTERBOX);

            float w = svg.getDocumentWidth();
            float h = svg.getDocumentHeight();
            Rect r = rectForSize(w < 1 ? size : (int) w, h < 1 ? size : (int) h, size);
            if (svg.getDocumentViewBox() == null) svg.setDocumentViewBox(0, 0, w, h);
            svg.setDocumentWidth("100%");
            svg.setDocumentHeight("100%");

            Bitmap output = Bitmap.createBitmap(r.width(), r.height(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            svg.renderToCanvas(canvas);

            return new SVGDrawable(output);
        } catch (final SVGParseException e) {
            Log.w(Config.LOGTAG, "Could not parse SVG: " + e);
            return null;
        }
    }

    public boolean isFileAvailable(Message message) {
        return getFile(message).exists();
    }

    public static void close(final Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to close stream", e);
            }
        }
    }

    public static void close(final Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to close socket", e);
            }
        }
    }

    public static void close(final ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to close socket", e);
            }
        }
    }

    public static boolean dangerousFile(final Uri uri) {
        if (uri == null || Strings.isNullOrEmpty(uri.getScheme())) {
            return true;
        }
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // On Android 7 (and apps that target 7) it is now longer possible to share files
                // with a file scheme. By now you should probably not be running apps that target
                // anything less than 7 any more
                return true;
            } else {
                return isFileOwnedByProcess(uri);
            }
        }
        return false;
    }

    private static boolean isFileOwnedByProcess(final Uri uri) {
        final String path = uri.getPath();
        if (path == null) {
            return true;
        }
        try (final var pfd =
                     ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY)) {
            final FileDescriptor fd = pfd.getFileDescriptor();
            final StructStat st = Os.fstat(fd);
            return st.st_uid == android.os.Process.myUid();
        } catch (final Exception e) {
            // when in doubt. better safe than sorry
            return true;
        }
    }

    public static Bitmap rotateBitmap(File file, Bitmap bitmap, int orientation) {

        if (orientation == 1) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2:
                matrix.setScale(-1, 1);
                break;
            case 3:
                matrix.setRotate(180);
                break;
            case 4:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case 5:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case 6:
                matrix.setRotate(90);
                break;
            case 7:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case 8:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }

        try {
            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return oriented;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    public void saveFile(final Message message, final Activity activity) {
        new Thread(new SaveFileFinisher(getFile(message), new File(getDestinationToSaveFile(message)), activity, this)).start();
    }

    private static class SaveFileFinisher implements Runnable {

        private final DownloadableFile source;
        private final File destination;
        private final WeakReference<Activity> activityReference;
        private final FileBackend fileBackend;

        private SaveFileFinisher(DownloadableFile source, File destination, Activity activity, FileBackend fileBackend) {
            this.source = source;
            this.destination = destination;
            this.activityReference = new WeakReference<>(activity);
            this.fileBackend = fileBackend;
        }

        @Override
        public void run() {
            final Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    () -> {
                        try {
                            activity.runOnUiThread(() -> {
                                ToastCompat.makeText(activity, activity.getString(R.string.copy_file_to, destination), ToastCompat.LENGTH_SHORT).show();
                            });
                            fileBackend.copyFile(source, destination);
                            activity.runOnUiThread(() -> {
                                ToastCompat.makeText(activity, activity.getString(R.string.file_copied_to, destination), ToastCompat.LENGTH_SHORT).show();
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    public static void moveFile(String inputPath, String inputFile, String outputPath) {
        Log.d(Config.LOGTAG, "Move " + inputPath + File.separator + inputFile + " to " + outputPath);
        InputStream in = null;
        OutputStream out = null;
        try {
            //create output directory if it doesn't exist
            File dir = new File(outputPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            in = new FileInputStream(inputPath + File.separator + inputFile);
            out = new FileOutputStream(outputPath + File.separator + inputFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            // write the output file
            out.flush();
            out.close();
            out = null;
            // delete the original file
            new File(inputPath + File.separator + inputFile).delete();
        } catch (Exception e) {
            Log.e(Config.LOGTAG, e.getMessage());
        }
    }

    public String getDestinationToSaveFile(Message message) {
        final DownloadableFile file = getFile(message);
        final String mime = file.getMimeType();
        String extension = MimeUtils.guessExtensionFromMimeType(mime);
        if (extension == null) {
            Log.d(Config.LOGTAG, "extension from mime type was null");
            extension = "null";
        }
        if ("ogg".equals(extension) && mime.startsWith("audio/")) {
            extension = "oga";
        }
        String filename = fileDateFormat.format(new Date(message.getTimeSent())) + "_" + message.getUuid().substring(0, 4) + "." + extension;
        if (mime != null && mime.startsWith("image")) {
            return getGlobalPicturesPath() + File.separator + filename;
        } else if (mime != null && mime.startsWith("video")) {
            return getGlobalVideosPath() + File.separator + filename;
        } else if (mime != null && mime.startsWith("audio")) {
            return getGlobalAudiosPath() + File.separator + filename;
        } else {
            return getGlobalDocumentsPath() + File.separator + filename;
        }
    }

    public BitmapDrawable getFallbackThumbnail(final Message message, int size, boolean cacheOnly) {
        List<Element> thumbs = message.getFileParams() != null ? message.getFileParams().getThumbnails() : null;
        if (thumbs != null && !thumbs.isEmpty()) {
            for (Element thumb : thumbs) {
                final var uriS = thumb.getAttribute("uri");
                if (uriS == null) continue;
                Uri uri = Uri.parse(uriS);
                if (uri.getScheme().equals("data")) {
                    String[] parts = uri.getSchemeSpecificPart().split(",", 2);

                    final LruCache<String, Drawable> cache = mXmppConnectionService.getDrawableCache();
                    BitmapDrawable cached = (BitmapDrawable) cache.get(parts[1]);
                    if (cached != null || cacheOnly) return cached;

                    byte[] data;
                    if (Arrays.asList(parts[0].split(";")).contains("base64")) {
                        String[] parts2 = parts[0].split(";", 2);
                        parts[0] = parts2[0];
                        data = Base64.decode(parts[1], 0);
                    } else {
                        try {
                            data = parts[1].getBytes("UTF-8");
                        } catch (final IOException e) {
                            data = new byte[0];
                        }
                    }

                    if (parts[0].equals("image/blurhash")) {
                        int width = message.getFileParams().width;
                        if (width < 1 && thumb.getAttribute("width") != null) width = Integer.parseInt(thumb.getAttribute("width"));
                        if (width < 1) width = 1920;

                        int height = message.getFileParams().height;
                        if (height < 1 && thumb.getAttribute("height") != null) height = Integer.parseInt(thumb.getAttribute("height"));
                        if (height < 1) height = 1080;
                        Rect r = rectForSize(width, height, size);

                        Bitmap blurhash = BlurHashDecoder.INSTANCE.decode(parts[1], r.width(), r.height(), 1.0f, false);
                        if (blurhash != null) {
                            cached = new BitmapDrawable(blurhash);
                            cache.put(parts[1], cached);
                            return cached;
                        }
                    } else if (parts[0].equals("image/thumbhash")) {
                        ThumbHash.Image image;
                        try {
                            image = ThumbHash.thumbHashToRGBA(data);
                        } catch (final Exception e) {
                            continue;
                        }
                        int[] pixels = new int[image.width * image.height];
                        for (int i = 0; i < pixels.length; i++) {
                            pixels[i] = Color.argb(image.rgba[(i*4)+3] & 0xff, image.rgba[i*4] & 0xff, image.rgba[(i*4)+1] & 0xff, image.rgba[(i*4)+2] & 0xff);
                        }
                        cached = new BitmapDrawable(Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888));
                        cache.put(parts[1], cached);
                        return cached;
                    }
                }
            }
        }

        return null;
    }

    public Drawable getThumbnail(Message message, Resources res, int size, boolean cacheOnly) throws IOException {
        final LruCache<String, Drawable> cache = mXmppConnectionService.getDrawableCache();
        DownloadableFile file = getFile(message);
        Drawable thumbnail = cache.get(file.getAbsolutePath());
        if (thumbnail != null) return thumbnail;

        if ((thumbnail == null) && (!cacheOnly)) {
            synchronized (THUMBNAIL_LOCK) {
                List<Element> thumbs = message.getFileParams() != null ? message.getFileParams().getThumbnails() : null;
                if (thumbs != null && !thumbs.isEmpty()) {
                    for (Element thumb : thumbs) {
                        final var uriS = thumb.getAttribute("uri");
                        if (uriS == null) continue;
                        Uri uri = Uri.parse(uriS);
                        if (uri.getScheme().equals("data")) {
                            if (android.os.Build.VERSION.SDK_INT < 28) continue;
                            String[] parts = uri.getSchemeSpecificPart().split(",", 2);

                            byte[] data;
                            if (Arrays.asList(parts[0].split(";")).contains("base64")) {
                                String[] parts2 = parts[0].split(";", 2);
                                parts[0] = parts2[0];
                                data = Base64.decode(parts[1], 0);
                            } else {
                                data = parts[1].getBytes("UTF-8");
                            }

                            if (parts[0].equals("image/blurhash")) continue; // blurhash only for fallback
                            if (parts[0].equals("image/thumbhash")) continue; // thumbhash only for fallback

                            ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(data));
                            thumbnail = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                                int w = info.getSize().getWidth();
                                int h = info.getSize().getHeight();
                                Rect r = rectForSize(w, h, size);
                                decoder.setTargetSize(r.width(), r.height());
                            });

                            if (thumbnail != null) {
                                cache.put(file.getAbsolutePath(), thumbnail);
                                return thumbnail;
                            }
                        } else if (uri.getScheme().equals("cid")) {
                            Cid cid = BobTransfer.cid(uri);
                            if (cid == null) continue;
                            DownloadableFile f = mXmppConnectionService.getFileForCid(cid);
                            if (f != null && f.canRead()) {
                                return getThumbnail(f, res, size, cacheOnly);
                            }
                        }
                    }
                }
            }
        }

        return getThumbnail(file, res, size, cacheOnly);
    }

    public Drawable getThumbnail(DownloadableFile file, Resources res, int size, boolean cacheOnly) throws IOException {
        return getThumbnail(file, res, size, cacheOnly, file.getAbsolutePath());
    }

    public Drawable getThumbnail(DownloadableFile file, Resources res, int size, boolean cacheOnly, String cacheKey) throws IOException {
        final LruCache<String, Drawable> cache = mXmppConnectionService.getDrawableCache();
        Drawable thumbnail = cache.get(cacheKey);
        if ((thumbnail == null) && (!cacheOnly) && file.exists()) {
            synchronized (THUMBNAIL_LOCK) {
                thumbnail = cache.get(cacheKey);
                if (thumbnail != null) {
                    return thumbnail;
                }
                final String mime = file.getMimeType();
                if ("image/svg+xml".equals(mime)) {
                    thumbnail = getSVG(file, size);
                } else if ("application/pdf".equals(mime)) {
                    thumbnail = new BitmapDrawable(res, getPDFPreview(file, size));
                } else if (mime.startsWith("video/")) {
                    thumbnail = new BitmapDrawable(res, getVideoPreview(file, size));
                } else {
                    thumbnail = getImagePreview(file, res, size, mime);
                    if (thumbnail == null) {
                        throw new FileNotFoundException();
                    }
                }
                cache.put(cacheKey, thumbnail);
            }
        }
        return thumbnail;
    }


    public static Rect rectForSize(int w, int h, int size) {
        int scalledW;
        int scalledH;
        if (w <= h) {
            scalledW = Math.max((int) (w / ((double) h / size)), 1);
            scalledH = size;
        } else {
            scalledW = size;
            scalledH = Math.max((int) (h / ((double) w / size)), 1);
        }

        if (scalledW > w || scalledH > h) return new Rect(0, 0, w, h);

        return new Rect(0, 0, scalledW, scalledH);
    }

    private Drawable getImagePreview(File file, Resources res, int size, final String mime) throws IOException {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(file);
            return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                Rect r = rectForSize(w, h, size);
                decoder.setTargetSize(r.width(), r.height());
            });
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(file, size);
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } catch (OutOfMemoryError e) {
                options.inSampleSize *= 2;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
            if (bitmap == null) return null;

            bitmap = resize(bitmap, size);
            bitmap = rotate(bitmap, getRotation(file));
            if (mime.equals("image/gif")) {
                Bitmap withGifOverlay = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                drawOverlay(withGifOverlay, paintOverlayBlack(withGifOverlay) ? R.drawable.play_gif_black : R.drawable.play_gif_white, 1.0f);
                bitmap.recycle();
                bitmap = withGifOverlay;
            }
            return new BitmapDrawable(res, bitmap);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap getPdfDocumentPreview(final File file, final int size) {
        try {
            final ParcelFileDescriptor fileDescriptor =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            final Bitmap rendered = renderPdfDocument(fileDescriptor, size, true);
            drawOverlay(
                    rendered,
                    paintOverlayBlackPdf(rendered)
                            ? R.drawable.open_pdf_black
                            : R.drawable.open_pdf_white,
                    0.75f);
            return rendered;
        } catch (final IOException | SecurityException e) {
            Log.d(Config.LOGTAG, "unable to render PDF document preview", e);
            final Bitmap placeholder = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            placeholder.eraseColor(0xff000000);
            return placeholder;
        }
    }


    /** https://stackoverflow.com/a/3943023/210897 */
    private boolean paintOverlayBlack(final Bitmap bitmap) {
        final int h = bitmap.getHeight();
        final int w = bitmap.getWidth();
        int record = 0;
        for (int y = Math.round(h * IGNORE_PADDING); y < h - Math.round(h * IGNORE_PADDING); ++y) {
            for (int x = Math.round(w * IGNORE_PADDING);
                 x < w - Math.round(w * IGNORE_PADDING);
                 ++x) {
                int pixel = bitmap.getPixel(x, y);
                if ((Color.red(pixel) * 0.299
                        + Color.green(pixel) * 0.587
                        + Color.blue(pixel) * 0.114)
                        > 186) {
                    --record;
                } else {
                    ++record;
                }
            }
        }
        return record < 0;
    }

    private boolean paintOverlayBlackPdf(final Bitmap bitmap) {
        final int h = bitmap.getHeight();
        final int w = bitmap.getWidth();
        int white = 0;
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int pixel = bitmap.getPixel(x, y);
                if ((Color.red(pixel) * 0.299
                        + Color.green(pixel) * 0.587
                        + Color.blue(pixel) * 0.114)
                        > 186) {
                    white++;
                }
            }
        }
        return white > (h * w * 0.4f);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap renderPdfDocument(
            ParcelFileDescriptor fileDescriptor, int targetSize, boolean fit) throws IOException {
        final PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
        final PdfRenderer.Page page = pdfRenderer.openPage(0);
        final Dimensions dimensions =
                scalePdfDimensions(
                        new Dimensions(page.getHeight(), page.getWidth()), targetSize, fit);
        final Bitmap rendered =
                Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888);
        rendered.eraseColor(0xffffffff);
        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pdfRenderer.close();
        fileDescriptor.close();
        return rendered;
    }

    private static Dimensions scalePdfDimensions(
            final Dimensions in, final int target, final boolean fit) {
        final int w, h;
        if (fit == (in.width <= in.height)) {
            w = Math.max((int) (in.width / ((double) in.height / target)), 1);
            h = target;
        } else {
            w = target;
            h = Math.max((int) (in.height / ((double) in.width / target)), 1);
        }
        return new Dimensions(h, w);
    }

    public static class SVGDrawable extends BitmapDrawable {
        public SVGDrawable(Bitmap bm) { super(bm); }
    }
}
