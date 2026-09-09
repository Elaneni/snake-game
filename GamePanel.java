import graphics.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import logic.*;

/**
 * Main module for the Snake Game.
 * Coordinates between the logic package (engine, rules, preferences, leaderboard, audio)
 * and the graphics package (renderers, themes, transparent UI controls).
 */
public class GamePanel extends JPanel implements ActionListener {
    public static final int SCREEN_WIDTH = SnakeEngine.SCREEN_WIDTH;
    public static final int SCREEN_HEIGHT = SnakeEngine.SCREEN_HEIGHT;

    // Active state machine
    private GameState state;
    private GameState previousState = GameState.MENU; // to resume back to game if settings was opened in-game
    private boolean isLevelUpOverlay = false;

    // Logic Modules
    private final PreferencesManager preferencesManager;
    private final LeaderboardManager leaderboardManager;
    private final ChallengeManager challengeManager;
    private final SnakeEngine engine;
    private final SoundPlayer soundPlayer;

    // Graphics Renderers
    private final BackgroundRenderer backgroundRenderer;
    private final SnakeRenderer snakeRenderer;
    private final AppleRenderer appleRenderer;
    private final ObstacleRenderer obstacleRenderer;
    private final ParticleRenderer particleRenderer;
    private final HudRenderer hudRenderer;
    private final MenuRenderer menuRenderer;
    private final SettingsRenderer settingsRenderer;
    private final LeaderboardRenderer leaderboardRenderer;
    private final HelpRenderer helpRenderer;
    private final GameOverRenderer gameOverRenderer;

    // Game loop timer
    private Timer timer;

    // --- UI Controls ---
    // Start Screen (Menu) buttons
    private TransparentButton btnStartGame;
    private TransparentButton btnSettings;
    private TransparentButton btnLeaderboard;
    private TransparentButton btnHelp;
    private TransparentButton btnExit;

    // In-Game Transparent HUD buttons (Do not interfere with playground)
    private TransparentButton btnInGameSound;
    private TransparentButton btnInGamePause;
    private TransparentButton btnInGameSettings;
    private TransparentButton btnInGameMenu;

    // Settings Screen controls
    private JSlider speedSlider;
    private TransparentButton btnModeClassic;
    private TransparentButton btnModeEndless;
    private TransparentButton btnModeChallenge;
    private final List<TransparentButton> levelButtons = new ArrayList<>();
    private TransparentButton btnHeadColor;
    private TransparentButton btnBodyColor;
    private TransparentButton btnResetColors;
    private TransparentButton btnSettingsSound;
    private TransparentButton btnSettingsBack;

    // Leaderboard Screen button
    private TransparentButton btnLeaderboardBack;

    // Help Screen button
    private TransparentButton btnHelpAction;

    // Game Over buttons
    private TransparentButton btnGameOverRestart;
    private TransparentButton btnGameOverMenu;

    public GamePanel() {
        // 1. Initialize Logic & Preferences
        this.preferencesManager = new PreferencesManager();
        this.leaderboardManager = new LeaderboardManager();
        this.challengeManager = new ChallengeManager();
        this.engine = new SnakeEngine();
        this.soundPlayer = new SoundPlayer(preferencesManager.isSoundEnabled());

        // 2. Initialize Graphics Renderers
        this.backgroundRenderer = new BackgroundRenderer();
        this.snakeRenderer = new SnakeRenderer();
        this.appleRenderer = new AppleRenderer();
        this.obstacleRenderer = new ObstacleRenderer();
        this.particleRenderer = new ParticleRenderer();
        this.hudRenderer = new HudRenderer();
        this.menuRenderer = new MenuRenderer();
        this.settingsRenderer = new SettingsRenderer();
        this.leaderboardRenderer = new LeaderboardRenderer();
        this.helpRenderer = new HelpRenderer();
        this.gameOverRenderer = new GameOverRenderer();

        // 3. Panel Configuration
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(preferencesManager.getBackgroundColor());
        this.setFocusable(true);
        this.setLayout(null); // Absolute positioning for seamless glass controls
        this.addKeyListener(new GameKeyAdapter());

        // 4. Initialize all UI buttons and components
        initUIComponents();

        // 5. First-time user evaluation
        if (preferencesManager.isFirstTimeUser()) {
            switchState(GameState.HELP);
        } else {
            switchState(GameState.MENU);
        }

        // 6. Setup game timer
        this.timer = new Timer(engine.getCurrentDelay() / SnakeEngine.ANIMATION_STEPS, this);
    }

