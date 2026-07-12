package com.yourorg.kingdomcore.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongStrongStrengthPotionUtilTest {

    @Test
    void shortStrongStrengthMatchesVanillaStrongStrength() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(PotionType.STRONG_STRENGTH);
        potion.setItemMeta(meta);

        assertTrue(LongStrongStrengthPotionUtil.isShortStrongStrength(potion));
    }

    @Test
    void longStrongStrengthDoesNotMatchAgain() {
        ItemStack potion = LongStrongStrengthPotionUtil.createLongStrongStrength(
                Material.POTION, LongStrongStrengthPotionUtil.DEFAULT_DURATION_TICKS);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        assertTrue(LongStrongStrengthPotionUtil.hasLongStrengthEffect(
                meta, LongStrongStrengthPotionUtil.DEFAULT_DURATION_TICKS));
        assertFalse(LongStrongStrengthPotionUtil.isShortStrongStrength(potion));
    }

    @Test
    void longStrongStrengthKeepsAmplifierTwo() {
        ItemStack potion = LongStrongStrengthPotionUtil.createLongStrongStrength(
                Material.SPLASH_POTION, LongStrongStrengthPotionUtil.DEFAULT_DURATION_TICKS);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        assertTrue(meta.getCustomEffects().stream().anyMatch(effect ->
                effect.getType() == PotionEffectType.STRENGTH
                        && effect.getAmplifier() == 1
                        && effect.getDuration() == LongStrongStrengthPotionUtil.DEFAULT_DURATION_TICKS));
    }
}
