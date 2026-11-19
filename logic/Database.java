package logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Embedded H2 database holding settings, every finished run, and the leaderboard.
 *
 * The database file (~/.snakegame/snake.mv.db) and its tables are created
 * automatically the first time the game starts on a machine; older text files
 * from previous versions are imported once. If the file can't be opened (e.g.
 * the game is already running) an in-memory database is used for the session.
 */
public final class Database {
    public static final File DIR = new File(System.getProperty("user.home"), ".snakegame");
    private static final String FILE_URL = "jdbc:h2:file:" + new File(DIR, "snake").getAbsolutePath();
    private static final int SCHEMA_VERSION = 1;

    public record ScoreRow(long runId, String name, int score, GameMode mode) {}

    /** Sums and maxima over all recorded runs. */
    public record Totals(int games, long apples, int bestCombo, int longestSnake, long playMs) {}

    private Connection conn;
    private boolean persistent;
    private String problem;

    public Database() {
        try {
            if (!DIR.exists() && !DIR.mkdirs()) throw new IOException("cannot create " + DIR);
            conn = DriverManager.getConnection(FILE_URL, "sa", "");
            persistent = true;
        } catch (SQLException | IOException e) {
            problem = e.getMessage();
            System.err.println("Database unavailable, using a temporary one: " + problem);
            try {
                conn = DriverManager.getConnection("jdbc:h2:mem:snake;DB_CLOSE_DELAY=-1", "sa", "");
            } catch (SQLException e2) {
                conn = null; // no driver at all: game still runs, nothing is stored
            }
        }
        if (conn != null) {
            try {
                createSchema();
                if (persistent) importLegacyFiles();
            } catch (SQLException e) {
                problem = e.getMessage();
                System.err.println("Database setup failed: " + problem);
            }
        }
    }

    /** True when data is being saved to disk. */
    public boolean isPersistent() { return persistent; }

    /** Human-readable reason data isn't being saved, or null. */
    public String problem() { return persistent ? null : problem; }

    public String location() { return new File(DIR, "snake.mv.db").getAbsolutePath(); }

    // ------------------------------------------------------------------
    // Schema
    // ------------------------------------------------------------------

