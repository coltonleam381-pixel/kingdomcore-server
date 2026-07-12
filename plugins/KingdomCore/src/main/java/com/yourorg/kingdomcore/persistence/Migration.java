package com.yourorg.kingdomcore.persistence;

public record Migration(int version, String description, String sql) {
}
