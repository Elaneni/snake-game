package logic;

/**
 * Represents an on-screen power-up item or active power-up effect.
 */
public class PowerUp {
    private final PowerUpType type;
    private int x;
    private int y;
    private final long spawnTime;
    private static final int DESPAWN_TIME = 8000; // on-screen for 8 seconds before disappearing

    public PowerUp(PowerUpType type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.spawnTime = System.currentTimeMillis();
    }

    public PowerUpType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - spawnTime >= DESPAWN_TIME;
    }

    public float getRemainingProgress() {
        long elapsed = System.currentTimeMillis() - spawnTime;
        return Math.max(0f, 1f - (float) elapsed / DESPAWN_TIME);
    }
}