    private void initUIComponents() {
        initMenuButtons();
        initInGameHudButtons();
        initSettingsComponents();
        initLeaderboardButtons();
        initHelpButtons();
        initGameOverButtons();
    }

    // =========================================================================
    // UI Component Initialization
    // =========================================================================

    private void initMenuButtons() {
        int btnW = 380;
        int btnH = 50;
        int btnX = (SCREEN_WIDTH - btnW) / 2;
        int startY = 285;
        int gap = 58;

        btnStartGame = new TransparentButton("\u25B6  START GAME", 25);
        btnStartGame.setBounds(btnX, startY, btnW, btnH);
        btnStartGame.setFont(UITheme.FONT_BUTTON);
        btnStartGame.setColors(
            new Color(0, 230, 118, 30), new Color(0, 230, 118, 70),
            new Color(0, 230, 118, 160), new Color(0, 255, 140, 240),
            UITheme.PRIMARY_GREEN, Color.WHITE
        );
        btnStartGame.addActionListener(e -> {
            soundPlayer.playClick();
            startGame();
        });
        this.add(btnStartGame);

        btnSettings = new TransparentButton("\u2699  SETTINGS", 25);
        btnSettings.setBounds(btnX, startY + gap, btnW, btnH);
        btnSettings.addActionListener(e -> {
            soundPlayer.playClick();
            previousState = GameState.MENU;
            switchState(GameState.SETTINGS);
        });
        this.add(btnSettings);

        btnLeaderboard = new TransparentButton("\uD83C\uDFC6  LEADERBOARD", 25);
        btnLeaderboard.setBounds(btnX, startY + gap * 2, btnW, btnH);
        btnLeaderboard.addActionListener(e -> {
            soundPlayer.playClick();
            switchState(GameState.LEADERBOARD);
        });
        this.add(btnLeaderboard);

        btnHelp = new TransparentButton("\u2753  HELP & INSTRUCTIONS", 25);
        btnHelp.setBounds(btnX, startY + gap * 3, btnW, btnH);
        btnHelp.addActionListener(e -> {
            soundPlayer.playClick();
            switchState(GameState.HELP);
        });
        this.add(btnHelp);

        btnExit = new TransparentButton("\u2715  EXIT GAME", 25);
        btnExit.setBounds(btnX, startY + gap * 4, btnW, btnH);
        btnExit.setColors(
            new Color(255, 61, 87, 20), new Color(255, 61, 87, 50),
            new Color(255, 61, 87, 100), new Color(255, 61, 87, 220),
            new Color(255, 120, 140), Color.WHITE
        );
        btnExit.addActionListener(e -> System.exit(0));
        this.add(btnExit);
    }

