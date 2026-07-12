package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public class UniqueItemLifecycleListener implements Listener {
    private static final Set<EntityDamageEvent.DamageCause> ITEM_DESTRUCTION_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.CONTACT
    );

    private final Plugin plugin;
    private final ItemIdentityService itemIdentityService;
    private final UniqueItemService uniqueItemService;
    private final com.yourorg.kingdomcore.core.services.HeartService heartService;
    private final com.yourorg.kingdomcore.core.KingdomConfig config;

    public UniqueItemLifecycleListener(Plugin plugin,
                                       ItemIdentityService itemIdentityService,
                                       UniqueItemService uniqueItemService,
                                       com.yourorg.kingdomcore.core.services.HeartService heartService,
                                       com.yourorg.kingdomcore.core.KingdomConfig config) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
        this.uniqueItemService = uniqueItemService;
        this.heartService = heartService;
        this.config = config;
    }

    // onPlayerJoin and onPlayerQuit are no longer needed because the state is fully managed by the database and explicit events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        
        if (itemIdentityService.isHeartItem(result)) {
            if (event.getView().getPlayer() instanceof org.bukkit.entity.Player player) {
                if (heartService.getOrCreateState(player.getUniqueId(), player.getName()).getProgressionHearts() >= config.getCraftMaxHearts()) {
                    event.getInventory().setResult(null);
                }
            }
            return;
        }

        String id = getUniqueItemId(result);
        if (id == null) {
            return;
        }
        if (!uniqueItemService.canCraft(id)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return;
        
        if (itemIdentityService.isHeartItem(result)) {
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                if (heartService.getOrCreateState(player.getUniqueId(), player.getName()).getProgressionHearts() >= config.getCraftMaxHearts()) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou have reached the maximum of " + config.getCraftMaxHearts() + " hearts and can no longer craft them. You must kill players to get more.");
                }
            }
            return;
        }

        String id = getUniqueItemId(result);
        if (id == null) {
            return;
        }
        if (!uniqueItemService.canCraft(id)) {
            event.setCancelled(true);
            return;
        }
        uniqueItemService.markCrafted(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        String id = getUniqueItemId(event.getBrokenItem());
        if (id != null) {
            uniqueItemService.markDestroyed(id);
            plugin.getServer().broadcastMessage("§c§lThe " + getDisplayName(id) + " has been completely destroyed! The 24-hour cooldown has started.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        String id = getUniqueItemId(event.getEntity().getItemStack());
        if (id != null) {
            uniqueItemService.markDestroyed(id);
            plugin.getServer().broadcastMessage("§c§lThe " + getDisplayName(id) + " was left on the ground and despawned! The 24-hour cooldown has started.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        if (!ITEM_DESTRUCTION_CAUSES.contains(event.getCause())) {
            return;
        }
        String id = getUniqueItemId(item.getItemStack());
        if (id == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!item.isValid() || item.isDead()) {
                uniqueItemService.markDestroyed(id);
                plugin.getServer().broadcastMessage("§c§lThe " + getDisplayName(id) + " has been destroyed! The 24-hour cooldown has started.");
            }
        });
    }

    private String getUniqueItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        if (itemIdentityService.isMaceItem(stack)) return "mace";
        if (itemIdentityService.isScytheItem(stack)) return "scythe";
        if (itemIdentityService.isWardenCpItem(stack)) return "warden_cp";
        if (itemIdentityService.isTridentItem(stack)) return "trident";
        if (itemIdentityService.isCrownItem(stack)) return "crown";
        return null;
    }

    private String getDisplayName(String id) {
        return switch (id) {
            case "mace" -> "Mace";
            case "scythe" -> "Dread Knight Scythe";
            case "warden_cp" -> "Warden Chestplate";
            case "trident" -> "Poseidon Trident";
            case "crown" -> "Crown";
            default -> "Unique Item";
        };
    }
}
