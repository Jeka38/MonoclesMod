package eu.siacs.conversations.ui;

import static eu.siacs.conversations.utils.StorageHelper.getConversationsDirectory;

import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.leinardi.android.speeddial.SpeedDialActionItem;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaViewerBinding;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.util.Rationals;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.MimeUtils;
import me.drakeet.support.toast.ToastCompat;

public class MediaViewerActivity extends XmppActivity implements AudioManager.OnAudioFocusChangeListener {

    Integer oldOrientation;
    ExoPlayer player;
    Uri mFileUri;
    File mFile;
    int height = 0;
    int width = 0;
    Rational aspect;
    int rotation = 0;
    boolean isImage = false;
    boolean isVideo = false;
    private ActivityMediaViewerBinding binding;
    private GestureDetector gestureDetector;

    public static String getMimeType(String path) {
        try {
            String type = null;
            String extension = path.substring(path.lastIndexOf(".") + 1, path.length());
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_viewer);
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                if (isImage) {
                    if (binding.speedDial.isShown()) {
                        hideFAB();
                    } else {
                        showFAB();
                    }
                }
                return super.onDown(e);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null && actionBar.isShowing()) {
            actionBar.hide();
        }

        oldOrientation = getRequestedOrientation();

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = 1;
        }
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //binding.speedDial.inflate(R.menu.media_viewer);
    }

    private void share() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(getMimeType(mFile.toString()));
        share.putExtra(Intent.EXTRA_STREAM, FileBackend.getUriForFile(this, mFile));
        try {
            startActivity(Intent.createChooser(share, getText(R.string.share_with)));
        } catch (ActivityNotFoundException e) {
            //This should happen only on faulty androids because normally chooser is always available
            ToastCompat.makeText(this, R.string.no_application_found_to_open_file, ToastCompat.LENGTH_SHORT).show();
        }
    }

    private void deleteFile() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_file_dialog);
        builder.setMessage(R.string.delete_file_dialog_msg);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            if (this.xmppConnectionService.getFileBackend().deleteFile(mFile)) {
                finish();
            }
        });
        builder.create().show();
    }

    private void open() {
        Uri uri;
        try {
            uri = FileBackend.getUriForFile(this, mFile);
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "No permission to access " + mFile.getAbsolutePath(), e);
            ToastCompat.makeText(this, this.getString(R.string.no_permission_to_access_x, mFile.getAbsolutePath()), ToastCompat.LENGTH_SHORT).show();
            return;
        }
        String mime = MimeUtils.guessMimeTypeFromUri(this, uri);
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(uri, mime);
        openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PackageManager manager = this.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri, "*/*");
        }
        if (player != null && isVideo) {
            openIntent.putExtra("position", player.getCurrentPosition());
        }
        try {
            this.startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            ToastCompat.makeText(this, R.string.no_application_found_to_open_file, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("image")) {
                mFileUri = intent.getParcelableExtra("image");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        isImage = true;
                        DisplayImage(mFile, mFileUri);
                    } catch (Exception e) {
                        isImage = false;
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.error_file_corrupt), ToastCompat.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.file_deleted), ToastCompat.LENGTH_SHORT).show();
                }
            } else if (intent.hasExtra("video")) {
                mFileUri = intent.getParcelableExtra("video");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        isVideo = true;
                        DisplayVideo(mFileUri);
                    } catch (Exception e) {
                        isVideo = false;
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.error_file_corrupt), ToastCompat.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.file_deleted), ToastCompat.LENGTH_SHORT).show();
                }
            }
        }
        if (isDeletableFile(mFile)) {
            binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_delete, R.drawable.ic_delete_white_24dp)
                    .setLabel(R.string.action_delete)
                    .setFabImageTintColor(ContextCompat.getColor(this, R.color.white))
                    .create()
            );
        }
        binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_open, R.drawable.ic_open_in_new_white_24dp)
                .setLabel(R.string.open_with)
                .setFabImageTintColor(ContextCompat.getColor(this, R.color.white))
                .create()
        );
        binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_share, R.drawable.ic_share_white_24dp)
                .setLabel(R.string.share)
                .setFabImageTintColor(ContextCompat.getColor(this, R.color.white))
                .create()
        );

        if (isDeletableFile(mFile)) {
            binding.speedDial.setOnActionSelectedListener(actionItem -> {
                switch (actionItem.getId()) {
                    case R.id.action_share:
                        share();
                        break;
                    case R.id.action_open:
                        open();
                        break;
                    case R.id.action_delete:
                        deleteFile();
                        break;
                    default:
                        return false;
                }
                return false;
            });
        } else {
            binding.speedDial.setOnActionSelectedListener(actionItem -> {
                switch (actionItem.getId()) {
                    case R.id.action_share:
                        share();
                        break;
                    case R.id.action_open:
                        open();
                        break;
                    default:
                        return false;
                }
                return false;
            });
        }
        binding.speedDial.getMainFab().setSupportImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.realwhite)));
    }

    private void DisplayImage(final File file, final Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(file.getPath()).getAbsolutePath(), options);
        height = options.outHeight;
        width = options.outWidth;
        aspect = new Rational(width, height);
        rotation = getRotation(Uri.parse("file://" + file.getAbsolutePath()));
        Log.d(Config.LOGTAG, "Image height: " + height + ", width: " + width + ", rotation: " + rotation + " aspect: " + aspect);
        if (useAutoRotateScreen()) {
            rotateScreen(width, height, rotation);
        }
        try {
            binding.messageImageView.setVisibility(View.VISIBLE);
            Glide.with(this).load(uri).into(binding.messageImageView);
            binding.messageImageView.setOnPhotoTapListener((view, motionEvent, listener) -> {
                if (isImage) {
                    if (binding.speedDial.isShown()) {
                        hideFAB();
                    } else {
                        showFAB();
                    }
                }
            });
        } catch (Exception e) {
            ToastCompat.makeText(this, getString(R.string.error_file_corrupt), ToastCompat.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void DisplayVideo(final Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(uri.getPath());
            Bitmap bitmap = null;
            try {
                bitmap = retriever.getFrameAtTime(0);
                height = bitmap.getHeight();
                width = bitmap.getWidth();
            } catch (Exception e) {
                height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            try {
                rotation = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            } catch (Exception e) {
                rotation = 0;
            }
            aspect = new Rational(width, height);
            Log.d(Config.LOGTAG, "Video height: " + height + ", width: " + width + ", rotation: " + rotation + ", aspect: " + aspect);
            if (useAutoRotateScreen()) {
                rotateScreen(width, height, rotation);
            }
            binding.messageVideoView.setVisibility(View.VISIBLE);
            player = new ExoPlayer.Builder(this).build();
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Player.Listener.super.onIsPlayingChanged(isPlaying);
                    if (isPlaying) {
                        hideFAB();
                    } else {
                        if (Compatibility.runsTwentyFour() && isInPictureInPictureMode()) {
                            hideFAB();
                        } else {
                            showFAB();
                        }
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    open();
                }
            });
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            binding.messageVideoView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            player.setPlayWhenReady(true);
            final MediaSessionCompat session = new MediaSessionCompat(this, getPackageName());
            final MediaSessionConnector connector = new MediaSessionConnector(session);
            connector.setPlayer(player);
            session.setActive(true);
            requestAudioFocus();
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
//            binding.messageVideoView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
        } catch (Exception e) {
            e.printStackTrace();
            open();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void PIPVideo() {
        try {
            binding.messageVideoView.hideController();
            binding.speedDial.setVisibility(View.GONE);
            if (supportsPIP()) {
                if (Compatibility.runsTwentySix()) {
                    final Rational rational = new Rational(width, height);
                    final Rational clippedRational = Rationals.clip(rational);
                    final PictureInPictureParams params = new PictureInPictureParams.Builder()
                            .setAspectRatio(clippedRational)
                            .build();
                    this.enterPictureInPictureMode(params);
                } else {
                    this.enterPictureInPictureMode();
                }
            }
        } catch (final IllegalStateException e) {
            // this sometimes happens on Samsung phones (possibly when Knox is enabled)
            Log.w(Config.LOGTAG, "unable to enter picture in picture mode", e);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        if (isInPictureInPictureMode) {
            startPlayer();
            hideFAB();
        } else {
            showFAB();
        }
    }

    private void releaseAudiFocus() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.abandonAudioFocus(this);
        }
    }

    private void requestAudioFocus() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    @Override
    public void onBackPressed() {
        if (isVideo && isPlaying() && supportsPIP()) {
            PIPVideo();
        } else {
            super.onBackPressed();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isVideo) {
            PIPVideo();
        }
    }

    private int getRotation(Uri image) {
        try (final InputStream is = this.getContentResolver().openInputStream(image)) {
            return is == null ? 0 : FileBackend.getRotation(is);
        } catch (final Exception e) {
            return 0;
        }
    }

    private void rotateScreen(final int width, final int height, final int rotation) {
        if (width > height) {
            if (rotation == 0 || rotation == 180) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else {
            if (rotation == 90 || rotation == 270) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }
    }

    private void pausePlayer() {
        if (player != null && isVideo && isPlaying()) {
            player.setPlayWhenReady(false);
            player.getPlaybackState();
            if (Compatibility.runsTwentyFour() && isInPictureInPictureMode()) {
                hideFAB();
            } else {
                showFAB();
            }
        }
    }

    private void startPlayer() {
        if (player != null && isVideo && !isPlaying()) {
            player.setPlayWhenReady(true);
            player.getPlaybackState();
            hideFAB();
        }
    }

    private void stopPlayer() {
        if (player != null && isVideo) {
            if (supportsPIP()) {
                finishAndRemoveTask();
            }
            if (isPlaying()) {
                player.stop();
            }
            player.release();
            if (Compatibility.runsTwentyFour() && isInPictureInPictureMode()) {
                hideFAB();
            } else {
                showFAB();
            }
        }
    }

    private boolean isPlaying() {
        return player != null
                && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlayWhenReady();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = 1;
        }
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!isPlaying()) {
            showFAB();
        } else {
            hideFAB();
        }
        if (!isPlaying()) {
            startPlayer();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        if (Compatibility.runsTwentyFour() && isInPictureInPictureMode()) {
            startPlayer();
        } else {
            pausePlayer();
        }
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = -1;
        }
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onPause();
    }

    @Override
    public void onStop() {
        stopPlayer();
        releaseAudiFocus();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = -1;
        }
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onStop();
    }

    @Override
    protected void onBackendConnected() {

    }

    public boolean useMaxBrightness() {
        return getPreferences().getBoolean("use_max_brightness", getResources().getBoolean(R.bool.use_max_brightness));
    }

    public boolean useAutoRotateScreen() {
        return getPreferences().getBoolean("use_auto_rotate", getResources().getBoolean(R.bool.auto_rotate));
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(Config.LOGTAG, "Audio focus granted.");
        } else if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.i(Config.LOGTAG, "Audio focus failed.");
        }
    }

    private boolean isDeletableFile(File file) {
        return (file == null || !file.toString().startsWith("/") || file.toString().contains(getConversationsDirectory(this, "null").getAbsolutePath()));
    }

    private void showFAB() {
        binding.speedDial.show();
    }

    private void hideFAB() {
        binding.speedDial.hide();
    }

    private boolean supportsPIP() {
        if (Compatibility.runsTwentyFour()) {
            return this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
        } else {
            return false;
        }
    }
}