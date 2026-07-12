package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ReviveService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class JoinListener implements Listener {
    private final KingdomCorePlugin plugin;
    private final HeartService heartService;
    private final HealthService healthService;
    private final CooldownService cooldownService;
    private final ReviveService reviveService;
    private final AbilityOwnershipService abilityOwnershipService;

    private final com.yourorg.kingdomcore.service.UniqueItemService uniqueItemService;
    private final com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService;

    public JoinListener(KingdomCorePlugin plugin, HeartService heartService, HealthService healthService,
                        CooldownService cooldownService, ReviveService reviveService,
                        AbilityOwnershipService abilityOwnershipService,
                        com.yourorg.kingdomcore.service.UniqueItemService uniqueItemService,
                        com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService) {
        this.plugin = plugin;
        this.heartService = heartService;
        this.healthService = healthService;
        this.cooldownService = cooldownService;
        this.reviveService = reviveService;
        this.abilityOwnershipService = abilityOwnershipService;
        this.uniqueItemService = uniqueItemService;
        this.itemIdentityService = itemIdentityService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerState state = heartService.getOrCreateState(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        abilityOwnershipService.syncPlayerAbilityFromOwnership(event.getPlayer().getUniqueId());
        heartService.updateLastName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        cooldownService.load(event.getPlayer().getUniqueId());
        uniqueItemService.checkAndPurgeDuplicates(event.getPlayer());
        repairTridentItems(event.getPlayer());
        state = heartService.getOrCreateState(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        healthService.applyHealth(event.getPlayer(), state);
        reviveService.handleJoin(event.getPlayer());

        if (!event.getPlayer().hasPlayedBefore()) {
            scheduleFirstJoinWelcome(event.getPlayer());
        }
    }

    private void repairTridentItems(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            itemIdentityService.ensureTridentItem(stack);
        }
        itemIdentityService.ensureTridentItem(player.getInventory().getItemInOffHand());
    }

    private void scheduleFirstJoinWelcome(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendTitle("§6§lWelcome", "§fTo the Server", 10, 60, 15);
        }, 40L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendTitle("§eChoose Your Ability", "§7Talk to the guide NPC", 10, 70, 15);
        }, 140L);
    }
}