    private void createSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS settings ("
                + "k VARCHAR(64) PRIMARY KEY, v VARCHAR(512) NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS runs ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,"
                + "mode VARCHAR(16) NOT NULL,"
                + "score INT NOT NULL,"
                + "level INT DEFAULT 0 NOT NULL,"
                + "max_length INT DEFAULT 0 NOT NULL,"
                + "apples INT DEFAULT 0 NOT NULL,"
                + "golden_apples INT DEFAULT 0 NOT NULL,"
                + "power_ups INT DEFAULT 0 NOT NULL,"
                + "best_combo INT DEFAULT 0 NOT NULL,"
                + "duration_ms BIGINT DEFAULT 0 NOT NULL,"
                + "end_reason VARCHAR(24),"
                + "player_name VARCHAR(32))");
            st.execute("CREATE INDEX IF NOT EXISTS runs_leaderboard ON runs(score DESC)");
        }
        int version = Integer.parseInt(getSetting("schema.version", "0"));
        if (version < SCHEMA_VERSION) {
            // future migrations go here: if (version < 2) { ALTER TABLE ... }
            putSetting("schema.version", String.valueOf(SCHEMA_VERSION));
        }
    }

    /** One-time import of the leaderboard.txt / preferences.properties files used by older versions. */
    private void importLegacyFiles() throws SQLException {
        File board = new File(DIR, "leaderboard.txt");
        if (board.exists()) {
            try (BufferedReader r = Files.newBufferedReader(board.toPath(), StandardCharsets.UTF_8);
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO runs(mode, score, player_name, end_reason) VALUES (?, ?, ?, 'IMPORTED')")) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.trim().split("\\|");
                    if (p.length < 2 || p[0].isBlank()) continue;
                    try {
                        GameMode mode = GameMode.CLASSIC;
                        if (p.length >= 3) {
                            try { mode = GameMode.valueOf(p[2].trim()); } catch (IllegalArgumentException ignored) { }
                        }
                        ps.setString(1, mode.name());
                        ps.setInt(2, Integer.parseInt(p[1].trim()));
                        ps.setString(3, truncate(p[0].trim(), 32));
                        ps.executeUpdate();
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not import leaderboard: " + e.getMessage());
            }
            board.renameTo(new File(DIR, "leaderboard.txt.imported"));
        }
        File prefs = new File(DIR, "preferences.properties");
        if (prefs.exists()) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(prefs)) {
                p.load(in);
                Map<String, String> map = new HashMap<>();
                for (String k : p.stringPropertyNames()) {
                    // old lifetime stats become a baseline added to the per-run totals
                    map.put(k.startsWith("stats.") ? "legacy." + k : k, p.getProperty(k));
                }
                putSettings(map);
            } catch (IOException e) {
                System.err.println("Could not import preferences: " + e.getMessage());
            }
            prefs.renameTo(new File(DIR, "preferences.properties.imported"));
        }
    }

    // ------------------------------------------------------------------
    // Settings (key/value)
    // ------------------------------------------------------------------

    public Map<String, String> loadSettings() {
        Map<String, String> map = new HashMap<>();
        if (conn == null) return map;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT k, v FROM settings")) {
            while (rs.next()) map.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) {
            System.err.println("Could not read settings: " + e.getMessage());
        }
        return map;
    }

    public void putSettings(Map<String, String> values) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("MERGE INTO settings(k, v) KEY(k) VALUES (?, ?)")) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, truncate(e.getValue(), 512));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    private void putSetting(String k, String v) { putSettings(Map.of(k, v)); }

    private String getSetting(String k, String def) {
        return loadSettings().getOrDefault(k, def);
    }

    // ------------------------------------------------------------------
    // Runs and leaderboard
    // ------------------------------------------------------------------

    /** Records a finished run and returns its id (or -1). */
    public long insertRun(SnakeEngine e, boolean endedManually) {
        if (conn == null) return -1;
        String sql = "INSERT INTO runs(mode, score, level, max_length, apples, golden_apples, power_ups,"
            + " best_combo, duration_ms, end_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.mode().name());
            ps.setInt(2, e.score());
            ps.setInt(3, e.level());
            ps.setInt(4, e.maxLength());
            ps.setInt(5, e.applesEaten());
            ps.setInt(6, e.bigEaten());
            ps.setInt(7, e.powerUps());
            ps.setInt(8, e.bestCombo());
            ps.setLong(9, (long) e.elapsedMs());
            ps.setString(10, endedManually ? "ENDED" : e.endReason().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException ex) {
            System.err.println("Could not record run: " + ex.getMessage());
            return -1;
        }
    }

    /** Puts a name on a run so it appears on the leaderboard. */
    public void nameRun(long runId, String name) {
        if (conn == null || runId < 0) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE runs SET player_name = ? WHERE id = ?")) {
            ps.setString(1, truncate(name, 32));
            ps.setLong(2, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save name: " + e.getMessage());
        }
    }

    public List<ScoreRow> topScores(int limit) {
        List<ScoreRow> list = new ArrayList<>();
        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, player_name, score, mode FROM runs WHERE player_name IS NOT NULL"
                    + " ORDER BY score DESC, played_at ASC, id ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GameMode mode;
                    try { mode = GameMode.valueOf(rs.getString(4)); } catch (IllegalArgumentException e) { mode = GameMode.CLASSIC; }
                    list.add(new ScoreRow(rs.getLong(1), rs.getString(2), rs.getInt(3), mode));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not read leaderboard: " + e.getMessage());
        }
        return list;
    }

    public Totals totals() {
        if (conn == null) return new Totals(0, 0, 0, 0, 0);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT COUNT(*), COALESCE(SUM(apples + golden_apples), 0), COALESCE(MAX(best_combo), 0),"
                    + " COALESCE(MAX(max_length), 0), COALESCE(SUM(duration_ms), 0)"
                    + " FROM runs WHERE end_reason IS NULL OR end_reason <> 'IMPORTED'")) {
            rs.next();
            return new Totals(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getLong(5));
        } catch (SQLException e) {
            System.err.println("Could not read stats: " + e.getMessage());
            return new Totals(0, 0, 0, 0, 0);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
