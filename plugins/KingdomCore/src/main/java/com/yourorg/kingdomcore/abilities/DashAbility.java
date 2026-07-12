package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dash ability - forward leap (not teleport).
 */
public class DashAbility implements AbilityHandler, Listener {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, Long> noFallUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingLandingSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dashLeftGround = new ConcurrentHashMap<>();
    
    public DashAbility(org.bukkit.plugin.Plugin plugin, WorldGuardHook worldGuardHook, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @Override
    public String getAbilityId() {
        return "dash";
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
        
        // Leap strength based on level
        double horizontal = switch (level) {
            case 1 -> 1.05;
            case 2 -> 1.35;
            case 3 -> 1.65;
            default -> 1.05;
        };

        double verticalBoost = switch (level) {
            case 1 -> 0.28;
            case 2 -> 0.34;
            case 3 -> 0.40;
            default -> 0.28;
        };

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector leap = direction.multiply(horizontal);
        leap.setY(leap.getY() + verticalBoost);
        noFallUntil.put(player.getUniqueId(), System.currentTimeMillis() + 2500L);
        player.setFallDistance(0);
        player.setVelocity(leap);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.55f);
        if (level >= 3) {
            pendingLandingSpeed.put(player.getUniqueId(), level);
            dashLeftGround.put(player.getUniqueId(), false);
        }
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
        // No state to clean
        noFallUntil.remove(player.getUniqueId());
        pendingLandingSpeed.remove(player.getUniqueId());
        dashLeftGround.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDashFallDamage(EntityDamageEvent event) {
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

        Integer level = pendingLandingSpeed.remove(player.getUniqueId());
        if (level != null && level >= 3) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 40, 1, false, false));
        }
        dashLeftGround.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDashMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Integer level = pendingLandingSpeed.get(player.getUniqueId());
        if (level == null || level < 3) {
            return;
        }
        boolean leftGround = dashLeftGround.getOrDefault(player.getUniqueId(), false);
        if (!player.isOnGround()) {
            dashLeftGround.put(player.getUniqueId(), true);
            return;
        }
        if (leftGround) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 40, 1, false, false));
            pendingLandingSpeed.remove(player.getUniqueId());
            dashLeftGround.remove(player.getUniqueId());
        }
    }
}
