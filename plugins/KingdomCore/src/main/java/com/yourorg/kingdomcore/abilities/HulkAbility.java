package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.core.services.CooldownService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hulk ability: Two-stage super jump and slam.
 * Stage 1: launch and hover for selection.
 * Stage 2: choose landing point (RMB) and dive/slam.
 */
public class HulkAbility implements AbilityHandler, Listener {
    private static final long SECOND_CLICK_ARM_DELAY_MS = 120L;
    private static final int MAX_DIVE_TICKS = 60;
    private static final long FALL_IMMUNITY_MS = 10000L;
    private static final double LANDING_TOLERANCE_BLOCKS = 0.65;

    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, HulkState> activeStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFallUntil = new ConcurrentHashMap<>();

    public HulkAbility(Plugin plugin,
                       WorldGuardHook worldGuardHook,
                       CooldownService cooldownService,
                       SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getAbilityId() {
        return "hulk";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        HulkState state = activeStates.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (state == null) {
            return startStage1(player, level);
        }

        // stale safety
        if (now >= state.expiresAtMs) {
            activeStates.remove(player.getUniqueId());
            restoreFlightState(player, state);
            plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
            plugin.getServer().getScheduler().cancelTask(state.hoverTaskId);
        plugin.getServer().getScheduler().cancelTask(state.circleTaskId);
            return startStage1(player, level);
        }

        // prevent accidental same-click stage2 trigger
        if (now - state.createdAtMs < SECOND_CLICK_ARM_DELAY_MS) {
            return false;
        }

        return chooseDestination(player, state);
    }

    private boolean startStage1(Player player, int level) {
        double launchHeight = switch (level) {
            case 1 -> 7.0;
            case 2 -> 8.5;
            case 3 -> 10.0;
            default -> 0.0;
        };

        int chooseDuration = switch (level) {
            case 1 -> 60;
            case 2 -> 80;
            case 3 -> 100;
            default -> 0;
        };

        int range = switch (level) {
            case 1 -> 8;
            case 2 -> 16;
            case 3 -> 30;
            default -> 0;
        };

        Location launchLoc = player.getLocation().clone();

        double launchSpeed = switch (level) {
            case 3 -> 1.60;
            default -> 1.28;
        };
        player.setVelocity(new Vector(0, launchSpeed, 0));

        long now = System.currentTimeMillis();
        HulkState state = new HulkState(
                launchLoc,
                range,
                level,
                chooseDuration,
                launchLoc.getY() + launchHeight,
                now,
                now + (chooseDuration * 50L),
                player.hasGravity()
        );
        player.setGravity(true);
        activeStates.put(player.getUniqueId(), state);

        int hoverTaskId = new BukkitRunnable() {
            int warmupTicks = 0;

            @Override
            public void run() {
                HulkState current = activeStates.get(player.getUniqueId());
                if (current == null) {
                    cancel();
                    return;
                }

                player.setFallDistance(0);

                // Prevent overshooting hover height without camera jitter.
                if (player.getLocation().getY() > current.hoverY + 0.10) {
                    Vector vCap = player.getVelocity();
                    player.setVelocity(new Vector(vCap.getX() * 0.12, -0.12, vCap.getZ() * 0.12));
                    long remainingMs = Math.max(0L, current.expiresAtMs - System.currentTimeMillis());
                    long remainingSec = (remainingMs + 999L) / 1000L;
                    player.sendActionBar("§aChoose landing spot §7(RMB) §8| §f" + remainingSec + "s");
                    return;
                }

                // Let launch happen briefly, but keep checking the height cap above.
                if (warmupTicks++ < 6) {
                    long remainingMs = Math.max(0L, current.expiresAtMs - System.currentTimeMillis());
                    long remainingSec = (remainingMs + 999L) / 1000L;
                    player.sendActionBar("§aChoose landing spot §7(RMB) §8| §f" + remainingSec + "s");
                    return;
                }

                Vector v = player.getVelocity();
                double dy = current.hoverY - player.getLocation().getY();
                double yVel = Math.max(-0.60, Math.min(0.60, dy * 0.25));
                if (Math.abs(dy) < 0.20) {
                    yVel = 0.03;
                }

                player.setVelocity(new Vector(v.getX() * 0.22, yVel, v.getZ() * 0.22));

                long remainingMs = Math.max(0L, current.expiresAtMs - System.currentTimeMillis());
                long remainingSec = (remainingMs + 999L) / 1000L;
                player.sendActionBar("§aChoose landing spot §7(RMB) §8| §f" + remainingSec + "s");
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
        state.hoverTaskId = hoverTaskId;

        showLandingCircle(player, state);

        int expiryTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                HulkState current = activeStates.remove(player.getUniqueId());
                if (current != null) {
                    restoreFlightState(player, current);
                    markAbilityCooldown(player, current.level);
                    grantNoFallDamage(player);
                    player.setFallDistance(0);
                    plugin.getServer().getScheduler().cancelTask(current.hoverTaskId);
                    plugin.getServer().getScheduler().cancelTask(current.circleTaskId);
                }
            }
        }.runTaskLater(plugin, chooseDuration).getTaskId();
        state.expiryTaskId = expiryTaskId;

        // Stage 1 does not consume cooldown.
        return false;
    }

    private boolean chooseDestination(Player player, HulkState state) {
        int raycastDistance = Math.max(64, state.range * 6);
        Block hitBlock = player.getTargetBlockExact(raycastDistance);
        if (hitBlock == null || hitBlock.getType() == Material.AIR) {
            showMissFeedback(player, state);
            markAbilityCooldown(player, state.level);
            dropNoSlam(player, state);
            // Requested: missed circle/target should consume cooldown.
            return true;
        }

        Location target = hitBlock.getLocation();

        double dx = (target.getX() + 0.5) - state.launchOrigin.getX();
        double dz = (target.getZ() + 0.5) - state.launchOrigin.getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        if (horizontalDistance > state.range + LANDING_TOLERANCE_BLOCKS) {
            showMissFeedback(player, state);
            markAbilityCooldown(player, state.level);
            dropNoSlam(player, state);
            return true;
        }

        activeStates.remove(player.getUniqueId());
        restoreFlightState(player, state);
        markAbilityCooldown(player, state.level);
        plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
        plugin.getServer().getScheduler().cancelTask(state.hoverTaskId);

        target.setY(target.getY() + 1.0);
        startDiveAndSlam(player, target, state.level);
        return true;
    }

    private void startDiveAndSlam(Player player, Location target, int level) {
        // Requested: landing speed faster.
        final double horizontalSpeed = switch (level) {
            case 1 -> 1.33;
            case 2 -> 2.10;
            case 3 -> 2.40;
            default -> 1.90;
        };
        final double diveSpeedNear = switch (level) {
            case 1 -> 1.26;
            case 2 -> 2.0;
            case 3 -> 2.3;
            default -> 1.8;
        };

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                Location current = player.getLocation();
                Vector toTarget = target.toVector().subtract(current.toVector());
                double horizontalDist = Math.hypot(toTarget.getX(), toTarget.getZ());
                boolean reached = horizontalDist <= 1.35 && current.getY() <= target.getY() + 1.15;

                if (reached) {
                    Location slamCenter = player.getLocation().clone();
                    slamCenter.setY(Math.max(target.getY(), slamCenter.getY()));
                    grantNoFallDamage(player);
                    player.setFallDistance(0);
                    performSlam(player, slamCenter, level);
                    cancel();
                    return;
                }

                // Guarantee that far-edge selected points still complete correctly.
                if (ticks >= MAX_DIVE_TICKS) {
                    grantNoFallDamage(player);
                    player.setFallDistance(0);
                    cancel();
                    return;
                }

                // Allow slam-on-ground only when already near selected point.
                if (player.isOnGround() && horizontalDist <= 1.6) {
                    Location slamCenter = player.getLocation().clone();
                    slamCenter.setY(Math.max(target.getY(), slamCenter.getY()));
                    grantNoFallDamage(player);
                    player.setFallDistance(0);
                    performSlam(player, slamCenter, level);
                    cancel();
                    return;
                }

                Vector horizontal = new Vector(toTarget.getX(), 0, toTarget.getZ());
                if (horizontalDist < 0.75) {
                    horizontal.zero();
                } else if (horizontal.lengthSquared() > 0.0001) {
                    double scaledHorizontalSpeed = Math.min(horizontalSpeed, Math.max(0.22, horizontalDist * 0.35));
                    horizontal.normalize().multiply(scaledHorizontalSpeed);
                } else {
                    horizontal.zero();
                }

                // Adaptive vertical drop: keep glide for long distances, dive hard near target.
                double yVel;
                if (horizontalDist > 10.0) {
                    yVel = -0.65;
                } else if (horizontalDist > 4.0) {
                    yVel = -1.1;
                } else {
                    yVel = -diveSpeedNear;
                }

                player.setVelocity(new Vector(horizontal.getX(), yVel, horizontal.getZ()));
                player.getWorld().spawnParticle(Particle.CLOUD, current, 2, 0.12, 0.05, 0.12, 0.005);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void performSlam(Player player, Location center, int level) {
        // Requested exact HP values: L1 4, L2 6, L3 8.
        double damage = switch (level) {
            case 1 -> 4.0;
            case 2 -> 6.0;
            case 3 -> 8.0;
            default -> 0.0;
        };

        int radius = switch (level) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 4;
            default -> 0;
        };

        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, radius)) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity.getLocation().distance(center) > radius) {
                continue;
            }

            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(entity, player, damage, center, 0.5);
            }

            if (level >= 2 && entity instanceof Player target) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
            }
            if (level >= 3 && entity instanceof Player target) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            }
        }

        // Requested: landing effect as an upward nautilus spiral (~0.8s).
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 10) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Location base = center.clone().add(0.0, 0.15, 0.0);
                double progress = ticks / 10.0;
                double y = progress * 2.0;
                double radius = 1.4 - (progress * 0.55);
                for (int i = 0; i < 8; i++) {
                    double angle = (ticks * 0.95) + (i * (Math.PI * 2.0 / 8.0));
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = base.clone().add(x, y, z);
                    player.spawnParticle(Particle.NAUTILUS, p, 1, 0.02, 0.02, 0.02, 0.0, null, true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.25f, 0.72f);
        center.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.85f, 1.1f);
        // Caster impact feel without damage.
        player.setNoDamageTicks(0);
        player.damage(0.0);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.95f);
    }

    private void showLandingCircle(Player player, HulkState state) {
        int circleTaskId = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!activeStates.containsKey(player.getUniqueId()) || ticks++ > state.chooseDuration) {
                    cancel();
                    return;
                }

                Location center = state.launchOrigin;
                double referenceY = player.getLocation().getY();
                int angleStep = 5;
                int ringThickness = switch (state.level) {
                    case 1 -> 1;
                    case 2 -> 1;
                    case 3 -> 2;
                    default -> 1;
                };

                for (int angle = 0; angle < 360; angle += angleStep) {
                    double radians = Math.toRadians(angle);
                    for (int layer = 0; layer < ringThickness; layer++) {
                        double radius = state.range - (layer * 0.18);
                        double x = center.getX() + radius * Math.cos(radians);
                        double z = center.getZ() + radius * Math.sin(radians);
                        Location point = groundAlignedPoint(center, x, z, referenceY, 0.18);
                        // Evenly distributed ring points (no local offset clumps).
                        if ((ticks + angle + layer) % 2 == 0) {
                            player.spawnParticle(Particle.GLOW, point, 1, 0.0, 0.0, 0.0, 0.0, null, true);
                        } else {
                            player.spawnParticle(Particle.WITCH, point, 1, 0.0, 0.0, 0.0, 0.0, null, true);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
        state.circleTaskId = circleTaskId;
    }

    private Location groundAlignedPoint(Location center, double x, double z, double referenceY, double yOffset) {
        Location rayStart = new Location(center.getWorld(), x, referenceY + 1.25, z);
        RayTraceResult hit = center.getWorld().rayTraceBlocks(
                rayStart,
                new Vector(0, -1, 0),
                80.0,
                FluidCollisionMode.NEVER,
                true
        );

        double y;
        if (hit != null && hit.getHitPosition() != null) {
            y = hit.getHitPosition().getY() + yOffset;
        } else {
            // Fallback clamp: never jump to a much higher top-surface (important for caves).
            double topY = center.getWorld().getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z)).getY() + 1.0 + yOffset;
            y = Math.min(topY, referenceY + 0.15 + yOffset);
        }
        return new Location(center.getWorld(), x, y, z);
    }

    private void showMissFeedback(Player player, HulkState state) {
        player.sendActionBar("§cMissed! Outside landing range.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 0.85f);
        showRedParticles(player, state);
    }

    private void showRedParticles(Player player, HulkState state) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline() || t++ >= 14) {
                    cancel();
                    return;
                }
                Location center = state.launchOrigin;
                for (int angle = 0; angle < 360; angle += 8) {
                    double radians = Math.toRadians(angle);
                    double x = center.getX() + state.range * Math.cos(radians);
                    double z = center.getZ() + state.range * Math.sin(radians);
                    Location ring = groundAlignedPoint(center, x, z, player.getLocation().getY(), 0.07);
                    center.getWorld().spawnParticle(Particle.DUST, ring, 1, 0.0, 0.0, 0.0, 0.0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 60, 60), 1.1f));
                    if ((angle + t) % 16 == 0) {
                        center.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, ring, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void dropNoSlam(Player player, HulkState state) {
        activeStates.remove(player.getUniqueId());
        restoreFlightState(player, state);
        grantNoFallDamage(player);
        player.setFallDistance(0);
        plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
        plugin.getServer().getScheduler().cancelTask(state.hoverTaskId);
        plugin.getServer().getScheduler().cancelTask(state.circleTaskId);
    }

    private void markAbilityCooldown(Player player, int level) {
        long now = System.currentTimeMillis();
        cooldownService.markUsed(player.getUniqueId(), getAbilityId(), now + cooldownMsForLevel(level));
    }

    private long cooldownMsForLevel(int level) {
        return switch (level) {
            case 1 -> 50000L;
            case 2 -> 45000L;
            case 3 -> 40000L;
            default -> 50000L;
        };
    }

    @Override
    public void cleanup(Player player) {
        HulkState state = activeStates.remove(player.getUniqueId());
        if (state != null) {
            restoreFlightState(player, state);
            plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
            plugin.getServer().getScheduler().cancelTask(state.hoverTaskId);
            plugin.getServer().getScheduler().cancelTask(state.circleTaskId);
        }
    }

    private void restoreFlightState(Player player, HulkState state) {
        player.setGravity(state.hadGravity);
    }

    private void grantNoFallDamage(Player player) {
        long until = System.currentTimeMillis() + FALL_IMMUNITY_MS;
        noFallUntil.put(player.getUniqueId(), until);
        player.setFallDistance(0);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    noFallUntil.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                Long expiry = noFallUntil.get(player.getUniqueId());
                if (expiry == null) {
                    noFallUntil.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                player.setFallDistance(0);
                if (System.currentTimeMillis() >= expiry && player.isOnGround()) {
                    noFallUntil.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                && event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Long expiry = noFallUntil.get(player.getUniqueId());
        if (expiry == null) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0);
        player.setFallDistance(0);
        if (System.currentTimeMillis() >= expiry && player.isOnGround()) {
            noFallUntil.remove(player.getUniqueId());
        }
    }

    private static class HulkState {
        final Location launchOrigin;
        final int range;
        final int level;
        final int chooseDuration;
        final double hoverY;
        final long createdAtMs;
        final long expiresAtMs;
        final boolean hadGravity;
        int expiryTaskId;
        int hoverTaskId;
        int circleTaskId;

        HulkState(Location launchOrigin,
                  int range,
                  int level,
                  int chooseDuration,
                  double hoverY,
                  long createdAtMs,
                  long expiresAtMs,
                  boolean hadGravity) {
            this.launchOrigin = launchOrigin;
            this.range = range;
            this.level = level;
            this.chooseDuration = chooseDuration;
            this.hoverY = hoverY;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.hadGravity = hadGravity;
        }
    }
}
