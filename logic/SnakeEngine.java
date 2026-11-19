package logic;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

/**
 * Grid-based game simulation. Everything is measured in cells and in game time
 * (milliseconds fed through {@link #update}), so pausing freezes every timer.
 * Rendering reads the state through getters; events are pushed to a {@link Listener}.
 */
public final class SnakeEngine {
    public static final int COLS = 32, ROWS = 16, CELLS = COLS * ROWS;
    public static final int START_LENGTH = 4, MAX_LEVEL = 6, POINTS_PER_LEVEL = 30;
    public static final int CLASSIC_LIVES = 3, MAX_LIVES = 5, SMALL_FOR_BIG = 5;
    public static final double BIG_APPLE_MS = 6500, ITEM_MS = 9000, COMBO_MS = 3000;
    public static final double TIME_ATTACK_MS = 90_000, READY_MS = 1800, CRASH_PENALTY_MS = 5000;

    public enum EndReason { NONE, OUT_OF_LIVES, CHALLENGE_FAILED, CHALLENGES_CLEARED, TIME_UP }

    /** Game events, all called synchronously from {@link #update}. */
    public interface Listener {
        default void onEat(int x, int y, int points, int combo, boolean big) {}
        default void onPowerUp(PowerUpType type, int x, int y) {}
        default void onShieldBreak(int x, int y) {}
        default void onCrash(int x, int y, boolean fatal) {}
        default void onLevelUp(int level) {}
        default void onChallengeComplete(int index, boolean allDone) {}
        default void onNewBest(int score) {}
    }

    private static final Listener SILENT = new Listener() {};

    // Configuration
    private GameMode mode = GameMode.CLASSIC;
    private boolean wrap, autopilot;
    private int speedLevel = 5, startLevel = 1;
    private Listener listener = SILENT;

    // Snake: index 0 is the head. p* arrays hold positions before the last step (for interpolation).
    private final int[] xs = new int[CELLS], ys = new int[CELLS], pxs = new int[CELLS], pys = new int[CELLS];
    private final byte[] body = new byte[CELLS];
    private final boolean[] walls = new boolean[CELLS];
    private int len, pendingGrowth, wallsVersion;
    private Direction dir = Direction.RIGHT;
    private final ArrayDeque<Direction> inputs = new ArrayDeque<>(4);

    // Items
    private int appleX, appleY, prevAppleX, prevAppleY;
    private double appleAge;
    private boolean bigActive;
    private int bigX, bigY;
    private double bigLeft, bigAge;
    private PowerUpType item;
    private int itemX, itemY;
    private double itemLeft, itemAge;
    private PowerUpType effect;
    private double effectLeft;

    // Progress
    private int score, lives, level, applesEaten, bigEaten, powerUps, combo, bestCombo, maxLength;
    private int smallSinceBig, nextItemAt, bestToBeat;
    private boolean bestAnnounced;
    private double comboLeft, stepAcc, readyMs, stunMs, elapsed, timeLeft;
    private int challengeIndex, challengeBase, challengeBestCombo;
    private double challengeLeft, challengeElapsed;
    private EndReason end = EndReason.NONE;

    private final Random rng = new Random();
    private final int[] scratch = new int[CELLS];
    private final int[] visitStamp = new int[CELLS];
    private int stamp;

    public void setListener(Listener l) { listener = l == null ? SILENT : l; }
    public void setAutopilot(boolean on) { autopilot = on; }

    public void start(GameMode mode, int speedLevel, int startLevel, boolean wrap, int bestToBeat) {
        this.mode = mode;
        this.speedLevel = Math.max(1, Math.min(10, speedLevel));
        this.startLevel = Math.max(1, Math.min(MAX_LEVEL, startLevel));
        this.wrap = wrap;
        this.bestToBeat = bestToBeat;
        level = this.startLevel;
        lives = mode == GameMode.CLASSIC ? CLASSIC_LIVES : 0;
        score = applesEaten = bigEaten = powerUps = combo = bestCombo = smallSinceBig = pendingGrowth = 0;
        nextItemAt = 6;
        bestAnnounced = false;
        comboLeft = stepAcc = elapsed = stunMs = effectLeft = 0;
        timeLeft = TIME_ATTACK_MS;
        effect = null;
        item = null;
        bigActive = false;
        end = EndReason.NONE;
        buildWalls();
        placeSnake();
        spawnApple();
        maxLength = len;
        readyMs = autopilot ? 0 : READY_MS;
        if (mode == GameMode.CHALLENGE) beginChallenge(0);
    }

