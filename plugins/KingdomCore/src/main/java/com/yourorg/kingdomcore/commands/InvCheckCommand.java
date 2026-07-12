package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.util.OfflinePlayerDataLoader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class InvCheckCommand implements CommandExecutor, TabCompleter, Listener {

    private final Plugin plugin;
    private final HeartService heartService;
    private final HealthService healthService;
    private final ItemIdentityService itemIdentityService;
    private final AbilityService abilityService;
    private final KingdomConfig config;
    private final PlayerStateRepository playerStateRepository;

    public InvCheckCommand(Plugin plugin,
                           HeartService heartService,
                           HealthService healthService,
                           ItemIdentityService itemIdentityService,
                           AbilityService abilityService,
                           KingdomConfig config,
                           PlayerStateRepository playerStateRepository) {
        this.plugin = plugin;
        this.heartService = heartService;
        this.healthService = healthService;
        this.itemIdentityService = itemIdentityService;
        this.abilityService = abilityService;
        this.config = config;
        this.playerStateRepository = playerStateRepository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!canUse(viewer)) {
            viewer.sendMessage("§cYou cannot use this command.");
            return true;
        }
        if (args.length != 1) {
            viewer.sendMessage("§cUsage: /" + label.toLowerCase(Locale.ROOT) + " <player>");
            return true;
        }

        Optional<InspectTarget> target = resolveTarget(args[0]);
        if (target.isEmpty()) {
            viewer.sendMessage("§cPlayer not found.");
            return true;
        }

        inspectPlayer(viewer, target.get());
        return true;
    }

    private boolean canUse(Player player) {
        List<String> names = plugin.getConfig().getStringList("inv-check.allowed-players");
        if (names == null || names.isEmpty()) {
            names = List.of("GameAxion");
        }
        String viewer = player.getName().toLowerCase(Locale.ROOT);
        for (String allowed : names) {
            if (allowed != null && allowed.toLowerCase(Locale.ROOT).equals(viewer)) {
                return true;
            }
        }
        return false;
    }

    private Optional<InspectTarget> resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return Optional.of(new InspectTarget(online.getUniqueId(), online.getName(), online));
        }

        Optional<PlayerState> byName = playerStateRepository.findByLastNameIgnoreCase(input);
        if (byName.isPresent()) {
            PlayerState state = byName.get();
            String name = state.getLastName() == null || state.getLastName().isBlank() ? input : state.getLastName();
            return Optional.of(new InspectTarget(state.getUuid(), name, null));
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline.getName() != null || offline.hasPlayedBefore()) {
            String name = offline.getName() != null ? offline.getName() : input;
            return Optional.of(new InspectTarget(offline.getUniqueId(), name, null));
        }

        return Optional.empty();
    }

    private void inspectPlayer(Player viewer, InspectTarget target) {
        sendHeartReport(viewer, target);
        openInspectInventory(viewer, target);
    }

    private void sendHeartReport(Player viewer, InspectTarget target) {
        PlayerState state = heartService.getOrCreateState(target.uuid(), target.displayName());
        ItemStack helmet;
        ItemStack[] inventoryContents;
        Double currentHealth;

        if (target.online() != null) {
            Player live = target.online();
            helmet = live.getInventory().getHelmet();
            inventoryContents = live.getInventory().getContents();
            currentHealth = live.getHealth();
        } else {
            Optional<OfflinePlayerDataLoader.OfflineInventorySnapshot> offlineInventory =
                    OfflinePlayerDataLoader.loadInventory(target.uuid());
            if (offlineInventory.isPresent()) {
                OfflinePlayerDataLoader.OfflineInventorySnapshot snapshot = offlineInventory.get();
                helmet = snapshot.helmet();
                inventoryContents = snapshot.allContents();
                currentHealth = snapshot.health();
            } else {
                helmet = null;
                inventoryContents = new ItemStack[0];
                currentHealth = null;
            }
        }

        double maxHealth = target.online() != null
                ? healthService.resolveMaxHealth(target.online(), state)
                : resolveMaxHealthOffline(state, helmet);
        boolean crownWorn = itemIdentityService.isCrownItem(helmet);
        int inventoryHearts = countHeartItems(inventoryContents);
        int progressionCap = config.getProgressionMaxHearts() + state.getAssassinWinBonus();

        viewer.sendMessage("§6§l=== " + target.displayName() + " Hearts ===");
        if (target.online() == null) {
            viewer.sendMessage("§7Status: §eOffline");
        }
        viewer.sendMessage("§7Progression hearts: §c" + state.getProgressionHearts()
                + "§7 / §c" + progressionCap);
        if (currentHealth != null) {
            viewer.sendMessage("§7Saved health: §c" + formatHp(currentHealth)
                    + "§7 / §c" + formatHp(maxHealth) + " HP");
        } else if (target.online() == null) {
            viewer.sendMessage("§7Saved health: §8Unknown (no player.dat)");
        }
        viewer.sendMessage("§7Hearts in inventory: §c" + inventoryHearts);
        viewer.sendMessage("§7Ability: §f" + formatAbility(state));
        viewer.sendMessage("§7Crown bonus: " + (crownWorn
                ? "§aActive (+" + config.getCrownBonusHearts() + " hearts)"
                : "§7Not worn"));
        if (state.getAssassinWinBonus() > 0) {
            viewer.sendMessage("§7Assassin win bonus: §c+" + state.getAssassinWinBonus() + " heart cap");
        }
        viewer.sendMessage("§7Blocked (0 hearts): " + (state.isBlocked() ? "§cYes" : "§aNo"));
    }

    private double resolveMaxHealthOffline(PlayerState state, ItemStack helmet) {
        double maxHealth = Math.max(2.0, state.getProgressionHearts() * 2.0);
        boolean crownWorn = itemIdentityService.isCrownItem(helmet);
        if (crownWorn) {
            maxHealth += config.getCrownBonusHearts() * 2.0;
        }
        int cap = config.getProgressionMaxHearts() + state.getAssassinWinBonus();
        if (crownWorn) {
            cap += config.getCrownBonusHearts();
        }
        return Math.min(maxHealth, cap * 2.0);
    }

    private String formatAbility(PlayerState state) {
        String abilityId = state.getAbilityId();
        if (abilityId == null || abilityId.isBlank()) {
            return "None";
        }
        AbilityDefinition ability = abilityService.getAbility(abilityId);
        String name = ability != null ? ability.name() : abilityId;
        return name + " §7(Lv " + state.getAbilityLevel() + ")";
    }

    private int countHeartItems(ItemStack[] contents) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (itemIdentityService.isHeartItem(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static String formatHp(double hp) {
        if (Math.rint(hp) == hp) {
            return String.valueOf((int) hp);
        }
        return String.format(Locale.ROOT, "%.1f", hp);
    }

    private void openInspectInventory(Player viewer, InspectTarget target) {
        InvCheckHolder holder = new InvCheckHolder(target.uuid(), target.displayName(), target.online() == null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                "§6§l" + target.displayName() + (target.online() == null ? " §7(offline)" : "") + "'s Inv");
        holder.setInventory(inventory);

        if (target.online() != null) {
            fillFromLiveInventory(inventory, target.online().getInventory());
        } else {
            Optional<OfflinePlayerDataLoader.OfflineInventorySnapshot> offlineInventory =
                    OfflinePlayerDataLoader.loadInventory(target.uuid());
            if (offlineInventory.isPresent()) {
                fillFromOfflineSnapshot(inventory, offlineInventory.get());
            } else {
                viewer.sendMessage("§eNo saved inventory found for §6" + target.displayName() + "§e.");
            }
        }

        ItemStack filler = namedPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 41; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }

        viewer.openInventory(inventory);
        viewer.sendMessage("§7Viewing §6" + target.displayName() + "§7's inventory (read-only).");
    }

    private void fillFromLiveInventory(Inventory inventory, PlayerInventory targetInventory) {
        ItemStack[] storage = targetInventory.getStorageContents();
        for (int slot = 0; slot < storage.length && slot < 36; slot++) {
            inventory.setItem(slot, cloneOrNull(storage[slot]));
        }

        inventory.setItem(36, cloneOrNull(targetInventory.getHelmet()));
        inventory.setItem(37, cloneOrNull(targetInventory.getChestplate()));
        inventory.setItem(38, cloneOrNull(targetInventory.getLeggings()));
        inventory.setItem(39, cloneOrNull(targetInventory.getBoots()));
        inventory.setItem(40, cloneOrNull(targetInventory.getItemInOffHand()));
    }

    private void fillFromOfflineSnapshot(Inventory inventory, OfflinePlayerDataLoader.OfflineInventorySnapshot snapshot) {
        ItemStack[] storage = snapshot.storage();
        for (int slot = 0; slot < storage.length && slot < 36; slot++) {
            inventory.setItem(slot, cloneOrNull(storage[slot]));
        }
        inventory.setItem(36, cloneOrNull(snapshot.helmet()));
        inventory.setItem(37, cloneOrNull(snapshot.chestplate()));
        inventory.setItem(38, cloneOrNull(snapshot.leggings()));
        inventory.setItem(39, cloneOrNull(snapshot.boots()));
        inventory.setItem(40, cloneOrNull(snapshot.offHand()));
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    private static ItemStack namedPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!canUse(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvCheckHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!canUse(player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof InvCheckHolder) {
            event.setCancelled(true);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player) || !canUse(player)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        List<String> matches = new ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix) && seen.add(online.getName().toLowerCase(Locale.ROOT))) {
                matches.add(online.getName());
            }
        }
        for (String name : playerStateRepository.findAllLastNames()) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix) && seen.add(name.toLowerCase(Locale.ROOT))) {
                matches.add(name);
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix) && seen.add(name.toLowerCase(Locale.ROOT))) {
                matches.add(name);
            }
        }
        return matches;
    }

    private record InspectTarget(UUID uuid, String displayName, Player online) {
    }

    public static final class InvCheckHolder implements InventoryHolder {
        private final UUID targetId;
        private final String targetName;
        private final boolean offline;
        private Inventory inventory;

        public InvCheckHolder(UUID targetId, String targetName, boolean offline) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.offline = offline;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public String getTargetName() {
            return targetName;
        }

        public boolean isOffline() {
            return offline;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
