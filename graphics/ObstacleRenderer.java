package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * Renders level obstacle barriers with metallic gradients and borders.
 */
public class ObstacleRenderer {

    public void draw(Graphics2D g2d, List<Rectangle> obstacles) {
        for (Rectangle obstacle : obstacles) {
            GradientPaint gradient = new GradientPaint(
                obstacle.x, obstacle.y, new Color(110, 115, 130),
                obstacle.x, obstacle.y + obstacle.height, new Color(50, 55, 68)
            );
            g2d.setPaint(gradient);
            g2d.fillRect(obstacle.x, obstacle.y, obstacle.width, obstacle.height);

            // Inset highlight border
            g2d.setColor(new Color(160, 170, 190, 140));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRect(obstacle.x + 1, obstacle.y + 1, obstacle.width - 2, obstacle.height - 2);

            // Corner decorative dot
            g2d.setColor(new Color(255, 61, 87, 180));
            g2d.fillRect(obstacle.x + 3, obstacle.y + 3, 4, 4);
        }
    }
}
