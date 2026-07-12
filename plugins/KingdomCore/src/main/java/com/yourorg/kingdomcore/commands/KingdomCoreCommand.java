package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.DebugTelemetryService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KingdomCoreCommand implements CommandExecutor {
    private final DebugTelemetryService debugTelemetryService;
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> testLineTasks = new ConcurrentHashMap<>();

    public KingdomCoreCommand(DebugTelemetryService debugTelemetryService, Plugin plugin) {
        this.debugTelemetryService = debugTelemetryService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        if ("debug".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                return false;
            }
            if ("on".equalsIgnoreCase(args[1])) {
                debugTelemetryService.setDebugEnabled(true);
                sender.sendMessage("Debug enabled.");
                return true;
            }
            if ("off".equalsIgnoreCase(args[1])) {
                debugTelemetryService.setDebugEnabled(false);
                sender.sendMessage("Debug disabled.");
                return true;
            }
            if ("player".equalsIgnoreCase(args[1]) && args.length >= 3) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                Map<DebugTelemetryService.FailReason, Integer> stats =
                        debugTelemetryService.getPlayerStats(target.getUniqueId());
                sender.sendMessage("Debug stats for " + args[2] + ": " + stats);
                return true;
            }
        }
        if ("ptest".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /kingdomcore ptest");
                return true;
            }

            sender.sendMessage("Running particle test for 3s...");
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || ticks++ >= 60) {
                        cancel();
                        return;
                    }

                    var base = player.getLocation().clone();

                    // Hulk landing style: bright green dust around player.
                    player.spawnParticle(
                            Particle.DUST,
                            base.clone().add(0.0, 1.0, 0.0),
                            70,
                            1.8,
                            0.4,
                            1.8,
                            0.0,
                            new Particle.DustOptions(Color.fromRGB(50, 220, 90), 1.6f),
                            true
                    );

                    // Hulk range style: cloud + dragon breath ring.
                    for (int angle = 0; angle < 360; angle += 8) {
                        double r = 5.0;
                        double rad = Math.toRadians(angle);
                        var p = base.clone().add(r * Math.cos(rad), 0.35, r * Math.sin(rad));
                        player.spawnParticle(Particle.CLOUD, p, 3, 0.24, 0.08, 0.24, 0.0, null, true);
                        player.spawnParticle(Particle.DRAGON_BREATH, p, 3, 0.26, 0.08, 0.26, 0.0, null, true);
                    }

                    // Thor mark style: small blue + blue->white above hitbox area.
                    var thor = base.clone().add(0.0, 2.1, 0.0);
                    player.spawnParticle(
                            Particle.DUST,
                            thor,
                            2,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            new Particle.DustOptions(Color.fromRGB(60, 130, 255), 1.85f),
                            true
                    );
                    player.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            thor,
                            1,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            new Particle.DustTransition(Color.fromRGB(60, 130, 255), Color.WHITE, 1.9f),
                            true
                    );
                }
            }.runTaskTimer(plugin, 0L, 1L);
            return true;
        }
        if ("ptestline".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /kingdomcore ptestline");
                return true;
            }
            runParticleLineTest(player);
            return true;
        }
        return false;
    }

    private void runParticleLineTest(Player player) {
        World world = player.getWorld();
        int baseX = -100;
        int baseY = 120;
        int baseZ = -323;

        List<String> particleNames = List.of(
                "TOTEM_OF_UNDYING",
                "SOUL_FIRE_FLAME",
                "END_ROD",
                "FLAME",
                "WITCH",
                "HAPPY_VILLAGER",
                "CRIT",
                "ENCHANT",
                "GLOW",
                "NOTE",
                "ELECTRIC_SPARK",
                "FIREWORK",
                "HEART",
                "NAUTILUS",
                "PORTAL",
                "REVERSE_PORTAL",
                "ENCHANTED_HIT",
                "INSTANT_EFFECT",
                "ENTITY_EFFECT",
                "EFFECT",
                "DAMAGE_INDICATOR",
                "SWEEP_ATTACK",
                "SPIT",
                "SQUID_INK",
                "GLOW_SQUID_INK",
                "BUBBLE",
                "SPLASH",
                "FISHING",
                "RAIN",
                "UNDERWATER",
                "CURRENT_DOWN",
                "BUBBLE_COLUMN_UP",
                "BUBBLE_POP",
                "LAVA",
                "DRIPPING_LAVA",
                "FALLING_LAVA",
                "LANDING_LAVA",
                "DRIPPING_WATER",
                "FALLING_WATER",
                "FALLING_DRIPSTONE_WATER",
                "DRIPPING_DRIPSTONE_WATER",
                "DRIPPING_HONEY",
                "FALLING_HONEY",
                "LANDING_HONEY",
                "FALLING_NECTAR",
                "MYCELIUM",
                "SPORE_BLOSSOM_AIR",
                "CRIMSON_SPORE",
                "WARPED_SPORE",
                "POOF"
        );

        List<Location> points = new ArrayList<>();
        int pointsCount = particleNames.size();
        for (int i = 0; i < pointsCount; i++) {
            int x = baseX + (i * 3);
            Location blockLoc = new Location(world, x, baseY, baseZ);
            blockLoc.getBlock().setType(Material.STONE);

            Location signLoc = blockLoc.clone().add(0, 1, 0);
            signLoc.getBlock().setType(Material.OAK_SIGN);
            if (signLoc.getBlock().getState() instanceof Sign sign) {
                sign.setLine(0, "#" + (i + 1));
                sign.setLine(1, particleNames.get(i));
                sign.update();
            }

            points.add(blockLoc.clone().add(0.5, 1.1, 0.5));
        }

        BukkitTask previous = testLineTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ >= 20 * 30) {
                    cancel();
                    testLineTasks.remove(player.getUniqueId());
                    return;
                }

                for (int i = 0; i < points.size(); i++) {
                    Location p = points.get(i);
                    spawnTestParticle(player, i, p);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        testLineTasks.put(player.getUniqueId(), task);
        player.sendMessage("Particle line test started (#1-#" + pointsCount + ") at x=-100 y=120 z=-323 for 30s.");
    }

    private void spawnTestParticle(Player player, int index, Location p) {
        switch (index) {
            case 0 -> player.spawnParticle(Particle.TOTEM_OF_UNDYING, p, 4, 0.20, 0.15, 0.20, 0.0, null, true);
            case 1 -> player.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 4, 0.18, 0.10, 0.18, 0.0, null, true);
            case 2 -> player.spawnParticle(Particle.END_ROD, p, 4, 0.18, 0.10, 0.18, 0.0, null, true);
            case 3 -> player.spawnParticle(Particle.FLAME, p, 4, 0.18, 0.10, 0.18, 0.0, null, true);
            case 4 -> player.spawnParticle(Particle.WITCH, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 5 -> player.spawnParticle(Particle.HAPPY_VILLAGER, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 6 -> player.spawnParticle(Particle.CRIT, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 7 -> player.spawnParticle(Particle.ENCHANT, p, 4, 0.24, 0.12, 0.24, 0.0, null, true);
            case 8 -> player.spawnParticle(Particle.GLOW, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 9 -> player.spawnParticle(Particle.NOTE, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 10 -> player.spawnParticle(Particle.ELECTRIC_SPARK, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 11 -> player.spawnParticle(Particle.FIREWORK, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 12 -> player.spawnParticle(Particle.HEART, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 13 -> player.spawnParticle(Particle.NAUTILUS, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 14 -> player.spawnParticle(Particle.PORTAL, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 15 -> player.spawnParticle(Particle.REVERSE_PORTAL, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 16 -> player.spawnParticle(Particle.ENCHANTED_HIT, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 17 -> player.spawnParticle(Particle.INSTANT_EFFECT, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 18 -> player.spawnParticle(Particle.ENTITY_EFFECT, p, 4, 1.0, 1.0, 1.0, 1.0, null, true);
            case 19 -> player.spawnParticle(Particle.EFFECT, p, 4, 1.0, 1.0, 1.0, 1.0, null, true);
            case 20 -> player.spawnParticle(Particle.DAMAGE_INDICATOR, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 21 -> player.spawnParticle(Particle.SWEEP_ATTACK, p, 3, 0.18, 0.10, 0.18, 0.0, null, true);
            case 22 -> player.spawnParticle(Particle.SPIT, p, 4, 0.18, 0.10, 0.18, 0.0, null, true);
            case 23 -> player.spawnParticle(Particle.SQUID_INK, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 24 -> player.spawnParticle(Particle.GLOW_SQUID_INK, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 25 -> player.spawnParticle(Particle.BUBBLE, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 26 -> player.spawnParticle(Particle.SPLASH, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 27 -> player.spawnParticle(Particle.FISHING, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 28 -> player.spawnParticle(Particle.RAIN, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 29 -> player.spawnParticle(Particle.UNDERWATER, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 30 -> player.spawnParticle(Particle.CURRENT_DOWN, p, 4, 0.20, 0.12, 0.20, 0.0, null, true);
            case 31 -> player.spawnParticle(Particle.BUBBLE_COLUMN_UP, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 32 -> player.spawnParticle(Particle.BUBBLE_POP, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 33 -> player.spawnParticle(Particle.LAVA, p, 4, 0.18, 0.12, 0.18, 0.0, null, true);
            case 34 -> player.spawnParticle(Particle.DRIPPING_LAVA, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 35 -> player.spawnParticle(Particle.FALLING_LAVA, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 36 -> player.spawnParticle(Particle.LANDING_LAVA, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 37 -> player.spawnParticle(Particle.DRIPPING_WATER, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 38 -> player.spawnParticle(Particle.FALLING_WATER, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 39 -> player.spawnParticle(Particle.FALLING_DRIPSTONE_WATER, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 40 -> player.spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 41 -> player.spawnParticle(Particle.DRIPPING_HONEY, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 42 -> player.spawnParticle(Particle.FALLING_HONEY, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 43 -> player.spawnParticle(Particle.LANDING_HONEY, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 44 -> player.spawnParticle(Particle.FALLING_NECTAR, p, 5, 0.18, 0.12, 0.18, 0.0, null, true);
            case 45 -> player.spawnParticle(Particle.MYCELIUM, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 46 -> player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 47 -> player.spawnParticle(Particle.CRIMSON_SPORE, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 48 -> player.spawnParticle(Particle.WARPED_SPORE, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            case 49 -> player.spawnParticle(Particle.POOF, p, 5, 0.20, 0.12, 0.20, 0.0, null, true);
            default -> {
            }
        }
    }
}
