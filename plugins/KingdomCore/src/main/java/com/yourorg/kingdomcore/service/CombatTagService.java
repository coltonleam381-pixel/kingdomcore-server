package com.yourorg.kingdomcore.service;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages combat tagging for PvP and ability combat.
 * Players tagged for 23 seconds after dealing or receiving damage.
 */
public interface CombatTagService {
    
    /**
     * Tag a player as in combat for 23 seconds.
     * Refreshes tag if already tagged.
     * 
     * @param player Player to tag
     * @param lastDamager The player who caused the tag (can be null)
     */
    void tagPlayer(Player player, Player lastDamager);
    
    /**
     * Check if a player is currently combat tagged.
     * 
     * @param playerId Player UUID
     * @return true if player is in combat
     */
    boolean isTagged(UUID playerId);
    
    /**
     * Get remaining combat tag time in milliseconds.
     * 
     * @param playerId Player UUID
     * @return milliseconds remaining, or 0 if not tagged
     */
    long getRemainingTagMs(UUID playerId);
    
    /**
     * Get the last player who damaged this player (for combat logout attribution).
     * 
     * @param playerId Player UUID
     * @return UUID of last damager, or null if none/expired
     */
    UUID getLastDamager(UUID playerId);
    
    /**
     * Remove combat tag from player immediately.
     * 
     * @param playerId Player UUID
     */
    void clearTag(UUID playerId);
    
    /**
     * Handle player logout while combat tagged.
     * Should trigger death at logout location with killer attribution.
     * 
     * @param player Player logging out
     */
    void handleCombatLogout(Player player);
    
    /**
     * Get remaining ender chest cooldown in milliseconds.
     * This is 30 seconds (30000ms) after the combat tag expires.
     * 
     * @param playerId Player UUID
     * @return milliseconds remaining, or 0 if no cooldown
     */
    long getEchestCooldownRemainingMs(UUID playerId);
    
    /**
     * Get remaining AFK command cooldown in milliseconds.
     * This is 3 minutes (180 seconds) after combat ends.
     * 
     * @param playerId Player UUID
     * @return milliseconds remaining, or 0 if no cooldown
     */
    long getAfkCooldownRemainingMs(UUID playerId);

    /**
     * Whether the player cannot throw ender pearls (combat pearl cooldown or post-combat lock).
     */
    boolean isPearlBlocked(UUID playerId);

    /**
     * Milliseconds until ender pearls are allowed again (0 if usable now).
     */
    long getPearlBlockRemainingMs(UUID playerId);

    /**
     * Starts the in-combat ender pearl cooldown after a successful throw.
     */
    void recordCombatPearlUse(UUID playerId);

    /**
     * Get remaining spawn entry block in milliseconds (30s after combat tag expires).
     */
    long getSpawnEntryCooldownRemainingMs(UUID playerId);

    /**
     * Whether the player is blocked from entering spawn (active tag or 30s post-tag).
     */
    boolean isSpawnEntryBlocked(UUID playerId);
}
