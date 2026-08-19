# Snake Game

A Java Swing Snake game with levels, obstacles, power-up apples, game modes
(Classic / Endless / Challenge), a persistent leaderboard, and a non-blocking
high-score toast.

## Files
- `SnakeGame.java` — entry point (`main`)
- `GameFrame.java` — the game window
- `GamePanel.java` — all game logic and rendering

## What's new in this version
- **Fixed the opposite-direction bug** — rapid key presses can no longer
  reverse the snake into itself.
- **Persistent leaderboard** — top 10 scores are saved to a file at
  `~/.snakegame/leaderboard.txt` (i.e. inside your user home folder) and
  reloaded automatically the next time you launch the game.
- **Graphical leaderboard** — click "Leaderboard" for a styled panel with
  gold/silver/bronze medal rows instead of a plain popup.
- **Non-blocking high-score toast** — beating the saved high score shows a
  gold banner at the top of the screen for a few seconds; the game keeps
  running the whole time (no dialog, no pause).

---

## 1. Compiling and running from source

You need a JDK (Java 11 or newer; JDK 17/21 recommended) installed and on
your `PATH`.

```bash
# From the folder containing the three .java files:
javac *.java

# Run it:
java SnakeGame
```

---

## 2. Packaging into a Windows 

### Prerequisites
1. Install a JDK on Windows (17 or 21 LTS): https://adoptium.net/
2. Install the **WiX Toolset v3** (required by jpackage to build `.exe`/`.msi`
   installers on Windows): https://wixtoolset.org/releases/
   - After installing, make sure its `bin` folder is on your `PATH`.

### Steps (run in PowerShell or Command Prompt, from the project folder)

```powershell
# 1. Compile the classes into an "out" folder
javac -d out *.java

# 2. Package the compiled classes into a runnable jar
jar --create --file SnakeGame.jar --main-class SnakeGame -C out .

# 3. Build a Windows installer/exe with jpackage
jpackage `
  --input . `
  --name SnakeGame `
  --main-jar SnakeGame.jar `
  --main-class SnakeGame `
  --type exe `
  --win-menu `
  --win-shortcut `
  --app-version 1.0 `
  --vendor "YourName"
```

- `--type exe` builds a double-clickable installer that installs the game
  and creates a Start Menu / desktop shortcut. If you'd rather have a single
  portable folder you can zip and hand to someone (no installer), use
  `--type app-image` instead — it produces a folder containing `SnakeGame.exe`
  plus a bundled Java runtime, so the other person doesn't need Java
  installed at all.
- The result appears in the same folder (e.g. `SnakeGame-1.0.exe`).

If `jpackage` isn't found, it means your JDK is older than 14 — install a
newer one from the Adoptium link above.

### macOS / Linux equivalent
The same `jpackage` command works on macOS (`--type pkg` or `--type dmg`) and
Linux (`--type deb` or `--type rpm`) if you ever want to distribute it there
too — just run the equivalent build on that OS.

---

