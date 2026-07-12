package com.yourorg.kingdomcore.service.impl;

import com.yourorg.kingdomcore.service.AfkService;
import com.yourorg.kingdomcore.service.CombatTagService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkServiceImpl implements AfkService {

    private final Plugin plugin;
    private final CombatTagService combatTagService;
    
    private final Map<UUID, Long> windupTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afkPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Location> afkAnchors = new ConcurrentHashMap<>();

    private static final long WINDUP_TICKS = 100L; // 5 seconds (20 ticks/sec)
    private static final long AFK_COOLDOWN_MS = 30000L; // 30 seconds

    public AfkServiceImpl(Plugin plugin, CombatTagService combatTagService) {
        this.plugin = plugin;
        this.combatTagService = combatTagService;
        
        // Cleanup disconnected players and expired cooldowns periodically
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            afkCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
        }, 200L, 200L);
    }

    @Override
    public void startWindup(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (isWindup(player) || isAfk(player)) {
            return;
        }

        player.sendMessage("§e[AFK] Do not move or act for 5 seconds to enter AFK mode...");
        
        long taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            windupTasks.remove(uuid);
            enterAfk(player);
        }, WINDUP_TICKS).getTaskId();
        
        windupTasks.put(uuid, taskId);
    }

    @Override
    public void cancelWindup(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        Long taskId = windupTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId.intValue());
            if (reason != null && !reason.isEmpty()) {
                player.sendMessage("§c[AFK] Windup cancelled: " + reason);
            }
        }
    }

    @Override
    public void enterAfk(Player player) {
        UUID uuid = player.getUniqueId();
        afkPlayers.put(uuid, true);
        afkAnchors.put(uuid, player.getLocation().clone());
        player.sendMessage("§a[AFK] You are now AFK! You are invincible but cannot move or act.");
        Bukkit.broadcastMessage("§7" + player.getName() + " is now AFK.");
    }

    @Override
    public void exitAfk(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        afkAnchors.remove(uuid);
        if (afkPlayers.remove(uuid) != null) {
            afkCooldowns.put(uuid, System.currentTimeMillis() + AFK_COOLDOWN_MS);
            
            if (reason != null && !reason.isEmpty()) {
                player.sendMessage("§c[AFK] You are no longer AFK (" + reason + ").");
            } else {
                player.sendMessage("§c[AFK] You are no longer AFK.");
            }
            Bukkit.broadcastMessage("§7" + player.getName() + " is no longer AFK.");
        }
    }

    @Override
    public boolean isAfk(Player player) {
        return afkPlayers.containsKey(player.getUniqueId());
    }

    @Override
    public boolean isWindup(Player player) {
        return windupTasks.containsKey(player.getUniqueId());
    }

    @Override
    public boolean canUseAfkCommand(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (combatTagService.isTagged(uuid)) {
            player.sendMessage("§cYou cannot use /afk while in combat.");
            return false;
        }
        
        long combatCooldown = combatTagService.getAfkCooldownRemainingMs(uuid);
        if (combatCooldown > 0) {
            player.sendMessage("§cYou must wait " + (combatCooldown / 1000) + " seconds after combat ends to use /afk.");
            return false;
        }
        
        long afkCooldown = getCooldownRemaining(player);
        if (afkCooldown > 0) {
            player.sendMessage("§cYou cannot use /afk for another " + (afkCooldown / 1000) + " seconds.");
            return false;
        }
        
        return true;
    }

    @Override
    public long getCooldownRemaining(Player player) {
        Long expiry = afkCooldowns.get(player.getUniqueId());
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            afkCooldowns.remove(player.getUniqueId());
            return 0;
        }
        return remaining;
    }

    @Override
    public Location getAnchor(Player player) {
        return afkAnchors.get(player.getUniqueId());
    }

    @Override
    public void snapToAnchor(Player player) {
        Location anchor = getAnchor(player);
        if (anchor == null) {
            return;
        }
        Location snap = anchor.clone();
        snap.setYaw(player.getLocation().getYaw());
        snap.setPitch(player.getLocation().getPitch());
        player.teleport(snap);
    }
}
