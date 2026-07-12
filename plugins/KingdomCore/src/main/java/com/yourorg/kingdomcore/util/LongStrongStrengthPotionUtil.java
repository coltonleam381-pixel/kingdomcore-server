package com.yourorg.kingdomcore.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class LongStrongStrengthPotionUtil {
    public static final int DEFAULT_DURATION_TICKS = 9600;

    private LongStrongStrengthPotionUtil() {
    }

    public static boolean isBrewablePotionMaterial(Material type) {
        return type == Material.POTION
                || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION;
    }

    public static boolean isShortStrongStrength(ItemStack stack) {
        if (stack == null || !isBrewablePotionMaterial(stack.getType())) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (meta.getBasePotionType() != PotionType.STRONG_STRENGTH) {
            return false;
        }
        return !hasLongStrengthEffect(meta);
    }

    public static boolean isLongStrongStrength(ItemStack stack, int durationTicks) {
        if (stack == null || !isBrewablePotionMaterial(stack.getType())) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (meta.getBasePotionType() != PotionType.STRONG_STRENGTH) {
            return false;
        }
        return hasLongStrengthEffect(meta, durationTicks);
    }

    public static boolean isLongStrongStrengthDrinkable(ItemStack stack, int durationTicks) {
        return stack != null
                && stack.getType() == Material.POTION
                && isLongStrongStrength(stack, durationTicks);
    }

    public static boolean hasLongStrengthEffect(PotionMeta meta, int durationTicks) {
        for (PotionEffect effect : meta.getCustomEffects()) {
            if (effect.getType() == PotionEffectType.STRENGTH
                    && effect.getAmplifier() >= 1
                    && effect.getDuration() >= durationTicks - 20) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLongStrengthEffect(PotionMeta meta) {
        return hasLongStrengthEffect(meta, DEFAULT_DURATION_TICKS);
    }

    public static ItemStack createLongStrongStrength(Material potionMaterial, int durationTicks) {
        ItemStack stack = new ItemStack(potionMaterial);
        applyLongStrongStrength(stack, durationTicks);
        return stack;
    }

    public static void applyLongStrongStrength(ItemStack stack, int durationTicks) {
        if (stack == null || !isBrewablePotionMaterial(stack.getType())) {
            return;
        }
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setBasePotionType(PotionType.STRONG_STRENGTH);
        meta.clearCustomEffects();
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                durationTicks,
                1,
                false,
                true,
                true
        ), true);
        stack.setItemMeta(meta);
    }
}
