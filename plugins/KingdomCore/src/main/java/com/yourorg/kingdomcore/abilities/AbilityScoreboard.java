package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ability HUD:
 * - right-side scoreboard (ability + cooldown + combat tag)
 * - actionbar status above hotbar (clear "do I have ability" indicator)
 */
public class AbilityScoreboard {
    private final Plugin plugin;
    private final AbilityService abilityService;
    private final CooldownService cooldownService;
    private final CombatTagService combatTagService;
    private final AtlantisAbility atlantisAbility;
    private final ThorAbility thorAbility;
    private final HeartService heartService;
    private final com.yourorg.kingdomcore.util.SpawnRegionPolicy spawnRegionPolicy;
    private com.yourorg.kingdomcore.listeners.WardenChestplateListener wardenListener;
    private com.yourorg.kingdomcore.listeners.TridentAbilityListener tridentListener;
    private com.yourorg.kingdomcore.listeners.ScytheAbilityListener scytheListener;
    private com.yourorg.kingdomcore.service.AssassinEventService assassinEventService;
    private com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService;
    private final Map<UUID, ActionBarOverride> actionBarOverrides = new ConcurrentHashMap<>();

    private record ActionBarOverride(String text, long expiresAtMs) {
    }

    public AbilityScoreboard(Plugin plugin,
                             AbilityService abilityService,
                             CooldownService cooldownService,
                             CombatTagService combatTagService,
                             AtlantisAbility atlantisAbility,
                             ThorAbility thorAbility,
                             HeartService heartService,
                             SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.abilityService = abilityService;
        this.cooldownService = cooldownService;
        this.combatTagService = combatTagService;
        this.atlantisAbility = atlantisAbility;
        this.thorAbility = thorAbility;
        this.heartService = heartService;
        this.spawnRegionPolicy = spawnRegionPolicy;

        startUpdateTask();
    }

    /** Called after construction so we can show warden CP info in the sidebar. */
    public void setWardenListener(com.yourorg.kingdomcore.listeners.WardenChestplateListener listener) {
        this.wardenListener = listener;
    }

    public void setTridentListener(com.yourorg.kingdomcore.listeners.TridentAbilityListener listener) {
        this.tridentListener = listener;
    }

    public void setScytheListener(com.yourorg.kingdomcore.listeners.ScytheAbilityListener listener) {
        this.scytheListener = listener;
    }

    public void setAssassinEventService(com.yourorg.kingdomcore.service.AssassinEventService assassinEventService) {
        this.assassinEventService = assassinEventService;
    }

    public void setItemIdentityService(com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    public void showTemporaryActionBar(Player player, String message, long durationMs) {
        if (player == null || message == null || message.isBlank() || durationMs <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationMs;
        actionBarOverrides.put(player.getUniqueId(), new ActionBarOverride(message, expiresAt));
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }

    private void sendActionBar(Player player, String message) {
        ActionBarOverride override = actionBarOverrides.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (override != null && now < override.expiresAtMs()) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(override.text()));
            return;
        }
        if (override != null) {
            actionBarOverrides.remove(player.getUniqueId());
        }
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }

