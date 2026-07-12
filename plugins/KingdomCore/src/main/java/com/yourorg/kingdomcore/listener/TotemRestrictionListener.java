package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Allows totems (farms, drops) but limits players to one totem total and applies post-combat pickup/storage rules.
 */
public class TotemRestrictionListener implements Listener {
    private static final int MAX_TOTEMS = 1;

    private final CombatTagService combatTagService;
    private final CombatRules combatRules;
    private final JavaPlugin plugin;

    public TotemRestrictionListener(CombatTagService combatTagService, CombatRules combatRules, JavaPlugin plugin) {
        this.combatTagService = combatTagService;
        this.combatRules = combatRules;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isTotem(event.getItem().getItemStack())) {
            return;
        }
        if (!enforce(player)) {
            return;
        }

        if (wouldExceedTotemLimit(player, 1)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can only carry §f1 totem §cat a time.");
            return;
        }

        if (!combatTagService.isTagged(player.getUniqueId())
                && combatTagService.getSpawnEntryCooldownRemainingMs(player.getUniqueId()) > 0) {
            event.setCancelled(true);
            long seconds = (combatTagService.getSpawnEntryCooldownRemainingMs(player.getUniqueId()) + 999) / 1000;
            player.sendMessage("§cYou must wait §f" + seconds + "s §cafter combat before picking up a totem.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!enforce(player)) {
            return;
        }
        if (!involvesTotem(event.getCurrentItem()) && !involvesTotem(event.getCursor())) {
            return;
        }

        if (isTakingTotemFromStorage(event, player)
                && combatTagService.getEchestCooldownRemainingMs(player.getUniqueId()) > 0) {
            event.setCancelled(true);
            long seconds = (combatTagService.getEchestCooldownRemainingMs(player.getUniqueId()) + 999) / 1000;
            player.sendMessage("§cYou cannot take totems from storage for another §f" + seconds + "s§c.");
            return;
        }

        if (isRelocatingTotemWithinPlayerInventory(event, player)) {
            return;
        }

        if (isAcquiringTotem(event, player) && wouldExceedTotemLimit(player, 1)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can only carry §f1 totem §cat a time.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        if (!event.isCancelled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!involvesTotem(event.getCurrentItem()) && !involvesTotem(event.getCursor())) {
            return;
        }
        resyncInventory(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!enforce(player)) {
            return;
        }

        int incoming = 0;
        PlayerInventory playerInventory = player.getInventory();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < playerInventory.getSize() && isTotem(event.getOldCursor())) {
                incoming++;
            }
        }
        if (incoming == 0) {
            return;
        }

        if (wouldExceedTotemLimit(player, incoming)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can only carry §f1 totem §cat a time.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHandsMonitor(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) {
            resyncInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enforce(event.getPlayer())) {
            return;
        }
        trimExcessTotems(event.getPlayer(), false);
    }

    public static int countTotems(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isTotem(stack)) {
                count += stack.getAmount();
            }
        }
        if (isTotem(inventory.getItemInOffHand())) {
            count += inventory.getItemInOffHand().getAmount();
        }
        if (isTotem(inventory.getHelmet())) {
            count += inventory.getHelmet().getAmount();
        }
        return count;
    }

    private static boolean isTotem(ItemStack stack) {
        return stack != null && stack.getType() == Material.TOTEM_OF_UNDYING;
    }

    private static boolean involvesTotem(ItemStack stack) {
        return isTotem(stack);
    }

    private boolean enforce(Player player) {
        return combatRules.shouldEnforce(player);
    }

    private boolean wouldExceedTotemLimit(Player player, int incoming) {
        return countTotems(player) + incoming > MAX_TOTEMS;
    }

    private boolean isAcquiringTotem(InventoryClickEvent event, Player player) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        Inventory clicked = event.getClickedInventory();
        PlayerInventory playerInventory = player.getInventory();
        boolean cursorTotem = isTotem(cursor);
        boolean currentTotem = isTotem(current);

        if (cursorTotem) {
            if (clicked != null && clicked.equals(playerInventory)) {
                return countTotems(player) + cursor.getAmount() > MAX_TOTEMS;
            }
            if (countTotems(player) >= MAX_TOTEMS) {
                return true;
            }
        }

        if (currentTotem && clicked != null && clicked != playerInventory
                && isStorageInventory(clicked.getType())) {
            return true;
        }

        if (currentTotem && event.isShiftClick() && clicked != null && clicked != playerInventory) {
            return true;
        }

        return false;
    }

    private boolean isRelocatingTotemWithinPlayerInventory(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        PlayerInventory playerInventory = player.getInventory();
        if (clicked == null || !clicked.equals(playerInventory)) {
            return false;
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.HOTBAR_SWAP) {
            return true;
        }

        ItemStack cursor = event.getCursor();
        if (isTotem(cursor) && countTotems(player) + cursor.getAmount() <= MAX_TOTEMS) {
            return true;
        }

        return false;
    }

    private void resyncInventory(Player player) {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private boolean isTakingTotemFromStorage(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        if (clicked.getType() == InventoryType.PLAYER) {
            return false;
        }
        if (!isStorageInventory(clicked.getType())) {
            return false;
        }

        ItemStack moving = event.getCurrentItem();
        if (!isTotem(moving)) {
            moving = event.getCursor();
        }
        if (!isTotem(moving)) {
            return false;
        }

        InventoryAction action = event.getAction();
        return action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME;
    }

    private void trimExcessTotems(Player player, boolean notify) {
        int count = countTotems(player);
        if (count <= MAX_TOTEMS) {
            return;
        }
        int toRemove = count - MAX_TOTEMS;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize() && toRemove > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isTotem(stack)) {
                continue;
            }
            inventory.setItem(slot, null);
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
            toRemove -= stack.getAmount();
        }
        if (toRemove > 0 && isTotem(inventory.getHelmet())) {
            ItemStack helmet = inventory.getHelmet();
            inventory.setHelmet(null);
            player.getWorld().dropItemNaturally(player.getLocation(), helmet);
            toRemove--;
        }
        if (toRemove > 0 && isTotem(inventory.getItemInOffHand())) {
            ItemStack offhand = inventory.getItemInOffHand();
            inventory.setItemInOffHand(null);
            player.getWorld().dropItemNaturally(player.getLocation(), offhand);
        }
        if (notify) {
            player.sendMessage("§cYou can only carry §f1 totem §cat a time.");
        }
    }

    private boolean isStorageInventory(InventoryType type) {
        return type == InventoryType.ENDER_CHEST
                || type == InventoryType.SHULKER_BOX
                || type == InventoryType.CHEST
                || type == InventoryType.BARREL
                || type == InventoryType.HOPPER
                || type == InventoryType.DROPPER
                || type == InventoryType.DISPENSER;
    }
}
