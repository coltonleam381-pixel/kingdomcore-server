package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.service.AfkService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class AfkListener implements Listener {

    private final AfkService afkService;

    public AfkListener(AfkService afkService) {
        this.afkService = afkService;
    }

    private void checkCancelOrExit(Player player, String actionReason) {
        if (afkService.isWindup(player)) {
            afkService.cancelWindup(player, actionReason);
        } else if (afkService.isAfk(player)) {
            afkService.exitAfk(player, actionReason);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (afkService.isAfk(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setCancelled(true);
                afkService.snapToAnchor(player);
            }
            return;
        }
        if (!afkService.isWindup(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            checkCancelOrExit(player, "moved");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakUnderAfk(BlockBreakEvent event) {
        org.bukkit.block.Block broken = event.getBlock();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!afkService.isAfk(player)) {
                continue;
            }
            if (!isStandingOnOrIn(player, broken)) {
                continue;
            }
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage("§cYou cannot break blocks under an AFK player.");
            }
            return;
        }
    }

    private boolean isStandingOnOrIn(Player player, org.bukkit.block.Block block) {
        org.bukkit.block.Block feet = player.getLocation().getBlock();
        org.bukkit.block.Block below = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return block.getX() == feet.getX() && block.getY() == feet.getY() && block.getZ() == feet.getZ()
                || block.getX() == below.getX() && block.getY() == below.getY() && block.getZ() == below.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (afkService.isWindup(player) || afkService.isAfk(player)) {
            checkCancelOrExit(player, "interacted");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (afkService.isWindup(player) || afkService.isAfk(player)) {
                checkCancelOrExit(player, "inventory action");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (afkService.isWindup(player) || afkService.isAfk(player)) {
            checkCancelOrExit(player, "dropped item");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (afkService.isWindup(player) || afkService.isAfk(player)) {
                // User specifically requested to block item pickup entirely to prevent trolling during windup and AFK
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (afkService.isAfk(player)) {
                // Invincible while AFK
                event.setCancelled(true);
            } else if (afkService.isWindup(player)) {
                // Taking damage during windup cancels it
                afkService.cancelWindup(player, "took damage");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (afkService.isWindup(player) || afkService.isAfk(player)) {
            checkCancelOrExit(player, "sneaked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (event.isSprinting() && (afkService.isWindup(player) || afkService.isAfk(player))) {
            checkCancelOrExit(player, "sprinted");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (afkService.isWindup(player) || afkService.isAfk(player)) {
            checkCancelOrExit(player, "swapped items");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (afkService.isAfk(player)) {
                // Prevent starving while AFK
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        // Delay the check to main thread since we access state
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AfkListener.class), 
            () -> {
                if (afkService.isWindup(player) || afkService.isAfk(player)) {
                    checkCancelOrExit(player, "chatted");
                }
            }
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String cmd = event.getMessage().split(" ")[0].toLowerCase();
        if (cmd.equals("/afk") || cmd.equals("/select")) return;
        
        if (afkService.isWindup(player) || afkService.isAfk(player)) {
            checkCancelOrExit(player, "used a command");
        }
    }
}
