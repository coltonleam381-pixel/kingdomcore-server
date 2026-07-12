package com.yourorg.kingdomcore.integrations;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class ItemsAdderHook {
    private final Logger logger;
    private final boolean available;

    public ItemsAdderHook(Logger logger) {
        this.logger = logger;
        this.available = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getCustomItemId(ItemStack stack) {
        if (!available || stack == null) {
            return null;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method byItemStack = customStackClass.getMethod("byItemStack", ItemStack.class);
            Object customStack = byItemStack.invoke(null, stack);
            if (customStack == null) {
                return null;
            }
            Method getId;
            try {
                getId = customStackClass.getMethod("getNamespacedID");
            } catch (NoSuchMethodException ex) {
                getId = customStackClass.getMethod("getNamespacedId");
            }
            Object value = getId.invoke(customStack);
            return value != null ? value.toString() : null;
        } catch (Throwable ex) {
            return null;
        }
    }

    public ItemStack createCustomItem(String id, int amount) {
        if (!available) {
            return null;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, id);
            if (customStack == null) {
                return null;
            }
            Method getItemStack = customStackClass.getMethod("getItemStack");
            ItemStack stack = (ItemStack) getItemStack.invoke(customStack);
            if (stack == null) {
                return null;
            }
            stack.setAmount(amount);
            return stack;
        } catch (Throwable ex) {
            logger.warning("ItemsAdder hook failed for id: " + id);
            return null;
        }
    }

    /**
     * Sends the ItemsAdder resource pack after PIN login so pack lock does not fight auth lock.
     */
    public void applyResourcePack(Player player) {
        if (!available || player == null || !player.isOnline()) {
            return;
        }
        try {
            Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Method apply = itemsAdderClass.getMethod("applyResourcepack", Player.class);
            apply.invoke(null, player);
        } catch (Throwable ex) {
            logger.warning("Failed to apply ItemsAdder resource pack for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void applyResourcePackDelayed(Plugin plugin, Player player, long delayTicks) {
        if (!available || player == null || plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyResourcePack(player), delayTicks);
    }
}
