package com.yourorg.kingdomcore.abilities;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Base interface for ability handlers.
 * Each ability implements activation logic for different interaction types.
 */
public interface AbilityHandler {
    /**
     * Get the ability ID this handler manages.
     */
    String getAbilityId();

    /**
     * Handle right-click activation.
     * @param player The player activating
     * @param level Current ability level (0 = locked, 1-3 = unlocked)
     * @param event The interaction event
     * @return true if ability activated successfully
     */
    boolean onRightClick(Player player, int level, PlayerInteractEvent event);

    /**
     * Handle left-click activation (for abilities like Thor).
     * @param player The player activating
     * @param level Current ability level
     * @return true if ability activated successfully
     */
    default boolean onLeftClick(Player player, int level) {
        return false;
    }

    /**
     * Handle sneak + right-click (mode toggle for Atlantis).
     * @param player The player
     * @param level Current ability level
     * @return true if handled
     */
    default boolean onSneakRightClick(Player player, int level) {
        return false;
    }

    /**
     * Handle space bar press (for Meteor early exit).
     * @param player The player
     * @return true if handled
     */
    default boolean onSpace(Player player) {
        return false;
    }

    /**
     * Cleanup any active state for a player (on logout, death, etc.).
     */
    void cleanup(Player player);
}
