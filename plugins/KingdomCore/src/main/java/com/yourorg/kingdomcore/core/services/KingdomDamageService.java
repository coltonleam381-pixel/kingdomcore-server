package com.yourorg.kingdomcore.core.services;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface KingdomDamageService {
    /**
     * Applies true damage, bypassing armor but respecting all plugin rules
     * (AFK, Phase Walk, Combat Tag, Permadeath, Protocol, etc.)
     */
    void applyTrueDamage(LivingEntity target, Player attacker, double damage, Location source, double knockbackStrength);
    
    void applyTrueDamage(LivingEntity target, Player attacker, double damage, Location source);
    
    /**
     * Set health directly (only used internally or for non-damage operations)
     */
    void setHealthBypassingRules(LivingEntity target, double newHealth);
}
