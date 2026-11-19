package logic;

/** Game modes available in the Snake Game. */
public enum GameMode {
    CLASSIC("Classic", "3 lives. Climb through 6 levels of obstacles."),
    ENDLESS("Endless", "No game over. Crashing just respawns you."),
    CHALLENGE("Challenge", "Clear 6 timed objectives. One crash ends the run."),
    TIME_ATTACK("Time Attack", "90s on the clock. Apples add time, crashes cost 5s.");

    public final String label;
    public final String description;

    GameMode(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
