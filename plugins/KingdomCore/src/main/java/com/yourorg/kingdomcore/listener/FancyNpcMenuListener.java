package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.AuthService;
import com.yourorg.kingdomcore.integrations.DeluxeMenusBridge;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles FancyNpcs clicks via console dispatch so normal players are not blocked by chat command filters.
 */
public final class FancyNpcMenuListener {

    private static final long CLICK_DEBOUNCE_MS = 400L;

    private static final Map<String, String> DELUXE_MENUS = Map.of(
            "hey", "abilitymen"
    );

    private static final Map<String, String> CONSOLE_COMMANDS = Map.ofEntries(
            Map.entry("upgrade", "abilityupgrade"),
            Map.entry("BountyNPC", "bounty391381"),
            Map.entry("beacon_clicker", "kcraft revive_beacon"),
            Map.entry("crown_clicker", "kcraft crown"),
            Map.entry("heart_clicker", "kcraft heart"),
            Map.entry("warden_clicker", "kcraft warden_cp"),
            Map.entry("mace_clicker", "kcraft mace"),
            Map.entry("mace_clicker1", "kcraft mace"),
            Map.entry("trident_clicker", "kcraft trident"),
            Map.entry("trident_clicker1", "kcraft trident"),
            Map.entry("scythe_clicker", "kcraft scythe"),
            Map.entry("scythe_clicker1", "kcraft scythe")
    );

    private static final Map<String, Long> lastClickMs = new ConcurrentHashMap<>();

    private FancyNpcMenuListener() {
    }

    public static void register(JavaPlugin plugin, AuthService authService,
                                CombatTagService combatTagService, CombatRules combatRules) {
        if (Bukkit.getPluginManager().getPlugin("FancyNpcs") == null) {
            plugin.getLogger().info("FancyNpcs not loaded; NPC bridge disabled.");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(
                    "de.oliver.fancynpcs.api.events.NpcInteractEvent");
            Listener listener = new Listener() {
            };
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.HIGH,
                    (unused, event) -> handleInteract(plugin, authService, combatTagService, combatRules, event),
                    plugin,
                    false
            );
            plugin.getLogger().info("FancyNpcs bridge enabled for menus, crafts, and upgrades.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs interact event class not found; NPC bridge disabled.");
        }
    }

    private static void handleInteract(JavaPlugin plugin, AuthService authService,
                                       CombatTagService combatTagService, CombatRules combatRules, Event event) {
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Method getNpc = event.getClass().getMethod("getNpc");
            Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);

            Player player = (Player) getPlayer.invoke(event);
            if (!authService.isAuthenticated(player.getUniqueId())) {
                return;
            }

            if (isNpcBlocked(player, combatTagService, combatRules)) {
                setCancelled.invoke(event, true);
                long remaining = combatTagService.isTagged(player.getUniqueId())
                        ? combatTagService.getRemainingTagMs(player.getUniqueId())
                        : combatTagService.getEchestCooldownRemainingMs(player.getUniqueId());
                long seconds = Math.max(1, remaining / 1000);
                player.sendMessage("§cYou cannot use NPCs for another " + seconds + " seconds.");
                return;
            }

            Object npc = getNpc.invoke(event);
            Method getData = npc.getClass().getMethod("getData");
            Object data = getData.invoke(npc);
            String npcName = (String) data.getClass().getMethod("getName").invoke(data);

            String deluxeMenu = DELUXE_MENUS.get(npcName);
            String consoleCommand = CONSOLE_COMMANDS.get(npcName);
            if (deluxeMenu == null && consoleCommand == null) {
                return;
            }

            setCancelled.invoke(event, true);

            String debounceKey = player.getUniqueId() + ":" + npcName;
            long now = System.currentTimeMillis();
            Long last = lastClickMs.get(debounceKey);
            if (last != null && now - last < CLICK_DEBOUNCE_MS) {
                return;
            }
            lastClickMs.put(debounceKey, now);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (deluxeMenu != null) {
                    if (!DeluxeMenusBridge.openMenu(plugin, player, deluxeMenu)) {
                        player.sendActionBar(Component.text("Could not open ability menu.", NamedTextColor.RED));
                        plugin.getLogger().warning("Failed to open DeluxeMenus menu '" + deluxeMenu
                                + "' for " + player.getName() + " via NPC '" + npcName + "'");
                    }
                    return;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand + " " + player.getName());
            });
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "FancyNpcs bridge error", ex);
        }
    }

    private static boolean isNpcBlocked(Player player, CombatTagService combatTagService, CombatRules combatRules) {
        if (!combatRules.shouldBlockCombatCooldowns(player)) {
            return false;
        }
        if (combatTagService.isTagged(player.getUniqueId())) {
            return true;
        }
        return combatTagService.getEchestCooldownRemainingMs(player.getUniqueId()) > 0;
    }
}
