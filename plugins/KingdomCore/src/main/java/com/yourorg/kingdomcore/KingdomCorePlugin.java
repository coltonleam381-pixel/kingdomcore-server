package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.abilities.*;
import com.yourorg.kingdomcore.core.CustomRecipeRegistrar;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.*;
import com.yourorg.kingdomcore.core.services.impl.*;
import com.yourorg.kingdomcore.service.AfkService;
import com.yourorg.kingdomcore.service.MonumentService;
import com.yourorg.kingdomcore.integrations.CitizensHook;
import com.yourorg.kingdomcore.integrations.ItemsAdderHook;
import com.yourorg.kingdomcore.integrations.PlaceholderRegistration;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.integrations.WorldGuardHookFactory;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import com.yourorg.kingdomcore.listener.AbilityDamageListener;
import com.yourorg.kingdomcore.listener.CombatRestrictionListener;
import com.yourorg.kingdomcore.listener.CombatTagListener;
import com.yourorg.kingdomcore.listener.PhaseWalkDamageListener;
import com.yourorg.kingdomcore.service.PhaseWalkService;
import com.yourorg.kingdomcore.service.UniqueItemService;
import com.yourorg.kingdomcore.listeners.*;
import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.Migration;
import com.yourorg.kingdomcore.persistence.MigrationRunner;
import com.yourorg.kingdomcore.persistence.repo.*;
import com.yourorg.kingdomcore.persistence.repo.impl.*;
import com.yourorg.kingdomcore.commands.CommandRegistrar;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.service.impl.CombatTagServiceImpl;
import com.yourorg.kingdomcore.service.impl.UniqueItemServiceImpl;
import com.yourorg.kingdomcore.util.CombatRules;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class KingdomCorePlugin extends JavaPlugin {
    public static final int GLOBAL_UPDATE_NUMBER = 161;
    private KingdomConfig configValues;
    private Database database;
    private DebugTelemetryService debugTelemetryService;
    private AbilityService abilityService;
    private AbilityOwnershipService abilityOwnershipService;
    private CooldownService cooldownService;
    private HeartService heartService;
    private HealthService healthService;
    private ReviveService reviveService;
    private AllowlistService allowlistService;
    private ItemIdentityService itemIdentityService;
    private CitizensHook citizensHook;
    private WorldGuardHook worldGuardHook;
    private CombatTagService combatTagService;
    private AbilityScoreboard abilityScoreboard;
    private com.yourorg.kingdomcore.listeners.WardenChestplateListener wardenChestplateListener;
    private com.yourorg.kingdomcore.listeners.TridentAbilityListener tridentAbilityListener;
    private com.yourorg.kingdomcore.listeners.ScytheAbilityListener scytheAbilityListener;
    private AtlantisAbility atlantisAbility;
    private ThorAbility thorAbility;
    private ProtocolAbility protocolAbility;
    private BerserkAbility berserkAbility;
    private IceNovaAbility iceNovaAbility;
    private PhaseWalkService phaseWalkService;
    private PhaseWalkAbility phaseWalkAbility;
    private UniqueItemService uniqueItemService;
    private AfkService afkService;
    private com.yourorg.kingdomcore.listener.TabPrefixListener tabPrefixListener;
    private com.yourorg.kingdomcore.service.SpawnProtectionService spawnProtectionService;
    private com.yourorg.kingdomcore.service.SpawnLeaveLockService spawnLeaveLockService;
    private com.yourorg.kingdomcore.service.impl.DiamondBeamServiceImpl diamondBeamService;
    private com.yourorg.kingdomcore.service.WorldBorderService worldBorderService;
    private com.yourorg.kingdomcore.service.AssassinEventService assassinEventService;
    private com.yourorg.kingdomcore.util.SpawnRegionPolicy spawnRegionPolicy;
    private com.yourorg.kingdomcore.util.CombatRules combatRules;

    private com.yourorg.kingdomcore.core.services.BountyService bountyService;
    private com.yourorg.kingdomcore.service.VillagerBedDistanceService villagerBedDistanceService;
    private com.yourorg.kingdomcore.service.CartographerTradeService cartographerTradeService;
    private com.yourorg.kingdomcore.service.ClericTradeService clericTradeService;
    private com.yourorg.kingdomcore.service.LongStrongStrengthPotionService longStrongStrengthPotionService;
    private com.yourorg.kingdomcore.service.VillagerEggGrantService villagerEggGrantService;

    public com.yourorg.kingdomcore.service.VillagerEggGrantService getVillagerEggGrantService() {
        return villagerEggGrantService;
    }

    public AbilityService getAbilityService() {
        return abilityService;
    }

    public HeartService getHeartService() {
        return heartService;
    }

    public com.yourorg.kingdomcore.core.services.BountyService getBountyService() {
        return bountyService;
    }

    public com.yourorg.kingdomcore.service.CombatTagService getCombatTagService() {
        return combatTagService;
    }

    public com.yourorg.kingdomcore.util.CombatRules getCombatRules() {
        return combatRules;
    }

    public com.yourorg.kingdomcore.util.SpawnRegionPolicy getSpawnRegionPolicy() {
        return spawnRegionPolicy;
    }

    public com.yourorg.kingdomcore.service.AssassinEventService getAssassinEventService() {
        return assassinEventService;
    }

    @Override
    public void onEnable() {
        getLogger().info("Build marker: KC_RENAME_HUD_FIX_" + GLOBAL_UPDATE_NUMBER);
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Remove vanilla mace recipe
        getServer().removeRecipe(NamespacedKey.minecraft("mace"));

        this.configValues = KingdomConfig.from(getConfig());
        registerClientLockdown();
        this.spawnProtectionService = new com.yourorg.kingdomcore.service.impl.SpawnProtectionServiceImpl(
                this,
                configValues.getSpawnRegionName(),
                getConfig().getBoolean("spawn-protection.enabled", true)
        );
        this.spawnLeaveLockService = new com.yourorg.kingdomcore.service.SpawnLeaveLockService(this);
        this.worldBorderService = new com.yourorg.kingdomcore.service.impl.WorldBorderServiceImpl(this);

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                worldBorderService.apply();
                if (getConfig().getBoolean("mob-rules.disable-phantoms", true)) {
                    for (org.bukkit.World world : getServer().getWorlds()) {
                        world.setGameRule(org.bukkit.GameRule.DO_INSOMNIA, false);
                    }
                    getLogger().info("Phantoms disabled (doInsomnia=false + spawn block).");
                }
                if (worldGuardHook.isAvailable()) {
                    spawnProtectionService.ensureRegion();
                    spawnProtectionService.applyCurrentState();
                }
            }
        }, this);

        this.database = new Database(new File(getDataFolder(), configValues.getSqliteFile()));
        try {
            new MigrationRunner(database, migrations()).migrate();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Database migration failed", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ItemsAdderHook itemsAdderHook = new ItemsAdderHook(getLogger());
        this.citizensHook = new CitizensHook();
        this.worldGuardHook = WorldGuardHookFactory.create(getLogger());
        this.diamondBeamService = new com.yourorg.kingdomcore.service.impl.DiamondBeamServiceImpl(
                this, worldGuardHook, resolveSpawnRegions());
        this.spawnRegionPolicy = new com.yourorg.kingdomcore.util.SpawnRegionPolicy(
                spawnProtectionService,
                worldGuardHook,
                resolveSpawnRegions());
        this.combatRules = new com.yourorg.kingdomcore.util.CombatRules(
                getConfig().getStringList("combat-rules.exempt-players"),
                getConfig().getBoolean("combat-rules.op-spawn-entry-bypass", false),
                getConfig().getStringList("combat-rules.combat-cd-bypass-players"),
                getConfig().getStringList("combat-rules.combat-spawn-bypass-players"));
        if (!worldGuardHook.isAvailable()) {
            getLogger().warning("WorldGuard not found! Some region features will be disabled.");
        }

        NamespacedKey itemKey = new NamespacedKey(this, "item-id");
        this.itemIdentityService = new ItemIdentityServiceImpl(itemsAdderHook, itemKey,
                configValues.getHeartItemId(), configValues.getCrownItemId(), configValues.getReviveBeaconId(),
                configValues.getMaceItemId(), configValues.getScytheItemId(),
                configValues.getWardenCpItemId(), configValues.getTridentItemId());

        PlayerStateRepository playerStateRepository = new SQLitePlayerStateRepository(database);
        AbilityOwnershipRepository ownershipRepository = new SQLiteAbilityOwnershipRepository(database);
        CooldownRepository cooldownRepository = new SQLiteCooldownRepository(database);
        AllowlistRepository allowlistRepository = new SQLiteAllowlistRepository(database);
        ReviveAuditRepository reviveAuditRepository = new SQLiteReviveAuditRepository(database);
        UniqueItemRepository uniqueItemRepository = new SQLiteUniqueItemRepository(database);
        com.yourorg.kingdomcore.core.repositories.BountyRepository bountyRepository = new com.yourorg.kingdomcore.core.repositories.impl.SQLiteBountyRepository(database);
        com.yourorg.kingdomcore.core.repositories.AuthRepository authRepository = new com.yourorg.kingdomcore.core.repositories.impl.SQLiteAuthRepository(database);

        this.debugTelemetryService = new DebugTelemetryServiceImpl();
        this.abilityService = new AbilityServiceImpl(configValues.getAbilities());
        this.cooldownService = new CooldownServiceImpl(cooldownRepository, configValues.isPersistCooldowns());
        this.healthService = new HealthServiceImpl(itemIdentityService, configValues, this);
        this.heartService = new HeartServiceImpl(playerStateRepository, itemIdentityService, healthService,
                configValues.getStartingHearts(), configValues.getProgressionMaxHearts());
        this.abilityOwnershipService = new AbilityOwnershipServiceImpl(ownershipRepository, playerStateRepository, heartService);
        this.allowlistService = new AllowlistServiceImpl(allowlistRepository);
        this.bountyService = new com.yourorg.kingdomcore.core.services.impl.BountyServiceImpl(bountyRepository);
        
        com.yourorg.kingdomcore.core.services.AuthService authService = new com.yourorg.kingdomcore.core.services.impl.AuthServiceImpl(authRepository);
        com.yourorg.kingdomcore.gui.PinGui pinGui = new com.yourorg.kingdomcore.gui.PinGui(authService, itemsAdderHook);

        this.reviveService = new ReviveServiceImpl(heartService, playerStateRepository, reviveAuditRepository,
                itemIdentityService, healthService, configValues.getProgressionMaxHearts());
        MonumentService monumentService = new com.yourorg.kingdomcore.service.impl.MonumentServiceImpl(this);

        this.uniqueItemService = new UniqueItemServiceImpl(
                this,
                itemIdentityService,
                monumentService,
                uniqueItemRepository,
                Map.of(
                        "mace", configValues.getMaceRecraftMs(),
                        "scythe", configValues.getScytheRecraftMs(),
                        "trident", configValues.getTridentRecraftMs(),
                        "warden_cp", configValues.getWardenCpRecraftMs(),
                        "crown", configValues.getCrownRecraftMs()
                ),
                Map.of(
                        "mace", configValues.getMaceItemId(),
                        "scythe", configValues.getScytheItemId(),
                        "trident", configValues.getTridentItemId(),
                        "warden_cp", configValues.getWardenCpItemId(),
                        "crown", configValues.getCrownItemId()
                )
        );

        allowlistService.reload();

        // Initialize combat tag service
        long pearlPostTagMs = getConfig().getLong("combat-rules.pearl-block-post-tag-seconds", 15L) * 1000L;
        long pearlCombatCooldownMs = getConfig().getLong("combat-rules.pearl-combat-cooldown-seconds", 10L) * 1000L;
        this.combatTagService = new CombatTagServiceImpl(this, playerStateRepository, heartService,
                configValues.getBlockedKickMessage(), pearlPostTagMs, pearlCombatCooldownMs);
        getServer().getPluginManager().registerEvents(
                new CombatLogoutJoinListener(playerStateRepository), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.AuthListener(authService, pinGui, itemsAdderHook, this), this);
        getCommand("kauth").setExecutor(new com.yourorg.kingdomcore.commands.KAuthCommand(authService, pinGui, itemsAdderHook, this));
        this.assassinEventService = new com.yourorg.kingdomcore.service.AssassinEventService(this, heartService, combatTagService);
        assassinEventService.resetEvent();
        getLogger().info("Assassin event state cleared on startup.");
        
        // Initialize AFK service
        this.afkService = new com.yourorg.kingdomcore.service.impl.AfkServiceImpl(this, combatTagService);

        // Start Dimension timer task
        getServer().getScheduler().runTaskTimer(this, new com.yourorg.kingdomcore.service.DimensionTimerTask(this), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, new com.yourorg.kingdomcore.service.PvpTimerTask(this), 20L, 20L);

        // Register ability handlers
        registerAbilityHandlers();

        // 6) Instantiate Scoreboard
        this.abilityScoreboard = new AbilityScoreboard(
            this, abilityService, cooldownService, combatTagService, 
            atlantisAbility, thorAbility, heartService, 
            spawnRegionPolicy
        );

        CustomRecipeRegistrar.registerAll(this, itemIdentityService);

        com.yourorg.kingdomcore.gui.MenuFactory menuFactory = new com.yourorg.kingdomcore.gui.MenuFactory(
                this, configValues, abilityService, abilityOwnershipService,
                heartService, itemIdentityService, reviveService,
                debugTelemetryService
        );

        registerListeners(playerStateRepository, menuFactory, monumentService);
        villagerBedDistanceService = new com.yourorg.kingdomcore.service.VillagerBedDistanceService(this);
        villagerBedDistanceService.start();
        cartographerTradeService = new com.yourorg.kingdomcore.service.CartographerTradeService(this);
        cartographerTradeService.start();
        clericTradeService = new com.yourorg.kingdomcore.service.ClericTradeService(this);
        clericTradeService.start();
        longStrongStrengthPotionService = new com.yourorg.kingdomcore.service.LongStrongStrengthPotionService(this);
        longStrongStrengthPotionService.start();
        villagerEggGrantService = new com.yourorg.kingdomcore.service.VillagerEggGrantService(this);
        villagerEggGrantService.queueFromConfig();
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.VillagerEggGrantListener(this, villagerEggGrantService), this);
        // Wire custom listeners into scoreboard
        abilityScoreboard.setWardenListener(wardenChestplateListener);
        abilityScoreboard.setTridentListener(tridentAbilityListener);
        abilityScoreboard.setScytheListener(scytheAbilityListener);
        abilityScoreboard.setAssassinEventService(assassinEventService);
        abilityScoreboard.setItemIdentityService(itemIdentityService);
        com.yourorg.kingdomcore.service.SelectionService selectionService =
                new com.yourorg.kingdomcore.service.SelectionService();
        getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listener.RailgunBowListener(this), this);
        getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listener.OpGamemodeBlockListener(), this);
        CommandRegistrar.registerAll(this, configValues, abilityService, abilityOwnershipService,
                cooldownService,
                heartService, healthService, allowlistService, reviveService, debugTelemetryService, itemIdentityService, uniqueItemService, monumentService,
                playerStateRepository, bountyService, afkService, spawnProtectionService, spawnLeaveLockService, diamondBeamService, assassinEventService, combatRules, abilityScoreboard, selectionService);

        PlaceholderRegistration.registerWhenReady(this, uniqueItemService, abilityOwnershipService);

        // Register menu listeners and commands
        getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listeners.ChatListener(this, menuFactory), this);
        getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listeners.InventoryListener(menuFactory), this);
        getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listeners.InventoryCloseListener(menuFactory), this);

        getCommand("revivemenu").setExecutor(new com.yourorg.kingdomcore.commands.MenuCommand(menuFactory, heartService, com.yourorg.kingdomcore.commands.MenuCommand.MenuKind.REVIVE));
        getCommand("abilitymenu").setExecutor(new com.yourorg.kingdomcore.commands.MenuCommand(menuFactory, heartService, com.yourorg.kingdomcore.commands.MenuCommand.MenuKind.ABILITY));

        com.yourorg.kingdomcore.listener.FancyNpcMenuListener.register(this, authService, combatTagService, combatRules);
        com.yourorg.kingdomcore.listener.MonumentDuplicateHologramRemover.register(this);

        registerTabPrefixes();
        registerChatStyles();
        getLogger().info("KingdomCore build " + GLOBAL_UPDATE_NUMBER + " enabled successfully.");
    }

    private void registerAbilityHandlers() {
        SpawnRegionPolicy policy = this.spawnRegionPolicy;

        // Initialize PhaseWalkService for the PhaseWalk ability
        this.phaseWalkService = new PhaseWalkService();

        // Register all 14 ability handlers
        abilityService.registerHandler(new HulkAbility(this, worldGuardHook, cooldownService, policy));
        this.thorAbility = new ThorAbility(this, worldGuardHook, cooldownService, policy);
        abilityService.registerHandler(thorAbility);
        abilityService.registerHandler(new ZeusAbility(this, worldGuardHook, policy));

        // Store Atlantis reference for scoreboard
        this.atlantisAbility = new AtlantisAbility(this, worldGuardHook, policy);
        abilityService.registerHandler(atlantisAbility);

        abilityService.registerHandler(new SkybreakerAbility(this, worldGuardHook, policy));
        abilityService.registerHandler(new MeteorAbility(this, worldGuardHook, cooldownService, policy));
        abilityService.registerHandler(new DashAbility(this, worldGuardHook, policy));
        abilityService.registerHandler(new HeartShieldAbility(this, worldGuardHook, policy));
        abilityService.registerHandler(new LifestealAbility(this, worldGuardHook, cooldownService, policy));
        abilityService.registerHandler(new SanguineFogAbility(this, worldGuardHook, cooldownService, policy));
        abilityService.registerHandler(new RecallAbility(this, worldGuardHook, policy));
        abilityService.registerHandler(new PhaseStepAbility(worldGuardHook, combatTagService, policy));
        
        // Store Berserk reference for damage listener
        this.berserkAbility = new BerserkAbility(this, worldGuardHook, cooldownService, policy);
        abilityService.registerHandler(berserkAbility);
        
        // Store Protocol reference for damage listener
        this.protocolAbility = new ProtocolAbility(this, cooldownService);
        abilityService.registerHandler(protocolAbility);
        
        abilityService.registerHandler(new ProtectorAbility(this, worldGuardHook, policy));
        
        this.iceNovaAbility = new IceNovaAbility(this, worldGuardHook, cooldownService, policy);
        abilityService.registerHandler(iceNovaAbility);
        
        this.phaseWalkAbility = new PhaseWalkAbility(this, worldGuardHook, policy, phaseWalkService);
        abilityService.registerHandler(phaseWalkAbility);
        
        // Initialize Damage Service
        this.damageService = new com.yourorg.kingdomcore.core.services.impl.KingdomDamageServiceImpl(
            this, afkService, combatTagService, phaseWalkService, heartService, protocolAbility
        );
    }
    
    private com.yourorg.kingdomcore.core.services.KingdomDamageService damageService;
    
    public com.yourorg.kingdomcore.core.services.KingdomDamageService getDamageService() {
        return damageService;
    }

    private void registerListeners(PlayerStateRepository playerStateRepository,
                                   com.yourorg.kingdomcore.gui.MenuFactory menuFactory,
                                   MonumentService monumentService) {
        java.util.List<String> spawnRegions = resolveSpawnRegions();

        getServer().getPluginManager().registerEvents(
                new PreLoginListener(configValues, allowlistService, heartService, debugTelemetryService, playerStateRepository), this);
        getServer().getPluginManager().registerEvents(
                new JoinListener(this, heartService, healthService, cooldownService, reviveService,
                        abilityOwnershipService, uniqueItemService, itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new QuitListener(cooldownService, abilityService, combatTagService, afkService, combatRules), this);
        getServer().getPluginManager().registerEvents(
                new InteractListener(configValues, abilityService, cooldownService, heartService,
                        debugTelemetryService, itemIdentityService, combatTagService, menuFactory, assassinEventService), this);
        getServer().getPluginManager().registerEvents(
                new InteractEntityListener(configValues, abilityService, cooldownService, heartService,
                        debugTelemetryService, itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new SpaceAbilityListener(abilityService, heartService), this);
        getServer().getPluginManager().registerEvents(
                new PDummyListener(this), this);
        getServer().getPluginManager().registerEvents(
                new DeathListener(configValues, heartService, abilityService), this);
        getServer().getPluginManager().registerEvents(
                new ArmorListener(this, heartService, healthService, itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new BlockProtectionListener(configValues, worldGuardHook, citizensHook, spawnProtectionService), this);
        getServer().getPluginManager().registerEvents(
                new SpawnEnterListener(worldGuardHook, spawnRegionPolicy, abilityService), this);
        getServer().getPluginManager().registerEvents(
                new CombatTagListener(combatTagService, combatRules), this);
        getServer().getPluginManager().registerEvents(
                new CombatRestrictionListener(combatTagService, combatRules), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.CombatSpawnEntryListener(
                        combatTagService, worldGuardHook, spawnRegions, combatRules), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.SpawnLeaveListener(
                        spawnLeaveLockService, worldGuardHook, spawnRegions), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.DiamondBeamListener(diamondBeamService), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.AssassinEventListener(
                        assassinEventService, worldGuardHook, configValues.getSpawnRegionName()), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.CombatCommandBlockListener(combatTagService, combatRules, this), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.NpcOnlyCommandListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.MonumentLegacyHologramCleaner(this, monumentService), this);
        getServer().getPluginManager().registerEvents(
                new AbilityDamageListener(heartService, protocolAbility, berserkAbility), this);
        getServer().getPluginManager().registerEvents(
                new IceNovaListener(iceNovaAbility), this);
        getServer().getPluginManager().registerEvents(
                new PhaseWalkDamageListener(phaseWalkService), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.TntMinecartDamageListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.DeathDropListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.BannedItemListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.TotemRestrictionListener(combatTagService, combatRules, this), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.DimensionListener(this), this);
        if (getConfig().getBoolean("mob-rules.disable-phantoms", true)) {
            getServer().getPluginManager().registerEvents(
                    new com.yourorg.kingdomcore.listener.PhantomSpawnListener(), this);
        }
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.NetheriteBlockListener(itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new UniqueItemLifecycleListener(this, itemIdentityService, uniqueItemService, heartService, configValues), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.BountyDeathListener(bountyService, heartService), this);
        getServer().getPluginManager().registerEvents(
                scytheAbilityListener = new ScytheAbilityListener(this, itemIdentityService, worldGuardHook, spawnRegionPolicy), this);
        long maceHitCooldownMs = getConfig().getLong("unique-items.mace-hit-cooldown-seconds", 0L) * 1000L;
        if (maceHitCooldownMs > 0L) {
            getServer().getPluginManager().registerEvents(
                    new com.yourorg.kingdomcore.listeners.MaceHitCooldownListener(
                            itemIdentityService, maceHitCooldownMs), this);
        }
        getServer().getPluginManager().registerEvents(
                wardenChestplateListener = new WardenChestplateListener(this, itemIdentityService, uniqueItemService, worldGuardHook, spawnRegionPolicy), this);
        getServer().getPluginManager().registerEvents(
                tridentAbilityListener = new TridentAbilityListener(
                        this,
                        itemIdentityService,
                        damageService,
                        spawnRegionPolicy,
                        new com.yourorg.kingdomcore.util.TabTeamResolver(
                                getConfig().getConfigurationSection("tab-prefixes.players"),
                                getConfig().getConfigurationSection("chat-styles.players"))), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.TridentEnchantListener(itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.PoseidonTridentAttackSpeedListener(this, itemIdentityService), this);
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listeners.AfkListener(afkService), this);
        getServer().getPluginManager().registerEvents(
                new HeartHealthCapListener(heartService, healthService), this);
    }

    private List<Migration> migrations() {
        String v1 = "CREATE TABLE IF NOT EXISTS player_state (" +
                "uuid TEXT PRIMARY KEY," +
                "last_name TEXT," +
                "ability_id TEXT," +
                "ability_level INTEGER," +
                "progression_hearts INTEGER," +
                "blocked_state INTEGER," +
                "updated_at INTEGER" +
                ");" +
                "CREATE TABLE IF NOT EXISTS bounties (" +
                "uuid TEXT PRIMARY KEY," +
                "bounty_amount INTEGER" +
                ");" +
                "CREATE TABLE IF NOT EXISTS ability_owners (" +
                "ability_id TEXT PRIMARY KEY," +
                "owner_uuid TEXT UNIQUE," +
                "claimed_at INTEGER" +
                ");" +
                "CREATE TABLE IF NOT EXISTS cooldowns (" +
                "uuid TEXT," +
                "ability_id TEXT," +
                "ready_at_ms INTEGER," +
                "PRIMARY KEY(uuid, ability_id)" +
                ");" +
                "CREATE TABLE IF NOT EXISTS allowlist (" +
                "name TEXT PRIMARY KEY" +
                ");" +
                "CREATE TABLE IF NOT EXISTS revive_audit (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "reviver_uuid TEXT," +
                "target_uuid TEXT," +
                "ts INTEGER," +
                "success INTEGER" +
                ");";
        String v2 = "CREATE TABLE IF NOT EXISTS unique_item_cooldowns (" +
                "item_id TEXT PRIMARY KEY," +
                "unlock_at_ms INTEGER" +
                ");";
        String v3 = "CREATE TABLE IF NOT EXISTS bounties (" +
                "uuid TEXT PRIMARY KEY," +
                "bounty_amount INTEGER" +
                ");";
        String v4 = "CREATE TABLE IF NOT EXISTS player_auth (" +
                "uuid TEXT PRIMARY KEY," +
                "pin_hash TEXT," +
                "last_ip TEXT" +
                ");";
        String v5 = "ALTER TABLE player_auth ADD COLUMN failed_attempts INTEGER DEFAULT 0;";
        String v6 = "ALTER TABLE player_state ADD COLUMN assassin_win_bonus INTEGER DEFAULT 0;";
        String v7 = "ALTER TABLE player_state ADD COLUMN combat_log_pending_at INTEGER DEFAULT 0;";
        return List.of(
                new Migration(1, "initial schema", v1),
                new Migration(2, "unique item cooldowns", v2),
                new Migration(3, "bounties table", v3),
                new Migration(4, "auth table", v4),
                new Migration(5, "auth failed attempts", v5),
                new Migration(6, "assassin win bonus", v6),
                new Migration(7, "combat log pending", v7)
        );
    }

    public KingdomConfig getConfigValues() {
        return configValues;
    }

    @Override
    public void onDisable() {
        if (assassinEventService != null) {
            assassinEventService.resetEvent();
        }
        if (villagerBedDistanceService != null) {
            villagerBedDistanceService.stop();
        }
        if (cartographerTradeService != null) {
            cartographerTradeService.stop();
        }
        if (clericTradeService != null) {
            clericTradeService.stop();
        }
        if (longStrongStrengthPotionService != null) {
            longStrongStrengthPotionService.stop();
        }
        if (diamondBeamService != null) {
            diamondBeamService.shutdown();
        }
        if (database != null) {
            database.checkpointAndClose();
        }
    }

    private void registerClientLockdown() {
        if (!getConfig().getBoolean("client-lockdown.enabled", true)) {
            getLogger().info("Client lockdown disabled in config.");
            return;
        }
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.ClientLockdownListener(
                        this,
                        getConfig().getStringList("client-lockdown.forbidden-patterns"),
                        getConfig().getBoolean("client-lockdown.block-seed-command", true),
                        getConfig().getBoolean("client-lockdown.op-bypass", false),
                        getConfig().getString("client-lockdown.kick-message")
                ),
                this
        );
        getLogger().info("Client lockdown enabled (seed block + forbidden mod channels).");
    }

    private void registerTabPrefixes() {
        if (!getConfig().getBoolean("tab-prefixes.enabled", true)) {
            return;
        }
        tabPrefixListener = new com.yourorg.kingdomcore.listener.TabPrefixListener(
                this,
                getConfig().getConfigurationSection("tab-prefixes.players"),
                getConfig().getConfigurationSection("chat-styles.players"));
        getServer().getPluginManager().registerEvents(tabPrefixListener, this);
        tabPrefixListener.applyToOnlinePlayers();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (tabPrefixListener != null) {
                tabPrefixListener.applyToOnlinePlayers();
            }
        }, 40L);
        getLogger().info("Tab prefixes enabled.");
    }

    private void registerChatStyles() {
        if (!getConfig().getBoolean("chat-styles.enabled", true)) {
            return;
        }
        getServer().getPluginManager().registerEvents(
                new com.yourorg.kingdomcore.listener.ChatStyleListener(
                        getConfig().getConfigurationSection("chat-styles.players")),
                this);
        getLogger().info("Chat styles enabled.");
    }

    private java.util.List<String> resolveSpawnRegions() {
        java.util.List<String> spawnRegions = new java.util.ArrayList<>(
                getConfig().getStringList("spawn-protection.worldguard-regions"));
        if (spawnRegions.isEmpty()) {
            spawnRegions.add(configValues.getSpawnRegionName());
        }
        return spawnRegions;
    }
}