    private void initInGameHudButtons() {
        // Positioned neatly inside the top HUD bar (height 80px) on the right side.
        // Transparent glass aesthetic ensures ZERO obstruction of gameplay!
        int topY = 18;
        int btnH = 44;

        btnInGameMenu = new TransparentButton("\u2BAE Menu", 20);
        btnInGameMenu.setBounds(SCREEN_WIDTH - 120, topY, 105, btnH);
        btnInGameMenu.setFont(UITheme.FONT_BODY_BOLD);
        btnInGameMenu.setColors(
            new Color(255, 255, 255, 12), new Color(255, 61, 87, 45),
            new Color(255, 255, 255, 50), new Color(255, 61, 87, 180),
            new Color(220, 220, 220), Color.WHITE
        );
        btnInGameMenu.addActionListener(e -> {
            soundPlayer.playClick();
            if (timer != null) timer.stop();
            switchState(GameState.MENU);
        });
        this.add(btnInGameMenu);

        btnInGameSettings = new TransparentButton("\u2699 Settings", 20);
        btnInGameSettings.setBounds(SCREEN_WIDTH - 245, topY, 115, btnH);
        btnInGameSettings.setFont(UITheme.FONT_BODY_BOLD);
        btnInGameSettings.setColors(
            new Color(255, 255, 255, 12), new Color(0, 229, 255, 45),
            new Color(255, 255, 255, 50), new Color(0, 229, 255, 180),
            new Color(220, 220, 220), Color.WHITE
        );
        btnInGameSettings.addActionListener(e -> {
            soundPlayer.playClick();
            if (state == GameState.PLAYING) togglePause();
            previousState = GameState.PAUSED;
            switchState(GameState.SETTINGS);
        });
        this.add(btnInGameSettings);

        btnInGamePause = new TransparentButton("\u23F8 Pause", 20);
        btnInGamePause.setBounds(SCREEN_WIDTH - 360, topY, 105, btnH);
        btnInGamePause.setFont(UITheme.FONT_BODY_BOLD);
        btnInGamePause.setColors(
            new Color(255, 255, 255, 12), new Color(0, 230, 118, 45),
            new Color(255, 255, 255, 50), new Color(0, 230, 118, 180),
            new Color(220, 220, 220), Color.WHITE
        );
        btnInGamePause.addActionListener(e -> {
            soundPlayer.playClick();
            togglePause();
        });
        this.add(btnInGamePause);

        btnInGameSound = new TransparentButton(soundPlayer.isEnabled() ? "\uD83D\uDD0A Sound" : "\uD83D\uDD07 Muted", 20);
        btnInGameSound.setBounds(SCREEN_WIDTH - 480, topY, 110, btnH);
        btnInGameSound.setFont(UITheme.FONT_BODY_BOLD);
        btnInGameSound.setColors(
            new Color(255, 255, 255, 12), new Color(255, 215, 0, 45),
            new Color(255, 255, 255, 50), new Color(255, 215, 0, 180),
            new Color(220, 220, 220), Color.WHITE
        );
        btnInGameSound.addActionListener(e -> toggleSound());
        this.add(btnInGameSound);
    }

