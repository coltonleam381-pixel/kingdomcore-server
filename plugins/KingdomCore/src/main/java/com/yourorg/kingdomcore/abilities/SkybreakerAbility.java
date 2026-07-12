package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skybreaker ability: short rise then slam (about 0.7s total).
 */
public class SkybreakerAbility implements AbilityHandler, Listener {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, Integer> activeSlamTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFallUntil = new ConcurrentHashMap<>();

    public SkybreakerAbility(Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getAbilityId() {
        return "skybreaker";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        double radius = switch (level) {
            case 1 -> 3.0;
            case 2 -> 3.5;
            case 3 -> 5.0;
            default -> 3.0;
        };

        double damage = switch (level) {
            case 1 -> 4.0;  // 2 hearts
            case 2 -> 6.0;  // 3 hearts
            case 3 -> 8.0;  // 4 hearts
            default -> 4.0;
        };

        // Requested flow: go up then slam, total around 0.7 seconds.
        grantNoFall(player, 3500L);
        final double startY = player.getLocation().getY();
        int taskId = new BukkitRunnable() {
            int ticks = 0;
            boolean dive = false;
            final int riseTicks = 7;
            final int maxTotalTicks = 14; // 0.7s total: 0.35s up + 0.35s down

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    activeSlamTasks.remove(player.getUniqueId());
                    return;
                }
                if (!dive) {
                    // Rise phase, respecting roofs naturally.
                    double risen = player.getLocation().getY() - startY;
                    if (risen < 5.0 && ticks <= riseTicks) {
                        player.setVelocity(new Vector(0, 0.62, 0));
                        return;
                    }
                    dive = true;
                }
                player.setVelocity(new Vector(0, -1.35, 0));

                if (player.isOnGround() || ticks >= maxTotalTicks) {
                    Location at = player.getLocation().clone();
                    performSlam(player, at, radius, damage, level);
                    player.setFallDistance(0);
                    cancel();
                    activeSlamTasks.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 0, 1).getTaskId();

        activeSlamTasks.put(player.getUniqueId(), taskId);

        return true;
    }

    private void performSlam(Player player, Location center, double radius, double damage, int level) {
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 10, radius / 2.0, 0.5, radius / 2.0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, radius, 4.0, radius)) {
            if (entity.equals(player)) {
                continue;
            }

            double dist = entity.getLocation().distance(center);
            if (dist > radius) {
                continue;
            }

            // Damage + knockback
            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(entity, player, damage, center, 0.6);
            }

            // Requested effects on impacted targets by level.
            if (level >= 2) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, true)); // 1s
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, true)); // 1s
            }
            if (level >= 3) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, true)); // 1.5s
                entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, true)); // 2s
            }
        }

        // Caster self-buffs by level.
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, true)); // Speed I, 2s
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, level >= 3 ? 1 : 0, false, true)); // Regen I/II, 2s
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, true)); // Strength I, 2s
        }

        // Crit ring particles for all levels.
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            Location p = new Location(center.getWorld(), x, center.getY() + 0.15, z);
            center.getWorld().spawnParticle(Particle.CRIT, p, 3, 0.08, 0.08, 0.08, 0.01);
        }
    }

    @Override
    public void cleanup(Player player) {
        Integer taskId = activeSlamTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        player.setFallDistance(0);
    }

    private void grantNoFall(Player player, long ms) {
        noFallUntil.put(player.getUniqueId(), System.currentTimeMillis() + ms);
        player.setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Long until = noFallUntil.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() <= until) {
            event.setCancelled(true);
            event.setDamage(0.0);
            player.setFallDistance(0);
        } else {
            noFallUntil.remove(player.getUniqueId());
        }
    }
}
