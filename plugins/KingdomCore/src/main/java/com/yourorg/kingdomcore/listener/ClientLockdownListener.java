package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.security.ClientPolicyMatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks /seed and kicks clients that register forbidden mod channels or brands
 * (Wurst, Xaero, minimaps, xray-related mods, etc.).
 */
public class ClientLockdownListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = Set.of("seed", "minecraft:seed");
    private static final String UNKNOWN_COMMAND = "§cUnknown command. Type \"/help\" for help.";

    private final JavaPlugin plugin;
    private final ClientPolicyMatcher matcher;
    private final boolean blockSeedCommand;
    private final boolean opBypass;
    private final String kickMessage;
    private final Set<UUID> pendingAudit = ConcurrentHashMap.newKeySet();

    public ClientLockdownListener(JavaPlugin plugin,
                                  List<String> forbiddenPatterns,
                                  boolean blockSeedCommand,
                                  boolean opBypass,
                                  String kickMessage) {
        this.plugin = plugin;
        this.matcher = new ClientPolicyMatcher(forbiddenPatterns);
        this.blockSeedCommand = blockSeedCommand;
        this.opBypass = opBypass;
        this.kickMessage = kickMessage == null || kickMessage.isBlank()
                ? "§cDisallowed client modification detected.\n§7Remove cheat, minimap, or xray mods/resource packs."
                : kickMessage;

        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, "minecraft:brand", (channel, player, message) -> onBrandMessage(player, message));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blockSeedCommand) {
            return;
        }
        Player player = event.getPlayer();
        if (opBypass && player.isOp()) {
            return;
        }
        String label = extractLabel(event.getMessage());
        if (BLOCKED_COMMANDS.contains(label)) {
            event.setCancelled(true);
            player.sendMessage(UNKNOWN_COMMAND);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        if (shouldBypass(player)) {
            return;
        }
        inspect(player, event.getChannel(), "channel");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (shouldBypass(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        pendingAudit.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> auditJoinedPlayer(player), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> auditJoinedPlayer(player), 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingAudit.remove(id), 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingAudit.remove(event.getPlayer().getUniqueId());
    }

    private void auditJoinedPlayer(Player player) {
        if (!player.isOnline() || shouldBypass(player)) {
            return;
        }
        String brand = player.getClientBrandName();
        if (brand != null) {
            inspect(player, brand, "client");
        }
    }

    private void onBrandMessage(Player player, byte[] message) {
        if (shouldBypass(player)) {
            return;
        }
        if (message == null || message.length == 0) {
            return;
        }
        String brand = new String(message, StandardCharsets.UTF_8);
        inspect(player, brand, "brand");
    }

    private void inspect(Player player, String value, String kind) {
        ClientPolicyMatcher.MatchResult result = matcher.match(value);
        if (!result.blocked()) {
            return;
        }
        kick(player, result.pattern(), kind, value);
    }

    private void kick(Player player, String pattern, String kind, String raw) {
        plugin.getLogger().warning("Client lockdown: kicked " + player.getName()
                + " (" + kind + "='" + raw + "', matched='" + pattern + "')");
        String message = kickMessage
                .replace("{pattern}", pattern == null ? "unknown" : pattern)
                .replace("{source}", raw == null ? "" : raw);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(message);
            }
        });
    }

    private boolean shouldBypass(Player player) {
        return opBypass && player.isOp();
    }

    private static String extractLabel(String message) {
        if (message == null) {
            return "";
        }
        String raw = message.trim();
        if (!raw.startsWith("/")) {
            return "";
        }
        String label = raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        return label;
    }
}
