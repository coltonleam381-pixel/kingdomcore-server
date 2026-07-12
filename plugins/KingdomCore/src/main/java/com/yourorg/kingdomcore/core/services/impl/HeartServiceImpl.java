package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.WithdrawResult;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.util.HeartRules;
import com.yourorg.kingdomcore.util.InventoryFit;
import com.yourorg.kingdomcore.util.NameNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeartServiceImpl implements HeartService {
    private final PlayerStateRepository repository;
    private final ItemIdentityService itemIdentityService;
    private final HealthService healthService;
    private final int startingHearts;
    private final int baseCap;
    private final Map<UUID, PlayerState> cache = new ConcurrentHashMap<>();

    public HeartServiceImpl(PlayerStateRepository repository,
                            ItemIdentityService itemIdentityService,
                            HealthService healthService,
                            int startingHearts,
                            int baseCap) {
        this.repository = repository;
        this.itemIdentityService = itemIdentityService;
        this.healthService = healthService;
        this.startingHearts = startingHearts;
        this.baseCap = baseCap;
    }

    @Override
    public PlayerState getOrCreateState(UUID playerId, String lastName) {
        return cache.computeIfAbsent(playerId, key -> repository.findById(playerId).map(existing -> {
            int clamped = HeartRules.clampProgression(existing.getProgressionHearts(), progressionCap(existing));
            if (clamped != existing.getProgressionHearts()) {
                existing.setProgressionHearts(clamped);
                repository.updateProgression(existing.getUuid(), clamped, clamped <= 0);
            }
            return existing;
        }).orElseGet(() -> {
            PlayerState state = new PlayerState(playerId);
            state.setLastName(NameNormalizer.normalize(lastName));
            state.setAbilityId(null);
            state.setAbilityLevel(0);
            int clamped = HeartRules.clampProgression(startingHearts, baseCap);
            state.setProgressionHearts(clamped);
            state.setBlocked(clamped <= 0);
            repository.upsert(state);
            return state;
        }));
    }

    @Override
    public boolean consumeHeartItem(Player player, ItemStack stack, int amount) {
        if (player == null || stack == null || amount <= 0) {
            return false;
        }
        if (!itemIdentityService.isHeartItem(stack)) {
            return false;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        int current = state.getProgressionHearts();
        if (!HeartRules.canConsume(current, progressionCap(state))) {
            return false;
        }
        int next = HeartRules.clampProgression(current + amount, progressionCap(state));
        if (next == current) {
            return false;
        }
        state.setProgressionHearts(next);
        state.setBlocked(false);
        repository.updateProgression(player.getUniqueId(), next, false);
        healthService.applyHealth(player, state);
        int remaining = stack.getAmount() - amount;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(remaining);
        }
        return true;
    }

    @Override
    public boolean tryUpgradeAbility(Player player, int cost) {
        if (player == null || cost <= 0) {
            return false;
        }

        int totalHearts = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (itemIdentityService.isHeartItem(stack)) {
                totalHearts += stack.getAmount();
                if (totalHearts >= cost) {
                    break;
                }
            }
        }

        if (totalHearts < cost) {
            return false;
        }

        int toRemove = cost;
        for (int slot = 0; slot < contents.length && toRemove > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!itemIdentityService.isHeartItem(stack)) {
                continue;
            }

            int stackAmount = stack.getAmount();
            if (stackAmount <= toRemove) {
                player.getInventory().setItem(slot, null);
                toRemove -= stackAmount;
            } else {
                stack.setAmount(stackAmount - toRemove);
                player.getInventory().setItem(slot, stack);
                toRemove = 0;
            }
        }

        return toRemove == 0;
    }

    @Override
    public boolean applyDeathPenalty(Player player, String kickMessage) {
        if (player == null) {
            return false;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        int current = state.getProgressionHearts();
        if (current <= 0) {
            return false;
        }
        int progression = HeartRules.clampProgression(current - 1, progressionCap(state));
        state.setProgressionHearts(progression);
        boolean blocked = HeartRules.shouldBlockAfterPenalty(current);
        state.setBlocked(blocked);
        repository.updateProgression(player.getUniqueId(), progression, blocked);
        healthService.applyHealth(player, state);
        if (blocked) {
            player.kickPlayer(kickMessage != null ? kickMessage : "Blocked");
        }
        return blocked;
    }

    @Override
    public boolean applyKillHeartTransfer(Player victim, Player killer, UUID killerId, Location dropLocation, String blockedKickMessage) {
        boolean blocked = applyDeathPenalty(victim, blockedKickMessage);

        UUID rewardId = killer != null ? killer.getUniqueId() : killerId;
        if (rewardId == null || victim == null || rewardId.equals(victim.getUniqueId())) {
            return blocked;
        }

        grantKillHeartReward(killer, rewardId, dropLocation, victim.getName());
        return blocked;
    }

    private void grantKillHeartReward(Player killerOnline, UUID killerId, Location dropLocation, String victimName) {
        String killerName = killerOnline != null ? killerOnline.getName() : resolveStoredName(killerId);
        PlayerState state = getOrCreateState(killerId, killerName);

        if (HeartRules.canConsume(state.getProgressionHearts(), progressionCap(state))) {
            int next = HeartRules.clampProgression(state.getProgressionHearts() + 1, progressionCap(state));
            state.setProgressionHearts(next);
            state.setBlocked(false);
            repository.updateProgression(killerId, next, false);
            if (killerOnline != null && killerOnline.isOnline()) {
                healthService.applyHealth(killerOnline, state);
                killerOnline.sendMessage("§a+1 heart §7for eliminating §f" + victimName + "§7.");
            }
            return;
        }

        if (dropLocation != null && dropLocation.getWorld() != null) {
            dropLocation.getWorld().dropItemNaturally(dropLocation, itemIdentityService.createHeartItem(1));
            if (killerOnline != null && killerOnline.isOnline()) {
                killerOnline.sendMessage("§eYou are at max hearts. The heart dropped on the ground.");
            }
        }
    }

    private String resolveStoredName(UUID playerId) {
        PlayerState state = cache.get(playerId);
        if (state != null && state.getLastName() != null) {
            return state.getLastName();
        }
        return repository.findById(playerId).map(PlayerState::getLastName).orElse("");
    }

    @Override
    public WithdrawResult withdrawHearts(Player player, int amount) {
        if (player == null || amount <= 0) {
            return WithdrawResult.INVALID_AMOUNT;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        if (state.getProgressionHearts() - amount <= 0) {
            return WithdrawResult.NOT_ENOUGH_HEARTS;
        }
        ItemStack toGive = itemIdentityService.createHeartItem(amount);
        if (!InventoryFit.canFit(player, toGive)) {
            return WithdrawResult.INVENTORY_FULL;
        }
        int newProgression = HeartRules.clampProgression(state.getProgressionHearts() - amount, progressionCap(state));
        state.setProgressionHearts(newProgression);
        repository.updateProgression(player.getUniqueId(), newProgression, false);
        player.getInventory().addItem(toGive);
        healthService.applyHealth(player, state);
        healthService.clampToMax(player, state);
        return WithdrawResult.SUCCESS;
    }

    @Override
    public WithdrawResult withdrawHeartsFrom(UUID sourceId, String sourceName, Player receiver, int amount) {
        if (sourceId == null || receiver == null || amount <= 0) {
            return WithdrawResult.INVALID_AMOUNT;
        }
        if (sourceId.equals(receiver.getUniqueId())) {
            return withdrawHearts(receiver, amount);
        }

        String resolvedName = sourceName == null || sourceName.isBlank()
                ? resolveStoredName(sourceId)
                : NameNormalizer.normalize(sourceName);
        PlayerState state = getOrCreateState(sourceId, resolvedName);
        if (state.getProgressionHearts() - amount <= 0) {
            return WithdrawResult.NOT_ENOUGH_HEARTS;
        }

        ItemStack toGive = itemIdentityService.createHeartItem(amount);
        if (!InventoryFit.canFit(receiver, toGive)) {
            return WithdrawResult.INVENTORY_FULL;
        }

        int newProgression = HeartRules.clampProgression(state.getProgressionHearts() - amount, progressionCap(state));
        state.setProgressionHearts(newProgression);
        repository.updateProgression(sourceId, newProgression, false);

        Player sourceOnline = Bukkit.getPlayer(sourceId);
        if (sourceOnline != null && sourceOnline.isOnline()) {
            healthService.applyHealth(sourceOnline, state);
            healthService.clampToMax(sourceOnline, state);
        }

        Map<Integer, ItemStack> leftovers = receiver.getInventory().addItem(toGive);
        if (!leftovers.isEmpty()) {
            Location dropLocation = receiver.getLocation();
            leftovers.values().forEach(item -> dropLocation.getWorld().dropItemNaturally(dropLocation, item));
        }
        return WithdrawResult.SUCCESS;
    }

    @Override
    public void addHearts(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        int newProgression = HeartRules.clampProgression(state.getProgressionHearts() + amount, progressionCap(state));
        state.setProgressionHearts(newProgression);
        repository.updateProgression(player.getUniqueId(), newProgression, false);
        healthService.applyHealth(player, state);
    }

    @Override
    public void removeHearts(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        if (state.getProgressionHearts() - amount < 0) {
            return;
        }
        int newProgression = HeartRules.clampProgression(state.getProgressionHearts() - amount, progressionCap(state));
        state.setProgressionHearts(newProgression);
        repository.updateProgression(player.getUniqueId(), newProgression, false);
        healthService.applyHealth(player, state);
        healthService.clampToMax(player, state);
    }

    @Override
    public void updateAbilityLevel(UUID playerId, int newLevel) {
        PlayerState state = cache.get(playerId);
        String abilityId = null;
        if (state == null) {
            state = repository.findById(playerId).orElse(null);
        }
        if (state != null) {
            abilityId = state.getAbilityId();
        }
        updateAbilityState(playerId, abilityId, newLevel);
    }

    @Override
    public void updateAbilityState(UUID playerId, String abilityId, int level) {
        PlayerState state = cache.get(playerId);
        if (state != null) {
            state.setAbilityId(abilityId);
            state.setAbilityLevel(level);
        }
        repository.updateAbility(playerId, abilityId, level);
    }
    
    @Override
    public void updateLastName(UUID playerId, String name) {
        String normalized = NameNormalizer.normalize(name);
        PlayerState state = cache.get(playerId);
        if (state != null) {
            state.setLastName(normalized);
        }
        repository.updateLastName(playerId, normalized);
    }
    @Override
    public void invalidateCache(UUID playerId) {
        cache.remove(playerId);
    }
    
    @Override
    public void setBlocked(UUID playerId, boolean blocked) {
        PlayerState state = cache.get(playerId);
        if (state != null) {
            state.setBlocked(blocked);
        } else {
            state = repository.findById(playerId).orElse(null);
            if (state != null) {
                state.setBlocked(blocked);
            }
        }
        if (state != null) {
            repository.updateProgression(playerId, state.getProgressionHearts(), blocked);
        }
    }

    @Override
    public void setProgressionAndBlocked(UUID playerId, int hearts, boolean blocked) {
        PlayerState state = cache.get(playerId);
        if (state != null) {
            state.setProgressionHearts(hearts);
            state.setBlocked(blocked);
        } else {
            state = repository.findById(playerId).orElse(null);
            if (state != null) {
                state.setProgressionHearts(hearts);
                state.setBlocked(blocked);
            }
        }
        if (state != null) {
            repository.updateProgression(playerId, hearts, blocked);
        } else {
            // It could be an offline player whose state isn't even in DB? 
            // In ReviveService, the state is known to exist.
            repository.updateProgression(playerId, hearts, blocked);
        }
    }

    @Override
    public void grantAssassinWinBonus(Player player, int bonusHearts) {
        if (player == null || bonusHearts <= 0) {
            return;
        }
        PlayerState state = getOrCreateState(player.getUniqueId(), player.getName());
        state.setAssassinWinBonus(bonusHearts);
        repository.updateAssassinWinBonus(player.getUniqueId(), bonusHearts);
        healthService.applyHealth(player, state);
    }

    private int progressionCap(PlayerState state) {
        int bonus = state == null ? 0 : state.getAssassinWinBonus();
        return baseCap + bonus;
    }
}
