package logic;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Procedural retro sound synthesizer for arcade audio effects.
 * Generates audio waveforms entirely in memory using javax.sound.sampled.
 */
public class SoundPlayer {
    private static final float SAMPLE_RATE = 22050f;
    private boolean enabled = true;

    public SoundPlayer(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void playEatApple() {
        if (!enabled) return;
        // Cheery rising pitch blip: 520Hz -> 780Hz (60ms)
        playToneSequence(new float[]{520, 780}, new int[]{30, 40}, WaveType.SINE, 0.4f);
    }

    public void playEatBigApple() {
        if (!enabled) return;
        // Sparkly chime: 600Hz -> 800Hz -> 1200Hz
        playToneSequence(new float[]{600, 800, 1200}, new int[]{40, 40, 80}, WaveType.SINE, 0.5f);
    }

    public void playPowerUp() {
        if (!enabled) return;
        // Power-up fanfare: 440 -> 660 -> 880 -> 1320
        playToneSequence(new float[]{440, 660, 880, 1320}, new int[]{50, 50, 60, 120}, WaveType.SQUARE, 0.35f);
    }

    public void playLevelUp() {
        if (!enabled) return;
        // Triumphant victory arpeggio: C5 -> E5 -> G5 -> C6
        playToneSequence(new float[]{523.25f, 659.25f, 783.99f, 1046.50f}, new int[]{80, 80, 80, 200}, WaveType.TRIANGLE, 0.5f);
    }

    public void playHit() {
        if (!enabled) return;
        // Low crunch / thud: 180Hz -> 60Hz
        playToneSequence(new float[]{180, 120, 70}, new int[]{40, 60, 90}, WaveType.SQUARE, 0.45f);
    }

    public void playGameOver() {
        if (!enabled) return;
        // Sad descending tones: 400Hz -> 300Hz -> 200Hz
        playToneSequence(new float[]{400, 320, 240, 160}, new int[]{100, 100, 120, 250}, WaveType.TRIANGLE, 0.4f);
    }

    public void playClick() {
        if (!enabled) return;
        // Subtle UI click
        playToneSequence(new float[]{900}, new int[]{18}, WaveType.SINE, 0.25f);
    }

    private enum WaveType {
        SINE, SQUARE, TRIANGLE
    }

    private void playToneSequence(float[] freqs, int[] durationsMs, WaveType waveType, float volume) {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();

                for (int t = 0; t < freqs.length; t++) {
                    float freq = freqs[t];
                    int durationMs = durationsMs[t];
                    int numSamples = (int) (SAMPLE_RATE * durationMs / 1000f);
                    byte[] buffer = new byte[numSamples];

                    for (int i = 0; i < numSamples; i++) {
                        double time = i / (double) SAMPLE_RATE;
                        double val = 0;
                        switch (waveType) {
                            case SINE:
                                val = Math.sin(2.0 * Math.PI * freq * time);
                                break;
                            case SQUARE:
                                val = Math.sin(2.0 * Math.PI * freq * time) >= 0 ? 1.0 : -1.0;
                                break;
                            case TRIANGLE:
                                val = 2.0 * Math.abs(2.0 * (time * freq - Math.floor(time * freq + 0.5))) - 1.0;
                                break;
                        }
                        // Smooth envelope fade out to avoid clicks
                        float envelope = (float) (numSamples - i) / numSamples;
                        buffer[i] = (byte) (val * 127.0 * volume * envelope);
                    }
                    line.write(buffer, 0, buffer.length);
                }

                line.drain();
                line.close();
            } catch (Exception ignored) {
                // Audio unavailable or busy - fail silently without impacting game performance
            }
        }).start();
    }
}
