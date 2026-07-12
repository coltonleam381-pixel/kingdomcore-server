package com.yourorg.kingdomcore.core.services;

import org.bukkit.inventory.ItemStack;

public interface ItemIdentityService {
    boolean isHeartItem(ItemStack stack);

    boolean isCrownItem(ItemStack stack);

    boolean isReviveBeacon(ItemStack stack);

    boolean isMaceItem(ItemStack stack);

    boolean isScytheItem(ItemStack stack);

    boolean isWardenCpItem(ItemStack stack);

    boolean isTridentItem(ItemStack stack);

    boolean matchesCustomId(ItemStack stack, String itemId);

    boolean matchesAbilityItem(ItemStack stack, String abilityId, String abilityName);

    ItemStack createHeartItem(int amount);

    ItemStack createReviveBeacon(int amount);

    ItemStack createCrownItem();

    ItemStack createMaceItem();

    ItemStack createScytheItem();

    ItemStack createWardenCpItem();

    ItemStack createTridentItem();

    void ensureTridentItem(ItemStack stack);

    ItemStack createCustomItem(String itemId, int amount);
}
