package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.ParticleBroadcast;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Protector ability - dome barrier around activator.
 * L3 grants temporary immunity against ability true-damage while active.
 */
public class ProtectorAbility implements AbilityHandler, Listener, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, BarrierState> activeBarriers = new HashMap<>();
    private static final Map<UUID, Long> level3ImmuneUntilMs = new HashMap<>();
    
    private static class BarrierState {
        final UUID ownerId;
        final Location center;
        final double radius;
        final int level;
        final int taskId;
        final long expiresAtMs;
        final Map<UUID, Long> nextDamageAtByTarget = new HashMap<>();
        
        BarrierState(UUID ownerId, Location center, double radius, int level, int taskId, long expiresAtMs) {
            this.ownerId = ownerId;
            this.center = center.clone();
            this.radius = radius;
            this.level = level;
            this.taskId = taskId;
            this.expiresAtMs = expiresAtMs;
        }
    }
    
    public ProtectorAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
        
        // Register as listener for movement/projectile blocking
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    @Override
    public String getAbilityId() {
        return "protector";
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
        
        // Clean up existing barrier
        cleanup(player);
        
        // Get parameters based on level
        int durationSeconds;
        double radius;
        
        switch (level) {
            case 1:
                durationSeconds = 4;
                radius = 3.0;
                break;
            case 2:
                durationSeconds = 7;
                radius = 3.0;
                break;
            case 3:
                durationSeconds = 10;
                radius = 4.0;
                break;
            default:
                return false;
        }
        
        Location center = player.getLocation().clone();
        int durationTicks = durationSeconds * 20;
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        
        // Start barrier effect
        final int[] ticksElapsed = {0};
        
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            BarrierState state = activeBarriers.get(player.getUniqueId());
            if (state == null) {
                return;
            }
            if (!player.isOnline() || player.isDead()) {
                cleanup(player);
                return;
            }

            Location fixedCenter = state.center;

            // Draw a clearly visible shield sphere with evenly spaced points.
            if (ticksElapsed[0] % 4 == 0) {
                drawBarrierDome(fixedCenter, radius);
            }
            // No entities should be able to stay inside; damage remains level-gated.
            repelIntruders(player, state, System.currentTimeMillis());

            if (ticksElapsed[0] % 3 == 0) {
                clearProjectiles(fixedCenter, radius);
            }

            ticksElapsed[0]++;
            if (ticksElapsed[0] >= durationTicks) {
                cleanup(player);
            }
        }, 0L, 1L);
        
        activeBarriers.put(player.getUniqueId(), new BarrierState(player.getUniqueId(), center, radius, level, taskId, expiresAt));
        BarrierState state = activeBarriers.get(player.getUniqueId());
        if (state != null) {
            repelIntruders(player, state, System.currentTimeMillis());
        }
        if (level >= 3) {
            level3ImmuneUntilMs.put(player.getUniqueId(), expiresAt);
            // Requested: instant heal from top of shield to owner.
            playTopHealDrop(center, radius, player);
        }
        
        // Sound feedback
        player.getWorld().playSound(center, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        return true;
    }
    
    private void drawBarrierDome(Location center, double radius) {
        // Full sphere with visible "line" structure: latitude rings + vertical meridians.
        final int rings = 14;
        final int pointsPerRing = 22;
        final int meridians = 16;

        // Latitude ring lines
        for (int ring = 0; ring <= rings; ring++) {
            double theta = Math.PI * ring / rings; // 0..pi
            double y = radius * Math.cos(theta);
            double ringRadius = radius * Math.sin(theta);
            for (int p = 0; p < pointsPerRing; p++) {
                double phi = (2.0 * Math.PI * p / pointsPerRing);
                double x = ringRadius * Math.cos(phi);
                double z = ringRadius * Math.sin(phi);
                Location particleLoc = center.clone().add(x, y, z);
                ParticleBroadcast.particle(particleLoc.getWorld(), particleLoc, Particle.END_ROD, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Vertical meridian lines
        for (int m = 0; m < meridians; m++) {
            double phi = (2.0 * Math.PI * m / meridians);
            for (int ring = 1; ring < rings; ring++) {
                double theta = Math.PI * ring / rings;
                double y = radius * Math.cos(theta);
                double ringRadius = radius * Math.sin(theta);
                double x = ringRadius * Math.cos(phi);
                double z = ringRadius * Math.sin(phi);
                Location particleLoc = center.clone().add(x, y, z);
                ParticleBroadcast.particle(particleLoc.getWorld(), particleLoc, Particle.END_ROD, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void repelIntruders(Player owner, BarrierState state, long nowMs) {
        Location center = state.center;
        double radius = state.radius;
        int level = state.level;
        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, radius + 1.5, radius + 1.5, radius + 1.5)) {
            if (entity.equals(owner)) {
                continue;
            }

            double dist = entity.getLocation().distance(center);
            if (dist >= radius - 0.15) {
                continue;
            }

            Vector fromCenter = entity.getLocation().toVector().subtract(center.toVector());
            if (fromCenter.lengthSquared() < 0.0001) {
                fromCenter = new Vector(1, 0, 0);
            }
            Vector push = fromCenter.normalize().multiply(0.85);
            push.setY(0.35);
            entity.setVelocity(push);

            if (level >= 3) {
                UUID targetId = entity.getUniqueId();
                long nextAllowed = state.nextDamageAtByTarget.getOrDefault(targetId, 0L);
                if (nowMs >= nextAllowed) {
                    if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                        kcPlugin.getDamageService().applyTrueDamage(entity, owner, 1.0, center, 0.25);
                    }
                    state.nextDamageAtByTarget.put(targetId, nowMs + 2000L);
                }
            }
        }
    }

    private void clearProjectiles(Location center, double radius) {
        for (Projectile projectile : center.getWorld().getEntitiesByClass(Projectile.class)) {
            if (projectile.getLocation().distanceSquared(center) <= (radius * radius)) {
                projectile.remove();
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is trying to cross any barrier
        for (Map.Entry<UUID, BarrierState> entry : activeBarriers.entrySet()) {
            UUID ownerId = entry.getKey();
            BarrierState barrier = entry.getValue();
            
            // Skip if this is the barrier owner
            if (player.getUniqueId().equals(ownerId)) {
                continue;
            }
            
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) {
                continue;
            }
            Location center = barrier.center;
            double distFrom = from.distance(center);
            double distTo = to.distance(center);

            // Only block entering the dome — always allow leaving so knockback/abilities cannot trap players.
            boolean wasOutside = distFrom >= barrier.radius;
            boolean wouldBeInside = distTo < barrier.radius;

            if (wasOutside && wouldBeInside) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Location projectileLoc = projectile.getLocation();
        
        // Check if projectile crossed any barrier
        for (Map.Entry<UUID, BarrierState> entry : activeBarriers.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) {
                continue;
            }
            double distance = projectileLoc.distance(entry.getValue().center);
            
            // If projectile is near barrier edge, cancel it
            if (distance <= entry.getValue().radius + 0.3) {
                projectile.remove();
                event.setCancelled(true);
                return;
            }
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
        BarrierState state = activeBarriers.remove(player.getUniqueId());
        if (state != null) {
            Bukkit.getScheduler().cancelTask(state.taskId);
        }
        level3ImmuneUntilMs.remove(player.getUniqueId());
    }

    public static boolean isAbilityImmune(Player player) {
        Long until = level3ImmuneUntilMs.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    private void playTopHealDrop(Location center, double radius, Player player) {
        Location top = center.clone().add(0, radius, 0);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline() || t++ >= 10) {
                    if (player.isOnline()) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.INSTANT_HEALTH, 1, 0, false, false
                        ));
                        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.35f);
                    }
                    cancel();
                    return;
                }
                double progress = t / 10.0;
                Location to = player.getLocation().clone().add(0, 1.0, 0);
                Location pos = top.clone().add(to.toVector().subtract(top.toVector()).multiply(progress));
                pos.getWorld().spawnParticle(Particle.HEART, pos, 3, 0.08, 0.08, 0.08, 0.0);
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        BarrierState state = activeBarriers.get(playerId);
        if (state == null) {
            return null;
        }
        long leftMs = Math.max(0L, state.expiresAtMs - nowMs);
        long sec = (leftMs + 999L) / 1000L;
        return "§bProtector §8| §e" + sec + "s";
    }
}
