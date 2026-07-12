# KingdomCore

Production-ready Paper 1.21.8 plugin (Java 21) with unique ability ownership, heart progression, revive flow, and spawn protection compatibility.

## Features
- Ability selection via NPC, global uniqueness, and upgrade management.
- Activation by renamed item matching assigned ability name (color-stripped, trimmed, lowercase).
- Heart progression, death penalty, revive flow, and crown bonus.
- Allowlist gate at login.
- SQLite storage with migrations.
- Soft hooks for ItemsAdder, Citizens, WorldGuard, CommandPanels.

## Setup
1. Build the plugin:
   - `./gradlew test`
   - `./gradlew build`
2. Copy the JAR from `build/libs` into your server `plugins/` directory.
3. Start the server once to generate configs.
4. Configure items and abilities in `config.yml`.
5. If using Citizens, add NPC names to `spawn-protection.npc-ability-names` and `spawn-protection.npc-revive-names`.
6. If using WorldGuard, create region matching `spawn-protection.worldguard-region`.

## Heart Consumption
- Sneak + right-click with a heart item in main hand to consume one heart.

## Commands
- `/ability admin reset <player>`
- `/ability admin set <player> <ability>`
- `/ability admin setlevel <player> <0-3>`
- `/hearts withdraw <amount>` (if enabled)
- `/allowlist add <nick>`
- `/allowlist remove <nick>`
- `/allowlist reload`
- `/kingdomcore debug on|off`
- `/kingdomcore debug player <nick>`
- `/revive admin unblock <player>`
- `/abilitymenu`
- `/revivemenu`
- `/revivecancel`

## Migration Notes
- Schema is managed by `schema_version` in SQLite.
- Do not edit tables manually while the server is running.

## Integration Test Checklist
See [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md).
