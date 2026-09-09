package logic;

import java.awt.Color;

/**
 * Particle representation for apple consumption explosion effects.
 */
public class Particle {
    private double x;
    private double y;
    private final double dx;
    private final double dy;
    private double alpha;
    private final Color color;

    public Particle(double x, double y, double dx, double dy, Color color) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.alpha = 1.0;
        this.color = color;
    }

    public void update() {
        x += dx;
        y += dy;
        alpha -= 0.05;
    }

    public boolean isDead() {
        return alpha <= 0;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getAlpha() {
        return Math.max(0.0, Math.min(1.0, alpha));
    }

    public Color getColor() {
        return color;
    }
}