    private void initSettingsComponents() {
        int cardX = (SCREEN_WIDTH - 680) / 2;
        int cardY = (SCREEN_HEIGHT - 620) / 2;

        // 1. Speed Slider
        speedSlider = new JSlider(1, 10, preferencesManager.getSpeedLevel());
        speedSlider.setBounds(cardX + 50, cardY + 130, 580, 50);
        speedSlider.setOpaque(false);
        speedSlider.setBackground(new Color(0, 0, 0, 0));
        speedSlider.setForeground(UITheme.ACCENT_CYAN);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setFocusable(false);
        speedSlider.addChangeListener(e -> {
            int val = speedSlider.getValue();
            preferencesManager.setSpeedLevel(val);
            engine.setBaseDelay(preferencesManager.getBaseDelay());
            if (timer != null) {
                timer.setDelay(engine.getEffectiveDelay() / SnakeEngine.ANIMATION_STEPS);
            }
            repaint();
        });
        this.add(speedSlider);

        // 2. Game Mode Selection
        int modeY = cardY + 240;
        int modeW = 180;
        int modeH = 40;

        btnModeClassic = new TransparentButton("Classic", 18);
        btnModeClassic.setBounds(cardX + 50, modeY, modeW, modeH);
        btnModeClassic.addActionListener(e -> {
            soundPlayer.playClick();
            setGameMode(GameMode.CLASSIC);
        });
        this.add(btnModeClassic);

        btnModeEndless = new TransparentButton("Endless", 18);
        btnModeEndless.setBounds(cardX + 250, modeY, modeW, modeH);
        btnModeEndless.addActionListener(e -> {
            soundPlayer.playClick();
            setGameMode(GameMode.ENDLESS);
        });
        this.add(btnModeEndless);

        btnModeChallenge = new TransparentButton("Challenge", 18);
        btnModeChallenge.setBounds(cardX + 450, modeY, modeW, modeH);
        btnModeChallenge.addActionListener(e -> {
            soundPlayer.playClick();
            setGameMode(GameMode.CHALLENGE);
        });
        this.add(btnModeChallenge);

        // 3. Starting Level Selection (1 to 5)
        int lvlY = cardY + 340;
        int lvlW = 105;
        int lvlH = 38;
        for (int i = 1; i <= 5; i++) {
            final int level = i;
            TransparentButton lvlBtn = new TransparentButton("Level " + i, 16);
            lvlBtn.setBounds(cardX + 50 + (i - 1) * 118, lvlY, lvlW, lvlH);
            lvlBtn.setFont(UITheme.FONT_BODY_BOLD);
            lvlBtn.addActionListener(e -> {
                soundPlayer.playClick();
                setStartingLevel(level);
            });
            levelButtons.add(lvlBtn);
            this.add(lvlBtn);
        }

        // 4. Color Pickers
        int colorY = cardY + 435;
        btnHeadColor = new TransparentButton("\uD83C\uDFA8 Head Color", 18);
        btnHeadColor.setBounds(cardX + 50, colorY, 180, 42);
        btnHeadColor.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose Snake Head Color", preferencesManager.getSnakeHeadColor());
            if (newColor != null) {
                preferencesManager.setSnakeHeadColor(newColor);
                repaint();
            }
        });
        this.add(btnHeadColor);

        btnBodyColor = new TransparentButton("\uD83C\uDFA8 Body Color", 18);
        btnBodyColor.setBounds(cardX + 250, colorY, 180, 42);
        btnBodyColor.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose Snake Body Color", preferencesManager.getSnakeBodyColor());
            if (newColor != null) {
                preferencesManager.setSnakeBodyColor(newColor);
                repaint();
            }
        });
        this.add(btnBodyColor);

        btnResetColors = new TransparentButton("\u21BA Reset Colors", 18);
        btnResetColors.setBounds(cardX + 450, colorY, 180, 42);
        btnResetColors.addActionListener(e -> {
            soundPlayer.playClick();
            preferencesManager.setSnakeHeadColor(new Color(0, 230, 118));
            preferencesManager.setSnakeBodyColor(new Color(46, 125, 50));
            preferencesManager.setBackgroundColor(new Color(15, 18, 26));
            repaint();
        });
        this.add(btnResetColors);

        // 5. Sound toggle in settings
        btnSettingsSound = new TransparentButton(soundPlayer.isEnabled() ? "\uD83D\uDD0A Sound: ON" : "\uD83D\uDD07 Sound: MUTED", 18);
        btnSettingsSound.setBounds(cardX + 50, cardY + 525, 200, 42);
        btnSettingsSound.addActionListener(e -> toggleSound());
        this.add(btnSettingsSound);

        // 6. Back / Apply button
        btnSettingsBack = new TransparentButton("\u2BAE BACK", 24);
        btnSettingsBack.setBounds(cardX + 280, cardY + 525, 350, 44);
        btnSettingsBack.setColors(
            new Color(0, 230, 118, 30), new Color(0, 230, 118, 65),
            new Color(0, 230, 118, 140), new Color(0, 230, 118, 220),
            UITheme.PRIMARY_GREEN, Color.WHITE
        );
        btnSettingsBack.addActionListener(e -> {
            soundPlayer.playClick();
            if (previousState == GameState.PAUSED) {
                switchState(GameState.PAUSED);
            } else {
                switchState(GameState.MENU);
            }
        });
        this.add(btnSettingsBack);

        updateSettingsButtonHighlights();
    }

    private void toggleSound() {
        boolean newState = !soundPlayer.isEnabled();
        soundPlayer.setEnabled(newState);
        preferencesManager.setSoundEnabled(newState);
        String label = newState ? "\uD83D\uDD0A Sound" : "\uD83D\uDD07 Muted";
        btnInGameSound.setText(label);
        btnSettingsSound.setText(newState ? "\uD83D\uDD0A Sound: ON" : "\uD83D\uDD07 Sound: MUTED");
        if (newState) {
            soundPlayer.playClick();
        }
        repaint();
    }

    private void initLeaderboardButtons() {
        int cardY = (SCREEN_HEIGHT - 620) / 2;
        btnLeaderboardBack = new TransparentButton("\u2BAE BACK TO MENU", 24);
        btnLeaderboardBack.setBounds((SCREEN_WIDTH - 260) / 2, cardY + 545, 260, 50);
        btnLeaderboardBack.addActionListener(e -> {
            soundPlayer.playClick();
            switchState(GameState.MENU);
        });
        this.add(btnLeaderboardBack);
    }

    private void initHelpButtons() {
        int cardY = (SCREEN_HEIGHT - 620) / 2;
        btnHelpAction = new TransparentButton("GOT IT! LET'S PLAY \u2794", 25);
        btnHelpAction.setBounds((SCREEN_WIDTH - 320) / 2, cardY + 535, 320, 52);
        btnHelpAction.setColors(
            new Color(0, 230, 118, 40), new Color(0, 230, 118, 80),
            new Color(0, 230, 118, 180), new Color(0, 255, 140, 240),
            UITheme.PRIMARY_GREEN, Color.WHITE
        );
        btnHelpAction.addActionListener(e -> {
            soundPlayer.playClick();
            if (preferencesManager.isFirstTimeUser()) {
                preferencesManager.setFirstTimeUser(false);
                switchState(GameState.MENU);
            } else {
                switchState(GameState.MENU);
            }
        });
        this.add(btnHelpAction);
    }

    private void initGameOverButtons() {
        int btnY = (SCREEN_HEIGHT - 380) / 2 - 40 + 250;
        int btnW = 200;
        int btnH = 50;
        int startX = (SCREEN_WIDTH - (btnW * 2 + 30)) / 2;

        btnGameOverRestart = new TransparentButton("\u21BA PLAY AGAIN", 24);
        btnGameOverRestart.setBounds(startX, btnY, btnW, btnH);
        btnGameOverRestart.setColors(
            new Color(0, 230, 118, 30), new Color(0, 230, 118, 70),
            new Color(0, 230, 118, 160), new Color(0, 255, 140, 240),
            UITheme.PRIMARY_GREEN, Color.WHITE
        );
        btnGameOverRestart.addActionListener(e -> {
            soundPlayer.playClick();
            startGame();
        });
        this.add(btnGameOverRestart);

        btnGameOverMenu = new TransparentButton("\u2BAE MAIN MENU", 24);
        btnGameOverMenu.setBounds(startX + btnW + 30, btnY, btnW, btnH);
        btnGameOverMenu.addActionListener(e -> {
            soundPlayer.playClick();
            switchState(GameState.MENU);
        });
        this.add(btnGameOverMenu);
    }

    // =========================================================================
    // State Transitions & Visibility Management
    // =========================================================================

    public void switchState(GameState newState) {
        this.state = newState;

        // Hide all initially
        btnStartGame.setVisible(false);
        btnSettings.setVisible(false);
        btnLeaderboard.setVisible(false);
        btnHelp.setVisible(false);
        btnExit.setVisible(false);

        btnInGameSound.setVisible(false);
        btnInGamePause.setVisible(false);
        btnInGameSettings.setVisible(false);
        btnInGameMenu.setVisible(false);

        speedSlider.setVisible(false);
        btnModeClassic.setVisible(false);
        btnModeEndless.setVisible(false);
        btnModeChallenge.setVisible(false);
        for (TransparentButton b : levelButtons) b.setVisible(false);
        btnHeadColor.setVisible(false);
        btnBodyColor.setVisible(false);
        btnResetColors.setVisible(false);
        btnSettingsSound.setVisible(false);
        btnSettingsBack.setVisible(false);

        btnLeaderboardBack.setVisible(false);
        btnHelpAction.setVisible(false);
        btnGameOverRestart.setVisible(false);
        btnGameOverMenu.setVisible(false);

        switch (state) {
            case MENU:
                btnStartGame.setVisible(true);
                btnSettings.setVisible(true);
                btnLeaderboard.setVisible(true);
                btnHelp.setVisible(true);
                btnExit.setVisible(true);
                break;

            case PLAYING:
            case PAUSED:
                btnInGameSound.setVisible(true);
                btnInGamePause.setVisible(true);
                btnInGameSettings.setVisible(true);
                btnInGameMenu.setVisible(true);
                btnInGamePause.setText(state == GameState.PAUSED ? "\u25B6 Resume" : "\u23F8 Pause");
                break;

            case SETTINGS:
                speedSlider.setValue(preferencesManager.getSpeedLevel());
                updateSettingsButtonHighlights();
                speedSlider.setVisible(true);
                btnModeClassic.setVisible(true);
                btnModeEndless.setVisible(true);
                btnModeChallenge.setVisible(true);
                for (TransparentButton b : levelButtons) b.setVisible(true);
                btnHeadColor.setVisible(true);
                btnBodyColor.setVisible(true);
                btnResetColors.setVisible(true);
                btnSettingsSound.setVisible(true);
                btnSettingsBack.setText(previousState == GameState.PAUSED ? "\u25B6 RESUME GAME" : "\u2BAE BACK TO MENU");
                btnSettingsBack.setVisible(true);
                break;

            case LEADERBOARD:
                leaderboardManager.load();
                btnLeaderboardBack.setVisible(true);
                break;

            case HELP:
                if (preferencesManager.isFirstTimeUser()) {
                    btnHelpAction.setText("GOT IT! LET'S PLAY \u2794");
                } else {
                    btnHelpAction.setText("\u2BAE BACK TO MENU");
                }
                btnHelpAction.setVisible(true);
                break;

            case GAME_OVER:
                btnGameOverRestart.setVisible(true);
                btnGameOverMenu.setVisible(true);
                break;
        }

        repaint();
        this.requestFocusInWindow();
    }

    private void updateSettingsButtonHighlights() {
        GameMode currentMode = preferencesManager.getGameMode();
        highlightActiveButton(btnModeClassic, currentMode == GameMode.CLASSIC);
        highlightActiveButton(btnModeEndless, currentMode == GameMode.ENDLESS);
        highlightActiveButton(btnModeChallenge, currentMode == GameMode.CHALLENGE);

        int curLvl = preferencesManager.getStartingLevel();
        for (int i = 0; i < levelButtons.size(); i++) {
            highlightActiveButton(levelButtons.get(i), (i + 1) == curLvl);
        }
    }

    private void highlightActiveButton(TransparentButton btn, boolean active) {
        if (active) {
            btn.setColors(
                new Color(0, 230, 118, 50), new Color(0, 230, 118, 90),
                new Color(0, 230, 118, 220), new Color(0, 255, 140, 255),
                UITheme.PRIMARY_GREEN, Color.WHITE
            );
        } else {
            btn.setColors(
                new Color(255, 255, 255, 14), new Color(255, 255, 255, 30),
                new Color(255, 255, 255, 50), new Color(255, 255, 255, 120),
                new Color(200, 200, 200), Color.WHITE
            );
        }
    }

    private void setGameMode(GameMode mode) {
        preferencesManager.setGameMode(mode);
        engine.setGameMode(mode);
        updateSettingsButtonHighlights();
        repaint();
    }

    private void setStartingLevel(int level) {
        preferencesManager.setStartingLevel(level);
        updateSettingsButtonHighlights();
        repaint();
    }

    // =========================================================================
    // Game Lifecycle Management
    // =========================================================================

    private void startGame() {
        int startingLevel = preferencesManager.getStartingLevel();
        int baseDelay = preferencesManager.getBaseDelay();
        GameMode mode = preferencesManager.getGameMode();
        int highScore = leaderboardManager.getHighScore();

        engine.initGame(startingLevel, baseDelay, mode, highScore);
        challengeManager.reset();
        isLevelUpOverlay = false;

        if (mode == GameMode.CHALLENGE) {
            challengeManager.startNextChallenge();
        }

        if (timer != null) timer.stop();
        timer = new Timer(engine.getEffectiveDelay() / SnakeEngine.ANIMATION_STEPS, this);
        timer.start();

        switchState(GameState.PLAYING);
    }

    private void togglePause() {
        if (state == GameState.PLAYING) {
            isLevelUpOverlay = false;
            timer.stop();
            switchState(GameState.PAUSED);
        } else if (state == GameState.PAUSED) {
            isLevelUpOverlay = false;
            timer.setDelay(engine.getEffectiveDelay() / SnakeEngine.ANIMATION_STEPS);
            timer.start();
            switchState(GameState.PLAYING);
        }
    }

    private void handleGameOver() {
        if (timer != null) timer.stop();
        soundPlayer.playGameOver();

        int finalScore = engine.getApplesEaten();
        if (finalScore > 0) {
            promptPlayerName(finalScore);
        }

        switchState(GameState.GAME_OVER);
    }

    private void promptPlayerName(int score) {
        String name = JOptionPane.showInputDialog(
            this,
            "Game Over! You scored " + score + " points!\nEnter your name for the Leaderboard:",
            "Save Score",
            JOptionPane.PLAIN_MESSAGE
        );
        if (name != null && !name.trim().isEmpty()) {
            leaderboardManager.addScore(name.trim(), score);
        }
    }

    // =========================================================================
    // Game Loop (Timer Action)
    // =========================================================================

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PLAYING) {
            boolean fullStep = engine.move();

            if (fullStep) {
                // Check apple & powerups
                SnakeEngine.AppleResult appleResult = engine.checkApple();
                if (appleResult != SnakeEngine.AppleResult.NONE) {
                    if (appleResult == SnakeEngine.AppleResult.SMALL_APPLE_EATEN) {
                        soundPlayer.playEatApple();
                    } else if (appleResult == SnakeEngine.AppleResult.BIG_APPLE_EATEN) {
                        soundPlayer.playEatBigApple();
                    } else if (appleResult == SnakeEngine.AppleResult.POWER_UP_COLLECTED) {
                        soundPlayer.playPowerUp();
                    }

                    // Update speed if needed
                    engine.updateSpeed();
                    timer.setDelay(engine.getEffectiveDelay() / SnakeEngine.ANIMATION_STEPS);

                    // Level progression check
                    if (engine.shouldAdvanceLevel()) {
                        engine.advanceToNextLevel();
                        soundPlayer.playLevelUp();
                        timer.setDelay(engine.getEffectiveDelay() / SnakeEngine.ANIMATION_STEPS);

                        // Celebrate level up and pause smoothly so player sees new obstacles!
                        isLevelUpOverlay = true;
                        timer.stop();
                        switchState(GameState.PAUSED);
                        btnInGamePause.setText("\u25B6 Resume");
                        repaint();
                        return;
                    }

                    // Challenge check
                    if (engine.getGameMode() == GameMode.CHALLENGE) {
                        if (appleResult == SnakeEngine.AppleResult.BIG_APPLE_EATEN && challengeManager.getChallengeIndex() == 2) {
                            challengeManager.incrementProgress(1);
                        }
                        if (challengeManager.checkCompletion(engine.getTotalSmallApplesEaten())) {
                            soundPlayer.playPowerUp();
                            engine.showToast("\u2694 CHALLENGE COMPLETED!", UITheme.PRIMARY_GREEN);
                            if (challengeManager.isAllChallengesCompleted()) {
                                engine.showToast("\uD83C\uDF89 ALL CHALLENGES COMPLETED!", UITheme.ACCENT_GOLD);
                                handleGameOver();
                                return;
                            } else {
                                challengeManager.startNextChallenge();
                            }
                        }
                    }
                }

                // Challenge timeout check
                if (engine.getGameMode() == GameMode.CHALLENGE) {
                    if (challengeManager.checkCompletion(engine.getTotalSmallApplesEaten())) {
                        soundPlayer.playPowerUp();
                        engine.showToast("\u2694 CHALLENGE COMPLETED!", UITheme.PRIMARY_GREEN);
                        if (challengeManager.isAllChallengesCompleted()) {
                            handleGameOver();
                            return;
                        } else {
                            challengeManager.startNextChallenge();
                        }
                    } else if (challengeManager.isTimedOut()) {
                        soundPlayer.playHit();
                        engine.showToast("\u2716 TIME'S UP! Challenge Failed.", UITheme.ACCENT_RED);
                        handleGameOver();
                        return;
                    }
                }

                // Check collisions
                SnakeEngine.CollisionResult collision = engine.checkCollisions();
                if (collision == SnakeEngine.CollisionResult.GAME_OVER) {
                    handleGameOver();
                    return;
                } else if (collision == SnakeEngine.CollisionResult.SHIELD_SAVED) {
                    soundPlayer.playHit();
                } else if (collision == SnakeEngine.CollisionResult.LIFE_LOST) {
                    soundPlayer.playHit();
                    engine.showToast("\u26A0 LIFE LOST! " + engine.getLives() + " remaining", UITheme.ACCENT_RED);
                }
            }

            engine.updateParticles();
        }

        repaint();
    }

    // =========================================================================
    // Rendering Pipeline
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        UITheme.applyQualityRendering(g2d);

        // 1. Draw consistent game background across all screens
        backgroundRenderer.draw(g2d, preferencesManager.getBackgroundColor(), SCREEN_WIDTH, SCREEN_HEIGHT);

        // 2. Render active screen elements
        switch (state) {
            case MENU:
                menuRenderer.draw(g2d, leaderboardManager.getHighScore(), SCREEN_WIDTH, SCREEN_HEIGHT);
                break;

            case PLAYING:
            case PAUSED:
                // Obstacles
                obstacleRenderer.draw(g2d, engine.getObstacles());

                // Apples & Power-ups
                appleRenderer.draw(g2d, engine);

                // Snake
                snakeRenderer.draw(g2d, engine, preferencesManager.getSnakeHeadColor(), preferencesManager.getSnakeBodyColor());

                // Particles
                particleRenderer.draw(g2d, engine.getParticles());

                // Heads-Up Display
                hudRenderer.draw(g2d, engine, challengeManager, leaderboardManager.getHighScore(), SCREEN_WIDTH, SCREEN_HEIGHT);

                // Paused overlay or Level Up celebration overlay
                if (state == GameState.PAUSED) {
                    if (isLevelUpOverlay) {
                        hudRenderer.drawLevelUpOverlay(g2d, engine.getCurrentLevel(), SCREEN_WIDTH, SCREEN_HEIGHT);
                    } else {
                        hudRenderer.drawPauseOverlay(g2d, SCREEN_WIDTH, SCREEN_HEIGHT);
                    }
                }
                break;

            case SETTINGS:
                settingsRenderer.draw(g2d, SCREEN_WIDTH, SCREEN_HEIGHT, preferencesManager.getSpeedLevel());
                break;

            case LEADERBOARD:
                leaderboardRenderer.draw(g2d, leaderboardManager.getScores(), SCREEN_WIDTH, SCREEN_HEIGHT);
                break;

            case HELP:
                helpRenderer.draw(g2d, preferencesManager.isFirstTimeUser(), SCREEN_WIDTH, SCREEN_HEIGHT);
                break;

            case GAME_OVER:
                // Draw background game elements frozen underneath for dramatic depth
                obstacleRenderer.draw(g2d, engine.getObstacles());
                appleRenderer.draw(g2d, engine);
                snakeRenderer.draw(g2d, engine, preferencesManager.getSnakeHeadColor(), preferencesManager.getSnakeBodyColor());
                // Game over modal overlay
                gameOverRenderer.draw(g2d, engine.getApplesEaten(), leaderboardManager.getHighScore(), engine.getCurrentLevel(), SCREEN_WIDTH, SCREEN_HEIGHT);
                break;
        }
    }

    // =========================================================================
    // Input Handling
    // =========================================================================

    private class GameKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            if (state == GameState.PLAYING) {
                switch (key) {
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_A:
                        engine.handleDirectionInput('L');
                        break;
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_D:
                        engine.handleDirectionInput('R');
                        break;
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_W:
                        engine.handleDirectionInput('U');
                        break;
                    case KeyEvent.VK_DOWN:
                    case KeyEvent.VK_S:
                        engine.handleDirectionInput('D');
                        break;
                    case KeyEvent.VK_SPACE:
                        togglePause();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        togglePause();
                        break;
                }
            } else if (state == GameState.PAUSED) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ESCAPE) {
                    togglePause();
                }
            } else if (state == GameState.SETTINGS || state == GameState.LEADERBOARD || state == GameState.HELP) {
                if (key == KeyEvent.VK_ESCAPE) {
                    switchState(previousState == GameState.PAUSED ? GameState.PAUSED : GameState.MENU);
                }
            }
        }
    }
}