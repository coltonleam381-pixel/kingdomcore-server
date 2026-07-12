package com.yourorg.kingdomcore.core;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.AbilityLevelCosts;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KingdomConfig {
    private final String displayName;
    private final Map<String, AbilityDefinition> abilities;
    private final AbilityLevelCosts abilityLevelCosts;
    private final long microCooldownMs;
    private final String spawnRegionName;
    private final List<String> abilityNpcNames;
    private final List<String> reviveNpcNames;
    private final String heartItemId;
    private final String crownItemId;
    private final String reviveBeaconId;
    private final String maceItemId;
    private final String scytheItemId;
    private final String wardenCpItemId;
    private final String tridentItemId;
    private final long maceRecraftMs;
    private final long scytheRecraftMs;
    private final long tridentRecraftMs;
    private final long wardenCpRecraftMs;
    private final long crownRecraftMs;
    private final int startingHearts;
    private final int craftMaxHearts;
    private final int progressionMaxHearts;
    private final int crownBonusHearts;
    private final boolean pvpOnlyDeathPenalty;
    private final String blockedKickMessage;
    private final boolean withdrawEnabled;
    private final boolean allowlistEnabled;
    private final boolean persistCooldowns;
    private final String sqliteFile;

    public KingdomConfig(String displayName,
                         Map<String, AbilityDefinition> abilities,
                         AbilityLevelCosts abilityLevelCosts,
                         long microCooldownMs,
                         String spawnRegionName,
                         List<String> abilityNpcNames,
                         List<String> reviveNpcNames,
                         String heartItemId,
                         String crownItemId,
                         String reviveBeaconId,
                         String maceItemId,
                         String scytheItemId,
                         String wardenCpItemId,
                         String tridentItemId,
                         long maceRecraftMs,
                         long scytheRecraftMs,
                         long tridentRecraftMs,
                         long wardenCpRecraftMs,
                         long crownRecraftMs,
                         int startingHearts,
                         int craftMaxHearts,
                         int progressionMaxHearts,
                         int crownBonusHearts,
                         boolean pvpOnlyDeathPenalty,
                         String blockedKickMessage,
                         boolean withdrawEnabled,
                         boolean allowlistEnabled,
                         boolean persistCooldowns,
                         String sqliteFile) {
        this.displayName = displayName;
        this.abilities = abilities;
        this.abilityLevelCosts = abilityLevelCosts;
        this.microCooldownMs = microCooldownMs;
        this.spawnRegionName = spawnRegionName;
        this.abilityNpcNames = abilityNpcNames;
        this.reviveNpcNames = reviveNpcNames;
        this.heartItemId = heartItemId;
        this.crownItemId = crownItemId;
        this.reviveBeaconId = reviveBeaconId;
        this.maceItemId = maceItemId;
        this.scytheItemId = scytheItemId;
        this.wardenCpItemId = wardenCpItemId;
        this.tridentItemId = tridentItemId;
        this.maceRecraftMs = maceRecraftMs;
        this.scytheRecraftMs = scytheRecraftMs;
        this.tridentRecraftMs = tridentRecraftMs;
        this.wardenCpRecraftMs = wardenCpRecraftMs;
        this.crownRecraftMs = crownRecraftMs;
        this.startingHearts = startingHearts;
        this.craftMaxHearts = craftMaxHearts;
        this.progressionMaxHearts = progressionMaxHearts;
        this.crownBonusHearts = crownBonusHearts;
        this.pvpOnlyDeathPenalty = pvpOnlyDeathPenalty;
        this.blockedKickMessage = blockedKickMessage;
        this.withdrawEnabled = withdrawEnabled;
        this.allowlistEnabled = allowlistEnabled;
        this.persistCooldowns = persistCooldowns;
        this.sqliteFile = sqliteFile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<String, AbilityDefinition> getAbilities() {
        return abilities;
    }

    public AbilityLevelCosts getAbilityLevelCosts() {
        return abilityLevelCosts;
    }

    public long getMicroCooldownMs() {
        return microCooldownMs;
    }

    public String getSpawnRegionName() {
        return spawnRegionName;
    }

    public List<String> getAbilityNpcNames() {
        return abilityNpcNames;
    }

    public List<String> getReviveNpcNames() {
        return reviveNpcNames;
    }

    public String getHeartItemId() {
        return heartItemId;
    }

    public String getCrownItemId() {
        return crownItemId;
    }

    public String getReviveBeaconId() {
        return reviveBeaconId;
    }

    public String getMaceItemId() {
        return maceItemId;
    }

    public String getScytheItemId() {
        return scytheItemId;
    }

    public String getWardenCpItemId() {
        return wardenCpItemId;
    }

    public String getTridentItemId() {
        return tridentItemId;
    }

    public long getMaceRecraftMs() {
        return maceRecraftMs;
    }

    public long getScytheRecraftMs() {
        return scytheRecraftMs;
    }

    public long getTridentRecraftMs() {
        return tridentRecraftMs;
    }

    public long getWardenCpRecraftMs() {
        return wardenCpRecraftMs;
    }

    public long getCrownRecraftMs() {
        return crownRecraftMs;
    }

    public int getStartingHearts() {
        return startingHearts;
    }

    public int getCraftMaxHearts() {
        return craftMaxHearts;
    }

    public int getProgressionMaxHearts() {
        return progressionMaxHearts;
    }

    public int getCrownBonusHearts() {
        return crownBonusHearts;
    }

    /** Absolute max HP cap including crown (progression max + crown bonus). */
    public int getAbsoluteMaxHearts() {
        return progressionMaxHearts + crownBonusHearts;
    }

    /** @deprecated use {@link #getProgressionMaxHearts()} */
    public int getMaxHearts() {
        return progressionMaxHearts;
    }

    public boolean isPvpOnlyDeathPenalty() {
        return pvpOnlyDeathPenalty;
    }

    public String getBlockedKickMessage() {
        return blockedKickMessage;
    }

    public boolean isWithdrawEnabled() {
        return withdrawEnabled;
    }

    public boolean isAllowlistEnabled() {
        return allowlistEnabled;
    }

    public boolean isPersistCooldowns() {
        return persistCooldowns;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public static KingdomConfig from(FileConfiguration config) {
        Map<String, AbilityDefinition> abilities = new HashMap<>();
        List<Map<?, ?>> abilityList = config.getMapList("abilities");
        for (Map<?, ?> entry : abilityList) {
            String id = String.valueOf(entry.get("id"));
            String name = String.valueOf(entry.get("name"));
            long cooldown = ((Number) entry.get("base-cooldown-ms")).longValue();
            String shortDesc = String.valueOf(entry.get("short"));
            String longDesc = String.valueOf(entry.get("long"));
            abilities.put(id, new AbilityDefinition(id, name, cooldown, shortDesc, longDesc));
        }

        ConfigurationSection upgrades = config.getConfigurationSection("upgrades");
        AbilityLevelCosts costs = new AbilityLevelCosts(
                upgrades.getInt("level-1-cost"),
                upgrades.getInt("level-2-cost"),
                upgrades.getInt("level-3-cost")
        );

        String displayName = config.getString("display-name", "KingdomCore");
        long microCooldown = config.getLong("cooldowns.micro-ms", 200L);
        String regionName = config.getString("spawn-protection.worldguard-region", "spawn_castle");
        List<String> abilityNpcNames = config.getStringList("spawn-protection.npc-ability-names");
        List<String> reviveNpcNames = config.getStringList("spawn-protection.npc-revive-names");
        String heartId = config.getString("items.heart-item-id", "kingdomcore:heart");
        String crownId = config.getString("items.crown-item-id", "kingdomcore:crown");
        String reviveId = config.getString("items.revive-beacon-id", "kingdomcore:revive_beacon");
        String maceId = config.getString("items.mace-item-id", "kingdomcore:mace");
        String scytheId = config.getString("items.scythe-item-id", "kingdomcore:scythe");
        String wardenCpId = config.getString("items.warden-cp-item-id", "kingdomcore:warden_cp");
        String tridentId = config.getString("items.trident-item-id", "kingdomcore:trident");
        long maceRecraftMs = config.getLong("unique-items.mace-recraft-hours", 120L) * 60L * 60L * 1000L;
        long scytheRecraftMs = config.getLong("unique-items.scythe-recraft-hours", 120L) * 60L * 60L * 1000L;
        long tridentRecraftMs = config.getLong("unique-items.trident-recraft-hours", 120L) * 60L * 60L * 1000L;
        long wardenCpRecraftMs = config.getLong("unique-items.warden-cp-recraft-hours", 60L) * 60L * 60L * 1000L;
        long crownRecraftMs = config.getLong("unique-items.crown-recraft-hours", 24L) * 60L * 60L * 1000L;
        int startingHearts = config.getInt("hearts.starting", 10);
        int craftMaxHearts = config.getInt("hearts.craft-max", config.getInt("hearts.max-hearts", 15));
        int progressionMaxHearts = config.getInt("hearts.progression-max", 20);
        int crownBonusHearts = config.getInt("hearts.crown-bonus", 10);
        boolean pvpOnlyDeathPenalty = config.getBoolean("hearts.pvp-only-death-penalty", true);
        String blockedKickMessage = config.getString("hearts.blocked-kick-message", "Blocked");
        boolean withdrawEnabled = config.getBoolean("hearts.withdraw.enabled", false);
        boolean allowlistEnabled = config.getBoolean("allowlist.enabled", true);
        boolean persistCooldowns = config.getBoolean("storage.persist-cooldowns", false);
        String sqliteFile = config.getString("storage.sqlite-file", "kingdomcore.db");

        return new KingdomConfig(displayName, abilities, costs, microCooldown, regionName,
                new ArrayList<>(abilityNpcNames), new ArrayList<>(reviveNpcNames),
            heartId, crownId, reviveId, maceId, scytheId, wardenCpId, tridentId,
                maceRecraftMs, scytheRecraftMs, tridentRecraftMs, wardenCpRecraftMs, crownRecraftMs,
                startingHearts, craftMaxHearts, progressionMaxHearts, crownBonusHearts, pvpOnlyDeathPenalty,
            blockedKickMessage, withdrawEnabled,
                allowlistEnabled, persistCooldowns, sqliteFile);
    }
}
