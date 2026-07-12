package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AllowlistService;
import com.yourorg.kingdomcore.core.services.DebugTelemetryService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PreLoginListener implements Listener {
    private final KingdomConfig config;
    private final AllowlistService allowlistService;
    private final HeartService heartService;
    private final DebugTelemetryService debugTelemetryService;
    private final com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository repository;

    public PreLoginListener(KingdomConfig config,
                            AllowlistService allowlistService,
                            HeartService heartService,
                            DebugTelemetryService debugTelemetryService,
                            com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository repository) {
        this.config = config;
        this.allowlistService = allowlistService;
        this.heartService = heartService;
        this.debugTelemetryService = debugTelemetryService;
        this.repository = repository;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (config.isAllowlistEnabled() && !allowlistService.isAllowed(event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Not allowed");
            return;
        }
        java.util.Optional<com.yourorg.kingdomcore.api.PlayerState> dbState = repository.findById(event.getUniqueId());
        boolean isBlocked = false;
        if (dbState.isPresent()) {
            isBlocked = dbState.get().isBlocked();
        } else {
            // New player, not in DB yet
            int clamped = com.yourorg.kingdomcore.util.HeartRules.clampProgression(config.getStartingHearts(), config.getProgressionMaxHearts());
            isBlocked = clamped <= 0;
        }

        if (isBlocked) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.getBlockedKickMessage());
            debugTelemetryService.record(event.getUniqueId(), DebugTelemetryService.FailReason.BLOCKED);
        }
    }
}
