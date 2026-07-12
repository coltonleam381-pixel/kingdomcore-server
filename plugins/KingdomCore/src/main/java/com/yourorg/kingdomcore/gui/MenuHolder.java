package com.yourorg.kingdomcore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuHolder implements InventoryHolder {
    private final MenuType type;
    private final String abilityId;

    public MenuHolder(MenuType type, String abilityId) {
        this.type = type;
        this.abilityId = abilityId;
    }

    public MenuType getType() {
        return type;
    }

    public String getAbilityId() {
        return abilityId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
