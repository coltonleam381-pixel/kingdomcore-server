package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks utility commands during active combat tag so they cannot interfere with PvP.
 */
public class CombatCommandBlockListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "afk",
            "revivemenu",
            "abilitymenu",
            "abilitymen",
            "abilityupgrade",
            "bounty391381",
            "withdraw",
            "kcraft",
            "dm",
            "dmenu",
            "deluxemenus",
            "deluxemenu"
    );

    private final CombatTagService combatTagService;
    private final CombatRules combatRules;
    private final Plugin plugin;

    public CombatCommandBlockListener(CombatTagService combatTagService, CombatRules combatRules, Plugin plugin) {
        this.combatTagService = combatTagService;
        this.combatRules = combatRules;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combatRules.shouldEnforce(player)) {
            return;
        }
        if (!combatRules.shouldBlockCombatCooldowns(player)) {
            return;
        }
        if (!combatTagService.isTagged(player.getUniqueId())) {
            return;
        }
        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) {
            return;
        }
        String label = raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if ("withdraw".equals(label) && isAdminWithdrawFromOther(player, raw)) {
            return;
        }
        if (BLOCKED_COMMANDS.contains(label)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot use that command while in combat.");
        }
    }

    private boolean isAdminWithdrawFromOther(Player player, String rawCommand) {
        String[] parts = rawCommand.trim().split("\\s+");
        if (parts.length < 3) {
            return false;
        }
        if ("heart".equalsIgnoreCase(parts[1]) || "hearts".equalsIgnoreCase(parts[1])) {
            return false;
        }
        List<String> names = plugin.getConfig().getStringList("inv-check.allowed-players");
        if (names == null || names.isEmpty()) {
            names = List.of("GameAxion");
        }
        String viewer = player.getName().toLowerCase(Locale.ROOT);
        for (String allowed : names) {
            if (allowed != null && allowed.toLowerCase(Locale.ROOT).equals(viewer)) {
                return true;
            }
        }
        return false;
    }
}
