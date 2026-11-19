package graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import logic.Challenge;
import logic.GameMode;
import logic.PowerUpType;
import logic.SnakeEngine;

/** The heads-up bar above the board: level, lives, combo, score, effect and mode timers. */
public final class HudRenderer {
    private static final int TOP = 14;

    public void draw(Graphics2D g, SnakeEngine e, int best, double shownScore, double t) {
        drawLevel(g, e);
        drawLives(g, e);
        drawCombo(g, e);
        drawScore(g, e, best, shownScore);
        drawEffect(g, e);
        if (e.mode() == GameMode.TIME_ATTACK) drawTimer(g, e, t);
        else if (e.mode() == GameMode.CHALLENGE) drawChallenge(g, e);
    }

    private void drawLevel(Graphics2D g, SnakeEngine e) {
        g.setColor(Theme.alpha(Theme.CYAN, 34));
        Gfx.fillRound(g, 20, TOP, 118, 30, 15);
        g.setColor(Theme.alpha(Theme.CYAN, 140));
        g.setStroke(Gfx.stroke(1.2f));
        Gfx.drawRound(g, 20.5, TOP + 0.5, 117, 29, 14.5);
        g.setFont(Theme.BODY_BOLD);
        g.setColor(Theme.CYAN);
        Gfx.centerIn(g, "LEVEL " + e.level(), 20, TOP, 118, 30);

        double progress = 1;
        if (e.level() < SnakeEngine.MAX_LEVEL) {
            int per = SnakeEngine.POINTS_PER_LEVEL;
            progress = (e.score() % per) / (double) per;
        }
        g.setColor(new Color(255, 255, 255, 25));
        Gfx.fillRound(g, 24, TOP + 38, 110, 5, 2.5);
        g.setColor(Theme.CYAN);
        Gfx.fillRound(g, 24, TOP + 38, Math.max(5, 110 * progress), 5, 2.5);
    }

    private void drawLives(Graphics2D g, SnakeEngine e) {
        double x = 160;
        if (e.mode() == GameMode.CLASSIC) {
            for (int i = 0; i < SnakeEngine.MAX_LIVES; i++) {
                boolean full = i < e.lives();
                if (!full && i >= SnakeEngine.CLASSIC_LIVES) continue;
                g.setColor(full ? Theme.RED : new Color(255, 255, 255, 30));
                if (full) Gfx.glow(g, Theme.RED, x + i * 28 + 11, TOP + 16, 18, 0.35);
                Gfx.heart(g, x + i * 28 + 11, TOP + 16, 22);
            }
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            g.drawString("LIVES", (float) x, TOP + 46);
        } else {
            g.setFont(Theme.BODY_BOLD);
            g.setColor(Theme.TEXT);
            g.drawString(e.mode().label.toUpperCase(), (float) x, TOP + 22);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            g.drawString(e.isWrap() ? "MODE  •  WRAP WALLS" : "MODE", (float) x, TOP + 44);
        }
    }

    private void drawCombo(Graphics2D g, SnakeEngine e) {
        int combo = e.combo();
        if (combo < 2) return;
        double x = 330;
        Color c = combo >= 5 ? Theme.RED : combo >= 3 ? Theme.ORANGE : Theme.GOLD;
        g.setFont(Theme.H2);
        g.setColor(c);
        g.drawString("x" + combo, (float) x, TOP + 26);
        int w = Gfx.width(g, Theme.H2, "x" + combo);
        g.setFont(Theme.SMALL);
        g.drawString("COMBO", (float) (x + w + 8), TOP + 24);
        g.setColor(new Color(255, 255, 255, 25));
        Gfx.fillRound(g, x, TOP + 38, 110, 5, 2.5);
        g.setColor(c);
        Gfx.fillRound(g, x, TOP + 38, Math.max(5, 110 * e.comboLeftFraction()), 5, 2.5);
    }

