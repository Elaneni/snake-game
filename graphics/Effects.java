package graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Transient visual feedback: particle bursts, floating score popups, stacked toast
 * notifications, screen shake and colour flashes. Particles live in flat arrays
 * (no per-particle objects) so bursts cost no garbage.
 */
public final class Effects {
    private static final int MAX = 800;
    private final float[] px = new float[MAX], py = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX], size = new float[MAX];
    private final Color[] color = new Color[MAX];
    private int count;

    private static final class Popup {
        String text; Color color; Font font; double x, y, age;
    }

    private static final class Toast {
        String text; Color color; double age; final double ttl = 2600;
    }

    private final List<Popup> popups = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();
    private final Random rng = new Random();

    private double trauma, shakeX, shakeY, shakeTime;
    private Color flashColor = Color.WHITE;
    private double flash;

    public void clear() {
        count = 0;
        popups.clear();
        toasts.clear();
        trauma = flash = 0;
    }

    public void burst(double x, double y, Color c, int n, double speed, double sz) {
        for (int i = 0; i < n && count < MAX; i++, count++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double v = speed * (0.35 + rng.nextDouble() * 0.65);
            px[count] = (float) x;
            py[count] = (float) y;
            vx[count] = (float) (Math.cos(a) * v);
            vy[count] = (float) (Math.sin(a) * v);
            maxLife[count] = life[count] = (float) (450 + rng.nextDouble() * 450);
            size[count] = (float) (sz * (0.6 + rng.nextDouble() * 0.7));
            color[count] = c;
        }
    }

    /** A ring of particles, used for level-ups and shield breaks. */
    public void ring(double x, double y, Color c, int n, double speed, double sz) {
        for (int i = 0; i < n && count < MAX; i++, count++) {
            double a = i * Math.PI * 2 / n;
            px[count] = (float) x;
            py[count] = (float) y;
            vx[count] = (float) (Math.cos(a) * speed);
            vy[count] = (float) (Math.sin(a) * speed);
            maxLife[count] = life[count] = 700;
            size[count] = (float) sz;
            color[count] = c;
        }
    }

    public void popup(String text, double x, double y, Color c, Font f) {
        Popup p = new Popup();
        p.text = text;
        p.x = x;
        p.y = y;
        p.color = c;
        p.font = f;
        popups.add(p);
    }

    public void toast(String text, Color c) {
        if (toasts.size() >= 3) toasts.remove(0);
        Toast t = new Toast();
        t.text = text;
        t.color = c;
        toasts.add(t);
    }

    public void shake(double amount) { trauma = Math.min(1, trauma + amount); }

    public void flash(Color c, double strength) {
        flashColor = c;
        flash = Math.max(flash, strength);
    }

    public void update(double dt) {
        double drag = Math.pow(0.9, dt / 16.0);
        for (int i = 0; i < count; i++) {
            life[i] -= (float) dt;
            if (life[i] <= 0) { // swap-remove
                count--;
                px[i] = px[count]; py[i] = py[count]; vx[i] = vx[count]; vy[i] = vy[count];
                life[i] = life[count]; maxLife[i] = maxLife[count]; size[i] = size[count]; color[i] = color[count];
                i--;
                continue;
            }
            px[i] += (float) (vx[i] * dt / 16);
            py[i] += (float) (vy[i] * dt / 16);
            vx[i] = (float) (vx[i] * drag);
            vy[i] = (float) (vy[i] * drag + 0.02 * dt / 16);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            p.age += dt;
            p.y -= dt * 0.045;
            if (p.age > 900) popups.remove(i);
        }
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            if ((t.age += dt) > t.ttl) toasts.remove(i);
        }
        trauma = Math.max(0, trauma - dt / 600);
        shakeTime += dt;
        double mag = trauma * trauma * 14;
        shakeX = mag * Math.sin(shakeTime * 0.091) * Math.cos(shakeTime * 0.037);
        shakeY = mag * Math.cos(shakeTime * 0.083) * Math.sin(shakeTime * 0.051);
        flash = Math.max(0, flash - dt / 350);
    }

    public double shakeX() { return shakeX; }
    public double shakeY() { return shakeY; }

    /** Particles and popups, drawn in board space. */
    public void drawWorld(Graphics2D g) {
        for (int i = 0; i < count; i++) {
            double t = life[i] / maxLife[i];
            Gfx.glow(g, color[i], px[i], py[i], size[i] * 2.4, t * 0.9);
            g.setColor(Theme.alpha(Theme.lerp(color[i], Color.WHITE, 0.5), t));
            Gfx.fillCircle(g, px[i], py[i], size[i] * 0.45 * (0.5 + t * 0.5));
        }
        for (Popup p : popups) {
            double t = p.age / 900;
            double scale = p.age < 120 ? 1.5 - 0.5 * (p.age / 120) : 1;
            double a = t < 0.6 ? 1 : 1 - (t - 0.6) / 0.4;
            Font f = p.font.deriveFont((float) (p.font.getSize2D() * scale));
            g.setFont(f);
            g.setColor(new Color(0, 0, 0, (int) (120 * a)));
            Gfx.center(g, p.text, p.x + 2, p.y + 2);
            g.setColor(Theme.alpha(p.color, a));
            Gfx.center(g, p.text, p.x, p.y);
        }
    }

    /** Toast stack centred at cx, starting at y, plus the full-screen flash. */
    public void drawOverlay(Graphics2D g, double cx, double y, int w, int h) {
        if (flash > 0) {
            Gfx.fillRectFast(g, Theme.alpha(flashColor, flash * 0.35), -50, -50, w + 100, h + 100);
        }
        g.setFont(Theme.H3);
        for (int i = 0; i < toasts.size(); i++) {
            Toast t = toasts.get(i);
            double in = Math.min(1, t.age / 220);
            double out = Math.min(1, (t.ttl - t.age) / 300);
            double a = Math.min(in, out);
            double ease = 1 - Math.pow(1 - in, 3);
            int tw = g.getFontMetrics().stringWidth(t.text) + 56;
            double ty = y + i * 52 - (1 - ease) * 18;
            g.setColor(new Color(12, 16, 26, (int) (230 * a)));
            Gfx.fillRound(g, cx - tw / 2.0, ty, tw, 42, 21);
            g.setColor(Theme.alpha(t.color, a * 0.8));
            g.setStroke(Gfx.stroke(1.5f));
            Gfx.drawRound(g, cx - tw / 2.0 + 0.5, ty + 0.5, tw - 1, 41, 20.5);
            g.setColor(Theme.alpha(t.color, a));
            Gfx.fillCircle(g, cx - tw / 2.0 + 22, ty + 21, 4);
            g.setColor(Theme.alpha(Theme.TEXT, a));
            g.drawString(t.text, (float) (cx - tw / 2.0 + 36), (float) (ty + 27));
        }
    }
}
