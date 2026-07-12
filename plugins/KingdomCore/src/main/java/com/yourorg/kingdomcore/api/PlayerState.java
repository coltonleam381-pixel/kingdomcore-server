package com.yourorg.kingdomcore.api;

import java.util.UUID;

public class PlayerState {
    private final UUID uuid;
    private String lastName;
    private String abilityId;
    private int abilityLevel;
    private int progressionHearts;
    private boolean blocked;
    private int assassinWinBonus;

    public PlayerState(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAbilityId() {
        return abilityId;
    }

    public void setAbilityId(String abilityId) {
        this.abilityId = abilityId;
    }

    public int getAbilityLevel() {
        return abilityLevel;
    }

    public void setAbilityLevel(int abilityLevel) {
        this.abilityLevel = abilityLevel;
    }

    public int getProgressionHearts() {
        return progressionHearts;
    }

    public void setProgressionHearts(int progressionHearts) {
        this.progressionHearts = progressionHearts;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public int getAssassinWinBonus() {
        return assassinWinBonus;
    }

    public void setAssassinWinBonus(int assassinWinBonus) {
        this.assassinWinBonus = Math.max(0, assassinWinBonus);
    }
}
