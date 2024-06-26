package me.ryanhamshire.GriefPrevention.commands;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import me.ryanhamshire.GriefPrevention.managers.EconomyManager;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.objects.enums.Messages;
import me.ryanhamshire.GriefPrevention.objects.enums.ShovelMode;
import me.ryanhamshire.GriefPrevention.tasks.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.tasks.PlayerRescueTask;
import me.ryanhamshire.GriefPrevention.tasks.WelcomeTask;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.function.Supplier;

public class CommandHandler {

    GriefPrevention plugin;

    public CommandHandler(GriefPrevention plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        // claim
        if (cmd.getName().equalsIgnoreCase("claim") && player != null) {
            if (!GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                return true;
            }

            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

            // if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
            if (GriefPrevention.instance.config_claims_maxClaimsPerPlayer > 0 &&
                    !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                    playerData.getClaims().size() >= GriefPrevention.instance.config_claims_maxClaimsPerPlayer) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                return true;
            }

            // default is chest claim radius, unless -1
            int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;
            if (radius < 0) radius = (int) Math.ceil(Math.sqrt(GriefPrevention.instance.config_claims_minArea) / 2);

            // if player has any claims, respect claim minimum size setting
            if (playerData.getClaims().size() > 0) {
                // if player has exactly one land claim, this requires the claim modification tool to be in hand (or creative mode player)
                if (playerData.getClaims().size() == 1 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                    return true;
                }

                radius = (int) Math.ceil(Math.sqrt(GriefPrevention.instance.config_claims_minArea) / 2);
            }

            // allow for specifying the radius
            if (args.length > 0) {
                if (playerData.getClaims().size() < 2 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.RadiusRequiresGoldenShovel);
                    return true;
                }

                int specifiedRadius;
                try {
                    specifiedRadius = Integer.parseInt(args[0]);
                }
                catch (NumberFormatException e) {
                    return false;
                }

                if (specifiedRadius < radius) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(radius));
                    return true;
                }
                else {
                    radius = specifiedRadius;
                }
            }

            if (radius < 0) radius = 0;

            Location lc = player.getLocation().add(-radius, 0, -radius);
            Location gc = player.getLocation().add(radius, 0, radius);

            // player must have sufficient unused claim blocks
            int area = Math.abs((gc.getBlockX() - lc.getBlockX() + 1) * (gc.getBlockZ() - lc.getBlockZ() + 1));
            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(area - remaining));
                GriefPrevention.instance.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }

            CreateClaimResult result = plugin.dataStore.createClaim(lc.getWorld(),
                    lc.getBlockX(), gc.getBlockX(),
                    lc.getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance - 1,
                    gc.getWorld().getHighestBlockYAt(gc) - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance - 1,
                    lc.getBlockZ(), gc.getBlockZ(),
                    player.getUniqueId(), null, null, player);
            if (!result.succeeded || result.claim == null) {
                if (result.claim != null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);

                    BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
                }
                else {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                }
            }
            else {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);

                // link to a video demo of land claiming, based on world type
                if (GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                }
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);
                playerData.claimResizing = null;
                playerData.lastShovelLocation = null;

                AutoExtendClaimTask.scheduleAsync(result.claim);
            }

            return true;
        }
        
        // nameclaim [name]
        if (cmd.getName().equalsIgnoreCase("nameclaim") && player != null) {
            if (args.length != 1) return false;

            String regex = "^[a-zA-Z0-9]{1,18}$";
            String name = args[0];

            if (!name.matches(regex)) {
                player.sendMessage(Utils.colour("&7Claim names must be alphanumeric and be a maximum length of 18 characters"));
                return true;
            }

            // must be standing in a land claim
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, true, playerData.lastClaim);
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.StandInClaimToRename);
                return true;
            }

            // must have permission to edit the land claim you're in
            Supplier<String> errorMessage = claim.checkPermission(player, ClaimPermission.Edit, null);
            if (errorMessage != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
                return true;
            }

            plugin.dataStore.setClaimName(claim, name);
            player.sendMessage(Utils.colour("&7You set the name of your claim to &b" + name));
            return true;
        }

        // extendclaim
        if (cmd.getName().equalsIgnoreCase("extendclaim") && player != null) {
            if (args.length < 1) {
                // link to a video demo of land claiming, based on world type
                if (GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.instance.claimsEnabledForWorld(player.getLocation().getWorld())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                }
                return false;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e) {
                // link to a video demo of land claiming, based on world type
                if (GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.instance.claimsEnabledForWorld(player.getLocation().getWorld())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                }
                return false;
            }

            // requires claim modification tool in hand
            if (player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                return true;
            }

            // must be standing in a land claim
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, playerData.lastClaim);
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.StandInClaimToResize);
                return true;
            }

            // must have permission to edit the land claim you're in
            Supplier<String> errorMessage = claim.checkPermission(player, ClaimPermission.Edit, null);
            if (errorMessage != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
                return true;
            }

            // determine new corner coordinates
            org.bukkit.util.Vector direction = player.getLocation().getDirection();
            if (direction.getY() > .75) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.ClaimsExtendToSky);
                return true;
            }

            if (direction.getY() < -.75) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.ClaimsAutoExtendDownward);
                return true;
            }

            Location lc = claim.getLesserBoundaryCorner();
            Location gc = claim.getGreaterBoundaryCorner();
            int newx1 = lc.getBlockX();
            int newx2 = gc.getBlockX();
            int newy1 = lc.getBlockY();
            int newy2 = gc.getBlockY();
            int newz1 = lc.getBlockZ();
            int newz2 = gc.getBlockZ();

            // if changing Z only
            if (Math.abs(direction.getX()) < .3) {
                if (direction.getZ() > 0) {
                    newz2 += amount;  // north
                }
                else {
                    newz1 -= amount;  // south
                }
            }

            // if changing X only
            else if (Math.abs(direction.getZ()) < .3) {
                if (direction.getX() > 0) {
                    newx2 += amount;  // east
                }
                else {
                    newx1 -= amount;  // west
                }
            }

            // diagonals
            else {
                if (direction.getX() > 0) {
                    newx2 += amount;
                }
                else {
                    newx1 -= amount;
                }

                if (direction.getZ() > 0) {
                    newz2 += amount;
                }
                else {
                    newz1 -= amount;
                }
            }

            // attempt resize
            playerData.claimResizing = claim;
            plugin.dataStore.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);
            playerData.claimResizing = null;

            return true;
        }

        // abandonclaim
        if (cmd.getName().equalsIgnoreCase("abandonclaim") && player != null) {
            return plugin.abandonClaimHandler(player, false);
        }

        // abandontoplevelclaim
        if (cmd.getName().equalsIgnoreCase("abandontoplevelclaim") && player != null) {
            return plugin.abandonClaimHandler(player, true);
        }

        // ignoreclaims
        if (cmd.getName().equalsIgnoreCase("ignoreclaims") && player != null) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

            playerData.ignoreClaims = !playerData.ignoreClaims;

            // toggle ignore claims mode on or off
            if (!playerData.ignoreClaims) {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.RespectingClaims);
            }
            else {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.IgnoringClaims);
            }

            return true;
        }

        // abandonallclaims
        else if (cmd.getName().equalsIgnoreCase("abandonallclaims") && player != null) {
            if (args.length > 1) return false;

            if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ConfirmAbandonAllClaims);
                return true;
            }

            // count claims
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            int originalClaimCount = playerData.getClaims().size();

            // check count
            if (originalClaimCount == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.YouHaveNoClaims);
                return true;
            }

            if (plugin.config_claims_abandonReturnRatio != 1.0D) {
                // adjust claim blocks
                for (Claim claim : playerData.getClaims()) {
                    playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - (int) Math.ceil((claim.getArea() * (1 - plugin.config_claims_abandonReturnRatio))));
                }
            }


            // delete them
            plugin.dataStore.deleteClaimsForPlayer(player.getUniqueId(), false);

            // inform the player
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.SuccessfulAbandon, String.valueOf(remainingBlocks));

            // revert any current visualization
            playerData.setVisibleBoundaries(null);

            return true;
        }

        // restore nature
        else if (cmd.getName().equalsIgnoreCase("restorenature") && player != null) {
            // change shovel mode
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.RestoreNature;
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.RestoreNatureActivate);
            return true;
        }

        // restore nature aggressive mode
        else if (cmd.getName().equalsIgnoreCase("restorenatureaggressive") && player != null) {
            // change shovel mode
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.RestoreNatureAggressive;
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.RestoreNatureAggressiveActivate);
            return true;
        }

        // restore nature fill mode
        else if (cmd.getName().equalsIgnoreCase("restorenaturefill") && player != null) {
            // change shovel mode
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.RestoreNatureFill;

            // set radius based on arguments
            playerData.fillRadius = 2;
            if (args.length > 0) {
                try {
                    playerData.fillRadius = Integer.parseInt(args[0]);
                }
                catch (Exception exception) {}
            }

            if (playerData.fillRadius < 0) playerData.fillRadius = 2;

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.FillModeActive, String.valueOf(playerData.fillRadius));
            return true;
        }

        // trust <player>
        else if (cmd.getName().equalsIgnoreCase("trust") && player != null) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            // most trust commands use this helper method, it keeps them consistent
            plugin.handleTrustCommand(player, ClaimPermission.Build, args[0]);

            return true;
        }

        // transferclaim <player>
        else if (cmd.getName().equalsIgnoreCase("transferclaim") && player != null) {
            // which claim is the user in?
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.TransferClaimMissing);
                return true;
            }

            // check additional permission for admin claims
            if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TransferClaimPermission);
                return true;
            }

            UUID newOwnerID = null;  // no argument = make an admin claim
            String ownerName = "admin";

            if (args.length > 0) {
                OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
                if (targetPlayer == null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return true;
                }
                newOwnerID = targetPlayer.getUniqueId();
                ownerName = targetPlayer.getName();
            }

            // change ownerhsip
            try {
                plugin.dataStore.changeClaimOwner(claim, newOwnerID);
            }
            catch (DataStore.NoTransferException e) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.TransferTopLevel);
                return true;
            }

            // confirm
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.TransferSuccess);
            GriefPrevention.AddLogEntry(player.getName() + " transferred a claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " to " + ownerName + ".", CustomLogEntryTypes.AdminActivity);

            return true;
        }

        // transferallclaims <fromPlayer> <toPlayer>
        else if (cmd.getName().equalsIgnoreCase("transferallclaims")) {
            if (args.length != 2) return false;

            OfflinePlayer fromPlayer = plugin.resolvePlayerByName(args[0]);
            OfflinePlayer toPlayer = plugin.resolvePlayerByName(args[1]);

            if (fromPlayer == null || toPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            PlayerData fromData = plugin.dataStore.getPlayerData(fromPlayer.getUniqueId());
            for (Claim claim : new Vector<>(fromData.getClaims())) {
                plugin.dataStore.changeClaimOwner(claim, toPlayer.getUniqueId());
            }

            // confirm
            sender.sendMessage(Utils.colour("&6Transferred all of " + fromPlayer.getName() + "'s claims to " + toPlayer.getName()));
            GriefPrevention.AddLogEntry("Transferred all of " + fromPlayer.getName() + "'s claims to " + toPlayer.getName());
            return true;
        }

        // trustlist
        else if (cmd.getName().equalsIgnoreCase("trustlist") && player != null) {
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

            // if no claim here, error message
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrustListNoClaim);
                return true;
            }

            // if no permission to manage permissions, error message
            Supplier<String> errorMessage = claim.checkPermission(player, ClaimPermission.Manage, null);
            if (errorMessage != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, errorMessage.get());
                return true;
            }

            // otherwise build a list of explicit permissions by permission level
            // and send that to the player
            ArrayList<String> builders = new ArrayList<>();
            ArrayList<String> containers = new ArrayList<>();
            ArrayList<String> accessors = new ArrayList<>();
            ArrayList<String> managers = new ArrayList<>();
            claim.getPermissions(builders, containers, accessors, managers);

            GriefPrevention.sendMessage(player, TextMode.Info, Messages.TrustListHeader, claim.getOwnerName());

            StringBuilder permissions = new StringBuilder();
            permissions.append(ChatColor.GOLD).append('>');

            if (managers.size() > 0) {
                for (String manager : managers)
                    permissions.append(plugin.trustEntryToPlayerName(manager)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.YELLOW).append('>');

            if (builders.size() > 0) {
                for (String builder : builders)
                    permissions.append(plugin.trustEntryToPlayerName(builder)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.GREEN).append('>');

            if (containers.size() > 0) {
                for (String container : containers)
                    permissions.append(plugin.trustEntryToPlayerName(container)).append(' ');
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.BLUE).append('>');

            if (accessors.size() > 0) {
                for (String accessor : accessors)
                    permissions.append(plugin.trustEntryToPlayerName(accessor)).append(' ');
            }

            player.sendMessage(permissions.toString());

            player.sendMessage(
                    ChatColor.GOLD + plugin.dataStore.getMessage(Messages.Manage) + " " +
                            ChatColor.YELLOW + plugin.dataStore.getMessage(Messages.Build) + " " +
                            ChatColor.GREEN + plugin.dataStore.getMessage(Messages.Containers) + " " +
                            ChatColor.BLUE + plugin.dataStore.getMessage(Messages.Access));

            if (claim.getSubclaimRestrictions()) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.HasSubclaimRestriction);
            }

            return true;
        }

        // untrust <player> or untrust [<group>]
        else if (cmd.getName().equalsIgnoreCase("untrust") && player != null) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            // determine which claim the player is standing in
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            // determine whether a single player or clearing permissions entirely
            boolean clearPermissions = false;
            OfflinePlayer otherPlayer = null;
            if (args[0].equals("all")) {
                if (claim == null || claim.checkPermission(player, ClaimPermission.Edit, null) == null) {
                    clearPermissions = true;
                }
                else {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClearPermsOwnerOnly);
                    return true;
                }
            }
            else {
                // validate player argument or group argument
                if (!args[0].startsWith("[") || !args[0].endsWith("]")) {
                    otherPlayer = plugin.resolvePlayerByName(args[0]);
                    if (!clearPermissions && otherPlayer == null && !args[0].equals("public")) {
                        // bracket any permissions - at this point it must be a permission without brackets
                        if (args[0].contains(".")) {
                            args[0] = "[" + args[0] + "]";
                        }
                        else {
                            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                            return true;
                        }
                    }

                    // correct to proper casing
                    if (otherPlayer != null)
                        args[0] = otherPlayer.getName();
                }
            }

            // if no claim here, apply changes to all his claims
            if (claim == null) {
                PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

                String idToDrop = args[0];
                if (otherPlayer != null) {
                    idToDrop = otherPlayer.getUniqueId().toString();
                }

                // calling event
                TrustChangedEvent event = new TrustChangedEvent(player, playerData.getClaims(), null, false, idToDrop);
                Bukkit.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return true;
                }

                // dropping permissions
                for (Claim targetClaim : event.getClaims()) {
                    claim = targetClaim;

                    // if untrusting "all" drop all permissions
                    if (clearPermissions) {
                        claim.clearPermissions();
                    }

                    // otherwise drop individual permissions
                    else {
                        claim.dropPermission(idToDrop);
                        claim.managers.remove(idToDrop);
                    }

                    // save changes
                    plugin.dataStore.saveClaim(claim);
                }

                // beautify for output
                if (args[0].equals("public")) {
                    args[0] = "the public";
                }

                // confirmation message
                if (!clearPermissions) {
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.UntrustIndividualAllClaims, args[0]);
                }
                else {
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.UntrustEveryoneAllClaims);
                }
            }

            // otherwise, apply changes to only this claim
            else if (claim.checkPermission(player, ClaimPermission.Manage, null) != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionTrust, claim.getOwnerName());
                return true;
            }
            else {
                // if clearing all
                if (clearPermissions) {
                    // requires owner
                    if (claim.checkPermission(player, ClaimPermission.Edit, null) != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.UntrustAllOwnerOnly);
                        return true;
                    }

                    // calling the event
                    TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, args[0]);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return true;
                    }

                    event.getClaims().forEach(Claim::clearPermissions);
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.ClearPermissionsOneClaim);
                }

                // otherwise individual permission drop
                else {
                    String idToDrop = args[0];
                    if (otherPlayer != null) {
                        idToDrop = otherPlayer.getUniqueId().toString();
                    }
                    boolean targetIsManager = claim.managers.contains(idToDrop);
                    if (targetIsManager && claim.checkPermission(player, ClaimPermission.Edit, null) != null)  // only claim owners can untrust managers
                    {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.ManagersDontUntrustManagers, claim.getOwnerName());
                        return true;
                    }
                    else {
                        // calling the event
                        TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, idToDrop);
                        Bukkit.getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            return true;
                        }

                        event.getClaims().forEach(targetClaim -> targetClaim.dropPermission(event.getIdentifier()));

                        // beautify for output
                        if (args[0].equals("public")) {
                            args[0] = "the public";
                        }

                        GriefPrevention.sendMessage(player, TextMode.Success, Messages.UntrustIndividualSingleClaim, args[0]);
                    }
                }

                // save changes
                plugin.dataStore.saveClaim(claim);
            }

            return true;
        }

        // accesstrust <player>
        else if (cmd.getName().equalsIgnoreCase("accesstrust") && player != null) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            plugin.handleTrustCommand(player, ClaimPermission.Access, args[0]);

            return true;
        }

        // containertrust <player>
        else if (cmd.getName().equalsIgnoreCase("containertrust") && player != null) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            plugin.handleTrustCommand(player, ClaimPermission.Inventory, args[0]);

            return true;
        }

        // permissiontrust <player>
        else if (cmd.getName().equalsIgnoreCase("permissiontrust") && player != null) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            plugin.handleTrustCommand(player, null, args[0]);  // null indicates permissiontrust to the helper method

            return true;
        }

        // restrictsubclaim
        else if (cmd.getName().equalsIgnoreCase("restrictsubclaim") && player != null) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, playerData.lastClaim);
            if (claim == null || claim.parent == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.StandInSubclaim);
                return true;
            }

            //  If player has /ignoreclaims on, continue
            //  If admin claim, fail if this user is not an admin
            //  If not an admin claim, fail if this user is not the owner
            if (!playerData.ignoreClaims && (claim.isAdminClaim() ? !player.hasPermission("griefprevention.adminclaims") : !player.getUniqueId().equals(claim.parent.ownerID))) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlyOwnersModifyClaims, claim.getOwnerName());
                return true;
            }

            if (claim.getSubclaimRestrictions()) {
                claim.setSubclaimRestrictions(false);
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.SubclaimUnrestricted);
            }
            else {
                claim.setSubclaimRestrictions(true);
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.SubclaimRestricted);
            }
            plugin.dataStore.saveClaim(claim);
            return true;
        }

        // buyclaimblocks
        else if (cmd.getName().equalsIgnoreCase("buyclaimblocks") && player != null) {
            // if economy is disabled, don't do anything
            EconomyManager.EconomyWrapper economyWrapper = plugin.economyManager.getWrapper();
            if (economyWrapper == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
                return true;
            }

            if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                return true;
            }

            // if purchase disabled, send error message
            if (GriefPrevention.instance.config_economy_claimBlocksPurchaseCost == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlySellBlocks);
                return true;
            }

            Economy economy = economyWrapper.getEconomy();

            // if no parameter, just tell player cost per block and balance
            if (args.length != 1) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockPurchaseCost, String.valueOf(GriefPrevention.instance.config_economy_claimBlocksPurchaseCost), String.valueOf(economy.getBalance(player)));
                return false;
            }
            else {
                PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

                // try to parse number of blocks
                int blockCount;
                try {
                    blockCount = Integer.parseInt(args[0]);
                }
                catch (NumberFormatException numberFormatException) {
                    return false;  // causes usage to be displayed
                }

                if (blockCount <= 0) {
                    return false;
                }

                // if the player can't afford his purchase, send error message
                double balance = economy.getBalance(player);
                double totalCost = blockCount * GriefPrevention.instance.config_economy_claimBlocksPurchaseCost;
                if (totalCost > balance) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.InsufficientFunds, economy.format(totalCost), economy.format(balance));
                }

                // otherwise carry out transaction
                else {
                    int newBonusClaimBlocks = playerData.getBonusClaimBlocks() + blockCount;

                    // if the player is going to reach max bonus limit, send error message
                    int bonusBlocksLimit = GriefPrevention.instance.config_economy_claimBlocksMaxBonus;
                    if (bonusBlocksLimit != 0 && newBonusClaimBlocks > bonusBlocksLimit) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.MaxBonusReached, String.valueOf(blockCount), String.valueOf(bonusBlocksLimit));
                        return true;
                    }

                    // withdraw cost
                    economy.withdrawPlayer(player, totalCost);

                    // add blocks
                    playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + blockCount);
                    plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

                    // inform player
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.PurchaseConfirmation, economy.format(totalCost), String.valueOf(playerData.getRemainingClaimBlocks()));
                }

                return true;
            }
        }

        // sellclaimblocks <amount>
        else if (cmd.getName().equalsIgnoreCase("sellclaimblocks") && player != null) {
            // if economy is disabled, don't do anything
            EconomyManager.EconomyWrapper economyWrapper = plugin.economyManager.getWrapper();
            if (economyWrapper == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
                return true;
            }

            if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                return true;
            }

            // if disabled, error message
            if (GriefPrevention.instance.config_economy_claimBlocksSellValue == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlyPurchaseBlocks);
                return true;
            }

            // load player data
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            int availableBlocks = playerData.getRemainingClaimBlocks();

            // if no amount provided, just tell player value per block sold, and how many he can sell
            if (args.length != 1) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockSaleValue, String.valueOf(GriefPrevention.instance.config_economy_claimBlocksSellValue), String.valueOf(availableBlocks));
                return false;
            }

            // parse number of blocks
            int blockCount;
            try {
                blockCount = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException numberFormatException) {
                return false;  // causes usage to be displayed
            }

            if (blockCount <= 0) {
                return false;
            }

            // if he doesn't have enough blocks, tell him so
            if (blockCount > availableBlocks) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotEnoughBlocksForSale);
            }

            // otherwise carry out the transaction
            else {
                // compute value and deposit it
                double totalValue = blockCount * GriefPrevention.instance.config_economy_claimBlocksSellValue;
                economyWrapper.getEconomy().depositPlayer(player, totalValue);

                // subtract blocks
                playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() - blockCount);
                plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

                // inform player
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.BlockSaleConfirmation, economyWrapper.getEconomy().format(totalValue), String.valueOf(playerData.getRemainingClaimBlocks()));
            }

            return true;
        }

        // adminclaims
        else if (cmd.getName().equalsIgnoreCase("adminclaims") && player != null) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.Admin;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdminClaimsMode);

            return true;
        }

        // basicclaims
        else if (cmd.getName().equalsIgnoreCase("basicclaims") && player != null) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.Basic;
            playerData.claimSubdividing = null;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.BasicClaimsMode);

            return true;
        }

        // subdivideclaims
        else if (cmd.getName().equalsIgnoreCase("subdivideclaims") && player != null) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            playerData.shovelMode = ShovelMode.Subdivide;
            playerData.claimSubdividing = null;
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionMode);
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, DataStore.SUBDIVISION_VIDEO_URL);

            return true;
        }

        // deleteclaim
        else if (cmd.getName().equalsIgnoreCase("deleteclaim") && player != null) {
            // determine which claim the player is standing in
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            }
            else {
                // deleting an admin claim additionally requires the adminclaims permission
                if (!claim.isAdminClaim() || player.hasPermission("griefprevention.adminclaims")) {
                    PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
                    if (claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion) {
                        GriefPrevention.sendMessage(player, TextMode.Warn, Messages.DeletionSubdivisionWarning);
                        playerData.warnedAboutMajorDeletion = true;
                    }
                    else {
                        claim.removeSurfaceFluids(null);
                        plugin.dataStore.deleteClaim(claim, true, true);

                        // if in a creative mode world, /restorenature the claim
                        if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner()) || GriefPrevention.instance.config_claims_survivalAutoNatureRestoration) {
                            GriefPrevention.instance.restoreClaim(claim, 0);
                        }

                        GriefPrevention.sendMessage(player, TextMode.Success, Messages.DeleteSuccess);
                        GriefPrevention.AddLogEntry(player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()), CustomLogEntryTypes.AdminActivity);

                        // revert any current visualization
                        playerData.setVisibleBoundaries(null);

                        playerData.warnedAboutMajorDeletion = false;
                    }
                }
                else {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CantDeleteAdminClaim);
                }
            }

            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("claimexplosions") && player != null) {
            // determine which claim the player is standing in
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            }
            else {
                Supplier<String> noBuildReason = claim.checkPermission(player, ClaimPermission.Build, null);
                if (noBuildReason != null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
                    return true;
                }

                if (claim.areExplosivesAllowed) {
                    claim.areExplosivesAllowed = false;
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.ExplosivesDisabled);
                }
                else {
                    claim.areExplosivesAllowed = true;
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.ExplosivesEnabled);
                }
            }

            return true;
        }

        // deleteallclaims <player>
        else if (cmd.getName().equalsIgnoreCase("deleteallclaims")) {
            // requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            // try to find that player
            OfflinePlayer otherPlayer = plugin.resolvePlayerByName(args[0]);
            if (otherPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            // delete all that player's claims
            plugin.dataStore.deleteClaimsForPlayer(otherPlayer.getUniqueId(), true);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.DeleteAllSuccess, otherPlayer.getName());
            if (player != null) {
                GriefPrevention.AddLogEntry(player.getName() + " deleted all claims belonging to " + otherPlayer.getName() + ".", CustomLogEntryTypes.AdminActivity);

                // revert any current visualization
                if (player.isOnline()) {
                    plugin.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
                }
            }

            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("deleteclaimsinworld")) {
            // must be executed at the console
            if (player != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ConsoleOnlyCommand);
                return true;
            }

            // requires exactly one parameter, the world name
            if (args.length != 1) return false;

            // try to find the specified world
            World world = Bukkit.getServer().getWorld(args[0]);
            if (world == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.WorldNotFound);
                return true;
            }

            // delete all claims in that world
            plugin.dataStore.deleteClaimsInWorld(world, true);
            GriefPrevention.AddLogEntry("Deleted all claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("deleteuserclaimsinworld")) {
            // must be executed at the console
            if (player != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ConsoleOnlyCommand);
                return true;
            }

            // requires exactly one parameter, the world name
            if (args.length != 1) return false;

            // try to find the specified world
            World world = Bukkit.getServer().getWorld(args[0]);
            if (world == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.WorldNotFound);
                return true;
            }

            // delete all USER claims in that world
            plugin.dataStore.deleteClaimsInWorld(world, false);
            GriefPrevention.AddLogEntry("Deleted all user claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
            return true;
        }

        // claimbook
        else if (cmd.getName().equalsIgnoreCase("claimbook")) {
            // requires one parameter
            if (args.length != 1) return false;

            // try to find the specified player
            Player otherPlayer = plugin.getServer().getPlayer(args[0]);
            if (otherPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }
            else {
                WelcomeTask task = new WelcomeTask(otherPlayer);
                task.run();
                return true;
            }
        }

        // claimslist or claimslist <player>
        else if (cmd.getName().equalsIgnoreCase("claimslist")) {
            // at most one parameter
            if (args.length > 1) return false;

            // player whose claims will be listed
            OfflinePlayer otherPlayer;

            // if another player isn't specified, assume current player
            if (args.length < 1) {
                if (player != null)
                    otherPlayer = player;
                else
                    return false;
            }

            // otherwise if no permission to delve into another player's claims data
            else if (player != null && !player.hasPermission("griefprevention.claimslistother")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsListNoPermission);
                return true;
            }

            // otherwise try to find the specified player
            else {
                otherPlayer = plugin.resolvePlayerByName(args[0]);
                if (otherPlayer == null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return true;
                }
            }

            // load the target player's data
            PlayerData playerData = plugin.dataStore.getPlayerData(otherPlayer.getUniqueId());
            Vector<Claim> claims = playerData.getClaims();
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.StartBlockMath,
                    String.valueOf(playerData.getAccruedClaimBlocks()),
                    String.valueOf((playerData.getBonusClaimBlocks() + plugin.dataStore.getGroupBonusBlocks(otherPlayer.getUniqueId()))),
                    String.valueOf((playerData.getAccruedClaimBlocks() + playerData.getBonusClaimBlocks() + plugin.dataStore.getGroupBonusBlocks(otherPlayer.getUniqueId()))));
            if (claims.size() > 0) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
                for (int i = 0; i < playerData.getClaims().size(); i++) {
                    Claim claim = playerData.getClaims().get(i);
                    /*TextComponent line = Component.text(Utils.colour("&e" + getfriendlyLocationString(claim.getLesserBoundaryCorner()) + plugin.dataStore.getMessage(Messages.ContinueBlockMath, String.valueOf(claim.getArea()))));
                    line = line.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "tppos " +
                            claim.getLesserBoundaryCorner().getBlockX() + " 100 " + claim.getLesserBoundaryCorner().getBlockZ()));*/

                    String claimName = (claim.name != null) ? claim.name + " - " : "";
                    String coords = claim.getLesserBoundaryCorner().getWorld().getName() + ": " + claim.getLesserBoundaryCorner().getBlockX() + " " + claim.getLesserBoundaryCorner().getBlockZ();
                    String area = plugin.df.format(claim.getArea()) + " blocks";

                    TextComponent line = new TextComponent(Utils.colour("&b" + (i+1) + ") " + claimName + "&f" + coords + " &7(" + area + ")"));
                    line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tppos " +
                            claim.getLesserBoundaryCorner().getBlockX() + " 100 " + claim.getLesserBoundaryCorner().getBlockZ()));

                    player.spigot().sendMessage(line);
                    // GriefPrevention.sendMessage(player, TextMode.Instr, );
                }

                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.EndBlockMath, String.valueOf(playerData.getRemainingClaimBlocks()));
            }

            // drop the data we just loaded, if the player isn't online
            if (!otherPlayer.isOnline())
                plugin.dataStore.clearCachedPlayerData(otherPlayer.getUniqueId());

            return true;
        }

        // adminclaimslist
        else if (cmd.getName().equalsIgnoreCase("adminclaimslist")) {
            // find admin claims
            Vector<Claim> claims = new Vector<>();
            for (Claim claim : plugin.dataStore.claims) {
                if (claim.ownerID == null)  // admin claim
                {
                    claims.add(claim);
                }
            }
            if (claims.size() > 0) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
                for (Claim claim : claims) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                }
            }

            return true;
        }

        // unlockItems
        else if (cmd.getName().equalsIgnoreCase("unlockdrops") && player != null) {
            PlayerData playerData;

            if (player.hasPermission("griefprevention.unlockothersdrops") && args.length == 1) {
                Player otherPlayer = Bukkit.getPlayer(args[0]);
                if (otherPlayer == null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return true;
                }

                playerData = plugin.dataStore.getPlayerData(otherPlayer.getUniqueId());
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.DropUnlockOthersConfirmation, otherPlayer.getName());
            }
            else {
                playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.DropUnlockConfirmation);
            }

            playerData.dropsAreUnlocked = true;

            return true;
        }

        // deletealladminclaims
        else if (player != null && cmd.getName().equalsIgnoreCase("deletealladminclaims")) {
            if (!player.hasPermission("griefprevention.deleteclaims")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoDeletePermission);
                return true;
            }

            // delete all admin claims
            plugin.dataStore.deleteClaimsForPlayer(null, true);  // null for owner id indicates an administrative claim

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AllAdminDeleted);
            if (player != null) {
                GriefPrevention.AddLogEntry(player.getName() + " deleted all administrative claims.", CustomLogEntryTypes.AdminActivity);

                // revert any current visualization
                plugin.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
            }

            return true;
        }

        // adjustbonusclaimblocks <player> <amount> or [<permission>] amount
        else if (cmd.getName().equalsIgnoreCase("adjustbonusclaimblocks")) {
            // requires exactly two parameters, the other player or group's name and the adjustment
            if (args.length != 2) return false;

            // parse the adjustment amount
            int adjustment;
            try {
                adjustment = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException numberFormatException) {
                return false;  // causes usage to be displayed
            }

            // if granting blocks to all players with a specific permission
            if (args[0].startsWith("[") && args[0].endsWith("]")) {
                String permissionIdentifier = args[0].substring(1, args[0].length() - 1);
                int newTotal = plugin.dataStore.adjustGroupBonusBlocks(permissionIdentifier, adjustment);

                GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdjustGroupBlocksSuccess, permissionIdentifier, String.valueOf(adjustment), String.valueOf(newTotal));
                if (player != null)
                    GriefPrevention.AddLogEntry(player.getName() + " adjusted " + permissionIdentifier + "'s bonus claim blocks by " + adjustment + ".");

                return true;
            }

            // otherwise, find the specified player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);

            if (targetPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            // give blocks to player
            PlayerData playerData = plugin.dataStore.getPlayerData(targetPlayer.getUniqueId());
            playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + adjustment);
            plugin.dataStore.savePlayerData(targetPlayer.getUniqueId(), playerData);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdjustBlocksSuccess, targetPlayer.getName(), String.valueOf(adjustment), String.valueOf(playerData.getBonusClaimBlocks()));
            if (player != null)
                GriefPrevention.AddLogEntry(player.getName() + " adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".", CustomLogEntryTypes.AdminActivity);

            return true;
        }

        // adjustbonusclaimblocksall <amount>
        else if (cmd.getName().equalsIgnoreCase("adjustbonusclaimblocksall")) {
            // requires exactly one parameter, the amount of adjustment
            if (args.length != 1) return false;

            // parse the adjustment amount
            int adjustment;
            try {
                adjustment = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException numberFormatException) {
                return false;  // causes usage to be displayed
            }

            // for each online player
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>) plugin.getServer().getOnlinePlayers();
            StringBuilder builder = new StringBuilder();
            for (Player onlinePlayer : players) {
                UUID playerID = onlinePlayer.getUniqueId();
                PlayerData playerData = plugin.dataStore.getPlayerData(playerID);
                playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + adjustment);
                plugin.dataStore.savePlayerData(playerID, playerData);
                builder.append(onlinePlayer.getName()).append(' ');
            }

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdjustBlocksAllSuccess, String.valueOf(adjustment));
            GriefPrevention.AddLogEntry("Adjusted all " + players.size() + "players' bonus claim blocks by " + adjustment + ".  " + builder.toString(), CustomLogEntryTypes.AdminActivity);

            return true;
        }

        // setaccruedclaimblocks <player> <amount>
        else if (cmd.getName().equalsIgnoreCase("setaccruedclaimblocks")) {
            // requires exactly two parameters, the other player's name and the new amount
            if (args.length != 2) return false;

            // parse the adjustment amount
            int newAmount;
            try {
                newAmount = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException numberFormatException) {
                return false;  // causes usage to be displayed
            }

            // find the specified player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            // set player's blocks
            PlayerData playerData = plugin.dataStore.getPlayerData(targetPlayer.getUniqueId());
            playerData.setAccruedClaimBlocks(newAmount);
            plugin.dataStore.savePlayerData(targetPlayer.getUniqueId(), playerData);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.SetClaimBlocksSuccess);
            if (player != null)
                GriefPrevention.AddLogEntry(player.getName() + " set " + targetPlayer.getName() + "'s accrued claim blocks to " + newAmount + ".", CustomLogEntryTypes.AdminActivity);

            return true;
        }

        // trapped
        else if (cmd.getName().equalsIgnoreCase("trapped") && player != null) {
            // FEATURE: empower players who get "stuck" in an area where they don't have permission to build to save themselves

            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);

            // if another /trapped is pending, ignore this slash command
            if (playerData.pendingTrapped) {
                return true;
            }

            // if the player isn't in a claim or has permission to build, tell him to man up
            if (claim == null || claim.checkPermission(player, ClaimPermission.Build, null) == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotTrappedHere);
                return true;
            }

            // rescue destination may be set by GPFlags or other plugin, ask to find out
            SaveTrappedPlayerEvent event = new SaveTrappedPlayerEvent(claim);
            Bukkit.getPluginManager().callEvent(event);

            // if the player is in the nether or end, he's screwed (there's no way to programmatically find a safe place for him)
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL && event.getDestination() == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return true;
            }

            // if the player is in an administrative claim and AllowTrappedInAdminClaims is false, he should contact an admin
            if (!GriefPrevention.instance.config_claims_allowTrappedInAdminClaims && claim.isAdminClaim() && event.getDestination() == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return true;
            }
            // send instructions
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.RescuePending);

            // create a task to rescue this player in a little while
            PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation(), event.getDestination());
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, 200L);  // 20L ~ 1 second

            return true;
        }

        // siege
        else if (cmd.getName().equalsIgnoreCase("siege") && player != null) {
            // error message for when siege mode is disabled
            if (!plugin.siegeEnabledForWorld(player.getWorld())) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NonSiegeWorld);
                return true;
            }

            // requires one argument
            if (args.length > 1) {
                return false;
            }

            // can't start a siege when you're already involved in one
            Player attacker = player;
            PlayerData attackerData = plugin.dataStore.getPlayerData(attacker.getUniqueId());
            if (attackerData.siegeData != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.AlreadySieging);
                return true;
            }

            // can't start a siege when you're protected from pvp combat
            if (attackerData.pvpImmune) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CantFightWhileImmune);
                return true;
            }

            // if a player name was specified, use that
            Player defender = null;
            if (args.length >= 1) {
                defender = plugin.getServer().getPlayer(args[0]);
                if (defender == null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                    return true;
                }
            }

            // otherwise use the last player this player was in pvp combat with
            else if (attackerData.lastPvpPlayer.length() > 0) {
                defender = plugin.getServer().getPlayer(attackerData.lastPvpPlayer);
                if (defender == null) {
                    return false;
                }
            }
            else {
                return false;
            }

            //  First off, you cannot siege yourself, that's just
            //  silly:
            if (attacker.getName().equals(defender.getName())) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoSiegeYourself);
                return true;
            }

            // victim must not have the permission which makes him immune to siege
            if (defender.hasPermission("griefprevention.siegeimmune")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeImmune);
                return true;
            }

            // victim must not be under siege already
            PlayerData defenderData = plugin.dataStore.getPlayerData(defender.getUniqueId());
            if (defenderData.siegeData != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegePlayer);
                return true;
            }

            // victim must not be pvp immune
            if (defenderData.pvpImmune) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoSiegeDefenseless);
                return true;
            }

            Claim defenderClaim = plugin.dataStore.getClaimAt(defender.getLocation(), false, null);

            // defender must have some level of permission there to be protected
            if (defenderClaim == null || defenderClaim.checkPermission(defender, ClaimPermission.Access, null) != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotSiegableThere);
                return true;
            }

            // attacker must be close to the claim he wants to siege
            if (!defenderClaim.isNear(attacker.getLocation(), 25)) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeTooFarAway);
                return true;
            }

            // claim can't be under siege already
            if (defenderClaim.siegeData != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegeArea);
                return true;
            }

            // can't siege admin claims
            if (defenderClaim.isAdminClaim()) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoSiegeAdminClaim);
                return true;
            }

            // can't be on cooldown
            if (plugin.dataStore.onCooldown(attacker, defender, defenderClaim)) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeOnCooldown);
                return true;
            }

            // start the siege
            plugin.dataStore.startSiege(attacker, defender, defenderClaim);

            // confirmation message for attacker, warning message for defender
            GriefPrevention.sendMessage(defender, TextMode.Warn, Messages.SiegeAlert, attacker.getName());
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.SiegeConfirmed, defender.getName());

            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("softmute")) {
            // requires one parameter
            if (args.length != 1) return false;

            // find the specified player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            // toggle mute for player
            boolean isMuted = plugin.dataStore.toggleSoftMute(targetPlayer.getUniqueId());
            if (isMuted) {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.SoftMuted, targetPlayer.getName());
                String executorName = "console";
                if (player != null) {
                    executorName = player.getName();
                }

                GriefPrevention.AddLogEntry(executorName + " muted " + targetPlayer.getName() + ".", CustomLogEntryTypes.AdminActivity, true);
            }
            else {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.UnSoftMuted, targetPlayer.getName());
            }

            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("gpreload")) {
            plugin.loadConfig();
            plugin.dataStore.loadMessages();
            plugin.playerEventHandler.resetPattern();
            if (player != null) {
                GriefPrevention.sendMessage(player, TextMode.Success, "Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
            }
            else {
                GriefPrevention.AddLogEntry("Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
            }

            return true;
        }

        // givepet
        else if (cmd.getName().equalsIgnoreCase("givepet") && player != null) {
            // requires one parameter
            if (args.length < 1) return false;

            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

            // special case: cancellation
            if (args[0].equalsIgnoreCase("cancel")) {
                playerData.petGiveawayRecipient = null;
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.PetTransferCancellation);
                return true;
            }

            // find the specified player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null
                    || !targetPlayer.isOnline() && !targetPlayer.hasPlayedBefore()
                    || targetPlayer.getName() == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            // remember the player's ID for later pet transfer
            playerData.petGiveawayRecipient = targetPlayer;

            // send instructions
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ReadyToTransferPet);

            return true;
        }

        // gpblockinfo
        else if (cmd.getName().equalsIgnoreCase("gpblockinfo") && player != null) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            player.sendMessage("In Hand: " + inHand.getType().name());

            Block inWorld = player.getTargetBlockExact(300, FluidCollisionMode.ALWAYS);
            if (inWorld == null) inWorld = player.getEyeLocation().getBlock();
            player.sendMessage("In World: " + inWorld.getType().name());

            return true;
        }

        // ignoreplayer
        /*else if (cmd.getName().equalsIgnoreCase("ignoreplayer") && player != null)
        {
            // requires target player name
            if (args.length < 1) return false;

            // validate target player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            plugin.setIgnoreStatus(player, targetPlayer, IgnoreMode.StandardIgnore);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.IgnoreConfirmation);

            return true;
        }

        // unignoreplayer
        else if (cmd.getName().equalsIgnoreCase("unignoreplayer") && player != null)
        {
            // requires target player name
            if (args.length < 1) return false;

            // validate target player
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Boolean ignoreStatus = playerData.ignoredPlayers.get(targetPlayer.getUniqueId());
            if (ignoreStatus == null || ignoreStatus == true)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotIgnoringPlayer);
                return true;
            }

            plugin.setIgnoreStatus(player, targetPlayer, IgnoreMode.None);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.UnIgnoreConfirmation);

            return true;
        }

        // ignoredplayerlist
        else if (cmd.getName().equalsIgnoreCase("ignoredplayerlist") && player != null)
        {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            StringBuilder builder = new StringBuilder();
            for (Entry<UUID, Boolean> entry : playerData.ignoredPlayers.entrySet())
            {
                if (entry.getValue() != null)
                {
                    // if not an admin ignore, add it to the list
                    if (!entry.getValue())
                    {
                        builder.append(GriefPrevention.lookupPlayerName(entry.getKey()));
                        builder.append(" ");
                    }
                }
            }

            String list = builder.toString().trim();
            if (list.isEmpty())
            {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.NotIgnoringAnyone);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Info, list);
            }

            return true;
        }

        // separateplayers
        else if (cmd.getName().equalsIgnoreCase("separate"))
        {
            // requires two player names
            if (args.length < 2) return false;

            // validate target players
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            OfflinePlayer targetPlayer2 = plugin.resolvePlayerByName(args[1]);
            if (targetPlayer2 == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            plugin.setIgnoreStatus(targetPlayer, targetPlayer2, IgnoreMode.AdminIgnore);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.SeparateConfirmation);

            return true;
        }

        // unseparateplayers
        else if (cmd.getName().equalsIgnoreCase("unseparate"))
        {
            // requires two player names
            if (args.length < 2) return false;

            // validate target players
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            OfflinePlayer targetPlayer2 = plugin.resolvePlayerByName(args[1]);
            if (targetPlayer2 == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            plugin.setIgnoreStatus(targetPlayer, targetPlayer2, IgnoreMode.None);
            plugin.setIgnoreStatus(targetPlayer2, targetPlayer, IgnoreMode.None);

            GriefPrevention.sendMessage(player, TextMode.Success, Messages.UnSeparateConfirmation);

            return true;
        }*/
        return false;
    }

    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return Collections.emptyList();
    }
}
