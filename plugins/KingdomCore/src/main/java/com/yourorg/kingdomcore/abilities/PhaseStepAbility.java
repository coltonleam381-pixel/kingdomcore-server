package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.service.CombatTagService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class PhaseStepAbility implements AbilityHandler, EntityInteractAbility, CooldownOverrideAbility {
    private static final double DEFAULT_INTERACT_RANGE = 5.0;
    private static final double SEGMENT_SAMPLE_STEP = 0.5;
    private static final double SEGMENT_SAMPLE_RADIUS = 0.6;

    private final WorldGuardHook worldGuardHook;
    private final CombatTagService combatTagService;
    private final SpawnRegionPolicy spawnRegionPolicy;

    public PhaseStepAbility(WorldGuardHook worldGuardHook, CombatTagService combatTagService, SpawnRegionPolicy spawnRegionPolicy) {
        this.worldGuardHook = worldGuardHook;
        this.combatTagService = combatTagService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "phase_step";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                rangeForLevel(level),
                0.4,
                entity -> entity instanceof LivingEntity living && !living.equals(player)
        );
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            return false;
        }
        return performPhaseStep(player, target, level);
    }

    @Override
    public boolean onEntityRightClick(Player player, LivingEntity target, int level, PlayerInteractEntityEvent event) {
        return performPhaseStep(player, target, level);
    }

    private boolean performPhaseStep(Player player, LivingEntity target, int level) {
        if (level == 0) {
            return false;
        }
        if (target == null || target.equals(player)) {
            return false;
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(target.getLocation())) {
            return false;
        }

        Location casterLocation = player.getLocation();
        Location targetLocation = target.getLocation();
        double maxRange = rangeForLevel(level);
        if (!isWithinRange(casterLocation, targetLocation, maxRange)) {
            return false;
        }
        Location desired = computeOppositeDestination(casterLocation, targetLocation, maxRange);
        if (desired == null) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(desired)) {
            return false;
        }
        Location safeDestination = findSafeDestination(desired);
        if (safeDestination == null) {
            return false;
        }

        Location teleportLoc = safeDestination.clone();
        // Keep player pitch (avoid forced "look up"), only rotate yaw to face target.
        float preservedPitch = casterLocation.getPitch();
        teleportLoc.setYaw(yawTowards(teleportLoc, targetLocation));
        teleportLoc.setPitch(preservedPitch);
        player.teleport(teleportLoc);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20, 0, false, true), true); // Strength I, 1s
        }

        Set<LivingEntity> affected = collectEntitiesAlongSegment(casterLocation, safeDestination, player);
        affected.add(target);
        for (LivingEntity entity : affected) {
            applyDebuffs(entity, level);
        }

        Player targetPlayer = target instanceof Player ? (Player) target : null;
        combatTagService.tagPlayer(player, targetPlayer);
        for (LivingEntity entity : affected) {
            if (entity instanceof Player affectedPlayer && !affectedPlayer.equals(player)) {
                combatTagService.tagPlayer(affectedPlayer, player);
            }
        }

        return true;
    }

    @Override
    public long getCooldownMs(int level) {
        return switch (level) {
            case 1 -> 45000L;
            case 2 -> 40000L;
            case 3 -> 35000L;
            default -> 45000L;
        };
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
        // No persistent state
    }

    static Location computeOppositeDestination(Location caster, Location target, double maxRange) {
        Vector direction = target.toVector().subtract(caster.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            return null;
        }
        double distance = direction.length();
        if (distance > maxRange) {
            return null;
        }
        direction.normalize();
        return target.clone().add(direction.multiply(distance));
    }

    static Location findSafeDestination(Location desired, Predicate<Location> isSafe) {
        if (isSafe.test(desired)) {
            return desired;
        }

        int[] offsets = {0, 1, -1, 2, -2};
        int[] verticals = {0, 1, -1, 2, -2};
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy : verticals) {
                    Location candidate = desired.clone().add(dx, dy, dz);
                    if (isSafe.test(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    static boolean isWithinRange(Location from, Location to, double maxRange) {
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }
        double maxRangeSquared = maxRange * maxRange;
        return from.distanceSquared(to) <= maxRangeSquared;
    }

    private Location findSafeDestination(Location desired) {
        return findSafeDestination(desired, this::isSafeLocation);
    }

    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        Block below = location.clone().add(0, -1, 0).getBlock();

        boolean hasHeadroom = head.isPassable() || !head.getType().isSolid();
        boolean hasFeetRoom = feet.isPassable() || !feet.getType().isSolid();
        boolean hasGround = below.getType().isSolid();

        return hasHeadroom && hasFeetRoom && hasGround;
    }

    private Set<LivingEntity> collectEntitiesAlongSegment(Location from, Location to, Player caster) {
        Set<LivingEntity> affected = new HashSet<>();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 1.0E-6) {
            return affected;
        }
        direction.normalize();

        for (double d = 0; d <= distance; d += SEGMENT_SAMPLE_STEP) {
            Location sample = from.clone().add(direction.clone().multiply(d));
            for (LivingEntity entity : sample.getWorld().getNearbyLivingEntities(sample, SEGMENT_SAMPLE_RADIUS)) {
                if (!entity.equals(caster)) {
                    affected.add(entity);
                }
            }
        }
        return affected;
    }

    private void applyDebuffs(LivingEntity target, int level) {
        switch (level) {
            case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true), true);
            case 2 -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, true), true);
            }
            case 3 -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, true), true);
            }
            default -> {
            }
        }
    }

    private double rangeForLevel(int level) {
        return switch (level) {
            case 1 -> 3.0;
            case 2 -> 4.0;
            case 3 -> 5.0;
            default -> DEFAULT_INTERACT_RANGE;
        };
    }

    private float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
