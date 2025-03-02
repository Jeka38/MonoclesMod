package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.WeakReferenceSet;

public class AudioPlayer implements View.OnClickListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, Runnable, SensorEventListener, AudioManager.OnAudioFocusChangeListener {

    private static final int REFRESH_INTERVAL = 250;
    private static final Object LOCK = new Object();
    private static MediaPlayer player = null;
    private static Message currentlyPlayingMessage = null;
    private static PowerManager.WakeLock wakeLock;
    private final MessageAdapter messageAdapter;
    private final WeakReferenceSet<RelativeLayout> audioPlayerLayouts = new WeakReferenceSet<>();
    private final SensorManager sensorManager;
    private final Sensor proximitySensor;
    private final PendingItem<WeakReference<ImageButton>> pendingOnClickView = new PendingItem<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isEarpieceBefore = false;

    private final Handler handler = new Handler();

    public AudioPlayer(MessageAdapter adapter) {
        final Context context = adapter.getContext();
        this.messageAdapter = adapter;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.proximitySensor = this.sensorManager == null ? null : this.sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        initializeProximityWakeLock(context);
        synchronized (AudioPlayer.LOCK) {
            if (AudioPlayer.player != null) {
                AudioPlayer.player.setOnCompletionListener(this);
                if (AudioPlayer.player.isPlaying() && sensorManager != null) {
                    sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
        }
    }

    private static String formatTime(int ms) {
        return String.format(Locale.ENGLISH, "%d:%02d", ms / 60000, Math.min(Math.round((ms % 60000) / 1000f), 59));
    }

    private void initializeProximityWakeLock(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            synchronized (AudioPlayer.LOCK) {
                if (AudioPlayer.wakeLock == null) {
                    final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    AudioPlayer.wakeLock = powerManager == null ? null : powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, AudioPlayer.class.getSimpleName());
                    AudioPlayer.wakeLock.setReferenceCounted(false);
                }
            }
        } else {
            AudioPlayer.wakeLock = null;
        }
    }

    public void init(RelativeLayout audioPlayer, Message message) {
        synchronized (AudioPlayer.LOCK) {
            audioPlayer.setTag(message);
            if (init(ViewHolder.get(audioPlayer), message)) {
                this.audioPlayerLayouts.addWeakReferenceTo(audioPlayer);
                executor.execute(() -> this.stopRefresher(true));
            } else {
                this.audioPlayerLayouts.removeWeakReferenceTo(audioPlayer);
            }
        }
    }

    private boolean init(ViewHolder viewHolder, Message message) {
        messageAdapter.getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        viewHolder.progress.setOnSeekBarChangeListener(this);
        ColorStateList color = ThemeHelper.AudioPlayerColor(messageAdapter.getContext());
        viewHolder.progress.setThumbTintList(color);
        viewHolder.progress.setProgressTintList(color);
        viewHolder.playPause.setAlpha(viewHolder.darkBackground ? 0.7f : 0.57f);
        viewHolder.playPause.setOnClickListener(this);
        if (message == currentlyPlayingMessage) {
            if (AudioPlayer.player != null && AudioPlayer.player.isPlaying()) {
                viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
                viewHolder.progress.setEnabled(true);
            } else {
                viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
                viewHolder.progress.setEnabled(false);
            }
            return true;
        } else {
            viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
            viewHolder.runtime.setText(formatTime(message.getFileParams().runtime));
            viewHolder.progress.setProgress(0);
            viewHolder.progress.setEnabled(false);
            return false;
        }
    }

    @Override
    public synchronized void onClick(View v) {
        if (v.getId() == R.id.play_pause) {
            synchronized (LOCK) {
                startStop((ImageButton) v);
            }
        }
    }

