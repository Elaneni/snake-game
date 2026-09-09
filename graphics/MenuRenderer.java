package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Renders the modern arcade Start Screen / Main Menu.
 */
public class MenuRenderer {

    public void draw(Graphics2D g2d, int highScore, int screenWidth, int screenHeight) {
        // Decorative center card backdrop
        int cardW = 560;
        int cardH = 580;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = (screenHeight - cardH) / 2;

        g2d.setColor(UITheme.BG_CARD);
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        g2d.setColor(UITheme.BG_CARD_BORDER);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 32, 32);

        // Neon Game Title
        g2d.setFont(UITheme.FONT_TITLE);
        FontMetrics fm = g2d.getFontMetrics();
        String title = "S N A K E";
        int titleX = (screenWidth - fm.stringWidth(title)) / 2;
        int titleY = cardY + 95;

        // Title glow
        g2d.setColor(new Color(0, 230, 118, 50));
        g2d.drawString(title, titleX - 2, titleY);
        g2d.drawString(title, titleX + 2, titleY);
        g2d.drawString(title, titleX, titleY - 2);
        g2d.drawString(title, titleX, titleY + 2);

        // Foreground title
        g2d.setColor(UITheme.PRIMARY_GREEN);
        g2d.drawString(title, titleX, titleY);

        // Subtitle
        g2d.setFont(UITheme.FONT_BODY_BOLD);
        fm = g2d.getFontMetrics();
        String subtitle = "ARCADE RETRO EVOLUTION";
        int subX = (screenWidth - fm.stringWidth(subtitle)) / 2;
        g2d.setColor(UITheme.ACCENT_CYAN);
        g2d.drawString(subtitle, subX, cardY + 130);

        // High score badge
        String hiScoreText = "\uD83C\uDFC6 ALL-TIME HIGH SCORE: " + highScore;
        g2d.setFont(UITheme.FONT_BODY_BOLD);
        fm = g2d.getFontMetrics();
        int badgeW = fm.stringWidth(hiScoreText) + 36;
        int badgeH = 34;
        int badgeX = (screenWidth - badgeW) / 2;
        int badgeY = cardY + 152;

        g2d.setColor(new Color(255, 215, 0, 25));
        g2d.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 17, 17);
        g2d.setColor(new Color(255, 215, 0, 90));
        g2d.setStroke(new BasicStroke(1.2f));
        g2d.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 17, 17);

        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString(hiScoreText, badgeX + 18, badgeY + 23);

        // Footer version tag
        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(new Color(255, 255, 255, 60));
        String footer = "Use WASD or Arrow Keys to Play \u2022 Spacebar to Pause";
        fm = g2d.getFontMetrics();
        g2d.drawString(footer, (screenWidth - fm.stringWidth(footer)) / 2, cardY + cardH - 25);
    }
}
