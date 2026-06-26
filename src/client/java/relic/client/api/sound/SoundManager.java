package relic.client.api.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public final class SoundManager {

    private SoundManager() {}

    public static void play(String resourcePath) {
        play(resourcePath, 1.0f);
    }

    public static void play(String resourcePath, float volume) {
        Thread thread = new Thread(() -> playBlocking(resourcePath, volume), "Relic-Sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playBlocking(String resourcePath, float volume) {
        try (InputStream raw = SoundManager.class.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                System.err.println("[Relic Client] Sound not found on classpath: " + resourcePath);
                return;
            }

            try (AudioInputStream source = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                AudioInputStream ais = toPlayable(source);
                Clip clip = AudioSystem.getClip();
                CountDownLatch finished = new CountDownLatch(1);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        finished.countDown();
                    }
                });
                clip.open(ais);
                applyGain(clip, volume);
                clip.start();

                finished.await();
                clip.close();
            }
        } catch (Exception e) {
            System.err.println("[Relic Client] Failed to play sound " + resourcePath + ": " + e.getMessage());
        }
    }

    private static AudioInputStream toPlayable(AudioInputStream in) {
        AudioFormat src = in.getFormat();
        boolean pcm = src.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                || src.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED;
        if (pcm && src.getSampleSizeInBits() == 16) {
            return in;
        }
        int channels = src.getChannels();
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(),
                16,
                channels,
                channels * 2,
                src.getSampleRate(),
                false);
        return AudioSystem.getAudioInputStream(target, in);
    }

    private static void applyGain(Clip clip, float volume) {
        if (volume >= 1.0f || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float clamped = Math.max(0.0001f, Math.min(1.0f, volume));

        float db = (float) (Math.log10(clamped) * 20.0);
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }
}
