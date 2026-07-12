package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.abilities.BerserkAbility;
import com.yourorg.kingdomcore.abilities.ProtocolAbility;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Handles ability-specific damage modifications:
 * - Protocol passive death prevention
 * - Berserk forced critical hits
 */
public class AbilityDamageListener implements Listener {
    private final HeartService heartService;
    private final ProtocolAbility protocolAbility;
    private final BerserkAbility berserkAbility;
    
    public AbilityDamageListener(HeartService heartService,
                                ProtocolAbility protocolAbility,
                                BerserkAbility berserkAbility) {
        this.heartService = heartService;
        this.protocolAbility = protocolAbility;
        this.berserkAbility = berserkAbility;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtocolCheck(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (victim.hasMetadata(RailgunBowListener.RAILGUN_KILL_META)) {
            return;
        }
        
        // Check if damage would be fatal
        double finalHealth = victim.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return; // Not fatal
        }
        
        // Check if player has Protocol ability
        var state = heartService.getOrCreateState(victim.getUniqueId(), victim.getName());
        if (state == null || !"protocol".equals(state.getAbilityId())) {
            return;
        }
        
        // Get last attacker for wolf summoning
        LivingEntity lastAttacker = null;
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            if (damageByEntity.getDamager() instanceof LivingEntity) {
                lastAttacker = (LivingEntity) damageByEntity.getDamager();
            }
        }
        
        // Try to trigger Protocol
        boolean triggered = protocolAbility.tryTrigger(victim, state.getAbilityLevel(), lastAttacker);
        if (triggered) {
            // Cancel the death - Protocol saved them
            event.setDamage(0);
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBerserkCrit(EntityDamageByEntityEvent event) {
        // Check if attacker is a player with Berserk active
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        
        // Check if Berserk should force a crit
        if (berserkAbility.shouldForceCrit(attacker)) {
            // Do not stack into "double crit". Only force crit when attack is not already a vanilla crit.
            if (!isLikelyVanillaCrit(attacker)) {
                event.setDamage(event.getDamage() * 1.5);
            }
        }
    }

    private boolean isLikelyVanillaCrit(Player attacker) {
        return attacker.getFallDistance() > 0.0F
                && !attacker.isOnGround()
                && !attacker.isClimbing()
                && !attacker.isInWater()
                && !attacker.isInsideVehicle()
                && !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                && !attacker.isSprinting();
    }
}
