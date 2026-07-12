package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Protocol ability - passive death prevention triggering on fatal damage.
 * This is NOT activated by RMB - it's a passive interceptor.
 * The cooldown is managed separately when it triggers.
 */
public class ProtocolAbility implements AbilityHandler, AbilityHudProvider, Listener {
    private static final String ANGEL_WOLF_TAG = "kc_protocol_angel_wolf";
    private final Plugin plugin;
    private final CooldownService cooldownService;
    private final Map<UUID, List<Wolf>> activeWolves = new HashMap<>();
    private final Map<UUID, Long> passiveHudUntilMs = new HashMap<>();
    
    private static final long COOLDOWN_MS = 120000; // 120 seconds
    
    public ProtocolAbility(Plugin plugin, CooldownService cooldownService) {
        this.plugin = plugin;
        this.cooldownService = cooldownService;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }
    
    @Override
    public String getAbilityId() {
        return "protocol";
    }
    
    /**
     * Check if protocol can trigger for this player.
     * Call this from damage listener before applying fatal damage.
     * 
     * @param player Player taking fatal damage
     * @param level Current Protocol ability level
     * @param lastAttacker Last entity that damaged the player
     * @return true if protocol triggered and death was prevented
     */
    public boolean tryTrigger(Player player, int level, LivingEntity lastAttacker) {
        if (level == 0) {
            return false;
        }
        
        // Check cooldown
        long now = System.currentTimeMillis();
        if (!cooldownService.isReady(player.getUniqueId(), getAbilityId(), now)) {
            return false;
        }
        
        // Trigger Protocol!
        cooldownService.markUsed(player.getUniqueId(), getAbilityId(), now + COOLDOWN_MS);
        
        // Save player from death - set to low HP
        double lowHealth = 1.0 + (Math.random() * 1.0); // 0.5-1.0 hearts
        player.setHealth(lowHealth);
        
        // Clear all potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        // Apply buff effects based on level
        int duration;
        int amplifier;
        
        if (level == 1) {
            duration = 100; // 5 seconds
            amplifier = 0; // Level I
        } else if (level == 2) {
            duration = 140; // 7 seconds
            amplifier = 1; // Level II
        } else {
            duration = 160; // 8 seconds
            amplifier = 1; // Keep core buffs at level II
        }
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, amplifier, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier, false, false));
        int absorptionAmp = (level >= 3) ? 2 : amplifier; // L3 = Absorption III
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, absorptionAmp, false, false));
        if (level >= 2) {
            player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 30)); // 1.5s immunity window
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false)); // 2s regen I
        }
        
        // L3: Summon 2 wolves for 5 seconds.
        if (level >= 3) {
            summonProtocolWolves(player, lastAttacker);
        }
        
        // Visual/sound feedback
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        // Approximate "red vignette pulse" with short nausea + red particles.
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false));
        player.getWorld().spawnParticle(
                Particle.DUST,
                player.getEyeLocation(),
                26,
                0.25, 0.25, 0.25, 0.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 25, 25), 1.5f)
        );
        
        return true;
    }
    
    private void summonProtocolWolves(Player owner, LivingEntity target) {
        // Clean up any existing wolves
        List<Wolf> existing = activeWolves.remove(owner.getUniqueId());
        if (existing != null) {
            existing.forEach(Wolf::remove);
        }
        
        List<Wolf> wolves = new ArrayList<>();
        Location spawnLoc = owner.getLocation();
        
        LivingEntity resolvedTarget = target;
        if (resolvedTarget == null) {
            resolvedTarget = owner.getWorld().getNearbyLivingEntities(owner.getLocation(), 20.0, 8.0, 20.0).stream()
                    .filter(entity -> !entity.equals(owner))
                    .findFirst()
                    .orElse(null);
        }

        for (int i = 0; i < 2; i++) {
            Wolf wolf = (Wolf) owner.getWorld().spawnEntity(
                spawnLoc.clone().add(i == 0 ? 1 : -1, 0, i == 0 ? 1 : -1),
                EntityType.WOLF
            );
            
            wolf.setOwner(owner);
            wolf.setTamed(true);
            wolf.setAdult();
            wolf.setAngry(resolvedTarget != null);
            
            // Set target
            wolf.setTarget(resolvedTarget);
            wolf.setCustomName("§e§lAngel");
            wolf.setCustomNameVisible(true);
            wolf.addScoreboardTag(ANGEL_WOLF_TAG);

            // Buff the wolf
            Objects.requireNonNull(wolf.getAttribute(Attribute.ARMOR))
                .setBaseValue(10.0); // Armor
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false)); // Speed II for 5s
            com.yourorg.kingdomcore.util.WolfArmorUtil.equipBodyArmor(wolf);
            
            wolves.add(wolf);
        }
        
        activeWolves.put(owner.getUniqueId(), wolves);
        
        // Remove wolves after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            wolves.forEach(Wolf::remove);
            activeWolves.remove(owner.getUniqueId());
        }, 100L);
    }
    
    /**
     * Get remaining cooldown in milliseconds.
     */
    public long getRemainingCooldown(UUID playerId) {
        return cooldownService.getRemainingMs(playerId, getAbilityId(), System.currentTimeMillis());
    }
    
    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long remaining = cooldownService.getRemainingMs(player.getUniqueId(), getAbilityId(), now);
        passiveHudUntilMs.put(player.getUniqueId(), now + 1500L);
        if (remaining > 0L) {
            player.sendActionBar("§bProtocol is passive §8| §c" + ((remaining + 999L) / 1000L) + "s");
        } else {
            player.sendActionBar("§bProtocol is passive §8| §aReady");
        }
        return false;
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        Long holdUntil = passiveHudUntilMs.get(playerId);
        if (holdUntil == null || nowMs > holdUntil) {
            return null;
        }
        long remaining = cooldownService.getRemainingMs(playerId, getAbilityId(), nowMs);
        if (remaining > 0L) {
            return "§bProtocol is passive §8| §c" + ((remaining + 999L) / 1000L) + "s";
        }
        return "§bProtocol is passive §8| §aReady";
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
        // Remove any active wolves on logout/death
        List<Wolf> wolves = activeWolves.remove(player.getUniqueId());
        if (wolves != null) {
            wolves.forEach(Wolf::remove);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAngelWolfHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Wolf wolf)) {
            return;
        }
        if (!wolf.getScoreboardTags().contains(ANGEL_WOLF_TAG)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // Do not allow angel wolf friendly-fire to owner.
        if (wolf.getOwner() instanceof Player owner && owner.getUniqueId().equals(target.getUniqueId())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        // Exactly 1 heart per hit each.
        event.setCancelled(true);
        event.setDamage(0.0);
        if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            Player pOwner = wolf.getOwner() instanceof Player ? (Player) wolf.getOwner() : null;
            kcPlugin.getDamageService().applyTrueDamage(target, pOwner, 2.0, wolf.getLocation(), 0.2);
        }
    }
}
