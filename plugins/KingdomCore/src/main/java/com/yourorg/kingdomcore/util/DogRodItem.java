package com.yourorg.kingdomcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class DogRodItem {
    public static final String DISPLAY_NAME = "100dog";
    public static final String WOLF_TAG = "kc_100dog_wolf";

    private DogRodItem() {
    }

    public static NamespacedKey rodKey(Plugin plugin) {
        return new NamespacedKey(plugin, "dog_rod");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) {
            return rod;
        }
        meta.displayName(Component.text(DISPLAY_NAME, NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(rodKey(plugin), PersistentDataType.BYTE, (byte) 1);
        if (meta instanceof Damageable damageable) {
            int max = Material.FISHING_ROD.getMaxDurability();
            damageable.setDamage(Math.max(0, max - 1));
        }
        rod.setItemMeta(meta);
        return rod;
    }

    public static boolean isDogRod(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(rodKey(plugin), PersistentDataType.BYTE);
    }

    public static ItemStack findInHands(org.bukkit.entity.Player player, Plugin plugin) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isDogRod(main, plugin)) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isDogRod(off, plugin)) {
            return off;
        }
        return null;
    }

    public static void consume(org.bukkit.entity.Player player, Plugin plugin) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isDogRod(main, plugin)) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isDogRod(off, plugin)) {
            player.getInventory().setItemInOffHand(null);
        }
    }
}