    private void drawScore(Graphics2D g, SnakeEngine e, int best, double shown) {
        double cx = BoardRenderer.W / 2.0;
        g.setFont(Theme.SMALL);
        g.setColor(Theme.TEXT_FAINT);
        Gfx.center(g, "SCORE", cx - 70, TOP + 12);
        Gfx.center(g, "BEST", cx + 70, TOP + 12);
        g.setFont(Theme.SCORE);
        String s = String.valueOf((int) Math.round(shown));
        boolean bumping = shown < e.score() - 0.5;
        g.setColor(bumping ? Theme.lerp(Theme.GREEN, Color.WHITE, 0.5) : Theme.GREEN);
        Gfx.center(g, s, cx - 70, TOP + 46);
        int b = Math.max(best, e.score());
        g.setColor(e.score() >= best && best > 0 ? Theme.GOLD : Theme.alpha(Theme.GOLD, 190));
        g.setFont(Theme.H2);
        Gfx.center(g, String.valueOf(b), cx + 70, TOP + 44);
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(Gfx.stroke(1f));
        Gfx.line(g, cx, TOP + 4, cx, TOP + 44);
    }

    private void drawEffect(Graphics2D g, SnakeEngine e) {
        PowerUpType fx = e.effect();
        if (fx == null) return;
        double x = 820, w = 172, h = 44, y = TOP + 2;
        g.setColor(Theme.alpha(fx.color, 30));
        Gfx.fillRound(g, x, y, w, h, 12);
        g.setColor(Theme.alpha(fx.color, 150));
        g.setStroke(Gfx.stroke(1.2f));
        Gfx.drawRound(g, x + 0.5, y + 0.5, w - 1, h - 1, 12);
        g.setColor(fx.color);
        Gfx.powerIcon(g, fx, x + 24, y + h / 2, 22);
        g.setFont(Theme.BODY_BOLD);
        g.setColor(Theme.TEXT);
        g.drawString(fx.label, (float) (x + 44), (float) (y + 20));
        if (fx.durationMs > 0 && fx != PowerUpType.SHIELD) {
            g.setColor(new Color(255, 255, 255, 30));
            Gfx.fillRound(g, x + 44, y + 28, w - 58, 5, 2.5);
            g.setColor(fx.color);
            Gfx.fillRound(g, x + 44, y + 28, Math.max(5, (w - 58) * e.effectLeftFraction()), 5, 2.5);
        } else {
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_DIM);
            g.drawString(fx == PowerUpType.SHIELD ? "Blocks next crash" : "", (float) (x + 44), (float) (y + 36));
        }
    }

    private void drawTimer(Graphics2D g, SnakeEngine e, double t) {
        double ms = e.timeLeftMs();
        boolean low = ms < 10_000;
        Color c = low ? Theme.RED : Theme.TEXT;
        if (low && Math.sin(t * 0.012) > 0) c = Theme.lerp(c, Color.WHITE, 0.4);
        int secs = (int) Math.ceil(ms / 1000);
        String s = String.format("%d:%02d", secs / 60, secs % 60);
        double x = 1020;
        g.setColor(c);
        Gfx.clockIcon(g, x + 12, TOP + 22, 22);
        g.setFont(Theme.SCORE);
        g.drawString(s, (float) (x + 32), (float) (TOP + 34));
        g.setFont(Theme.SMALL);
        g.setColor(Theme.TEXT_FAINT);
        g.drawString("TIME LEFT", (float) (x + 34), (float) (TOP + 50));
    }

    private void drawChallenge(Graphics2D g, SnakeEngine e) {
        Challenge ch = e.getChallenge();
        double x = 1010, w = 190;
        int progress = Math.min(ch.goal(), e.getChallengeProgress());
        g.setFont(Theme.SMALL);
        g.setColor(Theme.CYAN);
        g.drawString("CHALLENGE " + (e.getChallengeIndex() + 1) + "/" + Challenge.ALL.size(), (float) x, TOP + 10);
        g.setFont(Theme.BODY_BOLD);
        g.setColor(Theme.TEXT);
        g.drawString(ch.text(), (float) x, TOP + 30);
        g.setColor(new Color(255, 255, 255, 25));
        Gfx.fillRound(g, x, TOP + 38, w - 50, 6, 3);
        g.setColor(Theme.CYAN);
        Gfx.fillRound(g, x, TOP + 38, Math.max(6, (w - 50) * progress / (double) ch.goal()), 6, 3);
        double left = e.getChallengeLeftMs() / 1000;
        g.setFont(Theme.BODY_BOLD);
        g.setColor(left < 6 ? Theme.RED : Theme.ORANGE);
        Gfx.right(g, (int) Math.ceil(left) + "s", x + w, TOP + 46);
    }
}
