package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifesteal ability - drain true damage from nearby targets to heal caster.
 * Active for 2 seconds, cooldown starts when effect ends.
 */
public class LifestealAbility implements AbilityHandler, AbilityHudProvider, CooldownOverrideAbility {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Set<UUID> activeLifesteals = new HashSet<>();
    private final Map<UUID, Integer> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeEndsAt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeLevels = new ConcurrentHashMap<>();

    public LifestealAbility(Plugin plugin, WorldGuardHook worldGuardHook, CooldownService cooldownService, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "lifesteal";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        if (activeLifesteals.contains(player.getUniqueId())) {
            return false;
        }

        int maxTargets = switch (level) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> 1;
        };

        double drainPerSecond = switch (level) {
            case 1 -> 1.0;  // 0.5 hearts true damage
            case 2 -> 1.5;  // 0.75 hearts true damage
            case 3 -> 2.0;  // 1 heart true damage
            default -> 1.0;
        };

        UUID playerId = player.getUniqueId();
        activeLifesteals.add(playerId);
        activeLevels.put(playerId, level);
        activeEndsAt.put(playerId, System.currentTimeMillis() + 2000L);
        if (level >= 3) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.STRENGTH, 40, 0, false, true
            ), true); // Strength I, 2 seconds
        }

        int taskId = new BukkitRunnable() {
            int ticksRemaining = 40; // 2 seconds

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticksRemaining <= 0) {
                    finishLifesteal(player);
                    cancel();
                    return;
                }

                // Every second: true damage pulses (2 pulses total)
                if (ticksRemaining % 20 == 0) {
                    drainNearbyTargets(player, maxTargets, drainPerSecond);
                }

                if (ticksRemaining % 5 == 0) {
                    player.getWorld().spawnParticle(
                            Particle.DAMAGE_INDICATOR,
                            player.getLocation().add(0, 1, 0),
                            4,
                            0.45, 0.5, 0.45, 0.0
                    );
                }

                ticksRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        activeTasks.put(playerId, taskId);
        return true;
    }

    @Override
    public long getCooldownMs(int level) {
        return switch (level) {
            case 1 -> 50000L;
            case 2 -> 45000L;
            case 3 -> 40000L;
            default -> 50000L;
        };
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        Long endAt = activeEndsAt.get(playerId);
        if (endAt == null) {
            return null;
        }
        long remainingMs = Math.max(0L, endAt - nowMs);
        long sec = (remainingMs + 999L) / 1000L;
        return "§cLifesteal §8| §e" + sec + "s";
    }

    private void drainNearbyTargets(Player caster, int maxTargets, double drainAmount) {
        Location center = caster.getLocation();
        double radius = 8.0;
        var nearby = center.getWorld().getNearbyLivingEntities(center, radius);
        java.util.List<LivingEntity> sorted = new java.util.ArrayList<>();
        for (LivingEntity entity : nearby) {
            if (!entity.equals(caster)) {
                sorted.add(entity);
            }
        }
        sorted.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));

        int targetsDrained = 0;
        for (LivingEntity entity : sorted) {
            if (targetsDrained >= maxTargets) {
                break;
            }
            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(entity, caster, drainAmount, caster.getLocation());
                
                double maxHealth = java.util.Objects.requireNonNull(caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).getValue();
                kcPlugin.getDamageService().setHealthBypassingRules(caster, Math.min(maxHealth, caster.getHealth() + drainAmount));
            }
            drawLifestealBeam(caster.getLocation().add(0, 1, 0), entity.getLocation().add(0, 1, 0));
            targetsDrained++;
        }
    }

    private void drawLifestealBeam(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);

        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = from.clone().add(direction.clone().multiply(d));
            particleLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    1,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 0), 0.5f)
            );
        }
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
        UUID id = player.getUniqueId();
        Integer taskId = activeTasks.remove(id);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        activeLifesteals.remove(id);
        activeEndsAt.remove(id);
        activeLevels.remove(id);
    }

    private void finishLifesteal(Player player) {
        UUID id = player.getUniqueId();
        Integer level = activeLevels.get(id);
        cleanup(player);
        if (level != null) {
            cooldownService.markUsed(id, getAbilityId(), System.currentTimeMillis() + getCooldownMs(level));
        }
    }
}
