package graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import logic.Particle;

/**
 * Renders particle bursts on apple consumption.
 */
public class ParticleRenderer {

    public void draw(Graphics2D g2d, List<Particle> particles) {
        for (Particle p : particles) {
            if (p.isDead()) continue;
            Color c = p.getColor();
            int alphaInt = (int) (p.getAlpha() * 255);
            g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alphaInt));
            g2d.fillOval((int) p.getX(), (int) p.getY(), 6, 6);
        }
    }
}
