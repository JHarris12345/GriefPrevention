package me.ryanhamshire.GriefPrevention.utils.legacies;

import org.bukkit.Particle;

import java.util.HashMap;

public class ParticleUtils {

    private static final HashMap<String, Particle> legacyParticles = new HashMap<>();

    static {
        // Backwards compatibility for looking up legacy bukkit enums
        addLegacyParticleLookup("POOF", "EXPLOSION_NORMAL");
        addLegacyParticleLookup("EXPLOSION", "EXPLOSION_LARGE");
        addLegacyParticleLookup("EXPLOSION_EMITTER", "EXPLOSION_HUGE");
        addLegacyParticleLookup("FIREWORKS", "FIREWORKS_SPARK");
        addLegacyParticleLookup("BUBBLE", "WATER_BUBBLE");
        addLegacyParticleLookup("SPLASH", "WATER_SPLASH");
        addLegacyParticleLookup("FISHING", "WATER_WAKE");
        addLegacyParticleLookup("UNDERWATER", "SUSPENDED");
        addLegacyParticleLookup("UNDERWATER", "SUSPENDED_DEPTH");
        addLegacyParticleLookup("ENCHANTED_HIT", "CRIT_MAGIC");
        addLegacyParticleLookup("SMOKE", "SMOKE_NORMAL");
        addLegacyParticleLookup("LARGE_SMOKE", "SMOKE_LARGE");
        addLegacyParticleLookup("EFFECT", "SPELL");
        addLegacyParticleLookup("INSTANT_EFFECT", "SPELL_INSTANT");
        addLegacyParticleLookup("ENTITY_EFFECT", "SPELL_MOB");
        addLegacyParticleLookup("ENTITY_EFFECT", "SPELL_MOB_AMBIENT");
        addLegacyParticleLookup("WITCH", "SPELL_WITCH");
        addLegacyParticleLookup("DRIPPING_WATER", "DRIP_WATER");
        addLegacyParticleLookup("DRIPPING_LAVA", "DRIP_LAVA");
        addLegacyParticleLookup("ANGRY_VILLAGER", "VILLAGER_ANGRY");
        addLegacyParticleLookup("HAPPY_VILLAGER", "VILLAGER_HAPPY");
        addLegacyParticleLookup("MYCELIUM", "TOWN_AURA");
        addLegacyParticleLookup("ENCHANT", "ENCHANTMENT_TABLE");
        addLegacyParticleLookup("DUST", "REDSTONE");
        addLegacyParticleLookup("ITEM_SNOWBALL", "SNOWBALL");
        addLegacyParticleLookup("ITEM_SNOWBALL", "SNOW_SHOVEL");
        addLegacyParticleLookup("ITEM_SLIME", "SLIME");
        addLegacyParticleLookup("ITEM", "ITEM_CRACK");
        addLegacyParticleLookup("BLOCK", "BLOCK_CRACK");
        addLegacyParticleLookup("RAIN", "WATER_DROP");
        addLegacyParticleLookup("ELDER_GUARDIAN", "MOB_APPEARANCE");
        addLegacyParticleLookup("TOTEM_OF_UNDYING", "TOTEM");
        addLegacyParticleLookup("GUST_EMITTER_LARGE", "GUST_EMITTER");
        addLegacyParticleLookup("BLOCK ", "GUST_DUST");
    }

    public static Particle of(String particleName) {
        if (legacyParticles.containsKey(particleName)) {
            return legacyParticles.get(particleName);
        }

        return Particle.valueOf(particleName);
    }

    // If the server is on a version that uses legacy names, add the legacy name to the map with the modern name as the key
    private static void addLegacyParticleLookup(String particleName, String legacyBukkitName) {
        try {
            Particle particle = Particle.valueOf(legacyBukkitName);
            legacyParticles.put(particleName, particle);

        } catch (IllegalArgumentException ignored) {}
    }
}
