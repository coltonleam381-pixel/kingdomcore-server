package com.yourorg.kingdomcore.service;

import org.bukkit.entity.Player;

public interface UniqueItemService {
    boolean canCraft(String itemId);

    boolean isPresent(String itemId);

    long getRemainingMs(String itemId);

    void markCrafted(String itemId);

    void markDestroyed(String itemId);

    void resetItem(String itemId);

    void resetAllItems();

    void checkAndPurgeDuplicates(Player player);

    void syncWorldPresence();
}
