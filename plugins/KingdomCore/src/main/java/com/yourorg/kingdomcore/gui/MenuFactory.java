package com.yourorg.kingdomcore.gui;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.*;
import com.yourorg.kingdomcore.util.ItemUtils;
import com.yourorg.kingdomcore.util.NameNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuFactory {
    private final KingdomCorePlugin plugin;
    private final KingdomConfig config;
    private final AbilityService abilityService;
    private final AbilityOwnershipService abilityOwnershipService;
    private final HeartService heartService;
    private final ItemIdentityService itemIdentityService;
    private final ReviveService reviveService;
    private final DebugTelemetryService debugTelemetryService;
    private final NamespacedKey abilityKey;
    private final NamespacedKey buttonKey;
    private static final long REVIVE_PENDING_TIMEOUT_MS = 30_000L;
    private final Map<UUID, PendingRevive> pendingReviveTargets = new ConcurrentHashMap<>();

    public MenuFactory(KingdomCorePlugin plugin,
                       KingdomConfig config,
                       AbilityService abilityService,
                       AbilityOwnershipService abilityOwnershipService,
                       HeartService heartService,
                       ItemIdentityService itemIdentityService,
                       ReviveService reviveService,
                       DebugTelemetryService debugTelemetryService) {
        this.plugin = plugin;
        this.config = config;
        this.abilityService = abilityService;
        this.abilityOwnershipService = abilityOwnershipService;
        this.heartService = heartService;
        this.itemIdentityService = itemIdentityService;
        this.reviveService = reviveService;
        this.debugTelemetryService = debugTelemetryService;
        this.abilityKey = new NamespacedKey(plugin, "ability-id");
        this.buttonKey = new NamespacedKey(plugin, "button");
    }

    public void openAbilityList(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.ABILITY_LIST, null), 27, "Choose Ability");
        int slot = 0;
        for (AbilityDefinition ability : abilityService.getAllAbilities()) {
            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ability.name());
            meta.setLore(List.of(ability.shortDescription()));
            meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability.id());
            if (abilityOwnershipService.isAbilityTakenByOther(player.getUniqueId(), ability.id())) {
                meta.setLore(List.of(ability.shortDescription(), "§cAbility Taken"));
            }
            icon.setItemMeta(meta);
            inventory.setItem(slot++, icon);
            if (slot >= inventory.getSize()) {
                break;
            }
        }
        player.openInventory(inventory);
    }

    public void openAbilityConfirm(Player player, AbilityDefinition ability) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.ABILITY_CONFIRM, ability.id()), 27,
                "Confirm Ability");
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ability.name());
        meta.setLore(List.of(ability.shortDescription()));
        info.setItemMeta(meta);
        inventory.setItem(13, info);

        Optional<java.util.UUID> owner = abilityOwnershipService.getOwner(ability.id());
        if (abilityOwnershipService.isAbilityTakenByOther(player.getUniqueId(), ability.id())) {
            inventory.setItem(11, buttonItem("Ability Taken", MenuKeys.BUTTON_BACK, ability.id()));
        } else if (owner.isEmpty()) {
            inventory.setItem(11, buttonItem("Agree", MenuKeys.BUTTON_AGREE, ability.id()));
        }
        inventory.setItem(15, buttonItem("More", MenuKeys.BUTTON_MORE, ability.id()));
        inventory.setItem(18, buttonItem("Back", MenuKeys.BUTTON_BACK, ability.id()));
        player.openInventory(inventory);
    }

    public void openAbilityMore(Player player, AbilityDefinition ability) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.ABILITY_MORE, ability.id()), 27,
                "Ability Details");
        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ability.name());
        meta.setLore(List.of(ability.longDescription()));
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        inventory.setItem(18, buttonItem("Back", MenuKeys.BUTTON_BACK, ability.id()));
        player.openInventory(inventory);
    }
    public void openRevive(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.REVIVE, null), 27, "Revive Player");
        ItemStack info = new ItemStack(Material.BEACON);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("Enter nickname in chat");
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        inventory.setItem(18, buttonItem("Back", MenuKeys.BUTTON_BACK, null));
        player.openInventory(inventory);
        long expiresAt = System.currentTimeMillis() + REVIVE_PENDING_TIMEOUT_MS;
        pendingReviveTargets.put(player.getUniqueId(), new PendingRevive("", expiresAt));
        schedulePendingTimeout(player.getUniqueId(), expiresAt);
    }

    public boolean handleReviveChat(Player player, String message) {
        if (player == null || message == null) {
            return false;
        }
        PendingRevive pending = pendingReviveTargets.get(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        String target = NameNormalizer.normalize(message);
        if ("cancel".equals(target)) {
            clearRevivePending(player.getUniqueId());
            player.closeInventory();
            return true;
        }
        pendingReviveTargets.put(player.getUniqueId(), new PendingRevive(target, pending.expiresAtMs()));
        openReviveConfirm(player, target);
        return true;
    }

    private void openReviveConfirm(Player player, String targetName) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.REVIVE_CONFIRM, targetName), 27,
                "Confirm Revive");
        ItemStack info = new ItemStack(Material.BEACON);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("Revive: " + targetName);
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        inventory.setItem(11, buttonItem("Confirm", MenuKeys.BUTTON_CONFIRM, targetName));
        inventory.setItem(18, buttonItem("Back", MenuKeys.BUTTON_BACK, targetName));
        player.openInventory(inventory);
    }

    public void handleMenuClick(Player player, ItemStack clicked, MenuHolder holder) {
        if (player == null || clicked == null || holder == null) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        String abilityId = meta.getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        String button = meta.getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);

        switch (holder.getType()) {
            case ABILITY_LIST -> {
                if (abilityId == null) {
                    return;
                }
                Optional<UUID> owner = abilityOwnershipService.getOwner(abilityId);
                if (abilityOwnershipService.isAbilityTakenByOther(player.getUniqueId(), abilityId)) {
                    player.sendTitle("§cAbility Taken", "", 5, 35, 10);
                    player.sendActionBar("§cAbility Taken.");
                    return;
                }
                AbilityDefinition ability = abilityService.getAbility(abilityId);
                if (ability != null) {
                    openAbilityConfirm(player, ability);
                }
            }
            case ABILITY_CONFIRM -> handleConfirm(player, holder, abilityId, button);
            case ABILITY_MORE -> {
                if (MenuKeys.BUTTON_BACK.equals(button)) {
                    AbilityDefinition ability = abilityService.getAbility(holder.getAbilityId());
                    if (ability != null) {
                        openAbilityConfirm(player, ability);
                    }
                }
            }
            case REVIVE -> {
                if (MenuKeys.BUTTON_BACK.equals(button)) {
                    clearRevivePending(player.getUniqueId());
                    player.closeInventory();
                }
            }
            case REVIVE_CONFIRM -> handleReviveConfirm(player, holder, button);
            default -> {
            }
        }
    }

    private void handleConfirm(Player player, MenuHolder holder, String abilityId, String button) {
        AbilityDefinition ability = abilityService.getAbility(holder.getAbilityId());
        if (ability == null) {
            return;
        }
        if (MenuKeys.BUTTON_BACK.equals(button)) {
            openAbilityList(player);
            return;
        }
        if (MenuKeys.BUTTON_MORE.equals(button)) {
            openAbilityMore(player, ability);
            return;
        }
        if (!MenuKeys.BUTTON_AGREE.equals(button)) {
            return;
        }
        boolean claimed = abilityOwnershipService.claimAbility(player.getUniqueId(), ability.id());
        if (claimed) {
            player.closeInventory();
            player.sendTitle("§aWelcome", "§f" + ability.name(), 10, 50, 10);
        } else {
            player.sendTitle("§cAbility Taken", "", 5, 35, 10);
            player.sendActionBar("§cAbility Taken.");
        }
    }
    private void handleReviveConfirm(Player player, MenuHolder holder, String button) {
        if (MenuKeys.BUTTON_BACK.equals(button)) {
            openRevive(player);
            return;
        }
        if (!MenuKeys.BUTTON_CONFIRM.equals(button)) {
            return;
        }
        String target = holder.getAbilityId();
        reviveService.revive(player, target);
        clearRevivePending(player.getUniqueId());
        player.closeInventory();
    }

    public boolean isRevivePending(UUID playerId) {
        return playerId != null && pendingReviveTargets.containsKey(playerId);
    }

    public void clearRevivePending(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pendingReviveTargets.remove(playerId);
    }

    private void schedulePendingTimeout(UUID playerId, long expiresAt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingRevive pending = pendingReviveTargets.get(playerId);
            if (pending != null && pending.expiresAtMs() == expiresAt && System.currentTimeMillis() >= expiresAt) {
                pendingReviveTargets.remove(playerId);
            }
        }, REVIVE_PENDING_TIMEOUT_MS / 50L);
    }

    private record PendingRevive(String target, long expiresAtMs) {
    }

    private ItemStack buttonItem(String label, String id, String abilityId) {
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(label);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, id);
        if (abilityId != null) {
            meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, abilityId);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
