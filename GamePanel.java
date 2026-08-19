import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import javax.swing.*;

public class GamePanel extends JPanel implements ActionListener {
    static final int SCREEN_WIDTH = 1300;
    static final int SCREEN_HEIGHT = 750;
    static final int UNIT_SIZE = 50;
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    static final int INITIAL_DELAY = 200;
    static final int DELAY_DECREMENT = 20;
    static final int MINIMUM_DELAY = 80;
    static final int POINTS_PER_SPEED_INCREASE = 15;
    static final int POINTS_PER_LEVEL = 35;
    static final int MAX_LEVEL = 5;
    static final int INITIAL_LIVES = 3;
    private int currentDelay = INITIAL_DELAY;
    private int lastSpeedIncreaseScore = 0;
    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private final double[] renderX = new double[GAME_UNITS];
    private final double[] renderY = new double[GAME_UNITS];
    private int bodyParts = 6;
    private int applesEaten;
    private int smallApplesCount; // For spawning big apples
    private int totalSmallApplesEaten; // For tracking challenge progress
    private int highScore = 0;
    private int lives = INITIAL_LIVES;
    private int appleX;
    private int appleY;
    private boolean bigAppleActive = false;
    private int bigAppleX;
    private int bigAppleY;
    private long bigAppleStartTime;
    private static final int BIG_APPLE_DURATION = 3000;
    private static final int BIG_APPLE_SIZE = UNIT_SIZE * 2;
    private static final int SMALL_APPLES_FOR_BIG = 5;
    private char direction = 'R';
    private char nextDirection = 'R'; // buffered input; committed once per grid step
    private boolean running = false;
    private boolean paused = false;
    private Timer timer;
    private final Random random = new Random();
    private JButton restartButton;
    private JButton leaderboardButton;
    private JButton pauseButton;
    private JButton settingsButton;
    private JButton exitButton;
    private ArrayList<ScoreEntry> leaderboard = new ArrayList<>();
    private Color snakeHeadColor = Color.green;
    private Color snakeBodyColor = new Color(45, 180, 0);
    private Color backgroundColor = Color.black;
    private int currentLevel = 1;
    private int startingLevel = 1;
    private ArrayList<Rectangle> obstacles = new ArrayList<>();
    private enum GameMode { CLASSIC, ENDLESS, CHALLENGE }
    private GameMode gameMode = GameMode.CLASSIC;
    private int challengeIndex = 0;
    private long challengeStartTime;
    private boolean challengeActive = false;
    private String[] challenges = {
        "Collect 10 small apples in 30 seconds",
        "Survive for 30 seconds without hitting obstacles",
        "Eat 2 big apples in 20 seconds"
    };
    private int[] challengeGoals = {10, 30, 2};
    private int[] challengeTimeLimits = {30, 30, 20};
    private int challengeProgress = 0;
    private static final int ANIMATION_STEPS = 5;
    private int animationStep = 0;
    private double stepSize = (double) UNIT_SIZE / ANIMATION_STEPS;
    private ArrayList<Particle> particles = new ArrayList<>();

    // --- Persistent leaderboard (survives quitting the game) ---
    // Stored under the user's home folder so it works regardless of where the app is launched from.
    private static final String SCORES_DIR = System.getProperty("user.home") + File.separator + ".snakegame";
    private static final String SCORES_FILE = SCORES_DIR + File.separator + "leaderboard.txt";

    // --- Non-blocking "new high score" toast (drawn on screen, never pauses the game) ---
    private String toastMessage = null;
    private Color toastColor = new Color(255, 215, 0);
    private long toastStartTime;
    private static final int TOAST_DURATION = 3500;
    private static final int TOAST_FADE_MS = 400;
    private int scoreToBeat = 0;
    private boolean highScoreAnnouncedThisRun = false;

    private static class ScoreEntry {
        String name;
        int score;

        ScoreEntry(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    private static class Particle {
        double x, y;
        double dx, dy;
        double alpha;
        Color color;

        Particle(double x, double y, double dx, double dy, Color color) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.alpha = 1.0;
            this.color = color;
        }

        void update() {
            x += dx;
            y += dy;
            alpha -= 0.05;
        }

        void draw(Graphics g) {
            if (alpha <= 0) return;
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
            g2d.fillOval((int) x, (int) y, 5, 5);
        }
    }

    public GamePanel() {
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(backgroundColor);
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());
        loadLeaderboard();
        initButtons();
        showInstructions();
        startGame();
    }

