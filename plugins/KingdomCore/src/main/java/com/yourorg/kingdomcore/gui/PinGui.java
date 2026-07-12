package com.yourorg.kingdomcore.gui;

import com.yourorg.kingdomcore.core.services.AuthService;
import com.yourorg.kingdomcore.integrations.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PinGui {
    private static final int SLOT_DISPLAY = 4;
    private static final int SLOT_CLEAR = 39;
    private static final int SLOT_ZERO = 40;
    private static final int SLOT_CONFIRM = 41;

    private final AuthService authService;
    private final ItemsAdderHook itemsAdderHook;
    private final Map<UUID, String> currentPins = new HashMap<>();

    public PinGui(AuthService authService, ItemsAdderHook itemsAdderHook) {
        this.authService = authService;
        this.itemsAdderHook = itemsAdderHook;
    }

    public void openRegisterGui(Player player) {
        currentPins.put(player.getUniqueId(), "");
        openGui(player, PinMenuHolder.Mode.REGISTER);
    }

    public void openLoginGui(Player player) {
        currentPins.put(player.getUniqueId(), "");
        openGui(player, PinMenuHolder.Mode.LOGIN);
    }

    private void openGui(Player player, PinMenuHolder.Mode mode) {
        PinMenuHolder holder = new PinMenuHolder(mode);
        Component title = mode == PinMenuHolder.Mode.REGISTER
                ? Component.text("Register PIN", NamedTextColor.GREEN, TextDecoration.BOLD)
                : Component.text("Login Required", NamedTextColor.RED, TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        updateDisplay(inv, "");
        inv.setItem(12, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b1"));
        inv.setItem(13, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b2"));
        inv.setItem(14, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b3"));
        inv.setItem(21, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b4"));
        inv.setItem(22, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b5"));
        inv.setItem(23, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b6"));
        inv.setItem(30, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b7"));
        inv.setItem(31, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b8"));
        inv.setItem(32, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b9"));
        inv.setItem(SLOT_ZERO, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§b0"));
        inv.setItem(SLOT_CLEAR, createButton(Material.RED_WOOL, "§cClear"));
        inv.setItem(SLOT_CONFIRM, createButton(Material.GREEN_WOOL, "§aConfirm"));

        ItemStack bg = createButton(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, bg);
            }
        }

        player.openInventory(inv);
    }

    private void updateDisplay(Inventory inv, String pin) {
        ItemStack display = new ItemStack(Material.PAPER);
        ItemMeta displayMeta = display.getItemMeta();
        displayMeta.setDisplayName("§ePIN: §f" + (pin.isEmpty() ? "Enter 4 digits..." : "*".repeat(pin.length())));
        display.setItemMeta(displayMeta);
        inv.setItem(SLOT_DISPLAY, display);
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPinMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof PinMenuHolder;
    }

    public boolean hasOpenPinMenu(Player player) {
        return player != null && isPinMenu(player.getOpenInventory().getTopInventory());
    }

    public void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PinMenuHolder holder)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }

        String pin = currentPins.getOrDefault(player.getUniqueId(), "");
        String digit = digitForSlot(slot);
        if (digit != null) {
            if (pin.length() >= 4) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            String nextPin = pin + digit;
            currentPins.put(player.getUniqueId(), nextPin);
            updateDisplay(top, nextPin);
            flashDigitSlot(top, slot, digit, plugin);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (slot == SLOT_CLEAR) {
            currentPins.put(player.getUniqueId(), "");
            updateDisplay(top, "");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            if (pin.length() != 4) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (holder.getMode() == PinMenuHolder.Mode.REGISTER) {
                authService.registerPin(player, pin);
                finishLogin(player, plugin);
                return;
            }

            if (authService.verifyPin(player, pin)) {
                authService.resetFailedAttempts(player.getUniqueId());
                finishLogin(player, plugin);
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                int fails = authService.getFailedAttempts(player.getUniqueId()) + 1;
                if (fails >= 3) {
                    player.kickPlayer("§cToo many failed login attempts!");
                    authService.resetFailedAttempts(player.getUniqueId());
                    currentPins.remove(player.getUniqueId());
                    return;
                }
                authService.incrementFailedAttempts(player.getUniqueId());
                currentPins.put(player.getUniqueId(), "");
                updateDisplay(top, "");
            }
        }
    }

    private String digitForSlot(int slot) {
        return switch (slot) {
            case 12 -> "1";
            case 13 -> "2";
            case 14 -> "3";
            case 21 -> "4";
            case 22 -> "5";
            case 23 -> "6";
            case 30 -> "7";
            case 31 -> "8";
            case 32 -> "9";
            case SLOT_ZERO -> "0";
            default -> null;
        };
    }

    private void flashDigitSlot(Inventory inv, int slot, String digit, JavaPlugin plugin) {
        String label = "§b" + digit;
        inv.setItem(slot, createButton(Material.LIME_STAINED_GLASS_PANE, label));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(inv.getHolder() instanceof PinMenuHolder)) {
                return;
            }
            inv.setItem(slot, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE, label));
        }, 8L);
    }

    private void finishLogin(Player player, JavaPlugin plugin) {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        currentPins.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        itemsAdderHook.applyResourcePackDelayed(plugin, player, 20L);
    }

    public void removePlayer(UUID playerId) {
        currentPins.remove(playerId);
    }
}
