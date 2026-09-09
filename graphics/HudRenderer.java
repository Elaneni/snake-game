package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import logic.ChallengeManager;
import logic.GameMode;
import logic.SnakeEngine;

/**
 * Renders transparent in-game heads-up display (score, high score, level, lives, challenge status, toast).
 */
public class HudRenderer {

    public void draw(Graphics2D g2d, SnakeEngine engine, ChallengeManager challengeManager, int highScore, int screenWidth, int screenHeight) {
        // Transparent top HUD glass bar
        g2d.setColor(new Color(10, 15, 24, 130));
        g2d.fillRect(0, 0, screenWidth, 80);

        // Bottom border line for HUD
        g2d.setColor(new Color(255, 255, 255, 25));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawLine(0, 80, screenWidth, 80);

        // Left info: Level, Lives, Mode
        g2d.setFont(UITheme.FONT_HUD);
        g2d.setColor(Color.WHITE);
        g2d.drawString("LVL " + engine.getCurrentLevel(), 24, 34);

        // Lives display
        if (engine.getGameMode() == GameMode.CLASSIC) {
            g2d.setColor(new Color(255, 61, 87));
            StringBuilder hearts = new StringBuilder();
            for (int i = 0; i < engine.getLives(); i++) {
                hearts.append("\u2665 ");
            }
            g2d.drawString("LIVES: " + hearts.toString(), 24, 62);
        } else {
            g2d.setColor(UITheme.ACCENT_CYAN);
            g2d.drawString("MODE: " + engine.getGameMode(), 24, 62);
        }

        // Center: Score & High Score
        g2d.setFont(UITheme.FONT_SUBTITLE);
        FontMetrics fm = g2d.getFontMetrics();
        String scoreStr = "SCORE  " + engine.getApplesEaten();
        String hiScoreStr = "BEST  " + Math.max(highScore, engine.getApplesEaten());
        String fullScore = scoreStr + "    |    " + hiScoreStr;

        int scoreX = (screenWidth - fm.stringWidth(fullScore)) / 2;
        g2d.setColor(UITheme.PRIMARY_GREEN);
        g2d.drawString(scoreStr, scoreX, 50);

        int dividerX = scoreX + fm.stringWidth(scoreStr);
        g2d.setColor(new Color(255, 255, 255, 60));
        g2d.drawString("    |    ", dividerX, 50);

        int bestX = dividerX + fm.stringWidth("    |    ");
        g2d.setColor(UITheme.ACCENT_GOLD);
        g2d.drawString(hiScoreStr, bestX, 50);

        // Challenge banner
        if (engine.getGameMode() == GameMode.CHALLENGE && challengeManager.isChallengeActive()) {
            drawChallengeBanner(g2d, challengeManager);
        }

        // Toast notification
        drawToast(g2d, engine, screenWidth);
    }

    private void drawChallengeBanner(Graphics2D g2d, ChallengeManager cm) {
        int bannerX = 20;
        int bannerY = 92;
        int bannerW = 460;
        int bannerH = 76;

        g2d.setColor(new Color(15, 20, 32, 200));
        g2d.fillRoundRect(bannerX, bannerY, bannerW, bannerH, 16, 16);

        g2d.setColor(new Color(0, 229, 255, 120));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(bannerX, bannerY, bannerW, bannerH, 16, 16);

        g2d.setFont(UITheme.FONT_BODY_BOLD);
        g2d.setColor(UITheme.ACCENT_CYAN);
        g2d.drawString("\u2694 " + cm.getCurrentChallengeDescription(), bannerX + 16, bannerY + 28);

        g2d.setFont(UITheme.FONT_BODY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Progress: " + cm.getProgress() + " / " + cm.getCurrentGoal(), bannerX + 16, bannerY + 54);

        g2d.setColor(new Color(255, 145, 0));
        g2d.drawString("Time Left: " + cm.getRemainingSeconds() + "s", bannerX + 280, bannerY + 54);
    }

    private void drawToast(Graphics2D g2d, SnakeEngine engine, int screenWidth) {
        String msg = engine.getToastMessage();
        if (msg == null) return;

        float alpha = engine.getToastAlpha();
        if (alpha <= 0f) return;

        g2d.setFont(UITheme.FONT_SUBTITLE);
        FontMetrics fm = g2d.getFontMetrics();
        int textW = fm.stringWidth(msg);
        int panelW = textW + 60;
        int panelH = 54;
        int panelX = (screenWidth - panelW) / 2;
        int panelY = 96;

        g2d.setColor(new Color(15, 20, 30, (int) (220 * alpha)));
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 26, 26);

        Color tc = engine.getToastColor();
        g2d.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int) (220 * alpha)));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 26, 26);

        g2d.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int) (255 * alpha)));
        g2d.drawString(msg, panelX + 30, panelY + 37);
    }

    public void drawPauseOverlay(Graphics2D g2d, int screenWidth, int screenHeight) {
        g2d.setColor(new Color(8, 12, 18, 175));
        g2d.fillRect(0, 0, screenWidth, screenHeight);

        int panelW = 540, panelH = 200;
        int panelX = (screenWidth - panelW) / 2;
        int panelY = (screenHeight - panelH) / 2;

        g2d.setColor(UITheme.BG_CARD);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 24, 24);

        g2d.setColor(new Color(0, 230, 118, 150));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 24, 24);

        g2d.setColor(Color.WHITE);
        g2d.setFont(UITheme.FONT_TITLE);
        FontMetrics fm = g2d.getFontMetrics();
        String title = "PAUSED";
        g2d.drawString(title, (screenWidth - fm.stringWidth(title)) / 2, panelY + 90);

        g2d.setColor(new Color(200, 210, 225));
        g2d.setFont(UITheme.FONT_BODY);
        fm = g2d.getFontMetrics();
        String hint = "Press SPACEBAR or click Resume to continue";
        g2d.drawString(hint, (screenWidth - fm.stringWidth(hint)) / 2, panelY + 140);
    }
}
