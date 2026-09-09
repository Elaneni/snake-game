package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Renders the in-canvas Game Over screen and stats overview.
 */
public class GameOverRenderer {

    public void draw(Graphics2D g2d, int finalScore, int highScore, int levelReached, int screenWidth, int screenHeight) {
        // Soft red-tinged background overlay
        g2d.setColor(new Color(20, 8, 12, 185));
        g2d.fillRect(0, 0, screenWidth, screenHeight);

        int cardW = 580;
        int cardH = 380;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = (screenHeight - cardH) / 2 - 40;

        // Card backdrop
        g2d.setColor(UITheme.BG_CARD);
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        g2d.setColor(new Color(255, 61, 87, 160));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        // Header Title
        g2d.setFont(UITheme.FONT_TITLE);
        FontMetrics fm = g2d.getFontMetrics();
        String title = "GAME OVER";
        int titleX = (screenWidth - fm.stringWidth(title)) / 2;
        int titleY = cardY + 90;

        // Red glow
        g2d.setColor(new Color(255, 61, 87, 60));
        g2d.drawString(title, titleX - 2, titleY);
        g2d.drawString(title, titleX + 2, titleY);
        g2d.drawString(title, titleX, titleY - 2);
        g2d.drawString(title, titleX, titleY + 2);

        g2d.setColor(UITheme.ACCENT_RED);
        g2d.drawString(title, titleX, titleY);

        // Score summary
        g2d.setFont(UITheme.FONT_SUBTITLE);
        fm = g2d.getFontMetrics();
        String scoreText = "Final Score: " + finalScore;
        g2d.setColor(Color.WHITE);
        g2d.drawString(scoreText, (screenWidth - fm.stringWidth(scoreText)) / 2, cardY + 155);

        // High score & level
        g2d.setFont(UITheme.FONT_BODY_BOLD);
        fm = g2d.getFontMetrics();
        String statsText = "Level Reached: " + levelReached + "   \u2022   High Score: " + Math.max(highScore, finalScore);
        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString(statsText, (screenWidth - fm.stringWidth(statsText)) / 2, cardY + 195);
    }
}
