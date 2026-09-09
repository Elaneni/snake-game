package logic;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages persistent user preferences (first-time launch tracking, speed settings, colors, mode).
 */
public class PreferencesManager {
    private static final String PREF_DIR = System.getProperty("user.home") + File.separator + ".snakegame";
    private static final String PREF_FILE = PREF_DIR + File.separator + "preferences.properties";

    private boolean firstTimeUser = true;
    private int speedLevel = 5; // 1 (Slowest) to 10 (Fastest)
    private int baseDelay = 180; // milliseconds
    private Color snakeHeadColor = new Color(0, 230, 118);
    private Color snakeBodyColor = new Color(46, 125, 50);
    private Color backgroundColor = new Color(15, 18, 26);
    private int startingLevel = 1;
    private GameMode gameMode = GameMode.CLASSIC;
    private boolean soundEnabled = true;

    public PreferencesManager() {
        load();
    }

    public void load() {
        File file = new File(PREF_FILE);
        if (!file.exists()) {
            // First time ever running with this config
            firstTimeUser = true;
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
            firstTimeUser = Boolean.parseBoolean(props.getProperty("firstTimeUser", "false"));
            speedLevel = Integer.parseInt(props.getProperty("speedLevel", "5"));
            baseDelay = Integer.parseInt(props.getProperty("baseDelay", "180"));
            startingLevel = Integer.parseInt(props.getProperty("startingLevel", "1"));
            soundEnabled = Boolean.parseBoolean(props.getProperty("soundEnabled", "true"));
            
            String modeStr = props.getProperty("gameMode", "CLASSIC");
            try {
                gameMode = GameMode.valueOf(modeStr.toUpperCase());
            } catch (Exception e) {
                gameMode = GameMode.CLASSIC;
            }

            if (props.containsKey("snakeHeadColor")) {
                snakeHeadColor = new Color(Integer.parseInt(props.getProperty("snakeHeadColor")));
            }
            if (props.containsKey("snakeBodyColor")) {
                snakeBodyColor = new Color(Integer.parseInt(props.getProperty("snakeBodyColor")));
            }
            if (props.containsKey("backgroundColor")) {
                backgroundColor = new Color(Integer.parseInt(props.getProperty("backgroundColor")));
            }
        } catch (Exception e) {
            System.err.println("Could not load preferences: " + e.getMessage());
        }
    }

    public void save() {
        File dir = new File(PREF_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Could not create directory for preferences: " + PREF_DIR);
            return;
        }

        Properties props = new Properties();
        props.setProperty("firstTimeUser", String.valueOf(firstTimeUser));
        props.setProperty("speedLevel", String.valueOf(speedLevel));
        props.setProperty("baseDelay", String.valueOf(baseDelay));
        props.setProperty("startingLevel", String.valueOf(startingLevel));
        props.setProperty("gameMode", gameMode.name());
        props.setProperty("soundEnabled", String.valueOf(soundEnabled));
        props.setProperty("snakeHeadColor", String.valueOf(snakeHeadColor.getRGB()));
        props.setProperty("snakeBodyColor", String.valueOf(snakeBodyColor.getRGB()));
        props.setProperty("backgroundColor", String.valueOf(backgroundColor.getRGB()));

        try (FileOutputStream out = new FileOutputStream(PREF_FILE)) {
            props.store(out, "Snake Game User Preferences");
        } catch (IOException e) {
            System.err.println("Could not save preferences: " + e.getMessage());
        }
    }

    public boolean isFirstTimeUser() {
        return firstTimeUser;
    }

    public void setFirstTimeUser(boolean firstTimeUser) {
        this.firstTimeUser = firstTimeUser;
        save();
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    /**
     * Set speed level from 1 (Slowest, ~240ms) to 10 (Fastest, ~60ms).
     */
    public void setSpeedLevel(int level) {
        this.speedLevel = Math.max(1, Math.min(10, level));
        // Map 1..10 to 240ms down to 60ms linearly:
        // Level 1: 240ms
        // Level 5: 160ms
        // Level 10: 60ms
        this.baseDelay = 260 - (this.speedLevel * 20);
        save();
    }

    public int getBaseDelay() {
        return baseDelay;
    }

    public Color getSnakeHeadColor() {
        return snakeHeadColor;
    }

    public void setSnakeHeadColor(Color snakeHeadColor) {
        this.snakeHeadColor = snakeHeadColor;
        save();
    }

    public Color getSnakeBodyColor() {
        return snakeBodyColor;
    }

    public void setSnakeBodyColor(Color snakeBodyColor) {
        this.snakeBodyColor = snakeBodyColor;
        save();
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        save();
    }

    public int getStartingLevel() {
        return startingLevel;
    }

    public void setStartingLevel(int startingLevel) {
        this.startingLevel = Math.max(1, Math.min(5, startingLevel));
        save();
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
        save();
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        save();
    }
}
