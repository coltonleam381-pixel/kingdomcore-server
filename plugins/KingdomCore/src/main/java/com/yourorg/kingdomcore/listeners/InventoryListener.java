package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.gui.MenuFactory;
import com.yourorg.kingdomcore.gui.MenuHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class InventoryListener implements Listener {
    private final MenuFactory menuFactory;

    public InventoryListener(MenuFactory menuFactory) {
        this.menuFactory = menuFactory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) {
            return;
        }
        menuFactory.handleMenuClick((org.bukkit.entity.Player) event.getWhoClicked(), event.getCurrentItem(), holder);
    }
}
