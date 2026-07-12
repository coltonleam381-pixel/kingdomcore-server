package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.abilities.AbilityScoreboard;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.WithdrawResult;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.util.WithdrawFeedback;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class WithdrawCommand implements CommandExecutor {
    private final KingdomConfig config;
    private final HeartService heartService;
    private final AbilityScoreboard abilityScoreboard;
    private final PlayerStateRepository playerStateRepository;
    private final Plugin plugin;

    public WithdrawCommand(KingdomConfig config,
                           HeartService heartService,
                           AbilityScoreboard abilityScoreboard,
                           PlayerStateRepository playerStateRepository,
                           Plugin plugin) {
        this.config = config;
        this.heartService = heartService;
        this.abilityScoreboard = abilityScoreboard;
        this.playerStateRepository = playerStateRepository;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!config.isWithdrawEnabled()) {
            player.sendMessage("§cWithdraw is disabled.");
            return true;
        }

        if (args.length == 1) {
            return withdrawSelf(player, args[0]);
        }
        if (args.length == 2) {
            if ("heart".equalsIgnoreCase(args[0]) || "hearts".equalsIgnoreCase(args[0])) {
                return withdrawSelf(player, args[1]);
            }
            if (canStealFromOthers(player)) {
                return withdrawFromPlayer(player, args[0], args[1]);
            }
            player.sendMessage("§cUsage: /withdraw <amount> | /withdraw <player> <amount>");
            return true;
        }

        player.sendMessage("§cUsage: /withdraw <amount> | /withdraw <player> <amount>");
        return true;
    }

    private boolean withdrawSelf(Player player, String amountArg) {
        int amount = parseAmount(amountArg);
        if (amount <= 0) {
            player.sendMessage("§cAmount must be a positive number.");
            return true;
        }
        WithdrawResult result = heartService.withdrawHearts(player, amount);
        WithdrawFeedback.apply(player, abilityScoreboard, result, amount);
        return true;
    }

    private boolean withdrawFromPlayer(Player receiver, String targetName, String amountArg) {
        int amount = parseAmount(amountArg);
        if (amount <= 0) {
            receiver.sendMessage("§cAmount must be a positive number.");
            return true;
        }

        Optional<WithdrawTarget> target = resolveTarget(targetName);
        if (target.isEmpty()) {
            receiver.sendMessage("§cPlayer not found: §f" + targetName);
            return true;
        }

        WithdrawTarget victim = target.get();
        WithdrawResult result = heartService.withdrawHeartsFrom(victim.uuid(), victim.name(), receiver, amount);
        WithdrawFeedback.apply(receiver, abilityScoreboard, result, amount);
        if (result == WithdrawResult.SUCCESS) {
            receiver.sendMessage("§aWithdrew §f" + amount + " §aheart" + (amount == 1 ? "" : "s")
                    + " from §f" + victim.name() + "§a.");
            Player victimOnline = Bukkit.getPlayer(victim.uuid());
            if (victimOnline != null && victimOnline.isOnline() && !victimOnline.equals(receiver)) {
                victimOnline.sendMessage("§c§f" + amount + " §cheart" + (amount == 1 ? " was" : "s were")
                        + " withdrawn by §f" + receiver.getName() + "§c.");
            }
        }
        return true;
    }

    private Optional<WithdrawTarget> resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return Optional.of(new WithdrawTarget(online.getUniqueId(), online.getName()));
        }

        Optional<PlayerState> byName = playerStateRepository.findByLastNameIgnoreCase(input);
        if (byName.isPresent()) {
            PlayerState state = byName.get();
            String name = state.getLastName() == null || state.getLastName().isBlank() ? input : state.getLastName();
            return Optional.of(new WithdrawTarget(state.getUuid(), name));
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline.getName() != null || offline.hasPlayedBefore()) {
            String name = offline.getName() != null ? offline.getName() : input;
            return Optional.of(new WithdrawTarget(offline.getUniqueId(), name));
        }
        return Optional.empty();
    }

    private boolean canStealFromOthers(Player player) {
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

    private static int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private record WithdrawTarget(UUID uuid, String name) {
    }
}
