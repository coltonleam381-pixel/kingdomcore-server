package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.ClericTradeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClericTradeListener implements Listener {
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int emeralds;
    private final int blazeRods;

    public ClericTradeListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean(
                "villager-trades.cleric.blaze-rod-for-emerald.enabled", true);
        this.emeralds = Math.max(1, plugin.getConfig().getInt(
                "villager-trades.cleric.blaze-rod-for-emerald.emeralds", 32));
        this.blazeRods = Math.max(1, plugin.getConfig().getInt(
                "villager-trades.cleric.blaze-rod-for-emerald.blaze-rods", 1));
    }

    public void patchAllLoadedClerics() {
        if (!enabled) {
            return;
        }
        int patched = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (ClericTradeUtil.applyBlazeRodTrade(villager, emeralds, blazeRods)) {
                    patched++;
                }
            }
        }
        if (patched > 0) {
            plugin.getLogger().info("Cleric blaze rod trade patched on " + patched + " villager(s).");
        }
    }

    private void patch(Villager villager) {
        if (enabled) {
            ClericTradeUtil.applyBlazeRodTrade(villager, emeralds, blazeRods);
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
                patch(villager);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (!enabled) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> patch(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!enabled) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> patch(villager));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled) {
            return;
        }
        if (event.getRightClicked() instanceof Villager villager) {
            patch(villager);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Villager villager) {
            patch(villager);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractMonitor(PlayerInteractEntityEvent event) {
        if (!enabled) {
            return;
        }
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> patch(villager));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (!enabled) {
            return;
        }
        MerchantRecipe recipe = event.getRecipe();
        if (ClericTradeUtil.isBlazeRodForEmeraldTrade(recipe)
                && !matchesConfigured(recipe)) {
            event.setRecipe(ClericTradeUtil.copyWithEmeraldCost(recipe, emeralds, blazeRods));
        }
    }

    private boolean matchesConfigured(MerchantRecipe recipe) {
        if (recipe.getResult() == null || recipe.getResult().getAmount() != blazeRods) {
            return false;
        }
        if (recipe.getIngredients().isEmpty() || recipe.getIngredients().get(0) == null) {
            return false;
        }
        return recipe.getIngredients().get(0).getAmount() == emeralds;
    }
}
