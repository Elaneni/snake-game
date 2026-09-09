package logic;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Core game engine encapsulating grid physics, snake movement,
 * collision detection, apple spawning, obstacles, power-ups, and particle updates.
 */
public class SnakeEngine {
    public static final int SCREEN_WIDTH = 1300;
    public static final int SCREEN_HEIGHT = 750;
    public static final int UNIT_SIZE = 50;
    public static final int PLAY_AREA_MIN_Y = 100; // 2 * UNIT_SIZE: Apples never spawn in HUD/button area!
    public static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    public static final int ANIMATION_STEPS = 5;
    public static final int MINIMUM_DELAY = 50;
    public static final int DELAY_DECREMENT = 15;
    public static final int POINTS_PER_SPEED_INCREASE = 15;
    public static final int POINTS_PER_LEVEL = 35;
    public static final int MAX_LEVEL = 5;
    public static final int INITIAL_LIVES = 3;
    public static final int MAX_LIVES = 5;
    public static final int BIG_APPLE_DURATION = 5000;
    public static final int BIG_APPLE_SIZE = UNIT_SIZE * 2;
    public static final int SMALL_APPLES_FOR_BIG = 5;
    public static final int COMBO_WINDOW_MS = 4000;

    private static final int TOAST_DURATION = 3500;
    private static final int TOAST_FADE_MS = 400;

    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private final double[] renderX = new double[GAME_UNITS];
    private final double[] renderY = new double[GAME_UNITS];

    private int bodyParts = 6;
    private int applesEaten = 0;
    private int smallApplesCount = 0;
    private int totalSmallApplesEaten = 0;
    private int lives = INITIAL_LIVES;
    private int currentLevel = 1;
    private int startingLevel = 1;
    private int currentDelay = 180;
    private int baseDelay = 180;
    private int lastSpeedIncreaseScore = 0;

    private int appleX;
    private int appleY;
    private boolean bigAppleActive = false;
    private int bigAppleX;
    private int bigAppleY;
    private long bigAppleStartTime;

    // Power-Up System
    private PowerUp activePowerUpItem = null;
    private PowerUpType activeEffect = null;
    private long activeEffectEndTime = 0;
    private int nextPowerUpThreshold = 8;

    // Combo System
    private int comboCount = 0;
    private long lastAppleEatTime = 0;

    private char direction = 'R';
    private char nextDirection = 'R';
    private int animationStep = 0;

    private final List<Rectangle> obstacles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private GameMode gameMode = GameMode.CLASSIC;

    // Toast notifications
    private String toastMessage = null;
    private Color toastColor = new Color(255, 215, 0);
    private long toastStartTime = 0;
    private int scoreToBeat = 0;
    private boolean highScoreAnnouncedThisRun = false;

    public enum CollisionResult {
        NONE,
        SHIELD_SAVED,
        LIFE_LOST,
        GAME_OVER
    }

    public enum AppleResult {
        NONE,
        SMALL_APPLE_EATEN,
        BIG_APPLE_EATEN,
        POWER_UP_COLLECTED
    }

    public void initGame(int startingLevel, int baseDelay, GameMode mode, int currentHighScore) {
        this.startingLevel = startingLevel;
        this.currentLevel = startingLevel;
        this.baseDelay = baseDelay;
        this.currentDelay = Math.max(MINIMUM_DELAY, baseDelay - (currentLevel - 1) * DELAY_DECREMENT);
        this.gameMode = mode;
        this.bodyParts = 6;
        this.applesEaten = 0;
        this.smallApplesCount = 0;
        this.totalSmallApplesEaten = 0;
        this.lives = INITIAL_LIVES;
        this.lastSpeedIncreaseScore = 0;
        this.direction = 'R';
        this.nextDirection = 'R';
        this.animationStep = 0;
        this.bigAppleActive = false;
        this.activePowerUpItem = null;
        this.activeEffect = null;
        this.activeEffectEndTime = 0;
        this.nextPowerUpThreshold = 8;
        this.comboCount = 0;
        this.lastAppleEatTime = 0;
        this.scoreToBeat = currentHighScore;
        this.highScoreAnnouncedThisRun = false;
        this.toastMessage = null;
        this.particles.clear();

        generateObstacles(currentLevel);
        setSafeStartingPosition();
        newApple();
    }

