package logic;

/**
 * Represents a single score entry on the leaderboard.
 */
public class ScoreEntry {
    private final String name;
    private final int score;

    public ScoreEntry(String name, int score) {
        this.name = (name == null || name.trim().isEmpty()) ? "Player" : name.trim();
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }
}
