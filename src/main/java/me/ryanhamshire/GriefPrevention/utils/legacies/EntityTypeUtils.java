package me.ryanhamshire.GriefPrevention.utils.legacies;

import org.bukkit.entity.EntityType;

import java.util.HashMap;

public class EntityTypeUtils {
    private static final HashMap<String, EntityType> legacyEntityTypes = new HashMap<>();

    static {
        // Backwards compatibility for looking up legacy bukkit enums
        addLegacyEntityTypeLookup("MOOSHROOM", "MUSHROOM_COW");
        addLegacyEntityTypeLookup("SNOW_GOLEM", "SNOWMAN");
        addLegacyEntityTypeLookup("SNOW_GOLEM", "SNOWMAN");
        addLegacyEntityTypeLookup("ITEM", "DROPPED_ITEM");
        addLegacyEntityTypeLookup("TNT", "PRIMED_TNT");
        addLegacyEntityTypeLookup("FIREWORK_ROCKET", "FIREWORK");
        addLegacyEntityTypeLookup("END_CRYSTAL", "ENDER_CRYSTAL");
    }

    public static EntityType of(String entityType) {
        if (legacyEntityTypes.containsKey(entityType)) {
            return legacyEntityTypes.get(entityType);
        }

        return EntityType.valueOf(entityType);
    }

    // If the server is on a version that uses legacy names, add the legacy name to the map with the modern name as the key
    private static void addLegacyEntityTypeLookup(String entityType, String legacyBukkitName) {
        try {
            EntityType type = EntityType.valueOf(legacyBukkitName);
            legacyEntityTypes.put(entityType, type);

        } catch (IllegalArgumentException ignored) {}
    }
}
