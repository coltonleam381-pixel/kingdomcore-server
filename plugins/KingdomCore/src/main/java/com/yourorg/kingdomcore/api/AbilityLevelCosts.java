package com.yourorg.kingdomcore.api;

public record AbilityLevelCosts(int level1, int level2, int level3) {
    public int costForNextLevel(int currentLevel) {
        return switch (currentLevel) {
            case 0 -> level1;
            case 1 -> level2;
            case 2 -> level3;
            default -> 0;
        };
    }
}
