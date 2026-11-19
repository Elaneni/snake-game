package logic;

import java.util.List;

/** One timed objective in Challenge mode. */
public record Challenge(Kind kind, int goal, int seconds, String text) {

    public enum Kind { EAT, SURVIVE, BIG, LENGTH, COMBO, POWER }

    public static final List<Challenge> ALL = List.of(
        new Challenge(Kind.EAT, 8, 30, "Eat 8 apples"),
        new Challenge(Kind.SURVIVE, 25, 25, "Survive 25 seconds"),
        new Challenge(Kind.BIG, 2, 45, "Eat 2 golden apples"),
        new Challenge(Kind.LENGTH, 20, 50, "Grow to length 20"),
        new Challenge(Kind.COMBO, 4, 40, "Chain a x4 combo"),
        new Challenge(Kind.POWER, 2, 40, "Collect 2 power-ups")
    );
}
