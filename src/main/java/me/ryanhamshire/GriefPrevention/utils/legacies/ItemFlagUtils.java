package me.ryanhamshire.GriefPrevention.utils.legacies;

import org.bukkit.inventory.ItemFlag;

import java.util.HashMap;

public class ItemFlagUtils {

    private static final HashMap<String, ItemFlag> legacyFlags = new HashMap<>();

    static {
        // Backwards compatibility for looking up legacy bukkit enums
        addLegacyParticleLookup("HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS");
    }

    public static ItemFlag of(String itemFlag) {
        if (legacyFlags.containsKey(itemFlag)) {
            return legacyFlags.get(itemFlag);
        }

        return ItemFlag.valueOf(itemFlag);
    }

    // If the server is on a version that uses legacy names, add the legacy name to the map with the modern name as the key
    private static void addLegacyParticleLookup(String itemFlag, String legacyItemFlag) {
        try {
            ItemFlag flag = ItemFlag.valueOf(legacyItemFlag);
            legacyFlags.put(itemFlag, flag);

        } catch (IllegalArgumentException ignored) {}
    }
}