    private void startStop(ImageButton playPause) {
        if (Compatibility.runsThirtyThree() && ContextCompat.checkSelfPermission(messageAdapter.getActivity(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(messageAdapter.getActivity(), Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(messageAdapter.getActivity(), Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            pendingOnClickView.push(new WeakReference<>(playPause));
            ActivityCompat.requestPermissions(messageAdapter.getActivity(), new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO}, ConversationsActivity.REQUEST_PLAY_PAUSE);
            return;
        } else if (!Compatibility.runsThirtyThree() && ContextCompat.checkSelfPermission(messageAdapter.getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(messageAdapter.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingOnClickView.push(new WeakReference<>(playPause));
            ActivityCompat.requestPermissions(messageAdapter.getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_PLAY_PAUSE);
            return;
        }
        initializeProximityWakeLock(playPause.getContext());
        final RelativeLayout audioPlayer = (RelativeLayout) playPause.getParent();
        final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
        final Message message = (Message) audioPlayer.getTag();
        if (startStop(viewHolder, message)) {
            this.audioPlayerLayouts.clear();
            this.audioPlayerLayouts.addWeakReferenceTo(audioPlayer);
            stopRefresher(true);
        }
    }

    private boolean playPauseCurrent(ViewHolder viewHolder) {
        viewHolder.playPause.setAlpha(viewHolder.darkBackground ? 0.7f : 0.57f);
        if (player.isPlaying()) {
            viewHolder.progress.setEnabled(false);
            player.pause();
            releaseAudioFocus();
            messageAdapter.flagScreenOff();
            releaseProximityWakeLock();
            viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
        } else {
            viewHolder.progress.setEnabled(true);
            requestAudioFocus();
            player.start();
            messageAdapter.flagScreenOn();
            acquireProximityWakeLock();
            this.stopRefresher(true);
            viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
        }
        return false;
    }

    private void play(ViewHolder viewHolder, Message message, boolean earpiece, double progress) {
        if (play(viewHolder, message, earpiece)) {
            if (messageAdapter.autoPauseVoice() && (isEarpieceBefore && !earpiece)) {
                playPauseCurrent(viewHolder);
            }
            AudioPlayer.player.seekTo((int) (AudioPlayer.player.getDuration() * progress));
            isEarpieceBefore = earpiece;
        }
    }

    private boolean play(ViewHolder viewHolder, Message message, boolean earpiece) {
        AudioPlayer.player = new MediaPlayer();
        try {
            AudioPlayer.currentlyPlayingMessage = message;
            AudioPlayer.player.setAudioStreamType(earpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            AudioPlayer.player.setDataSource(messageAdapter.getFileBackend().getFile(message).getAbsolutePath());
            AudioPlayer.player.setOnCompletionListener(this);
            AudioPlayer.player.prepare();
            requestAudioFocus();
            AudioPlayer.player.start();
            messageAdapter.flagScreenOn();
            acquireProximityWakeLock();
            viewHolder.progress.setEnabled(true);
            viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
            return true;
        } catch (Exception e) {
            messageAdapter.flagScreenOff();
            releaseProximityWakeLock();
            AudioPlayer.currentlyPlayingMessage = null;
            sensorManager.unregisterListener(this);
            return false;
        }
    }

    public void startStopPending() {
        WeakReference<ImageButton> reference = pendingOnClickView.pop();
        if (reference != null) {
            ImageButton imageButton = reference.get();
            if (imageButton != null) {
                startStop(imageButton);
            }
        }
    }

    private boolean startStop(ViewHolder viewHolder, Message message) {
        if (message == currentlyPlayingMessage && player != null) {
            return playPauseCurrent(viewHolder);
        }
        if (AudioPlayer.player != null) {
            stopCurrent();
        }
        return play(viewHolder, message, false);
    }

    private void stopCurrent() {
        if (AudioPlayer.player.isPlaying()) {
            AudioPlayer.player.stop();
        }
        releaseAudioFocus();
        AudioPlayer.player.release();
        messageAdapter.flagScreenOff();
        releaseProximityWakeLock();
        AudioPlayer.player = null;
        resetPlayerUi();
    }

    private void resetPlayerUi() {
        for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
            resetPlayerUi(audioPlayer.get());
        }
    }

    private void resetPlayerUi(RelativeLayout audioPlayer) {
        if (audioPlayer == null) {
            return;
        }
        final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
        final Message message = (Message) audioPlayer.getTag();
        viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
        if (message != null) {
            viewHolder.runtime.setText(formatTime(message.getFileParams().runtime));
        }
        viewHolder.progress.setProgress(0);
        viewHolder.progress.setEnabled(false);
    }

    @Override
    public void onCompletion(android.media.MediaPlayer mediaPlayer) {
        synchronized (AudioPlayer.LOCK) {
            this.stopRefresher(false);
            if (AudioPlayer.player == mediaPlayer) {
                AudioPlayer.currentlyPlayingMessage = null;
                AudioPlayer.player = null;
            }
            mediaPlayer.release();
            messageAdapter.flagScreenOff();
            releaseProximityWakeLock();
            resetPlayerUi();
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        synchronized (AudioPlayer.LOCK) {
            final RelativeLayout audioPlayer = (RelativeLayout) seekBar.getParent();
            final Message message = (Message) audioPlayer.getTag();
            final MediaPlayer player = AudioPlayer.player;
            if (fromUser && message == AudioPlayer.currentlyPlayingMessage && player != null) {
                float percent = progress / 100f;
                int duration = player.getDuration();
                int seekTo = Math.round(duration * percent);
                player.seekTo(seekTo);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    public void stop() {
        synchronized (AudioPlayer.LOCK) {
            stopRefresher(false);
            if (AudioPlayer.player != null) {
                stopCurrent();
            }
            AudioPlayer.currentlyPlayingMessage = null;
            sensorManager.unregisterListener(this);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        }
    }

    private void stopRefresher(boolean runOnceMore) {
        this.handler.removeCallbacks(this);
        if (runOnceMore) {
            this.handler.post(this);
        }
    }

    public void unregisterListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void run() {
        synchronized (AudioPlayer.LOCK) {
            if (AudioPlayer.player != null) {
                boolean renew = false;
                final int current = player.getCurrentPosition();
                final int duration = player.getDuration();
                for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
                    renew |= refreshAudioPlayer(audioPlayer.get(), current, duration);
                }
                if (renew && AudioPlayer.player.isPlaying()) {
                    handler.postDelayed(this, REFRESH_INTERVAL);
                }
            }
        }
    }

    private boolean refreshAudioPlayer(RelativeLayout audioPlayer, int current, int duration) {
        if (audioPlayer == null || audioPlayer.getVisibility() != View.VISIBLE) {
            return false;
        }
        final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
        if (duration <= 0) {
            viewHolder.progress.setProgress(100);
        } else {
            viewHolder.progress.setProgress(current * 100 / duration);
        }
        viewHolder.runtime.setText(String.format("%s / %s", formatTime(current), formatTime(duration)));
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) {
            return;
        }
        if (AudioPlayer.player == null || !AudioPlayer.player.isPlaying()) {
            return;
        }
        final int streamType;
        if (event.values[0] < proximitySensor.getMaximumRange()) {
            streamType = AudioManager.STREAM_VOICE_CALL;
        } else {
            streamType = AudioManager.STREAM_MUSIC;
        }
        messageAdapter.setVolumeControl(streamType);
        double position = AudioPlayer.player.getCurrentPosition();
        double duration = AudioPlayer.player.getDuration();
        double progress = position / duration;
        if (AudioPlayer.player.getAudioStreamType() != streamType) {
            synchronized (AudioPlayer.LOCK) {
                AudioPlayer.player.stop();
                releaseAudioFocus();
                AudioPlayer.player.release();
                AudioPlayer.player = null;
                try {
                    ViewHolder currentViewHolder = getCurrentViewHolder();
                    if (currentViewHolder != null) {
                        messageAdapter.getActivity().setVolumeControlStream(streamType);
                        play(currentViewHolder, currentlyPlayingMessage, streamType == AudioManager.STREAM_VOICE_CALL, progress);
                    }
                } catch (Exception e) {
                    Log.d(Config.LOGTAG, "AudioPlayer Exception: " + e);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void acquireProximityWakeLock() {
        synchronized (AudioPlayer.LOCK) {
            if (wakeLock != null) {
                wakeLock.acquire();
            }
        }
    }

    private void releaseProximityWakeLock() {
        synchronized (AudioPlayer.LOCK) {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
        messageAdapter.setVolumeControl(AudioManager.STREAM_MUSIC);
    }

    private ViewHolder getCurrentViewHolder() {
        for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
            final Message message = (Message) audioPlayer.get().getTag();
            if (message == currentlyPlayingMessage) {
                return ViewHolder.get(audioPlayer.get());
            }
        }
        return null;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(Config.LOGTAG, "Audio focus granted.");
        } else if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.i(Config.LOGTAG, "Audio focus failed.");
        }
    }

    public static class ViewHolder {
        private TextView runtime;
        private SeekBar progress;
        private ImageButton playPause;
        private boolean darkBackground = false;

        public static ViewHolder get(RelativeLayout audioPlayer) {
            ViewHolder viewHolder = (ViewHolder) audioPlayer.getTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER);
            if (viewHolder == null) {
                viewHolder = new ViewHolder();
                viewHolder.runtime = audioPlayer.findViewById(R.id.runtime);
                viewHolder.progress = audioPlayer.findViewById(R.id.progress);
                viewHolder.playPause = audioPlayer.findViewById(R.id.play_pause);
                audioPlayer.setTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER, viewHolder);
            }
            return viewHolder;
        }

        public void setTheme(boolean darkBackground) {
            this.darkBackground = darkBackground;
        }
    }

    private void releaseAudioFocus() {
        AudioManager am = (AudioManager) messageAdapter.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.abandonAudioFocus(this);
        }
    }

    private void requestAudioFocus() {
        AudioManager am = (AudioManager) messageAdapter.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }
}