package com.yourorg.kingdomcore.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PvpTimerTask implements Runnable {

    private static final long THIRTY_MINUTES_MS = 1_800_000L;
    private static final long TEN_MINUTES_MS = 600_000L;
    private static final long ONE_MINUTE_MS = 60_000L;

    private static final String ENABLE_AT_KEY = "pvp.enables_at_ms";
    private static final String LAST_THRESHOLD_KEY = "pvp.last_announced_threshold_ms";

    private final JavaPlugin plugin;

    public PvpTimerTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().contains(ENABLE_AT_KEY)) {
            return;
        }

        long targetTime = plugin.getConfig().getLong(ENABLE_AT_KEY);
        long remaining = targetTime - System.currentTimeMillis();

        if (remaining <= 0) {
            enablePvpNow();
            return;
        }

        long lastThreshold = getLastThreshold();
        if (lastThreshold == Long.MAX_VALUE) {
            lastThreshold = inferPassedThreshold(remaining);
        }

        if (remaining <= ONE_MINUTE_MS && lastThreshold > ONE_MINUTE_MS) {
            broadcastCountdown("§cStarts in 1 minute!", "§c1 Minute");
            setLastThreshold(ONE_MINUTE_MS);
        } else if (remaining <= TEN_MINUTES_MS && lastThreshold > TEN_MINUTES_MS) {
            broadcastCountdown("§6Starts in 10 minutes", "§610 Minutes");
            setLastThreshold(TEN_MINUTES_MS);
        } else if (remaining <= THIRTY_MINUTES_MS && lastThreshold > THIRTY_MINUTES_MS) {
            broadcastCountdown("§eStarts in 30 minutes", "§e30 Minutes");
            setLastThreshold(THIRTY_MINUTES_MS);
        }
    }

    public static void scheduleEnable(JavaPlugin plugin) {
        long targetTime = System.currentTimeMillis() + THIRTY_MINUTES_MS;
        plugin.getConfig().set(ENABLE_AT_KEY, targetTime);
        plugin.getConfig().set(LAST_THRESHOLD_KEY, THIRTY_MINUTES_MS);
        plugin.saveConfig();
        broadcastGameBegun();
        Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastCountdown(
                "§e30 minutes until PvP is on",
                "§e30 minutes until PvP is on"
        ), 60L);
    }

    public static void cancelScheduledEnable(JavaPlugin plugin) {
        plugin.getConfig().set(ENABLE_AT_KEY, null);
        plugin.getConfig().set(LAST_THRESHOLD_KEY, null);
        plugin.saveConfig();
    }

    public static boolean hasScheduledEnable(JavaPlugin plugin) {
        return plugin.getConfig().contains(ENABLE_AT_KEY);
    }

    private void enablePvpNow() {
        plugin.getConfig().set(ENABLE_AT_KEY, null);
        plugin.getConfig().set(LAST_THRESHOLD_KEY, null);
        plugin.saveConfig();

        for (World world : Bukkit.getWorlds()) {
            world.setPVP(true);
        }

        Bukkit.broadcastMessage("§c§lPVP §a§lis now ON §7server-wide!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lPVP", "§a§lIS NOW ON!", 10, 70, 20);
        }
        plugin.getLogger().info("Scheduled PVP enable completed.");
    }

    private long getLastThreshold() {
        if (!plugin.getConfig().contains(LAST_THRESHOLD_KEY)) {
            return Long.MAX_VALUE;
        }
        return plugin.getConfig().getLong(LAST_THRESHOLD_KEY);
    }

    private void setLastThreshold(long thresholdMs) {
        plugin.getConfig().set(LAST_THRESHOLD_KEY, thresholdMs);
        plugin.saveConfig();
    }

    private static long inferPassedThreshold(long remainingMs) {
        if (remainingMs <= ONE_MINUTE_MS) {
            return ONE_MINUTE_MS;
        }
        if (remainingMs <= TEN_MINUTES_MS) {
            return TEN_MINUTES_MS;
        }
        return THIRTY_MINUTES_MS;
    }

    private static void broadcastGameBegun() {
        Bukkit.broadcastMessage("§a§lGood Luck §7— §fThe game has begun!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§a§lGood Luck", "§fThe game has begun!", 10, 70, 20);
        }
    }

    private static void broadcastCountdown(String subtitle, String chatLabel) {
        Bukkit.broadcastMessage("§c§lPVP §7" + chatLabel);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lPVP", subtitle, 10, 70, 20);
        }
    }
}
