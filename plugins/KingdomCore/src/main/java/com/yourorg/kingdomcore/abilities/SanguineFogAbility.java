package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.ParticleBroadcast;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sanguine Fog ability - blindness + red fog + optional speed boost.
 * Cooldown starts after the fog duration ends.
 */
public class SanguineFogAbility implements AbilityHandler, AbilityHudProvider, CooldownOverrideAbility {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Set<UUID> activeFogs = new HashSet<>();
    private final Map<UUID, Integer> activeFogTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeFogEndsAt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeFogLevels = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public SanguineFogAbility(Plugin plugin, WorldGuardHook worldGuardHook, CooldownService cooldownService, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "sanguine_fog";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        if (activeFogs.contains(player.getUniqueId())) {
            return false;
        }

        int durationSeconds = switch (level) {
            case 1 -> 5;
            case 2 -> 7;
            case 3 -> 8;
            default -> 5;
        };

        UUID playerId = player.getUniqueId();
        activeFogs.add(playerId);
        activeFogLevels.put(playerId, level);
        activeFogEndsAt.put(playerId, System.currentTimeMillis() + (durationSeconds * 1000L));

        player.sendActionBar("§4Sanguine Fog §f" + durationSeconds + "s");
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.45f, 1.35f);

        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false));
        }

        final double radius = 7.0;
        int totalTicks = durationSeconds * 20;
        int taskId = new BukkitRunnable() {
            int ticksRemaining = totalTicks;

            @Override
            public void run() {
                if (!player.isOnline() || ticksRemaining <= 0) {
                    finishFog(player);
                    cancel();
                    return;
                }

                Location center = player.getLocation().clone().add(0, 1.0, 0);
                spawnDenseFog(center, radius, level);

                if (ticksRemaining % 20 == 0) {
                    for (Player target : center.getWorld().getPlayers()) {
                        if (target.equals(player)) {
                            continue;
                        }
                        if (target.getLocation().distanceSquared(center) > radius * radius) {
                            continue;
                        }
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                        if (level >= 2) {
                            float randomYaw = random.nextFloat() * 360f;
                            Location newLoc = target.getLocation().clone();
                            newLoc.setYaw(randomYaw);
                            target.teleport(newLoc);
                        }
                        if (level >= 3) {
                            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                                kcPlugin.getDamageService().applyTrueDamage(target, player, 0.6, center);
                            }
                        }
                    }
                }

                ticksRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        activeFogTasks.put(playerId, taskId);
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
        Long endAt = activeFogEndsAt.get(playerId);
        if (endAt == null) {
            return null;
        }
        long remainingMs = Math.max(0L, endAt - nowMs);
        long remainingSec = (remainingMs + 999L) / 1000L;
        return "§4Sanguine Fog §8| §e" + remainingSec + "s";
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
        Integer taskId = activeFogTasks.remove(id);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        activeFogs.remove(id);
        activeFogEndsAt.remove(id);
        activeFogLevels.remove(id);
    }

    private void finishFog(Player player) {
        UUID id = player.getUniqueId();
        Integer level = activeFogLevels.get(id);
        cleanup(player);
        if (level != null) {
            cooldownService.markUsed(id, getAbilityId(), System.currentTimeMillis() + getCooldownMs(level));
        }
    }

    /**
     * Thick red fog visible to everyone nearby, including the caster (force=true particles).
     */
    private void spawnDenseFog(Location center, double radius, int level) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int particleBurst = switch (level) {
            case 1 -> 55;
            case 2 -> 75;
            case 3 -> 95;
            default -> 55;
        };

        Particle.DustOptions darkRed = new Particle.DustOptions(Color.fromRGB(95, 5, 5), 2.4f);
        Particle.DustOptions bloodRed = new Particle.DustOptions(Color.fromRGB(185, 20, 20), 2.0f);
        Particle.DustOptions crimson = new Particle.DustOptions(Color.fromRGB(220, 35, 35), 1.6f);

        for (int i = 0; i < particleBurst; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double yNorm = random.nextDouble();
            double y = (yNorm * yNorm * radius * 1.35) - (radius * 0.25);
            double horizontal = radius * Math.sqrt(random.nextDouble()) * (1.05 - yNorm * 0.25);
            double x = horizontal * Math.cos(theta);
            double z = horizontal * Math.sin(theta);
            Location loc = center.clone().add(x, y, z);

            int roll = random.nextInt(100);
            if (roll < 50) {
                Particle.DustOptions dust = switch (random.nextInt(3)) {
                    case 0 -> darkRed;
                    case 1 -> bloodRed;
                    default -> crimson;
                };
                ParticleBroadcast.particle(world, loc, Particle.DUST, 1, 0.12, 0.12, 0.12, 0.0, dust);
            } else if (roll < 78) {
                ParticleBroadcast.particle(world, loc, Particle.SMOKE, 2, 0.18, 0.22, 0.18, 0.015);
            } else if (roll < 92) {
                ParticleBroadcast.particle(world, loc, Particle.CLOUD, 1, 0.25, 0.12, 0.25, 0.03);
            } else {
                ParticleBroadcast.particle(world, loc, Particle.ENTITY_EFFECT, 1, 0.2, 0.2, 0.2, 0.0,
                        Color.fromRGB(160, 10, 10));
            }
        }

        int groundRing = 14 + level * 4;
        for (int i = 0; i < groundRing; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double r = radius * (0.25 + random.nextDouble() * 0.75);
            Location loc = center.clone().add(
                    Math.cos(angle) * r,
                    random.nextDouble() * 2.0 - 0.5,
                    Math.sin(angle) * r
            );
            ParticleBroadcast.particle(world, loc, Particle.DUST, 2, 0.2, 0.08, 0.2, 0.0, darkRed);
            if (i % 3 == 0) {
                ParticleBroadcast.particle(world, loc, Particle.SMOKE, 3, 0.15, 0.1, 0.15, 0.02);
            }
        }

        ParticleBroadcast.particle(world, center, Particle.SMOKE, 6, radius * 0.45, 0.6, radius * 0.45, 0.02);
    }
}
