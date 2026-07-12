package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ice Nova ability - surface transform to BLUE_ICE with layered restoration.
 * Cooldown starts when the active ice duration ends.
 */
public class IceNovaAbility implements AbilityHandler, CooldownOverrideAbility, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;

    private final Map<Block, Deque<BlockSnapshotLayer>> activeLayers = new HashMap<>();
    private final Map<UUID, IceNovaCastMeta> castMetadata = new HashMap<>();
    private final Map<UUID, Long> activeEndsAtByPlayer = new ConcurrentHashMap<>();

    public IceNovaAbility(Plugin plugin, WorldGuardHook worldGuardHook, CooldownService cooldownService, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @Override
    public String getAbilityId() {
        return "ice_nova";
    }

    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        if (level == 0) {
            return false;
        }
        Long activeUntil = activeEndsAtByPlayer.get(player.getUniqueId());
        if (activeUntil != null && System.currentTimeMillis() < activeUntil) {
            return false;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }

        int radius = switch (level) {
            case 1 -> 5;
            case 2 -> 7;
            case 3 -> 9;
            default -> 5;
        };

        long durationTicks = switch (level) {
            case 1 -> 100L;
            case 2 -> 140L;
            case 3 -> 180L;
            default -> 100L;
        };

        Set<Block> affectedBlocks = collectBlocksInRadius(player.getLocation(), radius);
        if (affectedBlocks.isEmpty()) {
            return false;
        }

        UUID castId = UUID.randomUUID();
        Set<Block> transformedBlocks = new HashSet<>();

        for (Block block : affectedBlocks) {
            if (isProtectedBlock(block)) {
                continue;
            }
            if (spawnRegionPolicy.blocksAbilities(block.getLocation())) {
                continue;
            }

            Deque<BlockSnapshotLayer> layers = activeLayers.computeIfAbsent(block, k -> new LinkedList<>());
            BlockState baseOriginal = layers.isEmpty() ? block.getState() : layers.getLast().previousState;
            layers.push(new BlockSnapshotLayer(castId, player.getUniqueId(), level, baseOriginal));

            block.setType(Material.BLUE_ICE, false); // no melt-to-water behavior
            transformedBlocks.add(block);
        }

        if (transformedBlocks.isEmpty()) {
            return false;
        }

        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.9f, 0.8f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                player.getLocation().add(0, 1, 0), 45, radius * 0.25, 0.7, radius * 0.25, 0.02);

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, false));

        BukkitTask revertTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> revertCast(castId), durationTicks);
        castMetadata.put(castId, new IceNovaCastMeta(player.getUniqueId(), level, transformedBlocks, revertTask));

        long endAt = System.currentTimeMillis() + (durationTicks * 50L);
        activeEndsAtByPlayer.merge(player.getUniqueId(), endAt, Math::max);
        return true;
    }

    @Override
    public long getCooldownMs(int level) {
        return switch (level) {
            case 1 -> 50000L;
            case 2 -> 50000L;
            case 3 -> 40000L;
            default -> 50000L;
        };
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        Long endAt = activeEndsAtByPlayer.get(playerId);
        if (endAt == null) {
            return null;
        }
        long leftMs = Math.max(0L, endAt - nowMs);
        if (leftMs == 0L) {
            return null;
        }
        long sec = (leftMs + 999L) / 1000L;
        return "§bIce Nova §8| §e" + sec + "s";
    }

    @Override
    public boolean onLeftClick(Player player, int level) {
        return false;
    }

    @Override
    public boolean onSneakRightClick(Player player, int level) {
        return false;
    }

    @Override
    public boolean onSpace(Player player) {
        return false;
    }

    @Override
    public void cleanup(Player player) {
        activeEndsAtByPlayer.remove(player.getUniqueId());
    }

    public int getIceLevelAt(Block block) {
        Deque<BlockSnapshotLayer> layers = activeLayers.get(block);
        if (layers == null || layers.isEmpty()) {
            return -1;
        }
        return layers.peek().level;
    }

    public UUID getTopOwnerAt(Block block) {
        Deque<BlockSnapshotLayer> layers = activeLayers.get(block);
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        return layers.peek().ownerId;
    }

    public boolean isIceNovaBlock(Block block) {
        Deque<BlockSnapshotLayer> layers = activeLayers.get(block);
        return layers != null && !layers.isEmpty();
    }

    private void revertCast(UUID castId) {
        IceNovaCastMeta castMeta = castMetadata.remove(castId);
        if (castMeta == null) {
            return;
        }

        for (Block block : castMeta.affectedBlocks) {
            Deque<BlockSnapshotLayer> layers = activeLayers.get(block);
            if (layers == null || layers.isEmpty()) {
                continue;
            }

            BlockSnapshotLayer removedLayer = null;
            Iterator<BlockSnapshotLayer> iter = layers.iterator();
            while (iter.hasNext()) {
                BlockSnapshotLayer layer = iter.next();
                if (layer.castId.equals(castId)) {
                    removedLayer = layer;
                    iter.remove();
                    break;
                }
            }
            if (removedLayer == null) {
                continue;
            }

            if (layers.isEmpty()) {
                removedLayer.previousState.update(true, false);
                activeLayers.remove(block);
            }
        }

        cooldownService.markUsed(castMeta.ownerId, getAbilityId(), System.currentTimeMillis() + getCooldownMs(castMeta.level));

        boolean stillActiveForOwner = false;
        for (IceNovaCastMeta meta : castMetadata.values()) {
            if (meta.ownerId.equals(castMeta.ownerId)) {
                stillActiveForOwner = true;
                break;
            }
        }
        if (!stillActiveForOwner) {
            activeEndsAtByPlayer.remove(castMeta.ownerId);
        }
    }

    private Set<Block> collectBlocksInRadius(Location center, int radius) {
        Set<Block> blocks = new HashSet<>();
        int radiusSquared = radius * radius;
        int yLayer = center.getBlockY() - 1; // cast base layer
        int yBottom = yLayer - 3; // requested: cover up to 3 blocks below base
        int yTop = yLayer + 5; // include up to +5 blocks above base if solid

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                int dx = x - center.getBlockX();
                int dz = z - center.getBlockZ();
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                for (int y = yBottom; y <= yTop; y++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    if (!isValidBlockToTransform(block)) {
                        continue;
                    }
                    blocks.add(block);
                }
            }
        }

        return blocks;
    }

    private boolean isValidBlockToTransform(Block block) {
        Material type = block.getType();
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return false;
        }
        return type.isSolid();
    }

    private boolean isProtectedBlock(Block block) {
        Material type = block.getType();
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL -> true;
            case SPAWNER -> true;
            case BEDROCK -> true;
            case OBSIDIAN, CRYING_OBSIDIAN -> true;
            case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> true;
            case STRUCTURE_BLOCK -> true;
            case FURNACE, BLAST_FURNACE, SMOKER -> true;
            case DROPPER, DISPENSER, HOPPER -> true;
            case ENCHANTING_TABLE, BREWING_STAND, BEACON -> true;
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> true;
            default -> false;
        };
    }

    private static class BlockSnapshotLayer {
        final UUID castId;
        final UUID ownerId;
        final int level;
        final BlockState previousState;

        BlockSnapshotLayer(UUID castId, UUID ownerId, int level, BlockState previousState) {
            this.castId = castId;
            this.ownerId = ownerId;
            this.level = level;
            this.previousState = previousState;
        }
    }

    private static class IceNovaCastMeta {
        final UUID ownerId;
        final int level;
        final Set<Block> affectedBlocks;
        final BukkitTask revertTask;

        IceNovaCastMeta(UUID ownerId, int level, Set<Block> affectedBlocks, BukkitTask revertTask) {
            this.ownerId = ownerId;
            this.level = level;
            this.affectedBlocks = affectedBlocks;
            this.revertTask = revertTask;
        }
    }
}
