package com.yourorg.kingdomcore.service;

import org.bukkit.plugin.java.JavaPlugin;

public class SpawnLeaveLockService {

    private static final String CONFIG_PATH = "spawn-leave-lock.enabled";

    private final JavaPlugin plugin;
    private boolean enabled;

    public SpawnLeaveLockService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean(CONFIG_PATH, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set(CONFIG_PATH, enabled);
        plugin.saveConfig();
        return true;
    }
}
