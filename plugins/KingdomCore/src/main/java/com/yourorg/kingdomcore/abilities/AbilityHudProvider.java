package com.yourorg.kingdomcore.abilities;

import java.util.UUID;

/**
 * Optional ability hook for custom actionbar HUD text.
 */
public interface AbilityHudProvider {
    /**
     * @return custom HUD line or null to use default HUD
     */
    String getHudLine(UUID playerId, long nowMs);
}