    /** Buffers up to three turns so quick key combos are never lost or able to reverse the snake. */
    public void queueDirection(Direction d) {
        Direction last = inputs.isEmpty() ? dir : inputs.peekLast();
        if (d == last || d.isOpposite(last) || inputs.size() >= 3) return;
        inputs.addLast(d);
    }

    /** Adds a short "get ready" freeze, e.g. when resuming from pause. */
    public void addReadyDelay(double ms) { readyMs = Math.max(readyMs, ms); }

    // ---------------------------------------------------------------------
    // Simulation
    // ---------------------------------------------------------------------

    public void update(double dt) {
        if (end != EndReason.NONE) return;
        if (readyMs > 0) {
            readyMs -= dt;
            return;
        }
        elapsed += dt;
        appleAge += dt;
        bigAge += dt;
        itemAge += dt;

        if (comboLeft > 0) comboLeft -= dt;
        if (effect != null && effect.durationMs > 0 && (effectLeft -= dt) <= 0) effect = null;
        if (bigActive && (bigLeft -= dt) <= 0) bigActive = false;
        if (item != null && (itemLeft -= dt) <= 0) item = null;

        if (mode == GameMode.TIME_ATTACK && (timeLeft -= dt) <= 0) {
            timeLeft = 0;
            end = EndReason.TIME_UP;
            return;
        }
        if (mode == GameMode.CHALLENGE) {
            challengeElapsed += dt;
            challengeLeft -= dt;
            if (checkChallenge()) return;
        }
        if (stunMs > 0) {
            stunMs -= dt;
            return;
        }

        stepAcc += dt;
        double s = stepMs();
        while (stepAcc >= s && end == EndReason.NONE && readyMs <= 0 && stunMs <= 0) {
            stepAcc -= s;
            step();
        }
    }

    private void step() {
        System.arraycopy(xs, 0, pxs, 0, len);
        System.arraycopy(ys, 0, pys, 0, len);
        prevAppleX = appleX;
        prevAppleY = appleY;

        if (autopilot) dir = think();
        else if (!inputs.isEmpty()) dir = inputs.pollFirst();

        int nx = xs[0] + dir.dx, ny = ys[0] + dir.dy;
        boolean outside = nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS;
        if (outside && wrap) {
            nx = (nx + COLS) % COLS;
            ny = (ny + ROWS) % ROWS;
            outside = false;
        }

        boolean grow = pendingGrowth > 0;
        boolean hit = outside;
        if (!hit && effect != PowerUpType.GHOST) {
            int c = idx(nx, ny);
            int tail = idx(xs[len - 1], ys[len - 1]);
            hit = walls[c] || (body[c] > 0 && !(c == tail && !grow && body[c] == 1));
        }
        if (hit) {
            crash(outside ? Math.max(0, Math.min(COLS - 1, nx)) : nx, outside ? Math.max(0, Math.min(ROWS - 1, ny)) : ny);
            return;
        }

        if (grow && len < CELLS) {
            len++;
            pendingGrowth--;
        } else {
            body[idx(xs[len - 1], ys[len - 1])]--;
        }
        for (int i = len - 1; i > 0; i--) {
            xs[i] = xs[i - 1];
            ys[i] = ys[i - 1];
        }
        if (grow) {
            pxs[len - 1] = xs[len - 1];
            pys[len - 1] = ys[len - 1];
        }
        xs[0] = nx;
        ys[0] = ny;
        body[idx(nx, ny)]++;
        maxLength = Math.max(maxLength, len);

        if (effect == PowerUpType.MAGNET) pullApple();

        if (nx == appleX && ny == appleY) eatApple();
        if (bigActive && nx >= bigX && nx <= bigX + 1 && ny >= bigY && ny <= bigY + 1) eatBig();
        if (item != null && nx == itemX && ny == itemY) collectItem();
    }

