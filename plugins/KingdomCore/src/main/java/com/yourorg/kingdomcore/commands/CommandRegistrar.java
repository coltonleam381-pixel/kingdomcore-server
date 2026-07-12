package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.*;
import com.yourorg.kingdomcore.service.UniqueItemService;
import com.yourorg.kingdomcore.service.MonumentService;
import com.yourorg.kingdomcore.service.AfkService;

public final class CommandRegistrar {
    private CommandRegistrar() {
    }

    public static void registerAll(KingdomCorePlugin plugin,
                                   KingdomConfig config,
                                   AbilityService abilityService,
                                   AbilityOwnershipService abilityOwnershipService,
                                   CooldownService cooldownService,
                                   HeartService heartService,
                                   HealthService healthService,
                                   AllowlistService allowlistService,
                                   ReviveService reviveService,
                                   DebugTelemetryService debugTelemetryService,
                                   ItemIdentityService itemIdentityService,
                                   UniqueItemService uniqueItemService,
                                   com.yourorg.kingdomcore.service.MonumentService monumentService,
                                   com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository playerStateRepository,
                                   com.yourorg.kingdomcore.core.services.BountyService bountyService,
                                   com.yourorg.kingdomcore.service.AfkService afkService,
                                   com.yourorg.kingdomcore.service.SpawnProtectionService spawnProtectionService,
                                   com.yourorg.kingdomcore.service.SpawnLeaveLockService spawnLeaveLockService,
                                   com.yourorg.kingdomcore.service.DiamondBeamService diamondBeamService,
                                   com.yourorg.kingdomcore.service.AssassinEventService assassinEventService,
                                   com.yourorg.kingdomcore.util.CombatRules combatRules,
                                   com.yourorg.kingdomcore.abilities.AbilityScoreboard abilityScoreboard,
                                   com.yourorg.kingdomcore.service.SelectionService selectionService) {
        
        BountyCommand bountyCommand = new BountyCommand(bountyService, playerStateRepository, heartService, itemIdentityService);
        plugin.getCommand("bounty391381").setExecutor(bountyCommand);
        plugin.getServer().getPluginManager().registerEvents(bountyCommand, plugin);

        plugin.getCommand("ability").setExecutor(new AbilityAdminCommand(abilityService, abilityOwnershipService, heartService));
        plugin.getCommand("chooseability").setExecutor(new ChooseAbilityCommand(abilityService, abilityOwnershipService, heartService));
        plugin.getCommand("hearts").setExecutor(new HeartsCommand(config, heartService, itemIdentityService, abilityScoreboard));
        plugin.getCommand("dimension").setExecutor(new com.yourorg.kingdomcore.commands.DimensionCommand(plugin));
        plugin.getCommand("pvp").setExecutor(new com.yourorg.kingdomcore.commands.PvpCommand(plugin));
        plugin.getCommand("spawnprotect").setExecutor(new SpawnProtectionCommand(spawnProtectionService));
        plugin.getCommand("spawnleave").setExecutor(new SpawnLeaveCommand(spawnLeaveLockService));
        plugin.getCommand("diamondbeam").setExecutor(new DiamondBeamCommand(diamondBeamService));
        plugin.getCommand("combatspawn").setExecutor(new CombatSpawnCommand(plugin, combatRules));
        plugin.getCommand("combatcd").setExecutor(new CombatCdCommand(plugin, combatRules));
        plugin.getCommand("withdraw").setExecutor(new WithdrawCommand(config, heartService, abilityScoreboard, playerStateRepository, plugin));
        plugin.getCommand("allowlist").setExecutor(new AllowlistCommand(allowlistService));
        plugin.getCommand("kingdomcore").setExecutor(new KingdomCoreCommand(debugTelemetryService, plugin));
        plugin.getCommand("revive").setExecutor(new ReviveAdminCommand(reviveService));
        plugin.getCommand("revivecancel").setExecutor(new ReviveCancelCommand());
        plugin.getCommand("removeability").setExecutor(new RemoveAbilityCommand(abilityService, abilityOwnershipService, heartService));
        plugin.getCommand("removelvl").setExecutor(new RemoveLevelCommand(heartService));
        plugin.getCommand("cd").setExecutor(new CooldownAdminCommand(cooldownService, heartService, abilityService));
        plugin.getCommand("pdummy").setExecutor(new PDummyCommand());
        plugin.getCommand("activateability").setExecutor(new ActivateAbilityCommand(plugin));
        plugin.getCommand("craftcustomitems").setExecutor(new CraftCustomItemsCommand(itemIdentityService));
        plugin.getCommand("kcraft").setExecutor(new UniqueCraftCommand(uniqueItemService, itemIdentityService, heartService, config));
        plugin.getCommand("afk").setExecutor(new AfkCommand(afkService));
        plugin.getCommand("ksetmonument").setExecutor(new SetMonumentCommand(monumentService));
        plugin.getCommand("kdelmonument").setExecutor(new DelMonumentCommand(monumentService));
        plugin.getCommand("kreset").setExecutor(new ResetCommand(uniqueItemService));
        plugin.getCommand("kresetability").setExecutor(new ResetAbilityCommand());

        AbilityUpgradeCommand abilityUpgradeCommand = new AbilityUpgradeCommand(
                abilityService,
                heartService,
                itemIdentityService,
                config.getAbilityLevelCosts()
        );
        plugin.getCommand("abilityupgrade").setExecutor(abilityUpgradeCommand);
        plugin.getServer().getPluginManager().registerEvents(abilityUpgradeCommand, plugin);

        plugin.getCommand("assasinevent").setExecutor(new AssassinEventCommand(assassinEventService));

        InvCheckCommand invCheckCommand = new InvCheckCommand(
                plugin,
                heartService,
                healthService,
                itemIdentityService,
                abilityService,
                config,
                playerStateRepository);
        plugin.getCommand("invcheck").setExecutor(invCheckCommand);
        plugin.getCommand("invcheck").setTabCompleter(invCheckCommand);
        plugin.getServer().getPluginManager().registerEvents(invCheckCommand, plugin);

        plugin.getCommand("heartscheck").setExecutor(invCheckCommand);
        plugin.getCommand("heartscheck").setTabCompleter(invCheckCommand);

        plugin.getCommand("select").setExecutor(new SelectCommand(plugin, selectionService));

        HundredDogCommand hundredDogCommand = new HundredDogCommand(plugin);
        plugin.getCommand("100dog").setExecutor(hundredDogCommand);
        plugin.getServer().getPluginManager().registerEvents(new com.yourorg.kingdomcore.listener.DogRodListener(plugin), plugin);
    }
}
