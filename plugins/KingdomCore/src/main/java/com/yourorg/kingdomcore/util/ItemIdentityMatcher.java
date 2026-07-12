package com.yourorg.kingdomcore.util;

public final class ItemIdentityMatcher {
    private ItemIdentityMatcher() {
    }

    public static boolean matches(String itemsAdderId, String pdcId, String targetId) {
        if (targetId == null) {
            return false;
        }
        if (itemsAdderId != null && itemsAdderId.equalsIgnoreCase(targetId)) {
            return true;
        }
        return pdcId != null && pdcId.equalsIgnoreCase(targetId);
    }
}
