package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.service.CombatTagService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import com.yourorg.kingdomcore.service.AfkService;
import com.yourorg.kingdomcore.util.CombatRules;

public class QuitListener implements Listener {
    private final CooldownService cooldownService;
    private final AbilityService abilityService;
    private final CombatTagService combatTagService;
    private final AfkService afkService;
    private final CombatRules combatRules;

    public QuitListener(CooldownService cooldownService,
                        AbilityService abilityService,
                        CombatTagService combatTagService,
                        AfkService afkService,
                        CombatRules combatRules) {
        this.cooldownService = cooldownService;
        this.abilityService = abilityService;
        this.combatTagService = combatTagService;
        this.afkService = afkService;
        this.combatRules = combatRules;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("KingdomCore") instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            kcPlugin.getHeartService().invalidateCache(player.getUniqueId());
        }

        if (afkService.isWindup(event.getPlayer())) {
            afkService.cancelWindup(event.getPlayer(), null);
        } else if (afkService.isAfk(event.getPlayer())) {
            afkService.exitAfk(event.getPlayer(), null);
        }

        if (org.bukkit.Bukkit.getPluginManager().getPlugin("KingdomCore") instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            if (kcPlugin.getAssassinEventService().wasQuitHandled(event.getPlayer().getUniqueId())) {
                cooldownService.flush(event.getPlayer().getUniqueId());
                abilityService.cleanup(event.getPlayer());
                return;
            }
        }

        cooldownService.flush(event.getPlayer().getUniqueId());
        abilityService.cleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLogout(PlayerQuitEvent event) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("KingdomCore") instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            if (kcPlugin.getAssassinEventService().wasQuitHandled(event.getPlayer().getUniqueId())) {
                return;
            }
            if (kcPlugin.getAssassinEventService().isParticipant(event.getPlayer().getUniqueId())) {
                return;
            }
        }
        if (combatRules.shouldEnforce(event.getPlayer())
                && combatTagService.isTagged(event.getPlayer().getUniqueId())) {
            combatTagService.handleCombatLogout(event.getPlayer());
        }
    }
}
