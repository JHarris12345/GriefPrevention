package me.ryanhamshire.GriefPrevention.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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

        // %griefprevention_setwarpaccess_[world]_[x]_[z]
        if (identifier.startsWith("setwarpaccess_")) {
            World world = Bukkit.getWorld(identifier.split("_")[1]);
            int x = Integer.parseInt(identifier.split("_")[2]);
            int z = Integer.parseInt(identifier.split("_")[3]);

            Location loc = new Location(world, x, 100, z);

            Claim claim = plugin.dataStore.getClaimAt(loc, true, null);
            if (claim == null) return "true";

            return (claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.SET_WARP_ACCESS)) ? "true" : "false";
        }

        return null;
    }
}
