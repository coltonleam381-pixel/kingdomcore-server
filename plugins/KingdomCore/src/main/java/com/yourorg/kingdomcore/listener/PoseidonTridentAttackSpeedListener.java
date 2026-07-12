package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

/**
 * Makes the Poseidon trident hit as fast as a sword by adjusting the player's ATTACK_SPEED
 * while holding the trident.
 */
public class PoseidonTridentAttackSpeedListener implements Listener {

    private static final UUID POSEIDON_TRIDENT_SPEED_UUID = UUID.fromString("b2b9b6cb-5e7b-4a6a-9a6f-0d4b2d1c1a7a");
    private static final String POSEIDON_TRIDENT_SPEED_NAME = "poseidon_trident_attack_speed";

    // Cache the computed delta (sword - trident) once at class load.
    private static final double SPEED_DELTA;
    private static final Operation SPEED_OPERATION;

    static {
        // Default item modifiers are based on weapon type.
        // We only support the common case where ATTACK_SPEED uses ADD_NUMBER.
        double delta = 0.0;
        Operation op = Operation.ADD_NUMBER;

        try {
            // Main-hand attacks use HAND slot for attribute modifiers.
            var swordMods = Material.DIAMOND_SWORD.getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_SPEED);
            var tridentMods = Material.TRIDENT.getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_SPEED);

            // If the API changes and there are multiple modifiers, we sum all amounts with the same operation.
            double swordAmount = 0.0;
            double tridentAmount = 0.0;
            Operation foundOp = null;

            if (swordMods != null && !swordMods.isEmpty()) {
                for (AttributeModifier mod : swordMods) {
                    foundOp = mod.getOperation();
                    if (foundOp != null) {
                        swordAmount += mod.getAmount();
                    }
                }
            }
            if (tridentMods != null && !tridentMods.isEmpty()) {
                for (AttributeModifier mod : tridentMods) {
                    if (foundOp == null) {
                        foundOp = mod.getOperation();
                    }
                    tridentAmount += mod.getAmount();
                }
            }

            delta = swordAmount - tridentAmount;
            if (foundOp != null) {
                op = foundOp;
            }
        } catch (Throwable ignored) {
            // Fallback: known approximate vanilla delta (sword 1.6 - trident 1.1 = +0.5).
            delta = 0.5;
            op = Operation.ADD_NUMBER;
        }

        SPEED_DELTA = delta;
        SPEED_OPERATION = op;
    }

    private final JavaPlugin plugin;
    private final ItemIdentityService itemIdentityService;

    public PoseidonTridentAttackSpeedListener(JavaPlugin plugin, ItemIdentityService itemIdentityService) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        updateFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        removeFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        updateFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // Wait until the swap actually happened.
        plugin.getServer().getScheduler().runTask(plugin, () -> updateFor(event.getPlayer()));
    }

    private void updateFor(Player player) {
        if (player == null || !player.isValid() || player.isDead()) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        boolean holdingTrident = itemIdentityService.isTridentItem(main);
        if (holdingTrident) {
            apply(player);
        } else {
            removeFrom(player);
        }
    }

    private void apply(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        // Prevent stacking if events fire multiple times.
        attackSpeed.removeModifier(POSEIDON_TRIDENT_SPEED_UUID);

        if (SPEED_DELTA == 0.0D) {
            return;
        }
        attackSpeed.addModifier(new AttributeModifier(
                POSEIDON_TRIDENT_SPEED_UUID,
                POSEIDON_TRIDENT_SPEED_NAME,
                SPEED_DELTA,
                SPEED_OPERATION
        ));
    }

    private void removeFrom(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        attackSpeed.removeModifier(POSEIDON_TRIDENT_SPEED_UUID);
    }
}

