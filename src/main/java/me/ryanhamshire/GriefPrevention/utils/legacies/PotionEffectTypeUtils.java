package me.ryanhamshire.GriefPrevention.utils.legacies;

import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;

public class PotionEffectTypeUtils {

    private static final HashMap<String, PotionEffectType> legacyEffects = new HashMap<>();

    static {
        // Backwards compatibility for looking up legacy bukkit enums
        addLegacyEffectLookup("JUMP_BOOST", "JUMP");
        addLegacyEffectLookup("NAUSEA", "CONFUSION");
        addLegacyEffectLookup("STRENGTH", "INCREASE_DAMAGE");
        addLegacyEffectLookup("WEAKNESS", "DECREASE_DAMAGE");
        addLegacyEffectLookup("HASTE", "FAST_DIGGING");
        addLegacyEffectLookup("MINING_FATIGUE", "SLOW_DIGGING");
        addLegacyEffectLookup("SLOWNESS", "SLOW");
        addLegacyEffectLookup("INSTANT_DAMAGE", "HARM");
        addLegacyEffectLookup("INSTANT_HEALTH", "HEAL");
        addLegacyEffectLookup("RESISTANCE", "DAMAGE_RESISTANCE");
    }

    public static PotionEffectType of(String potionEffectTypeName) {
        if (legacyEffects.containsKey(potionEffectTypeName)) {
            return legacyEffects.get(potionEffectTypeName);
        }

        return PotionEffectType.getByName(potionEffectTypeName);
    }

    // If the server is on a version that uses legacy names, add the legacy name to the map with the modern name as the key
    private static void addLegacyEffectLookup(String potionEffectTypeName, String legacyBukkitName) {
        PotionEffectType effectType = PotionEffectType.getByName(legacyBukkitName);

        if (effectType != null) {
            legacyEffects.put(potionEffectTypeName, effectType);
        }
    }
}
