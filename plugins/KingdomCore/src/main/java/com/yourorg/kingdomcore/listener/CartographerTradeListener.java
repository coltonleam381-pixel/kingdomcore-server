package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.CartographerTradeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class CartographerTradeListener implements Listener {
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int glassPanes;

    public CartographerTradeListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean(
                "villager-trades.cartographer.glass-pane-for-emerald.enabled", true);
        this.glassPanes = Math.max(1, plugin.getConfig().getInt(
                "villager-trades.cartographer.glass-pane-for-emerald.glass-panes", 5));
    }

    public void patchAllLoadedCartographers() {
        if (!enabled) {
            return;
        }
        int patched = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (CartographerTradeUtil.applyGlassPaneTrade(villager, glassPanes)) {
                    patched++;
                }
            }
        }
        if (patched > 0) {
            plugin.getLogger().info("Cartographer trades patched on " + patched + " villager(s).");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Villager villager) {
                CartographerTradeUtil.applyGlassPaneTrade(villager, glassPanes);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (!enabled) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () ->
                CartographerTradeUtil.applyGlassPaneTrade(event.getEntity(), glassPanes));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled) {
            return;
        }
        if (event.getRightClicked() instanceof Villager villager) {
            CartographerTradeUtil.applyGlassPaneTrade(villager, glassPanes);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Villager villager) {
            CartographerTradeUtil.applyGlassPaneTrade(villager, glassPanes);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (!enabled) {
            return;
        }
        MerchantRecipe recipe = event.getRecipe();
        if (CartographerTradeUtil.isGlassPaneForEmeraldTrade(recipe)
                && recipe.getIngredients().get(0).getAmount() != glassPanes) {
            event.setRecipe(CartographerTradeUtil.copyWithGlassPaneCost(recipe, glassPanes));
        }
    }
}
