package com.yourorg.kingdomcore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PinMenuHolder implements InventoryHolder {

    public enum Mode {
        REGISTER,
        LOGIN
    }

    private final Mode mode;
    private Inventory inventory;

    public PinMenuHolder(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
