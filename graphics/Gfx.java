package graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import logic.PowerUpType;

/** Shared drawing helpers: cached glow sprites and strokes, panels, text, and vector icons. */
public final class Gfx {
    private Gfx() {}

    private static final AffineTransform IDENTITY = new AffineTransform();
    private static final Map<Long, BufferedImage> GLOWS = lru(160);
    private static final Map<String, BufferedImage> PANELS = lru(12);
    private static final Map<String, BufferedImage> TEXTS = lru(8);
    private static final Map<Float, BasicStroke> STROKES = new HashMap<>();
    private static final RoundRectangle2D.Double RR = new RoundRectangle2D.Double();
    private static final Ellipse2D.Double OVAL = new Ellipse2D.Double();
    private static final Line2D.Double LINE = new Line2D.Double();

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<>(32, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > max; }
        };
    }

    /** Round-capped stroke, cached by width. */
    public static BasicStroke stroke(float w) {
        return STROKES.computeIfAbsent(w, k -> new BasicStroke(k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    /**
     * Soft radial glow. Sprites are rendered once per colour and on-screen size
     * (quantised) at device resolution, then blitted without scaling: scaled image
     * draws are slow on Java2D's software pipeline, plain blits are cheap.
     */
    public static void glow(Graphics2D g, Color c, double cx, double cy, double radius, double alpha) {
        if (alpha <= 0.01 || radius <= 0) return;
        AffineTransform t = g.getTransform();
        if (t.getShearX() != 0 || t.getShearY() != 0) return;
        double s = t.getScaleX();
        double dr = radius * s;
        int q = dr < 40 ? Math.max(2, (int) Math.ceil(dr / 2) * 2) : (int) Math.ceil(dr / 8) * 8;
        long key = ((long) (c.getRGB() & 0xFFFFFF) << 32) | q;
        BufferedImage img = GLOWS.get(key);
        if (img == null) {
            img = new BufferedImage(q * 2, q * 2, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D bg = img.createGraphics();
            Color base = new Color(c.getRGB() & 0xFFFFFF);
            bg.setPaint(new RadialGradientPaint(q, q, q,
                new float[] {0f, 0.25f, 0.6f, 1f},
                new Color[] {Theme.alpha(base, 255), Theme.alpha(base, 140), Theme.alpha(base, 38), Theme.alpha(base, 0)}));
            bg.fillRect(0, 0, q * 2, q * 2);
            bg.dispose();
            GLOWS.put(key, img);
        }
        Composite old = g.getComposite();
        if (alpha < 0.99) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, alpha)));
        blitDevice(g, img, t.getTranslateX() + cx * s - q, t.getTranslateY() + cy * s - q);
        g.setComposite(old);
    }

    /** Draws an image already rendered at device resolution, at a device-space position. */
    private static void blitDevice(Graphics2D g, BufferedImage img, double dx, double dy) {
        AffineTransform t = g.getTransform();
        g.setTransform(IDENTITY);
        g.drawImage(img, (int) Math.round(dx), (int) Math.round(dy), null);
        g.setTransform(t);
    }

    /**
     * Large axis-aligned fill. Done as an integer device-space rectangle so Java2D
     * takes its fast rectangle loop instead of the antialiased/parallelogram path.
     */
    public static void fillRectFast(Graphics2D g, Color c, double x, double y, double w, double h) {
        AffineTransform t = g.getTransform();
        double s = t.getScaleX();
        int x0 = (int) Math.floor(t.getTranslateX() + x * s), y0 = (int) Math.floor(t.getTranslateY() + y * s);
        int x1 = (int) Math.ceil(t.getTranslateX() + (x + w) * s), y1 = (int) Math.ceil(t.getTranslateY() + (y + h) * s);
        g.setTransform(IDENTITY);
        g.setColor(c);
        g.fillRect(x0, y0, x1 - x0, y1 - y0);
        g.setTransform(t);
    }

    /** Text rendered once into an image (with any paint) and then blitted; for big static titles. */
    public static void cachedText(Graphics2D g, String text, Font font, java.awt.Paint paint, double cx, double baseline) {
        AffineTransform t = g.getTransform();
        double s = t.getScaleX();
        String key = text + "|" + font + "|" + paint + "|" + s;
        BufferedImage img = TEXTS.get(key);
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        java.awt.geom.Rectangle2D b = font.getStringBounds(text, frc);
        java.awt.font.LineMetrics lm = font.getLineMetrics(text, frc);
        if (img == null) {
            img = new BufferedImage((int) Math.ceil(b.getWidth() * s) + 4, (int) Math.ceil(b.getHeight() * s) + 4, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D tg = img.createGraphics();
            Theme.quality(tg);
            tg.scale(s, s);
            tg.setFont(font);
            tg.setPaint(paint);
            tg.drawString(text, 0, lm.getAscent());
            tg.dispose();
            TEXTS.put(key, img);
        }
        blitDevice(g, img, t.getTranslateX() + (cx - b.getWidth() / 2) * s, t.getTranslateY() + (baseline - lm.getAscent()) * s);
    }

    public static void fillCircle(Graphics2D g, double cx, double cy, double r) {
        OVAL.setFrame(cx - r, cy - r, r * 2, r * 2);
        g.fill(OVAL);
    }

    public static void drawCircle(Graphics2D g, double cx, double cy, double r) {
        OVAL.setFrame(cx - r, cy - r, r * 2, r * 2);
        g.draw(OVAL);
    }

    public static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        LINE.setLine(x1, y1, x2, y2);
        g.draw(LINE);
    }

    public static void fillRound(Graphics2D g, double x, double y, double w, double h, double r) {
        RR.setRoundRect(x, y, w, h, r * 2, r * 2);
        g.fill(RR);
    }

    public static void drawRound(Graphics2D g, double x, double y, double w, double h, double r) {
        RR.setRoundRect(x, y, w, h, r * 2, r * 2);
        g.draw(RR);
    }

    /** Glass card with a soft drop shadow and hairline border; cached as a device-resolution image. */
    public static void panel(Graphics2D g, double x, double y, double w, double h, double r, Color fill, Color border) {
        AffineTransform t = g.getTransform();
        double s = t.getScaleX();
        int pad = 18;
        String key = w + "x" + h + ":" + r + ":" + fill.getRGB() + ":" + (border == null ? 0 : border.getRGB()) + ":" + s;
        BufferedImage img = PANELS.get(key);
        if (img == null) {
            img = new BufferedImage((int) Math.ceil((w + pad * 2) * s), (int) Math.ceil((h + pad * 2) * s), BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D pg = img.createGraphics();
            Theme.quality(pg);
            pg.scale(s, s);
            pg.translate(pad, pad);
            paintPanel(pg, w, h, r, fill, border);
            pg.dispose();
            PANELS.put(key, img);
        }
        blitDevice(g, img, t.getTranslateX() + (x - pad) * s, t.getTranslateY() + (y - pad) * s);
    }

    private static void paintPanel(Graphics2D g, double w, double h, double r, Color fill, Color border) {
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 22));
            fillRound(g, -i * 3, -i * 3 + 6, w + i * 6, h + i * 6, r + i * 3);
        }
        g.setColor(fill);
        fillRound(g, 0, 0, w, h, r);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 12), 0, 90, new Color(255, 255, 255, 0)));
        fillRound(g, 0, 0, w, h, r);
        if (border != null) {
            g.setColor(border);
            g.setStroke(stroke(1.2f));
            drawRound(g, 0.5, 0.5, w - 1, h - 1, r);
        }
    }

    public static int width(Graphics2D g, Font f, String s) {
        return g.getFontMetrics(f).stringWidth(s);
    }

    /** Draws text horizontally centred on cx with its baseline at y. */
    public static void center(Graphics2D g, String s, double cx, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) y);
    }

    /** Draws text centred both ways inside a box. */
    public static void centerIn(Graphics2D g, String s, double x, double y, double w, double h) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (x + (w - fm.stringWidth(s)) / 2), (float) (y + (h - fm.getHeight()) / 2 + fm.getAscent()));
    }

    public static void right(Graphics2D g, String s, double rx, double y) {
        g.drawString(s, (float) (rx - g.getFontMetrics().stringWidth(s)), (float) y);
    }

    /** Small rounded key-cap, used in hints ("SPACE", "ESC" ...). Returns its width. */
    public static double keycap(Graphics2D g, String label, double x, double y) {
        g.setFont(Theme.SMALL);
        double w = Math.max(26, g.getFontMetrics().stringWidth(label) + 16);
        g.setColor(new Color(255, 255, 255, 22));
        fillRound(g, x, y, w, 24, 6);
        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(stroke(1f));
        drawRound(g, x + 0.5, y + 0.5, w - 1, 23, 6);
        g.setColor(Theme.TEXT);
        centerIn(g, label, x, y, w, 24);
        return w;
    }

    // ------------------------------------------------------------------
    // Vector icons, all centred on (cx, cy) and scaled to size s.
    // ------------------------------------------------------------------

    public static void heart(Graphics2D g, double cx, double cy, double s) {
        Path2D.Double p = new Path2D.Double();
        double w = s / 2, top = cy - s * 0.28;
        p.moveTo(cx, cy + s * 0.42);
        p.curveTo(cx - w * 1.1, cy - s * 0.05, cx - w * 0.9, top - s * 0.35, cx, top + s * 0.05);
        p.curveTo(cx + w * 0.9, top - s * 0.35, cx + w * 1.1, cy - s * 0.05, cx, cy + s * 0.42);
        g.fill(p);
    }

    public static void star(Graphics2D g, double cx, double cy, double s) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? s / 2 : s / 4.6;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            if (i == 0) p.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
            else p.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
        p.closePath();
        g.fill(p);
    }

    public static void shield(Graphics2D g, double cx, double cy, double s) {
        Path2D.Double p = new Path2D.Double();
        double w = s * 0.42, top = cy - s * 0.45;
        p.moveTo(cx, top);
        p.lineTo(cx + w, top + s * 0.14);
        p.curveTo(cx + w, cy + s * 0.15, cx + w * 0.6, cy + s * 0.35, cx, cy + s * 0.5);
        p.curveTo(cx - w * 0.6, cy + s * 0.35, cx - w, cy + s * 0.15, cx - w, top + s * 0.14);
        p.closePath();
        g.fill(p);
    }

    public static void snowflake(Graphics2D g, double cx, double cy, double s) {
        g.setStroke(stroke((float) Math.max(1.5, s * 0.09)));
        double r = s * 0.45;
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI / 3;
            double dx = Math.cos(a) * r, dy = Math.sin(a) * r;
            line(g, cx - dx, cy - dy, cx + dx, cy + dy);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double bx = cx + sgn * dx * 0.6, by = cy + sgn * dy * 0.6;
                double ba = a + (sgn > 0 ? 0 : Math.PI);
                line(g, bx, by, bx + Math.cos(ba + 0.8) * r * 0.32, by + Math.sin(ba + 0.8) * r * 0.32);
                line(g, bx, by, bx + Math.cos(ba - 0.8) * r * 0.32, by + Math.sin(ba - 0.8) * r * 0.32);
            }
        }
    }

    public static void ghost(Graphics2D g, double cx, double cy, double s) {
        Path2D.Double p = new Path2D.Double();
        double w = s * 0.38, top = cy - s * 0.45, bottom = cy + s * 0.42;
        p.moveTo(cx - w, bottom);
        p.lineTo(cx - w, cy - s * 0.08);
        p.curveTo(cx - w, top - s * 0.05, cx + w, top - s * 0.05, cx + w, cy - s * 0.08);
        p.lineTo(cx + w, bottom);
        double step = w * 2 / 3;
        for (int i = 0; i < 3; i++) {
            double x0 = cx + w - i * step;
            p.quadTo(x0 - step / 2, bottom - s * 0.2, x0 - step, bottom);
        }
        p.closePath();
        Color c = g.getColor();
        g.fill(p);
        g.setColor(new Color(30, 20, 50));
        fillCircle(g, cx - w * 0.4, cy - s * 0.08, s * 0.08);
        fillCircle(g, cx + w * 0.4, cy - s * 0.08, s * 0.08);
        g.setColor(c);
    }

    public static void magnet(Graphics2D g, double cx, double cy, double s) {
        Color c = g.getColor();
        float t = (float) (s * 0.22);
        double r = s * 0.28;
        g.setStroke(new BasicStroke(t, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx - r, cy - s * 0.4);
        p.lineTo(cx - r, cy);
        p.curveTo(cx - r, cy + r * 1.35, cx + r, cy + r * 1.35, cx + r, cy);
        p.lineTo(cx + r, cy - s * 0.4);
        g.draw(p);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(t, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        line(g, cx - r, cy - s * 0.4, cx - r, cy - s * 0.24);
        line(g, cx + r, cy - s * 0.4, cx + r, cy - s * 0.24);
        g.setColor(c);
    }

    public static void powerIcon(Graphics2D g, PowerUpType t, double cx, double cy, double s) {
        switch (t) {
            case SHIELD -> shield(g, cx, cy, s);
            case SLOW_MO -> snowflake(g, cx, cy, s);
            case DOUBLE_SCORE -> {
                g.setFont(Theme.SCORE.deriveFont((float) (s * 0.62)));
                centerIn(g, "2x", cx - s / 2, cy - s / 2, s, s);
            }
            case GHOST -> ghost(g, cx, cy, s);
            case MAGNET -> magnet(g, cx, cy, s);
            case EXTRA_LIFE -> heart(g, cx, cy + s * 0.04, s * 0.9);
        }
    }

    public static void pauseIcon(Graphics2D g, double cx, double cy, double s) {
        fillRound(g, cx - s * 0.32, cy - s * 0.4, s * 0.22, s * 0.8, s * 0.06);
        fillRound(g, cx + s * 0.1, cy - s * 0.4, s * 0.22, s * 0.8, s * 0.06);
    }

    public static void speakerIcon(Graphics2D g, double cx, double cy, double s, boolean on) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx - s * 0.45, cy - s * 0.15);
        p.lineTo(cx - s * 0.22, cy - s * 0.15);
        p.lineTo(cx + s * 0.05, cy - s * 0.42);
        p.lineTo(cx + s * 0.05, cy + s * 0.42);
        p.lineTo(cx - s * 0.22, cy + s * 0.15);
        p.lineTo(cx - s * 0.45, cy + s * 0.15);
        p.closePath();
        g.fill(p);
        g.setStroke(stroke((float) (s * 0.09)));
        if (on) {
            g.draw(new java.awt.geom.Arc2D.Double(cx - s * 0.2, cy - s * 0.25, s * 0.45, s * 0.5, -50, 100, java.awt.geom.Arc2D.OPEN));
            g.draw(new java.awt.geom.Arc2D.Double(cx - s * 0.3, cy - s * 0.42, s * 0.75, s * 0.84, -50, 100, java.awt.geom.Arc2D.OPEN));
        } else {
            line(g, cx + s * 0.2, cy - s * 0.18, cx + s * 0.48, cy + s * 0.18);
            line(g, cx + s * 0.48, cy - s * 0.18, cx + s * 0.2, cy + s * 0.18);
        }
    }

    public static void clockIcon(Graphics2D g, double cx, double cy, double s) {
        g.setStroke(stroke((float) (s * 0.12)));
        drawCircle(g, cx, cy, s * 0.42);
        line(g, cx, cy, cx, cy - s * 0.24);
        line(g, cx, cy, cx + s * 0.18, cy + s * 0.08);
    }
}
