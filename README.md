# Snake

A modern take on Snake written in plain Java, with one embedded-database
library (H2). It has smooth
interpolated movement, four game modes, power-ups, combos, skins and board
themes, and a persistent leaderboard. Everything, including menus, settings,
name entry and notifications, is drawn inside the game window. It never opens
a pop-up dialog.

## Run it

Requires JDK 17 or newer and the H2 database driver in `lib/` (the installer
script downloads it; or get `h2-2.3.232.jar` from Maven Central).

```bash
javac -d out *.java logic/*.java graphics/*.java
java -cp "out;lib/h2-2.3.232.jar" SnakeGame      # use ':' instead of ';' on macOS/Linux
```

Without the driver the game still runs, but nothing is saved.

## Controls

| Key | Action |
| --- | --- |
| Arrow keys / W A S D | Steer (turns are buffered, so quick double-taps are never lost) |
| Space / P / Esc | Pause and resume |
| Arrows + Enter | Navigate menus (the mouse works too) |
| M | Mute / unmute |
| F11 or Alt+Enter | Toggle fullscreen (the game scales to any window size) |
| F3 | FPS counter |
| R | Restart from the game-over screen |

## Gameplay

- **Apples**: +1 each. Eat again within 3 seconds to chain a combo for bonus points.
- **Golden apples**: +5. They appear for a few seconds after every 5 apples.
- **Power-ups**: Shield (absorbs one crash), Slow-Mo, Double Points, Ghost
  (pass through walls and yourself), Magnet (pulls apples toward you),
  and Extra Life (Classic only).
- **Levels**: every 30 points the snake speeds up and new obstacles appear (6 levels).

### Modes

- **Classic**: 3 lives, climb through the levels.
- **Endless**: no game over; crashes respawn you. End the run from the pause menu to save your score.
- **Challenge**: 6 timed objectives in a row. One crash ends the run.
- **Time Attack**: 90 seconds. Apples add time and crashes cost 5 seconds.

Settings also cover speed (1–10), starting level, solid or wrap-around walls,
6 snake skins, 4 board themes, sound, screen shake and the FPS counter.

## Project layout

```
SnakeGame.java          entry point
Game.java               window, game loop, input, screens
logic/                  simulation and persistence (no drawing code)
  SnakeEngine.java      grid simulation, spawning, collisions, modes, autopilot (menu demo)
  Challenge.java        challenge-mode objectives
  PreferencesManager    settings + lifetime stats
  LeaderboardManager    top-10 scores
  SoundPlayer           synthesised sound effects
graphics/               rendering
  BoardRenderer         board, walls, apples, power-ups, snake
  HudRenderer           score bar
  Effects               particles, popups, toasts, screen shake
  Ui                    immediate-mode buttons and option rows drawn on the canvas
  Gfx, Theme            drawing helpers, palette, fonts, skins, themes
```

### How it stays smooth

- The game loop runs on its own thread at the monitor's refresh rate. The
  simulation advances in fixed grid steps, and the snake is drawn
  interpolated between steps. Speed changes never cause stutter.
- All timers (combos, power-ups, challenges) use game time, so pausing freezes them.
- Frames are rendered into a system-memory image and shown with one blit.
  The static board, panels, glows and titles are pre-rendered at screen
  resolution and reused, and full-screen dimming is baked into the cached
  board instead of blended every frame. This keeps it at 60 FPS even on
  Java's software rendering pipeline.
- Sounds are synthesised once at startup into reusable clips. Playing one
  doesn't create a thread or open an audio line.

## Saved data (embedded database)

The game stores everything in an embedded [H2](https://h2database.com)
database at `%USERPROFILE%\.snakegame\snake.mv.db`. It needs no server or
setup: on a new machine the file and its tables are created automatically
on first launch.

| Table | Contents |
| --- | --- |
| `settings` | key/value preferences, plus `schema.version` for future migrations |
| `runs` | one row per finished game: mode, score, level, length, apples, power-ups, best combo, duration, end reason, date, and the player name if it was saved to the leaderboard |

The leaderboard is the top 10 named runs, and the lifetime stats are
aggregates over `runs`. Files from older versions (`leaderboard.txt`,
`preferences.properties`) are imported once and renamed to `*.imported`.
If the database is locked because the game is already open, that session
uses a temporary in-memory database and says so on screen.

To browse the data:
`java -cp lib/h2-2.3.232.jar org.h2.tools.Shell -url jdbc:h2:file:~/.snakegame/snake -user sa`

## Windows installer

```powershell
powershell -ExecutionPolicy Bypass -File build-installer.ps1 [-Version 2.1.0]
```

This produces `dist\Snake-<version>.exe`, a single installer that bundles a
trimmed Java runtime and the H2 driver. It installs per-user (no admin
needed), adds Desktop and Start menu shortcuts, and upgrades older
versions in place. Building it requires a JDK and WiX Toolset 3.x.
