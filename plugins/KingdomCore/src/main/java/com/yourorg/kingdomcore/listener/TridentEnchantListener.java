package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows Loyalty III + Riptide III together on the Poseidon trident (vanilla normally forbids this),
 * plus Sharpness (normally not allowed on tridents).
 */
public class TridentEnchantListener implements Listener {
    private static final int MAX_LOYALTY = 3;
    private static final int MAX_RIPTIDE = 3;
    private static final int MAX_SHARPNESS = 5;

    private final ItemIdentityService itemIdentityService;

    public TridentEnchantListener(ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        if (!isAnvilTrident(left)) {
            return;
        }
        ItemStack right = event.getInventory().getSecondItem();
        if (right == null || !carriesEnchantments(right)) {
            return;
        }

        // Vanilla can handle normal trident enchants (riptide-only, impaling, etc.) — don't replace those.
        ItemStack vanillaResult = event.getResult();
        if (vanillaResult != null && !needsCompatibilityOverride(left, right)) {
            return;
        }

        ItemStack result = buildMergedResult(left, right);
        if (result == null) {
            return;
        }

        int repairCost = event.getInventory().getRepairCost();
        if (repairCost <= 0) {
            repairCost = estimateRepairCost(left, right);
        }
        event.getInventory().setRepairCost(Math.min(39, Math.max(1, repairCost)));
        itemIdentityService.ensureTridentItem(result);
        event.setResult(result);
    }

    private ItemStack buildMergedResult(ItemStack left, ItemStack right) {
        ItemStack result = left.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }

        Map<Enchantment, Integer> merged = new HashMap<>(meta.getEnchants());
        mergeFromItem(merged, right);

        for (Enchantment enchantment : merged.keySet()) {
            meta.removeEnchant(enchantment);
        }
        for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
            if (!isAllowedTridentEnchant(entry.getKey())) {
                continue;
            }
            meta.addEnchant(entry.getKey(), clampLevel(entry.getKey(), entry.getValue()), true);
        }
        result.setItemMeta(meta);
        return result;
    }

    private boolean needsCompatibilityOverride(ItemStack left, ItemStack right) {
        Map<Enchantment, Integer> fromRight = new HashMap<>();
        mergeFromItem(fromRight, right);

        if (fromRight.containsKey(Enchantment.SHARPNESS)) {
            return true;
        }

        ItemMeta leftMeta = left.getItemMeta();
        if (leftMeta == null) {
            return false;
        }
        if (leftMeta.hasEnchant(Enchantment.LOYALTY) && fromRight.containsKey(Enchantment.RIPTIDE)) {
            return true;
        }
        return leftMeta.hasEnchant(Enchantment.RIPTIDE) && fromRight.containsKey(Enchantment.LOYALTY);
    }

    private int estimateRepairCost(ItemStack left, ItemStack right) {
        int cost = 1;
        ItemMeta leftMeta = left.getItemMeta();
        if (leftMeta != null) {
            for (int level : leftMeta.getEnchants().values()) {
                cost += level;
            }
        }
        if (right.getType() == Material.ENCHANTED_BOOK
                && right.getItemMeta() instanceof EnchantmentStorageMeta bookMeta) {
            for (int level : bookMeta.getStoredEnchants().values()) {
                cost += level * 2;
            }
        } else {
            for (int level : right.getEnchantments().values()) {
                cost += level;
            }
        }
        return cost;
    }

    private boolean isAnvilTrident(ItemStack stack) {
        return itemIdentityService.isTridentItem(stack);
    }

    private boolean carriesEnchantments(ItemStack stack) {
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
                return false;
            }
            return !bookMeta.getStoredEnchants().isEmpty();
        }
        return !stack.getEnchantments().isEmpty();
    }

    private void mergeFromItem(Map<Enchantment, Integer> merged, ItemStack stack) {
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
                return;
            }
            for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
                mergeEnchant(merged, entry.getKey(), entry.getValue());
            }
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            mergeEnchant(merged, entry.getKey(), entry.getValue());
        }
    }

    private void mergeEnchant(Map<Enchantment, Integer> merged, Enchantment enchantment, int level) {
        if (!isAllowedTridentEnchant(enchantment)) {
            return;
        }
        int clamped = clampLevel(enchantment, level);
        merged.merge(enchantment, clamped, Math::max);
    }

    private boolean isAllowedTridentEnchant(Enchantment enchantment) {
        return enchantment.equals(Enchantment.LOYALTY)
                || enchantment.equals(Enchantment.RIPTIDE)
                || enchantment.equals(Enchantment.SHARPNESS)
                || enchantment.equals(Enchantment.IMPALING)
                || enchantment.equals(Enchantment.CHANNELING)
                || enchantment.equals(Enchantment.UNBREAKING)
                || enchantment.equals(Enchantment.MENDING);
    }

    private int clampLevel(Enchantment enchantment, int level) {
        if (enchantment.equals(Enchantment.LOYALTY)) {
            return Math.min(Math.max(level, 1), MAX_LOYALTY);
        }
        if (enchantment.equals(Enchantment.RIPTIDE)) {
            return Math.min(Math.max(level, 1), MAX_RIPTIDE);
        }
        if (enchantment.equals(Enchantment.SHARPNESS)) {
            return Math.min(Math.max(level, 1), MAX_SHARPNESS);
        }
        return level;
    }
}
