package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heart Shield ability - temporary absorption based on max health.
 */
public class HeartShieldAbility implements AbilityHandler, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, Integer> clearTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeEndsAtMs = new ConcurrentHashMap<>();
    
    public HeartShieldAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }
    
    @Override
    public String getAbilityId() {
        return "heart_shield";
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
        
        int durationSeconds;
        int absorptionAmp;
        
        switch (level) {
            case 1:
                durationSeconds = 5;
                absorptionAmp = 2; // +6 hearts
                break;
            case 2:
                durationSeconds = 7;
                absorptionAmp = 4; // +10 hearts
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
                break;
            case 3:
                durationSeconds = 10;
                absorptionAmp = 7; // +16 hearts
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
                PotionEffect existingSpeed = player.getPotionEffect(PotionEffectType.SPEED);
                int amp = (existingSpeed == null) ? 0 : Math.min(existingSpeed.getAmplifier() + 1, 4);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, amp, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                break;
            default:
                return false;
        }
        
        Integer existing = clearTasks.remove(player.getUniqueId());
        if (existing != null) {
            plugin.getServer().getScheduler().cancelTask(existing);
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                durationSeconds * 20,
                absorptionAmp,
                false,
                false
        ));
        
        // Visual/sound feedback
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
        player.sendActionBar("§dHeart Shield: §f" + durationSeconds + "s");
        activeEndsAtMs.put(player.getUniqueId(), System.currentTimeMillis() + (durationSeconds * 1000L));

        // Clear absorption after duration
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.ABSORPTION);
                    player.setAbsorptionAmount(0);
                }
                clearTasks.remove(player.getUniqueId());
                activeEndsAtMs.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, durationSeconds * 20L).getTaskId();
        clearTasks.put(player.getUniqueId(), taskId);
        return true;
    }
    
    @Override
    public boolean onLeftClick(Player player, int level) {
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
        // Clear any remaining absorption on logout/death
        player.setAbsorptionAmount(0);
        activeEndsAtMs.remove(player.getUniqueId());
        Integer taskId = clearTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        Long endAt = activeEndsAtMs.get(playerId);
        if (endAt == null) {
            return null;
        }
        long leftMs = Math.max(0L, endAt - nowMs);
        if (leftMs <= 0L) {
            return null;
        }
        long sec = (leftMs + 999L) / 1000L;
        return "§dHeart Shield §8| §e" + sec + "s";
    }
}
