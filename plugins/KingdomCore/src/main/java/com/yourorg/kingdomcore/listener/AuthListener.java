package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.AuthService;
import com.yourorg.kingdomcore.gui.PinGui;
import com.yourorg.kingdomcore.integrations.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AuthListener implements Listener {
    private final AuthService authService;
    private final PinGui pinGui;
    private final ItemsAdderHook itemsAdderHook;
    private final JavaPlugin plugin;

    public AuthListener(AuthService authService, PinGui pinGui, ItemsAdderHook itemsAdderHook, JavaPlugin plugin) {
        this.authService = authService;
        this.pinGui = pinGui;
        this.itemsAdderHook = itemsAdderHook;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) {
            return;
        }
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        authService.handlePlayerJoin(player, ip);

        if (!authService.isAuthenticated(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
            player.sendActionBar(Component.text("Enter your PIN in the menu...", NamedTextColor.YELLOW));
            schedulePinGui(player);
        } else {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            itemsAdderHook.applyResourcePackDelayed(plugin, player, 20L);
        }
    }

    private void schedulePinGui(Player player) {
        long[] delays = {10L, 20L, 40L, 80L};
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPinGuiIfNeeded(player), delay);
        }
    }

    private void openPinGuiIfNeeded(Player player) {
        if (!player.isOnline() || authService.isAuthenticated(player.getUniqueId())) {
            return;
        }
        if (pinGui.hasOpenPinMenu(player)) {
            return;
        }
        if (authService.isRegistered(player.getUniqueId())) {
            pinGui.openLoginGui(player);
            player.sendActionBar(Component.text("Login required — enter your PIN", NamedTextColor.RED));
        } else {
            pinGui.openRegisterGui(player);
            player.sendActionBar(Component.text("Create a 4-digit PIN", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authService.handlePlayerQuit(event.getPlayer().getUniqueId());
        pinGui.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ() || event.getFrom().getY() != event.getTo().getY()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou must log in first!");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            // Allow login/register commands if we had them, but we use GUI.
            // So completely block everything.
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou must log in first!");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!authService.isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player) {
            if (!authService.isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!pinGui.isPinMenu(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);

        if (!authService.isAuthenticated(player.getUniqueId())) {
            pinGui.handleClick(player, event, plugin);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!pinGui.isPinMenu(event.getInventory())) {
            if (authService.isAuthenticated(player.getUniqueId())) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            return;
        }

        if (!authService.isAuthenticated(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || authService.isAuthenticated(player.getUniqueId())) {
                    return;
                }
                if (pinGui.hasOpenPinMenu(player)) {
                    return;
                }
                if (authService.isRegistered(player.getUniqueId())) {
                    pinGui.openLoginGui(player);
                } else {
                    pinGui.openRegisterGui(player);
                }
            }, 5L);
        } else {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }
}
