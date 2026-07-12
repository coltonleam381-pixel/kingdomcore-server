package com.yourorg.kingdomcore.service;

import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.util.CombatLogoutInventory;
import com.yourorg.kingdomcore.util.TabTeamResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AssassinEventService {

    public static final int WIN_BONUS_HEARTS = 3;
    private static final long GRACE_MS = 60_000L;
    private static final long ABILITY_HUD_MS = 3_000L;

    public enum DeathHandling {
        NOT_IN_EVENT,
        CORRECT_KILL,
        DIED_TO_YOUR_TARGET,
        WRONG_DEATH,
        ENVIRONMENTAL
    }

    public enum StartResult {
        STARTED,
        ALREADY_ACTIVE,
        NOT_ENOUGH_PLAYERS
    }

    private final JavaPlugin plugin;
    private final HeartService heartService;
    private final CombatTagService combatTagService;
    private final TabTeamResolver tabTeamResolver;

    private volatile boolean active;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> hunterToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> targetToHunter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> graceUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> graceTaskIds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> abilityHudUntilMs = new ConcurrentHashMap<>();
    private final Set<UUID> skipNormalDeathPenalty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> skipBountyForVictim = ConcurrentHashMap.newKeySet();
    private final Set<UUID> handledQuits = ConcurrentHashMap.newKeySet();

    public AssassinEventService(JavaPlugin plugin, HeartService heartService, CombatTagService combatTagService) {
        this.plugin = plugin;
        this.heartService = heartService;
        this.combatTagService = combatTagService;
        this.tabTeamResolver = new TabTeamResolver(
                plugin.getConfig().getConfigurationSection("tab-prefixes.players"),
                plugin.getConfig().getConfigurationSection("chat-styles.players"));
        resetEvent();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isParticipant(UUID playerId) {
        return active && participants.contains(playerId) && !eliminated.contains(playerId);
    }

    public boolean wasQuitHandled(UUID playerId) {
        return handledQuits.remove(playerId);
    }

    public boolean shouldSkipNormalDeathPenalty(UUID playerId) {
        return skipNormalDeathPenalty.remove(playerId);
    }

    public boolean shouldSkipBounty(UUID victimId) {
        return skipBountyForVictim.remove(victimId);
    }

    public UUID getTarget(UUID hunterId) {
        return hunterToTarget.get(hunterId);
    }

    public UUID getHunter(UUID targetId) {
        return targetToHunter.get(targetId);
    }

    public long getGraceRemainingMs(UUID playerId) {
        Long until = graceUntilMs.get(playerId);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            graceUntilMs.remove(playerId);
            return 0;
        }
        return remaining;
    }

    public boolean isInGrace(UUID playerId) {
        return getGraceRemainingMs(playerId) > 0;
    }

    public void notifyAbilityUsed(UUID playerId) {
        if (!isParticipant(playerId)) {
            return;
        }
        abilityHudUntilMs.put(playerId, System.currentTimeMillis() + ABILITY_HUD_MS);
    }

    public boolean shouldShowAbilityHud(UUID playerId) {
        Long until = abilityHudUntilMs.get(playerId);
        return until != null && System.currentTimeMillis() < until;
    }

    public void resetEvent() {
        active = false;
        participants.clear();
        eliminated.clear();
        hunterToTarget.clear();
        targetToHunter.clear();
        graceUntilMs.clear();
        graceTaskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        graceTaskIds.clear();
        abilityHudUntilMs.clear();
        skipNormalDeathPenalty.clear();
        skipBountyForVictim.clear();
        handledQuits.clear();
    }

    public StartResult startEvent() {
        resetEvent();

        List<Player> eligible = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (eligible.size() < 2) {
            plugin.getLogger().warning(
                    "Assassin event aborted: need at least 2 players online (eligible=" + eligible.size() + ").");
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        active = true;

        for (Player player : eligible) {
            participants.add(player.getUniqueId());
            player.sendMessage("§4§lAssassin Event §7— you are in. Check your HUD for your target.");
        }
        rebuildAssignments();
        Bukkit.broadcastMessage("§4§lASSASSIN EVENT §7has begun! Hunt your target. Last one standing wins §c+3 max hearts§7.");
        for (Player player : eligible) {
            player.sendTitle("§4§lAssassin Event", "§7Eliminate your target", 10, 60, 15);
        }
        plugin.getLogger().info("Assassin event started with " + participants.size() + " participants.");
        return StartResult.STARTED;
    }

    public void tryJoin(Player player) {
        if (!active || eliminated.contains(player.getUniqueId())) {
            return;
        }
        if (participants.add(player.getUniqueId())) {
            player.sendMessage("§4§lAssassin Event §7— you are in. Check your HUD for your target.");
            assignNewTarget(player.getUniqueId());
            checkForWinner();
        }
    }

    public boolean handleQuit(Player player) {
        if (!isParticipant(player.getUniqueId())) {
            return false;
        }
        UUID id = player.getUniqueId();
        UUID hunterWhoTargetedMe = getHunter(id);

        if (isInGrace(id)) {
            cancelGraceTask(id);
            handledQuits.add(id);
            eliminatePermanent(id, "§7You left during your safe logout window.");
            reassignTargetForHunter(hunterWhoTargetedMe, "§7Your target left the event. You have been assigned a new target.");
            checkForWinner();
            return true;
        }

        handledQuits.add(id);
        Location logoutSpot = player.getLocation().clone();
        CombatLogoutInventory.dropAndClear(player, logoutSpot);
        heartService.removeHearts(player, 3);
        eliminatePermanent(id, "§cYou combat-logged during the assassin event.");
        Bukkit.broadcastMessage("§4" + player.getName() + " §7fled the hunt and lost §c3 hearts§7.");
        reassignTargetForHunter(hunterWhoTargetedMe, "§7Your target left the event. You have been assigned a new target.");
        checkForWinner();
        return true;
    }

    public DeathHandling handleDeath(Player victim, Player killer) {
        if (!isParticipant(victim.getUniqueId())) {
            return DeathHandling.NOT_IN_EVENT;
        }

        killer = resolveKiller(victim, killer);

        if (killer == null) {
            return handleEnvironmentalDeath(victim);
        }
        if (!isParticipant(killer.getUniqueId())) {
            return handleOutsiderKill(victim);
        }

        UUID victimId = victim.getUniqueId();
        UUID killerId = killer.getUniqueId();
        UUID killersTarget = getTarget(killerId);
        UUID victimsTarget = getTarget(victimId);

        if (killersTarget != null && killersTarget.equals(victimId)) {
            handleCorrectKill(killer, victim);
            return DeathHandling.CORRECT_KILL;
        }

        if (victimsTarget != null && victimsTarget.equals(killerId)) {
            handleDiedToYourTarget(victim);
            return DeathHandling.DIED_TO_YOUR_TARGET;
        }

        heartService.removeHearts(killer, 1);
        killer.sendActionBar("§c-1 heart §7(illegal kill)");
        markProtectedDeath(victimId);
        return DeathHandling.WRONG_DEATH;
    }

    private Player resolveKiller(Player victim, Player killer) {
        if (killer != null && killer.isOnline()) {
            return killer;
        }
        UUID damagerId = combatTagService.getLastDamager(victim.getUniqueId());
        if (damagerId == null) {
            return null;
        }
        Player resolved = Bukkit.getPlayer(damagerId);
        if (resolved != null && resolved.isOnline()) {
            return resolved;
        }
        return null;
    }

    private DeathHandling handleOutsiderKill(Player victim) {
        markProtectedDeath(victim.getUniqueId());
        victim.sendActionBar("§7You were killed by a non-participant — no hearts lost.");
        return DeathHandling.WRONG_DEATH;
    }

    private DeathHandling handleEnvironmentalDeath(Player victim) {
        markProtectedDeath(victim.getUniqueId());
        heartService.removeHearts(victim, 2);
        eliminate(victim.getUniqueId(), "§7You were eliminated from the assassin event.", false);
        victim.sendActionBar("§c-2 hearts §7(environmental death)");
        checkForWinner();
        return DeathHandling.ENVIRONMENTAL;
    }

    private void handleDiedToYourTarget(Player victim) {
        skipNormalDeathPenalty.add(victim.getUniqueId());
        skipBountyForVictim.add(victim.getUniqueId());
        heartService.removeHearts(victim, 2);
        victim.sendActionBar("§c-2 hearts §7(your target killed you — still in the event)");
    }

    private void handleCorrectKill(Player killer, Player victim) {
        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        markProtectedDeath(victimId);
        heartService.removeHearts(victim, 2);
        eliminate(victimId, "§7You were eliminated — your hunter completed their contract.", true);

        heartService.addHearts(killer, 2);
        killer.sendTitle("§aTarget Eliminated", "§7+2 hearts", 5, 35, 10);
        killer.sendMessage("§a+2 hearts! §7You have §f1 minute §7to log off safely or keep hunting.");

        graceUntilMs.put(killerId, System.currentTimeMillis() + GRACE_MS);
        cancelGraceTask(killerId);
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> endGraceWindow(killerId), 20L * 60L).getTaskId();
        graceTaskIds.put(killerId, taskId);

        checkForWinner();
    }

    private void markProtectedDeath(UUID victimId) {
        skipNormalDeathPenalty.add(victimId);
        skipBountyForVictim.add(victimId);
    }

    private void endGraceWindow(UUID killerId) {
        graceTaskIds.remove(killerId);
        if (!active || !isParticipant(killerId)) {
            return;
        }
        graceUntilMs.remove(killerId);
        Player killer = Bukkit.getPlayer(killerId);
        if (killer != null && killer.isOnline()) {
            killer.sendMessage("§cGrace window ended. §7New target assigned — logging off now will cost §c3 hearts§7.");
            killer.sendTitle("§4New Target", "§7Assigned", 5, 30, 10);
        }
        assignNewTarget(killerId);
    }

    private void cancelGraceTask(UUID playerId) {
        Integer taskId = graceTaskIds.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void eliminate(UUID playerId, String message, boolean clearHuntersOfVictim) {
        cancelGraceTask(playerId);
        participants.remove(playerId);
        eliminated.add(playerId);
        removeEliminatedAsHunter(playerId);
        targetToHunter.remove(playerId);
        if (clearHuntersOfVictim) {
            hunterToTarget.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        }
        graceUntilMs.remove(playerId);
        abilityHudUntilMs.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && message != null) {
            player.sendMessage("§4§lAssassin Event §7— " + message);
        }
    }

    private void removeEliminatedAsHunter(UUID playerId) {
        UUID target = hunterToTarget.remove(playerId);
        if (target == null) {
            return;
        }
        UUID mappedHunter = targetToHunter.get(target);
        if (playerId.equals(mappedHunter)) {
            targetToHunter.remove(target);
        }
    }

    private void eliminatePermanent(UUID playerId, String message) {
        eliminate(playerId, message, true);
    }

    private void rebuildAssignments() {
        hunterToTarget.clear();
        targetToHunter.clear();

        List<UUID> hunters = new ArrayList<>();
        for (UUID id : participants) {
            if (!isInGrace(id)) {
                hunters.add(id);
            }
        }
        if (hunters.size() < 2) {
            return;
        }

        Collections.shuffle(hunters);
        for (UUID hunterId : hunters) {
            assignNewTarget(hunterId);
        }
    }

    private void reassignTargetForHunter(UUID hunterId, String message) {
        if (hunterId == null || !isParticipant(hunterId) || isInGrace(hunterId)) {
            return;
        }
        if (!assignNewTarget(hunterId)) {
            return;
        }
        Player hunterPlayer = Bukkit.getPlayer(hunterId);
        if (hunterPlayer != null && message != null) {
            hunterPlayer.sendMessage(message);
        }
    }

    private boolean assignNewTarget(UUID hunterId) {
        Player hunter = Bukkit.getPlayer(hunterId);
        if (hunter == null || !isParticipant(hunterId) || isInGrace(hunterId)) {
            return false;
        }

        List<UUID> candidates = collectTargetCandidates(hunter, true);
        if (candidates.isEmpty()) {
            candidates = collectTargetCandidates(hunter, false);
        }
        if (candidates.isEmpty()) {
            clearHunterAssignment(hunterId);
            return false;
        }

        UUID targetId = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        setAssignment(hunterId, targetId);
        return true;
    }

    private List<UUID> collectTargetCandidates(Player hunter, boolean preferUnhunted) {
        List<UUID> all = new ArrayList<>();
        List<UUID> unhunted = new ArrayList<>();
        for (UUID candidateId : participants) {
            if (candidateId.equals(hunter.getUniqueId())) {
                continue;
            }
            Player candidate = Bukkit.getPlayer(candidateId);
            if (candidate == null || !candidate.isOnline()) {
                continue;
            }
            if (tabTeamResolver.isSameTeam(hunter, candidate)) {
                continue;
            }
            all.add(candidateId);
            if (!targetToHunter.containsKey(candidateId)) {
                unhunted.add(candidateId);
            }
        }
        if (preferUnhunted && !unhunted.isEmpty()) {
            return unhunted;
        }
        return all;
    }

    private void setAssignment(UUID hunterId, UUID targetId) {
        clearHunterAssignment(hunterId);

        UUID existingHunter = targetToHunter.get(targetId);
        if (existingHunter != null && !existingHunter.equals(hunterId)) {
            hunterToTarget.remove(existingHunter);
        }

        hunterToTarget.put(hunterId, targetId);
        targetToHunter.put(targetId, hunterId);
    }

    private void clearHunterAssignment(UUID hunterId) {
        UUID oldTarget = hunterToTarget.remove(hunterId);
        if (oldTarget == null) {
            return;
        }
        UUID mappedHunter = targetToHunter.get(oldTarget);
        if (hunterId.equals(mappedHunter)) {
            targetToHunter.remove(oldTarget);
        }
    }

    private void checkForWinner() {
        if (!active) {
            return;
        }
        List<UUID> remaining = new ArrayList<>(participants);
        if (remaining.size() == 1) {
            UUID winnerId = remaining.get(0);
            Player winner = Bukkit.getPlayer(winnerId);
            resetEvent();
            if (winner != null && winner.isOnline()) {
                heartService.grantAssassinWinBonus(winner, WIN_BONUS_HEARTS);
                Bukkit.broadcastMessage("§6§l" + winner.getName() + " §awins the Assassin Event! §7Permanent §c+3 max hearts§7.");
                winner.sendTitle("§6§lVictory", "§a+3 permanent max hearts", 10, 80, 20);
            }
            plugin.getLogger().info("Assassin event ended. Winner: " + winnerId);
        } else if (remaining.isEmpty()) {
            resetEvent();
            Bukkit.broadcastMessage("§4§lAssassin Event §7ended with no winner.");
        }
    }

    public String formatHudLine(Player player) {
        long grace = getGraceRemainingMs(player.getUniqueId());
        if (grace > 0) {
            long seconds = (grace + 999) / 1000;
            return "§aSafe logout: §f" + seconds + "s";
        }
        UUID targetId = getTarget(player.getUniqueId());
        if (targetId == null) {
            return "§7Assassin §8| §eNo target yet";
        }
        if (!isParticipant(targetId)) {
            Player eliminatedTarget = Bukkit.getPlayer(targetId);
            String name = eliminatedTarget != null ? eliminatedTarget.getName() : "Unknown";
            return "§7Assassin §8| §e" + name + " §8(eliminated)";
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return "§7Assassin §8| §eTarget offline";
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return "§4☠ §f" + target.getName() + " §8| §eother world";
        }
        int distance = (int) player.getLocation().distance(target.getLocation());
        return "§4☠ §f" + target.getName() + " §8| §c" + distance + "m";
    }
}
