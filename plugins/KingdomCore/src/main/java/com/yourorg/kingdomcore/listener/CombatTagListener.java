package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

/**
 * Listener for combat tagging players on PvP damage.
 */
public class CombatTagListener implements Listener {
    private final CombatTagService combatTagService;
    private final CombatRules combatRules;
    
    public CombatTagListener(CombatTagService combatTagService, CombatRules combatRules) {
        this.combatTagService = combatTagService;
        this.combatRules = combatRules;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only care about players being damaged
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        
        // Don't tag if attacker and victim are the same
        if (attacker.equals(victim)) {
            return;
        }

        if (combatRules.shouldEnforce(victim)) {
            combatTagService.tagPlayer(victim, attacker);
        }
        if (combatRules.shouldEnforce(attacker)) {
            combatTagService.tagPlayer(attacker, victim);
        }
    }
    
    /**
     * Resolve the actual player attacker from various damage sources.
     */
    private Player resolveAttacker(Entity damager) {
        // Direct player damage
        if (damager instanceof Player) {
            return (Player) damager;
        }
        
        // Projectile damage
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        
        // Tameable entity damage (wolves, etc - for Protocol ability)
        if (damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player) {
                return (Player) tameable.getOwner();
            }
        }

        // Trident combo lightning (Poseidon unique item)
        if (damager instanceof LightningStrike lightning && lightning.hasMetadata("kc_trident_lightning_owner")) {
            try {
                UUID ownerId = UUID.fromString(lightning.getMetadata("kc_trident_lightning_owner").get(0).asString());
                return org.bukkit.Bukkit.getPlayer(ownerId);
            } catch (Exception ignored) {
                return null;
            }
        }
        
        // Other potential sources (future-proofing for summoned entities)
        if (damager instanceof LivingEntity living) {
            // Could check custom metadata for owner attribution if needed
        }
        
        return null;
    }
}
