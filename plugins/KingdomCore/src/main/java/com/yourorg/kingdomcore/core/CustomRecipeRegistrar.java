package com.yourorg.kingdomcore.core;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

public final class CustomRecipeRegistrar {
    private CustomRecipeRegistrar() {
    }

    public static void registerAll(Plugin plugin, ItemIdentityService itemIdentityService) {
        registerGoldenApple(plugin);
        registerMace(plugin, itemIdentityService);
        registerScythe(plugin, itemIdentityService);
        registerTrident(plugin, itemIdentityService);
        registerWardenCp(plugin, itemIdentityService);
        registerRevivalBeacon(plugin, itemIdentityService);
    }

    private static void registerGoldenApple(Plugin plugin) {
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_apple"));

        ItemStack goldenApple = new ItemStack(Material.GOLDEN_APPLE);
        NamespacedKey key = new NamespacedKey(plugin, "recipe_golden_apple");
        ShapedRecipe recipe = new ShapedRecipe(key, goldenApple);
        // Slots 2,4,5,6,8 — gold cross around apple (4 gold instead of vanilla 8)
        recipe.shape(" G ", "GAG", " G ");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('A', Material.APPLE);
        Bukkit.addRecipe(recipe);
    }

    private static void registerMace(Plugin plugin, ItemIdentityService itemIdentityService) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_custom_mace");
        ShapedRecipe recipe = new ShapedRecipe(key, itemIdentityService.createMaceItem());
        recipe.shape(" H ", " B ", " B ");
        recipe.setIngredient('H', Material.HEAVY_CORE);
        recipe.setIngredient('B', Material.BREEZE_ROD);
        // Bukkit.addRecipe(recipe); // Disabled vanilla crafting
    }

    private static void registerScythe(Plugin plugin, ItemIdentityService itemIdentityService) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_custom_scythe");
        ShapedRecipe recipe = new ShapedRecipe(key, itemIdentityService.createScytheItem());
        recipe.shape(" NS", " SN", "W  ");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        recipe.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        // Bukkit.addRecipe(recipe); // Disabled vanilla crafting
    }

    private static void registerTrident(Plugin plugin, ItemIdentityService itemIdentityService) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_custom_trident");
        ShapedRecipe recipe = new ShapedRecipe(key, itemIdentityService.createTridentItem());
        recipe.shape(" PS", " HP", "S  ");
        recipe.setIngredient('P', Material.PRISMARINE_SHARD);
        recipe.setIngredient('S', Material.PRISMARINE_CRYSTALS);
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
        // Bukkit.addRecipe(recipe); // Disabled vanilla crafting
    }

    private static void registerWardenCp(Plugin plugin, ItemIdentityService itemIdentityService) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_custom_warden_cp");
        ShapedRecipe recipe = new ShapedRecipe(key, itemIdentityService.createWardenCpItem());
        recipe.shape("ENE", "ECE", "ENE");
        recipe.setIngredient('E', Material.ECHO_SHARD);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('C', Material.NETHERITE_CHESTPLATE);
        // Bukkit.addRecipe(recipe); // Disabled vanilla crafting
    }

    private static void registerRevivalBeacon(Plugin plugin, ItemIdentityService itemIdentityService) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_custom_revival_beacon");
        ShapedRecipe recipe = new ShapedRecipe(key, itemIdentityService.createReviveBeacon(1));
        recipe.shape("NTN", "BHB", "NNN");
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('B', Material.BEACON);
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
        // Bukkit.addRecipe(recipe); // Disabled vanilla crafting
    }
}
