package logic;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Core game engine encapsulating grid physics, snake movement,
 * collision detection, apple spawning, obstacles, and particle updates.
 */
public class SnakeEngine {
    public static final int SCREEN_WIDTH = 1300;
    public static final int SCREEN_HEIGHT = 750;
    public static final int UNIT_SIZE = 50;
    public static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    public static final int ANIMATION_STEPS = 5;
    public static final int MINIMUM_DELAY = 60;
    public static final int DELAY_DECREMENT = 15;
    public static final int POINTS_PER_SPEED_INCREASE = 15;
    public static final int POINTS_PER_LEVEL = 35;
    public static final int MAX_LEVEL = 5;
    public static final int INITIAL_LIVES = 3;
    public static final int BIG_APPLE_DURATION = 5000;
    public static final int BIG_APPLE_SIZE = UNIT_SIZE * 2;
    public static final int SMALL_APPLES_FOR_BIG = 5;

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
        LIFE_LOST,
        GAME_OVER
    }

    public enum AppleResult {
        NONE,
        SMALL_APPLE_EATEN,
        BIG_APPLE_EATEN
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
        int startX = SCREEN_WIDTH / 2 - (bodyParts * UNIT_SIZE) / 2;
        int startY = SCREEN_HEIGHT / 2;
        startX = (startX / UNIT_SIZE) * UNIT_SIZE;
        startY = (startY / UNIT_SIZE) * UNIT_SIZE;

        while (isOnObstacle(startX, startY)) {
            startX += UNIT_SIZE;
            if (startX >= SCREEN_WIDTH - bodyParts * UNIT_SIZE) {
                startX = UNIT_SIZE;
                startY += UNIT_SIZE;
            }
            if (startY >= SCREEN_HEIGHT) {
                startY = UNIT_SIZE;
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
        // Prevent 180-degree reversal against committed direction
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

    /**
     * Updates interpolation step. When reaching ANIMATION_STEPS, advances grid coordinates.
     * Returns true if a full grid step was completed.
     */
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

    public void newApple() {
        boolean validPosition;
        int maxTries = 200;
        int tries = 0;
        do {
            validPosition = true;
            appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
            appleY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
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

    public void newBigApple() {
        boolean validPosition;
        int maxTries = 200;
        int tries = 0;
        do {
            validPosition = true;
            bigAppleX = random.nextInt((SCREEN_WIDTH - BIG_APPLE_SIZE) / UNIT_SIZE) * UNIT_SIZE;
            bigAppleY = random.nextInt((SCREEN_HEIGHT - BIG_APPLE_SIZE) / UNIT_SIZE) * UNIT_SIZE;
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

    public AppleResult checkApple() {
        // Check timeout for big apple
        if (bigAppleActive && (System.currentTimeMillis() - bigAppleStartTime >= BIG_APPLE_DURATION)) {
            bigAppleActive = false;
        }

        // Check small apple
        if (!bigAppleActive && x[0] == appleX && y[0] == appleY) {
            int tailX = x[bodyParts - 1];
            int tailY = y[bodyParts - 1];
            bodyParts++;
            applesEaten++;
            smallApplesCount++;
            totalSmallApplesEaten++;

            x[bodyParts - 1] = tailX;
            y[bodyParts - 1] = tailY;
            renderX[bodyParts - 1] = tailX;
            renderY[bodyParts - 1] = tailY;

            spawnParticles(appleX + UNIT_SIZE / 2, appleY + UNIT_SIZE / 2, new Color(255, 50, 50));
            newApple();

            if (smallApplesCount >= SMALL_APPLES_FOR_BIG) {
                newBigApple();
                smallApplesCount = 0;
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
                applesEaten += 5;

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

    public void advanceToNextLevel() {
        if (currentLevel < MAX_LEVEL) {
            currentLevel++;
            generateObstacles(currentLevel);
            currentDelay = Math.max(MINIMUM_DELAY, baseDelay - (currentLevel - 1) * DELAY_DECREMENT);
            resetSnakePosition();
        }
    }

    public void generateObstacles(int level) {
        obstacles.clear();
        if (level >= 2) {
            obstacles.add(new Rectangle(0, 0, UNIT_SIZE * 2, UNIT_SIZE * 2));
            obstacles.add(new Rectangle(SCREEN_WIDTH - UNIT_SIZE * 2, SCREEN_HEIGHT - UNIT_SIZE * 2, UNIT_SIZE * 2, UNIT_SIZE * 2));
        }
        if (level >= 3) {
            int centerX = SCREEN_WIDTH / 2 - UNIT_SIZE;
            int centerY = SCREEN_HEIGHT / 2 - UNIT_SIZE;
            obstacles.add(new Rectangle(centerX, centerY, UNIT_SIZE * 2, UNIT_SIZE * 2));
        }
        if (level >= 4) {
            obstacles.add(new Rectangle(UNIT_SIZE * 5, UNIT_SIZE * 5, UNIT_SIZE, UNIT_SIZE));
            obstacles.add(new Rectangle(UNIT_SIZE * 10, UNIT_SIZE * 10, UNIT_SIZE, UNIT_SIZE));
            obstacles.add(new Rectangle(UNIT_SIZE * 15, UNIT_SIZE * 15, UNIT_SIZE, UNIT_SIZE));
        }
        if (level >= 5) {
            int borderThickness = UNIT_SIZE;
            obstacles.add(new Rectangle(0, 0, SCREEN_WIDTH, borderThickness));
            obstacles.add(new Rectangle(0, SCREEN_HEIGHT - borderThickness, SCREEN_WIDTH, borderThickness));
            obstacles.add(new Rectangle(0, 0, borderThickness, SCREEN_HEIGHT));
            obstacles.add(new Rectangle(SCREEN_WIDTH - borderThickness, 0, borderThickness, SCREEN_HEIGHT));
        }
    }

    private boolean isOnObstacle(int px, int py) {
        for (Rectangle obstacle : obstacles) {
            if (obstacle.contains(px, py)) {
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
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = random.nextDouble() * 3 + 1;
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
