package logic;

import java.util.Collections;
import java.util.List;

/** Top-10 named runs, read from the {@link Database} and cached between changes. */
public final class LeaderboardManager {
    public static final int MAX_ENTRIES = 10;

    private final Database db;
    private List<Database.ScoreRow> scores;

    public LeaderboardManager(Database db) {
        this.db = db;
        refresh();
    }

    private void refresh() {
        scores = db.topScores(MAX_ENTRIES);
    }

    /** Names a recorded run and returns its 1-based rank, or -1 if it isn't in the top 10. */
    public int nameRun(long runId, String name) {
        db.nameRun(runId, name.isBlank() ? "Player" : name.trim());
        refresh();
        for (int i = 0; i < scores.size(); i++) if (scores.get(i).runId() == runId) return i + 1;
        return -1;
    }

    public boolean qualifies(int score) {
        return score > 0 && (scores.size() < MAX_ENTRIES || score > scores.get(scores.size() - 1).score());
    }

    public List<Database.ScoreRow> getScores() {
        return Collections.unmodifiableList(scores);
    }

    public int getHighScore() {
        return scores.isEmpty() ? 0 : scores.get(0).score();
    }
}
