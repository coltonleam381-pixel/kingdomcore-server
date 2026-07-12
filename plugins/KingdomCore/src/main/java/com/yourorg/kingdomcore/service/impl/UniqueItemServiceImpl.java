package com.yourorg.kingdomcore.service.impl;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.persistence.repo.UniqueItemRepository;
import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class UniqueItemServiceImpl implements UniqueItemService {
    private final Plugin plugin;
    private final ItemIdentityService itemIdentityService;
    private final com.yourorg.kingdomcore.service.MonumentService monumentService;
    private final UniqueItemRepository repository;
    private final Map<String, Long> recraftMsByItem;
    private final Map<String, String> matcherItemIdsByKey;
    private final Map<String, Long> cooldownUntilMs = new HashMap<>();

    public UniqueItemServiceImpl(Plugin plugin,
                                 ItemIdentityService itemIdentityService,
                                 com.yourorg.kingdomcore.service.MonumentService monumentService,
                                 UniqueItemRepository repository,
                                 Map<String, Long> recraftMsByItem,
                                 Map<String, String> matcherItemIdsByKey) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
        this.monumentService = monumentService;
        this.repository = repository;
        this.recraftMsByItem = recraftMsByItem;
        this.matcherItemIdsByKey = matcherItemIdsByKey;
        this.cooldownUntilMs.putAll(repository.loadCooldowns());
        for (String id : matcherItemIdsByKey.keySet()) {
            monumentService.updateGlassState(id, !canCraft(id));
        }
    }

    @Override
    public synchronized boolean canCraft(String itemId) {
        long now = System.currentTimeMillis();
        long unlockAt = cooldownUntilMs.getOrDefault(itemId, 0L);
        if (unlockAt == -1L) return false;
        return now >= unlockAt;
    }

    @Override
    public synchronized boolean isPresent(String itemId) {
        return cooldownUntilMs.getOrDefault(itemId, 0L) == -1L;
    }

    @Override
    public synchronized long getRemainingMs(String itemId) {
        long now = System.currentTimeMillis();
        long unlockAt = cooldownUntilMs.getOrDefault(itemId, 0L);
        if (unlockAt == -1L) return 0L;
        return Math.max(0L, unlockAt - now);
    }

    private String getDisplayName(String id) {
        return switch (id) {
            case "crown" -> "Crown";
            case "mace" -> "Mace";
            case "scythe" -> "Scythe";
            case "warden_cp" -> "Warden Chestplate";
            case "trident" -> "Poseidon Trident";
            default -> id;
        };
    }

    @Override
    public synchronized void markCrafted(String itemId) {
        boolean wasPresent = isPresent(itemId);
        cooldownUntilMs.put(itemId, -1L);
        repository.saveCooldown(itemId, -1L);
        monumentService.updateGlassState(itemId, true);

        if (!wasPresent) {
            String name = getDisplayName(itemId);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§a" + name, "§7has been obtained!", 10, 40, 10);
            }
        }
    }

    @Override
    public synchronized void markDestroyed(String itemId) {
        boolean wasPresent = isPresent(itemId);
        long ms = recraftMsByItem.getOrDefault(itemId, 0L);
        long unlockAt = System.currentTimeMillis() + ms;
        cooldownUntilMs.put(itemId, unlockAt);
        repository.saveCooldown(itemId, unlockAt);
        monumentService.updateGlassState(itemId, true);

        if (wasPresent) {
            String name = getDisplayName(itemId);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§c" + name, "§7has been destroyed!", 10, 40, 10);
            }
        }
    }

    @Override
    public synchronized void resetItem(String itemId) {
        cooldownUntilMs.put(itemId, 0L);
        repository.saveCooldown(itemId, 0L);
        monumentService.updateGlassState(itemId, false);

        String targetCustomId = matcherItemIdsByKey.get(itemId);
        if (targetCustomId != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                purgeAllFromInventory(p.getInventory(), targetCustomId);
                purgeAllFromInventory(p.getEnderChest(), targetCustomId);
            }
        }
    }

    @Override
    public void resetAllItems() {
        for (String id : matcherItemIdsByKey.keySet()) {
            resetItem(id);
        }
    }

    @Override
    public synchronized void checkAndPurgeDuplicates(Player player) {
        syncWorldPresence();

        for (Map.Entry<String, String> entry : matcherItemIdsByKey.entrySet()) {
            String itemId = entry.getKey();
            String matcherId = entry.getValue();
            int onPlayer = countOnPlayer(player, matcherId);
            if (onPlayer == 0) {
                continue;
            }

            int totalInWorld = countAllInstances(matcherId);

            // DB lost "present" state but this player is the only holder — restore, do not delete.
            if (!isPresent(itemId) && totalInWorld <= onPlayer) {
                markCrafted(itemId);
            }

            if (!canCraft(itemId) && !isPresent(itemId) && totalInWorld > onPlayer) {
                purgeAllFromPlayer(player, matcherId);
                player.sendMessage("§c§lWARNING: §cIllegal copy of " + getDisplayName(itemId) + " removed (item is on cooldown).");
                continue;
            }

            if (onPlayer > 1) {
                purgeCountFromPlayer(player, matcherId, onPlayer - 1);
                player.sendMessage("§c§lWARNING: §cDuplicate " + getDisplayName(itemId) + " copies removed from your inventory!");
                continue;
            }

            if (onPlayer == 1 && totalInWorld > 1) {
                int removed = purgeDuplicatesExceptPlayer(player, matcherId);
                if (removed > 0) {
                    player.sendMessage("§c§lWARNING: §cDuplicate " + getDisplayName(itemId) + " copies removed from the world.");
                }
            }
        }
    }

    @Override
    public synchronized void syncWorldPresence() {
        for (Map.Entry<String, String> entry : matcherItemIdsByKey.entrySet()) {
            String itemId = entry.getKey();
            String matcherId = entry.getValue();
            if (countAllInstances(matcherId) > 0 && !isPresent(itemId)) {
                cooldownUntilMs.put(itemId, -1L);
                repository.saveCooldown(itemId, -1L);
                monumentService.updateGlassState(itemId, true);
            }
        }
    }

    private int countOnPlayer(Player player, String matcherId) {
        int count = countInInventory(player.getInventory(), matcherId);
        count += countInInventory(player.getEnderChest(), matcherId);
        return count;
    }

    private int countAllInstances(String matcherId) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            count += countOnPlayer(online, matcherId);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                count += countInStack(item.getItemStack(), matcherId);
            }
        }
        return count;
    }

    private int countInInventory(Inventory inventory, String matcherId) {
        if (inventory == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            count += countInStack(stack, matcherId);
        }
        if (inventory instanceof PlayerInventory playerInventory) {
            for (ItemStack stack : playerInventory.getArmorContents()) {
                count += countInStack(stack, matcherId);
            }
            count += countInStack(playerInventory.getItemInOffHand(), matcherId);
        }
        return count;
    }

    private int countInStack(ItemStack stack, String matcherId) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int count = 0;
        if (itemIdentityService.matchesCustomId(stack, matcherId)) {
            count += Math.max(1, stack.getAmount());
        }
        if (stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof InventoryHolder holder) {
            count += countInInventory(holder.getInventory(), matcherId);
        }
        return count;
    }

    private int purgeDuplicatesExceptPlayer(Player keeper, String matcherId) {
        int removed = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(keeper.getUniqueId())) {
                continue;
            }
            int onPlayer = countOnPlayer(online, matcherId);
            if (onPlayer > 0) {
                purgeAllFromPlayer(online, matcherId);
                online.sendMessage("§c§lWARNING: §cDuplicate unique item removed from your inventory.");
                removed += onPlayer;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (itemIdentityService.matchesCustomId(item.getItemStack(), matcherId)) {
                    item.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private void purgeAllFromPlayer(Player player, String matcherId) {
        purgeAllFromInventory(player.getInventory(), matcherId);
        purgeAllFromInventory(player.getEnderChest(), matcherId);
    }

    private void purgeCountFromPlayer(Player player, String matcherId, int count) {
        int remaining = count;
        remaining = purgeCountFromInventory(player.getInventory(), matcherId, remaining);
        if (remaining > 0) {
            purgeCountFromInventory(player.getEnderChest(), matcherId, remaining);
        }
    }

    private void purgeAllFromInventory(Inventory inventory, String matcherId) {
        purgeCountFromInventory(inventory, matcherId, Integer.MAX_VALUE);
    }

    private int purgeCountFromInventory(Inventory inventory, String matcherId, int count) {
        if (inventory == null || count <= 0) {
            return count;
        }
        int remaining = count;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null) {
                continue;
            }
            if (itemIdentityService.matchesCustomId(stack, matcherId)) {
                inventory.setItem(slot, null);
                remaining--;
                continue;
            }
            if (stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
                    && blockStateMeta.getBlockState() instanceof InventoryHolder holder) {
                int before = remaining;
                remaining = purgeCountFromInventory(holder.getInventory(), matcherId, remaining);
                if (remaining < before) {
                    blockStateMeta.setBlockState((org.bukkit.block.BlockState) holder);
                    stack.setItemMeta(blockStateMeta);
                }
            }
        }
        if (inventory instanceof PlayerInventory playerInventory && remaining > 0) {
            for (int slot = 0; slot < playerInventory.getArmorContents().length && remaining > 0; slot++) {
                ItemStack stack = playerInventory.getArmorContents()[slot];
                if (stack != null && itemIdentityService.matchesCustomId(stack, matcherId)) {
                    playerInventory.setItem(36 + slot, null);
                    remaining--;
                }
            }
            ItemStack offHand = playerInventory.getItemInOffHand();
            if (remaining > 0 && offHand != null && itemIdentityService.matchesCustomId(offHand, matcherId)) {
                playerInventory.setItemInOffHand(null);
                remaining--;
            }
        }
        return remaining;
    }
}
