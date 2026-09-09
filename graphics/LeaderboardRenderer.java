package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import logic.ScoreEntry;

/**
 * Renders the in-canvas Leaderboard screen with medal icons and scores.
 */
public class LeaderboardRenderer {

    public void draw(Graphics2D g2d, List<ScoreEntry> scores, int screenWidth, int screenHeight) {
        int cardW = 660;
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
        String title = "\uD83C\uDFC6  LEADERBOARD";
        int titleX = (screenWidth - fm.stringWidth(title)) / 2;
        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString(title, titleX, cardY + 54);

        // Divider
        g2d.setColor(new Color(255, 255, 255, 30));
        g2d.drawLine(cardX + 40, cardY + 70, cardX + cardW - 40, cardY + 70);

        // Scores List
        int startY = cardY + 95;
        int rowH = 42;

        if (scores.isEmpty()) {
            g2d.setFont(UITheme.FONT_HEADER);
            g2d.setColor(new Color(255, 255, 255, 120));
            String emptyMsg = "No high scores yet \u2014 play a game to set one!";
            fm = g2d.getFontMetrics();
            g2d.drawString(emptyMsg, (screenWidth - fm.stringWidth(emptyMsg)) / 2, cardY + 280);
        } else {
            for (int i = 0; i < Math.min(10, scores.size()); i++) {
                ScoreEntry entry = scores.get(i);
                int rowY = startY + i * rowH;

                // Alternate row background
                if (i % 2 == 0) {
                    g2d.setColor(new Color(255, 255, 255, 8));
                    g2d.fillRoundRect(cardX + 30, rowY, cardW - 60, 36, 12, 12);
                }

                // Rank
                int rank = i + 1;
                String rankStr;
                Color rankColor;
                if (rank == 1) {
                    rankStr = "\uD83E\uDD47 #1";
                    rankColor = UITheme.ACCENT_GOLD;
                } else if (rank == 2) {
                    rankStr = "\uD83E\uDD48 #2";
                    rankColor = new Color(200, 210, 225);
                } else if (rank == 3) {
                    rankStr = "\uD83E\uDD49 #3";
                    rankColor = new Color(205, 127, 50);
                } else {
                    rankStr = "  #" + rank;
                    rankColor = new Color(180, 180, 180);
                }

                g2d.setFont(UITheme.FONT_BODY_BOLD);
                g2d.setColor(rankColor);
                g2d.drawString(rankStr, cardX + 48, rowY + 24);

                // Player name
                g2d.setColor(Color.WHITE);
                g2d.drawString(entry.getName(), cardX + 160, rowY + 24);

                // Score
                g2d.setFont(UITheme.FONT_BODY_BOLD);
                g2d.setColor(UITheme.PRIMARY_GREEN);
                String scoreStr = entry.getScore() + " pts";
                fm = g2d.getFontMetrics();
                g2d.drawString(scoreStr, cardX + cardW - 60 - fm.stringWidth(scoreStr), rowY + 24);
            }
        }
    }
}
