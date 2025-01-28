package eu.siacs.conversations.utils;

import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy;

public final class TranscoderStrategies {

    public static DefaultVideoStrategy VIDEO(final long bitrate, final int resolution) {
        return DefaultVideoStrategy.atMost(resolution)
                .bitRate(bitrate)
                .frameRate(30)
                .keyFrameInterval(3F)
                .build();
    }
    // see suggested bit rates on https://www.videoproc.com/media-converter/bitrate-setting-for-h264.htm

    public static final DefaultAudioStrategy AUDIO_HQ = DefaultAudioStrategy.builder()
            .bitRate(192 * 1000)
            .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
            .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
            .build();

    public static final DefaultAudioStrategy AUDIO_MQ = DefaultAudioStrategy.builder()
            .bitRate(128 * 1000)
            .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
            .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
            .build();

    public static final DefaultAudioStrategy AUDIO_LQ = DefaultAudioStrategy.builder()
            .bitRate(96 * 1000)
            .channels(1)
            .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
            .build();

    private TranscoderStrategies() {
        throw new IllegalStateException("Do not instantiate me");
    }
}
