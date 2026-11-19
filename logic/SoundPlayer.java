package logic;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Procedurally synthesised sound effects. Every sound is rendered once into a
 * pre-opened {@link Clip} at startup, and playback runs on one background thread,
 * so playing a sound never blocks the game loop or spawns threads.
 */
public final class SoundPlayer {
    public enum Sfx { EAT, BIG, POWER, LEVEL, HIT, SHIELD, OVER, WIN, CLICK, NAV, TICK, GO, BEST }

    private static final float RATE = 44100f;
    private static final int EAT_VARIANTS = 6;

    private enum Wave { SINE, CHIME, SQUARE, TRIANGLE, NOISE }

    private final Map<Sfx, Clip> clips = new EnumMap<>(Sfx.class);
    private final Clip[] eatClips = new Clip[EAT_VARIANTS];
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sound");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled;
    private volatile boolean ready;

    public SoundPlayer(boolean enabled) {
        this.enabled = enabled;
        exec.execute(this::init);
    }

    public void setEnabled(boolean on) { enabled = on; }

    public void play(Sfx sfx) {
        if (enabled && ready) start(clips.get(sfx));
    }

    /** Eat sound whose pitch climbs with the combo counter. */
    public void playEat(int combo) {
        if (enabled && ready) start(eatClips[Math.max(0, Math.min(EAT_VARIANTS - 1, combo - 1))]);
    }

    private void start(Clip clip) {
        if (clip == null) return;
        exec.execute(() -> {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        });
    }

    private void init() {
        try {
            for (int i = 0; i < EAT_VARIANTS; i++) {
                float k = (float) Math.pow(2, i * 2 / 12.0); // rise a whole tone per combo step
                eatClips[i] = clip(render(new float[] {587 * k, 880 * k}, new int[] {35, 70}, Wave.CHIME, 0.45f));
            }
            put(Sfx.BIG, new float[] {660, 880, 1175, 1568}, new int[] {45, 45, 45, 160}, Wave.CHIME, 0.5f);
            put(Sfx.POWER, new float[] {392, 523, 659, 784, 1047}, new int[] {40, 40, 40, 40, 140}, Wave.SQUARE, 0.22f);
            put(Sfx.LEVEL, new float[] {523, 659, 784, 1047, 1319}, new int[] {90, 90, 90, 90, 260}, Wave.CHIME, 0.5f);
            put(Sfx.HIT, new float[] {160, 110, 70}, new int[] {50, 70, 120}, Wave.NOISE, 0.5f);
            put(Sfx.SHIELD, new float[] {1400, 900, 600}, new int[] {40, 50, 90}, Wave.TRIANGLE, 0.45f);
            put(Sfx.OVER, new float[] {440, 370, 311, 220}, new int[] {130, 130, 130, 380}, Wave.TRIANGLE, 0.5f);
            put(Sfx.WIN, new float[] {523, 659, 784, 1047, 784, 1047}, new int[] {90, 90, 90, 140, 90, 360}, Wave.CHIME, 0.5f);
            put(Sfx.CLICK, new float[] {1200}, new int[] {22}, Wave.SINE, 0.3f);
            put(Sfx.NAV, new float[] {800}, new int[] {14}, Wave.SINE, 0.18f);
            put(Sfx.TICK, new float[] {880}, new int[] {70}, Wave.CHIME, 0.35f);
            put(Sfx.GO, new float[] {1320}, new int[] {200}, Wave.CHIME, 0.45f);
            put(Sfx.BEST, new float[] {784, 988, 1175, 1568}, new int[] {70, 70, 70, 240}, Wave.CHIME, 0.45f);
            ready = true;
        } catch (Exception | LinkageError e) {
            // No audio device: stay silent.
        }
    }

    private void put(Sfx sfx, float[] freqs, int[] ms, Wave wave, float vol) throws Exception {
        clips.put(sfx, clip(render(freqs, ms, wave, vol)));
    }

    private static Clip clip(byte[] pcm) throws Exception {
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        Clip c = AudioSystem.getClip();
        c.open(fmt, pcm, 0, pcm.length);
        return c;
    }

    /** Renders a note sequence to 16-bit little-endian PCM with a soft attack/decay envelope. */
    private static byte[] render(float[] freqs, int[] ms, Wave wave, float vol) {
        int total = 0;
        for (int m : ms) total += (int) (RATE * m / 1000f);
        byte[] out = new byte[total * 2];
        java.util.Random noise = new java.util.Random(7);
        int pos = 0;
        double phase = 0;
        for (int n = 0; n < freqs.length; n++) {
            int count = (int) (RATE * ms[n] / 1000f);
            int attack = Math.min(count / 4, (int) (RATE * 0.004));
            for (int i = 0; i < count; i++) {
                double f = freqs[n];
                phase += f / RATE;
                double p = phase - Math.floor(phase);
                double v = switch (wave) {
                    case SINE -> Math.sin(2 * Math.PI * p);
                    case CHIME -> Math.sin(2 * Math.PI * p) * 0.75 + Math.sin(4 * Math.PI * p) * 0.25;
                    case SQUARE -> p < 0.5 ? 0.6 : -0.6;
                    case TRIANGLE -> 1 - 4 * Math.abs(p - 0.5);
                    case NOISE -> (noise.nextDouble() * 2 - 1) * 0.6 + Math.sin(2 * Math.PI * p) * 0.4;
                };
                double env = i < attack ? i / (double) attack : Math.exp(-3.2 * (i - attack) / (double) count);
                if (i > count - 64) env *= (count - i) / 64.0; // de-click tail
                int s = (int) (v * env * vol * 32767);
                out[pos++] = (byte) s;
                out[pos++] = (byte) (s >> 8);
            }
        }
        return out;
    }
}
