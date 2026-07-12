package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
    private final KingdomConfig config;
    private final HeartService heartService;
    private final AbilityService abilityService;

    public DeathListener(KingdomConfig config, HeartService heartService, AbilityService abilityService) {
        this.config = config;
        this.heartService = heartService;
        this.abilityService = abilityService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        abilityService.cleanup(player);

        boolean pvpKill = killer != null && killer != player;
        if (config.isPvpOnlyDeathPenalty() && !pvpKill) {
            return;
        }
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("KingdomCore") instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            if (kcPlugin.getAssassinEventService().shouldSkipNormalDeathPenalty(player.getUniqueId())) {
                return;
            }
        }

        if (pvpKill) {
            heartService.applyKillHeartTransfer(player, killer, killer.getUniqueId(),
                    player.getLocation(), config.getBlockedKickMessage());
        } else {
            heartService.applyDeathPenalty(player, config.getBlockedKickMessage());
        }
    }
}
