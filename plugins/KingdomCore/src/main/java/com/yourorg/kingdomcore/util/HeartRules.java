package com.yourorg.kingdomcore.util;

public final class HeartRules {
    private HeartRules() {
    }

    public static int clampProgression(int value, int baseCap) {
        int cap = Math.max(0, baseCap);
        if (value < 0) {
            return 0;
        }
        return Math.min(value, cap);
    }

    public static boolean canConsume(int current, int baseCap) {
        return clampProgression(current + 1, baseCap) > current;
    }

    public static boolean shouldBlockAfterPenalty(int current) {
        return current > 0 && current - 1 <= 0;
    }
}
