package me.ryanhamshire.GriefPrevention.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class Placeholders extends PlaceholderExpansion {

    GriefPrevention plugin;

    public Placeholders(GriefPrevention plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "griefprevention";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JHarris";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }


    @Override
    public String onRequest(OfflinePlayer player, String identifier) {

        // %griefprevention_remainingclaims_formatted%
        if (identifier.equalsIgnoreCase("remainingclaims_formatted")) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            return plugin.df.format(playerData.getRemainingClaimBlocks());
        }

        return null;
    }
}
