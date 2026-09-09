package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Renders the in-canvas Help / Instructions screen.
 */
public class HelpRenderer {

    public void draw(Graphics2D g2d, boolean isFirstTime, int screenWidth, int screenHeight) {
        int cardW = 760;
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
        String title = isFirstTime ? "\uD83D\uDC4B  WELCOME TO SNAKE!" : "\u2753  HOW TO PLAY";
        int titleX = (screenWidth - fm.stringWidth(title)) / 2;
        g2d.setColor(isFirstTime ? UITheme.PRIMARY_GREEN : UITheme.ACCENT_CYAN);
        g2d.drawString(title, titleX, cardY + 52);

        // Divider
        g2d.setColor(new Color(255, 255, 255, 30));
        g2d.drawLine(cardX + 40, cardY + 68, cardX + cardW - 40, cardY + 68);

        int col1X = cardX + 50;
        int col2X = cardX + 410;

        // Section 1: Controls
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString("\u2328 Controls", col1X, cardY + 105);

        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("\u2022 Arrow Keys or W / A / S / D to steer", col1X, cardY + 135);
        g2d.drawString("\u2022 SPACEBAR to Pause / Resume", col1X, cardY + 162);
        g2d.drawString("\u2022 ESC to open Menu or go back", col1X, cardY + 189);

        // Section 2: Apples & Scoring
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(UITheme.PRIMARY_GREEN);
        g2d.drawString("\uD83C\uDF4E Apples & Scoring", col1X, cardY + 235);

        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("\u2022 Small Red Apple: +1 point and +1 body unit", col1X, cardY + 265);
        g2d.drawString("\u2022 Big Gold Apple: +5 points! Appears for 5s", col1X, cardY + 292);
        g2d.drawString("  every 5 small apples eaten.", col1X, cardY + 314);
        g2d.drawString("\u2022 Speed increases every 15 points!", col1X, cardY + 341);

        // Section 3: Game Modes
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(UITheme.ACCENT_CYAN);
        g2d.drawString("\uD83C\uDFC1 Game Modes", col2X, cardY + 105);

        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("\u2022 Classic: 3 lives. Colliding with walls,", col2X, cardY + 135);
        g2d.drawString("  obstacles, or your body loses a life.", col2X, cardY + 158);
        g2d.drawString("\u2022 Endless: No game over! Collisions reset", col2X, cardY + 185);
        g2d.drawString("  the snake while keeping your score.", col2X, cardY + 208);
        g2d.drawString("\u2022 Challenge: Complete exciting objectives", col2X, cardY + 235);
        g2d.drawString("  under a time limit!", col2X, cardY + 258);

        // Section 4: Obstacles & Levels
        g2d.setFont(UITheme.FONT_HEADER);
        g2d.setColor(UITheme.ACCENT_RED);
        g2d.drawString("\uD83E\uDDF1 Levels & Hazards", col2X, cardY + 300);

        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("\u2022 Advance levels every 35 points (up to lvl 5)", col2X, cardY + 330);
        g2d.drawString("\u2022 Levels 2-5 introduce metallic wall barriers", col2X, cardY + 355);

        // Tip footer
        g2d.setFont(UITheme.FONT_BODY_BOLD);
        g2d.setColor(new Color(255, 255, 255, 120));
        String tip = "Tip: You can change snake speed anytime in the Settings menu!";
        fm = g2d.getFontMetrics();
        g2d.drawString(tip, (screenWidth - fm.stringWidth(tip)) / 2, cardY + 475);
    }
}
