package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class ChooseAbilityCommand implements CommandExecutor {
    private final AbilityService abilityService;
    private final AbilityOwnershipService abilityOwnershipService;
    private final HeartService heartService;

    public ChooseAbilityCommand(AbilityService abilityService,
                                AbilityOwnershipService abilityOwnershipService,
                                HeartService heartService) {
        this.abilityService = abilityService;
        this.abilityOwnershipService = abilityOwnershipService;
        this.heartService = heartService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String targetName;
        UUID targetId;
        String abilityRaw;

        if (sender instanceof Player playerSender) {
            if (args.length != 1) {
                hud(playerSender, "§cUsage: /chooseability <ability_id>");
                return true;
            }
            targetId = playerSender.getUniqueId();
            targetName = playerSender.getName();
            abilityRaw = args[0];

            AbilityDefinition ability = resolveAbility(abilityRaw);
            if (ability == null) {
                hud(playerSender, "§cUnknown ability: §f" + abilityRaw);
                return true;
            }

            PlayerState state = heartService.getOrCreateState(targetId, targetName);
            String currentAbilityId = state.getAbilityId();
            if (currentAbilityId != null && !currentAbilityId.isBlank()) {
                if (abilityService.getAbility(currentAbilityId) != null) {
                    AbilityDefinition current = abilityService.getAbility(currentAbilityId);
                    String currentName = current != null ? current.name() : currentAbilityId;
                    playerSender.sendTitle("§cAlready chosen", "§f" + currentName, 5, 35, 10);
                    playerSender.sendActionBar("§7You already have §f" + currentName + "§7.");
                    playerSender.playSound(playerSender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
                    return true;
                }
                abilityOwnershipService.resetPlayer(targetId);
            }

            Optional<UUID> owner = abilityOwnershipService.getOwner(ability.id());
            if (abilityOwnershipService.isAbilityTakenByOther(targetId, ability.id())) {
                showAbilityTaken(playerSender);
                return true;
            }

            if (!abilityOwnershipService.claimAbility(targetId, ability.id())) {
                showAbilityTaken(playerSender);
                return true;
            }

            playerSender.sendTitle("§aWelcome", "§f" + ability.name(), 10, 50, 10);
            playerSender.playSound(playerSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.15f);
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("Usage: /chooseability <player> <ability_id>");
            return true;
        }

        targetName = args[0];
        abilityRaw = args[1];

        Player resolved = resolveOnlinePlayer(targetName);
        if (resolved != null) {
            targetId = resolved.getUniqueId();
            targetName = resolved.getName();
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target.getUniqueId() == null) {
                sender.sendMessage("Unknown player: " + targetName);
                return true;
            }
            targetId = target.getUniqueId();
            if (target.getName() != null) {
                targetName = target.getName();
            }
        }

        Player onlineTarget = Bukkit.getPlayer(targetId);

        AbilityDefinition ability = resolveAbility(abilityRaw);
        if (ability == null) {
            notifyPlayer(onlineTarget, sender, "§cUnknown ability: §f" + abilityRaw);
            return true;
        }

        PlayerState state = heartService.getOrCreateState(targetId, targetName);
        String currentAbilityId = state.getAbilityId();
        if (currentAbilityId != null && !currentAbilityId.isBlank()) {
            if (abilityService.getAbility(currentAbilityId) != null) {
                AbilityDefinition current = abilityService.getAbility(currentAbilityId);
                String currentName = current != null ? current.name() : currentAbilityId;
                if (onlineTarget != null) {
                    onlineTarget.sendTitle("§cAlready chosen", "§f" + currentName, 5, 35, 10);
                    onlineTarget.sendActionBar("§7You already have §f" + currentName + "§7.");
                    onlineTarget.playSound(onlineTarget.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
                } else {
                    sender.sendMessage("Player already has ability: " + currentAbilityId);
                }
                return true;
            }
            abilityOwnershipService.resetPlayer(targetId);
        }

        boolean assigned = abilityOwnershipService.forceAssignAbility(targetId, ability.id());
        if (!assigned) {
            if (onlineTarget != null) {
                showAbilityTaken(onlineTarget);
            } else {
                sender.sendMessage("Could not assign ability " + ability.id() + " to " + targetName);
            }
            return true;
        }

        if (onlineTarget != null) {
            onlineTarget.sendTitle("§aWelcome", "§f" + ability.name(), 10, 50, 10);
            onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.15f);
        } else {
            sender.sendMessage("Assigned " + ability.id() + " to " + targetName + " (offline).");
        }

        return true;
    }

    private static void showAbilityTaken(Player player) {
        player.sendTitle("§cAbility Taken", "", 5, 35, 10);
        player.sendActionBar("§cAbility Taken.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
    }

    private static void notifyPlayer(Player onlineTarget, CommandSender sender, String message) {
        if (onlineTarget != null) {
            hud(onlineTarget, message);
        } else {
            sender.sendMessage(message.replace('§', '&'));
        }
    }

    private static void hud(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(message));
    }

    private static Player resolveOnlinePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    private AbilityDefinition resolveAbility(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = normalizeAbilityId(raw);
        AbilityDefinition direct = abilityService.getAbility(normalized);
        if (direct != null) {
            return direct;
        }

        String compactInput = compact(raw);
        for (AbilityDefinition candidate : abilityService.getAllAbilities()) {
            if (candidate == null || candidate.id() == null || candidate.name() == null) {
                continue;
            }
            if (compact(candidate.id()).equals(compactInput) || compact(candidate.name()).equals(compactInput)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeAbilityId(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        return switch (s) {
            case "atlant" -> "atlantis";
            case "heartshield" -> "heart_shield";
            case "ice_nova", "icenova" -> "ice_nova";
            case "phase_step", "phasestep" -> "phase_step";
            case "phase_walk", "phasewalk" -> "phase_walk";
            case "sanguine_fog", "sanguinefog" -> "sanguine_fog";
            case "sky_breaker" -> "skybreaker";
            default -> s;
        };
    }

    private String compact(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
