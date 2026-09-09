package logic;

/**
 * High-level state machine representing the active screen in the Snake Game.
 */
public enum GameState {
    MENU,
    HELP,
    SETTINGS,
    LEADERBOARD,
    PLAYING,
    PAUSED,
    GAME_OVER
}