    private void eatApple() {
        combo = comboLeft > 0 ? combo + 1 : 1;
        comboLeft = COMBO_MS;
        bestCombo = Math.max(bestCombo, combo);
        challengeBestCombo = Math.max(challengeBestCombo, combo);

        int pts = 1 + Math.min(combo - 1, 4);
        if (effect == PowerUpType.DOUBLE_SCORE) pts *= 2;
        score += pts;
        applesEaten++;
        smallSinceBig++;
        pendingGrowth++;
        if (mode == GameMode.TIME_ATTACK) timeLeft += 1500;
        listener.onEat(appleX, appleY, pts, combo, false);

        spawnApple();
        prevAppleX = appleX;
        prevAppleY = appleY;
        int bigEvery = isChallenge(Challenge.Kind.BIG) ? 2 : SMALL_FOR_BIG;
        if (smallSinceBig >= bigEvery && !bigActive) {
            spawnBig();
            smallSinceBig = 0;
        }
        if (item == null && (applesEaten >= nextItemAt || isChallenge(Challenge.Kind.POWER))) {
            spawnItem();
            nextItemAt = applesEaten + 6 + rng.nextInt(6);
        }
        afterScore();
    }

    private void eatBig() {
        int pts = effect == PowerUpType.DOUBLE_SCORE ? 10 : 5;
        score += pts;
        bigEaten++;
        pendingGrowth += 2;
        bigActive = false;
        if (mode == GameMode.TIME_ATTACK) timeLeft += 4000;
        listener.onEat(bigX, bigY, pts, combo, true);
        afterScore();
    }

    private void collectItem() {
        PowerUpType t = item;
        item = null;
        powerUps++;
        if (t == PowerUpType.EXTRA_LIFE) {
            if (lives < MAX_LIVES) lives++;
            else score += 10;
        } else {
            effect = t;
            effectLeft = t.durationMs;
        }
        listener.onPowerUp(t, itemX, itemY);
        afterScore();
    }

    private void afterScore() {
        if (!bestAnnounced && bestToBeat > 0 && score > bestToBeat) {
            bestAnnounced = true;
            listener.onNewBest(score);
        }
        if (level < MAX_LEVEL && score >= (level - startLevel + 1) * POINTS_PER_LEVEL) levelUp();
    }

    private void crash(int cx, int cy) {
        if (effect == PowerUpType.SHIELD) {
            effect = null;
            stunMs = 650;
            inputs.clear();
            syncPrev();
            listener.onShieldBreak(xs[0], ys[0]);
            return;
        }
        switch (mode) {
            case CLASSIC -> {
                lives--;
                if (lives <= 0) {
                    end = EndReason.OUT_OF_LIVES;
                    syncPrev();
                    listener.onCrash(cx, cy, true);
                    return;
                }
                listener.onCrash(cx, cy, false);
                respawn();
            }
            case CHALLENGE -> {
                end = EndReason.CHALLENGE_FAILED;
                syncPrev();
                listener.onCrash(cx, cy, true);
            }
            case TIME_ATTACK -> {
                timeLeft = Math.max(0, timeLeft - CRASH_PENALTY_MS);
                listener.onCrash(cx, cy, false);
                respawn();
            }
            default -> {
                listener.onCrash(cx, cy, false);
                respawn();
            }
        }
    }

    private void respawn() {
        combo = 0;
        comboLeft = 0;
        pendingGrowth = 0;
        placeSnake();
        if (!appleOnBoard() || body[idx(appleX, appleY)] > 0) spawnApple();
        readyMs = autopilot ? 0 : READY_MS * 0.8;
    }

    private void levelUp() {
        level++;
        buildWalls();
        // Never let a new wall land on, or right in front of, the snake.
        for (int i = 0; i < len; i++) walls[idx(xs[i], ys[i])] = false;
        int hx = xs[0], hy = ys[0];
        for (int k = 1; k <= 3; k++) {
            int ax = hx + dir.dx * k, ay = hy + dir.dy * k;
            if (wrap) { ax = (ax + COLS) % COLS; ay = (ay + ROWS) % ROWS; }
            if (ax >= 0 && ay >= 0 && ax < COLS && ay < ROWS) walls[idx(ax, ay)] = false;
        }
        if (!appleOnBoard() || walls[idx(appleX, appleY)]) spawnApple();
        if (bigActive && !bigFits(bigX, bigY)) bigActive = false;
        if (item != null && walls[idx(itemX, itemY)]) item = null;
        syncPrev();
        stepAcc = 0;
        inputs.clear();
        readyMs = autopilot ? 0 : READY_MS;
        listener.onLevelUp(level);
    }

    // ---------------------------------------------------------------------
    // Challenge mode
    // ---------------------------------------------------------------------

