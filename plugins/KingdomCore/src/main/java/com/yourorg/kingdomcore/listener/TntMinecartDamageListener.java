package com.yourorg.kingdomcore.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Scales explosion damage caused by TNT minecarts (1.0 = vanilla, 0.9 = -10%).
 */
public final class TntMinecartDamageListener implements Listener {
    private final boolean enabled;
    private final double damageMultiplier;

    public TntMinecartDamageListener(JavaPlugin plugin) {
        this.enabled = plugin.getConfig().getBoolean("tnt-minecart.enabled", true);
        this.damageMultiplier = plugin.getConfig().getDouble("tnt-minecart.damage-multiplier", 0.7D);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!enabled || damageMultiplier == 1.0D) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        if (!isTntMinecartExplosion(event)) {
            return;
        }
        event.setDamage(event.getDamage() * damageMultiplier);
    }

    private boolean isTntMinecartExplosion(EntityDamageEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing != null && causing.getType() == EntityType.TNT_MINECART) {
            return true;
        }
        Entity direct = event.getDamageSource().getDirectEntity();
        return direct != null && direct.getType() == EntityType.TNT_MINECART;
    }
}
