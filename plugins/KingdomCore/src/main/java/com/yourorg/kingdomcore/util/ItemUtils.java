package com.yourorg.kingdomcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ItemUtils {
    private ItemUtils() {
    }

    public static String getPdcString(ItemStack stack, NamespacedKey key) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static void setPdcString(ItemStack stack, NamespacedKey key, String value) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
    }
}
