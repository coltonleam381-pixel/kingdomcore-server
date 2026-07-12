package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.BountyService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.api.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.yourorg.kingdomcore.util.NpcOnlyCommands;

import java.util.*;

public class BountyCommand implements CommandExecutor, Listener {
    private final BountyService bountyService;
    private final PlayerStateRepository playerStateRepository;
    private final HeartService heartService;
    private final com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService;
    private final Map<UUID, UUID> viewingBounty = new HashMap<>();
    private final Map<UUID, Integer> pendingBounty = new HashMap<>();
    private final Map<UUID, Integer> viewingPage = new HashMap<>();
    private final Map<UUID, Integer> pickerPage = new HashMap<>();

    public BountyCommand(BountyService bountyService, PlayerStateRepository playerStateRepository, HeartService heartService, com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService) {
        this.bountyService = bountyService;
        this.playerStateRepository = playerStateRepository;
        this.heartService = heartService;
        this.itemIdentityService = itemIdentityService;
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
                sender.sendMessage("Usage: /bounty391381 <player>");
                return true;
            }
            player = Bukkit.getPlayerExact(args[0]);
            if (player == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        }
        openMainMenu(player, 0);
        return true;
    }

    private void openMainMenu(Player player, int page) {
        List<PlayerState> allPlayers = new ArrayList<>();
        Map<UUID, Integer> activeBounties = bountyService.getAllBounties();
        for (Map.Entry<UUID, Integer> entry : activeBounties.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            playerStateRepository.findById(entry.getKey()).ifPresent(allPlayers::add);
        }

        allPlayers.sort((a, b) -> Integer.compare(
                bountyService.getBounty(b.getUuid()),
                bountyService.getBounty(a.getUuid())
        ));

        int totalPages = Math.max(1, (int) Math.ceil(allPlayers.size() / 45.0));
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "💀 Wanted Players - Page " + (page + 1));
        viewingPage.put(player.getUniqueId(), page);

        if (allPlayers.isEmpty()) {
            ItemStack empty = createItem(Material.BARRIER, "§7No active bounties",
                    "§7No one has a bounty right now.");
            inv.setItem(22, empty);
            inv.setItem(40, createItem(Material.EMERALD, "§aPlace Bounty",
                    "§7Choose a player to put a bounty on."));
            player.openInventory(inv);
            viewingBounty.remove(player.getUniqueId());
            pendingBounty.remove(player.getUniqueId());
            return;
        }

        int start = page * 45;
        int end = Math.min(start + 45, allPlayers.size());

        for (int i = start; i < end; i++) {
            PlayerState state = allPlayers.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(state.getUuid()));
            meta.setDisplayName("§c" + state.getLastName());
            int currentBounty = bountyService.getBounty(state.getUuid());
            meta.setLore(List.of("§7Current Bounty: §e" + currentBounty + " Hearts", "§eClick to add bounty!"));
            head.setItemMeta(meta);
            inv.setItem(i - start, head);
        }

        if (page > 0) {
            ItemStack prev = createItem(Material.ARROW, "§aPrevious Page");
            inv.setItem(45, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = createItem(Material.ARROW, "§aNext Page");
            inv.setItem(53, next);
        }

        inv.setItem(49, createItem(Material.EMERALD, "§aPlace Bounty",
                "§7Choose a player to put a bounty on."));

        player.openInventory(inv);
        viewingBounty.remove(player.getUniqueId());
        pendingBounty.remove(player.getUniqueId());
    }

    private void openPlayerPicker(Player player, int page) {
        List<PlayerState> candidates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            playerStateRepository.findById(online.getUniqueId()).ifPresent(state -> {
                if (seen.add(state.getUuid())) {
                    candidates.add(state);
                }
            });
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() == null || offline.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            playerStateRepository.findById(offline.getUniqueId()).ifPresent(state -> {
                if (seen.add(state.getUuid())) {
                    candidates.add(state);
                }
            });
        }
        candidates.sort(Comparator.comparing(PlayerState::getLastName, String.CASE_INSENSITIVE_ORDER));

        int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / 45.0));
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "💀 Choose Target - Page " + (page + 1));
        pickerPage.put(player.getUniqueId(), page);

        int start = page * 45;
        int end = Math.min(start + 45, candidates.size());
        for (int i = start; i < end; i++) {
            PlayerState state = candidates.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(state.getUuid()));
            meta.setDisplayName("§c" + state.getLastName());
            int currentBounty = bountyService.getBounty(state.getUuid());
            if (currentBounty > 0) {
                meta.setLore(List.of("§7Current Bounty: §e" + currentBounty + " Hearts", "§eClick to add more!"));
            } else {
                meta.setLore(List.of("§7No bounty yet", "§eClick to place bounty!"));
            }
            head.setItemMeta(meta);
            inv.setItem(i - start, head);
        }

        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§aPrevious Page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "§aNext Page"));
        }
        inv.setItem(49, createItem(Material.BARRIER, "§cBack", "§7Return to wanted list."));
        player.openInventory(inv);
    }

    private void openBountyMenu(Player player, UUID targetId, String targetName) {
        viewingBounty.put(player.getUniqueId(), targetId);
        int currentBounty = bountyService.getBounty(targetId);
        int pending = pendingBounty.getOrDefault(player.getUniqueId(), 0);

        Inventory inv = Bukkit.createInventory(null, 27, "§8Bounty: §c" + targetName);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetId));
        meta.setDisplayName("§c" + targetName);
        meta.setLore(List.of("§7Total Bounty: §e" + currentBounty + " Hearts"));
        head.setItemMeta(meta);
        inv.setItem(13, head);

        ItemStack addHeart = createItem(Material.RED_DYE, "§a+1 Heart", "§7Click to stage 1 heart", "§7Pending: §e" + pending);
        inv.setItem(11, addHeart);

        ItemStack pay = createItem(Material.GOLD_INGOT, "§ePay Bounty", "§7Total to pay: §e" + pending + " Hearts", "§7Will take from inventory", "§7or max health.");
        inv.setItem(15, pay);

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith("💀 Wanted Players")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            
            int page = viewingPage.getOrDefault(player.getUniqueId(), 0);
            if (event.getRawSlot() == 45 && event.getCurrentItem().getType() == Material.ARROW) {
                openMainMenu(player, page - 1);
                return;
            }
            if (event.getRawSlot() == 53 && event.getCurrentItem().getType() == Material.ARROW) {
                openMainMenu(player, page + 1);
                return;
            }
            if (event.getRawSlot() == 40 || event.getRawSlot() == 49) {
                if (event.getCurrentItem().getType() == Material.EMERALD) {
                    openPlayerPicker(player, 0);
                    return;
                }
            }

            if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    if (meta.getOwningPlayer().getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage("§cYou cannot place a bounty on yourself!");
                        return;
                    }
                    openBountyMenu(player, meta.getOwningPlayer().getUniqueId(), meta.getOwningPlayer().getName());
                }
            }
            return;
        }

        if (title.startsWith("💀 Choose Target")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            int page = pickerPage.getOrDefault(player.getUniqueId(), 0);
            if (event.getRawSlot() == 45 && event.getCurrentItem().getType() == Material.ARROW) {
                openPlayerPicker(player, page - 1);
                return;
            }
            if (event.getRawSlot() == 53 && event.getCurrentItem().getType() == Material.ARROW) {
                openPlayerPicker(player, page + 1);
                return;
            }
            if (event.getRawSlot() == 49 && event.getCurrentItem().getType() == Material.BARRIER) {
                openMainMenu(player, 0);
                return;
            }
            if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    openBountyMenu(player, meta.getOwningPlayer().getUniqueId(), meta.getOwningPlayer().getName());
                }
            }
            return;
        }

        if (title.startsWith("§8Bounty: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            UUID targetId = viewingBounty.get(player.getUniqueId());
            if (targetId == null) return;

            if (event.getRawSlot() == 11) { // Add heart
                int current = pendingBounty.getOrDefault(player.getUniqueId(), 0);
                pendingBounty.put(player.getUniqueId(), current + 1);
                openBountyMenu(player, targetId, Bukkit.getOfflinePlayer(targetId).getName());
            } else if (event.getRawSlot() == 15) { // Pay
                int pending = pendingBounty.getOrDefault(player.getUniqueId(), 0);
                if (pending <= 0) return;

                boolean success = withdrawPlayerHearts(player, pending);
                if (!success) {
                    player.sendMessage("§cYou don't have enough hearts or max health to pay this bounty!");
                    return;
                }

                bountyService.addBounty(targetId, pending);
                pendingBounty.put(player.getUniqueId(), 0);
                String targetName = Bukkit.getOfflinePlayer(targetId).getName();
                
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    p.sendMessage("§c§lBOUNTY! §e" + player.getName() + " §7has increased the bounty on §c" + targetName + " §7by §e" + pending + " Hearts!");
                }
                openBountyMenu(player, targetId, targetName);
            }
        }
    }

    private boolean withdrawPlayerHearts(Player player, int amount) {
        // Take from inventory or max health
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && itemIdentityService.isHeartItem(item)) {
                count += item.getAmount();
            }
        }
        
        if (count >= amount) {
            int leftToTake = amount;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && itemIdentityService.isHeartItem(item)) {
                    if (item.getAmount() <= leftToTake) {
                        leftToTake -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - leftToTake);
                        leftToTake = 0;
                    }
                    if (leftToTake <= 0) break;
                }
            }
            return true;
        }

        PlayerState state = playerStateRepository.findById(player.getUniqueId()).orElse(null);
        if (state == null || state.getProgressionHearts() < amount) {
            return false;
        }
        heartService.removeHearts(player, amount);
        return true;
    }
}
