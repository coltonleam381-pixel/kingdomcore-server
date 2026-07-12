package com.yourorg.kingdomcore.commands;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;

/**
 * OP-only test command to spawn a stationary, pushable combat dummy.
 */
public class PDummyCommand implements CommandExecutor {
    public static final String DUMMY_TAG = "kc_pdummy";

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

        int protectionLevel = 0;
        if (args.length >= 2 && "prot".equalsIgnoreCase(args[0])) {
            try {
                protectionLevel = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                protectionLevel = 0;
            }
            protectionLevel = Math.max(1, Math.min(4, protectionLevel));
        } else if (args.length > 0) {
            player.sendMessage("§cUsage: /pdummy or /pdummy prot <1-4>");
            return true;
        }

        Zombie dummy = (Zombie) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        dummy.setAdult();
        dummy.setAI(true); // must stay true so knockback works normally
        dummy.setCollidable(true); // can be pushed
        dummy.setCanPickupItems(false);
        dummy.setSilent(true);
        dummy.setPersistent(true);
        dummy.setRemoveWhenFarAway(false);
        dummy.setConversionTime(-1);
        dummy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 10, false, false));
        dummy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 10, false, false));
        dummy.addScoreboardTag(DUMMY_TAG);

        AttributeInstance maxHealth = dummy.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0); // player-like hearts
        }
        dummy.setHealth(20.0);
        updateDummyName(dummy);

        if (dummy.getEquipment() != null) {
            dummy.getEquipment().setHelmet(makeArmor(Material.DIAMOND_HELMET, protectionLevel));
            dummy.getEquipment().setChestplate(makeArmor(Material.DIAMOND_CHESTPLATE, protectionLevel));
            dummy.getEquipment().setLeggings(makeArmor(Material.DIAMOND_LEGGINGS, protectionLevel));
            dummy.getEquipment().setBoots(makeArmor(Material.DIAMOND_BOOTS, protectionLevel));
            dummy.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            dummy.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
        }

        if (protectionLevel > 0) {
            player.sendMessage("§aSpawned P Dummy with diamond Prot " + protectionLevel + ".");
        } else {
            player.sendMessage("§aSpawned P Dummy.");
        }
        return true;
    }

    public static void updateDummyName(Zombie dummy) {
        double hearts = Math.max(0.0, dummy.getHealth() / 2.0);
        String heartText = (Math.floor(hearts) == hearts)
                ? Integer.toString((int) hearts)
                : String.format(java.util.Locale.US, "%.1f", hearts);
        var effects = dummy.getActivePotionEffects().stream()
                // Keep baseline dummy-control effects hidden, but show all real combat effects.
                .filter(effect -> !isBaselineControlEffect(effect))
                .sorted(Comparator.comparing(effect -> effect.getType().getKey().getKey()))
                .toList();

        StringBuilder effectText = new StringBuilder();
        if (!effects.isEmpty()) {
            effectText.append(" §8| §b");
            for (int i = 0; i < effects.size(); i++) {
                PotionEffect effect = effects.get(i);
                if (i > 0) {
                    effectText.append(" §7, §b");
                }
                String name = effect.getType().getKey().getKey();
                int level = effect.getAmplifier() + 1;
                double sec = Math.max(0.0, effect.getDuration() / 20.0);
                effectText.append(name).append(" ").append(level).append(" ")
                        .append(String.format(java.util.Locale.US, "%.1fs", sec));
            }
        }

        dummy.setCustomName("§e§lP Dummy §7| §c" + heartText + "❤" + effectText);
        dummy.setCustomNameVisible(true);
    }

    private static boolean isBaselineControlEffect(PotionEffect effect) {
        if (effect.getDuration() < 1_000_000) {
            return false;
        }
        PotionEffectType type = effect.getType();
        int amp = effect.getAmplifier();
        return (type == PotionEffectType.SLOWNESS || type == PotionEffectType.WEAKNESS) && amp >= 10;
    }

    private static ItemStack makeArmor(Material material, int protectionLevel) {
        ItemStack item = new ItemStack(material);
        if (protectionLevel > 0) {
            item.addUnsafeEnchantment(Enchantment.PROTECTION, protectionLevel);
        }
        return item;
    }
}
