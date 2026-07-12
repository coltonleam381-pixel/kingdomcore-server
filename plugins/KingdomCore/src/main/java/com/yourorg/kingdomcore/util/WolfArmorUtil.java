package com.yourorg.kingdomcore.util;

import org.bukkit.Material;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WolfArmorUtil {
    private WolfArmorUtil() {
    }

    public static void equipBodyArmor(Wolf wolf) {
        EntityEquipment equipment = wolf.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItem(EquipmentSlot.BODY, new ItemStack(Material.WOLF_ARMOR));
    }
}