    private void beginChallenge(int i) {
        challengeIndex = i;
        Challenge ch = Challenge.ALL.get(i);
        challengeLeft = ch.seconds() * 1000.0;
        challengeElapsed = 0;
        challengeBestCombo = 0;
        challengeBase = switch (ch.kind()) {
            case EAT -> applesEaten;
            case BIG -> bigEaten;
            case POWER -> powerUps;
            default -> 0;
        };
        if (ch.kind() == Challenge.Kind.BIG && !bigActive) spawnBig();
        if (ch.kind() == Challenge.Kind.POWER && item == null) spawnItem();
    }

    /** @return true if the run ended. */
    private boolean checkChallenge() {
        Challenge ch = getChallenge();
        if (getChallengeProgress() >= ch.goal()) {
            boolean last = challengeIndex == Challenge.ALL.size() - 1;
            score += 10;
            listener.onChallengeComplete(challengeIndex, last);
            if (last) {
                end = EndReason.CHALLENGES_CLEARED;
                return true;
            }
            beginChallenge(challengeIndex + 1);
        } else if (challengeLeft <= 0) {
            challengeLeft = 0;
            end = EndReason.CHALLENGE_FAILED;
            return true;
        }
        return false;
    }

    private boolean isChallenge(Challenge.Kind k) {
        return mode == GameMode.CHALLENGE && end == EndReason.NONE && getChallenge().kind() == k;
    }

    public Challenge getChallenge() { return Challenge.ALL.get(challengeIndex); }
    public int getChallengeIndex() { return challengeIndex; }
    public double getChallengeLeftMs() { return Math.max(0, challengeLeft); }

    public int getChallengeProgress() {
        return switch (getChallenge().kind()) {
            case EAT -> applesEaten - challengeBase;
            case BIG -> bigEaten - challengeBase;
            case POWER -> powerUps - challengeBase;
            case LENGTH -> len;
            case SURVIVE -> (int) (challengeElapsed / 1000);
            case COMBO -> challengeBestCombo;
        };
    }

    // ---------------------------------------------------------------------
    // Board setup and spawning
    // ---------------------------------------------------------------------

    private void buildWalls() {
        Arrays.fill(walls, false);
        if (level >= 2) {
            rect(1, 1, 3, 1); rect(1, 1, 1, 3);
            rect(COLS - 4, 1, 3, 1); rect(COLS - 2, 1, 1, 3);
            rect(1, ROWS - 2, 3, 1); rect(1, ROWS - 4, 1, 3);
            rect(COLS - 4, ROWS - 2, 3, 1); rect(COLS - 2, ROWS - 4, 1, 3);
        }
        if (level >= 3) {
            rect(10, 3, 12, 1);
            rect(10, ROWS - 4, 12, 1);
        }
        if (level >= 4) {
            rect(6, 5, 1, 6);
            rect(COLS - 7, 5, 1, 6);
        }
        if (level >= 5) {
            rect(15, 0, 2, 2);
            rect(15, ROWS - 2, 2, 2);
            rect(13, 7, 1, 2);
            rect(18, 7, 1, 2);
        }
        if (level >= 6) {
            if (wrap) { // border with portal gaps
                rect(0, 0, 13, 1); rect(19, 0, 13, 1);
                rect(0, ROWS - 1, 13, 1); rect(19, ROWS - 1, 13, 1);
                rect(0, 0, 1, 6); rect(0, 10, 1, 6);
                rect(COLS - 1, 0, 1, 6); rect(COLS - 1, 10, 1, 6);
            } else {
                rect(0, 0, COLS, 1); rect(0, ROWS - 1, COLS, 1);
                rect(0, 0, 1, ROWS); rect(COLS - 1, 0, 1, ROWS);
            }
        }
        wallsVersion++;
    }

