package logic;

import java.awt.Color;

/** Special items that spawn on the board from time to time. */
public enum PowerUpType {
    SHIELD("Shield", "Absorbs one crash", new Color(64, 196, 255), 15000),
    SLOW_MO("Slow-Mo", "Slows time down", new Color(130, 177, 255), 7000),
    DOUBLE_SCORE("Double Points", "2x points on everything", new Color(255, 202, 40), 9000),
    GHOST("Ghost", "Pass through yourself and walls", new Color(206, 147, 216), 6000),
    MAGNET("Magnet", "Apples are pulled toward you", new Color(255, 112, 67), 9000),
    EXTRA_LIFE("Extra Life", "+1 life (Classic)", new Color(255, 82, 120), 0);

    public final String label;
    public final String description;
    public final Color color;
    public final int durationMs;

    PowerUpType(String label, String description, Color color, int durationMs) {
        this.label = label;
        this.description = description;
        this.color = color;
        this.durationMs = durationMs;
    }
}
