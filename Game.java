import graphics.BoardRenderer;
import graphics.Effects;
import graphics.Gfx;
import graphics.HudRenderer;
import graphics.Theme;
import graphics.Ui;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JFrame;
import logic.Challenge;
import logic.Database;
import logic.Direction;
import logic.GameMode;
import logic.GameState;
import logic.LeaderboardManager;
import logic.PowerUpType;
import logic.PreferencesManager;
import logic.SnakeEngine;
import logic.SoundPlayer;
import logic.SoundPlayer.Sfx;

/**
 * Window, game loop, input and screens.
 *
 * The loop runs on its own thread with active rendering (BufferStrategy page
 * flipping), a fixed-timestep simulation inside {@link SnakeEngine} and
 * interpolated drawing, so motion stays smooth at any refresh rate. All UI is
 * drawn on the canvas (see {@link Ui}); the game never opens a dialog.
 */
@SuppressWarnings("serial")
public final class Game extends Canvas implements Runnable, SnakeEngine.Listener {
    static final int W = BoardRenderer.W, H = BoardRenderer.H;

    private final Database db = new Database();
    private final PreferencesManager prefs = new PreferencesManager(db);
    private final LeaderboardManager leaderboard = new LeaderboardManager(db);
    private final SoundPlayer sound = new SoundPlayer(prefs.soundEnabled);
    private final SnakeEngine engine = new SnakeEngine();
    private final SnakeEngine demo = new SnakeEngine();
    private final BoardRenderer boardRenderer = new BoardRenderer();
    private final HudRenderer hud = new HudRenderer();
    private final Effects fx = new Effects();
    private final Ui ui = new Ui();

    private GameState state = GameState.MENU;
    private GameState settingsReturn = GameState.MENU;
    private double time, stateTime, fade, shownScore, gameOverDelay = -1, goFlash, levelBanner;
    private int lastDigit;
    private Runnable pending;

    // Game-over / name entry
    private boolean nameEntry, manualEnd, newBest;
    private final StringBuilder name = new StringBuilder();
    private int savedRank, highlightRank;
    private long lastRunId = -1;

    // Window / rendering
    private final Object renderLock = new Object();
    private final ConcurrentLinkedQueue<Object[]> inputs = new ConcurrentLinkedQueue<>();
    private JFrame frame;
    private Rectangle windowedBounds;
    private volatile boolean fullscreen, needStrategy = true;
    private BufferStrategy strategy;
    private BufferedImage frameBuffer;
    private double viewScale = 1, viewX, viewY;
    private int fps, frames, targetFps = 60;
    private double fpsTimer;

    private static final int IN_KEY = 0, IN_CHAR = 1, IN_MOVE = 2, IN_PRESS = 3, IN_FOCUS_LOST = 4;

    Game() {
        engine.setListener(this);
        demo.setAutopilot(true);
        demo.setListener(new SnakeEngine.Listener() {
            @Override
            public void onEat(int x, int y, int points, int combo, boolean big) {
                fx.burst(BoardRenderer.cx(x), BoardRenderer.cy(y), new Color(240, 45, 70), 12, 4, 5);
            }
        });
        restartDemo();
        ui.setNavSound(() -> sound.play(Sfx.NAV));
        if (prefs.firstTimeUser) switchState(GameState.HELP);
    }

    // =====================================================================
    // Window & loop
    // =====================================================================

