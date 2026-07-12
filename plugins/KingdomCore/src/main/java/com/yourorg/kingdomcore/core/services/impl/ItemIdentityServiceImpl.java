package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.integrations.ItemsAdderHook;
import com.yourorg.kingdomcore.util.AbilityRenameTokens;
import com.yourorg.kingdomcore.util.ItemIdentityMatcher;
import com.yourorg.kingdomcore.util.ItemUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

public class ItemIdentityServiceImpl implements ItemIdentityService {
    private final ItemsAdderHook itemsAdder;
    private final NamespacedKey pdcKey;
    private final String heartId;
    private final String crownId;
    private final String reviveBeaconId;
    private final String maceId;
    private final String scytheId;
    private final String wardenCpId;
    private final String tridentId;

    public ItemIdentityServiceImpl(ItemsAdderHook itemsAdder, NamespacedKey pdcKey,
                                   String heartId, String crownId, String reviveBeaconId,
                                   String maceId, String scytheId, String wardenCpId, String tridentId) {
        this.itemsAdder = itemsAdder;
        this.pdcKey = pdcKey;
        this.heartId = heartId;
        this.crownId = crownId;
        this.reviveBeaconId = reviveBeaconId;
        this.maceId = maceId;
        this.scytheId = scytheId;
        this.wardenCpId = wardenCpId;
        this.tridentId = tridentId;
    }

    @Override
    public boolean isHeartItem(ItemStack stack) {
        return matchesId(stack, heartId);
    }

    @Override
    public boolean isCrownItem(ItemStack stack) {
        return matchesId(stack, crownId);
    }

    @Override
    public boolean isReviveBeacon(ItemStack stack) {
        return matchesId(stack, reviveBeaconId);
    }

    @Override
    public boolean isMaceItem(ItemStack stack) {
        return matchesId(stack, maceId);
    }

    @Override
    public boolean isScytheItem(ItemStack stack) {
        return matchesId(stack, scytheId);
    }

    @Override
    public boolean isWardenCpItem(ItemStack stack) {
        return matchesId(stack, wardenCpId);
    }

    @Override
    public boolean isTridentItem(ItemStack stack) {
        return matchesId(stack, tridentId);
    }

    @Override
    public boolean matchesCustomId(ItemStack stack, String itemId) {
        return matchesId(stack, itemId);
    }

    @Override
    public boolean matchesAbilityItem(ItemStack stack, String abilityId, String abilityName) {
        String plain = readRenamedItemName(stack);
        return AbilityRenameTokens.matchesRenamedItem(plain, abilityId, abilityName);
    }

    private String readRenamedItemName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
        }
        String legacy = meta.getDisplayName();
        if (legacy != null && !legacy.isBlank()) {
            return ChatColor.stripColor(legacy).trim();
        }
        return null;
    }

    @Override
    public ItemStack createHeartItem(int amount) {
        ItemStack ia = itemsAdder.createCustomItem(heartId, amount);
        if (ia != null) {
            applyHeartPresentation(ia);
            ItemUtils.setPdcString(ia, pdcKey, heartId);
            return ia;
        }
        ItemStack stack = new ItemStack(Material.NETHER_STAR, amount);
        applyHeartPresentation(stack);
        ItemUtils.setPdcString(stack, pdcKey, heartId);
        return stack;
    }

    @Override
    public ItemStack createReviveBeacon(int amount) {
        ItemStack ia = itemsAdder.createCustomItem(reviveBeaconId, amount);
        if (ia != null) {
            return ia;
        }
        ItemStack stack = new ItemStack(Material.BEACON, amount);
        ItemUtils.setPdcString(stack, pdcKey, reviveBeaconId);
        return stack;
    }

    @Override
    public ItemStack createCrownItem() {
        ItemStack ia = itemsAdder.createCustomItem(crownId, 1);
        if (ia != null) {
            ItemUtils.setPdcString(ia, pdcKey, crownId);
            return ia;
        }
        ItemStack stack = new ItemStack(Material.NETHERITE_HELMET, 1);
        ItemUtils.setPdcString(stack, pdcKey, crownId);
        return stack;
    }

    @Override
    public ItemStack createMaceItem() {
        ItemStack ia = itemsAdder.createCustomItem(maceId, 1);
        if (ia != null) {
            return ia;
        }
        return fallbackNamed(Material.MACE, maceId, ChatColor.GOLD + "Mace");
    }

    @Override
    public ItemStack createScytheItem() {
        ItemStack ia = itemsAdder.createCustomItem(scytheId, 1);
        if (ia != null) {
            return ia;
        }
        return fallbackNamed(Material.NETHERITE_SWORD, scytheId, ChatColor.DARK_PURPLE + "Scythe");
    }

    @Override
    public ItemStack createWardenCpItem() {
        ItemStack ia = itemsAdder.createCustomItem(wardenCpId, 1);
        if (ia != null) {
            return ia;
        }
        return fallbackNamed(Material.NETHERITE_CHESTPLATE, wardenCpId, ChatColor.DARK_AQUA + "Warden CP");
    }

    @Override
    public ItemStack createTridentItem() {
        ItemStack ia = itemsAdder.createCustomItem(tridentId, 1);
        if (ia != null) {
            ItemUtils.setPdcString(ia, pdcKey, tridentId);
            return ia;
        }
        ItemStack stack = fallbackNamed(Material.TRIDENT, tridentId, ChatColor.AQUA + "Poseidon Trident");
        ItemUtils.setPdcString(stack, pdcKey, tridentId);
        return stack;
    }

    @Override
    public void ensureTridentItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TRIDENT) {
            return;
        }
        if (matchesId(stack, tridentId)) {
            return;
        }
        if (isPoseidonDisplayName(stack)) {
            ItemUtils.setPdcString(stack, pdcKey, tridentId);
        }
    }

    private boolean isPoseidonDisplayName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName()).trim();
        if (plain.isEmpty()) {
            plain = ChatColor.stripColor(meta.getDisplayName());
        }
        return plain != null && plain.equalsIgnoreCase("Poseidon Trident");
    }

    @Override
    public ItemStack createCustomItem(String itemId, int amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return null;
        }
        return itemsAdder.createCustomItem(itemId, amount);
    }

    private String compact(String input) {
        return input.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean matchesId(ItemStack stack, String id) {
        if (stack == null) {
            return false;
        }
        String iaId = itemsAdder.getCustomItemId(stack);
        String pdc = ItemUtils.getPdcString(stack, pdcKey);
        return ItemIdentityMatcher.matches(iaId, pdc, id);
    }

    private ItemStack fallbackNamed(Material material, String id, String displayName) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            stack.setItemMeta(meta);
        }
        ItemUtils.setPdcString(stack, pdcKey, id);
        return stack;
    }

    private void applyHeartPresentation(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Heart");
        meta.setLore(null);
        stack.setItemMeta(meta);
    }
}
