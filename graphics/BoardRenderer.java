package graphics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import logic.PowerUpType;
import logic.SnakeEngine;

/**
 * Draws the playfield. The background, board and walls are rendered once into a
 * cached image at the current screen resolution and only rebuilt when the level,
 * theme or window scale changes; per frame we only draw the moving parts.
 */
public final class BoardRenderer {
    public static final int W = 1320, H = 736;           // logical canvas size
    public static final int CELL = 40;
    public static final int BX = 20, BY = 76;            // board origin
    public static final int BW = SnakeEngine.COLS * CELL, BH = SnakeEngine.ROWS * CELL;
    private static final RoundRectangle2D.Double BOARD_SHAPE = new RoundRectangle2D.Double(BX, BY, BW, BH, 28, 28);

    /** Cached static layers keyed by engine/walls/theme/scale/dim; a few are kept so pause/resume doesn't rebuild. */
    private final java.util.Map<String, BufferedImage> layers = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, BufferedImage> en) { return size() > 3; }
    };

    /** When set, moving parts are blended toward this colour (used behind menus instead of a full-screen dim). */
    private Color veil;
    private double veilAmount;

    private final double[] sx = new double[SnakeEngine.CELLS + 1], sy = new double[SnakeEngine.CELLS + 1];
    private final Path2D.Double path = new Path2D.Double();

    public static double cx(double cellX) { return BX + (cellX + 0.5) * CELL; }
    public static double cy(double cellY) { return BY + (cellY + 0.5) * CELL; }

    // ------------------------------------------------------------------
    // Static layer
    // ------------------------------------------------------------------

    /**
     * Draws background, board and walls from a cached image. {@code dim} (0..1) darkens
     * it toward the theme's background, baked into the cache rather than blended per frame.
     */
    public void drawStatic(Graphics2D g, SnakeEngine e, int themeIndex, double dim) {
        AffineTransform t = g.getTransform();
        double s = t.getScaleX();
        String key = System.identityHashCode(e) + ":" + e.wallsVersion() + ":" + themeIndex + ":" + s + ":" + dim;
        BufferedImage layer = layers.get(key);
        if (layer == null) {
            int w = (int) Math.ceil(W * s), h = (int) Math.ceil(H * s);
            layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D lg = layer.createGraphics();
            Theme.quality(lg);
            lg.scale(s, s);
            Theme.Board b = Theme.board(themeIndex);
            paintStatic(lg, e, b);
            if (dim > 0) {
                lg.setColor(Theme.alpha(b.bgBottom(), dim));
                lg.fillRect(-1, -1, W + 2, H + 2);
            }
            lg.dispose();
            layers.put(key, layer);
        }
        g.setTransform(new AffineTransform(1, 0, 0, 1, Math.round(t.getTranslateX()), Math.round(t.getTranslateY())));
        g.drawImage(layer, 0, 0, null);
        g.setTransform(t);
    }

    public void setVeil(Color c, double amount) {
        veil = amount > 0 ? c : null;
        veilAmount = veil == null ? 0 : amount;
    }

    private Color veiled(Color c) {
        return veil == null ? c : Theme.lerp(c, veil, veilAmount);
    }

    private void paintStatic(Graphics2D g, SnakeEngine e, Theme.Board b) {
        g.setPaint(new GradientPaint(0, 0, b.bgTop(), 0, H, b.bgBottom()));
        g.fillRect(0, 0, W, H);
        Gfx.glow(g, b.frame(), W * 0.15, H * 0.1, 420, 0.25);
        Gfx.glow(g, b.wallEdge(), W * 0.9, H * 0.95, 380, 0.12);

        // board drop shadow
        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 26));
            Gfx.fillRound(g, BX - i * 3, BY - i * 3 + 8, BW + i * 6, BH + i * 6, 14 + i * 3);
        }
        Shape oldClip = g.getClip();
        g.clip(BOARD_SHAPE);
        g.setColor(b.cellA());
        g.fillRect(BX, BY, BW, BH);
        g.setColor(b.cellB());
        for (int y = 0; y < SnakeEngine.ROWS; y++)
            for (int x = (y & 1); x < SnakeEngine.COLS; x += 2)
                g.fillRect(BX + x * CELL, BY + y * CELL, CELL, CELL);
        // vignette
        g.setPaint(new RadialGradientPaint(BX + BW / 2f, BY + BH / 2f, BW * 0.62f,
            new float[] {0.55f, 1f}, new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 90)}));
        g.fillRect(BX, BY, BW, BH);

        // walls: shadow pass, then blocks
        g.setColor(new Color(0, 0, 0, 90));
        for (int y = 0; y < SnakeEngine.ROWS; y++)
            for (int x = 0; x < SnakeEngine.COLS; x++)
                if (e.isWall(x, y)) Gfx.fillRound(g, BX + x * CELL + 3, BY + y * CELL + 6, CELL - 2, CELL - 2, 8);
        for (int y = 0; y < SnakeEngine.ROWS; y++)
            for (int x = 0; x < SnakeEngine.COLS; x++)
                if (e.isWall(x, y)) drawWall(g, b, BX + x * CELL, BY + y * CELL);
        g.setClip(oldClip);

        g.setColor(b.frame());
        g.setStroke(Gfx.stroke(1.5f));
        Gfx.drawRound(g, BX + 0.5, BY + 0.5, BW - 1, BH - 1, 14);
    }

    private static void drawWall(Graphics2D g, Theme.Board b, int x, int y) {
        g.setPaint(new GradientPaint(x, y, b.wallTop(), x, y + CELL, b.wallBottom()));
        Gfx.fillRound(g, x + 1.5, y + 1.5, CELL - 3, CELL - 3, 8);
        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(Gfx.stroke(1.5f));
        Gfx.line(g, x + 9, y + 4.5, x + CELL - 9, y + 4.5);
        g.setColor(Theme.alpha(b.wallEdge(), 200));
        Gfx.fillCircle(g, x + CELL / 2.0, y + CELL / 2.0 + 2, 3);
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    public void drawItems(Graphics2D g, SnakeEngine e, double alpha, double t) {
        Composite oldComp = g.getComposite();
        if (veil != null) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - veilAmount)));
        drawItemsInner(g, e, alpha, t);
        g.setComposite(oldComp);
    }

    private void drawItemsInner(Graphics2D g, SnakeEngine e, double alpha, double t) {
        if (e.appleX() >= 0) {
            double ax = cx(e.prevAppleX() + (e.appleX() - e.prevAppleX()) * alpha);
            double ay = cy(e.prevAppleY() + (e.appleY() - e.prevAppleY()) * alpha);
            double spawn = easeOutBack(Math.min(1, e.appleAge() / 320));
            drawApple(g, ax, ay + Math.sin(t * 0.004) * 2.5, CELL * 0.36 * spawn, false, t);
        }
        if (e.bigActive()) {
            double left = e.bigLeftFraction();
            if (left > 0.25 || Math.sin(t * 0.03) > -0.3) {
                double bx = BX + (e.bigX() + 1) * CELL, by = BY + (e.bigY() + 1) * CELL;
                double spawn = easeOutBack(Math.min(1, e.bigAge() / 380));
                double r = CELL * 0.74 * spawn * (1 + 0.04 * Math.sin(t * 0.008));
                drawApple(g, bx, by, r, true, t);
                g.setColor(Theme.alpha(Theme.GOLD, 210));
                g.setStroke(Gfx.stroke(3.5f));
                g.draw(new Arc2D.Double(bx - r - 12, by - r - 12, (r + 12) * 2, (r + 12) * 2, 90, 360 * left, Arc2D.OPEN));
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.002 + i * Math.PI * 2 / 3;
                    g.setColor(new Color(255, 250, 220, 220));
                    Gfx.star(g, bx + Math.cos(a) * (r + 4), by + Math.sin(a) * (r + 4), 9 + 3 * Math.sin(t * 0.01 + i));
                }
            }
        }
        PowerUpType item = e.item();
        if (item != null) {
            double left = e.itemLeftFraction();
            if (left > 0.25 || Math.sin(t * 0.03) > -0.3) {
                double ix = cx(e.itemX()), iy = cy(e.itemY()) + Math.sin(t * 0.005 + 1) * 3;
                double spawn = easeOutBack(Math.min(1, e.itemAge() / 350));
                double r = CELL * 0.42 * spawn;
                Gfx.glow(g, item.color, ix, iy, r * 3.2, 0.55 + 0.2 * Math.sin(t * 0.006));
                g.setColor(new Color(16, 20, 34));
                Gfx.fillCircle(g, ix, iy, r);
                g.setColor(item.color);
                g.setStroke(Gfx.stroke(2.5f));
                Gfx.drawCircle(g, ix, iy, r);
                g.setColor(new Color(255, 255, 255, 200));
                g.setStroke(Gfx.stroke(2f));
                g.draw(new Arc2D.Double(ix - r - 5, iy - r - 5, (r + 5) * 2, (r + 5) * 2, 90, 360 * left, Arc2D.OPEN));
                g.setColor(item.color);
                Gfx.powerIcon(g, item, ix, iy, r * 1.15);
            }
        }
    }

    private void drawApple(Graphics2D g, double x, double y, double r, boolean golden, double t) {
        if (r <= 0.5) return;
        Color base = golden ? new Color(255, 190, 30) : new Color(240, 45, 70);
        Color light = golden ? new Color(255, 245, 170) : new Color(255, 140, 150);
        Color dark = golden ? new Color(190, 100, 0) : new Color(140, 10, 40);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(x - r * 0.85, y + r * 0.75, r * 1.7, r * 0.5));
        Gfx.glow(g, base, x, y, r * 2.8, golden ? 0.7 : 0.45);
        g.setPaint(new RadialGradientPaint((float) (x - r * 0.35), (float) (y - r * 0.4), (float) (r * 1.5),
            new float[] {0f, 0.45f, 1f}, new Color[] {light, base, dark}));
        Gfx.fillCircle(g, x, y, r);
        g.setColor(new Color(110, 70, 30));
        g.setStroke(Gfx.stroke((float) Math.max(2, r * 0.15)));
        Gfx.line(g, x, y - r * 0.8, x + r * 0.12, y - r * 1.25);
        AffineTransform old = g.getTransform();
        g.translate(x + r * 0.38, y - r * 1.02);
        g.rotate(-0.5 + 0.08 * Math.sin(t * 0.003));
        g.setColor(new Color(70, 200, 90));
        g.fill(new Ellipse2D.Double(-r * 0.3, -r * 0.14, r * 0.6, r * 0.28));
        g.setTransform(old);
        g.setColor(new Color(255, 255, 255, 160));
        g.fill(new Ellipse2D.Double(x - r * 0.55, y - r * 0.55, r * 0.38, r * 0.24));
    }

    // ------------------------------------------------------------------
    // Snake
    // ------------------------------------------------------------------

    public void drawSnake(Graphics2D g, SnakeEngine e, Theme.Skin skin, double alpha, double t, boolean dead) {
        int n = e.length();
        if (n == 0) return;
        int[] xs = e.xs(), ys = e.ys(), pxs = e.prevXs(), pys = e.prevYs();
        // Joints sit on their grid cells; only the two ends slide: the head toward its new
        // cell and an extra tail point retracting from the cell just vacated. Interpolating
        // every joint instead would cut each corner diagonally mid-step.
        lerpInto(0, pxs[0], pys[0], xs[0], ys[0], alpha);
        for (int i = 1; i < n; i++) {
            sx[i] = cx(xs[i]);
            sy[i] = cy(ys[i]);
        }
        int m = n;
        if (n >= 2) {
            lerpInto(n, pxs[n - 1], pys[n - 1], xs[n - 1], ys[n - 1], alpha);
            m = n + 1;
        }

        Shape oldClip = g.getClip();
        Composite oldComp = g.getComposite();
        g.clip(BOARD_SHAPE);
        boolean ghost = e.effect() == PowerUpType.GHOST;
        boolean stunned = e.isStunned() && ((int) (t / 80) & 1) == 0;
        if (ghost) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));

        float maxW = CELL * 0.78f;
        // shadow + highlight as single paths (no overlap artefacts, two draw calls total)
        buildPath(m);
        AffineTransform at = g.getTransform();
        g.translate(3, 6);
        g.setColor(new Color(0, 0, 0, (int) (80 * (1 - veilAmount))));
        g.setStroke(Gfx.stroke(maxW));
        g.draw(path);
        g.setTransform(at);

        for (int i = m - 1; i >= 1; i--) {
            int k = Math.min(i, n - 1);
            double w = segWidth(k, n);
            Color c = veiled(segColor(skin, k, n, t, dead));
            if (stunned) c = Theme.lerp(c, Color.WHITE, 0.6);
            g.setColor(c);
            g.setStroke(Gfx.stroke((float) (Math.round(w * 2) / 2.0)));
            double dx = sx[i - 1] - sx[i], dy = sy[i - 1] - sy[i];
            if (Math.abs(dx) > CELL * 1.5) dx -= Math.signum(dx) * BW;
            if (Math.abs(dy) > CELL * 1.5) dy -= Math.signum(dy) * BH;
            Gfx.line(g, sx[i], sy[i], sx[i] + dx, sy[i] + dy);
            if (sx[i] + dx != sx[i - 1] || sy[i] + dy != sy[i - 1])
                Gfx.line(g, sx[i - 1] - dx, sy[i - 1] - dy, sx[i - 1], sy[i - 1]);
        }
        if (n == 1) {
            g.setColor(veiled(segColor(skin, 0, n, t, dead)));
            Gfx.fillCircle(g, sx[0], sy[0], maxW / 2);
        }
        g.translate(-maxW * 0.1, -maxW * 0.16);
        g.setColor(new Color(255, 255, 255, (int) ((dead ? 25 : 60) * (1 - veilAmount))));
        g.setStroke(Gfx.stroke(CELL * 0.16f));
        g.draw(path);
        g.setTransform(at);

        if (veil != null) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - veilAmount)));
        drawHead(g, e, skin, t, dead, stunned);
        g.setComposite(oldComp);
        g.setClip(oldClip);
    }

    /** Sets point i to the interpolated (wrap-aware) position between two cells. */
    private void lerpInto(int i, int x0, int y0, int x1, int y1, double alpha) {
        double fx0 = x0, fy0 = y0;
        if (x1 - x0 > 1) fx0 += SnakeEngine.COLS; else if (x0 - x1 > 1) fx0 -= SnakeEngine.COLS;
        if (y1 - y0 > 1) fy0 += SnakeEngine.ROWS; else if (y0 - y1 > 1) fy0 -= SnakeEngine.ROWS;
        sx[i] = cx(fx0 + (x1 - fx0) * alpha);
        sy[i] = cy(fy0 + (y1 - fy0) * alpha);
    }

    private void buildPath(int n) {
        path.reset();
        path.moveTo(sx[n - 1], sy[n - 1]);
        for (int i = n - 2; i >= 0; i--) {
            if (Math.abs(sx[i] - sx[i + 1]) > CELL * 1.5 || Math.abs(sy[i] - sy[i + 1]) > CELL * 1.5) path.moveTo(sx[i], sy[i]);
            else path.lineTo(sx[i], sy[i]);
        }
        if (n == 1) path.lineTo(sx[0] + 0.01, sy[0]);
    }

    private static double segWidth(int i, int n) {
        // taper over at least 14 segments so short snakes stay plump
        return CELL * (0.78 - 0.3 * (n <= 1 ? 0 : (double) i / Math.max(n - 1, 14)));
    }

    private static Color segColor(Theme.Skin skin, int i, int n, double t, boolean dead) {
        Color c;
        if (skin.rainbow()) {
            c = Color.getHSBColor((float) ((t * 0.00015 + i * 0.03) % 1.0), 0.62f, 1f);
        } else {
            double f = n <= 1 ? 0 : Math.pow((double) i / (n - 1), 0.8);
            c = Theme.lerp(skin.head(), skin.tail(), f);
        }
        if (i % 2 == 1) c = Theme.lerp(c, Color.BLACK, 0.035); // subtle banding
        if (dead) {
            int grey = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            c = Theme.lerp(c, new Color(grey, grey, grey), 0.75);
        }
        return c;
    }

    private void drawHead(Graphics2D g, SnakeEngine e, Theme.Skin skin, double t, boolean dead, boolean stunned) {
        double hx = sx[0], hy = sy[0];
        double r = CELL * 0.46;
        int dx = e.direction().dx, dy = e.direction().dy;
        double px = -dy, py = dx;

        if (e.effect() == PowerUpType.SHIELD) {
            Gfx.glow(g, PowerUpType.SHIELD.color, hx, hy, r * 3, 0.45);
            g.setColor(Theme.alpha(PowerUpType.SHIELD.color, 200));
            g.setStroke(Gfx.stroke(2.5f));
            double rr = r * 1.65;
            for (int i = 0; i < 3; i++) {
                double start = Math.toDegrees(t * 0.003) + i * 120;
                g.draw(new Arc2D.Double(hx - rr, hy - rr, rr * 2, rr * 2, start, 80, Arc2D.OPEN));
            }
        }
        if (e.effect() == PowerUpType.MAGNET) {
            double p = (t % 900) / 900;
            g.setColor(Theme.alpha(PowerUpType.MAGNET.color, (1 - p) * 0.6));
            g.setStroke(Gfx.stroke(2f));
            Gfx.drawCircle(g, hx, hy, r + p * CELL * 2.2);
        }

        Color hc = segColor(skin, 0, Math.max(2, e.length()), t, dead);
        if (stunned) hc = Theme.lerp(hc, Color.WHITE, 0.6);
        g.setPaint(new RadialGradientPaint((float) (hx - r * 0.3), (float) (hy - r * 0.35), (float) (r * 1.6),
            new float[] {0f, 1f}, new Color[] {Theme.lerp(hc, Color.WHITE, 0.35), Theme.lerp(hc, Color.BLACK, 0.2)}));
        Gfx.fillCircle(g, hx + dx * 2, hy + dy * 2, r);

        // tongue flick
        if (!dead && (t % 2600) < 240) {
            double tx = hx + dx * r * 1.0, ty = hy + dy * r * 1.0;
            double ex = hx + dx * r * 1.75, ey = hy + dy * r * 1.75;
            g.setColor(new Color(255, 60, 90));
            g.setStroke(Gfx.stroke(2.6f));
            Gfx.line(g, tx, ty, ex, ey);
            Gfx.line(g, ex, ey, ex + dx * 5 + px * 4, ey + dy * 5 + py * 4);
            Gfx.line(g, ex, ey, ex + dx * 5 - px * 4, ey + dy * 5 - py * 4);
        }

        // eyes
        for (int side = -1; side <= 1; side += 2) {
            double ex = hx + dx * r * 0.28 + px * r * 0.46 * side;
            double ey = hy + dy * r * 0.28 + py * r * 0.46 * side;
            if (dead) {
                g.setColor(new Color(30, 30, 40));
                g.setStroke(Gfx.stroke(2.4f));
                double k = r * 0.2;
                Gfx.line(g, ex - k, ey - k, ex + k, ey + k);
                Gfx.line(g, ex - k, ey + k, ex + k, ey - k);
            } else if ((t % 4200) < 130) {
                g.setColor(new Color(20, 30, 30));
                g.setStroke(Gfx.stroke(2.4f));
                Gfx.line(g, ex - px * r * 0.22, ey - py * r * 0.22, ex + px * r * 0.22, ey + py * r * 0.22);
            } else {
                g.setColor(Color.WHITE);
                Gfx.fillCircle(g, ex, ey, r * 0.3);
                g.setColor(new Color(15, 20, 30));
                Gfx.fillCircle(g, ex + dx * r * 0.1, ey + dy * r * 0.1, r * 0.17);
                g.setColor(new Color(255, 255, 255, 220));
                Gfx.fillCircle(g, ex + dx * r * 0.04 - r * 0.07, ey + dy * r * 0.04 - r * 0.07, r * 0.06);
            }
        }
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }
}