    public void resetSnakePosition() {
        setSafeStartingPosition();
        direction = 'R';
        nextDirection = 'R';
        animationStep = 0;
        newApple();
    }

    public void setSafeStartingPosition() {
        // Cap body length on respawn so it fits on screen safely!
        bodyParts = Math.min(bodyParts, 6);
        int startX = SCREEN_WIDTH / 2 - (bodyParts * UNIT_SIZE) / 2;
        int startY = SCREEN_HEIGHT / 2;
        startX = (startX / UNIT_SIZE) * UNIT_SIZE;
        startY = (startY / UNIT_SIZE) * UNIT_SIZE;
        if (startY < PLAY_AREA_MIN_Y) {
            startY = PLAY_AREA_MIN_Y;
        }

        while (isOnObstacle(startX, startY)) {
            startX += UNIT_SIZE;
            if (startX >= SCREEN_WIDTH - bodyParts * UNIT_SIZE) {
                startX = UNIT_SIZE;
                startY += UNIT_SIZE;
            }
            if (startY >= SCREEN_HEIGHT - UNIT_SIZE) {
                startY = PLAY_AREA_MIN_Y;
            }
        }

        for (int i = 0; i < bodyParts; i++) {
            x[i] = startX + (bodyParts - i - 1) * UNIT_SIZE;
            y[i] = startY;
            renderX[i] = x[i];
            renderY[i] = y[i];
        }
    }

    public void handleDirectionInput(char newDir) {
        if (newDir == 'L' && direction != 'R') {
            nextDirection = 'L';
        } else if (newDir == 'R' && direction != 'L') {
            nextDirection = 'R';
        } else if (newDir == 'U' && direction != 'D') {
            nextDirection = 'U';
        } else if (newDir == 'D' && direction != 'U') {
            nextDirection = 'D';
        }
    }

    public boolean move() {
        animationStep++;
        boolean fullStepCompleted = false;

        if (animationStep >= ANIMATION_STEPS) {
            direction = nextDirection;
            for (int i = bodyParts - 1; i > 0; i--) {
                x[i] = x[i - 1];
                y[i] = y[i - 1];
            }
            switch (direction) {
                case 'U': y[0] -= UNIT_SIZE; break;
                case 'D': y[0] += UNIT_SIZE; break;
                case 'L': x[0] -= UNIT_SIZE; break;
                case 'R': x[0] += UNIT_SIZE; break;
            }
            animationStep = 0;
            fullStepCompleted = true;
        }

        for (int i = 0; i < bodyParts; i++) {
            double targetX = x[i];
            double targetY = y[i];
            double curX = renderX[i];
            double curY = renderY[i];
            renderX[i] = curX + (targetX - curX) * ((double) animationStep / ANIMATION_STEPS);
            renderY[i] = curY + (targetY - curY) * ((double) animationStep / ANIMATION_STEPS);
        }

        return fullStepCompleted;
    }

