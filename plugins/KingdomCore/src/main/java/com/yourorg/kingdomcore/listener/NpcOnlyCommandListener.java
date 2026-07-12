package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.NpcOnlyCommands;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks NPC-only commands when typed in chat. FancyNPC actions use {@code player_command_as_op},
 * which grants temporary OP for the duration of the command.
 */
public class NpcOnlyCommandListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String label = NpcOnlyCommands.extractCommandLabel(event.getMessage());
        if (!NpcOnlyCommands.isNpcOnlyCommand(label)) {
            return;
        }
        if (NpcOnlyCommands.canRunNpcOnlyCommand(player)) {
            return;
        }
        event.setCancelled(true);
        NpcOnlyCommands.denyUnlessAllowed(player);
    }
}
