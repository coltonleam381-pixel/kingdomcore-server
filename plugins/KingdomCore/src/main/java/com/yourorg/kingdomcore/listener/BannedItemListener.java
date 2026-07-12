package com.yourorg.kingdomcore.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BannedItemListener implements Listener {

    private boolean isBanned(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Check banned materials
        Material type = item.getType();
        if (type == Material.END_CRYSTAL || 
            type == Material.RESPAWN_ANCHOR) {
            return true;
        }

        // Check for Fire Aspect enchantment
        if (item.containsEnchantment(Enchantment.FIRE_ASPECT)) {
            return true;
        }

        // Check for Fire Aspect in Enchanted Books
        if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            if (meta.hasStoredEnchant(Enchantment.FIRE_ASPECT)) {
                return true;
            }
        }

        // Check for banned potions (Speed II only)
        if (type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW) {
            if (item.getItemMeta() instanceof PotionMeta potionMeta) {
                PotionType pType = potionMeta.getBasePotionType();
                if (pType != null && pType == PotionType.STRONG_SWIFTNESS) {
                    return true;
                }
                if (potionMeta.hasCustomEffects()) {
                    for (org.bukkit.potion.PotionEffect effect : potionMeta.getCustomEffects()) {
                        if (effect.getType().equals(org.bukkit.potion.PotionEffectType.SPEED)
                                && effect.getAmplifier() >= 1) {
                            return true;
                        }
                    }
                }
            }
        }

        // Cleanse Bundles and Shulker Boxes
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
            boolean changed = false;
            List<ItemStack> items = new java.util.ArrayList<>();
            for (ItemStack i : bundleMeta.getItems()) {
                if (isBanned(i)) {
                    changed = true;
                } else {
                    items.add(i);
                }
            }
            if (changed) {
                bundleMeta.setItems(items);
                item.setItemMeta(bundleMeta);
            }
        } else if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta blockStateMeta) {
            if (blockStateMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox shulkerBox) {
                boolean changed = false;
                for (int i = 0; i < shulkerBox.getInventory().getSize(); i++) {
                    ItemStack iItem = shulkerBox.getInventory().getItem(i);
                    if (isBanned(iItem)) {
                        shulkerBox.getInventory().setItem(i, null);
                        changed = true;
                    }
                }
                if (changed) {
                    blockStateMeta.setBlockState(shulkerBox);
                    item.setItemMeta(blockStateMeta);
                }
            }
        }

        return false;
    }

    private void notifyPlayer(Player player) {
        player.sendMessage("§cThis item is permanently banned on this server and has been destroyed!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();

            boolean cancelled = false;

            if (isBanned(current)) {
                event.setCurrentItem(null);
                cancelled = true;
            }

            if (isBanned(cursor)) {
                event.getView().setCursor(null);
                cancelled = true;
            }

            if (cancelled) {
                event.setCancelled(true);
                notifyPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            if (isBanned(cursor) || isBanned(current)) {
                event.setCancelled(true);
                event.setCurrentItem(null);
                event.getView().setCursor(null);
                notifyPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isBanned(item)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().setItemInMainHand(null);
            notifyPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item itemEntity = event.getItem();
        if (isBanned(itemEntity.getItemStack())) {
            event.setCancelled(true);
            itemEntity.remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isBanned(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (isBanned(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.ThrownPotion thrownPotion) {
            if (isBanned(thrownPotion.getItem())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getConsumable() != null && isBanned(event.getConsumable())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (isBanned(result)) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();
        if (enchants.containsKey(Enchantment.FIRE_ASPECT)) {
            enchants.remove(Enchantment.FIRE_ASPECT);
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        List<ItemStack> loot = event.getLoot();
        Iterator<ItemStack> iterator = loot.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isBanned(item)) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isBanned(item)) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean removed = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isBanned(item)) {
                player.getInventory().setItem(i, null);
                removed = true;
            } else if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
                boolean bundleModified = false;
                java.util.List<ItemStack> items = new java.util.ArrayList<>(bundleMeta.getItems());
                java.util.Iterator<ItemStack> it = items.iterator();
                while (it.hasNext()) {
                    ItemStack child = it.next();
                    if (isBanned(child)) {
                        it.remove();
                        bundleModified = true;
                        removed = true;
                    }
                }
                if (bundleModified) {
                    bundleMeta.setItems(items);
                    item.setItemMeta(bundleMeta);
                }
            }
        }
        if (removed) {
            notifyPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryMoveItem(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (isBanned(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareItemCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getInventory().getResult() != null && isBanned(event.getInventory().getResult())) {
            event.getInventory().setResult(null);
        }
    }
}
