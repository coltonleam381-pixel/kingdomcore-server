package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Zeus ability: call a lightning storm at looked position.
 * Damage is true-damage and caster is immune.
 */
public class ZeusAbility implements AbilityHandler {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;

    public ZeusAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "zeus";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        ZeusProfile profile = profile(level);
        if (profile == null) {
            return false;
        }

        Location center = getStormCenter(player, profile.castRangeBlocks);
        if (spawnRegionPolicy.blocksAbilities(center)) {
            return false;
        }

        startStorm(player, center, profile);
        return true;
    }

    private ZeusProfile profile(int level) {
        return switch (level) {
            case 1 -> new ZeusProfile(0, 6, 1, damagePerStrikeForLevel(level), 30, 60, -1);
            case 2 -> new ZeusProfile(7, 8, 1, damagePerStrikeForLevel(level), 73, 100, -1);
            case 3 -> new ZeusProfile(10, 10, 1, damagePerStrikeForLevel(level), 120, 100, -1);
            default -> null;
        };
    }

    public static double damagePerStrikeForLevel(int level) {
        return 1.0; // 0.5 hearts at all levels
    }

    public static boolean appliesSlownessOnHit(int level) {
        return level >= 3;
    }

    private Location getStormCenter(Player player, int castRangeBlocks) {
        if (castRangeBlocks <= 0) {
            return player.getLocation().clone().add(0.0, 1.0, 0.0);
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        var hit = player.getWorld().rayTraceBlocks(
                eye,
                direction,
                castRangeBlocks,
                org.bukkit.FluidCollisionMode.NEVER,
                true
        );
        if (hit != null && hit.getHitPosition() != null) {
            return new Location(
                    player.getWorld(),
                    hit.getHitPosition().getX(),
                    eye.getY(),
                    hit.getHitPosition().getZ()
            );
        }

        // Hard clamp to max allowed cast distance.
        Location fallback = eye.clone().add(direction.multiply(castRangeBlocks));
        return new Location(player.getWorld(), fallback.getX(), eye.getY(), fallback.getZ());
    }

    private void startStorm(Player caster, Location center, ZeusProfile profile) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.15f);

        new BukkitRunnable() {
            int elapsedTicks = 0;
            int strikesDone = 0;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }

                int shouldHaveByNow = (int) Math.floor(((double) (elapsedTicks + 1) * profile.pulses) / profile.durationTicks);
                while (strikesDone < shouldHaveByNow) {
                    if (profile.totalStrikes > 0 && strikesDone >= profile.totalStrikes) {
                        cancel();
                        return;
                    }
                    for (int i = 0; i < profile.boltsPerPulse; i++) {
                        executeSingleStrike(caster, center, profile, random);
                    }
                    strikesDone++;
                }

                elapsedTicks++;
                if (elapsedTicks >= profile.durationTicks && strikesDone >= profile.pulses) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeSingleStrike(Player caster, Location center, ZeusProfile profile, ThreadLocalRandom random) {
        Location strikeLoc = randomPointInRadius(center, profile.radiusBlocks, random);
        strikeLoc = snapToGround(strikeLoc, caster.getLocation().getBlockY());

        strikeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc, 8, 0.35, 0.15, 0.35, 0.02);
        // Visual "bolt through roof/floors" column from world top down to strike point.
        int maxY = strikeLoc.getWorld().getMaxHeight() - 1;
        for (int y = maxY; y >= strikeLoc.getBlockY(); y -= 2) {
            Location beam = new Location(strikeLoc.getWorld(), strikeLoc.getX(), y + 0.1, strikeLoc.getZ());
            strikeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, beam, 3, 0.06, 0.06, 0.06, 0.01);
        }
        strikeLoc.getWorld().strikeLightningEffect(strikeLoc);

        for (LivingEntity entity : strikeLoc.getWorld().getNearbyLivingEntities(strikeLoc, 1.9, 8.0, 1.9)) {
            if (entity.equals(caster)) {
                continue;
            }
            double dx = entity.getLocation().getX() - strikeLoc.getX();
            double dz = entity.getLocation().getZ() - strikeLoc.getZ();
            if ((dx * dx) + (dz * dz) > 3.61) {
                continue;
            }
            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(entity, caster, profile.damageHearts, strikeLoc, 0.35);
            }
            applyHitFeedback(entity, strikeLoc);
            if (profile.applySlow()) {
                var active = entity.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                if (active == null || active.getDuration() < 40 || active.getAmplifier() < 0) {
                    entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, false, false), true);
                }
            }
        }
    }

    private Location randomPointInRadius(Location center, double radius, ThreadLocalRandom random) {
        double angle = random.nextDouble(0.0, Math.PI * 2.0);
        double distance = Math.sqrt(random.nextDouble()) * radius;
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        return new Location(center.getWorld(), x, center.getY(), z);
    }

    private Location snapToGround(Location loc, int preferredY) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int minY = loc.getWorld().getMinHeight();
        int worldMaxY = loc.getWorld().getMaxHeight() - 1;

        int preferredStart = Math.min(Math.max(preferredY, minY), worldMaxY);
        for (int y = preferredStart; y >= minY; y--) {
            if (loc.getWorld().getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(loc.getWorld(), x + 0.5, y + 1.0, z + 0.5);
            }
        }

        int yStart = Math.min(loc.getBlockY(), worldMaxY);
        for (int y = yStart; y >= minY; y--) {
            if (loc.getWorld().getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(loc.getWorld(), x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return new Location(loc.getWorld(), x + 0.5, Math.max(minY + 1, loc.getY()), z + 0.5);
    }

    @Override
    public void cleanup(Player player) {
        // no persistent state
    }

    private void applyHitFeedback(LivingEntity entity, Location strikeLoc) {
        // Make each strike feel like an actual hit.
        entity.setNoDamageTicks(0);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.85f, 0.95f);
        entity.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                entity.getLocation().add(0, 1.0, 0),
                8,
                0.22, 0.25, 0.22, 0.01
        );

        Vector away = entity.getLocation().toVector().subtract(strikeLoc.toVector());
        away.setY(0.0);
        if (away.lengthSquared() > 0.0001) {
            away.normalize().multiply(0.26);
        } else {
            away.zero();
        }
        away.setY(0.22);
        entity.setVelocity(entity.getVelocity().multiply(0.4).add(away));
    }

    private record ZeusProfile(
            int castRangeBlocks,
            int radiusBlocks,
            int boltsPerPulse,
            double damageHearts,
            int pulses,
            int durationTicks,
            int totalStrikes
    ) {
        private boolean applySlow() {
            return castRangeBlocks >= 10; // L3
        }
    }
}
