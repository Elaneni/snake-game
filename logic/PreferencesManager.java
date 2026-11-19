package logic;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings and lifetime stats, persisted in the {@link Database}.
 * Fields are public for brevity; call {@link #save()} after changing them.
 */
public final class PreferencesManager {
    private final Database db;

    public boolean firstTimeUser = true;
    public int speedLevel = 5;          // 1..10
    public int startingLevel = 1;       // 1..MAX_LEVEL
    public GameMode gameMode = GameMode.CLASSIC;
    public boolean soundEnabled = true;
    public boolean wrapWalls = false;
    public int skin = 0;
    public int theme = 0;
    public boolean screenShake = true;
    public boolean showFps = false;
    public String lastName = "";

    // Lifetime stats: computed from the runs table (plus any totals imported from older versions).
    public int gamesPlayed, totalApples, bestCombo, longestSnake;
    public long totalPlayMs;
    private Map<String, String> settings = new HashMap<>();

    public PreferencesManager(Database db) {
        this.db = db;
        load();
        refreshStats();
    }

    private void load() {
        settings = db.loadSettings(); // empty on a brand-new database: defaults apply
        firstTimeUser = Boolean.parseBoolean(settings.getOrDefault("firstTimeUser", "true"));
        speedLevel = clamp(num("speedLevel", 5), 1, 10);
        startingLevel = clamp(num("startingLevel", 1), 1, SnakeEngine.MAX_LEVEL);
        soundEnabled = Boolean.parseBoolean(settings.getOrDefault("soundEnabled", "true"));
        wrapWalls = Boolean.parseBoolean(settings.getOrDefault("wrapWalls", "false"));
        screenShake = Boolean.parseBoolean(settings.getOrDefault("screenShake", "true"));
        showFps = Boolean.parseBoolean(settings.getOrDefault("showFps", "false"));
        skin = Math.max(0, num("skin", 0));
        theme = Math.max(0, num("theme", 0));
        lastName = settings.getOrDefault("lastName", "");
        try {
            gameMode = GameMode.valueOf(settings.getOrDefault("gameMode", "CLASSIC").toUpperCase());
        } catch (IllegalArgumentException e) {
            gameMode = GameMode.CLASSIC;
        }
    }

    public void save() {
        Map<String, String> m = new HashMap<>();
        m.put("firstTimeUser", String.valueOf(firstTimeUser));
        m.put("speedLevel", String.valueOf(speedLevel));
        m.put("startingLevel", String.valueOf(startingLevel));
        m.put("gameMode", gameMode.name());
        m.put("soundEnabled", String.valueOf(soundEnabled));
        m.put("wrapWalls", String.valueOf(wrapWalls));
        m.put("screenShake", String.valueOf(screenShake));
        m.put("showFps", String.valueOf(showFps));
        m.put("skin", String.valueOf(skin));
        m.put("theme", String.valueOf(theme));
        m.put("lastName", lastName);
        db.putSettings(m);
    }

    /** Stores a finished run, refreshes the lifetime stats, and returns the run id. */
    public long recordRun(SnakeEngine e, boolean endedManually) {
        long id = db.insertRun(e, endedManually);
        refreshStats();
        return id;
    }

    private void refreshStats() {
        Database.Totals t = db.totals();
        gamesPlayed = num("legacy.stats.gamesPlayed", 0) + t.games();
        totalApples = (int) (num("legacy.stats.totalApples", 0) + t.apples());
        bestCombo = Math.max(num("legacy.stats.bestCombo", 0), t.bestCombo());
        longestSnake = Math.max(num("legacy.stats.longestSnake", 0), t.longestSnake());
        long legacyMs;
        try {
            legacyMs = Long.parseLong(settings.getOrDefault("legacy.stats.totalPlayMs", "0"));
        } catch (NumberFormatException e) {
            legacyMs = 0;
        }
        totalPlayMs = legacyMs + t.playMs();
    }

    private int num(String key, int def) {
        try {
            return Integer.parseInt(settings.getOrDefault(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
