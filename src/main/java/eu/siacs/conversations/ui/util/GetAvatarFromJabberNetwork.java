package eu.siacs.conversations.ui.util;

import static eu.siacs.conversations.Config.MILLISECONDS_IN_DAY;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DimenRes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;

public class GetAvatarFromJabberNetwork extends AsyncTask<String, Void, Bitmap> {
    private static Map<String, File> linkMap = new HashMap<>();
    private final WeakReference<ImageView> imageViewReference;
    private Bitmap bitmap = null;
    private AvatarService.Avatarable avatarable;
    ImageView imageView;
    int size;
    boolean overlay;
    XmppConnectionService xmppConnectionService;

    public GetAvatarFromJabberNetwork(final XmppConnectionService xmppConnectionService, final AvatarService.Avatarable avatarable, final ImageView imageView, final @DimenRes int size, final boolean overlay) {
        imageViewReference = new WeakReference<>(imageView);
        this.avatarable = avatarable;
        this.imageView = imageView;
        this.size = size;
        this.overlay = overlay;
        this.xmppConnectionService = xmppConnectionService;
    }

    @Override
    protected Bitmap doInBackground(String... url) {
        String stringUrl = url[0];
        long lastUpdate = xmppConnectionService.getPreferences().getLong("lastAvatarUpdate", 0L);
        final long now = System.currentTimeMillis();
        final File data = linkMap.get(stringUrl);
        if (data == null) {
            final File cachedBitmap = new File(xmppConnectionService.getCacheDir(), "avatars" + File.separator + stringUrl.hashCode());
            if (cachedBitmap.exists() && cachedBitmap.length() > 0 && now <= lastUpdate + MILLISECONDS_IN_DAY) {
                if (!loadCachedBitmap(cachedBitmap)) {
                    downloadBitmap(cachedBitmap, stringUrl, now);
                    linkMap.put(stringUrl, cachedBitmap);
                }
            } else {
                cachedBitmap.getParentFile().mkdirs();
                xmppConnectionService.getFileBackend().deleteFile(cachedBitmap);
                downloadBitmap(cachedBitmap, stringUrl, now);
            }
        } else {
            loadCachedBitmap(data);
        }
        return bitmap;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        AvatarWorkerTask.loadAvatar(avatarable, imageView, size, overlay, null);
    }

    private boolean downloadBitmap(final File cachedBitmap, final String stringUrl, final long now) {
        xmppConnectionService.getPreferences().edit().putLong("lastAvatarUpdate", now).commit();
        try (InputStream inputStream = new java.net.URL(stringUrl).openStream()) {
            bitmap = BitmapFactory.decodeStream(inputStream);
            OutputStream os;
            try {
                os = new FileOutputStream(cachedBitmap);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                os.flush();
                os.close();
                Log.d(Config.LOGTAG, "GetAvatarFromJabberNetwork: Load avatar from url: " + stringUrl);
                return true;
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "GetAvatarFromJabberNetwork: Error caching avatar bitmap: ", e);
                xmppConnectionService.getFileBackend().deleteFile(cachedBitmap);
            }
        } catch (Exception e) {
            //igrnore
        }
        return false;
    }

    private boolean loadCachedBitmap(final File cachedBitmap) {
        try {
            InputStream is;
            try {
                is = new FileInputStream(cachedBitmap);
                bitmap = BitmapFactory.decodeStream(is);
                is.close();
                Log.d(Config.LOGTAG, "GetAvatarFromJabberNetwork: Load avatar from cache: " + cachedBitmap.getPath());
                return true;
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "GetAvatarFromJabberNetwork: Error fetching cached avatar bitmap: ", e);
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        if (imageViewReference != null && bitmap != null && !isCancelled()) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        } else {
            AvatarWorkerTask.loadAvatar(avatarable, imageView, size, overlay, null);
        }
    }
}
