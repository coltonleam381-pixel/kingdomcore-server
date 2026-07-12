# Integration Test Checklist

- ItemsAdder available: verify heart/crown/revive beacon IDs resolve and items created by IDs.
- ItemsAdder missing: verify PDC fallback items still validate.
- Citizens available: verify NPC clicks open ability/upgrade/revive menus.
- Citizens missing: verify fallback commands still open menus (use admin command to open if needed).
- WorldGuard region present: verify block break/place cancelled in spawn region.
- WorldGuard missing: verify plugin logs warning and does not bypass existing protections.
- SQLite file persists ability ownership and progression across restart.
- Multiple players: verify ability uniqueness across claims.