    /** Reads the saved leaderboard from disk (if any) so scores survive quitting the app. */
    private void loadLeaderboard() {
        leaderboard.clear();
        File file = new File(SCORES_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int sep = line.lastIndexOf('|');
                if (sep == -1) {
                    continue;
                }
                String name = line.substring(0, sep).trim();
                try {
                    int score = Integer.parseInt(line.substring(sep + 1).trim());
                    if (!name.isEmpty()) {
                        leaderboard.add(new ScoreEntry(name, score));
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed lines rather than crashing the game.
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load leaderboard from " + SCORES_FILE + ": " + e.getMessage());
        }
        Collections.sort(leaderboard, Comparator.comparingInt((ScoreEntry en) -> en.score).reversed());
        if (leaderboard.size() > 10) {
            leaderboard.subList(10, leaderboard.size()).clear();
        }
        highScore = leaderboard.isEmpty() ? 0 : leaderboard.get(0).score;
    }

    /** Writes the current leaderboard to disk so it's there next time the game is launched. */
    private void saveLeaderboard() {
        File dir = new File(SCORES_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Could not create score directory: " + SCORES_DIR);
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SCORES_FILE))) {
            for (ScoreEntry entry : leaderboard) {
                String safeName = entry.name.replace("|", "/").replace("\n", " ").replace("\r", " ");
                writer.write(safeName + "|" + entry.score);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save leaderboard to " + SCORES_FILE + ": " + e.getMessage());
        }
    }

    private void initButtons() {
        restartButton = new JButton("Restart");
        restartButton.setFont(new Font("Ink Free", Font.BOLD, 30));
        restartButton.setForeground(Color.red);
        restartButton.setBackground(backgroundColor);
        restartButton.setFocusPainted(false);
        restartButton.setVisible(false);
        restartButton.addActionListener(e -> restartGame());
        this.setLayout(null);
        restartButton.setBounds(SCREEN_WIDTH/2 - 100, SCREEN_HEIGHT/2 + 50, 200, 50);
        this.add(restartButton);

        leaderboardButton = new JButton("Leaderboard");
        leaderboardButton.setFont(new Font("Ink Free", Font.BOLD, 30));
        leaderboardButton.setForeground(Color.red);
        leaderboardButton.setBackground(backgroundColor);
        leaderboardButton.setFocusPainted(false);
        leaderboardButton.setVisible(false);
        leaderboardButton.addActionListener(e -> showLeaderboard());
        leaderboardButton.setBounds(SCREEN_WIDTH/2 - 100, SCREEN_HEIGHT/2 + 120, 200, 50);
        this.add(leaderboardButton);

        pauseButton = new JButton("Pause");
        pauseButton.setFont(new Font("Ink Free", Font.BOLD, 20));
        pauseButton.setForeground(Color.red);
        pauseButton.setBackground(backgroundColor);
        pauseButton.setFocusPainted(false);
        pauseButton.addActionListener(e -> togglePause());
        pauseButton.setBounds(SCREEN_WIDTH - 120, 10, 100, 40);
        this.add(pauseButton);

        settingsButton = new JButton("Settings");
        settingsButton.setFont(new Font("Ink Free", Font.BOLD, 20));
        settingsButton.setForeground(Color.red);
        settingsButton.setBackground(backgroundColor);
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> openSettings());
        settingsButton.setBounds(SCREEN_WIDTH - 240, 10, 100, 40);
        this.add(settingsButton);

        exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Ink Free", Font.BOLD, 20));
        exitButton.setForeground(Color.red);
        exitButton.setBackground(backgroundColor);
        exitButton.setFocusPainted(false);
        exitButton.addActionListener(e -> System.exit(0));
        exitButton.setBounds(SCREEN_WIDTH - 360, 10, 100, 40);
        this.add(exitButton);
    }

    private void showInstructions() {
        JOptionPane.showMessageDialog(this, 
            "Welcome to Snake!\n\n" +
            "Instructions:\n" +
            "- Use arrow keys to move the snake.\n" +
            "- Eat small apples (1 point each) to grow.\n" +
            "- After 5 small apples, a big apple (3 points) appears for 3 seconds.\n" +
            "- Snake speed increases every 15 points!\n" +
            "- Advance to the next level every 35 points (up to Level 5)!\n" +
            "- After Level 5, continue playing until game over (Classic Mode).\n" +
            "- Classic Mode: 3 lives; lose a life on collision, game over when all lives are lost.\n" +
            "- Endless Mode: No game over; reset on collision and keep scoring.\n" +
            "- Challenge Mode: Complete objectives to progress (e.g., collect apples, survive time).\n" +
            "- Game pauses on level-up; press SPACEBAR or click Resume to continue.\n" +
            "- Watch out for obstacles that increase with each level!\n" +
            "- Press SPACEBAR to pause/unpause the game.\n" +
            "- Click the Settings button to change colors, starting level, and game mode.\n" +
            "- Avoid hitting the walls, obstacles, or yourself (except in Endless Mode)!\n\n" +
            "Click OK to start playing!",
            "Snake Game Instructions", 
            JOptionPane.INFORMATION_MESSAGE);
        this.requestFocusInWindow();
    }

