package com.yourorg.kingdomcore.ability;

import com.yourorg.kingdomcore.abilities.AbilityHandler;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

/**
 * Phase Walk ability: temporarily make player intangible via spectator mode.
 * Cannot be hit by damage, can pass through walls (not ground).
 */
public class PhaseWalkAbility implements AbilityHandler {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final PhaseWalkService phaseWalkService;

    public PhaseWalkAbility(Plugin plugin, WorldGuardHook worldGuardHook,
                            SpawnRegionPolicy spawnRegionPolicy, PhaseWalkService phaseWalkService) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
        this.phaseWalkService = phaseWalkService;
    }

    @Override
    public String getAbilityId() {
        return "phase_walk";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false; // Locked, silent fail
        }
        
        // Silent fail if in spawn region
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        
        // Silent fail if already phasing
        if (phaseWalkService.isInPhase(player)) {
            return false;
        }
        
        // Get duration based on level
        long durationTicks = getDurationTicks(level);
        
        // Start phase
        phaseWalkService.startPhase(player, durationTicks, level);
        
        // Schedule end of phase
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(
            plugin,
            () -> phaseWalkService.endPhase(player),
            durationTicks
        );
        
        return true;
    }

    @Override
    public boolean onLeftClick(Player player, int level) {
        return false; // Not used for this ability
    }

    @Override
    public boolean onSneakRightClick(Player player, int level) {
        return false; // Not used for this ability
    }

    @Override
    public boolean onSpace(Player player) {
        return false; // Not used for this ability
    }

    @Override
    public void cleanup(Player player) {
        // Clean up phase state on logout/death
        phaseWalkService.cleanup(player);
    }

    /**
     * Get duration in ticks based on level.
     */
    private long getDurationTicks(int level) {
        return switch (level) {
            case 1 -> 60;   // 3 seconds
            case 2 -> 100;  // 5 seconds
            case 3 -> 140;  // 7 seconds
            default -> 0;
        };
    }
}


