package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Enforces combat tag restrictions: no elytra while tagged, ender pearl cooldown in combat,
 * post-combat pearl lock, and post-combat storage lockdown.
 */
public class CombatRestrictionListener implements Listener {
    private final CombatTagService combatTagService;
    private final CombatRules combatRules;

    public CombatRestrictionListener(CombatTagService combatTagService, CombatRules combatRules) {
        this.combatTagService = combatTagService;
        this.combatRules = combatRules;
    }

    private boolean enforce(Player player) {
        return combatRules.shouldEnforce(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElytraToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!enforce(player)) {
            return;
        }
        if (combatTagService.isTagged(player.getUniqueId())) {
            event.setCancelled(true);
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
        if (!combatTagService.isTagged(player.getUniqueId())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (event.getSlot() == 38) {
            if (isElytra(clicked) || isElytra(cursor)) {
                event.setCancelled(true);
            }
        }
        if (event.isShiftClick() && isElytra(clicked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearl(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();
        if (!enforce(player)) {
            return;
        }
        if (blockPearlUse(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!enforce(player)) {
            return;
        }
        if (blockPearlUse(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunchSuccess(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!enforce(player)) {
            return;
        }
        combatTagService.recordCombatPearlUse(player.getUniqueId());
    }

    private boolean blockPearlUse(Player player) {
        UUID playerId = player.getUniqueId();
        long remainingMs = combatTagService.getPearlBlockRemainingMs(playerId);
        if (remainingMs <= 0) {
            return false;
        }
        long seconds = (remainingMs + 999L) / 1000L;
        if (combatTagService.isTagged(playerId)) {
            player.sendMessage("§cEnder pearl cooldown: §f" + seconds + "s§c remaining.");
        } else {
            player.sendMessage("§cYou cannot use ender pearls for another §f" + seconds + "s §cafter combat!");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!enforce(player) || !combatRules.shouldBlockCombatCooldowns(player)) {
            return;
        }
        if (isStorageBlock(event.getBlockPlaced().getType())) {
            long cooldownMs = combatTagService.getEchestCooldownRemainingMs(player.getUniqueId());
            if (cooldownMs > 0) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot place storage blocks for another " + (cooldownMs / 1000) + " seconds!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractStorage(PlayerInteractEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !enforce(player)) {
            return;
        }
        if (!combatRules.shouldBlockCombatCooldowns(player)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (isStorageBlock(event.getClickedBlock().getType())) {
                long cooldownMs = combatTagService.getEchestCooldownRemainingMs(player.getUniqueId());
                if (cooldownMs > 0) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot open storage blocks for another " + (cooldownMs / 1000) + " seconds!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStorageOpen(InventoryOpenEvent event) {
        if (!isStorageInventory(event.getInventory().getType())) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !enforce(player)) {
            return;
        }
        if (!combatRules.shouldBlockCombatCooldowns(player)) {
            return;
        }
        long cooldownMs = combatTagService.getEchestCooldownRemainingMs(player.getUniqueId());
        if (cooldownMs > 0) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot open this storage for another " + (cooldownMs / 1000) + " seconds!");
        }
    }

    private boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }

    private boolean isStorageBlock(Material type) {
        if (type == null) {
            return false;
        }
        return type == Material.ENDER_CHEST
                || type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type.name().endsWith("SHULKER_BOX");
    }

    private boolean isStorageInventory(InventoryType type) {
        return type == InventoryType.ENDER_CHEST
                || type == InventoryType.SHULKER_BOX
                || type == InventoryType.CHEST
                || type == InventoryType.BARREL;
    }

    public long getPearlBlockRemainingMs(UUID playerId) {
        return combatTagService.getPearlBlockRemainingMs(playerId);
    }
}
