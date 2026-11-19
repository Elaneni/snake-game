package graphics;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny immediate-mode UI drawn straight onto the game canvas: no Swing components,
 * no dialogs. Widgets are declared every frame; each call draws itself and reports
 * whether it was activated. Mouse and keyboard (arrows + Enter) both work: every
 * focusable widget gets an index and the arrow keys move focus between them.
 */
public final class Ui {
    public interface ChipPainter {
        void paint(Graphics2D g, int index, double x, double y, double w, double h, boolean selected);
    }

    // Input for the current frame (set by the game loop).
    public double mx = -1e4, my = -1e4, dt = 16;
    public boolean clicked, moved, up, down, left, right, enter;

    private int focus, count, lastCount;
    private final Map<Integer, Double> anim = new HashMap<>();
    private Runnable navSound = () -> {};

    public void setNavSound(Runnable r) { navSound = r; }

    public void begin() {
        lastCount = count;
        count = 0;
        if (lastCount > 0) {
            if (up) { focus = Math.floorMod(focus - 1, lastCount); navSound.run(); }
            if (down) { focus = Math.floorMod(focus + 1, lastCount); navSound.run(); }
        }
    }

    public void end() {
        clicked = moved = up = down = left = right = enter = false;
    }

    /** Call on every screen change so focus starts at the top. */
    public void reset(int focusIndex) {
        focus = focusIndex;
        count = lastCount = 0;
        anim.clear();
    }

    private boolean hit(double x, double y, double w, double h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private double animate(int id, boolean on) {
        double v = anim.getOrDefault(id, 0.0);
        double k = Math.min(1, dt / 90);
        v += ((on ? 1 : 0) - v) * k;
        anim.put(id, v);
        return v;
    }

    /** A focusable button. Returns true on the frame it is activated. */
    public boolean button(Graphics2D g, String label, double x, double y, double w, double h, Color accent, boolean primary) {
        int id = count++;
        boolean hover = hit(x, y, w, h);
        if (hover && moved && focus != id) focus = id;
        boolean focused = focus == id;
        double a = animate(id, focused);
        double lift = a * 2;
        y -= lift;

        if (primary) {
            g.setPaint(new GradientPaint((float) x, (float) y, Theme.lerp(accent, Color.WHITE, 0.15 + 0.15 * a),
                (float) x, (float) (y + h), Theme.lerp(accent, Color.BLACK, 0.12)));
            Gfx.fillRound(g, x, y, w, h, h / 2);
            if (a > 0.01) {
                g.setColor(Theme.alpha(accent, a * 0.45));
                g.setStroke(Gfx.stroke(5f));
                Gfx.drawRound(g, x - 3, y - 3, w + 6, h + 6, h / 2 + 3);
            }
            g.setColor(new Color(10, 14, 22));
        } else {
            g.setColor(Theme.lerp(Theme.CHIP, Theme.alpha(accent, 46), a));
            Gfx.fillRound(g, x, y, w, h, h / 2);
            g.setColor(Theme.lerp(new Color(255, 255, 255, 34), Theme.alpha(accent, 220), a));
            g.setStroke(Gfx.stroke(1.4f));
            Gfx.drawRound(g, x + 0.5, y + 0.5, w - 1, h - 1, h / 2);
            g.setColor(Theme.lerp(Theme.TEXT, Color.WHITE, a));
        }
        g.setFont(Theme.BUTTON);
        Gfx.centerIn(g, label, x, y, w, h);
        return (hover && clicked) || (focused && enter);
    }

    /** Mouse-only round icon button (HUD). */
    public boolean iconButton(Graphics2D g, double x, double y, double s, Color accent, java.util.function.Consumer<Graphics2D> icon) {
        boolean hover = hit(x, y, s, s);
        double a = animate(10_000 + (int) x, hover);
        g.setColor(Theme.lerp(Theme.CHIP, Theme.alpha(accent, 50), a));
        Gfx.fillCircle(g, x + s / 2, y + s / 2, s / 2);
        g.setColor(Theme.lerp(new Color(255, 255, 255, 40), Theme.alpha(accent, 220), a));
        g.setStroke(Gfx.stroke(1.3f));
        Gfx.drawCircle(g, x + s / 2, y + s / 2, s / 2 - 0.5);
        g.setColor(Theme.lerp(Theme.TEXT_DIM, Color.WHITE, a));
        icon.accept(g);
        return hover && clicked;
    }

    /**
     * A labelled row of mutually exclusive chips. Left/Right change it when focused.
     * Returns the (possibly new) selected index.
     */
    public int options(Graphics2D g, String label, String[] opts, int sel, double x, double y, double w, double h,
                       Color accent, ChipPainter painter) {
        int id = count++;
        boolean rowHover = hit(x, y, w, h);
        if (rowHover && moved && focus != id) focus = id;
        boolean focused = focus == id;
        double a = animate(id, focused);
        int n = opts.length;
        if (focused && left) { sel = Math.floorMod(sel - 1, n); navSound.run(); }
        if (focused && right) { sel = Math.floorMod(sel + 1, n); navSound.run(); }

        if (a > 0.01) {
            g.setColor(new Color(255, 255, 255, (int) (12 * a)));
            Gfx.fillRound(g, x, y, w, h, 12);
            g.setColor(Theme.alpha(accent, a));
            Gfx.fillRound(g, x, y + 10, 3, h - 20, 1.5);
        }
        g.setFont(Theme.BODY_BOLD);
        g.setColor(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, a));
        g.drawString(label, (float) (x + 18), (float) (y + h / 2 + 5));

        double labelW = 190, gap = 6;
        double cx0 = x + labelW, cw = (w - labelW - 10 - gap * (n - 1)) / n, ch = h - 14, cy0 = y + 7;
        for (int i = 0; i < n; i++) {
            double chx = cx0 + i * (cw + gap);
            boolean hover = hit(chx, cy0, cw, ch);
            if (hover && clicked && sel != i) sel = i;
            boolean selected = i == sel;
            if (selected) {
                g.setColor(Theme.alpha(accent, 225));
            } else {
                g.setColor(hover ? Theme.CHIP_HOVER : Theme.CHIP);
            }
            Gfx.fillRound(g, chx, cy0, cw, ch, 9);
            if (painter != null) {
                painter.paint(g, i, chx, cy0, cw, ch, selected);
            } else {
                g.setFont(Theme.SMALL.deriveFont(13f));
                g.setColor(selected ? new Color(10, 14, 22) : Theme.TEXT_DIM);
                Gfx.centerIn(g, opts[i], chx, cy0, cw, ch);
            }
        }
        return sel;
    }
}
