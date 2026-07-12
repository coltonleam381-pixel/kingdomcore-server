package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.BountyService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.Sound;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BountyDeathListener implements Listener {
    private final BountyService bountyService;
    private final HeartService heartService;
    private final Map<UUID, String> lastIpMap = new HashMap<>();

    public BountyDeathListener(BountyService bountyService, HeartService heartService) {
        this.bountyService = bountyService;
        this.heartService = heartService;
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (event.getPlayer().getAddress() != null) {
            lastIpMap.put(event.getPlayer().getUniqueId(), event.getPlayer().getAddress().getAddress().getHostAddress());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("KingdomCore") instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
            if (kcPlugin.getAssassinEventService().shouldSkipBounty(victim.getUniqueId())) {
                return;
            }
        }
        Player killer = victim.getKiller();

        if (killer != null && killer != victim) {
            int bounty = bountyService.getBounty(victim.getUniqueId());
            if (bounty > 0) {
                String victimIp = lastIpMap.get(victim.getUniqueId());
                String killerIp = lastIpMap.get(killer.getUniqueId());

                if (victimIp != null && killerIp != null && victimIp.equals(killerIp)) {
                    killer.sendMessage(ChatColor.RED + "You cannot claim a bounty from a player on the same IP address!");
                    return;
                }

                bountyService.resetBounty(victim.getUniqueId());
                heartService.addHearts(killer, bounty);

                killer.sendMessage(ChatColor.GREEN + "You claimed the " + ChatColor.YELLOW + bounty + " Heart" + (bounty > 1 ? "s" : "") + ChatColor.GREEN + " bounty on " + ChatColor.RED + victim.getName() + ChatColor.GREEN + "!");
                
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    if (p != killer) {
                        p.sendMessage(ChatColor.YELLOW + killer.getName() + ChatColor.GREEN + " has claimed the " + ChatColor.YELLOW + bounty + " Heart" + (bounty > 1 ? "s" : "") + ChatColor.GREEN + " bounty on " + ChatColor.RED + victim.getName() + ChatColor.GREEN + "!");
                    }
                }
            }
        }
    }
}
