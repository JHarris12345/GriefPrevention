package me.ryanhamshire.GriefPrevention.utils.legacies;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashMap;

public class MaterialUtils{
    private static final HashMap<String, Material> legacyMaterials = new HashMap<>();

    static {
        // Backwards compatibility for looking up legacy bukkit enums
        addLegacyMaterialLookup("SHORT_GRASS", "GRASS");
    }

    public static Material of(String material) {
        if (legacyMaterials.containsKey(material)) {
            return legacyMaterials.get(material);
        }

        return Material.valueOf(material);
    }

    // If the server is on a version that uses legacy names, add the legacy name to the map with the modern name as the key
    private static void addLegacyMaterialLookup(String material, String legacyBukkitName) {
        try {
            Material mat = Material.valueOf(legacyBukkitName);
            legacyMaterials.put(material, mat);

        } catch (IllegalArgumentException ignored) {}
    }
}
