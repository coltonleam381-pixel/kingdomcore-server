package com.yourorg.kingdomcore.core.services;

import com.yourorg.kingdomcore.abilities.AbilityHandler;
import com.yourorg.kingdomcore.api.AbilityDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Collection;

public interface AbilityService {
    AbilityDefinition getAbility(String id);

    Collection<AbilityDefinition> getAllAbilities();

    /**
     * Register an ability handler.
     */
    void registerHandler(AbilityHandler handler);

    /**
     * Get a registered ability handler by id.
     */
    AbilityHandler getHandler(String abilityId);

    /**
     * Handle right-click activation.
     */
    boolean activateAbilityRightClick(Player player, AbilityDefinition ability, int level, PlayerInteractEvent event);

    /**
     * Handle left-click activation.
     */
    boolean activateAbilityLeftClick(Player player, String abilityId, int level);

    /**
     * Handle sneak + right-click.
     */
    boolean handleSneakRightClick(Player player, String abilityId, int level);

    /**
     * Handle space bar press.
     */
    boolean handleSpace(Player player, String abilityId);

    /**
     * Cleanup ability state for player.
     */
    void cleanup(Player player);
}
