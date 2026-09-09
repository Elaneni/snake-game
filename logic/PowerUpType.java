package logic;

import java.awt.Color;

/**
 * Types of special power-ups that can spawn during gameplay.
 */
public enum PowerUpType {
    SHIELD("Shield", "Absorbs 1 collision!", new Color(0, 229, 255), 10000),
    SLOW_MO("Slow-Mo", "Slows game by 40%!", new Color(129, 212, 250), 7000),
    DOUBLE_SCORE("2x Multiplier", "Double score on all apples!", new Color(255, 215, 0), 8000),
    EXTRA_LIFE("Extra Life", "+1 Heart!", new Color(255, 64, 129), 0);

    private final String displayName;
    private final String description;
    private final Color color;
    private final int durationMs;

    PowerUpType(String displayName, String description, Color color, int durationMs) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.durationMs = durationMs;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Color getColor() {
        return color;
    }

    public int getDurationMs() {
        return durationMs;
    }
}
