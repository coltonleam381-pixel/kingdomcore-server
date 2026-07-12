package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase Walk ability: temporarily make player intangible via spectator mode.
 * Cannot be hit by damage, can pass through walls (not ground).
 */
public class PhaseWalkAbility implements AbilityHandler, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final PhaseWalkService phaseWalkService;
    private final Map<UUID, Integer> activeTickTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeEndAtMs = new ConcurrentHashMap<>();

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
        activeEndAtMs.put(player.getUniqueId(), System.currentTimeMillis() + (durationTicks * 50L));
        
        int taskId = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !phaseWalkService.isInPhase(player)) {
                    activeTickTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                phaseWalkService.tryPhaseThroughWall(player);
                elapsed++;
                if (elapsed >= durationTicks) {
                    phaseWalkService.endPhase(player);
                    activeTickTasks.remove(player.getUniqueId());
                    activeEndAtMs.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
        activeTickTasks.put(player.getUniqueId(), taskId);
        
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
        Integer taskId = activeTickTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        activeEndAtMs.remove(player.getUniqueId());
        phaseWalkService.cleanup(player);
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        Long endAt = activeEndAtMs.get(playerId);
        if (endAt == null) {
            return null;
        }
        long leftMs = Math.max(0L, endAt - nowMs);
        if (leftMs <= 0L) {
            return null;
        }
        long sec = (leftMs + 999L) / 1000L;
        return "§7Phase Walk §8| §e" + sec + "s";
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
