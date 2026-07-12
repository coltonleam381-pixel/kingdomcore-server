package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.AbilityLevelCosts;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.yourorg.kingdomcore.util.NpcOnlyCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AbilityUpgradeCommand implements CommandExecutor, Listener {
    private static final Pattern ABILITY_INFO_SPLIT = Pattern.compile("\\s*[;|]\\s*");
    private static final String LEGACY_MENU_TITLE = ChatColor.DARK_GRAY + "Ability Upgrades";

    private static final String TITLE_NO_ABILITY = ":offset_-48::upgrade_noability_gui:";
    private static final String TITLE_LVL1_GREEN = ":offset_-48::upgrade_lvl1_green_gui:";
    private static final String TITLE_LVL1_RED = ":offset_-48::upgrade_lvl1_red_gui:";
    private static final String TITLE_LVL2_GREEN = ":offset_-48::upgrade_lvl2_green_gui:";
    private static final String TITLE_LVL2_RED = ":offset_-48::upgrade_lvl2_red_gui:";
    private static final String TITLE_LVL3_GREEN = ":offset_-48::upgrade_lvl3_green_gui:";
    private static final String TITLE_LVL3_RED = ":offset_-48::upgrade_lvl3_red_gui:";
    private static final String TITLE_MAX = ":offset_-48::upgrade_lvlmax_gold_gui:";

    private static final int[] LEVEL_1_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] LEVEL_2_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] LEVEL_3_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int[] INFO_SLOTS = {38, 39, 40, 41, 42};

    private final AbilityService abilityService;
    private final HeartService heartService;
    private final ItemIdentityService itemIdentityService;
    private final AbilityLevelCosts costs;

    public AbilityUpgradeCommand(AbilityService abilityService,
                                 HeartService heartService,
                                 ItemIdentityService itemIdentityService,
                                 AbilityLevelCosts costs) {
        this.abilityService = abilityService;
        this.heartService = heartService;
        this.itemIdentityService = itemIdentityService;
        this.costs = costs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player;
        if (sender instanceof Player playerSender) {
            if (NpcOnlyCommands.denyUnlessAllowed(playerSender)) {
                return true;
            }
            player = playerSender;
        } else {
            if (args.length != 1) {
                sender.sendMessage("Usage: /abilityupgrade <player>");
                return true;
            }
            player = Bukkit.getPlayerExact(args[0]);
            if (player == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        }
        openMenu(player);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!isUpgradeMenuTitle(view.getTitle())) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= view.getTopInventory().getSize()) {
            return;
        }

        PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
        String abilityId = state.getAbilityId();
        if (abilityId == null || abilityId.isBlank() || abilityService.getAbility(abilityId) == null) {
            return;
        }

        int currentLevel = state.getAbilityLevel();
        if (currentLevel >= 3) {
            return;
        }

        int targetLevel = slotToLevel(rawSlot);
        if (targetLevel == -1 || targetLevel != currentLevel + 1) {
            return;
        }

        int cost = costs.costForNextLevel(currentLevel);
        if (!heartService.tryUpgradeAbility(player, cost)) {
            player.sendMessage(ChatColor.RED + "You need " + cost + " hearts in inventory/hotbar.");
            openMenu(player);
            return;
        }

        heartService.updateAbilityLevel(player.getUniqueId(), targetLevel);
        player.sendTitle("§aAbility Upgraded", "§fLevel " + targetLevel, 5, 35, 10);
        player.sendActionBar("§aUpgraded to Level " + targetLevel + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 0.7f);
        player.sendMessage(ChatColor.GREEN + "Ability upgraded to Level " + targetLevel + ".");
        player.closeInventory();
    }

    private void openMenu(Player player) {
        PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
        String abilityId = state.getAbilityId();
        AbilityDefinition ability = abilityId == null ? null : abilityService.getAbility(abilityId);
        boolean hasAbility = ability != null;
        int level = Math.max(0, state.getAbilityLevel());

        String menuTitle = resolveMenuTitle(player, state, hasAbility);
        Inventory inventory = Bukkit.createInventory(null, 54, menuTitle);
        fillBackground(inventory);

        placeLevelButton(inventory, player, 1, level, hasAbility);
        placeLevelButton(inventory, player, 2, level, hasAbility);
        placeLevelButton(inventory, player, 3, level, hasAbility);
        placeInfo(inventory, ability, level, hasAbility);

        player.openInventory(inventory);
    }

    private String resolveMenuTitle(Player player, PlayerState state, boolean hasAbility) {
        if (!hasAbility) {
            return TITLE_NO_ABILITY;
        }

        int level = Math.max(0, state.getAbilityLevel());
        if (level >= 3) {
            return TITLE_MAX;
        }

        int nextCost = costs.costForNextLevel(level);
        boolean canAfford = canAffordInInventory(player, nextCost);
        return switch (level) {
            case 0 -> canAfford ? TITLE_LVL1_GREEN : TITLE_LVL1_RED;
            case 1 -> canAfford ? TITLE_LVL2_GREEN : TITLE_LVL2_RED;
            case 2 -> canAfford ? TITLE_LVL3_GREEN : TITLE_LVL3_RED;
            default -> LEGACY_MENU_TITLE;
        };
    }

    private boolean isUpgradeMenuTitle(String title) {
        return LEGACY_MENU_TITLE.equals(title)
                || TITLE_NO_ABILITY.equals(title)
                || TITLE_LVL1_GREEN.equals(title)
                || TITLE_LVL1_RED.equals(title)
                || TITLE_LVL2_GREEN.equals(title)
                || TITLE_LVL2_RED.equals(title)
                || TITLE_LVL3_GREEN.equals(title)
                || TITLE_LVL3_RED.equals(title)
                || TITLE_MAX.equals(title);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void placeLevelButton(Inventory inventory, Player player, int targetLevel, int currentLevel, boolean hasAbility) {
        int[] slots = slotsForLevel(targetLevel);
        if (slots.length == 0) {
            return;
        }

        ItemStack item;
        if (!hasAbility) {
            item = createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    ChatColor.GRAY + "Level " + targetLevel,
                    List.of(ChatColor.RED + "Choose an ability first.")
            );
        } else if (currentLevel >= 3) {
            item = createItem(
                    Material.YELLOW_STAINED_GLASS_PANE,
                    ChatColor.GOLD + "Level " + targetLevel + " (MAX)",
                    List.of(ChatColor.YELLOW + "All upgrades unlocked.")
            );
        } else if (targetLevel <= currentLevel) {
            item = createItem(
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    ChatColor.AQUA + "Level " + targetLevel + " Unlocked",
                    List.of(ChatColor.GREEN + "Already unlocked.")
            );
        } else if (targetLevel == currentLevel + 1) {
            int cost = costs.costForNextLevel(currentLevel);
            boolean canAfford = canAffordInInventory(player, cost);
            ChatColor color = canAfford ? ChatColor.GREEN : ChatColor.RED;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Cost: " + cost + " hearts");
            lore.add(canAfford ? ChatColor.GREEN + "Click to upgrade now." : ChatColor.RED + "Not enough hearts in inventory.");
            item = createClickableBlankItem(color + "Level " + targetLevel, lore);
        } else {
            item = createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    ChatColor.DARK_GRAY + "Level " + targetLevel + " Locked",
                    List.of(ChatColor.GRAY + "Unlock previous level first.")
            );
        }

        for (int slot : slots) {
            inventory.setItem(slot, item);
        }
    }

    private void placeInfo(Inventory inventory, AbilityDefinition ability, int currentLevel, boolean hasAbility) {
        ItemStack info;
        if (!hasAbility) {
            info = createItem(
                    Material.BARRIER,
                    ChatColor.RED + "No Ability Selected",
                    List.of(
                            ChatColor.GRAY + "Use the ability NPC first.",
                            ChatColor.GRAY + "Then come back to upgrade."
                    )
            );
        } else if (currentLevel >= 3) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + ability.name());
            lore.addAll(splitAbilityInfoLore(ability.longDescription()));
            lore.add(ChatColor.GREEN + "Level: 3/3");
            info = createItem(
                    Material.NETHER_STAR,
                    ChatColor.GOLD + "Max Level Reached",
                    lore
            );
        } else {
            int nextCost = costs.costForNextLevel(currentLevel);
            List<String> lore = new ArrayList<>();
            lore.addAll(splitAbilityInfoLore(ability.longDescription()));
            lore.add(ChatColor.LIGHT_PURPLE + "Current Level: " + currentLevel + "/3");
            lore.add(ChatColor.YELLOW + "Next upgrade cost: " + nextCost + " hearts");
            lore.add(ChatColor.DARK_GRAY + "Upgrade buttons: L1/L2/L3");
            info = createItem(
                    Material.BOOK,
                    ChatColor.AQUA + ability.name(),
                    lore
            );
        }

        for (int slot : INFO_SLOTS) {
            inventory.setItem(slot, info);
        }
    }

    private List<String> splitAbilityInfoLore(String longDescription) {
        List<String> lore = new ArrayList<>();
        if (longDescription == null || longDescription.isBlank()) {
            return lore;
        }
        String[] parts = ABILITY_INFO_SPLIT.split(longDescription.trim());
        for (String raw : parts) {
            String line = raw == null ? "" : raw.trim();
            if (!line.isEmpty()) {
                lore.add(ChatColor.GRAY + line);
            }
        }
        if (lore.isEmpty()) {
            lore.add(ChatColor.GRAY + longDescription.trim());
        }
        return lore;
    }

    private boolean canAffordInInventory(Player player, int cost) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (itemIdentityService.isHeartItem(stack)) {
                total += stack.getAmount();
                if (total >= cost) {
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack createClickableBlankItem(String name, List<String> lore) {
        ItemStack item = itemIdentityService.createCustomItem("itemsadder-gui:blank", 1);
        if (item == null) {
            item = createItem(Material.PAPER, name, lore);
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int slotToLevel(int slot) {
        if (contains(LEVEL_1_SLOTS, slot)) {
            return 1;
        }
        if (contains(LEVEL_2_SLOTS, slot)) {
            return 2;
        }
        if (contains(LEVEL_3_SLOTS, slot)) {
            return 3;
        }
        return -1;
    }

    private boolean contains(int[] slots, int slot) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private int[] slotsForLevel(int level) {
        return switch (level) {
            case 1 -> LEVEL_1_SLOTS;
            case 2 -> LEVEL_2_SLOTS;
            case 3 -> LEVEL_3_SLOTS;
            default -> new int[0];
        };
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = itemIdentityService.createCustomItem("itemsadder-gui:blank", 1);
        if (item == null) {
            item = new ItemStack(material);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
