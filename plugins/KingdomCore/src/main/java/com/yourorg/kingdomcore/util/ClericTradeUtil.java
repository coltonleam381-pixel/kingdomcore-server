package com.yourorg.kingdomcore.util;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public final class ClericTradeUtil {
    private static final int MASTER_LEVEL = 5;

    private ClericTradeUtil() {
    }

    public static boolean applyBlazeRodTrade(Villager villager, int emeralds, int blazeRods) {
        if (villager == null || emeralds < 1 || blazeRods < 1) {
            return false;
        }
        if (villager.getProfession() != Villager.Profession.CLERIC) {
            return false;
        }
        if (villager.getVillagerLevel() < MASTER_LEVEL) {
            return false;
        }

        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        boolean changed = false;
        if (!hasExperienceBottleTrade(recipes)) {
            recipes.add(createExperienceBottleTrade());
            changed = true;
        }

        MerchantRecipe desired = createBlazeRodTrade(emeralds, blazeRods);

        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);
            if (isBlazeRodForEmeraldTrade(recipe)) {
                if (matchesBlazeRodTrade(recipe, emeralds, blazeRods)) {
                    if (changed) {
                        villager.setRecipes(recipes);
                    }
                    return changed;
                }
                recipes.set(i, desired);
                villager.setRecipes(recipes);
                return true;
            }
        }

        // Add blaze rods without removing vanilla experience bottle trades.
        recipes.add(desired);
        villager.setRecipes(recipes);
        return true;
    }

    private static boolean hasExperienceBottleTrade(List<MerchantRecipe> recipes) {
        for (MerchantRecipe recipe : recipes) {
            if (recipe.getResult() != null && recipe.getResult().getType() == Material.EXPERIENCE_BOTTLE) {
                return true;
            }
        }
        return false;
    }

    private static MerchantRecipe createExperienceBottleTrade() {
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.EXPERIENCE_BOTTLE, 1),
                0,
                12,
                true,
                30,
                0.05f);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 3));
        return recipe;
    }

    public static boolean isBlazeRodForEmeraldTrade(MerchantRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        ItemStack result = recipe.getResult();
        if (result == null || result.getType() != Material.BLAZE_ROD) {
            return false;
        }
        return hasEmeraldCost(recipe);
    }

    private static boolean hasEmeraldCost(MerchantRecipe recipe) {
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient != null && ingredient.getType() == Material.EMERALD) {
                return true;
            }
        }
        return false;
    }

    public static MerchantRecipe createBlazeRodTrade(int emeralds, int blazeRods) {
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.BLAZE_ROD, blazeRods),
                0,
                16,
                true,
                5,
                0.05f);
        recipe.addIngredient(new ItemStack(Material.EMERALD, emeralds));
        return recipe;
    }

    public static MerchantRecipe copyWithEmeraldCost(MerchantRecipe template, int emeralds, int blazeRods) {
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.BLAZE_ROD, blazeRods),
                template.getUses(),
                template.getMaxUses(),
                template.hasExperienceReward(),
                template.getVillagerExperience(),
                template.getPriceMultiplier());
        recipe.setDemand(template.getDemand());
        recipe.setSpecialPrice(template.getSpecialPrice());
        recipe.addIngredient(new ItemStack(Material.EMERALD, emeralds));
        return recipe;
    }

    private static boolean matchesBlazeRodTrade(MerchantRecipe recipe, int emeralds, int blazeRods) {
        if (!isBlazeRodForEmeraldTrade(recipe)) {
            return false;
        }
        ItemStack result = recipe.getResult();
        if (result == null || result.getAmount() != blazeRods) {
            return false;
        }
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient != null && ingredient.getType() == Material.EMERALD) {
                return ingredient.getAmount() == emeralds;
            }
        }
        return false;
    }
}
