package graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Visual design system and theme constants for the Snake Game.
 */
public class UITheme {
    // Curated Harmonious Palette
    public static final Color PRIMARY_GREEN = new Color(0, 230, 118);
    public static final Color ACCENT_CYAN = new Color(0, 229, 255);
    public static final Color ACCENT_GOLD = new Color(255, 215, 0);
    public static final Color ACCENT_RED = new Color(255, 61, 87);
    public static final Color ACCENT_ORANGE = new Color(255, 145, 0);

    // Dark glassmorphism colors
    public static final Color BG_DARK = new Color(12, 16, 24);
    public static final Color BG_CARD = new Color(20, 26, 38, 220);
    public static final Color BG_CARD_BORDER = new Color(255, 255, 255, 45);
    public static final Color GLASS_BUTTON_BG = new Color(255, 255, 255, 15);
    public static final Color GLASS_BUTTON_HOVER = new Color(0, 230, 118, 45);
    public static final Color GLASS_BUTTON_BORDER = new Color(255, 255, 255, 70);
    public static final Color GLASS_BUTTON_BORDER_HOVER = new Color(0, 230, 118, 200);

    // Modern Typography
    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 68);
    public static final Font FONT_SUBTITLE = new Font("SansSerif", Font.BOLD, 28);
    public static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 22);
    public static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 16);
    public static final Font FONT_BODY_BOLD = new Font("SansSerif", Font.BOLD, 16);
    public static final Font FONT_BUTTON = new Font("SansSerif", Font.BOLD, 18);
    public static final Font FONT_HUD = new Font("SansSerif", Font.BOLD, 20);

    /**
     * Applies high quality anti-aliasing hints.
     */
    public static void applyQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