    /**
     * Spawns a small apple.
     * Guaranteed to spawn strictly below PLAY_AREA_MIN_Y so it NEVER interferes with HUD buttons!
     */
    public void newApple() {
        boolean validPosition;
        int maxTries = 300;
        int tries = 0;
        int minGridY = PLAY_AREA_MIN_Y / UNIT_SIZE;
        int maxGridY = SCREEN_HEIGHT / UNIT_SIZE;

        do {
            validPosition = true;
            appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
            appleY = (minGridY + random.nextInt(maxGridY - minGridY)) * UNIT_SIZE;
            Rectangle appleRect = new Rectangle(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

            for (Rectangle obstacle : obstacles) {
                if (obstacle.intersects(appleRect)) {
                    validPosition = false;
                    break;
                }
            }

            if (validPosition) {
                for (int i = 0; i < bodyParts; i++) {
                    if (x[i] == appleX && y[i] == appleY) {
                        validPosition = false;
                        break;
                    }
                }
            }
            tries++;
        } while (!validPosition && tries < maxTries);
    }

    /**
     * Spawns a big apple.
     * Guaranteed to spawn strictly below PLAY_AREA_MIN_Y!
     */
    public void newBigApple() {
        boolean validPosition;
        int maxTries = 300;
        int tries = 0;
        int minGridY = PLAY_AREA_MIN_Y / UNIT_SIZE;
        int maxGridY = (SCREEN_HEIGHT - BIG_APPLE_SIZE) / UNIT_SIZE;

        do {
            validPosition = true;
            bigAppleX = random.nextInt((SCREEN_WIDTH - BIG_APPLE_SIZE) / UNIT_SIZE) * UNIT_SIZE;
            bigAppleY = (minGridY + random.nextInt(Math.max(1, maxGridY - minGridY + 1))) * UNIT_SIZE;
            Rectangle bigAppleRect = new Rectangle(bigAppleX, bigAppleY, BIG_APPLE_SIZE, BIG_APPLE_SIZE);

            for (Rectangle obstacle : obstacles) {
                if (obstacle.intersects(bigAppleRect)) {
                    validPosition = false;
                    break;
                }
            }
            tries++;
        } while (!validPosition && tries < maxTries);

        bigAppleActive = true;
        bigAppleStartTime = System.currentTimeMillis();
    }

    /**
     * Spawns a special power-up item on the field.
     */
    public void spawnPowerUp() {
        int minGridY = PLAY_AREA_MIN_Y / UNIT_SIZE;
        int maxGridY = SCREEN_HEIGHT / UNIT_SIZE;
        int px = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        int py = (minGridY + random.nextInt(maxGridY - minGridY)) * UNIT_SIZE;

        PowerUpType[] types = PowerUpType.values();
        PowerUpType selected = types[random.nextInt(types.length)];

        activePowerUpItem = new PowerUp(selected, px, py);
    }

    public AppleResult checkApple() {
        long now = System.currentTimeMillis();

        // Expire active power-up effect
        if (activeEffect != null && now >= activeEffectEndTime) {
            activeEffect = null;
        }

        // Expire on-screen power-up item
        if (activePowerUpItem != null && activePowerUpItem.isExpired()) {
            activePowerUpItem = null;
        }

        // Check timeout for big apple
        if (bigAppleActive && (now - bigAppleStartTime >= BIG_APPLE_DURATION)) {
            bigAppleActive = false;
        }

        // Check power-up collection
        if (activePowerUpItem != null && x[0] == activePowerUpItem.getX() && y[0] == activePowerUpItem.getY()) {
            PowerUpType type = activePowerUpItem.getType();
            activePowerUpItem = null;
            applyPowerUp(type);
            spawnParticles(x[0] + UNIT_SIZE / 2, y[0] + UNIT_SIZE / 2, type.getColor());
            return AppleResult.POWER_UP_COLLECTED;
        }

        // Check small apple
        if (!bigAppleActive && x[0] == appleX && y[0] == appleY) {
            int tailX = x[bodyParts - 1];
            int tailY = y[bodyParts - 1];
            bodyParts++;
            
            // Combo calculation
            if (now - lastAppleEatTime <= COMBO_WINDOW_MS) {
                comboCount++;
            } else {
                comboCount = 1;
            }
            lastAppleEatTime = now;

            int pointsAwarded = 1 * (activeEffect == PowerUpType.DOUBLE_SCORE ? 2 : 1);
            if (comboCount > 1) {
                pointsAwarded += (comboCount - 1);
                showToast("\u26A1 COMBO x" + comboCount + "! +" + pointsAwarded + " PTS", new Color(255, 215, 0));
            }

            applesEaten += pointsAwarded;
            smallApplesCount++;
            totalSmallApplesEaten++;

            x[bodyParts - 1] = tailX;
            y[bodyParts - 1] = tailY;
            renderX[bodyParts - 1] = tailX;
            renderY[bodyParts - 1] = tailY;

            spawnParticles(appleX + UNIT_SIZE / 2, appleY + UNIT_SIZE / 2, new Color(255, 50, 50));
            newApple();

            // Spawn Big Apple
            if (smallApplesCount >= SMALL_APPLES_FOR_BIG) {
                newBigApple();
                smallApplesCount = 0;
            }

            // Occasionally spawn Power-Up
            if (applesEaten >= nextPowerUpThreshold && activePowerUpItem == null) {
                spawnPowerUp();
                nextPowerUpThreshold = applesEaten + 10 + random.nextInt(10);
            }

            checkHighScoreToast();
            return AppleResult.SMALL_APPLE_EATEN;
        }

        // Check big apple
        if (bigAppleActive) {
            Rectangle snakeHead = new Rectangle(x[0], y[0], UNIT_SIZE, UNIT_SIZE);
            Rectangle bigApple = new Rectangle(bigAppleX, bigAppleY, BIG_APPLE_SIZE, BIG_APPLE_SIZE);
            if (snakeHead.intersects(bigApple)) {
                int tailX = x[bodyParts - 1];
                int tailY = y[bodyParts - 1];
                bodyParts++;

                int points = 5 * (activeEffect == PowerUpType.DOUBLE_SCORE ? 2 : 1);
                applesEaten += points;
                showToast("\uD83C\uDF1F BIG BONUS! +" + points + " PTS", new Color(255, 140, 0));

                x[bodyParts - 1] = tailX;
                y[bodyParts - 1] = tailY;
                renderX[bodyParts - 1] = tailX;
                renderY[bodyParts - 1] = tailY;

                spawnParticles(bigAppleX + BIG_APPLE_SIZE / 2, bigAppleY + BIG_APPLE_SIZE / 2, new Color(255, 160, 0));
                bigAppleActive = false;

                checkHighScoreToast();
                return AppleResult.BIG_APPLE_EATEN;
            }
        }

        return AppleResult.NONE;
    }

    private void applyPowerUp(PowerUpType type) {
        if (type == PowerUpType.EXTRA_LIFE) {
            if (lives < MAX_LIVES) {
                lives++;
                showToast("\u2764 EXTRA LIFE! (" + lives + " Lives)", type.getColor());
            } else {
                applesEaten += 10;
                showToast("\u2764 MAX LIVES! +10 BONUS PTS", type.getColor());
            }
        } else {
            activeEffect = type;
            activeEffectEndTime = System.currentTimeMillis() + type.getDurationMs();
            showToast("\u2728 " + type.getDisplayName().toUpperCase() + ": " + type.getDescription(), type.getColor());
        }
    }

    public CollisionResult checkCollisions() {
        boolean collided = false;

        // Boundaries
        if (x[0] < 0 || x[0] >= SCREEN_WIDTH || y[0] < 0 || y[0] >= SCREEN_HEIGHT) {
            collided = true;
        }

        // Self collision
        if (!collided) {
            for (int i = bodyParts - 1; i > 0; i--) {
                if (x[0] == x[i] && y[0] == y[i]) {
                    collided = true;
                    break;
                }
            }
        }

        // Obstacle collision
        if (!collided) {
            for (Rectangle obstacle : obstacles) {
                if (obstacle.contains(x[0], y[0])) {
                    collided = true;
                    break;
                }
            }
        }

        if (collided) {
            // Shield protection: absorbs 1 hit!
            if (activeEffect == PowerUpType.SHIELD) {
                activeEffect = null;
                spawnParticles(x[0] + UNIT_SIZE / 2, y[0] + UNIT_SIZE / 2, new Color(0, 229, 255));
                showToast("\uD83D\uDEE1\uFE0F SHIELD SHATTERED! Collision Absorbed!", new Color(0, 229, 255));
                // Bounce back into valid boundary if outside
                if (x[0] < 0) x[0] = 0;
                if (x[0] >= SCREEN_WIDTH) x[0] = SCREEN_WIDTH - UNIT_SIZE;
                if (y[0] < PLAY_AREA_MIN_Y) y[0] = PLAY_AREA_MIN_Y;
                if (y[0] >= SCREEN_HEIGHT) y[0] = SCREEN_HEIGHT - UNIT_SIZE;
                return CollisionResult.SHIELD_SAVED;
            }

            if (gameMode == GameMode.CLASSIC) {
                lives--;
                if (lives <= 0) {
                    return CollisionResult.GAME_OVER;
                } else {
                    resetSnakePosition();
                    return CollisionResult.LIFE_LOST;
                }
            } else if (gameMode == GameMode.ENDLESS) {
                resetSnakePosition();
                return CollisionResult.LIFE_LOST;
            } else if (gameMode == GameMode.CHALLENGE) {
                return CollisionResult.GAME_OVER;
            }
        }

        return CollisionResult.NONE;
    }

    public boolean updateSpeed() {
        int threshold = (applesEaten / POINTS_PER_SPEED_INCREASE) * POINTS_PER_SPEED_INCREASE;
        if (applesEaten >= lastSpeedIncreaseScore + POINTS_PER_SPEED_INCREASE) {
            lastSpeedIncreaseScore = threshold;
            int newDelay = Math.max(MINIMUM_DELAY, currentDelay - DELAY_DECREMENT);
            if (newDelay != currentDelay) {
                currentDelay = newDelay;
                return true;
            }
        }
        return false;
    }

    public boolean shouldAdvanceLevel() {
        int nextLevelThreshold = currentLevel * POINTS_PER_LEVEL;
        return (applesEaten >= nextLevelThreshold && currentLevel < MAX_LEVEL);
    }

    /**
     * Advances to the next level smoothly:
     * Increases level, adds obstacles, safely removes any obstacle overlapping the snake,
     * and DOES NOT reset the snake onto out-of-bounds coordinates!
     */
    public void advanceToNextLevel() {
        if (currentLevel < MAX_LEVEL) {
            currentLevel++;
            generateObstacles(currentLevel);
            currentDelay = Math.max(MINIMUM_DELAY, baseDelay - (currentLevel - 1) * DELAY_DECREMENT);

            // Safely remove any obstacle that happens to overlap the snake's current position
            // so the player NEVER dies on level up!
            obstacles.removeIf(obs -> {
                for (int i = 0; i < bodyParts; i++) {
                    Rectangle seg = new Rectangle(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
                    if (obs.intersects(seg)) return true;
                }
                return false;
            });

            // Ensure apple is in a valid spot
            newApple();
        }
    }

    public void generateObstacles(int level) {
        obstacles.clear();
        if (level >= 2) {
            // Level 2 corners (below HUD)
            obstacles.add(new Rectangle(0, PLAY_AREA_MIN_Y, UNIT_SIZE * 2, UNIT_SIZE * 2));
            obstacles.add(new Rectangle(SCREEN_WIDTH - UNIT_SIZE * 2, SCREEN_HEIGHT - UNIT_SIZE * 2, UNIT_SIZE * 2, UNIT_SIZE * 2));
        }
        if (level >= 3) {
            // Center block
            int centerX = SCREEN_WIDTH / 2 - UNIT_SIZE;
            int centerY = SCREEN_HEIGHT / 2 - UNIT_SIZE;
            obstacles.add(new Rectangle(centerX, centerY, UNIT_SIZE * 2, UNIT_SIZE * 2));
        }
        if (level >= 4) {
            // Symmetric barriers
            obstacles.add(new Rectangle(UNIT_SIZE * 5, PLAY_AREA_MIN_Y + UNIT_SIZE * 2, UNIT_SIZE, UNIT_SIZE));
            obstacles.add(new Rectangle(UNIT_SIZE * 10, PLAY_AREA_MIN_Y + UNIT_SIZE * 5, UNIT_SIZE, UNIT_SIZE));
            obstacles.add(new Rectangle(UNIT_SIZE * 16, PLAY_AREA_MIN_Y + UNIT_SIZE * 2, UNIT_SIZE, UNIT_SIZE));
        }
        if (level >= 5) {
            // Side barriers
            int borderThickness = UNIT_SIZE;
            obstacles.add(new Rectangle(0, PLAY_AREA_MIN_Y, SCREEN_WIDTH, borderThickness));
            obstacles.add(new Rectangle(0, SCREEN_HEIGHT - borderThickness, SCREEN_WIDTH, borderThickness));
            obstacles.add(new Rectangle(0, PLAY_AREA_MIN_Y, borderThickness, SCREEN_HEIGHT - PLAY_AREA_MIN_Y));
            obstacles.add(new Rectangle(SCREEN_WIDTH - borderThickness, PLAY_AREA_MIN_Y, borderThickness, SCREEN_HEIGHT - PLAY_AREA_MIN_Y));
        }
    }

    private boolean isOnObstacle(int px, int py) {
        Rectangle r = new Rectangle(px, py, UNIT_SIZE, UNIT_SIZE);
        for (Rectangle obstacle : obstacles) {
            if (obstacle.intersects(r)) {
                return true;
            }
        }
        return false;
    }

    private void checkHighScoreToast() {
        if (!highScoreAnnouncedThisRun && applesEaten > scoreToBeat && scoreToBeat > 0) {
            highScoreAnnouncedThisRun = true;
            showToast("\u2605 NEW HIGH SCORE: " + applesEaten + "! \u2605", new Color(255, 215, 0));
        }
    }

    public void showToast(String message, Color color) {
        this.toastMessage = message;
        this.toastColor = color;
        this.toastStartTime = System.currentTimeMillis();
    }

    public String getToastMessage() {
        if (toastMessage == null) return null;
        if (System.currentTimeMillis() - toastStartTime >= TOAST_DURATION) {
            toastMessage = null;
            return null;
        }
        return toastMessage;
    }

    public float getToastAlpha() {
        if (toastMessage == null) return 0f;
        long elapsed = System.currentTimeMillis() - toastStartTime;
        if (elapsed >= TOAST_DURATION) return 0f;
        float alpha;
        if (elapsed < TOAST_FADE_MS) {
            alpha = elapsed / (float) TOAST_FADE_MS;
        } else if (elapsed > TOAST_DURATION - TOAST_FADE_MS) {
            alpha = (TOAST_DURATION - elapsed) / (float) TOAST_FADE_MS;
        } else {
            alpha = 1f;
        }
        return Math.max(0f, Math.min(1f, alpha));
    }

    public Color getToastColor() {
        return toastColor;
    }

    public void spawnParticles(int px, int py, Color color) {
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = random.nextDouble() * 3.5 + 1.2;
            double dx = Math.cos(angle) * speed;
            double dy = Math.sin(angle) * speed;
            particles.add(new Particle(px, py, dx, dy, color));
        }
    }

    public void updateParticles() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update();
            if (p.isDead()) {
                particles.remove(i);
            }
        }
    }

    // Power-up & Speed calculation
    public int getEffectiveDelay() {
        if (activeEffect == PowerUpType.SLOW_MO) {
            return (int) (currentDelay * 1.5); // 50% slower for precision control
        }
        return currentDelay;
    }

    public PowerUp getActivePowerUpItem() {
        return activePowerUpItem;
    }

    public PowerUpType getActiveEffect() {
        return activeEffect;
    }

    public int getActiveEffectRemainingSeconds() {
        if (activeEffect == null) return 0;
        long remainingMs = activeEffectEndTime - System.currentTimeMillis();
        return Math.max(0, (int) (remainingMs / 1000));
    }

    // Getters & Setters
    public int[] getX() { return x; }
    public int[] getY() { return y; }
    public double[] getRenderX() { return renderX; }
    public double[] getRenderY() { return renderY; }
    public int getBodyParts() { return bodyParts; }
    public int getApplesEaten() { return applesEaten; }
    public int getTotalSmallApplesEaten() { return totalSmallApplesEaten; }
    public int getLives() { return lives; }
    public int getCurrentLevel() { return currentLevel; }
    public int getStartingLevel() { return startingLevel; }
    public int getCurrentDelay() { return currentDelay; }
    public int getBaseDelay() { return baseDelay; }
    public void setBaseDelay(int baseDelay) { this.baseDelay = baseDelay; }
    public int getAppleX() { return appleX; }
    public int getAppleY() { return appleY; }
    public boolean isBigAppleActive() { return bigAppleActive; }
    public int getBigAppleX() { return bigAppleX; }
    public int getBigAppleY() { return bigAppleY; }
    public long getBigAppleStartTime() { return bigAppleStartTime; }
    public char getDirection() { return direction; }
    public List<Rectangle> getObstacles() { return Collections.unmodifiableList(obstacles); }
    public List<Particle> getParticles() { return Collections.unmodifiableList(particles); }
    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode mode) { this.gameMode = mode; }
}
