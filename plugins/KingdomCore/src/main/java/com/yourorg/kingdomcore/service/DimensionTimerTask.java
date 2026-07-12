package com.yourorg.kingdomcore.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class DimensionTimerTask implements Runnable {
    
    private final JavaPlugin plugin;
    
    // Track which thresholds we have already broadcasted for a given dimension and action (open/close)
    // Key format: "nether_open", "end_close"
    // Value: The last threshold (in ms) we broadcasted. We broadcast when remaining time drops below a threshold.
    private final Map<String, Long> lastThresholdPassed = new HashMap<>();

    public DimensionTimerTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        checkDimension("nether", "Nether");
        checkDimension("end", "End");
    }
    
    private void checkDimension(String dim, String displayName) {
        // Check Open
        if (plugin.getConfig().contains("dimensions." + dim + ".opens_at_ms")) {
            long targetTime = plugin.getConfig().getLong("dimensions." + dim + ".opens_at_ms");
            long remaining = targetTime - System.currentTimeMillis();
            
            if (remaining <= 0) {
                // Open it!
                plugin.getConfig().set("dimensions." + dim + ".open", true);
                plugin.getConfig().set("dimensions." + dim + ".opens_at_ms", null);
                plugin.saveConfig();
                
                lastThresholdPassed.remove(dim + "_open");
                
                broadcastTitle(displayName, "§aIs now OPEN!", "§aOpened!");
            } else {
                handleBroadcasts(dim + "_open", displayName, remaining, true);
            }
        }
        
        // Check Close
        if (plugin.getConfig().contains("dimensions." + dim + ".closes_at_ms")) {
            long targetTime = plugin.getConfig().getLong("dimensions." + dim + ".closes_at_ms");
            long remaining = targetTime - System.currentTimeMillis();
            
            if (remaining <= 0) {
                // Close it!
                plugin.getConfig().set("dimensions." + dim + ".open", false);
                plugin.getConfig().set("dimensions." + dim + ".closes_at_ms", null);
                plugin.saveConfig();
                
                lastThresholdPassed.remove(dim + "_close");
                
                broadcastTitle(displayName, "§cIs now CLOSED!", "§cClosed!");
            } else {
                handleBroadcasts(dim + "_close", displayName, remaining, false);
            }
        }
    }
    
    private void handleBroadcasts(String key, String displayName, long remainingMs, boolean isOpen) {
        long last = lastThresholdPassed.getOrDefault(key, Long.MAX_VALUE);
        
        long thirtyMins = 1_800_000L;
        long tenMins = 600_000L;
        long fiveMins = 300_000L;
        long oneMin = 60_000L;
        
        if (isOpen) {
            if (remainingMs <= thirtyMins && last > thirtyMins) {
                broadcastTitle(displayName, "§eOpens in 30 minutes", "§e30 Minutes");
                lastThresholdPassed.put(key, thirtyMins);
            } else if (remainingMs <= tenMins && last > tenMins) {
                broadcastTitle(displayName, "§6Opens in 10 minutes", "§610 Minutes");
                lastThresholdPassed.put(key, tenMins);
            } else if (remainingMs <= fiveMins && last > fiveMins) {
                broadcastTitle(displayName, "§6Opens in 5 minutes", "§65 Minutes");
                lastThresholdPassed.put(key, fiveMins);
            } else if (remainingMs <= oneMin && last > oneMin) {
                broadcastTitle(displayName, "§cOpens in 1 minute!", "§c1 Minute");
                lastThresholdPassed.put(key, oneMin);
            }
        } else {
            if (remainingMs <= tenMins && last > tenMins) {
                broadcastTitle(displayName, "§6Closes in 10 minutes", "§610 Minutes");
                lastThresholdPassed.put(key, tenMins);
            } else if (remainingMs <= oneMin && last > oneMin) {
                broadcastTitle(displayName, "§cCloses in 1 minute!", "§c1 Minute");
                lastThresholdPassed.put(key, oneMin);
            }
        }
    }
    
    private void broadcastTitle(String title, String subtitle, String chatAction) {
        Bukkit.broadcastMessage("§b§l" + title + " §7" + chatAction);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§b" + title, subtitle, 10, 70, 20);
        }
    }
}
