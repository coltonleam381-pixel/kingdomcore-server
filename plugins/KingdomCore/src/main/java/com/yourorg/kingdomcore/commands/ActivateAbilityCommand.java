package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.abilities.AtlantisAbility;
import com.yourorg.kingdomcore.abilities.ThorAbility;
import com.yourorg.kingdomcore.abilities.ZeusAbility;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OP-only test command that applies ability impact to the executor as a target (enemy perspective).
 * Usage: /activateability <ability_id> [level]
 */
public class ActivateAbilityCommand implements CommandExecutor {
    private final KingdomCorePlugin plugin;

    public ActivateAbilityCommand(KingdomCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage("You must be OP to use this command.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("Usage: /activateability <ability_id> [level]");
            return true;
        }

        String ability = args[0].toLowerCase(Locale.ROOT);
        int level = 1;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        }
        level = Math.max(1, Math.min(3, level));

        Location src = player.getLocation().clone().add(0, 1.0, 0);
        switch (ability) {
            case "sang_fog", "sanguine_fog", "sanguinefog" -> applySanguineFogAsEnemy(player, level, src);
            case "hulk" -> applyHulkImpact(player, level, src);
            case "thor" -> applyThorImpact(player, level, src);
            case "zeus" -> applyZeusImpact(player, level, src);
            case "skybreaker" -> applySkybreakerImpact(player, level, src);
            case "atlantis" -> applyAtlantisImpact(player, level, src);
            default -> {
                player.sendMessage("Unknown ability id for test: " + ability);
                return true;
            }
        }

        player.sendActionBar("§7Tested as enemy: §f" + ability + " §7L" + level);
        return true;
    }

    private void applySanguineFogAsEnemy(Player player, int level, Location source) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
        if (level >= 2) {
            float randomYaw = ThreadLocalRandom.current().nextFloat() * 360f;
            Location loc = player.getLocation().clone();
            loc.setYaw(randomYaw);
            player.teleport(loc);
        }
        if (level >= 3) {
            plugin.getDamageService().applyTrueDamage(player, null, 0.6, source);
        }
    }

    private void applyHulkImpact(Player player, int level, Location source) {
        double damage = switch (level) {
            case 1 -> 4.0;
            case 2 -> 6.0;
            case 3 -> 8.0;
            default -> 4.0;
        };
        plugin.getDamageService().applyTrueDamage(player, null, damage, source, 0.45);
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
        }
    }

    private void applyThorImpact(Player player, int level, Location source) {
        double damage = ThorAbility.strikeDamageForLevel(level);
        plugin.getDamageService().applyTrueDamage(player, null, damage, source, 0.3);
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
        }
    }

    private void applyZeusImpact(Player player, int level, Location source) {
        plugin.getDamageService().applyTrueDamage(player, null, ZeusAbility.damagePerStrikeForLevel(level), source, 0.2);
        if (ZeusAbility.appliesSlownessOnHit(level)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
        }
    }

    private void applySkybreakerImpact(Player player, int level, Location source) {
        double damage = switch (level) {
            case 1 -> 4.0;
            case 2 -> 5.0;
            case 3 -> 6.0;
            default -> 4.0;
        };
        plugin.getDamageService().applyTrueDamage(player, null, damage, source, 0.4);
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false));
        }
    }

    private void applyAtlantisImpact(Player player, int level, Location source) {
        double damage = AtlantisAbility.wallDamageForLevel(level);
        plugin.getDamageService().applyTrueDamage(player, null, damage, source, 0.45);
    }
}
