package com.yourorg.kingdomcore.util;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public final class CartographerTradeUtil {
    private CartographerTradeUtil() {
    }

    public static boolean applyGlassPaneTrade(Villager villager, int glassPanes) {
        if (villager == null || glassPanes < 1) {
            return false;
        }
        if (villager.getProfession() != Villager.Profession.CARTOGRAPHER) {
            return false;
        }

        List<MerchantRecipe> recipes = villager.getRecipes();
        if (recipes.isEmpty()) {
            return false;
        }

        List<MerchantRecipe> updated = new ArrayList<>(recipes.size());
        boolean changed = false;
        for (MerchantRecipe recipe : recipes) {
            if (isGlassPaneForEmeraldTrade(recipe) && ingredientAmount(recipe) != glassPanes) {
                updated.add(copyWithGlassPaneCost(recipe, glassPanes));
                changed = true;
            } else {
                updated.add(recipe);
            }
        }

        if (changed) {
            villager.setRecipes(updated);
        }
        return changed;
    }

    public static boolean isGlassPaneForEmeraldTrade(MerchantRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        ItemStack result = recipe.getResult();
        if (result == null || result.getType() != Material.EMERALD) {
            return false;
        }
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.size() != 1) {
            return false;
        }
        ItemStack ingredient = ingredients.get(0);
        return ingredient != null && ingredient.getType() == Material.GLASS_PANE;
    }

    public static MerchantRecipe copyWithGlassPaneCost(MerchantRecipe template, int glassPanes) {
        ItemStack result = template.getResult();
        MerchantRecipe recipe = new MerchantRecipe(
                result.clone(),
                template.getUses(),
                template.getMaxUses(),
                template.hasExperienceReward(),
                template.getVillagerExperience(),
                template.getPriceMultiplier());
        recipe.setDemand(template.getDemand());
        recipe.setSpecialPrice(template.getSpecialPrice());
        recipe.addIngredient(new ItemStack(Material.GLASS_PANE, glassPanes));
        return recipe;
    }

    private static int ingredientAmount(MerchantRecipe recipe) {
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.get(0) == null) {
            return 0;
        }
        return ingredients.get(0).getAmount();
    }
}