    private boolean shouldShowUniqueItemHud(Player player) {
        if (itemIdentityService == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        if (tridentListener != null && itemIdentityService.isTridentItem(main)) {
            return tridentListener.getTridentHudLine(player, now) != null;
        }
        if (scytheListener != null && itemIdentityService.isScytheItem(main)) {
            return scytheListener.getScytheHudLine(player, now) != null;
        }
        org.bukkit.inventory.ItemStack chest = player.getInventory().getChestplate();
        return wardenListener != null && itemIdentityService.isWardenCpItem(chest);
    }

    private void renderAssassinHud(Player player) {
        clearScoreboard(player);
        String line = assassinEventService.formatHudLine(player);
        sendActionBar(player, line);
    }

    public void updatePlayer(Player player, PlayerState state) {
        if (assassinEventService != null && assassinEventService.isParticipant(player.getUniqueId())) {
            if (!shouldShowUniqueItemHud(player) && !assassinEventService.shouldShowAbilityHud(player.getUniqueId())) {
                renderAssassinHud(player);
                return;
            }
        }

        if (state.getAbilityId() == null || state.getAbilityId().isBlank()) {
            clearScoreboard(player);
            sendActionBar(player, "§7Ability: §cNone");
            return;
        }

        AbilityDefinition def = abilityService.getAbility(state.getAbilityId());
        if (def == null) {
            clearScoreboard(player);
            sendActionBar(player, "§7Ability: §cInvalid");
            return;
        }
        if (state.getAbilityLevel() == 0) {
            clearScoreboard(player);
            sendActionBar(player, "§7Upgrade ability to §eL1");
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(scoreboard);
        }

        Objective objective = scoreboard.getObjective("ability");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("ability", Criteria.DUMMY, "§6§lAbility");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int nextScore = 5;
        objective.getScore("§e" + def.name() + " §7(L" + state.getAbilityLevel() + ")").setScore(nextScore--);

        long now = System.currentTimeMillis();
        long cooldownRemaining = cooldownService.getRemainingMs(player.getUniqueId(), state.getAbilityId(), now);
        if (cooldownRemaining > 0) {
            long seconds = cooldownRemaining / 1000;
            objective.getScore("§cCooldown: " + seconds + "s").setScore(nextScore--);
        } else {
            objective.getScore("§aReady").setScore(nextScore--);
        }

        long combatTagRemaining = combatTagService.getRemainingTagMs(player.getUniqueId());
        if (combatTagRemaining > 0) {
            long seconds = combatTagRemaining / 1000;
            objective.getScore("§4Player CD: " + seconds + "s").setScore(nextScore--);
        }

        if ("atlantis".equals(state.getAbilityId()) && state.getAbilityLevel() == 3) {
            AtlantisAbility.AtlantisMode mode = atlantisAbility.getMode(player.getUniqueId());
            objective.getScore("§bMode: " + mode).setScore(nextScore--);
        }

        // Warden Chestplate — two separate sidebar lines below ability entries
        if (wardenListener != null) {
            ItemStack chestplate = player.getInventory().getChestplate();
            if (wardenListener.getWardenSidebarLine(player.getUniqueId(), chestplate, now) != null) {
                // Label row
                objective.getScore("§3⚔ Warden CP").setScore(nextScore--);
                // Status row: cd + uses
                String status = wardenListener.getWardenStatusLine(player.getUniqueId(), chestplate, now);
                objective.getScore("  " + status).setScore(nextScore--);
            }
        }

        String cooldownText = cooldownRemaining > 0
                ? "§c" + (cooldownRemaining / 1000) + "s"
                : "§aReady";
        String actionbar = "§6✦ §f" + def.name() + " §7[L" + state.getAbilityLevel() + "] §8| " + cooldownText;

        // Custom actionbar if in spawn
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            actionbar = "§a§l✦ Spawn ✦";
        } else {
            var handler = abilityService.getHandler(state.getAbilityId());
            if (handler instanceof AbilityHudProvider hudProvider) {
                String customHud = hudProvider.getHudLine(player.getUniqueId(), now);
                if (customHud != null) {
                    actionbar = customHud;
                }
            }
            // Append Warden CP status as second line in actionbar (only when wearing it)
            if (wardenListener != null) {
                ItemStack chestplate = player.getInventory().getChestplate();
                String wardenLine = wardenListener.getWardenSidebarLine(player.getUniqueId(), chestplate, now);
                if (wardenLine != null) {
                    actionbar += " §8| " + wardenLine;
                }
            }

            // Override with Trident HUD if holding the Trident
            if (tridentListener != null) {
                String tridentHud = tridentListener.getTridentHudLine(player, now);
                if (tridentHud != null) {
                    actionbar = tridentHud;
                }
            }

            // Override with Scythe HUD if holding the Scythe
            if (scytheListener != null) {
                String scytheHud = scytheListener.getScytheHudLine(player, now);
                if (scytheHud != null) {
                    actionbar = scytheHud;
                }
            }
        }

        sendActionBar(player, actionbar);
    }

    public void clearScoreboard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard != null && scoreboard != Bukkit.getScoreboardManager().getMainScoreboard()) {
            Objective objective = scoreboard.getObjective("ability");
            if (objective != null) {
                objective.unregister();
            }
        }
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
                    updatePlayer(player, state);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
}
