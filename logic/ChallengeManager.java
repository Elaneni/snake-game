package logic;

/**
 * Manages objectives, timers, and progress for Challenge Mode.
 */
public class ChallengeManager {
    private final String[] challenges = {
        "Collect 10 small apples in 30 seconds",
        "Survive for 30 seconds without hitting obstacles",
        "Eat 2 big apples in 20 seconds"
    };
    private final int[] challengeGoals = {10, 30, 2};
    private final int[] challengeTimeLimits = {30, 30, 20};

    private int challengeIndex = 0;
    private long challengeStartTime;
    private boolean challengeActive = false;
    private int challengeProgress = 0;

    public void reset() {
        challengeIndex = 0;
        challengeActive = false;
        challengeProgress = 0;
    }

    public void startNextChallenge() {
        if (challengeIndex < challenges.length) {
            challengeActive = true;
            challengeStartTime = System.currentTimeMillis();
            challengeProgress = 0;
        } else {
            challengeActive = false;
        }
    }

    public boolean isAllChallengesCompleted() {
        return challengeIndex >= challenges.length;
    }

    public boolean isChallengeActive() {
        return challengeActive;
    }

    public void setChallengeActive(boolean active) {
        this.challengeActive = active;
    }

    public int getChallengeIndex() {
        return challengeIndex;
    }

    public String getCurrentChallengeDescription() {
        if (challengeIndex < challenges.length) {
            return challenges[challengeIndex];
        }
        return "All challenges completed!";
    }

    public int getCurrentGoal() {
        if (challengeIndex < challengeGoals.length) {
            return challengeGoals[challengeIndex];
        }
        return 0;
    }

    public int getCurrentTimeLimit() {
        if (challengeIndex < challengeTimeLimits.length) {
            return challengeTimeLimits[challengeIndex];
        }
        return 0;
    }

    public int getRemainingSeconds() {
        if (!challengeActive || challengeIndex >= challengeTimeLimits.length) return 0;
        long elapsed = (System.currentTimeMillis() - challengeStartTime) / 1000;
        return Math.max(0, challengeTimeLimits[challengeIndex] - (int) elapsed);
    }

    public boolean isTimedOut() {
        return challengeActive && getRemainingSeconds() <= 0;
    }

    public int getProgress() {
        return challengeProgress;
    }

    public void setProgress(int progress) {
        this.challengeProgress = progress;
    }

    public void incrementProgress(int amount) {
        this.challengeProgress += amount;
    }

    public boolean checkCompletion(int currentSmallApplesEaten) {
        if (!challengeActive) return false;

        boolean completed = false;
        switch (challengeIndex) {
            case 0: // Collect 10 small apples
                challengeProgress = currentSmallApplesEaten;
                if (challengeProgress >= challengeGoals[0]) {
                    completed = true;
                }
                break;
            case 1: // Survive for 30 seconds
                long elapsed = (System.currentTimeMillis() - challengeStartTime) / 1000;
                challengeProgress = (int) elapsed;
                if (challengeProgress >= challengeGoals[1]) {
                    completed = true;
                }
                break;
            case 2: // Eat 2 big apples
                if (challengeProgress >= challengeGoals[2]) {
                    completed = true;
                }
                break;
        }

        if (completed) {
            challengeIndex++;
            challengeActive = false;
            return true;
        }
        return false;
    }
}