    private void rect(int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++)
            for (int i = x; i < x + w; i++)
                if (i >= 0 && j >= 0 && i < COLS && j < ROWS) walls[idx(i, j)] = true;
    }

    /** Finds a free horizontal run near the centre with room to move right. */
    private void placeSnake() {
        Arrays.fill(body, (byte) 0);
        len = START_LENGTH;
        dir = Direction.RIGHT;
        inputs.clear();
        int hx = len - 1, hy = 0;
        search:
        for (int dr = 0; dr < ROWS; dr++) {
            int r = ROWS / 2 + ((dr & 1) == 0 ? dr / 2 : -(dr / 2 + 1));
            if (r < 0 || r >= ROWS) continue;
            for (int dc = 0; dc < COLS; dc++) {
                int c = COLS / 2 - 2 + ((dc & 1) == 0 ? dc / 2 : -(dc / 2 + 1));
                if (c - len + 1 < 0 || c + 5 >= COLS) continue;
                boolean ok = true;
                for (int i = c - len + 1; i <= c + 5 && ok; i++) ok = !walls[idx(i, r)];
                if (ok) {
                    hx = c;
                    hy = r;
                    break search;
                }
            }
        }
        for (int i = 0; i < len; i++) {
            xs[i] = pxs[i] = hx - i;
            ys[i] = pys[i] = hy;
            body[idx(xs[i], ys[i])]++;
        }
        stepAcc = 0;
    }

    private boolean isFree(int c) {
        if (walls[c] || body[c] > 0) return false;
        int x = c % COLS, y = c / COLS;
        if (x == appleX && y == appleY) return false;
        if (item != null && x == itemX && y == itemY) return false;
        return !(bigActive && x >= bigX && x <= bigX + 1 && y >= bigY && y <= bigY + 1);
    }

    private void spawnApple() {
        appleX = -100; // so the old position doesn't block itself
        int n = 0;
        int head = idx(xs[0], ys[0]);
        for (int c = 0; c < CELLS; c++) if (isFree(c) && c != head) scratch[n++] = c;
        if (n == 0) return;
        int c = scratch[rng.nextInt(n)];
        appleX = prevAppleX = c % COLS;
        appleY = prevAppleY = c / COLS;
        appleAge = 0;
    }

    private boolean bigFits(int x, int y) {
        if (x < 0 || y < 0 || x + 1 >= COLS || y + 1 >= ROWS) return false;
        for (int j = 0; j < 2; j++)
            for (int i = 0; i < 2; i++) {
                int c = idx(x + i, y + j);
                if (walls[c] || body[c] > 0 || (x + i == appleX && y + j == appleY)
                        || (item != null && x + i == itemX && y + j == itemY)) return false;
            }
        return true;
    }

    private void spawnBig() {
        int n = 0;
        for (int y = 0; y < ROWS - 1; y++)
            for (int x = 0; x < COLS - 1; x++)
                if (bigFits(x, y)) scratch[n++] = idx(x, y);
        if (n == 0) return;
        int c = scratch[rng.nextInt(n)];
        bigX = c % COLS;
        bigY = c / COLS;
        bigActive = true;
        bigLeft = BIG_APPLE_MS;
        bigAge = 0;
    }

    private void spawnItem() {
        PowerUpType[] all = PowerUpType.values();
        PowerUpType t;
        do t = all[rng.nextInt(all.length)];
        while (t == PowerUpType.EXTRA_LIFE && mode != GameMode.CLASSIC);
        int n = 0;
        for (int c = 0; c < CELLS; c++) if (isFree(c)) scratch[n++] = c;
        if (n == 0) return;
        int c = scratch[rng.nextInt(n)];
        item = t;
        itemX = c % COLS;
        itemY = c / COLS;
        itemLeft = ITEM_MS;
        itemAge = 0;
    }

    private void pullApple() {
        int dx = xs[0] - appleX, dy = ys[0] - appleY;
        int dist = Math.abs(dx) + Math.abs(dy);
        if (dist <= 1 || dist > 7) return;
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        int[][] tries = Math.abs(dx) >= Math.abs(dy)
            ? new int[][] {{sx, 0}, {0, sy}} : new int[][] {{0, sy}, {sx, 0}};
        for (int[] t : tries) {
            if (t[0] == 0 && t[1] == 0) continue;
            int nx = appleX + t[0], ny = appleY + t[1];
            int c = idx(nx, ny);
            if (!walls[c] && body[c] == 0 && !(item != null && nx == itemX && ny == itemY)) {
                appleX = nx;
                appleY = ny;
                return;
            }
        }
    }

    private void syncPrev() {
        System.arraycopy(xs, 0, pxs, 0, len);
        System.arraycopy(ys, 0, pys, 0, len);
        prevAppleX = appleX;
        prevAppleY = appleY;
    }

    // ---------------------------------------------------------------------
    // Autopilot (menu attract mode): greedy toward the apple, avoiding dead ends.
    // ---------------------------------------------------------------------

    private Direction think() {
        Direction best = dir;
        double bestScore = Double.NEGATIVE_INFINITY;
        int tail = idx(xs[len - 1], ys[len - 1]);
        for (Direction d : Direction.values()) {
            if (d.isOpposite(dir)) continue;
            int nx = xs[0] + d.dx, ny = ys[0] + d.dy;
            if (wrap) { nx = (nx + COLS) % COLS; ny = (ny + ROWS) % ROWS; }
            else if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) continue;
            int c = idx(nx, ny);
            if (walls[c] || (body[c] > 0 && c != tail)) continue;
            double s = -wrapDist(nx, ny, appleX, appleY) + rng.nextDouble() * 0.5;
            if (reachable(c, len + 4) < len + 4) s -= 1000;
            if (s > bestScore) {
                bestScore = s;
                best = d;
            }
        }
        return best;
    }

    private int wrapDist(int ax, int ay, int bx, int by) {
        int dx = Math.abs(ax - bx), dy = Math.abs(ay - by);
        if (wrap) {
            dx = Math.min(dx, COLS - dx);
            dy = Math.min(dy, ROWS - dy);
        }
        return dx + dy;
    }

    private int reachable(int start, int limit) {
        stamp++;
        int head = 0, tailQ = 0, count = 0;
        scratch[tailQ++] = start;
        visitStamp[start] = stamp;
        while (head < tailQ && count < limit) {
            int c = scratch[head++];
            count++;
            int x = c % COLS, y = c / COLS;
            for (Direction d : Direction.values()) {
                int nx = x + d.dx, ny = y + d.dy;
                if (wrap) { nx = (nx + COLS) % COLS; ny = (ny + ROWS) % ROWS; }
                else if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) continue;
                int n = idx(nx, ny);
                if (visitStamp[n] == stamp || walls[n] || body[n] > 0) continue;
                visitStamp[n] = stamp;
                scratch[tailQ++] = n;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    private static int idx(int x, int y) { return y * COLS + x; }

    private boolean appleOnBoard() { return appleX >= 0 && appleY >= 0; }

    /** Milliseconds per grid step at the current level/score/effect. */
    public double stepMs() {
        double base = 215 - speedLevel * 15; // 200ms (speed 1) .. 65ms (speed 10)
        double s = base * Math.pow(0.95, level - 1) - Math.min(30, (score / 15) * 2.0);
        s = Math.max(45, s);
        return effect == PowerUpType.SLOW_MO ? s * 1.6 : s;
    }

    /** Interpolation factor between the previous and current grid positions. */
    public double alpha() {
        if (readyMs > 0 || stunMs > 0) return 1;
        return Math.min(1, stepAcc / stepMs());
    }

    public int[] xs() { return xs; }
    public int[] ys() { return ys; }
    public int[] prevXs() { return pxs; }
    public int[] prevYs() { return pys; }
    public int length() { return len; }
    public Direction direction() { return dir; }
    public boolean isWall(int x, int y) { return walls[idx(x, y)]; }
    public int wallsVersion() { return wallsVersion; }
    public boolean isWrap() { return wrap; }
    public GameMode mode() { return mode; }

    public int appleX() { return appleX; }
    public int appleY() { return appleY; }
    public int prevAppleX() { return prevAppleX; }
    public int prevAppleY() { return prevAppleY; }
    public double appleAge() { return appleAge; }
    public boolean bigActive() { return bigActive; }
    public int bigX() { return bigX; }
    public int bigY() { return bigY; }
    public double bigLeftFraction() { return bigLeft / BIG_APPLE_MS; }
    public double bigAge() { return bigAge; }
    public PowerUpType item() { return item; }
    public int itemX() { return itemX; }
    public int itemY() { return itemY; }
    public double itemLeftFraction() { return itemLeft / ITEM_MS; }
    public double itemAge() { return itemAge; }
    public PowerUpType effect() { return effect; }
    public double effectLeftFraction() {
        return effect == null || effect.durationMs == 0 ? 0 : effectLeft / effect.durationMs;
    }

    public int score() { return score; }
    public int lives() { return lives; }
    public int level() { return level; }
    public int applesEaten() { return applesEaten; }
    public int bigEaten() { return bigEaten; }
    public int powerUps() { return powerUps; }
    public int combo() { return comboLeft > 0 ? combo : 0; }
    public double comboLeftFraction() { return Math.max(0, comboLeft / COMBO_MS); }
    public int bestCombo() { return bestCombo; }
    public int maxLength() { return maxLength; }
    public double elapsedMs() { return elapsed; }
    public double timeLeftMs() { return timeLeft; }
    public double readyMs() { return readyMs; }
    public boolean isStunned() { return stunMs > 0; }
    public boolean isOver() { return end != EndReason.NONE; }
    public EndReason endReason() { return end; }
}
