package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Renders the in-canvas Settings screen backdrop and layout headers.
 */
public class SettingsRenderer {

    public void draw(Graphics2D g2d, int screenWidth, int screenHeight, int speedLevel) {
        int cardW = 680;
        int cardH = 620;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = (screenHeight - cardH) / 2;

        // Card backdrop
        g2d.setColor(UITheme.BG_CARD);
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        g2d.setColor(UITheme.BG_CARD_BORDER);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        // Header Title
        g2d.setFont(UITheme.FONT_SUBTITLE);
        FontMetrics fm = g2d.getFontMetrics();
        String title = "\u2699  SETTINGS";
        int titleX = (screenWidth - fm.stringWidth(title)) / 2;
        g2d.setColor(UITheme.ACCENT_CYAN);
        g2d.drawString(title, titleX, cardY + 54);

        // Divider
        g2d.setColor(new Color(255, 255, 255, 30));
        g2d.drawLine(cardX + 40, cardY + 70, cardX + cardW - 40, cardY + 70);

        // Section 1: Snake Speed Slider
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Snake Speed", cardX + 50, cardY + 115);

        // Speed descriptor text
        String speedDesc;
        if (speedLevel <= 2) speedDesc = "Casual (Slow)";
        else if (speedLevel <= 4) speedDesc = "Standard";
        else if (speedLevel <= 6) speedDesc = "Normal (Balanced)";
        else if (speedLevel <= 8) speedDesc = "Fast";
        else speedDesc = "Ludicrous Speed!";

        g2d.setFont(UITheme.FONT_BODY_BOLD);
        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString("Level " + speedLevel + " - " + speedDesc, cardX + 380, cardY + 115);

        // Section 2: Game Mode
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Game Mode", cardX + 50, cardY + 225);

        // Section 3: Starting Level
        g2d.drawString("Starting Level", cardX + 50, cardY + 325);

        // Section 4: Snake Colors
        g2d.drawString("Snake Colors", cardX + 50, cardY + 425);
    }
}
