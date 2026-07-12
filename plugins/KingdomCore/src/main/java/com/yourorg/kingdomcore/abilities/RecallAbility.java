package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recall ability - save location and teleport back after 5 seconds.
 */
public class RecallAbility implements AbilityHandler, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, RecallState> activeRecalls = new HashMap<>();
    private final Map<UUID, Integer> level3DelayIndex = new HashMap<>();
    private final Map<UUID, Long> switchHudUntilMs = new HashMap<>();
    private static final int[] LEVEL3_DELAYS_SECONDS = {2, 3, 5, 6};
    private static final int POST_TELEPORT_BUFF_TICKS = 60;
    
    private static class RecallState {
        final Location savedLocation;
        final int taskId;
        final long teleportAtMs;
        
        RecallState(Location savedLocation, int taskId, long teleportAtMs) {
            this.savedLocation = savedLocation.clone();
            this.taskId = taskId;
            this.teleportAtMs = teleportAtMs;
        }
    }
    
    public RecallAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }
    
    @Override
    public String getAbilityId() {
        return "recall";
    }
    
    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        // Level 0 = locked
        if (level == 0) {
            return false;
        }
        
        // Block in spawn
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        
        // Cancel existing recall if any
        RecallState existing = activeRecalls.remove(player.getUniqueId());
        if (existing != null) {
            Bukkit.getScheduler().cancelTask(existing.taskId);
        }
        
        int delaySeconds = delaySecondsFor(player, level);

        // Save current location
        Location savedLoc = player.getLocation().clone();
        
        // Visual feedback
        player.getWorld().playSound(savedLoc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.ENCHANT, savedLoc, 20, 0.5, 0.5, 0.5, 0);
        
        // Schedule teleport after selected delay
        long teleportAtMs = System.currentTimeMillis() + (delaySeconds * 1000L);
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                player.teleport(savedLoc);
                player.getWorld().playSound(savedLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.PORTAL, savedLoc, 30, 0.5, 0.5, 0.5, 0.5);
                applyPostTeleportBuffs(player, level);
            }
            activeRecalls.remove(player.getUniqueId());
        }, delaySeconds * 20L);
        
        activeRecalls.put(player.getUniqueId(), new RecallState(savedLoc, taskId, teleportAtMs));
        return true;
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        RecallState state = activeRecalls.get(playerId);
        if (state == null) {
            Integer idx = level3DelayIndex.get(playerId);
            if (idx != null && idx >= 0 && idx < LEVEL3_DELAYS_SECONDS.length) {
                long holdUntil = switchHudUntilMs.getOrDefault(playerId, 0L);
                int selected = LEVEL3_DELAYS_SECONDS[idx];
                if (nowMs <= holdUntil) {
                    return "§dRecall delay: §f" + selected + "s";
                }
                return "§dRecall §8| §aReady §8(" + selected + "s)";
            }
            return null;
        }
        long left = Math.max(0L, state.teleportAtMs - nowMs);
        long sec = (left + 999L) / 1000L;
        return "§dRecall in §f" + sec + "s";
    }
    
    @Override
    public boolean onLeftClick(Player player, int level) {
        if (level < 3) {
            return false;
        }
        int current = level3DelayIndex.getOrDefault(player.getUniqueId(), 0);
        int next = (current + 1) % LEVEL3_DELAYS_SECONDS.length;
        level3DelayIndex.put(player.getUniqueId(), next);
        switchHudUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1500L);
        player.sendActionBar("§dRecall delay: §f" + LEVEL3_DELAYS_SECONDS[next] + "s");
        return false;
    }
    
    @Override
    public boolean onSneakRightClick(Player player, int level) {
        return false;
    }
    
    @Override
    public boolean onSpace(Player player) {
        return false;
    }
    
    @Override
    public void cleanup(Player player) {
        // Cancel recall on death/logout
        RecallState state = activeRecalls.remove(player.getUniqueId());
        if (state != null) {
            Bukkit.getScheduler().cancelTask(state.taskId);
        }
        switchHudUntilMs.remove(player.getUniqueId());
    }

    private int delaySecondsFor(Player player, int level) {
        if (level <= 1) {
            return 3;
        }
        if (level == 2) {
            return 5;
        }
        int index = level3DelayIndex.getOrDefault(player.getUniqueId(), 0);
        if (index < 0 || index >= LEVEL3_DELAYS_SECONDS.length) {
            index = 0;
        }
        return LEVEL3_DELAYS_SECONDS[index];
    }

    private void applyPostTeleportBuffs(Player player, int level) {
        if (level < 2) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, POST_TELEPORT_BUFF_TICKS, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION, POST_TELEPORT_BUFF_TICKS, 1, false, true, true));
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, POST_TELEPORT_BUFF_TICKS, 1, false, true, true));
        }
    }
}
