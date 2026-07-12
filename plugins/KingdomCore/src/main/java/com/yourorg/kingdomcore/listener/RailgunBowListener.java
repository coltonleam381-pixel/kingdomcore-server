package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.AdminToolAccess;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RailgunBowListener implements Listener {
    public static final String RAILGUN_META = "kc_railgun_arrow";
    public static final String RAILGUN_KILL_META = "kc_railgun_kill";

    private final JavaPlugin plugin;
    private final Map<UUID, UUID> pendingKillShooterByVictim = new ConcurrentHashMap<>();

    public RailgunBowListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        tagRailgunArrow(shooter, event.getProjectile());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (arrow.hasMetadata(RAILGUN_META)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        tagRailgunArrow(shooter, arrow);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRailgunHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!arrow.hasMetadata(RAILGUN_META)) {
            return;
        }

        event.setCancelled(true);
        arrow.removeMetadata(RAILGUN_META, plugin);

        Location popLoc = arrow.getLocation().clone();
        Player shooter = arrow.getShooter() instanceof Player player ? player : null;
        arrow.remove();
        spawnPop(popLoc);

        scheduleRailgunKill(victim, shooter);
    }

    private void tagRailgunArrow(Player shooter, org.bukkit.entity.Entity projectile) {
        if (!AdminToolAccess.canUse(shooter, plugin)) {
            return;
        }
        ItemStack main = shooter.getInventory().getItemInMainHand();
        ItemStack off = shooter.getInventory().getItemInOffHand();
        if (!isRailgunBow(main) && !isRailgunBow(off)) {
            return;
        }
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setMetadata(RAILGUN_META, new FixedMetadataValue(plugin, true));
        }
    }

    private void scheduleRailgunKill(Player victim, Player shooter) {
        UUID victimId = victim.getUniqueId();
        UUID shooterId = shooter != null ? shooter.getUniqueId() : victimId;
        if (pendingKillShooterByVictim.putIfAbsent(victimId, shooterId) != null) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            UUID creditedShooterId = pendingKillShooterByVictim.remove(victimId);
            if (!victim.isValid() || victim.isDead()) {
                return;
            }

            Player creditedShooter = creditedShooterId != null ? Bukkit.getPlayer(creditedShooterId) : null;
            victim.setNoDamageTicks(0);
            victim.setMetadata(RAILGUN_KILL_META, new FixedMetadataValue(plugin, true));
            try {
                if (creditedShooter != null && creditedShooter.isOnline()) {
                    victim.damage(1_000_000.0D, creditedShooter);
                }
                if (!victim.isDead()) {
                    victim.setHealth(0.0D);
                }
            } finally {
                victim.removeMetadata(RAILGUN_KILL_META, plugin);
            }
        });
    }

    private void spawnPop(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, location, 2, 0.05, 0.05, 0.05, 0.0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.35F);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.6F);
    }

    static boolean isRailgunBow(ItemStack bow) {
        if (bow == null || bow.getType().isAir() || !bow.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = bow.getItemMeta();
        if (meta.hasDisplayName()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
            if ("railgun".equalsIgnoreCase(plain)) {
                return true;
            }
        }
        String legacy = meta.getDisplayName();
        if (legacy != null && "railgun".equalsIgnoreCase(ChatColor.stripColor(legacy).trim())) {
            return true;
        }
        return false;
    }
}
