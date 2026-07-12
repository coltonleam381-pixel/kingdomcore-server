package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thor ability: mark targets with RMB, strike with LMB.
 * Cooldown starts only on strike attempt or mark timeout.
 */
public class ThorAbility implements AbilityHandler, AbilityHudProvider {
    private static final double BASE_MARK_RANGE = 15.0;
    private static final double MARK_HITBOX_SIZE = 0.22;

    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, ThorState> activeMarks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hudHoldUntilMs = new ConcurrentHashMap<>();

    public ThorAbility(Plugin plugin,
                       WorldGuardHook worldGuardHook,
                       CooldownService cooldownService,
                       SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "thor";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            fail(player, "Cannot use Thor in spawn.");
            return false;
        }

        return markTarget(player, level);
    }

    @Override
    public boolean onLeftClick(Player player, int level) {
        if (level == 0) {
            return false;
        }

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        return strikeMarked(player, level);
    }

    public String getHudLine(UUID playerId, long nowMs) {
        ThorState state = activeMarks.get(playerId);
        if (state == null || state.marked.isEmpty()) {
            return null;
        }
        long remainingMs = Math.max(0L, state.expiresAtMs - nowMs);
        long remainingSec = (remainingMs + 999L) / 1000L;
        return "§b⚡ Thor Marks: §f" + state.marked.size() + " §8| §e" + remainingSec + "s";
    }

    private boolean markTarget(Player player, int level) {
        LivingEntity target = getTargetEntity(player, markRangeForLevel(level));
        if (target == null) {
            fail(player, "No target in range.", 1000L);
            return false;
        }

        ThorState state = activeMarks.computeIfAbsent(player.getUniqueId(), key -> new ThorState());
        int cap = markCap(level);
        if (state.marked.size() >= cap) {
            fail(player, "Mark cap reached.", 1000L);
            return false;
        }

        if (state.marked.contains(target)) {
            fail(player, "Target already marked.");
            return false;
        }

        state.level = level;
        state.marked.add(target);
        showMarkParticles(target);

        if (state.expiryTaskId == -1) {
            state.expiresAtMs = System.currentTimeMillis() + markDurationMs(level);
            state.expiryTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    ThorState expired = activeMarks.remove(player.getUniqueId());
                    if (expired != null) {
                        fail(player, "Thor mark expired.");
                        markAbilityCooldown(player.getUniqueId(), expired.level);
                    }
                }
            }.runTaskLater(plugin, markDurationTicks(level)).getTaskId();

            state.particleTaskId = startMarkParticleLoop(player);
        }

        successMark(player, state.marked.size());
        return false;
    }

    private boolean strikeMarked(Player player, int level) {
        ThorState state = activeMarks.remove(player.getUniqueId());
        if (state == null || state.marked.isEmpty()) {
            return false;
        }

        if (state.expiryTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
        }
        if (state.particleTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(state.particleTaskId);
        }

        int lightningCount = switch (level) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 0;
        };

        double strikeDamage = strikeDamageForLevel(level);

        boolean hadValidTarget = false;
        for (LivingEntity target : state.marked) {
            if (target.isDead()) {
                continue;
            }
            if (spawnRegionPolicy.blocksAbilities(target.getLocation())) {
                continue;
            }
            hadValidTarget = true;

            Location loc = target.getLocation();
            for (int i = 0; i < lightningCount; i++) {
                loc.getWorld().strikeLightningEffect(loc);
            }

            if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                kcPlugin.getDamageService().applyTrueDamage(target, player, strikeDamage, loc);
            }
            target.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.2f);
            Vector kb = target.getVelocity().add(new Vector(0, 0.28, 0));
            target.setVelocity(kb);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.01);

            if (level >= 3 && target instanceof Player targetPlayer) {
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            }
        }

        // Silent strike but still starts cooldown on attempt.
        markAbilityCooldown(player.getUniqueId(), level);
        return hadValidTarget;
    }

    public static double strikeDamageForLevel(int level) {
        return switch (level) {
            case 1 -> 4.0; // 2 hearts
            case 2 -> 5.0; // 2.5 hearts
            case 3 -> 6.0; // 3 hearts
            default -> 0.0;
        };
    }

    private LivingEntity getTargetEntity(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                MARK_HITBOX_SIZE,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        if (result == null || !(result.getHitEntity() instanceof LivingEntity living)) {
            return null;
        }
        return living;
    }

    private void showMarkParticles(LivingEntity target) {
        Location loc = new Location(
                target.getWorld(),
                target.getLocation().getX(),
                target.getBoundingBox().getMaxY() + 0.08,
                target.getLocation().getZ()
        );
        for (Player viewer : target.getWorld().getPlayers()) {
            viewer.spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    loc,
                    3,
                    0.04,
                    0.05,
                    0.04,
                    0.0,
                    null,
                    true
            );
        }
    }

    private int startMarkParticleLoop(Player player) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                ThorState state = activeMarks.get(player.getUniqueId());
                if (state == null) {
                    cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                long holdUntil = hudHoldUntilMs.getOrDefault(player.getUniqueId(), 0L);
                if (now >= holdUntil) {
                    player.sendActionBar(getHudLine(player.getUniqueId(), now));
                }
                for (LivingEntity target : state.marked) {
                    if (!target.isDead()) {
                        showMarkParticles(target);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L).getTaskId();
    }

    @Override
    public void cleanup(Player player) {
        ThorState state = activeMarks.remove(player.getUniqueId());
        if (state != null) {
            if (state.expiryTaskId != -1) {
                plugin.getServer().getScheduler().cancelTask(state.expiryTaskId);
            }
            if (state.particleTaskId != -1) {
                plugin.getServer().getScheduler().cancelTask(state.particleTaskId);
            }
            state.marked.clear();
        }
    }

    private int markCap(int level) {
        return switch (level) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 7;
            default -> 1;
        };
    }

    private double markRangeForLevel(int level) {
        return switch (level) {
            case 1 -> 5.0;
            case 2 -> 6.0;
            case 3 -> 8.0;
            default -> BASE_MARK_RANGE;
        };
    }

    private long markDurationMs(int level) {
        return switch (level) {
            case 1 -> 5000L;
            case 2 -> 7000L;
            case 3 -> 10000L;
            default -> 5000L;
        };
    }

    private long cooldownMsForLevel(int level) {
        return switch (level) {
            case 1 -> 50000L;
            case 2 -> 45000L;
            case 3 -> 40000L;
            default -> 50000L;
        };
    }

    private int markDurationTicks(int level) {
        return (int) (markDurationMs(level) / 50L);
    }

    private void markAbilityCooldown(UUID playerId, int level) {
        long now = System.currentTimeMillis();
        cooldownService.markUsed(playerId, getAbilityId(), now + cooldownMsForLevel(level));
    }

    private void fail(Player player, String message) {
        fail(player, message, 0L);
    }

    private void fail(Player player, String message, long holdMs) {
        if (!player.isOnline()) {
            return;
        }
        if (holdMs > 0L) {
            hudHoldUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + holdMs);
        }
        player.sendActionBar("§c" + message);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
    }

    private void successMark(Player player, int marks) {
        if (!player.isOnline()) {
            return;
        }
        player.sendActionBar("§b⚡ Thor Marks: §f" + marks);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
    }

    private static class ThorState {
        final List<LivingEntity> marked = new ArrayList<>();
        int expiryTaskId = -1;
        int particleTaskId = -1;
        int level = 1;
        long expiresAtMs = 0L;
    }
}
