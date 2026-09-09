package logic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages persistent leaderboard scores that survive restarting the game.
 */
public class LeaderboardManager {
    private static final String SCORES_DIR = System.getProperty("user.home") + File.separator + ".snakegame";
    private static final String SCORES_FILE = SCORES_DIR + File.separator + "leaderboard.txt";
    private static final int MAX_ENTRIES = 10;

    private final List<ScoreEntry> scores = new ArrayList<>();

    public LeaderboardManager() {
        load();
    }

    public void load() {
        scores.clear();
        File file = new File(SCORES_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.lastIndexOf('|');
                if (sep == -1) continue;
                String name = line.substring(0, sep).trim();
                try {
                    int score = Integer.parseInt(line.substring(sep + 1).trim());
                    if (!name.isEmpty()) {
                        scores.add(new ScoreEntry(name, score));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load leaderboard from " + SCORES_FILE + ": " + e.getMessage());
        }

        sortAndTrim();
    }

    public void save() {
        File dir = new File(SCORES_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Could not create score directory: " + SCORES_DIR);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SCORES_FILE))) {
            for (ScoreEntry entry : scores) {
                String safeName = entry.getName().replace("|", "/").replace("\n", " ").replace("\r", " ");
                writer.write(safeName + "|" + entry.getScore());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save leaderboard to " + SCORES_FILE + ": " + e.getMessage());
        }
    }

    public void addScore(String name, int score) {
        scores.add(new ScoreEntry(name, score));
        sortAndTrim();
        save();
    }

    private void sortAndTrim() {
        Collections.sort(scores, Comparator.comparingInt(ScoreEntry::getScore).reversed());
        if (scores.size() > MAX_ENTRIES) {
            scores.subList(MAX_ENTRIES, scores.size()).clear();
        }
    }

    public List<ScoreEntry> getScores() {
        return Collections.unmodifiableList(scores);
    }

    public int getHighScore() {
        return scores.isEmpty() ? 0 : scores.get(0).getScore();
    }

    public boolean isTopScore(int score) {
        return score > 0 && (scores.isEmpty() || score > scores.get(0).getScore());
    }
}
