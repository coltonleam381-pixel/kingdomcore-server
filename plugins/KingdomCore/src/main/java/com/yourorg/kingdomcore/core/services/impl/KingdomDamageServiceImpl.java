package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.abilities.ProtectorAbility;
import com.yourorg.kingdomcore.abilities.ProtocolAbility;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.KingdomDamageService;
import com.yourorg.kingdomcore.service.AfkService;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;

public class KingdomDamageServiceImpl implements KingdomDamageService {
    
    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final CombatTagService combatTagService;
    private final PhaseWalkService phaseWalkService;
    private final HeartService heartService;
    private final ProtocolAbility protocolAbility;

    public KingdomDamageServiceImpl(JavaPlugin plugin, AfkService afkService, CombatTagService combatTagService, 
                                    PhaseWalkService phaseWalkService, HeartService heartService, 
                                    ProtocolAbility protocolAbility) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.combatTagService = combatTagService;
        this.phaseWalkService = phaseWalkService;
        this.heartService = heartService;
        this.protocolAbility = protocolAbility;
    }

    @Override
    public void applyTrueDamage(LivingEntity target, Player attacker, double damage, Location source) {
        applyTrueDamage(target, attacker, damage, source, 0.0);
    }

    @Override
    public void applyTrueDamage(LivingEntity target, Player attacker, double damage, Location source, double knockbackStrength) {
        if (target == null || target.isDead()) {
            return;
        }

        if (target instanceof Player playerTarget) {
            // Check AFK
            if (afkService != null && afkService.isAfk(playerTarget)) {
                return;
            }
            if (phaseWalkService != null && phaseWalkService.isInPhase(playerTarget)) {
                return;
            }
            // Check Protector
            if (ProtectorAbility.isAbilityImmune(playerTarget)) {
                return;
            }
        }

        // Fire synthetic event for other plugins to track
        if (attacker != null) {
            org.bukkit.event.entity.EntityDamageByEntityEvent syntheticEvent = new org.bukkit.event.entity.EntityDamageByEntityEvent(
                    attacker, target, org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM, damage);
            plugin.getServer().getPluginManager().callEvent(syntheticEvent);
            if (syntheticEvent.isCancelled()) {
                return;
            }
            damage = syntheticEvent.getDamage();
            
            // Tag combat (After event check, so cancelled events don't tag)
            if (target instanceof Player playerTarget && combatTagService != null) {
                combatTagService.tagPlayer(attacker, playerTarget);
                combatTagService.tagPlayer(playerTarget, attacker);
            }
        }

        // Apply knockback & animation
        triggerHurtAnimation(target, source);
        applyKnockback(target, source, knockbackStrength);

        double currentHealth = target.getHealth();
        double newHealth = Math.max(0, currentHealth - damage);

        if (newHealth <= 0 && target instanceof Player playerTarget) {
            if (tryActivateTotem(playerTarget)) {
                return;
            }
            if (attacker != null) {
                playerTarget.setLastDamageCause(new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        attacker, playerTarget,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM,
                        damage));
            }
            if (protocolAbility != null) {
                int level = getAbilityLevel(playerTarget);
                if (protocolAbility.tryTrigger(playerTarget, level, attacker)) {
                    return;
                }
            }
        }

        setHealthBypassingRules(target, newHealth);
    }

    @Override
    public void setHealthBypassingRules(LivingEntity target, double newHealth) {
        if (target != null && !target.isDead()) {
            target.setHealth(Math.max(0, newHealth));
        }
    }

    private void triggerHurtAnimation(LivingEntity entity, Location source) {
        entity.setNoDamageTicks(0);
        entity.damage(0.0);
    }

    private void applyKnockback(LivingEntity entity, Location source, double strength) {
        if (strength <= 0 || source == null) return;
        Location targetLoc = entity.getLocation();
        Vector direction = targetLoc.toVector().subtract(source.toVector()).normalize();
        direction.setY(0.3);
        direction.multiply(strength);
        entity.setVelocity(entity.getVelocity().add(direction));
    }

    private boolean tryActivateTotem(Player player) {
        EquipmentSlot slot = getTotemSlot(player);
        if (slot == null) {
            return false;
        }

        EntityResurrectEvent resurrectEvent = new EntityResurrectEvent(player);
        plugin.getServer().getPluginManager().callEvent(resurrectEvent);
        if (resurrectEvent.isCancelled()) {
            return false;
        }

        consumeTotem(player, slot);
        player.setHealth(Math.min(player.getMaxHealth(), 1.0));
        player.setFireTicks(0);
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 8.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, true, true));
        Location fx = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, fx, 40, 0.5, 1.0, 0.5, 0.15);
        player.playSound(fx, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        return true;
    }

    private static EquipmentSlot getTotemSlot(Player player) {
        if (player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return EquipmentSlot.OFF_HAND;
        }
        if (player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) {
            return EquipmentSlot.HAND;
        }
        return null;
    }

    private static void consumeTotem(Player player, EquipmentSlot slot) {
        ItemStack totem = slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (totem.getAmount() <= 1) {
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        totem.setAmount(totem.getAmount() - 1);
    }

    private int getAbilityLevel(Player player) {
        // Find protocol level
        com.yourorg.kingdomcore.api.PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
        if ("protocol".equals(state.getAbilityId())) {
            return state.getAbilityLevel();
        }
        return 0;
    }
}