    private void openSettings() {
        boolean wasPaused = paused;
        if (running && !paused) {
            togglePause();
        }

        JDialog settingsDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Settings", true);
        settingsDialog.setLayout(new GridLayout(6, 1, 10, 10));
        settingsDialog.setSize(300, 300);
        settingsDialog.setLocationRelativeTo(this);

        JButton headColorButton = new JButton("Change Snake Head Color");
        headColorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(settingsDialog, "Choose Snake Head Color", snakeHeadColor);
            if (newColor != null) {
                snakeHeadColor = newColor;
                repaint();
            }
        });

        JButton bodyColorButton = new JButton("Change Snake Body Color");
        bodyColorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(settingsDialog, "Choose Snake Body Color", snakeBodyColor);
            if (newColor != null) {
                snakeBodyColor = newColor;
                repaint();
            }
        });

        JButton bgColorButton = new JButton("Change Background Color");
        bgColorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(settingsDialog, "Choose Background Color", backgroundColor);
            if (newColor != null) {
                backgroundColor = newColor;
                this.setBackground(backgroundColor);
                restartButton.setBackground(backgroundColor);
                leaderboardButton.setBackground(backgroundColor);
                pauseButton.setBackground(backgroundColor);
                settingsButton.setBackground(backgroundColor);
                exitButton.setBackground(backgroundColor);
                repaint();
            }
        });

        JButton levelButton = new JButton("Choose Starting Level");
        levelButton.addActionListener(e -> {
            String[] levels = {"1", "2", "3", "4", "5"};
            String selectedLevel = (String) JOptionPane.showInputDialog(
                settingsDialog,
                "Select starting level (1-5):",
                "Choose Starting Level",
                JOptionPane.PLAIN_MESSAGE,
                null,
                levels,
                String.valueOf(startingLevel)
            );
            if (selectedLevel != null) {
                startingLevel = Integer.parseInt(selectedLevel);
                bodyParts = 6;
                applesEaten = 0;
                lives = INITIAL_LIVES;
                direction = 'R';
                nextDirection = 'R';
                setSafeStartingPosition();
                currentLevel = startingLevel;
                obstacles.clear();
                generateObstacles(currentLevel);
                currentDelay = Math.max(MINIMUM_DELAY, INITIAL_DELAY - (currentLevel - 1) * DELAY_DECREMENT);
                newApple();
                smallApplesCount = 0;
                totalSmallApplesEaten = 0;
                bigAppleActive = false;
                lastSpeedIncreaseScore = 0;
                challengeIndex = 0;
                challengeActive = false;
                challengeProgress = 0;
                if (timer != null) {
                    timer.stop();
                }
                timer = new Timer(currentDelay / ANIMATION_STEPS, this);
                repaint();
            }
        });

        JButton modeButton = new JButton("Choose Game Mode");
        modeButton.addActionListener(e -> {
            String[] modes = {"Classic", "Endless", "Challenge"};
            String selectedMode = (String) JOptionPane.showInputDialog(
                settingsDialog,
                "Select game mode:",
                "Choose Game Mode",
                JOptionPane.PLAIN_MESSAGE,
                null,
                modes,
                gameMode.toString()
            );
            if (selectedMode != null) {
                gameMode = GameMode.valueOf(selectedMode.toUpperCase());
                bodyParts = 6;
                applesEaten = 0;
                lives = INITIAL_LIVES;
                direction = 'R';
                nextDirection = 'R';
                setSafeStartingPosition();
                currentLevel = startingLevel;
                obstacles.clear();
                generateObstacles(currentLevel);
                currentDelay = Math.max(MINIMUM_DELAY, INITIAL_DELAY - (currentLevel - 1) * DELAY_DECREMENT);
                newApple();
                smallApplesCount = 0;
                totalSmallApplesEaten = 0;
                bigAppleActive = false;
                lastSpeedIncreaseScore = 0;
                challengeIndex = 0;
                challengeActive = false;
                challengeProgress = 0;
                if (timer != null) {
                    timer.stop();
                }
                timer = new Timer(currentDelay / ANIMATION_STEPS, this);
                repaint();
            }
        });

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> settingsDialog.dispose());

        settingsDialog.add(headColorButton);
        settingsDialog.add(bodyColorButton);
        settingsDialog.add(bgColorButton);
        settingsDialog.add(levelButton);
        settingsDialog.add(modeButton);
        settingsDialog.add(closeButton);
        settingsDialog.setVisible(true);

        this.requestFocusInWindow();

        if (running && !wasPaused) {
            togglePause();
        }
    }

    private void setSafeStartingPosition() {
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

    public void startGame() {
        setSafeStartingPosition();
        currentLevel = startingLevel;
        obstacles.clear();
        generateObstacles(currentLevel);
        currentDelay = Math.max(MINIMUM_DELAY, INITIAL_DELAY - (currentLevel - 1) * DELAY_DECREMENT);
        newApple();
        running = true;
        paused = false;
        smallApplesCount = 0;
        totalSmallApplesEaten = 0;
        bigAppleActive = false;
        applesEaten = 0;
        lives = INITIAL_LIVES;
        lastSpeedIncreaseScore = 0;
        challengeIndex = 0;
        challengeActive = false;
        challengeProgress = 0;
        animationStep = 0;
        particles.clear();
        toastMessage = null;
        highScoreAnnouncedThisRun = false;
        scoreToBeat = leaderboard.isEmpty() ? 0 : leaderboard.get(0).score;
        if (timer != null) {
            timer.stop();
        }
        timer = new Timer(currentDelay / ANIMATION_STEPS, this);
        timer.start();
        restartButton.setVisible(false);
        leaderboardButton.setVisible(false);
        pauseButton.setText("Pause");
        this.requestFocusInWindow();
    }

    private void restartGame() {
        bodyParts = 6;
        applesEaten = 0;
        lives = INITIAL_LIVES;
        direction = 'R';
        nextDirection = 'R';
        startGame();
    }

    private void togglePause() {
        if (running) {
            paused = !paused;
            if (paused) {
                timer.stop();
                pauseButton.setText("Resume");
            } else {
                timer.start();
                pauseButton.setText("Pause");
            }
            repaint();
            this.requestFocusInWindow();
        }
    }

    private void updateSpeed() {
        int scoreThreshold = (applesEaten / POINTS_PER_SPEED_INCREASE) * POINTS_PER_SPEED_INCREASE;
        if (applesEaten >= lastSpeedIncreaseScore + POINTS_PER_SPEED_INCREASE) {
            lastSpeedIncreaseScore = scoreThreshold;
            currentDelay = Math.max(MINIMUM_DELAY, currentDelay - DELAY_DECREMENT);
            timer.stop();
            timer = new Timer(currentDelay / ANIMATION_STEPS, this);
            timer.start();
        }
    }

    private void generateObstacles(int level) {
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

    private void advanceToNextLevel() {
        if (currentLevel < MAX_LEVEL) {
            currentLevel++;
            generateObstacles(currentLevel);
            currentDelay = Math.max(MINIMUM_DELAY, INITIAL_DELAY - (currentLevel - 1) * DELAY_DECREMENT);
            timer.stop();
            timer = new Timer(currentDelay / ANIMATION_STEPS, this);
            paused = true;
            pauseButton.setText("Resume");
            JOptionPane.showMessageDialog(this, "Level Up! Now on Level " + currentLevel + "\nPress SPACEBAR or click Resume to continue.");
            repaint();
            this.requestFocusInWindow();
        }
    }

    private void startChallenge() {
        if (challengeIndex >= challenges.length) {
            running = false;
            timer.stop();
            JOptionPane.showMessageDialog(this, "Congratulations! You completed all challenges!\nFinal Score: " + applesEaten);
            promptForName();
            restartButton.setVisible(true);
            leaderboardButton.setVisible(true);
            return;
        }
        challengeActive = true;
        challengeStartTime = System.currentTimeMillis();
        challengeProgress = 0;
        totalSmallApplesEaten = 0; // Reset for the new challenge
        smallApplesCount = 0;
        JOptionPane.showMessageDialog(this, "Challenge " + (challengeIndex + 1) + ": " + challenges[challengeIndex]);
        this.requestFocusInWindow();
    }

    private void updateChallenge() {
        if (!challengeActive) return;
        long elapsedTime = (System.currentTimeMillis() - challengeStartTime) / 1000;
        int timeLimit = challengeTimeLimits[challengeIndex];
        if (elapsedTime >= timeLimit) {
            running = false;
            timer.stop();
            JOptionPane.showMessageDialog(this, "Challenge Failed! Time's up.");
            promptForName();
            restartButton.setVisible(true);
            leaderboardButton.setVisible(true);
            return;
        }

        boolean challengeCompleted = false;
        switch (challengeIndex) {
            case 0: // Collect 10 small apples
                challengeProgress = totalSmallApplesEaten;
                if (challengeProgress >= challengeGoals[0]) {
                    challengeCompleted = true;
                }
                break;
            case 1: // Survive for 30 seconds
                challengeProgress = (int) elapsedTime;
                if (challengeProgress >= challengeGoals[1]) {
                    challengeCompleted = true;
                }
                break;
            case 2: // Eat 2 big apples
                if (challengeProgress >= challengeGoals[2]) {
                    challengeCompleted = true;
                }
                break;
        }

        if (challengeCompleted) {
            challengeIndex++;
            challengeActive = false;
            JOptionPane.showMessageDialog(this, "Challenge Completed!");
            startChallenge();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (running) {
            drawBackground(g2d);

            // Obstacles: soft gradient block with a lighter inset so they
            // read as solid "walls" rather than flat gray rectangles.
            for (Rectangle obstacle : obstacles) {
                GradientPaint obstacleGradient = new GradientPaint(
                    obstacle.x, obstacle.y, new Color(110, 110, 120),
                    obstacle.x, obstacle.y + obstacle.height, new Color(60, 60, 68));
                g2d.setPaint(obstacleGradient);
                g2d.fillRect(obstacle.x, obstacle.y, obstacle.width, obstacle.height);
                g2d.setColor(new Color(150, 150, 160, 120));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect(obstacle.x + 1, obstacle.y + 1, obstacle.width - 2, obstacle.height - 2);
            }

            if (!bigAppleActive) {
                drawApple(g2d, appleX, appleY, UNIT_SIZE, new Color(220, 30, 30));
            } else {
                drawApple(g2d, bigAppleX, bigAppleY, BIG_APPLE_SIZE, new Color(255, 140, 0));
            }

            // Drop shadow pass first (drawn behind the whole snake for a bit of depth)
            g2d.setColor(new Color(0, 0, 0, 70));
            for (int i = 0; i < bodyParts; i++) {
                int sx = (int) renderX[i] + 4;
                int sy = (int) renderY[i] + 6;
                g2d.fillRoundRect(sx, sy, UNIT_SIZE, UNIT_SIZE, UNIT_SIZE / 2, UNIT_SIZE / 2);
            }

            // Draw snake with enhanced graphics
            for (int i = 0; i < bodyParts; i++) {
                int posX = (int) renderX[i];
                int posY = (int) renderY[i];

                // Gradient paint for head and body
                Color startColor = (i == 0) ? snakeHeadColor : snakeBodyColor;
                Color endColor = startColor.darker();
                GradientPaint gradient = new GradientPaint(posX, posY, startColor, posX + UNIT_SIZE, posY + UNIT_SIZE, endColor);
                g2d.setPaint(gradient);

                // Draw rounded rectangles
                int arcSize = UNIT_SIZE / 2; // For rounded corners
                g2d.fillRoundRect(posX, posY, UNIT_SIZE, UNIT_SIZE, arcSize, arcSize);
                g2d.setColor(new Color(255, 255, 255, 40));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(posX + 1, posY + 1, UNIT_SIZE - 2, UNIT_SIZE - 2, arcSize, arcSize);

                // Draw body pattern (scales)
                if (i != 0) { // Skip head
                    g2d.setColor(endColor.brighter());
                    g2d.setStroke(new BasicStroke(2));
                    // Draw a diagonal line to simulate a scale
                    g2d.drawLine(posX + 5, posY + UNIT_SIZE - 5, posX + UNIT_SIZE - 5, posY + 5);
                }

                // Draw eyes on the head
                if (i == 0) {
                    g2d.setColor(Color.white);
                    int eyeSize = UNIT_SIZE / 4;
                    int pupilSize = eyeSize / 2;
                    int offset = UNIT_SIZE / 4;

                    // Adjust eye positions based on direction
                    int leftEyeX = posX, leftEyeY = posY, rightEyeX = posX, rightEyeY = posY;
                    int leftPupilX = posX, leftPupilY = posY, rightPupilX = posX, rightPupilY = posY;

                    switch (direction) {
                        case 'R':
                            leftEyeX = posX + UNIT_SIZE - offset - eyeSize;
                            leftEyeY = posY + offset;
                            rightEyeX = posX + UNIT_SIZE - offset - eyeSize;
                            rightEyeY = posY + UNIT_SIZE - offset - eyeSize;
                            leftPupilX = leftEyeX + eyeSize - pupilSize;
                            leftPupilY = leftEyeY + pupilSize / 2;
                            rightPupilX = rightEyeX + eyeSize - pupilSize;
                            rightPupilY = rightEyeY + pupilSize / 2;
                            break;
                        case 'L':
                            leftEyeX = posX + offset;
                            leftEyeY = posY + offset;
                            rightEyeX = posX + offset;
                            rightEyeY = posY + UNIT_SIZE - offset - eyeSize;
                            leftPupilX = leftEyeX;
                            leftPupilY = leftEyeY + pupilSize / 2;
                            rightPupilX = rightEyeX;
                            rightPupilY = rightEyeY + pupilSize / 2;
                            break;
                        case 'U':
                            leftEyeX = posX + offset;
                            leftEyeY = posY + offset;
                            rightEyeX = posX + UNIT_SIZE - offset - eyeSize;
                            rightEyeY = posY + offset;
                            leftPupilX = leftEyeX + pupilSize / 2;
                            leftPupilY = leftEyeY;
                            rightPupilX = rightEyeX + pupilSize / 2;
                            rightPupilY = rightEyeY;
                            break;
                        case 'D':
                            leftEyeX = posX + offset;
                            leftEyeY = posY + UNIT_SIZE - offset - eyeSize;
                            rightEyeX = posX + UNIT_SIZE - offset - eyeSize;
                            rightEyeY = posY + UNIT_SIZE - offset - eyeSize;
                            leftPupilX = leftEyeX + pupilSize / 2;
                            leftPupilY = leftEyeY + eyeSize - pupilSize;
                            rightPupilX = rightEyeX + pupilSize / 2;
                            rightPupilY = rightEyeY + eyeSize - pupilSize;
                            break;
                    }

                    // Draw eyes
                    g2d.fillOval(leftEyeX, leftEyeY, eyeSize, eyeSize);
                    g2d.fillOval(rightEyeX, rightEyeY, eyeSize, eyeSize);

                    // Draw pupils
                    g2d.setColor(Color.black);
                    g2d.fillOval(leftPupilX, leftPupilY, pupilSize, pupilSize);
                    g2d.fillOval(rightPupilX, rightPupilY, pupilSize, pupilSize);
                }
            }

            for (Particle particle : particles) {
                particle.draw(g2d);
            }

            drawHud(g2d);
            drawToast(g2d);

            if (paused) {
                drawOverlayPanel(g2d, "Paused", "Press SPACEBAR or click Resume to continue");
            }
        } else {
            gameOver(g2d);
        }
    }

    /** Soft dark gradient background with a faint grid, replacing the flat black fill. */
    private void drawBackground(Graphics2D g2d) {
        GradientPaint bgGradient = new GradientPaint(
            0, 0, backgroundColor.brighter(),
            0, SCREEN_HEIGHT, backgroundColor.darker().darker());
        g2d.setPaint(bgGradient);
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2d.setColor(new Color(255, 255, 255, 14));
        g2d.setStroke(new BasicStroke(1));
        for (int gx = 0; gx <= SCREEN_WIDTH; gx += UNIT_SIZE) {
            g2d.drawLine(gx, 0, gx, SCREEN_HEIGHT);
        }
        for (int gy = 0; gy <= SCREEN_HEIGHT; gy += UNIT_SIZE) {
            g2d.drawLine(0, gy, SCREEN_WIDTH, gy);
        }
    }

    /** Apple with a radial-style highlight, gradient body, and a little stem/leaf for polish. */
    private void drawApple(Graphics2D g2d, int ax, int ay, int size, Color baseColor) {
        int cx = ax + size / 2;
        int cy = ay + size / 2;

        // Soft glow so the apple pops against the background.
        g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 60));
        g2d.fillOval(ax - size / 6, ay - size / 6, (int) (size * 1.33), (int) (size * 1.33));

        RadialGradientPaint appleGradient = new RadialGradientPaint(
            new Point2D.Float(cx - size / 6f, cy - size / 6f), size * 0.75f,
            new float[] {0f, 1f},
            new Color[] {baseColor.brighter(), baseColor.darker()});
        g2d.setPaint(appleGradient);
        g2d.fillOval(ax, ay, size, size);

        // Stem
        g2d.setColor(new Color(90, 55, 20));
        g2d.setStroke(new BasicStroke(Math.max(2, size / 16f)));
        g2d.drawLine(cx, ay, cx + size / 10, ay - size / 6);

        // Leaf
        g2d.setColor(new Color(60, 170, 70));
        int leafW = size / 3, leafH = size / 5;
        g2d.fillOval(cx + size / 12, ay - size / 8, leafW, leafH);

        // Shine highlight
        g2d.setColor(new Color(255, 255, 255, 130));
        int shineSize = size / 4;
        g2d.fillOval(ax + size / 5, ay + size / 6, shineSize, shineSize);
    }

    /** Heads-up display drawn on a translucent bar for legibility over any background color. */
    private void drawHud(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 110));
        g2d.fillRoundRect(0, 0, SCREEN_WIDTH, 100, 0, 0);

        g2d.setColor(new Color(255, 60, 60));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 34));
        FontMetrics metrics = getFontMetrics(g2d.getFont());
        String scoreText = "Score: " + applesEaten + "   High Score: " + highScore;
        g2d.drawString(scoreText, (SCREEN_WIDTH - metrics.stringWidth(scoreText)) / 2, 45);

        g2d.setColor(Color.white);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2d.drawString("Level: " + currentLevel, 15, 30);
        g2d.drawString("Lives: " + lives, 15, 58);
        g2d.drawString("Mode: " + gameMode, 15, 86);

        if (gameMode == GameMode.CHALLENGE && challengeActive) {
            long elapsedTime = (System.currentTimeMillis() - challengeStartTime) / 1000;
            int timeLeft = challengeTimeLimits[challengeIndex] - (int) elapsedTime;
            g2d.setColor(new Color(0, 0, 0, 110));
            g2d.fillRoundRect(10, 106, 420, 90, 12, 12);
            g2d.setColor(Color.white);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2d.drawString("Challenge: " + challenges[challengeIndex], 20, 128);
            g2d.drawString("Progress: " + challengeProgress + "/" + challengeGoals[challengeIndex], 20, 154);
            g2d.drawString("Time Left: " + timeLeft + "s", 20, 180);
        }
    }

    /** Centered translucent panel used for the Paused overlay. */
    private void drawOverlayPanel(Graphics2D g2d, String title, String subtitle) {
        g2d.setColor(new Color(0, 0, 0, 140));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        int panelW = 520, panelH = 180;
        int panelX = (SCREEN_WIDTH - panelW) / 2;
        int panelY = (SCREEN_HEIGHT - panelH) / 2;
        g2d.setColor(new Color(25, 25, 25, 220));
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 24, 24);
        g2d.setColor(new Color(255, 255, 255, 60));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 24, 24);

        g2d.setColor(Color.white);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 60));
        FontMetrics metrics = getFontMetrics(g2d.getFont());
        g2d.drawString(title, (SCREEN_WIDTH - metrics.stringWidth(title)) / 2, panelY + 90);

        g2d.setColor(new Color(220, 220, 220));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        metrics = getFontMetrics(g2d.getFont());
        g2d.drawString(subtitle, (SCREEN_WIDTH - metrics.stringWidth(subtitle)) / 2, panelY + 130);
    }

    private void newApple() {
        boolean validPosition;
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
        } while (!validPosition);
    }

    private void newBigApple() {
        boolean validPosition;
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
        } while (!validPosition);
        bigAppleActive = true;
        bigAppleStartTime = System.currentTimeMillis();
    }

    private boolean isOnObstacle(int x, int y) {
        for (Rectangle obstacle : obstacles) {
            if (obstacle.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private void move() {
        animationStep++;
        if (animationStep >= ANIMATION_STEPS) {
            // Commit exactly one buffered direction change per grid step.
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
        }

        for (int i = 0; i < bodyParts; i++) {
            double targetX = x[i];
            double targetY = y[i];
            double currentX = renderX[i];
            double currentY = renderY[i];
            renderX[i] = currentX + (targetX - currentX) * ((double) animationStep / ANIMATION_STEPS);
            renderY[i] = currentY + (targetY - currentY) * ((double) animationStep / ANIMATION_STEPS);
        }
    }

    private void spawnParticles(int appleX, int appleY, Color color) {
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = random.nextDouble() * 2 + 1;
            double dx = Math.cos(angle) * speed;
            double dy = Math.sin(angle) * speed;
            particles.add(new Particle(appleX + UNIT_SIZE / 2, appleY + UNIT_SIZE / 2, dx, dy, color));
        }
    }

    private void updateParticles() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.update();
            if (particle.alpha <= 0) {
                particles.remove(i);
            }
        }
    }

    /**
     * Fires a non-blocking "new high score" toast the first time this run's score
     * beats the best score already on the (persisted) leaderboard. Purely visual —
     * it never pauses the timer or interrupts play.
     */
    private void checkForNewHighScore() {
        if (!highScoreAnnouncedThisRun && applesEaten > scoreToBeat) {
            highScoreAnnouncedThisRun = true;
            showToast("\u2605 NEW HIGH SCORE: " + applesEaten + "! \u2605", new Color(255, 215, 0));
        }
    }

    private void showToast(String message, Color color) {
        toastMessage = message;
        toastColor = color;
        toastStartTime = System.currentTimeMillis();
    }

    /** Draws the toast as a fading pill banner; called from draw() every frame while active. */
    private void drawToast(Graphics2D g2d) {
        if (toastMessage == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - toastStartTime;
        if (elapsed >= TOAST_DURATION) {
            toastMessage = null;
            return;
        }
        float alpha;
        if (elapsed < TOAST_FADE_MS) {
            alpha = elapsed / (float) TOAST_FADE_MS;
        } else if (elapsed > TOAST_DURATION - TOAST_FADE_MS) {
            alpha = (TOAST_DURATION - elapsed) / (float) TOAST_FADE_MS;
        } else {
            alpha = 1f;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        g2d.setFont(new Font("SansSerif", Font.BOLD, 30));
        FontMetrics metrics = getFontMetrics(g2d.getFont());
        int textW = metrics.stringWidth(toastMessage);
        int panelW = textW + 60;
        int panelH = 58;
        int panelX = (SCREEN_WIDTH - panelW) / 2;
        int panelY = 112;

        g2d.setColor(new Color(0, 0, 0, (int) (180 * alpha)));
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 22, 22);
        g2d.setColor(new Color(toastColor.getRed(), toastColor.getGreen(), toastColor.getBlue(), (int) (220 * alpha)));
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 22, 22);

        g2d.setColor(new Color(toastColor.getRed(), toastColor.getGreen(), toastColor.getBlue(), (int) (255 * alpha)));
        g2d.drawString(toastMessage, panelX + 30, panelY + 38);
    }

    private void checkApple() {
        if (!bigAppleActive && x[0] == appleX && y[0] == appleY) {
            int tailX = x[bodyParts - 1];
            int tailY = y[bodyParts - 1];
            bodyParts++;
            applesEaten++;
            smallApplesCount++;
            totalSmallApplesEaten++; // Increment total small apples for challenge
            highScore = Math.max(highScore, applesEaten);
            checkForNewHighScore();
            x[bodyParts - 1] = tailX;
            y[bodyParts - 1] = tailY;
            renderX[bodyParts - 1] = tailX;
            renderY[bodyParts - 1] = tailY;
            spawnParticles(appleX, appleY, Color.red);
            newApple();
            if (smallApplesCount >= SMALL_APPLES_FOR_BIG) {
                newBigApple();
                smallApplesCount = 0; // Reset only the counter for big apple spawning
            }
            updateSpeed();
            if (gameMode == GameMode.CHALLENGE) {
                updateChallenge();
            }
        }
        if (bigAppleActive) {
            if (System.currentTimeMillis() - bigAppleStartTime >= BIG_APPLE_DURATION) {
                bigAppleActive = false;
                return;
            }
            Rectangle snakeHead = new Rectangle(x[0], y[0], UNIT_SIZE, UNIT_SIZE);
            Rectangle bigApple = new Rectangle(bigAppleX, bigAppleY, BIG_APPLE_SIZE, BIG_APPLE_SIZE);
            if (snakeHead.intersects(bigApple)) {
                int tailX = x[bodyParts - 1];
                int tailY = y[bodyParts - 1];
                bodyParts++;
                applesEaten += 3;
                highScore = Math.max(highScore, applesEaten);
                checkForNewHighScore();
                x[bodyParts - 1] = tailX;
                y[bodyParts - 1] = tailY;
                renderX[bodyParts - 1] = tailX;
                renderY[bodyParts - 1] = tailY;
                spawnParticles(bigAppleX + BIG_APPLE_SIZE / 2, bigAppleY + BIG_APPLE_SIZE / 2, Color.orange);
                bigAppleActive = false;
                updateSpeed();
                if (gameMode == GameMode.CHALLENGE && challengeIndex == 2) {
                    challengeProgress++;
                    updateChallenge();
                }
            }
        }
    }

    private void checkCollisions() {
        boolean collision = false;

        if (x[0] < 0 || x[0] >= SCREEN_WIDTH || y[0] < 0 || y[0] >= SCREEN_HEIGHT) {
            collision = true;
        }

        for (int i = bodyParts - 1; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                collision = true;
                break;
            }
        }

        for (Rectangle obstacle : obstacles) {
            if (obstacle.contains(x[0], y[0])) {
                collision = true;
                break;
            }
        }

        if (collision) {
            if (gameMode == GameMode.CLASSIC) {
                lives--;
                if (lives <= 0) {
                    running = false;
                    timer.stop();
                    promptForName();
                    restartButton.setVisible(true);
                    leaderboardButton.setVisible(true);
                } else {
                    setSafeStartingPosition();
                    direction = 'R';
                    nextDirection = 'R';
                    bodyParts = 6;
                    newApple();
                    repaint();
                }
            } else if (gameMode == GameMode.ENDLESS) {
                setSafeStartingPosition();
                direction = 'R';
                nextDirection = 'R';
                bodyParts = 6;
                newApple();
                repaint();
            } else if (gameMode == GameMode.CHALLENGE) {
                if (challengeIndex == 1) {
                    running = false;
                    timer.stop();
                    JOptionPane.showMessageDialog(this, "Challenge Failed! You hit an obstacle.");
                    promptForName();
                    restartButton.setVisible(true);
                    leaderboardButton.setVisible(true);
                } else {
                    setSafeStartingPosition();
                    direction = 'R';
                    nextDirection = 'R';
                    bodyParts = 6;
                    newApple();
                    repaint();
                }
            }
        }
    }

    private void promptForName() {
        String name = JOptionPane.showInputDialog(this, "Game Over! Enter your name:", "Score: " + applesEaten, JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            leaderboard.add(new ScoreEntry(name.trim(), applesEaten));
            Collections.sort(leaderboard, Comparator.comparingInt((ScoreEntry e) -> e.score).reversed());
            if (leaderboard.size() > 10) {
                leaderboard.subList(10, leaderboard.size()).clear();
            }
            saveLeaderboard();
            highScore = leaderboard.isEmpty() ? 0 : leaderboard.get(0).score;
        }
        this.requestFocusInWindow();
    }

    private void showLeaderboard() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Leaderboard", true);
        dialog.setSize(440, 580);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(18, 18, 18));

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(12, 12, 12));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(18, 10, 18, 10));
        JLabel title = new JLabel("\uD83C\uDFC6  LEADERBOARD", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(new Color(255, 215, 0));
        headerPanel.add(title);
        dialog.add(headerPanel, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(18, 18, 18));
        listPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        if (leaderboard.isEmpty()) {
            JLabel empty = new JLabel("No scores yet \u2014 be the first!");
            empty.setForeground(Color.white);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 16));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(empty);
        } else {
            for (int i = 0; i < leaderboard.size(); i++) {
                ScoreEntry entry = leaderboard.get(i);
                JPanel row = buildLeaderboardRow(i + 1, entry.name, entry.score);
                row.setAlignmentX(Component.CENTER_ALIGNMENT);
                listPanel.add(row);
                listPanel.add(Box.createVerticalStrut(8));
            }
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(new Color(18, 18, 18));
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setBackground(new Color(12, 12, 12));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.setForeground(Color.red);
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dialog.dispose());
        footer.add(closeButton);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setVisible(true);
        this.requestFocusInWindow();
    }

    /** Builds one styled row (rank medal, name, score) for the leaderboard dialog. */
    private JPanel buildLeaderboardRow(int rank, String name, int score) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setMaximumSize(new Dimension(1000, 46));
        row.setPreferredSize(new Dimension(380, 46));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1, true),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));

        Color rankColor;
        String rankText;
        switch (rank) {
            case 1: rankColor = new Color(255, 215, 0); rankText = "\uD83E\uDD47"; break;   // gold
            case 2: rankColor = new Color(200, 200, 210); rankText = "\uD83E\uDD48"; break;  // silver
            case 3: rankColor = new Color(205, 127, 50); rankText = "\uD83E\uDD49"; break;   // bronze
            default: rankColor = Color.white; rankText = "#" + rank;
        }
        row.setBackground(rank <= 3 ? new Color(38, 34, 20) : new Color(28, 28, 28));

        JLabel rankLabel = new JLabel(rankText);
        rankLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        rankLabel.setForeground(rankColor);
        rankLabel.setPreferredSize(new Dimension(50, 30));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        nameLabel.setForeground(Color.white);

        JLabel scoreLabel = new JLabel(String.valueOf(score), SwingConstants.RIGHT);
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        scoreLabel.setForeground(new Color(255, 90, 90));
        scoreLabel.setPreferredSize(new Dimension(60, 30));

        row.add(rankLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    private void gameOver(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint bgGradient = new GradientPaint(
            0, 0, new Color(40, 0, 0), 0, SCREEN_HEIGHT, Color.black);
        g2d.setPaint(bgGradient);
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        int panelW = 560, panelH = 240;
        int panelX = (SCREEN_WIDTH - panelW) / 2;
        int panelY = (SCREEN_HEIGHT - panelH) / 2 - 20;
        g2d.setColor(new Color(20, 20, 20, 230));
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 28, 28);
        g2d.setColor(new Color(220, 30, 30, 160));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 28, 28);

        g2d.setColor(Color.red);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 70));
        FontMetrics metrics = getFontMetrics(g2d.getFont());
        g2d.drawString("Game Over", (SCREEN_WIDTH - metrics.stringWidth("Game Over")) / 2, panelY + 90);

        g2d.setColor(Color.white);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 30));
        metrics = getFontMetrics(g2d.getFont());
        String scoreText = "Score: " + applesEaten + "   High Score: " + highScore;
        g2d.drawString(scoreText, (SCREEN_WIDTH - metrics.stringWidth(scoreText)) / 2, panelY + 145);

        g2d.setColor(new Color(200, 200, 200));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        metrics = getFontMetrics(g2d.getFont());
        String hint = "Click Restart to play again";
        g2d.drawString(hint, (SCREEN_WIDTH - metrics.stringWidth(hint)) / 2, panelY + 190);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            move();
            if (animationStep == 0) {
                checkApple();
                checkCollisions();
                int nextLevelThreshold = currentLevel * POINTS_PER_LEVEL;
                if (applesEaten >= nextLevelThreshold) {
                    advanceToNextLevel();
                }
                if (gameMode == GameMode.CHALLENGE && !challengeActive) {
                    startChallenge();
                }
                if (gameMode == GameMode.CHALLENGE) {
                    updateChallenge();
                }
            }
            updateParticles();
        }
        repaint();
    }

    private class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            // IMPORTANT: validate against `direction` (the last COMMITTED grid
            // direction), never against `nextDirection`. This stops a fast
            // double key-press (e.g. UP then LEFT while still moving RIGHT)
            // from chaining through an intermediate direction and sneaking a
            // 180-degree reversal past the check before the snake has even
            // visually moved. Only one direction change is honored per grid
            // step; extra presses in between are simply ignored.
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_A:
                    if (direction != 'R') {
                        nextDirection = 'L';
                    }
                    break;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    if (direction != 'L') {
                        nextDirection = 'R';
                    }
                    break;
                case KeyEvent.VK_UP:
                case KeyEvent.VK_W:
                    if (direction != 'D') {
                        nextDirection = 'U';
                    }
                    break;
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_S:
                    if (direction != 'U') {
                        nextDirection = 'D';
                    }
                    break;
                case KeyEvent.VK_SPACE:
                    togglePause();
                    break;
            }
        }
    }
}