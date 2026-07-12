package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.ParticleBroadcast;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Atlantis ability: Water wave knockback.
 * L3 has mode toggle (Wall/Sphere).
 */
public class AtlantisAbility implements AbilityHandler, AbilityHudProvider {
    private static final long DAMAGE_TICK_CD_MS = 600L;
    private static final double ENTITY_HIT_RADIUS = 2.0;
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, AtlantisMode> playerModes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> modeHudUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> targetDamageCdUntil = new ConcurrentHashMap<>();

    public AtlantisAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "atlantis";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        if (level == 3) {
            AtlantisMode mode = playerModes.getOrDefault(player.getUniqueId(), AtlantisMode.WALL);
            if (mode == AtlantisMode.SPHERE) {
                launchSphere(player, level);
            } else {
                launchWall(player, level, 7, 7, 12);
            }
        } else if (level == 2) {
            launchWall(player, level, 7, 7, 12);
        } else {
            launchWall(player, level, 5, 5, 7);
        }

        return true;
    }

    @Override
    public boolean onSneakRightClick(Player player, int level) {
        if (level != 3) {
            return false;
        }

        // Mode toggle allowed even in spawn
        AtlantisMode current = playerModes.getOrDefault(player.getUniqueId(), AtlantisMode.WALL);
        AtlantisMode newMode = (current == AtlantisMode.WALL) ? AtlantisMode.SPHERE : AtlantisMode.WALL;
        playerModes.put(player.getUniqueId(), newMode);
        modeHudUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1500L);
        player.sendActionBar("§bAtlantis mode: §f" + newMode);

        return true;
    }

    public String getHudLine(UUID playerId, long nowMs) {
        Long until = modeHudUntilMs.get(playerId);
        if (until == null || nowMs > until) {
            return null;
        }
        AtlantisMode mode = playerModes.getOrDefault(playerId, AtlantisMode.WALL);
        return "§bAtlantis mode: §f" + mode;
    }

    private void launchWall(Player player, int level, int width, int height, int distance) {
        double damage = wallDamageForLevel(level);
        Location start = player.getLocation().clone();
        Vector direction = start.getDirection().normalize();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Set<String> blockedSegments = new HashSet<>();

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 40; // 2 seconds

            @Override
            public void run() {
                if (tick++ >= duration) {
                    cancel();
                    return;
                }

                double progress = (double) tick * distance / duration;
                Location center = start.clone().add(direction.clone().multiply(progress));
                Set<UUID> processedThisTick = new HashSet<>();

                // Create wall of water particles
                for (int y = 0; y < height; y++) {
                    for (int x = -width / 2; x <= width / 2; x++) {
                        String segmentKey = x + ":" + y;
                        if (blockedSegments.contains(segmentKey)) {
                            continue;
                        }
                        Location particleLoc = center.clone()
                                .add(right.clone().multiply(x))
                                .add(0, y, 0);
                        if (isSolidAt(particleLoc)) {
                            blockedSegments.add(segmentKey);
                            continue;
                        }

                        ParticleBroadcast.particle(particleLoc.getWorld(), particleLoc, Particle.SPLASH, 3, 0.1, 0.1, 0.1, 0);

                        // Check for entities to damage/knockback (only once per tick per entity)
                        for (LivingEntity entity : particleLoc.getWorld().getNearbyLivingEntities(particleLoc, ENTITY_HIT_RADIUS)) {
                            if (entity.equals(player)) {
                                continue;
                            }
                            if (!processedThisTick.add(entity.getUniqueId())) {
                                continue;
                            }

                            // Knockback
                            Vector knock;
                            if (entity instanceof Player) {
                                knock = direction.clone().setY(0.20).multiply(0.45);
                            } else {
                                knock = direction.clone().setY(0.35).multiply(1.10);
                            }
                            entity.setVelocity(entity.getVelocity().multiply(0.45).add(knock));

                            // Damage
                            if (damage > 0 && canDamageNow(entity)) {
                                if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                                    kcPlugin.getDamageService().applyTrueDamage(entity, player, damage, center);
                                }
                                playHitFeedback(entity);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void launchSphere(Player player, int level) {
        double damage = sphereDamageForLevel(level);
        Location center = player.getLocation().clone();
        Set<String> blockedRays = new HashSet<>();

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 40; // 2 seconds
            final int maxRadius = 12;

            @Override
            public void run() {
                if (tick++ >= duration) {
                    cancel();
                    return;
                }

                double radius = (double) tick * maxRadius / duration;

                // Spawn sphere particles
                for (int theta = 0; theta < 180; theta += 15) {
                    for (int phi = 0; phi < 360; phi += 15) {
                        String rayKey = theta + ":" + phi;
                        if (blockedRays.contains(rayKey)) {
                            continue;
                        }
                        double radTheta = Math.toRadians(theta);
                        double radPhi = Math.toRadians(phi);

                        double x = radius * Math.sin(radTheta) * Math.cos(radPhi);
                        double y = radius * Math.cos(radTheta);
                        double z = radius * Math.sin(radTheta) * Math.sin(radPhi);

                        Location particleLoc = center.clone().add(x, y, z);
                        if (isSolidAt(particleLoc)) {
                            blockedRays.add(rayKey);
                            continue;
                        }
                        ParticleBroadcast.particle(particleLoc.getWorld(), particleLoc, Particle.SPLASH, 2, 0.1, 0.1, 0.1, 0);
                    }
                }

                // Damage and knockback entities within current radius
                for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, radius + ENTITY_HIT_RADIUS)) {
                    if (entity.equals(player)) {
                        continue;
                    }
                    if (entity.getLocation().distanceSquared(center) > (radius + ENTITY_HIT_RADIUS) * (radius + ENTITY_HIT_RADIUS)) {
                        continue;
                    }
                    if (!hasLineOfSight(center, entity.getLocation().add(0, 1.0, 0))) {
                        continue;
                    }
                    // Push outward
                    Vector knock = entity.getLocation().toVector().subtract(center.toVector()).normalize();
                    knock.setY(0.3).multiply(1.2);
                    entity.setVelocity(knock);

                    if (canDamageNow(entity)) {
                        if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                            kcPlugin.getDamageService().applyTrueDamage(entity, player, damage, center);
                        }
                        playHitFeedback(entity);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static double wallDamageForLevel(int level) {
        return switch (level) {
            case 1 -> 2.0;  // 1 heart per wave tick
            case 2 -> 4.0;  // 2 hearts per wave tick
            case 3 -> 5.0;  // 2.5 hearts per wave tick
            default -> 2.0;
        };
    }

    private static double sphereDamageForLevel(int level) {
        return switch (level) {
            case 3 -> 4.0;  // 2 hearts per wave tick
            default -> 3.0;
        };
    }

    private boolean isSolidAt(Location location) {
        var block = location.getBlock();
        return block.getType().isSolid() && !block.isPassable();
    }

    private boolean hasLineOfSight(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double distance = dir.length();
        if (distance <= 0.01) {
            return true;
        }
        dir.normalize();
        var hit = from.getWorld().rayTraceBlocks(from, dir, distance, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    private boolean canDamageNow(LivingEntity entity) {
        long now = System.currentTimeMillis();
        Long until = targetDamageCdUntil.get(entity.getUniqueId());
        if (until != null && now < until) {
            return false;
        }
        targetDamageCdUntil.put(entity.getUniqueId(), now + DAMAGE_TICK_CD_MS);
        return true;
    }

    private void playHitFeedback(LivingEntity entity) {
        entity.setNoDamageTicks(0);
        entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.8f, 1.05f);
        entity.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                entity.getLocation().add(0, 1.0, 0),
                8,
                0.2, 0.25, 0.2, 0.01
        );
    }

    public AtlantisMode getMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, AtlantisMode.WALL);
    }

    @Override
    public void cleanup(Player player) {
        // Modes persist
    }

    public enum AtlantisMode {
        WALL, SPHERE
    }
}
