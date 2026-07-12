package com.yourorg.kingdomcore.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Queues villager spawn eggs on restart and silently delivers them on next login. */
public final class VillagerEggGrantService {
    private final JavaPlugin plugin;
    private final Path pendingFile;
    private Map<UUID, Integer> pending = new HashMap<>();

    public VillagerEggGrantService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pendingFile = plugin.getDataFolder().toPath().resolve("villager-egg-pending.txt");
    }

    public void queueFromConfig() {
        if (!plugin.getConfig().getBoolean("restart-villager-eggs.enabled", false)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("restart-villager-eggs.queue-on-startup", true)) {
            return;
        }

        loadPending();
        var section = plugin.getConfig().getConfigurationSection("restart-villager-eggs.players");
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            int amount = section.getInt(name, 0);
            if (amount <= 0) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            pending.put(offline.getUniqueId(), amount);
        }
        savePending();
    }

    public void deliverIfPending(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        loadPending();
        Integer amount = pending.remove(player.getUniqueId());
        if (amount == null || amount <= 0) {
            return;
        }
        savePending();

        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack stack = new ItemStack(Material.VILLAGER_SPAWN_EGG, give);
            for (ItemStack left : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            remaining -= give;
        }
    }

    private void loadPending() {
        pending = new HashMap<>();
        if (!Files.exists(pendingFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(pendingFile);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int sep = trimmed.indexOf('=');
                if (sep <= 0) {
                    continue;
                }
                UUID uuid = UUID.fromString(trimmed.substring(0, sep).trim());
                int amount = Integer.parseInt(trimmed.substring(sep + 1).trim());
                if (amount > 0) {
                    pending.put(uuid, amount);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read villager egg pending file.", ex);
            pending = new HashMap<>();
        }
    }

    private void savePending() {
        try {
            Files.createDirectories(pendingFile.getParent());
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<UUID, Integer> entry : pending.entrySet()) {
                if (entry.getValue() > 0) {
                    builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
                }
            }
            Files.writeString(pendingFile, builder.toString());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save villager egg pending file.", ex);
        }
    }
}
