package com.yourorg.kingdomcore.util;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.ByteArrayTag;
import net.querz.nbt.tag.ByteTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.FloatTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.NumberTag;
import net.querz.nbt.tag.ShortTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class OfflinePlayerDataLoader {

    private OfflinePlayerDataLoader() {
    }

    public record OfflineInventorySnapshot(
            ItemStack[] storage,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offHand,
            Double health
    ) {
        public ItemStack[] allContents() {
            List<ItemStack> items = new ArrayList<>();
            if (storage != null) {
                for (ItemStack stack : storage) {
                    if (stack != null && !stack.getType().isAir()) {
                        items.add(stack);
                    }
                }
            }
            addIfPresent(items, helmet);
            addIfPresent(items, chestplate);
            addIfPresent(items, leggings);
            addIfPresent(items, boots);
            addIfPresent(items, offHand);
            return items.toArray(ItemStack[]::new);
        }

        private static void addIfPresent(List<ItemStack> items, ItemStack stack) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack);
            }
        }
    }

    public static Optional<OfflineInventorySnapshot> loadInventory(UUID playerId) {
        File playerDat = findPlayerDat(playerId);
        if (playerDat == null) {
            return Optional.empty();
        }

        try {
            NamedTag namedTag = NBTUtil.read(playerDat, true);
            if (!(namedTag.getTag() instanceof CompoundTag root)) {
                return Optional.empty();
            }

            int dataVersion = root.containsKey("DataVersion") ? root.getInt("DataVersion") : 0;
            ItemStack[] storage = new ItemStack[36];
            ItemStack helmet = null;
            ItemStack chestplate = null;
            ItemStack leggings = null;
            ItemStack boots = null;
            ItemStack offHand = null;

            ListTag<?> inventoryTag = root.getListTag("Inventory");
            if (inventoryTag != null) {
                for (Tag<?> entry : inventoryTag) {
                    if (!(entry instanceof CompoundTag itemTag)) {
                        continue;
                    }
                    try {
                        ItemStack stack = parseItem(itemTag, dataVersion);
                        if (stack == null || stack.getType().isAir()) {
                            continue;
                        }
                        int slot = readSlot(itemTag);
                        if (slot >= 0 && slot < 36) {
                            storage[slot] = stack;
                        } else if (slot == 100) {
                            boots = stack;
                        } else if (slot == 101) {
                            leggings = stack;
                        } else if (slot == 102) {
                            chestplate = stack;
                        } else if (slot == 103) {
                            helmet = stack;
                        } else if (slot == -106) {
                            offHand = stack;
                        }
                    } catch (Exception ignored) {
                        // skip malformed item entry
                    }
                }
            }

            CompoundTag equipment = root.getCompoundTag("equipment");
            if (equipment != null) {
                if (helmet == null) {
                    helmet = parseEquipmentPiece(equipment, "head", dataVersion);
                }
                if (chestplate == null) {
                    chestplate = parseEquipmentPiece(equipment, "chest", dataVersion);
                }
                if (leggings == null) {
                    leggings = parseEquipmentPiece(equipment, "legs", dataVersion);
                }
                if (boots == null) {
                    boots = parseEquipmentPiece(equipment, "feet", dataVersion);
                }
                if (offHand == null) {
                    offHand = parseEquipmentPiece(equipment, "offhand", dataVersion);
                }
            }

            Double health = root.containsKey("Health") ? (double) root.getFloat("Health") : null;
            return Optional.of(new OfflineInventorySnapshot(
                    storage,
                    helmet,
                    chestplate,
                    leggings,
                    boots,
                    offHand,
                    health
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static File findPlayerDat(UUID playerId) {
        String fileName = playerId + ".dat";
        for (World world : Bukkit.getWorlds()) {
            File candidate = new File(world.getWorldFolder(), "playerdata/" + fileName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        File fallback = new File(Bukkit.getWorldContainer(), "world/playerdata/" + fileName);
        return fallback.isFile() ? fallback : null;
    }

    private static ItemStack parseItem(CompoundTag compound, int dataVersion) {
        String id = compound.getString("id");
        if (id == null || id.isBlank() || "minecraft:air".equalsIgnoreCase(id)) {
            return null;
        }

        int count = 1;
        if (compound.containsKey("count")) {
            count = Math.max(1, readNumber(compound.get("count")));
        } else if (compound.containsKey("Count")) {
            count = Math.max(1, readNumber(compound.get("Count")));
        }

        Map<String, Object> serialized = new HashMap<>();
        if (dataVersion > 0) {
            serialized.put("v", dataVersion);
        }

        Material material = Material.matchMaterial(id);
        if (material == null && id.contains(":")) {
            material = Material.matchMaterial(id.substring(id.indexOf(':') + 1));
        }
        serialized.put("type", material != null ? material.name() : id);
        serialized.put("amount", count);

        if (compound.containsKey("components") && compound.get("components") instanceof CompoundTag components) {
            serialized.put("components", nbtToJava(components));
        } else if (compound.containsKey("tag") && compound.get("tag") instanceof CompoundTag legacyTag) {
            serialized.put("meta", nbtToJava(legacyTag));
        }

        try {
            return ItemStack.deserialize(serialized);
        } catch (Exception ignored) {
            return new ItemStack(material != null ? material : Material.STONE, count);
        }
    }

    private static Object nbtToJava(Tag<?> tag) {
        if (tag instanceof CompoundTag compound) {
            Map<String, Object> map = new HashMap<>();
            for (String key : compound.keySet()) {
                map.put(key, nbtToJava(compound.get(key)));
            }
            return map;
        }
        if (tag instanceof ListTag<?> listTag) {
            List<Object> list = new ArrayList<>(listTag.size());
            for (Tag<?> entry : listTag) {
                list.add(nbtToJava(entry));
            }
            return list;
        }
        if (tag instanceof StringTag stringTag) {
            return stringTag.getValue();
        }
        if (tag instanceof IntTag intTag) {
            return intTag.asInt();
        }
        if (tag instanceof ByteTag byteTag) {
            return byteTag.asInt();
        }
        if (tag instanceof ShortTag shortTag) {
            return shortTag.asInt();
        }
        if (tag instanceof LongTag longTag) {
            return longTag.asLong();
        }
        if (tag instanceof FloatTag floatTag) {
            return floatTag.asFloat();
        }
        if (tag instanceof DoubleTag doubleTag) {
            return doubleTag.asDouble();
        }
        if (tag instanceof ByteArrayTag byteArrayTag) {
            return byteArrayTag.getValue();
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            return intArrayTag.getValue();
        }
        if (tag instanceof LongArrayTag longArrayTag) {
            return longArrayTag.getValue();
        }
        return null;
    }

    private static ItemStack parseEquipmentPiece(CompoundTag equipment, String key, int dataVersion) {
        Tag<?> tag = equipment.get(key);
        if (!(tag instanceof CompoundTag itemTag)) {
            return null;
        }
        return parseItem(itemTag, dataVersion);
    }

    private static int readSlot(CompoundTag itemTag) {
        if (!itemTag.containsKey("Slot")) {
            return -1;
        }
        return readNumber(itemTag.get("Slot"));
    }

    private static int readNumber(Tag<?> tag) {
        if (tag instanceof NumberTag<?> numberTag) {
            return numberTag.asInt();
        }
        return 0;
    }
}
