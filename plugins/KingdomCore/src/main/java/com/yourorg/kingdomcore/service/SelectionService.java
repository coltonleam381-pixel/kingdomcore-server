package com.yourorg.kingdomcore.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionService {
    private static final int MAX_AXIS = 25;
    private static final int MAX_VOLUME = MAX_AXIS * MAX_AXIS * MAX_AXIS;

    private final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();

    public void setPos1(Player player, Location location) {
        getOrCreate(player).pos1 = location.clone();
    }

    public void setPos2(Player player, Location location) {
        getOrCreate(player).pos2 = location.clone();
    }

    public int copy(Player player) {
        PlayerSelection selection = getOrCreate(player);
        Bounds bounds = requireBounds(player, selection);
        if (bounds == null) {
            return -1;
        }
        World world = bounds.world();
        List<ClipboardBlock> blocks = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new ClipboardBlock(
                            x - bounds.minX(),
                            y - bounds.minY(),
                            z - bounds.minZ(),
                            block.getBlockData().clone()
                    ));
                }
            }
        }
        selection.clipboard = new Clipboard(world.getUID(), bounds.sizeX(), bounds.sizeZ(), blocks);
        selection.lastPlacement = null;
        return blocks.size();
    }

    public int paste(Player player, Location anchor) {
        PlayerSelection selection = getOrCreate(player);
        Clipboard clipboard = selection.clipboard;
        if (clipboard == null) {
            player.sendMessage("§cCopy a selection first with §f/select copy§c.");
            return -1;
        }
        World world = anchor.getWorld();
        if (world == null || !world.getUID().equals(clipboard.worldId())) {
            player.sendMessage("§cPaste must be in the same world as the copied selection.");
            return -1;
        }
        int placed = placeClipboard(world, anchor, clipboard);
        selection.lastPlacement = PastePlacement.from(anchor, clipboard);
        return placed;
    }

    public boolean rotate(Player player) {
        PlayerSelection selection = getOrCreate(player);
        Clipboard clipboard = selection.clipboard;
        if (clipboard == null) {
            player.sendMessage("§cCopy a selection first with §f/select copy§c.");
            return false;
        }
        Clipboard rotated = rotateClipboard(clipboard);
        selection.clipboard = rotated;

        PastePlacement lastPlacement = selection.lastPlacement;
        if (lastPlacement == null) {
            return false;
        }
        World world = Bukkit.getWorld(lastPlacement.worldId());
        if (world == null) {
            player.sendMessage("§cCould not find the world where the selection was pasted.");
            return false;
        }
        clearPlacement(world, lastPlacement);
        Location anchor = new Location(world, lastPlacement.anchorX(), lastPlacement.anchorY(), lastPlacement.anchorZ());
        placeClipboard(world, anchor, rotated);
        selection.lastPlacement = PastePlacement.from(anchor, rotated);
        return true;
    }

    public int move(Player player, Location anchor) {
        PlayerSelection selection = getOrCreate(player);
        Clipboard clipboard = selection.clipboard;
        if (clipboard == null) {
            player.sendMessage("§cCopy a selection first with §f/select copy§c.");
            return -1;
        }
        World world = anchor.getWorld();
        if (world == null || !world.getUID().equals(clipboard.worldId())) {
            player.sendMessage("§cMove must be in the same world as the copied selection.");
            return -1;
        }
        PastePlacement lastPlacement = selection.lastPlacement;
        if (lastPlacement != null && lastPlacement.worldId().equals(world.getUID())) {
            clearPlacement(world, lastPlacement);
        }
        int placed = placeClipboard(world, anchor, clipboard);
        selection.lastPlacement = PastePlacement.from(anchor, clipboard);
        return placed;
    }

    public boolean hasClipboard(Player player) {
        Clipboard clipboard = getOrCreate(player).clipboard;
        return clipboard != null && !clipboard.blocks().isEmpty();
    }

    private PlayerSelection getOrCreate(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerSelection());
    }

    private Bounds requireBounds(Player player, PlayerSelection selection) {
        if (selection.pos1 == null || selection.pos2 == null) {
            player.sendMessage("§cSet both corners with §f/select 1 §cand §f/select 2§c.");
            return null;
        }
        if (selection.pos1.getWorld() == null || selection.pos2.getWorld() == null
                || !selection.pos1.getWorld().equals(selection.pos2.getWorld())) {
            player.sendMessage("§cBoth selection corners must be in the same world.");
            return null;
        }
        Bounds bounds = Bounds.from(selection.pos1, selection.pos2);
        if (bounds.sizeX() > MAX_AXIS || bounds.sizeY() > MAX_AXIS || bounds.sizeZ() > MAX_AXIS) {
            player.sendMessage("§cSelection too large. Max size per axis is §f" + MAX_AXIS + "§c.");
            return null;
        }
        if (bounds.volume() > MAX_VOLUME) {
            player.sendMessage("§cSelection too large. Max volume is §f" + MAX_VOLUME + "§c blocks.");
            return null;
        }
        return bounds;
    }

    private Clipboard rotateClipboard(Clipboard clipboard) {
        int sizeX = clipboard.sizeX();
        List<ClipboardBlock> rotated = new ArrayList<>(clipboard.blocks().size());
        for (ClipboardBlock block : clipboard.blocks()) {
            int newX = block.dz();
            int newZ = (sizeX - 1) - block.dx();
            BlockData data = block.data().clone();
            try {
                data.rotate(StructureRotation.CLOCKWISE_90);
            } catch (IllegalArgumentException ignored) {
                // Some block types do not support rotation.
            }
            rotated.add(new ClipboardBlock(newX, block.dy(), newZ, data));
        }
        int newSizeX = clipboard.sizeZ();
        int newSizeZ = clipboard.sizeX();
        return new Clipboard(clipboard.worldId(), newSizeX, newSizeZ, rotated);
    }

    private int placeClipboard(World world, Location anchor, Clipboard clipboard) {
        int placed = 0;
        for (ClipboardBlock block : clipboard.blocks()) {
            Block target = world.getBlockAt(
                    anchor.getBlockX() + block.dx(),
                    anchor.getBlockY() + block.dy(),
                    anchor.getBlockZ() + block.dz()
            );
            target.setBlockData(block.data());
            placed++;
        }
        return placed;
    }

    private void clearPlacement(World world, PastePlacement placement) {
        for (ClipboardBlock block : placement.blocks()) {
            world.getBlockAt(
                    placement.anchorX() + block.dx(),
                    placement.anchorY() + block.dy(),
                    placement.anchorZ() + block.dz()
            ).setType(org.bukkit.Material.AIR);
        }
    }

    private static final class PlayerSelection {
        private Location pos1;
        private Location pos2;
        private Clipboard clipboard;
        private PastePlacement lastPlacement;
    }

    private record Bounds(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds from(Location a, Location b) {
            World world = a.getWorld();
            int minX = Math.min(a.getBlockX(), b.getBlockX());
            int minY = Math.min(a.getBlockY(), b.getBlockY());
            int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
            int maxX = Math.max(a.getBlockX(), b.getBlockX());
            int maxY = Math.max(a.getBlockY(), b.getBlockY());
            int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
            return new Bounds(world, minX, minY, minZ, maxX, maxY, maxZ);
        }

        int sizeX() {
            return maxX - minX + 1;
        }

        int sizeY() {
            return maxY - minY + 1;
        }

        int sizeZ() {
            return maxZ - minZ + 1;
        }

        int volume() {
            return sizeX() * sizeY() * sizeZ();
        }
    }

    private record Clipboard(UUID worldId, int sizeX, int sizeZ, List<ClipboardBlock> blocks) {
    }

    private record PastePlacement(UUID worldId, int anchorX, int anchorY, int anchorZ, List<ClipboardBlock> blocks) {
        static PastePlacement from(Location anchor, Clipboard clipboard) {
            return new PastePlacement(
                    anchor.getWorld().getUID(),
                    anchor.getBlockX(),
                    anchor.getBlockY(),
                    anchor.getBlockZ(),
                    List.copyOf(clipboard.blocks())
            );
        }
    }

    private record ClipboardBlock(int dx, int dy, int dz, BlockData data) {
    }
}