    void open() {
        frame = new JFrame("Snake");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(makeIcon());
        frame.getContentPane().setBackground(Color.BLACK);
        setBackground(Color.BLACK);
        setIgnoreRepaint(true);
        setFocusTraversalKeysEnabled(false);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double s = Math.min(1, Math.min((screen.width - 40) / (double) W, (screen.height - 60) / (double) H));
        setPreferredSize(new Dimension((int) (W * s), (int) (H * s)));
        frame.add(this);
        frame.pack();
        frame.setMinimumSize(new Dimension(680, 420));
        frame.setLocationRelativeTo(null);
        installListeners();
        frame.setVisible(true);
        requestFocus();

        Thread loop = new Thread(this, "game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void installListeners() {
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F11
                        || (e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown())) {
                    toggleFullscreen();
                    return;
                }
                inputs.add(new Object[] {IN_KEY, e.getKeyCode()});
            }
            @Override public void keyTyped(KeyEvent e) {
                inputs.add(new Object[] {IN_CHAR, e.getKeyChar()});
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { inputs.add(new Object[] {IN_MOVE, e.getX(), e.getY()}); }
            @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
            @Override public void mousePressed(MouseEvent e) {
                requestFocus();
                if (e.getButton() == MouseEvent.BUTTON1) inputs.add(new Object[] {IN_PRESS, e.getX(), e.getY()});
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { inputs.add(new Object[] {IN_FOCUS_LOST}); }
        });
    }

    private void toggleFullscreen() {
        EventQueue.invokeLater(() -> {
            synchronized (renderLock) {
                GraphicsConfiguration gc = frame.getGraphicsConfiguration();
                boolean fs = !fullscreen;
                if (fs) windowedBounds = frame.getBounds();
                frame.dispose();
                frame.setUndecorated(fs);
                frame.setBounds(fs ? gc.getBounds() : windowedBounds);
                frame.setVisible(true);
                fullscreen = fs;
                needStrategy = true;
                requestFocus();
            }
        });
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (true) {
            long start = System.nanoTime();
            double dt = Math.min(100, (start - last) / 1e6);
            last = start;
            synchronized (renderLock) {
                processInput();
                update(dt);
                present(dt);
                if (pending != null) {
                    Runnable p = pending;
                    pending = null;
                    p.run();
                }
            }
            countFps(dt);
            long target = start + 1_000_000_000L / targetFps;
            for (long rem; (rem = target - System.nanoTime()) > 0; ) {
                if (rem > 2_000_000) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                } else {
                    Thread.onSpinWait();
                }
            }
        }
    }

    private void present(double dt) {
        if (!isDisplayable() || getWidth() <= 0 || getHeight() <= 0) return;
        if (needStrategy || strategy == null) {
            createBufferStrategy(2);
            strategy = getBufferStrategy();
            needStrategy = false;
        }
        renderFrame(dt);
        do {
            do {
                Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                try {
                    g.setTransform(new AffineTransform());
                    g.drawImage(frameBuffer, 0, 0, null);
                } finally {
                    g.dispose();
                }
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
        Toolkit.getDefaultToolkit().sync();
    }

    /**
     * Draws the frame into a system-memory image at device resolution. Java2D's
     * software loops blend translucency quickly there, whereas blending straight
     * into a GDI/accelerated back buffer forces slow read-backs. One opaque blit
     * then puts the finished frame on screen.
     */
    void renderFrame(double dt) {
        double dpi = getGraphicsConfiguration() == null ? 1 : getGraphicsConfiguration().getDefaultTransform().getScaleX();
        int w = getWidth(), h = getHeight();
        int pw = (int) Math.ceil(w * dpi), ph = (int) Math.ceil(h * dpi);
        if (frameBuffer == null || frameBuffer.getWidth() != pw || frameBuffer.getHeight() != ph) {
            frameBuffer = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D g = frameBuffer.createGraphics();
        try {
            g.scale(dpi, dpi);
            render(g, w, h, dt);
        } finally {
            g.dispose();
        }
    }

    private void countFps(double dt) {
        frames++;
        fpsTimer += dt;
        if (fpsTimer >= 1000) {
            fps = frames;
            frames = 0;
            fpsTimer -= 1000;
            if (frame != null) {
                DisplayMode dm = frame.getGraphicsConfiguration().getDevice().getDisplayMode();
                int hz = dm.getRefreshRate();
                targetFps = hz >= 30 && hz <= 360 ? hz : 60;
            }
        }
    }

    // =====================================================================
    // Input
    // =====================================================================

    private void processInput() {
        Object[] in;
        while ((in = inputs.poll()) != null) {
            int type = (Integer) in[0];
            switch (type) {
                case IN_KEY -> onKey((Integer) in[1]);
                case IN_CHAR -> onChar((Character) in[1]);
                case IN_MOVE, IN_PRESS -> {
                    ui.mx = ((Integer) in[1] - viewX) / viewScale;
                    ui.my = ((Integer) in[2] - viewY) / viewScale;
                    ui.moved = true;
                    if (type == IN_PRESS) ui.clicked = true;
                }
                case IN_FOCUS_LOST -> {
                    if (state == GameState.PLAYING && !engine.isOver()) pause();
                }
                default -> { }
            }
        }
    }

    private void onKey(int k) {
        if (k == KeyEvent.VK_F3) {
            prefs.showFps = !prefs.showFps;
            prefs.save();
            return;
        }
        if (nameEntry && state == GameState.GAME_OVER) {
            if (k == KeyEvent.VK_ENTER) submitName();
            else if (k == KeyEvent.VK_ESCAPE) { nameEntry = false; ui.reset(0); }
            else if (k == KeyEvent.VK_BACK_SPACE && name.length() > 0) name.setLength(name.length() - 1);
            return;
        }
        if (k == KeyEvent.VK_M) {
            toggleSound();
            return;
        }
        if (state == GameState.PLAYING) {
            switch (k) {
                case KeyEvent.VK_UP, KeyEvent.VK_W -> engine.queueDirection(Direction.UP);
                case KeyEvent.VK_DOWN, KeyEvent.VK_S -> engine.queueDirection(Direction.DOWN);
                case KeyEvent.VK_LEFT, KeyEvent.VK_A -> engine.queueDirection(Direction.LEFT);
                case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> engine.queueDirection(Direction.RIGHT);
                case KeyEvent.VK_SPACE, KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> { if (!engine.isOver()) pause(); }
                default -> { }
            }
            return;
        }
        switch (k) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> ui.up = true;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> ui.down = true;
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> ui.left = true;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> ui.right = true;
            case KeyEvent.VK_ENTER -> ui.enter = true;
            case KeyEvent.VK_SPACE -> {
                if (state == GameState.PAUSED) resume();
                else ui.enter = true;
            }
            case KeyEvent.VK_P -> { if (state == GameState.PAUSED) resume(); }
            case KeyEvent.VK_R -> { if (state == GameState.GAME_OVER) startGame(); }
            case KeyEvent.VK_ESCAPE -> back();
            default -> { }
        }
    }

    private void onChar(char c) {
        if (!nameEntry || state != GameState.GAME_OVER) return;
        if (c >= 32 && c != 127 && c != '|' && name.length() < 16 && Character.isDefined(c)) name.append(c);
    }

    private void back() {
        switch (state) {
            case PAUSED -> resume();
            case SETTINGS -> switchState(settingsReturn);
            case HELP -> closeHelp();
            case LEADERBOARD, GAME_OVER -> switchState(GameState.MENU);
            default -> { }
        }
    }

    // =====================================================================
    // State changes
    // =====================================================================

    private void switchState(GameState s) {
        if ((s == GameState.MENU || s == GameState.HELP || s == GameState.LEADERBOARD)
                && (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER)) {
            fx.clear();
            restartDemo();
        }
        state = s;
        stateTime = 0;
        fade = 1;
        ui.reset(0);
    }

    /** Defers an action until after the current frame is drawn, and clicks. */
    private void act(Runnable r) {
        sound.play(Sfx.CLICK);
        pending = r;
    }

    private void restartDemo() {
        demo.start(GameMode.ENDLESS, 7, 1, true, 0);
    }

    private void startGame() {
        engine.start(prefs.gameMode, prefs.speedLevel, prefs.startingLevel, prefs.wrapWalls, leaderboard.getHighScore());
        fx.clear();
        shownScore = 0;
        gameOverDelay = -1;
        lastDigit = 0;
        goFlash = 0;
        levelBanner = 0;
        manualEnd = false;
        switchState(GameState.PLAYING);
        fade = 0.6;
    }

    private void pause() {
        state = GameState.PAUSED;
        stateTime = 0;
        ui.reset(0);
    }

    private void resume() {
        engine.addReadyDelay(1100);
        lastDigit = 0;
        state = GameState.PLAYING;
        stateTime = 0;
    }

    private void closeHelp() {
        if (prefs.firstTimeUser) {
            prefs.firstTimeUser = false;
            prefs.save();
        }
        switchState(GameState.MENU);
    }

    private void openSettings(GameState returnTo) {
        settingsReturn = returnTo;
        switchState(GameState.SETTINGS);
    }

    private void toggleSound() {
        prefs.soundEnabled = !prefs.soundEnabled;
        sound.setEnabled(prefs.soundEnabled);
        prefs.save();
        sound.play(Sfx.CLICK);
    }

    private void enterGameOver() {
        lastRunId = prefs.recordRun(engine, manualEnd);
        int score = engine.score();
        newBest = score > leaderboard.getHighScore() && score > 0;
        nameEntry = leaderboard.qualifies(score);
        name.setLength(0);
        name.append(prefs.lastName);
        savedRank = 0;
        switchState(GameState.GAME_OVER);
        fade = 0;
    }

    private void submitName() {
        String n = name.toString().trim();
        if (n.isEmpty()) n = "Player";
        prefs.lastName = n;
        prefs.save();
        savedRank = leaderboard.nameRun(lastRunId, n);
        highlightRank = savedRank;
        nameEntry = false;
        sound.play(Sfx.BEST);
        ui.reset(0);
    }

    // =====================================================================
    // Update
    // =====================================================================

    private void update(double dt) {
        time += dt;
        stateTime += dt;
        fade = Math.max(0, fade - dt / 260);
        goFlash = Math.max(0, goFlash - dt);
        levelBanner = Math.max(0, levelBanner - dt);
        if (state != GameState.PAUSED) fx.update(dt);

        switch (state) {
            case MENU, HELP, LEADERBOARD -> updateDemo(dt);
            case SETTINGS -> { if (settingsReturn == GameState.MENU) updateDemo(dt); }
            case PLAYING -> updatePlaying(dt);
            default -> { }
        }
        shownScore += (engine.score() - shownScore) * Math.min(1, dt / 110);
        if (Math.abs(engine.score() - shownScore) < 0.05) shownScore = engine.score();
    }

    private void updateDemo(double dt) {
        demo.update(dt);
        if (demo.length() > 60) restartDemo();
    }

    private void updatePlaying(double dt) {
        int digit = engine.readyMs() > 0 ? (int) Math.ceil(engine.readyMs() / 600) : 0;
        if (digit != lastDigit) {
            if (digit > 0) sound.play(Sfx.TICK);
            else if (lastDigit > 0) {
                sound.play(Sfx.GO);
                goFlash = 500;
            }
            lastDigit = digit;
        }
        engine.update(dt);
        if (engine.isOver()) {
            if (gameOverDelay < 0) {
                gameOverDelay = 1100;
                sound.play(engine.endReason() == SnakeEngine.EndReason.CHALLENGES_CLEARED ? Sfx.WIN : Sfx.OVER);
            } else if ((gameOverDelay -= dt) <= 0) {
                enterGameOver();
            }
        }
    }

    // ---- engine events ----

    @Override
    public void onEat(int x, int y, int points, int combo, boolean big) {
        double px = big ? BoardRenderer.BX + (x + 1) * BoardRenderer.CELL : BoardRenderer.cx(x);
        double py = big ? BoardRenderer.BY + (y + 1) * BoardRenderer.CELL : BoardRenderer.cy(y);
        if (big) {
            sound.play(Sfx.BIG);
            fx.burst(px, py, Theme.GOLD, 40, 7, 7);
            fx.shake(0.25);
            fx.popup("+" + points, px, py - 30, Theme.GOLD, Theme.H1);
        } else {
            sound.playEat(combo);
            fx.burst(px, py, new Color(255, 70, 90), 14 + combo * 3, 4 + combo * 0.5, 5);
            fx.popup("+" + points, px, py - 22, combo >= 3 ? Theme.ORANGE : Color.WHITE, Theme.H2);
        }
    }

    @Override
    public void onPowerUp(PowerUpType type, int x, int y) {
        sound.play(Sfx.POWER);
        fx.ring(BoardRenderer.cx(x), BoardRenderer.cy(y), type.color, 24, 6, 5);
        fx.flash(type.color, 0.5);
        String extra = type == PowerUpType.EXTRA_LIFE && engine.lives() >= SnakeEngine.MAX_LIVES ? " (max, +10 pts)" : "";
        fx.toast(type.label + " — " + type.description + extra, type.color);
    }

    @Override
    public void onShieldBreak(int x, int y) {
        sound.play(Sfx.SHIELD);
        fx.ring(BoardRenderer.cx(x), BoardRenderer.cy(y), PowerUpType.SHIELD.color, 30, 7, 6);
        if (prefs.screenShake) fx.shake(0.45);
        fx.toast("Shield absorbed the crash — turn!", PowerUpType.SHIELD.color);
    }

    @Override
    public void onCrash(int x, int y, boolean fatal) {
        sound.play(Sfx.HIT);
        fx.burst(BoardRenderer.cx(x), BoardRenderer.cy(y), Theme.RED, 36, 7, 6);
        if (prefs.screenShake) fx.shake(fatal ? 0.9 : 0.65);
        fx.flash(Theme.RED, fatal ? 0.9 : 0.6);
        if (!fatal) {
            switch (engine.mode()) {
                case CLASSIC -> fx.toast(engine.lives() == 1 ? "Crashed! Last life — careful"
                    : "Crashed! " + engine.lives() + " lives left", Theme.RED);
                case TIME_ATTACK -> fx.toast("Crashed! −5 seconds", Theme.RED);
                default -> fx.toast("Crashed! Respawning…", Theme.ORANGE);
            }
        }
    }

    @Override
    public void onLevelUp(int level) {
        sound.play(Sfx.LEVEL);
        fx.flash(Theme.CYAN, 0.5);
        fx.ring(BoardRenderer.cx(engine.xs()[0]), BoardRenderer.cy(engine.ys()[0]), Theme.CYAN, 36, 8, 5);
        levelBanner = SnakeEngine.READY_MS + 400;
    }

    @Override
    public void onChallengeComplete(int index, boolean allDone) {
        sound.play(allDone ? Sfx.WIN : Sfx.BEST);
        fx.flash(Theme.GREEN, 0.5);
        fx.toast(allDone ? "All challenges cleared!" : "Challenge " + (index + 1) + " complete! +10", Theme.GREEN);
    }

    @Override
    public void onNewBest(int score) {
        sound.play(Sfx.BEST);
        fx.toast("New high score!", Theme.GOLD);
    }

    // =====================================================================
    // Rendering
    // =====================================================================

    void render(Graphics2D g, int w, int h, double dt) {
        Theme.quality(g);
        Theme.Board theme = Theme.board(prefs.theme);
        viewScale = Math.min(w / (double) W, h / (double) H);
        viewX = (w - W * viewScale) / 2;
        viewY = (h - H * viewScale) / 2;
        // Clear only what the scene won't cover: letterbox bars, or everything while shaking.
        boolean shaking = prefs.screenShake && (fx.shakeX() != 0 || fx.shakeY() != 0);
        if (shaking || viewX >= 1 || viewY >= 1) Gfx.fillRectFast(g, theme.bgBottom(), 0, 0, w, h);
        AffineTransform base = g.getTransform();
        g.translate(viewX, viewY);
        g.scale(viewScale, viewScale);
        g.clipRect(0, 0, W, H);

        ui.dt = dt;
        ui.begin();
        switch (state) {
            case MENU -> { drawDemo(g); drawMenu(g); }
            case HELP -> { drawDemo(g); drawHelp(g); }
            case LEADERBOARD -> { drawDemo(g); drawLeaderboard(g); }
            case SETTINGS -> {
                if (settingsReturn == GameState.PAUSED) drawGame(g, false);
                else drawDemo(g);
                drawSettings(g);
            }
            case PLAYING -> drawGame(g, true);
            case PAUSED -> { drawGame(g, false); drawPause(g); }
            case GAME_OVER -> { drawGame(g, false); drawGameOver(g); }
        }
        ui.end();

        if (fade > 0) {
            Gfx.fillRectFast(g, new Color(0, 0, 0, (int) (200 * fade * fade)), 0, 0, W, H);
        }
        if (prefs.showFps) {
            g.setFont(Theme.MONO);
            g.setColor(new Color(255, 255, 255, 140));
            Gfx.right(g, fps + " FPS", W - 12, H - 10);
        }
        g.setTransform(base);
    }

    /** How much the board is darkened behind the current screen (baked into the cached layer). */
    private double backdropDim() {
        return switch (state) {
            case PLAYING -> 0;
            case PAUSED -> 0.55;
            case GAME_OVER -> 0.65;
            case SETTINGS -> 0.75;
            default -> 0.62;
        };
    }

    private void drawDemo(Graphics2D g) {
        double dim = backdropDim();
        boardRenderer.drawStatic(g, demo, prefs.theme, dim);
        boardRenderer.setVeil(Theme.board(prefs.theme).bgBottom(), dim);
        boardRenderer.drawItems(g, demo, demo.alpha(), time);
        boardRenderer.drawSnake(g, demo, Theme.skin(prefs.skin), demo.alpha(), time, false);
        boardRenderer.setVeil(null, 0);
        fx.drawWorld(g);
    }

    private void drawGame(Graphics2D g, boolean interactive) {
        AffineTransform t = g.getTransform();
        if (prefs.screenShake) g.translate(fx.shakeX(), fx.shakeY());
        double dim = backdropDim();
        boardRenderer.drawStatic(g, engine, prefs.theme, dim);
        boardRenderer.setVeil(Theme.board(prefs.theme).bgBottom(), dim);
        double alpha = engine.alpha();
        boardRenderer.drawItems(g, engine, alpha, time);
        boardRenderer.drawSnake(g, engine, Theme.skin(prefs.skin), alpha, time, engine.isOver());
        boardRenderer.setVeil(null, 0);
        if (dim == 0) fx.drawWorld(g);
        g.setTransform(t);

        hud.draw(g, engine, leaderboard.getHighScore(), shownScore, time);
        if (interactive) {
            double s = 42, y = 17, x = W - 20 - s;
            if (ui.iconButton(g, x, y, s, Theme.GREEN, gg -> Gfx.pauseIcon(gg, W - 20 - 21, 17 + 21, 16)))
                act(this::pause);
            if (ui.iconButton(g, x - s - 10, y, s, Theme.GOLD,
                    gg -> Gfx.speakerIcon(gg, W - 20 - 21 - 52, 17 + 21, 18, prefs.soundEnabled)))
                toggleSound();
        }
        if (state == GameState.PLAYING && !engine.isOver()) drawCountdown(g);
        fx.drawOverlay(g, W / 2.0, BoardRenderer.BY + 14, W, H);
    }

    private void drawCountdown(Graphics2D g) {
        // upper third of the board, so the countdown never sits on the freshly spawned snake
        double cx = BoardRenderer.BX + BoardRenderer.BW / 2.0, cy = BoardRenderer.BY + 175;
        double ready = engine.readyMs();
        if (ready > 0) {
            g.setColor(new Color(0, 0, 0, 80));
            Gfx.fillCircle(g, cx, cy - 10, 96);
            int digit = (int) Math.ceil(ready / 600);
            double frac = (ready % 600) / 600; // 1 -> 0 within each digit
            double scale = 0.8 + 0.5 * frac;
            g.setFont(Theme.HUGE.deriveFont((float) (150 * scale)));
            Gfx.glow(g, Theme.GREEN, cx, cy - 10, 160, 0.25 * frac + 0.1);
            g.setColor(Theme.alpha(Color.WHITE, 0.35 + 0.65 * Math.min(1, frac * 2)));
            Gfx.centerIn(g, String.valueOf(digit), cx - 200, cy - 110, 400, 200);
            g.setFont(Theme.H3);
            g.setColor(Theme.TEXT_DIM);
            String sub = levelBanner > 0 ? "" : engine.elapsedMs() == 0
                ? engine.mode().label + "  •  " + engine.mode().description : "Get ready";
            if (!sub.isEmpty()) Gfx.center(g, sub, cx, cy + 110);
        }
        if (levelBanner > 0) {
            double a = Math.min(1, levelBanner / 300);
            g.setFont(Theme.H1);
            g.setColor(Theme.alpha(Theme.CYAN, a));
            Gfx.center(g, "LEVEL " + engine.level(), cx, cy + 118);
            g.setFont(Theme.BODY_BOLD);
            g.setColor(Theme.alpha(Theme.TEXT_DIM, a));
            Gfx.center(g, "Faster snake  •  new obstacles", cx, cy + 146);
        }
        if (goFlash > 0) {
            double p = 1 - goFlash / 500;
            g.setFont(Theme.HUGE.deriveFont((float) (110 + 80 * p)));
            g.setColor(Theme.alpha(Theme.GREEN, 1 - p));
            Gfx.centerIn(g, "GO!", cx - 300, cy - 110, 600, 200);
        }
    }

    // ---- Menu ----

    private void drawMenu(Graphics2D g) {
        double cx = W / 2.0;
        double intro = easeOut(Math.min(1, stateTime / 500));
        double ty = 190 - (1 - intro) * 20;

        Gfx.glow(g, Theme.GREEN, cx, ty - 40, 240, 0.22);
        String title = "SNAKE";
        int tw = Gfx.width(g, Theme.TITLE, title);
        Gfx.cachedText(g, title, Theme.TITLE, new Color(0, 0, 0, 90), cx + 4, ty + 6);
        Gfx.cachedText(g, title, Theme.TITLE, TITLE_PAINT(tw), cx, ty);

        g.setFont(Theme.H3);
        g.setColor(Theme.TEXT_DIM);
        Gfx.center(g, "MODERN ARCADE EDITION", cx, ty + 36);

        int best = leaderboard.getHighScore();
        String bestText = best > 0 ? "BEST  " + best : "NO HIGH SCORE YET";
        g.setFont(Theme.BODY_BOLD);
        double bw = Gfx.width(g, Theme.BODY_BOLD, bestText) + 50;
        g.setColor(Theme.alpha(Theme.GOLD, 26));
        Gfx.fillRound(g, cx - bw / 2, ty + 54, bw, 32, 16);
        g.setColor(Theme.GOLD);
        Gfx.star(g, cx - bw / 2 + 20, ty + 70, 14);
        g.drawString(bestText, (float) (cx - bw / 2 + 34), (float) (ty + 75));

        double bx = cx - 170, by = 312, bwid = 340, bh = 52, gap = 64;
        if (ui.button(g, "PLAY  •  " + prefs.gameMode.label.toUpperCase(), bx, by, bwid, bh, Theme.GREEN, true))
            act(this::startGame);
        if (ui.button(g, "SETTINGS", bx, by + gap, bwid, bh, Theme.CYAN, false))
            act(() -> openSettings(GameState.MENU));
        if (ui.button(g, "LEADERBOARD", bx, by + gap * 2, bwid, bh, Theme.GOLD, false))
            act(() -> switchState(GameState.LEADERBOARD));
        if (ui.button(g, "HOW TO PLAY", bx, by + gap * 3, bwid, bh, Theme.PURPLE, false))
            act(() -> switchState(GameState.HELP));
        if (ui.button(g, "QUIT", bx, by + gap * 4, bwid, bh, Theme.RED, false))
            act(() -> System.exit(0));

        if (!db.isPersistent()) {
            g.setFont(Theme.BODY_BOLD);
            g.setColor(Theme.ORANGE);
            Gfx.center(g, "Database unavailable (is Snake already open?) — progress won't be saved", cx, H - 70);
        }
        drawHints(g, H - 40, new String[][] {
            {"↑↓", "Navigate"}, {"ENTER", "Select"}, {"M", "Mute"}, {"F11", "Fullscreen"}, {"F3", "FPS"}});
    }

    private void drawHints(Graphics2D g, double y, String[][] hints) {
        double total = 0;
        for (String[] h : hints) {
            g.setFont(Theme.SMALL);
            total += Math.max(26, Gfx.width(g, Theme.SMALL, h[0]) + 16) + 8 + Gfx.width(g, Theme.BODY, h[1]) + 26;
        }
        double x = (W - total) / 2;
        for (String[] h : hints) {
            x += Gfx.keycap(g, h[0], x, y - 17) + 8;
            g.setFont(Theme.BODY);
            g.setColor(Theme.TEXT_DIM);
            g.drawString(h[1], (float) x, (float) y);
            x += Gfx.width(g, Theme.BODY, h[1]) + 26;
        }
    }

    // ---- Settings ----

    private void drawSettings(Graphics2D g) {
        double cw = 800, ch = 650, x = (W - cw) / 2, y = (H - ch) / 2;
        Gfx.panel(g, x, y, cw, ch, 24, Theme.PANEL, Theme.PANEL_BORDER);
        g.setFont(Theme.H2);
        g.setColor(Theme.TEXT);
        g.drawString("Settings", (float) (x + 32), (float) (y + 50));
        if (settingsReturn == GameState.PAUSED) {
            g.setFont(Theme.BODY);
            g.setColor(Theme.TEXT_FAINT);
            Gfx.right(g, "Mode, speed, level and walls apply to your next run", x + cw - 32, y + 48);
        }

        double rx = x + 20, rw = cw - 40, rh = 48, ry = y + 76;
        int i = 0;
        GameMode[] modes = GameMode.values();
        String[] modeNames = new String[modes.length];
        for (int k = 0; k < modes.length; k++) modeNames[k] = modes[k].label;
        int m = ui.options(g, "Game mode", modeNames, prefs.gameMode.ordinal(), rx, ry + rh * i++, rw, rh, Theme.GREEN, null);
        if (m != prefs.gameMode.ordinal()) { prefs.gameMode = modes[m]; prefs.save(); }
        g.setFont(Theme.BODY);
        g.setColor(Theme.TEXT_FAINT);
        g.drawString(prefs.gameMode.description, (float) (rx + 190), (float) (ry + rh * i + 14));
        ry += 24;

        String[] speeds = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        int sp = ui.options(g, "Speed", speeds, prefs.speedLevel - 1, rx, ry + rh * i++, rw, rh, Theme.CYAN, null) + 1;
        if (sp != prefs.speedLevel) { prefs.speedLevel = sp; prefs.save(); }

        String[] lv = new String[SnakeEngine.MAX_LEVEL];
        for (int k = 0; k < lv.length; k++) lv[k] = String.valueOf(k + 1);
        int sl = ui.options(g, "Starting level", lv, prefs.startingLevel - 1, rx, ry + rh * i++, rw, rh, Theme.CYAN, null) + 1;
        if (sl != prefs.startingLevel) { prefs.startingLevel = sl; prefs.save(); }

        int wr = ui.options(g, "Walls", new String[] {"Solid (crash)", "Wrap-around"}, prefs.wrapWalls ? 1 : 0,
            rx, ry + rh * i++, rw, rh, Theme.CYAN, null);
        if ((wr == 1) != prefs.wrapWalls) { prefs.wrapWalls = wr == 1; prefs.save(); }

        String[] skins = new String[Theme.SKINS.length];
        for (int k = 0; k < skins.length; k++) skins[k] = Theme.SKINS[k].name();
        int sk = ui.options(g, "Snake skin", skins, Math.floorMod(prefs.skin, skins.length), rx, ry + rh * i++, rw, rh,
            Theme.PURPLE, this::paintSkinChip);
        if (sk != prefs.skin) { prefs.skin = sk; prefs.save(); }

        String[] boards = new String[Theme.BOARDS.length];
        for (int k = 0; k < boards.length; k++) boards[k] = Theme.BOARDS[k].name();
        int bd = ui.options(g, "Board theme", boards, Math.floorMod(prefs.theme, boards.length), rx, ry + rh * i++, rw, rh,
            Theme.PURPLE, this::paintBoardChip);
        if (bd != prefs.theme) { prefs.theme = bd; prefs.save(); }

        String[] onOff = {"Off", "On"};
        int so = ui.options(g, "Sound", onOff, prefs.soundEnabled ? 1 : 0, rx, ry + rh * i++, rw, rh, Theme.GOLD, null);
        if ((so == 1) != prefs.soundEnabled) toggleSound();
        int sh = ui.options(g, "Screen shake", onOff, prefs.screenShake ? 1 : 0, rx, ry + rh * i++, rw, rh, Theme.GOLD, null);
        if ((sh == 1) != prefs.screenShake) { prefs.screenShake = sh == 1; prefs.save(); }
        int fp = ui.options(g, "Show FPS", onOff, prefs.showFps ? 1 : 0, rx, ry + rh * i++, rw, rh, Theme.GOLD, null);
        if ((fp == 1) != prefs.showFps) { prefs.showFps = fp == 1; prefs.save(); }

        String label = settingsReturn == GameState.PAUSED ? "BACK TO GAME" : "DONE";
        if (ui.button(g, label, x + cw / 2 - 130, y + ch - 70, 260, 48, Theme.GREEN, true))
            act(() -> switchState(settingsReturn));
    }

    private void paintSkinChip(Graphics2D g, int i, double x, double y, double w, double h, boolean sel) {
        Theme.Skin s = Theme.SKINS[i];
        double pad = 10, bx = x + pad, bw = w - pad * 2, bh = 12, by = y + h / 2 - bh / 2;
        if (s.rainbow()) {
            g.setPaint(new LinearGradientPaint((float) bx, 0, (float) (bx + bw), 0, new float[] {0f, 0.25f, 0.5f, 0.75f, 1f},
                new Color[] {new Color(255, 90, 90), new Color(255, 220, 80), new Color(80, 255, 140),
                    new Color(80, 160, 255), new Color(220, 100, 255)}));
        } else {
            g.setPaint(new GradientPaint((float) bx, 0, s.head(), (float) (bx + bw), 0, s.tail()));
        }
        Gfx.fillRound(g, bx, by, bw, bh, bh / 2);
        if (sel) {
            g.setColor(new Color(10, 14, 22));
            g.setStroke(Gfx.stroke(2f));
            Gfx.drawRound(g, bx - 3, by - 3, bw + 6, bh + 6, bh / 2 + 3);
        }
    }

    private void paintBoardChip(Graphics2D g, int i, double x, double y, double w, double h, boolean sel) {
        Theme.Board b = Theme.BOARDS[i];
        double pad = 5, sx = x + pad, sy = y + pad, sw = w - pad * 2, sh = h - pad * 2;
        g.setColor(b.cellA());
        Gfx.fillRound(g, sx, sy, sw, sh, 6);
        g.setColor(b.cellB());
        int cells = 8;
        double cs = sw / cells;
        for (int k = 0; k < cells; k += 2) g.fillRect((int) (sx + k * cs), (int) (sy + 3), (int) cs, (int) (sh - 6));
        g.setFont(Theme.SMALL);
        g.setColor(sel ? Color.WHITE : Theme.TEXT_DIM);
        Gfx.centerIn(g, b.name(), x, y, w, h);
        if (sel) {
            g.setColor(Color.WHITE);
            g.setStroke(Gfx.stroke(2f));
            Gfx.drawRound(g, sx, sy, sw, sh, 6);
        }
    }

    // ---- Leaderboard ----

    private void drawLeaderboard(Graphics2D g) {
        double cw = 1000, ch = 620, x = (W - cw) / 2, y = (H - ch) / 2;
        Gfx.panel(g, x, y, cw, ch, 24, Theme.PANEL, Theme.PANEL_BORDER);
        g.setFont(Theme.H2);
        g.setColor(Theme.TEXT);
        g.drawString("Leaderboard", (float) (x + 32), (float) (y + 50));

        double lx = x + 24, ly = y + 76, lw = 600;
        List<Database.ScoreRow> scores = leaderboard.getScores();
        if (scores.isEmpty()) {
            g.setFont(Theme.H3);
            g.setColor(Theme.TEXT_FAINT);
            Gfx.center(g, "No scores yet — play a round to claim #1!", lx + lw / 2, ly + 200);
        }
        Color[] medals = {Theme.GOLD, new Color(200, 210, 225), new Color(215, 140, 70)};
        for (int i = 0; i < scores.size(); i++) {
            Database.ScoreRow e = scores.get(i);
            double ry = ly + i * 46;
            double appear = easeOut(Math.max(0, Math.min(1, (stateTime - i * 35) / 300)));
            double ox = (1 - appear) * 30;
            boolean hl = highlightRank == i + 1;
            g.setColor(hl ? Theme.alpha(Theme.GREEN, 40) : new Color(255, 255, 255, i % 2 == 0 ? 10 : 4));
            Gfx.fillRound(g, lx + ox, ry, lw, 40, 12);
            Color rc = i < 3 ? medals[i] : Theme.TEXT_FAINT;
            if (i < 3) {
                g.setColor(Theme.alpha(rc, 50));
                Gfx.fillCircle(g, lx + ox + 26, ry + 20, 14);
            }
            g.setColor(rc);
            g.setFont(Theme.BODY_BOLD);
            Gfx.centerIn(g, String.valueOf(i + 1), lx + ox + 12, ry, 28, 40);
            g.setColor(Theme.TEXT);
            g.drawString(e.name(), (float) (lx + ox + 56), (float) (ry + 26));
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            g.drawString(e.mode().label.toUpperCase(), (float) (lx + ox + 330), (float) (ry + 25));
            g.setFont(Theme.H3);
            g.setColor(i == 0 ? Theme.GOLD : Theme.GREEN);
            Gfx.right(g, String.valueOf(e.score()), lx + ox + lw - 20, ry + 27);
        }

        // lifetime stats
        double sx = x + cw - 24 - 340, sy = ly, sw = 340, sh = 456;
        g.setColor(new Color(255, 255, 255, 8));
        Gfx.fillRound(g, sx, sy, sw, sh, 16);
        g.setFont(Theme.H3);
        g.setColor(Theme.TEXT);
        g.drawString("Lifetime stats", (float) (sx + 22), (float) (sy + 36));
        String[][] stats = {
            {"Games played", String.valueOf(prefs.gamesPlayed)},
            {"Apples eaten", String.valueOf(prefs.totalApples)},
            {"Best combo", "x" + prefs.bestCombo},
            {"Longest snake", String.valueOf(prefs.longestSnake)},
            {"Time played", formatTime(prefs.totalPlayMs)},
            {"High score", String.valueOf(leaderboard.getHighScore())},
        };
        for (int i = 0; i < stats.length; i++) {
            double ry = sy + 70 + i * 62;
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            g.drawString(stats[i][0].toUpperCase(), (float) (sx + 22), (float) ry);
            g.setFont(Theme.H2);
            g.setColor(i == 5 ? Theme.GOLD : Theme.TEXT);
            g.drawString(stats[i][1], (float) (sx + 22), (float) (ry + 32));
        }
        g.setFont(Theme.SMALL);
        g.setColor(db.isPersistent() ? Theme.TEXT_FAINT : Theme.ORANGE);
        g.drawString(db.isPersistent() ? "Database: " + db.location()
            : "Database unavailable (is Snake already open?) — this session won't be saved",
            (float) (x + 32), (float) (y + ch - 14));
        if (ui.button(g, "BACK", x + cw / 2 - 120, y + ch - 66, 240, 46, Theme.GOLD, false))
            act(() -> switchState(GameState.MENU));
    }

    // ---- Help ----

    private void drawHelp(Graphics2D g) {
        double cw = 1120, ch = 640, x = (W - cw) / 2, y = (H - ch) / 2;
        Gfx.panel(g, x, y, cw, ch, 24, Theme.PANEL, Theme.PANEL_BORDER);
        g.setFont(Theme.H2);
        g.setColor(Theme.TEXT);
        g.drawString(prefs.firstTimeUser ? "Welcome to Snake!" : "How to play", (float) (x + 32), (float) (y + 50));
        g.setFont(Theme.BODY);
        g.setColor(Theme.TEXT_DIM);
        g.drawString("Eat apples, grow longer, chain combos and don't bite yourself.", (float) (x + 32), (float) (y + 76));

        double colW = (cw - 64 - 40) / 3, c1 = x + 32, c2 = c1 + colW + 20, c3 = c2 + colW + 20, top = y + 112;

        // Column 1: controls
        sectionTitle(g, "Controls", c1, top, Theme.CYAN);
        String[][] keys = {
            {"↑ ↓ ← →", "Steer (or W A S D)"}, {"SPACE", "Pause / resume"}, {"ESC", "Back / pause"},
            {"ENTER", "Select in menus"}, {"M", "Mute sound"}, {"F11", "Toggle fullscreen"}, {"F3", "FPS counter"}};
        for (int i = 0; i < keys.length; i++) {
            double ky = top + 26 + i * 38;
            double kw = Gfx.keycap(g, keys[i][0], c1, ky);
            g.setFont(Theme.BODY);
            g.setColor(Theme.TEXT);
            g.drawString(keys[i][1], (float) (c1 + Math.max(kw, 96) + 12), (float) (ky + 17));
        }
        g.setFont(Theme.BODY);
        g.setColor(Theme.TEXT_FAINT);
        g.drawString("Turns are buffered, so quick", (float) c1, (float) (top + 310));
        g.drawString("double-taps never get lost.", (float) c1, (float) (top + 330));

        // Column 2: items
        sectionTitle(g, "Food & power-ups", c2, top, Theme.GOLD);
        double iy = top + 40;
        g.setColor(new Color(240, 45, 70));
        Gfx.glow(g, new Color(240, 45, 70), c2 + 14, iy, 22, 0.5);
        Gfx.fillCircle(g, c2 + 14, iy, 11);
        itemLine(g, c2, iy, "Apple", "+1, more with combos");
        iy += 40;
        g.setColor(Theme.GOLD);
        Gfx.glow(g, Theme.GOLD, c2 + 14, iy, 24, 0.6);
        Gfx.fillCircle(g, c2 + 14, iy, 13);
        itemLine(g, c2, iy, "Golden apple", "+5, appears for a few secs");
        for (PowerUpType t : PowerUpType.values()) {
            iy += 40;
            g.setColor(new Color(16, 20, 34));
            Gfx.fillCircle(g, c2 + 14, iy, 14);
            g.setColor(t.color);
            g.setStroke(Gfx.stroke(2f));
            Gfx.drawCircle(g, c2 + 14, iy, 14);
            Gfx.powerIcon(g, t, c2 + 14, iy, 16);
            itemLine(g, c2, iy, t.label, t.description);
        }

        // Column 3: modes
        sectionTitle(g, "Modes", c3, top, Theme.GREEN);
        double my = top + 30;
        for (GameMode m : GameMode.values()) {
            g.setFont(Theme.BODY_BOLD);
            g.setColor(Theme.TEXT);
            g.drawString(m.label, (float) c3, (float) (my + 12));
            g.setFont(Theme.BODY);
            g.setColor(Theme.TEXT_DIM);
            drawWrapped(g, m.description, c3, my + 32, colW, 20);
            my += 74;
        }
        g.setFont(Theme.BODY);
        g.setColor(Theme.TEXT_FAINT);
        drawWrapped(g, "Eat within " + (int) (SnakeEngine.COMBO_MS / 1000) + "s of the last apple to build a combo. Every "
            + SnakeEngine.POINTS_PER_LEVEL + " points you level up.", c3, my + 10, colW, 20);

        String label = prefs.firstTimeUser ? "LET'S PLAY" : "BACK";
        if (ui.button(g, label, x + cw / 2 - 130, y + ch - 70, 260, 48, Theme.GREEN, true))
            act(this::closeHelp);
    }

    private void sectionTitle(Graphics2D g, String s, double x, double y, Color c) {
        g.setColor(c);
        Gfx.fillRound(g, x, y - 13, 4, 16, 2);
        g.setFont(Theme.H3);
        g.setColor(Theme.TEXT);
        g.drawString(s, (float) (x + 12), (float) y);
    }

    private void itemLine(Graphics2D g, double x, double y, String title, String desc) {
        g.setFont(Theme.BODY_BOLD);
        g.setColor(Theme.TEXT);
        g.drawString(title, (float) (x + 40), (float) (y - 2));
        g.setFont(Theme.SMALL);
        g.setColor(Theme.TEXT_DIM);
        g.drawString(desc, (float) (x + 40), (float) (y + 14));
    }

    private void drawWrapped(Graphics2D g, String text, double x, double y, double maxW, double lineH) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(test) > maxW && !line.isEmpty()) {
                g.drawString(line.toString(), (float) x, (float) y);
                y += lineH;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (!line.isEmpty()) g.drawString(line.toString(), (float) x, (float) y);
    }

    // ---- Pause ----

    private void drawPause(Graphics2D g) {
        double a = easeOut(Math.min(1, stateTime / 200));
        double cw = 400, ch = 452, x = (W - cw) / 2, y = (H - ch) / 2 + (1 - a) * 20;
        Gfx.panel(g, x, y, cw, ch, 24, Theme.PANEL, Theme.PANEL_BORDER);
        g.setFont(Theme.H1);
        g.setColor(Theme.TEXT);
        Gfx.center(g, "Paused", W / 2.0, y + 66);
        g.setFont(Theme.BODY);
        g.setColor(Theme.TEXT_DIM);
        Gfx.center(g, "Score " + engine.score() + "   •   Length " + engine.length() + "   •   "
            + formatTime((long) engine.elapsedMs()), W / 2.0, y + 98);

        double bx = x + 40, bw = cw - 80, bh = 48, by = y + 128, gap = 60;
        if (ui.button(g, "RESUME", bx, by, bw, bh, Theme.GREEN, true)) act(this::resume);
        if (ui.button(g, "RESTART", bx, by + gap, bw, bh, Theme.CYAN, false)) act(this::startGame);
        if (ui.button(g, "SETTINGS", bx, by + gap * 2, bw, bh, Theme.PURPLE, false)) act(() -> openSettings(GameState.PAUSED));
        if (ui.button(g, "END RUN & SAVE SCORE", bx, by + gap * 3, bw, bh, Theme.GOLD, false)) act(() -> {
            manualEnd = true;
            enterGameOver();
        });
        if (ui.button(g, "MAIN MENU", bx, by + gap * 4, bw, bh, Theme.RED, false)) act(() -> switchState(GameState.MENU));
    }

    // ---- Game over ----

    private void drawGameOver(Graphics2D g) {
        double a = easeOut(Math.min(1, stateTime / 350));

        double cw = 620, ch = nameEntry ? 590 : savedRank > 0 ? 520 : 470, x = (W - cw) / 2, y = (H - ch) / 2 + (1 - a) * 30;
        String title;
        Color tc;
        SnakeEngine.EndReason r = engine.endReason();
        if (manualEnd || engine.mode() == GameMode.ENDLESS) { title = "Run complete"; tc = Theme.CYAN; }
        else if (r == SnakeEngine.EndReason.CHALLENGES_CLEARED) { title = "All challenges cleared!"; tc = Theme.GOLD; }
        else if (r == SnakeEngine.EndReason.TIME_UP) { title = "Time's up!"; tc = Theme.ORANGE; }
        else if (r == SnakeEngine.EndReason.CHALLENGE_FAILED && engine.getChallengeLeftMs() <= 0) { title = "Out of time"; tc = Theme.RED; }
        else { title = "Game over"; tc = Theme.RED; }

        Gfx.panel(g, x, y, cw, ch, 26, Theme.PANEL, Theme.alpha(tc, 90));
        Gfx.glow(g, tc, W / 2.0, y + 40, 200, 0.18);
        g.setFont(Theme.H1);
        g.setColor(tc);
        Gfx.center(g, title, W / 2.0, y + 66);

        // score count-up
        double countT = easeOut(Math.min(1, stateTime / 900));
        int shown = (int) Math.round(engine.score() * countT);
        g.setFont(Theme.HUGE.deriveFont(84f));
        g.setColor(Theme.TEXT);
        Gfx.center(g, String.valueOf(shown), W / 2.0, y + 160);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.TEXT_FAINT);
        Gfx.center(g, "FINAL SCORE", W / 2.0, y + 184);
        if (newBest) {
            double p = 1 + 0.06 * Math.sin(time * 0.008);
            g.setFont(Theme.BODY_BOLD.deriveFont((float) (15 * p)));
            double bw = 170;
            g.setColor(Theme.alpha(Theme.GOLD, 40));
            Gfx.fillRound(g, W / 2.0 - bw / 2, y + 196, bw, 28, 14);
            g.setColor(Theme.GOLD);
            Gfx.centerIn(g, "NEW HIGH SCORE!", W / 2.0 - bw / 2, y + 196, bw, 28);
        }

        String[][] stats = {
            {"LEVEL", String.valueOf(engine.level())},
            {"LENGTH", String.valueOf(engine.maxLength())},
            {"APPLES", String.valueOf(engine.applesEaten() + engine.bigEaten())},
            {"BEST COMBO", "x" + engine.bestCombo()},
            {"POWER-UPS", String.valueOf(engine.powerUps())},
            {engine.mode() == GameMode.CHALLENGE ? "CHALLENGES" : "TIME",
                engine.mode() == GameMode.CHALLENGE
                    ? (engine.getChallengeIndex() + (r == SnakeEngine.EndReason.CHALLENGES_CLEARED ? 1 : 0)) + "/" + Challenge.ALL.size()
                    : formatTime((long) engine.elapsedMs())},
        };
        double gx = x + 40, gy = y + 238, gw = (cw - 80) / 3;
        for (int i = 0; i < stats.length; i++) {
            double sx = gx + (i % 3) * gw, sy = gy + (i / 3) * 62;
            g.setColor(new Color(255, 255, 255, 8));
            Gfx.fillRound(g, sx + 4, sy, gw - 8, 54, 12);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            Gfx.center(g, stats[i][0], sx + gw / 2, sy + 20);
            g.setFont(Theme.H3);
            g.setColor(Theme.TEXT);
            Gfx.center(g, stats[i][1], sx + gw / 2, sy + 44);
        }

        double ny = gy + 136;
        if (nameEntry) {
            g.setFont(Theme.BODY_BOLD);
            g.setColor(Theme.GREEN);
            Gfx.center(g, "You made the leaderboard! Enter your name:", W / 2.0, ny + 4);
            double fw = 340, fx0 = W / 2.0 - fw / 2, fy = ny + 16;
            g.setColor(new Color(0, 0, 0, 90));
            Gfx.fillRound(g, fx0, fy, fw, 44, 12);
            g.setColor(Theme.alpha(Theme.GREEN, 180 + (int) (60 * Math.sin(time * 0.006))));
            g.setStroke(Gfx.stroke(1.6f));
            Gfx.drawRound(g, fx0 + 0.5, fy + 0.5, fw - 1, 43, 12);
            g.setFont(Theme.H3);
            g.setColor(Theme.TEXT);
            String txt = name.toString();
            int txtW = Gfx.width(g, Theme.H3, txt);
            g.drawString(txt, (float) (W / 2.0 - txtW / 2.0), (float) (fy + 29));
            if (((int) (time / 500) & 1) == 0) {
                g.setColor(Theme.GREEN);
                Gfx.fillRound(g, W / 2.0 + txtW / 2.0 + 3, fy + 11, 2, 22, 1);
            }
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_FAINT);
            Gfx.center(g, "ENTER to save  •  ESC to skip", W / 2.0, fy + 62);
        } else if (savedRank > 0) {
            g.setFont(Theme.H3);
            g.setColor(Theme.GREEN);
            Gfx.center(g, "Saved as #" + savedRank + " on the leaderboard", W / 2.0, ny + 34);
        }

        double by = y + ch - 76, bw = 230;
        if (ui.button(g, "PLAY AGAIN", W / 2.0 - bw - 10, by, bw, 50, Theme.GREEN, true)) act(() -> {
            if (nameEntry) submitName();
            startGame();
        });
        if (ui.button(g, "MAIN MENU", W / 2.0 + 10, by, bw, 50, Theme.CYAN, false)) act(() -> {
            if (nameEntry) submitName();
            switchState(GameState.MENU);
        });
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static GradientPaint titlePaint;

    /** Gradient for the title, positioned relative to the text's own image (cached by the paint's identity). */
    private static GradientPaint TITLE_PAINT(int width) {
        if (titlePaint == null) titlePaint = new GradientPaint(0, 0, Theme.GREEN, width, 0, Theme.CYAN);
        return titlePaint;
    }

    private static double easeOut(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static BufferedImage makeIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Theme.quality(g);
        g.setPaint(new GradientPaint(0, 0, Theme.GREEN, 64, 64, new Color(0, 140, 95)));
        Gfx.fillRound(g, 2, 2, 60, 60, 16);
        g.setColor(Color.WHITE);
        Gfx.fillCircle(g, 22, 26, 8);
        Gfx.fillCircle(g, 42, 26, 8);
        g.setColor(new Color(15, 20, 30));
        Gfx.fillCircle(g, 24, 27, 4);
        Gfx.fillCircle(g, 44, 27, 4);
        g.setColor(new Color(255, 60, 90));
        g.setStroke(Gfx.stroke(3f));
        Gfx.line(g, 32, 44, 32, 54);
        g.dispose();
        return img;
    }
}
