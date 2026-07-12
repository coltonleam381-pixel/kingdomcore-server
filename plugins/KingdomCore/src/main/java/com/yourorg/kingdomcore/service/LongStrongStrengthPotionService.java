package com.yourorg.kingdomcore.service;

import com.yourorg.kingdomcore.util.LongStrongStrengthPotionUtil;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionBrewer;

import java.util.ArrayList;
import java.util.List;

public final class LongStrongStrengthPotionService {
    private static final Material[] POTION_MATERIALS = {
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION
    };

    private final JavaPlugin plugin;
    private Listener brewListener;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public LongStrongStrengthPotionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("potion-brewing.long-strong-strength.enabled", true)) {
            return;
        }

        int durationTicks = plugin.getConfig().getInt(
                "potion-brewing.long-strong-strength.duration-ticks",
                LongStrongStrengthPotionUtil.DEFAULT_DURATION_TICKS);
        registerMixes(durationTicks);
        brewListener = new BrewListener(durationTicks);
        plugin.getServer().getPluginManager().registerEvents(brewListener, plugin);
        plugin.getLogger().info("Long Strong Strength brewing enabled (Strength II + redstone -> "
                + (durationTicks / 20) + "s; 8min drinkable + gunpowder -> splash).");
    }

    public void stop() {
        PotionBrewer brewer = plugin.getServer().getPotionBrewer();
        for (NamespacedKey key : registeredKeys) {
            brewer.removePotionMix(key);
        }
        registeredKeys.clear();
        if (brewListener != null) {
            HandlerList.unregisterAll(brewListener);
            brewListener = null;
        }
    }

    private void registerMixes(int durationTicks) {
        PotionBrewer brewer = plugin.getServer().getPotionBrewer();
        RecipeChoice redstone = new RecipeChoice.MaterialChoice(Material.REDSTONE);
        for (Material material : POTION_MATERIALS) {
            NamespacedKey key = new NamespacedKey(plugin, "long_strong_strength_" + material.name().toLowerCase());
            RecipeChoice input = PotionMix.createPredicateChoice(stack ->
                    stack.getType() == material && LongStrongStrengthPotionUtil.isShortStrongStrength(stack));
            ItemStack result = LongStrongStrengthPotionUtil.createLongStrongStrength(material, durationTicks);
            brewer.addPotionMix(new PotionMix(key, result, input, redstone));
            registeredKeys.add(key);
        }

        NamespacedKey splashKey = new NamespacedKey(plugin, "long_strong_strength_splash_from_drinkable");
        RecipeChoice drinkableInput = PotionMix.createPredicateChoice(stack ->
                LongStrongStrengthPotionUtil.isLongStrongStrengthDrinkable(stack, durationTicks));
        RecipeChoice gunpowder = new RecipeChoice.MaterialChoice(Material.GUNPOWDER);
        ItemStack splashResult = LongStrongStrengthPotionUtil.createLongStrongStrength(
                Material.SPLASH_POTION, durationTicks);
        brewer.addPotionMix(new PotionMix(splashKey, splashResult, drinkableInput, gunpowder));
        registeredKeys.add(splashKey);
    }

    private final class BrewListener implements Listener {
        private final int durationTicks;

        private BrewListener(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onBrew(BrewEvent event) {
            BrewerInventory inventory = event.getContents();
            ItemStack ingredient = inventory.getIngredient();
            if (ingredient == null) {
                return;
            }

            Material ingredientType = ingredient.getType();
            if (ingredientType != Material.REDSTONE && ingredientType != Material.GUNPOWDER) {
                return;
            }

            List<ItemStack> results = event.getResults();
            for (int slot = 0; slot < results.size(); slot++) {
                ItemStack input = inventory.getItem(slot);
                Material resultType;
                if (ingredientType == Material.REDSTONE) {
                    if (!LongStrongStrengthPotionUtil.isShortStrongStrength(input)) {
                        continue;
                    }
                    resultType = input.getType();
                } else {
                    if (!LongStrongStrengthPotionUtil.isLongStrongStrengthDrinkable(input, durationTicks)) {
                        continue;
                    }
                    resultType = Material.SPLASH_POTION;
                }

                ItemStack result = results.get(slot);
                if (result == null || result.getType() == Material.AIR) {
                    result = new ItemStack(resultType);
                    results.set(slot, result);
                }
                LongStrongStrengthPotionUtil.applyLongStrongStrength(result, durationTicks);
            }
        }
    }
}
