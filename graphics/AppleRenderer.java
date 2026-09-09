package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import logic.SnakeEngine;

/**
 * Renders small apples and big bonus apples with radial lighting,
 * leaf/stem details, and bonus countdown rings.
 */
public class AppleRenderer {

    public void draw(Graphics2D g2d, SnakeEngine engine) {
        if (!engine.isBigAppleActive()) {
            drawApple(g2d, engine.getAppleX(), engine.getAppleY(), SnakeEngine.UNIT_SIZE, new Color(230, 40, 40));
        } else {
            int bigX = engine.getBigAppleX();
            int bigY = engine.getBigAppleY();
            int bigSize = SnakeEngine.BIG_APPLE_SIZE;
            drawApple(g2d, bigX, bigY, bigSize, new Color(255, 140, 0));

            // Pulsing countdown ring around big apple
            long elapsed = System.currentTimeMillis() - engine.getBigAppleStartTime();
            float progress = Math.max(0f, 1f - (float) elapsed / SnakeEngine.BIG_APPLE_DURATION);
            int arcAngle = (int) (progress * 360);

            g2d.setColor(new Color(255, 215, 0, 180));
            g2d.setStroke(new BasicStroke(3.0f));
            g2d.drawArc(bigX - 6, bigY - 6, bigSize + 12, bigSize + 12, 90, arcAngle);
        }
    }

    private void drawApple(Graphics2D g2d, int ax, int ay, int size, Color baseColor) {
        int cx = ax + size / 2;
        int cy = ay + size / 2;

        // Soft ambient glow
        g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 60));
        g2d.fillOval(ax - size / 6, ay - size / 6, (int) (size * 1.33), (int) (size * 1.33));

        // Radial gradient body
        RadialGradientPaint appleGradient = new RadialGradientPaint(
            new Point2D.Float(cx - size / 6f, cy - size / 6f), size * 0.75f,
            new float[] {0.0f, 1.0f},
            new Color[] {baseColor.brighter(), baseColor.darker()}
        );
        g2d.setPaint(appleGradient);
        g2d.fillOval(ax, ay, size, size);

        // Stem
        g2d.setColor(new Color(90, 55, 20));
        g2d.setStroke(new BasicStroke(Math.max(2, size / 16.0f)));
        g2d.drawLine(cx, ay, cx + size / 10, ay - size / 6);

        // Leaf
        g2d.setColor(new Color(60, 185, 75));
        int leafW = size / 3;
        int leafH = size / 5;
        g2d.fillOval(cx + size / 12, ay - size / 8, leafW, leafH);

        // Specular highlight
        g2d.setColor(new Color(255, 255, 255, 140));
        int shineSize = size / 4;
        g2d.fillOval(ax + size / 5, ay + size / 6, shineSize, shineSize);
    }
}
