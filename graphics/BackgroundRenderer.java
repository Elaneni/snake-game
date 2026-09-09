package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import logic.SnakeEngine;

/**
 * Renders dynamic background with subtle grid lines and soft gradient depth.
 */
public class BackgroundRenderer {

    public void draw(Graphics2D g2d, Color baseColor, int width, int height) {
        // Base dark gradient
        Color topColor = baseColor.brighter();
        Color bottomColor = baseColor.darker().darker();
        GradientPaint bgGradient = new GradientPaint(0, 0, topColor, 0, height, bottomColor);
        g2d.setPaint(bgGradient);
        g2d.fillRect(0, 0, width, height);

        // Fine grid lines
        g2d.setColor(new Color(255, 255, 255, 12));
        g2d.setStroke(new BasicStroke(1.0f));

        for (int x = 0; x <= width; x += SnakeEngine.UNIT_SIZE) {
            g2d.drawLine(x, 0, x, height);
        }
        for (int y = 0; y <= height; y += SnakeEngine.UNIT_SIZE) {
            g2d.drawLine(0, y, width, y);
        }
    }
}
