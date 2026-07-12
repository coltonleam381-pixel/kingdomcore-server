package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.gui.MenuFactory;
import com.yourorg.kingdomcore.gui.MenuHolder;
import com.yourorg.kingdomcore.gui.MenuType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class InventoryCloseListener implements Listener {
    private final MenuFactory menuFactory;

    public InventoryCloseListener(MenuFactory menuFactory) {
        this.menuFactory = menuFactory;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.getType() == MenuType.REVIVE_CONFIRM) {
            menuFactory.clearRevivePending(event.getPlayer().getUniqueId());
        }
    }
}
