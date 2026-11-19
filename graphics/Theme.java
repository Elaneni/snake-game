package graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Set;

/** Palette, typography, snake skins and board themes. */
public final class Theme {
    private Theme() {}

    // Accent palette
    public static final Color GREEN = new Color(0, 230, 130);
    public static final Color CYAN = new Color(0, 220, 255);
    public static final Color GOLD = new Color(255, 202, 40);
    public static final Color RED = new Color(255, 72, 100);
    public static final Color ORANGE = new Color(255, 145, 40);
    public static final Color PURPLE = new Color(180, 130, 255);

    // Neutrals
    public static final Color TEXT = new Color(236, 241, 250);
    public static final Color TEXT_DIM = new Color(156, 167, 190);
    public static final Color TEXT_FAINT = new Color(100, 112, 138);
    public static final Color PANEL = new Color(17, 21, 33);
    public static final Color PANEL_BORDER = new Color(255, 255, 255, 30);
    public static final Color CHIP = new Color(255, 255, 255, 14);
    public static final Color CHIP_HOVER = new Color(255, 255, 255, 30);

    // Typography: prefer modern system UI fonts, fall back to the JDK's SansSerif.
    private static final String FAMILY;
    private static final String HEAVY_FAMILY;

    static {
        Set<String> names = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        FAMILY = pick(names, "Segoe UI", "Inter", "SF Pro Display", "Helvetica Neue", "Ubuntu", "Roboto", "SansSerif");
        HEAVY_FAMILY = pick(names, "Segoe UI Black", "Inter Black", "Arial Black", FAMILY);
    }

    private static String pick(Set<String> names, String... wanted) {
        for (String w : wanted) if (names.contains(w)) return w;
        return "SansSerif";
    }

    public static final Font TITLE = new Font(HEAVY_FAMILY, Font.PLAIN, 112);
    public static final Font H1 = new Font(FAMILY, Font.BOLD, 44);
    public static final Font H2 = new Font(FAMILY, Font.BOLD, 26);
    public static final Font H3 = new Font(FAMILY, Font.BOLD, 18);
    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 15);
    public static final Font BODY_BOLD = new Font(FAMILY, Font.BOLD, 15);
    public static final Font SMALL = new Font(FAMILY, Font.BOLD, 12);
    public static final Font BUTTON = new Font(FAMILY, Font.BOLD, 17);
    public static final Font SCORE = new Font(HEAVY_FAMILY, Font.PLAIN, 30);
    public static final Font HUGE = new Font(HEAVY_FAMILY, Font.PLAIN, 150);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.BOLD, 12);

    // Snake skins -----------------------------------------------------------
    public record Skin(String name, Color head, Color tail, boolean rainbow) {}

    public static final Skin[] SKINS = {
        new Skin("Emerald", new Color(80, 255, 160), new Color(0, 140, 95), false),
        new Skin("Aqua", new Color(110, 235, 255), new Color(30, 110, 230), false),
        new Skin("Sunset", new Color(255, 205, 70), new Color(235, 50, 120), false),
        new Skin("Violet", new Color(225, 160, 255), new Color(110, 60, 220), false),
        new Skin("Lava", new Color(255, 150, 60), new Color(170, 30, 30), false),
        new Skin("Rainbow", Color.WHITE, Color.WHITE, true),
    };

    // Board themes ----------------------------------------------------------
    public record Board(String name, Color bgTop, Color bgBottom, Color cellA, Color cellB,
                        Color frame, Color wallTop, Color wallBottom, Color wallEdge) {}

    public static final Board[] BOARDS = {
        new Board("Midnight", new Color(20, 24, 44), new Color(8, 10, 20), new Color(24, 30, 50), new Color(28, 35, 57),
            new Color(90, 110, 180, 70), new Color(92, 100, 140), new Color(48, 54, 82), new Color(255, 72, 100)),
        new Board("Forest", new Color(16, 34, 28), new Color(6, 14, 12), new Color(22, 44, 34), new Color(26, 51, 39),
            new Color(100, 190, 130, 70), new Color(110, 92, 70), new Color(62, 50, 38), new Color(255, 190, 60)),
        new Board("Ocean", new Color(10, 36, 56), new Color(4, 12, 24), new Color(14, 44, 66), new Color(17, 51, 75),
            new Color(80, 190, 255, 70), new Color(70, 120, 150), new Color(32, 62, 86), new Color(0, 220, 255)),
        new Board("Graphite", new Color(34, 34, 38), new Color(12, 12, 14), new Color(36, 36, 41), new Color(42, 42, 48),
            new Color(255, 255, 255, 45), new Color(120, 120, 128), new Color(62, 62, 70), new Color(255, 145, 40)),
    };

    public static Skin skin(int i) { return SKINS[Math.floorMod(i, SKINS.length)]; }
    public static Board board(int i) { return BOARDS[Math.floorMod(i, BOARDS.length)]; }

    // Helpers ---------------------------------------------------------------
    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static Color alpha(Color c, double a) {
        return alpha(c, (int) (a * 255));
    }

    public static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
            (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
