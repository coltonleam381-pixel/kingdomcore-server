package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.ParticleBroadcast;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meteor ability: Forward meteor ride with camera steering.
 * SPACE exits early without explosion.
 */
public class MeteorAbility implements AbilityHandler, CooldownOverrideAbility, AbilityHudProvider, Listener {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, MeteorState> activeRides = new ConcurrentHashMap<>();

    public MeteorAbility(Plugin plugin, WorldGuardHook worldGuardHook, CooldownService cooldownService, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getAbilityId() {
        return "meteor";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        if (activeRides.containsKey(player.getUniqueId())) {
            return false;
        }

        double speed = switch (level) {
            case 1 -> 8.1 / 20.0;  // 40% lower
            case 2 -> 9.45 / 20.0;
            case 3 -> 10.8 / 20.0;
            default -> 0.0;
        };

        int duration = switch (level) {
            case 1 -> 20; // 1 second
            case 2 -> 40; // 2 seconds
            case 3 -> 60; // 3 seconds
            default -> 0;
        };

        int explRadius = switch (level) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 6;
            default -> 0;
        };

        double explDamage = switch (level) {
            case 1 -> 4.0; // 2 hearts
            case 2 -> 5.0; // 2.5 hearts
            case 3 -> 6.0; // 3 hearts
            default -> 0.0;
        };

        int burnDuration = switch (level) {
            case 1 -> 40; // 2 seconds
            case 2 -> 60; // 3 seconds
            case 3 -> 80; // 4 seconds
            default -> 0;
        };

        MeteorState state = new MeteorState(speed, explRadius, explDamage, burnDuration, level);
        state.hadAllowFlight = player.getAllowFlight();
        activeRides.put(player.getUniqueId(), state);
        state.endsAtMs = System.currentTimeMillis() + (duration * 50L);
        player.setAllowFlight(true);

        int taskId = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= duration || !player.isOnline()) {
                    cancel();
                    finishRide(player, state, true);
                    return;
                }

                // Steer by camera direction
                Vector direction = player.getEyeLocation().getDirection();
                direction.normalize().multiply(speed);

                player.setVelocity(direction);

                Location trail = player.getLocation().clone().add(direction.clone().normalize().multiply(-0.6));
                spawnMeteorTrail(trail, level);
            }
        }.runTaskTimer(plugin, 0, 1).getTaskId();

        state.taskId = taskId;

        return true;
    }

    @Override
    public boolean onSpace(Player player) {
        MeteorState state = activeRides.get(player.getUniqueId());
        if (state != null) {
            plugin.getServer().getScheduler().cancelTask(state.taskId);
            finishRide(player, state, false);
            return true;
        }
        return false;
    }

    private void finishRide(Player player, MeteorState state, boolean explode) {
        activeRides.remove(player.getUniqueId());
        player.setAllowFlight(state.hadAllowFlight);
        cooldownService.markUsed(player.getUniqueId(), getAbilityId(),
                System.currentTimeMillis() + getCooldownMs(state.level));
        if (!explode) {
            return;
        }

        Location center = player.getLocation();
        spawnMeteorExplosion(center, state.explRadius, state.level);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);

        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, state.explRadius)) {
            if (entity.equals(player)) {
                continue; // Caster immune
            }

            double dist = entity.getLocation().distance(center);
            if (dist > state.explRadius) {
                continue;
            }

            // Explosion damage
            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(entity, player, state.explDamage, center, 0.4);
            }
            entity.setFireTicks(state.burnDuration);

            // Level 3: small knock-up
            if (state.level >= 3) {
                Vector knock = entity.getVelocity();
                knock.setY(0.5);
                entity.setVelocity(knock);
            }
        }
    }

    @Override
    public void cleanup(Player player) {
        MeteorState state = activeRides.remove(player.getUniqueId());
        if (state != null) {
            plugin.getServer().getScheduler().cancelTask(state.taskId);
            player.setAllowFlight(state.hadAllowFlight);
        }
    }

    @Override
    public long getCooldownMs(int level) {
        return switch (level) {
            case 1 -> 55000L;
            case 2 -> 50000L;
            case 3 -> 50000L;
            default -> 55000L;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (activeRides.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (activeRides.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (activeRides.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        MeteorState state = activeRides.get(playerId);
        if (state == null) {
            return null;
        }
        long remainingMs = Math.max(0L, state.endsAtMs - nowMs);
        long remainingSec = (remainingMs + 999L) / 1000L;
        return "§6☄ Meteor Flight §8| §e" + remainingSec + "s";
    }

    private void spawnMeteorTrail(Location center, int level) {
        if (center.getWorld() == null) {
            return;
        }
        var world = center.getWorld();

        int flameCount = switch (level) {
            case 1 -> 14;
            case 2 -> 18;
            case 3 -> 22;
            default -> 14;
        };

        ParticleBroadcast.particle(world, center, Particle.FLAME, flameCount, 0.35, 0.35, 0.35, 0.06);
        ParticleBroadcast.particle(world, center, Particle.SMOKE, 10, 0.28, 0.28, 0.28, 0.03);
        ParticleBroadcast.particle(world, center, Particle.LARGE_SMOKE, 4, 0.2, 0.2, 0.2, 0.01);
        ParticleBroadcast.particle(world, center, Particle.LAVA, 2, 0.15, 0.15, 0.15, 0.0);
        ParticleBroadcast.particle(world, center, Particle.SOUL_FIRE_FLAME, 6, 0.25, 0.25, 0.25, 0.04);

        if (level >= 2) {
            ParticleBroadcast.particle(world, center, Particle.FLAME, 8, 0.5, 0.5, 0.5, 0.08);
        }
        if (level >= 3) {
            ParticleBroadcast.particle(world, center, Particle.CAMPFIRE_SIGNAL_SMOKE, 2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void spawnMeteorExplosion(Location center, int radius, int level) {
        if (center.getWorld() == null) {
            return;
        }
        var world = center.getWorld();
        double spread = Math.max(1.0, radius / 2.0);

        ParticleBroadcast.particle(world, center, Particle.EXPLOSION, 4, spread * 0.35, 0.4, spread * 0.35, 0.0);
        ParticleBroadcast.particle(world, center, Particle.EXPLOSION_EMITTER, 1, 0.0, 0.0, 0.0, 0.0);
        ParticleBroadcast.particle(world, center, Particle.FLAME, 40 + level * 10, spread, 0.6, spread, 0.12);
        ParticleBroadcast.particle(world, center, Particle.SMOKE, 35 + level * 8, spread, 0.5, spread, 0.06);
        ParticleBroadcast.particle(world, center, Particle.LARGE_SMOKE, 12, spread * 0.8, 0.4, spread * 0.8, 0.02);
        ParticleBroadcast.particle(world, center, Particle.LAVA, 8, spread * 0.5, 0.2, spread * 0.5, 0.0);
        ParticleBroadcast.particle(world, center, Particle.SOUL_FIRE_FLAME, 20, spread * 0.7, 0.5, spread * 0.7, 0.08);
    }

    private static class MeteorState {
        final double speed;
        final int explRadius;
        final double explDamage;
        final int burnDuration;
        final int level;
        int taskId;
        long endsAtMs;
        boolean hadAllowFlight;

        MeteorState(double speed, int explRadius, double explDamage, int burnDuration, int level) {
            this.speed = speed;
            this.explRadius = explRadius;
            this.explDamage = explDamage;
            this.burnDuration = burnDuration;
            this.level = level;
        }
    }
}
