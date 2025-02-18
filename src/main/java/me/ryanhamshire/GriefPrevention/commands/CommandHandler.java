package me.ryanhamshire.GriefPrevention.commands;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.clip.placeholderapi.PlaceholderAPI;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.MenuGUI;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent;
import me.ryanhamshire.GriefPrevention.logs.ClaimModificationLog;
import me.ryanhamshire.GriefPrevention.logs.MemberModificationLogs;
import me.ryanhamshire.GriefPrevention.logs.PermissionChangeLogs;
import me.ryanhamshire.GriefPrevention.managers.EconomyManager;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.ClaimCorner;
import me.ryanhamshire.GriefPrevention.objects.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

public class CommandHandler {

    GriefPrevention plugin;

    public static List<UUID> abandonClaimConfirmations = new ArrayList<>();
    private List<UUID> abandonAllClaimConfirmations = new ArrayList<>();
    private HashMap<UUID, ClaimSetting> unlockingSettings = new HashMap<>();
    private HashMap<UUID, ClaimPermission> unlockingPermissions = new HashMap<>();

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
            if (!GriefPrevention.plugin.claimsEnabledForWorld(player.getWorld())) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                return true;
            }

            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

            // if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
            if (GriefPrevention.plugin.config_claims_maxClaimsPerPlayer > 0 &&
                    !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                    playerData.getClaims(true).size() >= GriefPrevention.plugin.config_claims_maxClaimsPerPlayer) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                return true;
            }

            // default is chest claim radius, unless -1
            int radius = GriefPrevention.plugin.config_claims_automaticClaimsForNewPlayersRadius;
            if (radius < 0) radius = (int) Math.ceil(Math.sqrt(GriefPrevention.plugin.config_claims_minArea) / 2);

            // if player has any claims, respect claim minimum size setting
            if (playerData.getClaims(true).size() > 0) {
                // if player has exactly one land claim, this requires the claim modification tool to be in hand (or creative mode player)
                if (playerData.getClaims(true).size() == 1 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.plugin.config_claims_modificationTool) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                    return true;
                }

                radius = (int) Math.ceil(Math.sqrt(GriefPrevention.plugin.config_claims_minArea) / 2);
            }

            // allow for specifying the radius
            if (args.length > 0) {
                if (playerData.getClaims(true).size() < 2 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.plugin.config_claims_modificationTool) {
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
                GriefPrevention.plugin.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }

            CreateClaimResult result = plugin.dataStore.createClaim(lc.getWorld(),
                    lc.getBlockX(), gc.getBlockX(),
                    lc.getBlockY() - GriefPrevention.plugin.config_claims_claimsExtendIntoGroundDistance - 1,
                    gc.getWorld().getHighestBlockYAt(gc) - GriefPrevention.plugin.config_claims_claimsExtendIntoGroundDistance - 1,
                    lc.getBlockZ(), gc.getBlockZ(),
                    player.getUniqueId(), null, null, player, new ArrayList<>());
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
                if (GriefPrevention.plugin.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.plugin.claimsEnabledForWorld(player.getWorld())) {
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

            // Must be the owner of the claim
            if (!claim.getOwnerID().equals(player.getUniqueId())) {
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
                if (GriefPrevention.plugin.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.plugin.claimsEnabledForWorld(player.getLocation().getWorld())) {
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
                if (GriefPrevention.plugin.creativeRulesApply(player.getLocation())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                }
                else if (GriefPrevention.plugin.claimsEnabledForWorld(player.getLocation().getWorld())) {
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                }
                return false;
            }

            // requires claim modification tool in hand
            if (player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.plugin.config_claims_modificationTool) {
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
            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MODIFY)) {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.MODIFY.getDenialMessage());
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

            ClaimCorner lc = claim.getLesserBoundaryCorner();
            ClaimCorner gc = claim.getGreaterBoundaryCorner();
            int newx1 = lc.x;
            int newx2 = gc.x;
            int newy1 = lc.y;
            int newy2 = gc.y;
            int newz1 = lc.z;
            int newz2 = gc.z;

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
            return plugin.abandonClaimHandler(player, false, cmd.getLabel());
        }

        // abandontoplevelclaim
        if (cmd.getName().equalsIgnoreCase("abandontoplevelclaim") && player != null) {
            return plugin.abandonClaimHandler(player, true, cmd.getLabel());
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
            if (args.length > 0) return false;

            if (!abandonAllClaimConfirmations.contains(player.getUniqueId())) {
                player.sendMessage(Utils.colour("&cAre you sure you want to delete &lALL&c of your claims? You will lose any unlocked permissions. Type &f/" + cmd.getLabel() + "&c again to confirm!"));

                abandonAllClaimConfirmations.add(player.getUniqueId());
                Player finalPlayer1 = player;

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        abandonAllClaimConfirmations.remove(finalPlayer1.getUniqueId());
                    }
                }.runTaskLater(plugin, 20 * 10);
                return true;
            }

            // count claims
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            int originalClaimCount = playerData.getClaims(true).size();

            // check count
            if (originalClaimCount == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.YouHaveNoClaims);
                return true;
            }

            if (plugin.config_claims_abandonReturnRatio != 1.0D) {
                // adjust claim blocks
                for (Claim claim : playerData.getClaims(true)) {
                    playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - (int) Math.ceil((claim.getArea() * (1 - plugin.config_claims_abandonReturnRatio))));
                }
            }

            // Log it
            ClaimModificationLog.logToFile(player.getName() + " deleted all their claims", true);

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

        // trust <player> (all)
        else if (cmd.getName().equalsIgnoreCase("trust") && player != null) {
            return trustMethod(player, args, ClaimRole.GUEST);
        }

        // membertrust <player> (all)
        else if (cmd.getName().equalsIgnoreCase("membertrust") && player != null) {
            return trustMethod(player, args, ClaimRole.MEMBER);
        }

        // managertrust <player> (all)
        else if (cmd.getName().equalsIgnoreCase("managertrust") && player != null) {
            return trustMethod(player, args, ClaimRole.MANAGER);
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
            for (Claim claim : new Vector<>(fromData.getClaims(true))) {
                plugin.dataStore.changeClaimOwner(claim, toPlayer.getUniqueId());
            }

            // confirm
            sender.sendMessage(Utils.colour("&6Transferred all of " + fromPlayer.getName() + "'s claims to " + toPlayer.getName()));
            GriefPrevention.AddLogEntry("Transferred all of " + fromPlayer.getName() + "'s claims to " + toPlayer.getName());
            return true;
        }

        // claimboot (player)
        else if (cmd.getName().equals("claimboot")) {
            if (args.length != 1) return false;

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is not online"));
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                sender.sendMessage(Utils.colour("&cYou can't boot yourself"));
                return true;
            }

            Claim senderClaim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);
            Claim targetClaim = plugin.dataStore.getClaimAt(target.getLocation(), true, null);

            if (senderClaim == null) {
                sender.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                return true;
            }

            if (!senderClaim.hasClaimPermission(player.getUniqueId(), ClaimPermission.BOOT_PLAYERS)) {
                player.sendMessage(Utils.colour(ClaimPermission.BOOT_PLAYERS.getDenialMessage()));
                return true;
            }

            if (targetClaim == null || !targetClaim.id.equals(senderClaim.id)) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is not standing in the same claim as you"));
                return true;
            }

            if (senderClaim.members.containsKey(target.getUniqueId())) {
                sender.sendMessage(Utils.colour("&cYou can't boot members of the claim to spawn. Consider removing them from your claim first using &o/untrust " + target.getName()));
                return true;
            }

            if (target.hasPermission("group.trialmod")) {
                sender.sendMessage(Utils.colour("&cYou can't boot this member from your claim"));
                return true;
            }

            Utils.sendConsoleCommand("spawn " + target.getName());

            player.sendMessage(Utils.colour("&aYou sent " + target.getName() + " to spawn"));
            target.sendMessage(Utils.colour("&cYou were sent from the claim you were on to spawn. If the claim members do not want you there, please do not go back as this would result in punishment"));
            return true;
        }

        // claimmenu (-sub/-main)
        else if (cmd.getName().equalsIgnoreCase("claimmenu")) {
            if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("-sub") || args[0].equalsIgnoreCase("-main")))) {
                Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

                if (claim == null) {
                    player.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                    return true;
                }

                if (claim.getPlayerRole(player.getUniqueId()) == ClaimRole.PUBLIC && !GriefPrevention.plugin.dataStore.isBypassing(player.getUniqueId())) {
                    player.sendMessage(Utils.colour("&cYou aren't a member of this claim"));
                    return true;
                }

                if (claim.parent != null && args.length == 0) {
                    player.sendMessage(Utils.colour("&aYou are standing in a sub-claim. Use &2/" + commandLabel + " -sub &ato open the sub claim's menu OR &2/" + commandLabel + " -main &ato open the main claim's menu"));
                    return true;
                }

                // They've used -sub but aren't standing in a sub claim
                if (claim.parent == null && args.length == 1 && args[0].equalsIgnoreCase("-sub")) {
                    player.sendMessage(Utils.colour("&cYou aren't standing in a sub-claim"));
                    return true;

                // They've used -sub in a sub claim - Open the sub's menu
                } else if (claim.parent != null && args.length == 1 && args[0].equalsIgnoreCase("-sub")) {
                    player.openInventory(new MenuGUI(claim, false).getInventory());
                    return true;

                // They've used -main in a sub claim - Open the main's menu
                } else if (claim.parent != null && args.length == 1 && args[0].equalsIgnoreCase("-main"))  {
                    player.openInventory(new MenuGUI(claim.parent, false).getInventory());
                    return true;
                }

                player.openInventory(new MenuGUI(claim, false).getInventory());
                return true;
            }

            return false; // Yes it's meant to be return false
        }

        // claimoutlines
        else if (cmd.getName().equalsIgnoreCase("claimoutlines")) {
            if (args.length != 0) return false;

            if (plugin.claimOutlines.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(plugin.claimOutlines.get(player.getUniqueId()));
                plugin.claimOutlines.remove(player.getUniqueId());
                player.sendMessage(Utils.colour("&cYou are no longer view claim outlines"));

            } else {
                plugin.showClaimOutlines(player);
                player.sendMessage(Utils.colour("&aYou are now viewing claim outlines. Use &2/" + commandLabel + "&a again to disable it"));
            }

            return true;
        }

        // trustlist
        else if (cmd.getName().equalsIgnoreCase("trustlist") && player != null) {
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

            // if no claim here, error message
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrustListNoClaim);
                return true;
            }

            // They must be trusted on the claim
            if (claim.getPlayerRole(player.getUniqueId()) == ClaimRole.PUBLIC && !playerData.ignoreClaims) {
                GriefPrevention.sendMessage(player, TextMode.Err, "You must be a member of this claim to see the trust list");
                return true;
            }

            player.sendMessage(Utils.colour("&eMembers for claim ID " + claim.id + ":"));
            for (Map.Entry<UUID, ClaimRole> entry : claim.getClaimMembers(true).entrySet()) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                player.sendMessage(Utils.colour("&e" + p.getName() + ": &f" + entry.getValue().readable()));
            }
            return true;
        }

        // untrust <player/uuid> (all)
        else if (cmd.getName().equalsIgnoreCase("untrust") && player != null) {
            if (args.length != 1 && args.length != 2) return false;
            if (args.length == 2 && !args[1].equalsIgnoreCase("all")) return false;

            boolean all = args.length == 2;
            UUID uuid = null;
            try {
                uuid = UUID.fromString(args[0]);
            } catch (IllegalArgumentException ignored) {}

            OfflinePlayer otherPlayer = (uuid == null) ? plugin.resolvePlayerByName(args[0]) : Bukkit.getOfflinePlayer(uuid);

            if (otherPlayer.getUniqueId().equals(player.getUniqueId()) && !plugin.dataStore.isBypassing(player.getUniqueId())) {
                player.sendMessage(Utils.colour("&cYou can't untrust yourself"));
                return true;
            }

            if (!all) {
                // determine which claim the player is standing in
                Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, false, null);

                if (claim == null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, "You are not standing in a claim");
                    return true;
                }

                if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.TRUST_UNTRUST)) {
                    GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.TRUST_UNTRUST.getDenialMessage());
                    return true;
                }

                ClaimRole senderRole = claim.getPlayerRole(player.getUniqueId());
                ClaimRole targetRole = claim.getPlayerRole(otherPlayer.getUniqueId());

                if (targetRole == ClaimRole.PUBLIC) {
                    GriefPrevention.sendMessage(player, TextMode.Err, "" + otherPlayer.getName() + " is not a member of this claim");
                    return true;
                }

                if (ClaimRole.isRole1HigherThanRole2(targetRole, senderRole) && !plugin.dataStore.isBypassing(player.getUniqueId())) {
                    GriefPrevention.sendMessage(player, TextMode.Err, "You cannot untrust someone who has the same or higher role than you");
                    return true;
                }

                claim.members.remove(otherPlayer.getUniqueId());

                player.sendMessage(Utils.colour("&aYou removed " + otherPlayer.getName() + " from this claim"));
                if (otherPlayer.isOnline()) {
                    otherPlayer.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " removed you from their claim"));
                }

                // Log it
                MemberModificationLogs.logToFile(player.getName() + " untrusted " + otherPlayer.getName() + " on claim " + claim.id, true);

                // save changes
                plugin.dataStore.saveClaim(claim);
                return true;

            } else {
                PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
                Vector<Claim> claims = playerData.getClaims(true);

                for (Claim claim : claims) {
                    ClaimRole senderRole = claim.getPlayerRole(player.getUniqueId());
                    ClaimRole targetRole = claim.getPlayerRole(otherPlayer.getUniqueId());

                    if (targetRole == ClaimRole.PUBLIC) continue;
                    if (ClaimRole.isRole1HigherThanRole2(targetRole, senderRole) && !plugin.dataStore.isBypassing(player.getUniqueId())) continue;

                    claim.members.remove(otherPlayer.getUniqueId());

                    // Log it
                    MemberModificationLogs.logToFile(player.getName() + " untrusted " + otherPlayer.getName() + " on claim " + claim.id, true);

                    // save changes
                    plugin.dataStore.saveClaim(claim);
                }

                player.sendMessage(Utils.colour("&aYou removed " + otherPlayer.getName() + " from &lall&a of your claims."));
                if (otherPlayer.isOnline()) {
                    otherPlayer.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " removed you from all of their claims"));
                }
            }

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
            if (GriefPrevention.plugin.config_economy_claimBlocksPurchaseCost == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlySellBlocks);
                return true;
            }

            Economy economy = economyWrapper.getEconomy();

            // if no parameter, just tell player cost per block and balance
            if (args.length != 1) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockPurchaseCost, String.valueOf(GriefPrevention.plugin.config_economy_claimBlocksPurchaseCost), String.valueOf(economy.getBalance(player)));
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
                double totalCost = blockCount * GriefPrevention.plugin.config_economy_claimBlocksPurchaseCost;
                if (totalCost > balance) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.InsufficientFunds, economy.format(totalCost), economy.format(balance));
                }

                // otherwise carry out transaction
                else {
                    int newBonusClaimBlocks = playerData.getBonusClaimBlocks() + blockCount;

                    // if the player is going to reach max bonus limit, send error message
                    int bonusBlocksLimit = GriefPrevention.plugin.config_economy_claimBlocksMaxBonus;
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
            if (GriefPrevention.plugin.config_economy_claimBlocksSellValue == 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlyPurchaseBlocks);
                return true;
            }

            // load player data
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            int availableBlocks = playerData.getRemainingClaimBlocks();

            // if no amount provided, just tell player value per block sold, and how many he can sell
            if (args.length != 1) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockSaleValue, String.valueOf(GriefPrevention.plugin.config_economy_claimBlocksSellValue), String.valueOf(availableBlocks));
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
                double totalValue = blockCount * GriefPrevention.plugin.config_economy_claimBlocksSellValue;
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
                        if (GriefPrevention.plugin.creativeRulesApply(claim.getLesserBoundaryCorner()) || GriefPrevention.plugin.config_claims_survivalAutoNatureRestoration) {
                            GriefPrevention.plugin.restoreClaim(claim, 0);
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

        // claimtp <id>
        else if (cmd.getName().equalsIgnoreCase("claimtp")) {
            if (args.length != 1) return false;

            long id;
            try {
                id = Long.parseLong(args[0]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is an invalid claim ID"));
                return true;
            }

            Claim claim = plugin.dataStore.getClaim(id);
            if (claim == null) {
                sender.sendMessage(Utils.colour("&cThere is no claim with the ID '" + id + "'"));
                return true;
            }

            if (!claim.ownerID.equals(player.getUniqueId()) && !player.hasPermission("griefprevention.claimslistother")) {
                sender.sendMessage(Utils.colour("&cYou don't have permission to teleport to claims you don't own"));
                return true;
            }

            Location location = new Location(claim.lesserBoundaryCorner.world, claim.lesserBoundaryCorner.x, 100, claim.lesserBoundaryCorner.z);

            player.teleport(location.toHighestLocation().add(0, 3, 0));
            player.sendMessage(Utils.colour("&aYou teleported to claim ID " + id));
            return true;
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
            Vector<Claim> claims = playerData.getClaims(false);
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.StartBlockMath,
                    String.valueOf(playerData.getAccruedClaimBlocks()),
                    String.valueOf((playerData.getBonusClaimBlocks() + plugin.dataStore.getGroupBonusBlocks(otherPlayer.getUniqueId()))),
                    String.valueOf((playerData.getAccruedClaimBlocks() + playerData.getBonusClaimBlocks() + plugin.dataStore.getGroupBonusBlocks(otherPlayer.getUniqueId()))));
            if (claims.size() > 0) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
                for (int i = 0; i < playerData.getClaims(true).size(); i++) {
                    Claim claim = playerData.getClaims(true).get(i);
                    /*TextComponent line = Component.text(Utils.colour("&e" + getfriendlyLocationString(claim.getLesserBoundaryCorner()) + plugin.dataStore.getMessage(Messages.ContinueBlockMath, String.valueOf(claim.getArea()))));
                    line = line.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "tppos " +
                            claim.getLesserBoundaryCorner().getBlockX() + " 100 " + claim.getLesserBoundaryCorner().getBlockZ()));*/

                    String claimName = (claim.name != null) ? claim.name + " - " : "";
                    String coords = claim.getLesserBoundaryCorner().world.getName() + ": " + claim.getLesserBoundaryCorner().x + " " + claim.getLesserBoundaryCorner().z;
                    String area = plugin.df.format(claim.getArea()) + " blocks";

                    TextComponent line = new TextComponent(Utils.colour("&b" + (i+1) + ") " + claimName + "&f" + coords + " &7(" + area + ") &8(id: " + claim.id + ")"));
                    line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claimtp " + claim.id));

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
            for (Claim claim : plugin.dataStore.claimMap.values()) {
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
            boolean canBuild = false;
            if (claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.BREAK_BLOCKS) || claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
                canBuild = true;
            }
            if (claim == null || canBuild) {
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
            if (!GriefPrevention.plugin.config_claims_allowTrappedInAdminClaims && claim.isAdminClaim() && event.getDestination() == null) {
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

        else if (cmd.getName().equalsIgnoreCase("gpreload")) {
            plugin.loadConfig();
            plugin.dataStore.loadMessages();
            plugin.setupFiles();

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

        /*else if (cmd.getName().equalsIgnoreCase("unlockclaimsetting") && player != null) {
            if (args.length != 1) return false;
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

            if (claim == null) {
                player.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                return true;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MANAGE_SETTINGS)) {
                player.sendMessage(Utils.colour(ClaimPermission.MANAGE_SETTINGS.getDenialMessage()));
                return true;
            }

            ClaimSetting setting;
            try {
                setting = ClaimSetting.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is not a valid claim setting"));
                return true;
            }

            if (!claim.isSettingUnlocked(setting)) {
                ClaimSetting unlockingSetting = unlockingSettings.getOrDefault(player.getUniqueId(), null);
                if (unlockingSetting == null || unlockingSetting != setting) {
                    unlockingSettings.put(player.getUniqueId(), setting);
                    sender.sendMessage(Utils.colour("&aTo confirm unlocking the " + setting.name() + " claim setting for " + setting.getUnlockCost() + " iCoins, type this command again"));
                    return true;
                }

                int iCoinsBalance = Integer.parseInt(PlaceholderAPI.setPlaceholders(player, "%icore_insanitypoints_iCoins%"));
                if (iCoinsBalance < setting.getUnlockCost()) {
                    player.sendMessage(Utils.colour("&cYou don't have enough iCoins to unlock this setting. Get iCoins from the &o/shop"));
                    return true;
                }

                claim.unlockClaimSetting(setting);
                player.sendMessage(Utils.colour("&aYou just unlocked the " + setting + " setting for this claim"));
                Utils.sendConsoleCommand("ipoints remove " + player.getName() + " iCoins " + setting.getUnlockCost());

                long id = (claim.parent == null) ? claim.id : claim.parent.id;
                SettingsChangeLogs.logToFile(player.getName() + " unlocked the " + setting.name() + " setting" +
                        " for claim " + id, true);

                unlockingSettings.remove(player.getUniqueId());

            } else {
                player.sendMessage(Utils.colour("&cThis claim already has unlocked this setting"));
            }

            return true;
        }*/

        else if (cmd.getName().equalsIgnoreCase("unlockclaimpermission") && player != null) {
            if (args.length != 1) return false;
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

            if (claim == null) {
                player.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                return true;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MANAGE_PERMISSIONS)) {
                player.sendMessage(Utils.colour(ClaimPermission.MANAGE_PERMISSIONS.getDenialMessage()));
                return true;
            }

            ClaimPermission permission;
            try {
                permission = ClaimPermission.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is not a valid claim permission"));
                return true;
            }

            if (!claim.isPermissionUnlocked(permission)) {
                ClaimPermission unlockingPermission = unlockingPermissions.getOrDefault(player.getUniqueId(), null);
                if (unlockingPermission == null || unlockingPermission != permission) {
                    unlockingPermissions.put(player.getUniqueId(), permission);
                    sender.sendMessage(Utils.colour("&aTo confirm unlocking the " + permission.name() + " claim permission for " + permission.getUnlockCost() + " iCoins, type this command again"));
                    return true;
                }

                int iCoinsBalance = Integer.parseInt(PlaceholderAPI.setPlaceholders(player, "%icore_insanitypoints_iCoins%"));
                if (iCoinsBalance < permission.getUnlockCost()) {
                    player.sendMessage(Utils.colour("&cYou don't have enough iCoins to unlock this setting. Get iCoins from the &o/shop"));
                    return true;
                }

                claim.logSpentICoins(player, permission.getUnlockCost(), false); // Must be done BEFORE claim.unlockClaimPermission so it's saved
                claim.unlockClaimPermission(permission);
                player.sendMessage(Utils.colour("&aYou just unlocked the " + permission + " permission for all of your claims"));
                Utils.sendConsoleCommand("ipoints remove " + player.getName() + " iCoins " + permission.getUnlockCost());

                long id = (claim.parent == null) ? claim.id : claim.parent.id;
                PermissionChangeLogs.logToFile(player.getName() + " unlocked the " + permission.name() + " permission" +
                        " for claim " + id, true);

                unlockingPermissions.remove(player.getUniqueId());

            } else {
                player.sendMessage(Utils.colour("&cThis claim already has unlocked this permission"));
            }

            return true;
        }

        else if (cmd.getName().equalsIgnoreCase("demoteclaimmember") && player != null) {
            if (args.length != 1) return false;
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

            if (claim == null) {
                player.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                return true;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PROMOTE_DEMOTE)) {
                player.sendMessage(Utils.colour(ClaimPermission.PROMOTE_DEMOTE.getDenialMessage()));
                return true;
            }

            OfflinePlayer target = plugin.resolvePlayerByName(args[0]);
            if (target == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            if (!claim.members.containsKey(target.getUniqueId())) {
                player.sendMessage(Utils.colour("&c" + target.getName() + " is not a member of this claim"));
                return true;
            }

            ClaimRole targetRole = claim.getPlayerRole(target.getUniqueId());
            ClaimRole playerRole = claim.getPlayerRole(player.getUniqueId());

            if (target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(Utils.colour("&cYou can't change your own role"));
                return true;
            }

            if (!ClaimRole.isRole1HigherThanRole2(playerRole, targetRole)) {
                player.sendMessage(Utils.colour("&cYou can't change the role of someone with the same role as you or higher"));
                return true;
            }

            if (targetRole == ClaimRole.OWNER) {
                player.sendMessage("&cYou can't demote the owner of the claim");
                return true;
            }

            if (targetRole == ClaimRole.GUEST) {
                player.sendMessage(Utils.colour("&cYou can't demote someone from the lowest role. To untrust them from the claim use &o/untrust " + target.getName()));
                return true;
            }

            ClaimRole nextRoleDown = ClaimRole.getLowerRole(targetRole);

            claim.setClaimRole(target.getUniqueId(), nextRoleDown);
            player.sendMessage(Utils.colour("&aYou demoted " + target.getName() + " to the " + nextRoleDown + " role on this claim"));

            if (target.isOnline()) {
                target.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " demoted you to the " + nextRoleDown + " role on their claim"));
            }

            // Log it
            MemberModificationLogs.logToFile(player.getName() + " demoted " + target.getName() + " to " + nextRoleDown + " on claim " + claim.id, true);
            return true;
        }

        else if (cmd.getName().equalsIgnoreCase("promoteclaimmember") && player != null) {
            if (args.length != 1) return false;
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);

            if (claim == null) {
                player.sendMessage(Utils.colour("&cYou are not standing in a claim"));
                return true;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PROMOTE_DEMOTE)) {
                player.sendMessage(Utils.colour(ClaimPermission.PROMOTE_DEMOTE.getDenialMessage()));
                return true;
            }

            OfflinePlayer target = plugin.resolvePlayerByName(args[0]);
            if (target == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }

            if (!claim.members.containsKey(target.getUniqueId())) {
                player.sendMessage(Utils.colour("&c" + target.getName() + " is not a member of this claim"));
                return true;
            }

            PlayerData data = GriefPrevention.plugin.dataStore.getPlayerData(player.getUniqueId());
            boolean bypassing = (data != null && data.ignoreClaims);

            ClaimRole targetRole = claim.getPlayerRole(target.getUniqueId());
            ClaimRole playerRole = claim.getPlayerRole(player.getUniqueId());

            if (!bypassing && target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(Utils.colour("&cYou can't change your own role"));
                return true;
            }

            if (!bypassing && !ClaimRole.isRole1HigherThanRole2(playerRole, targetRole)) {
                player.sendMessage(Utils.colour("&cYou can't change the role of someone with the same role as you or higher"));
                return true;
            }

            if (targetRole == ClaimRole.OWNER) {
                player.sendMessage("&cYou can't promote the owner of the claim");
                return true;
            }

            if (targetRole == ClaimRole.MANAGER) {
                player.sendMessage(Utils.colour("&cYou can't promote someone higher than manager"));
                return true;
            }

            ClaimRole nextRoleUp = ClaimRole.getHigherRole(targetRole);

            claim.setClaimRole(target.getUniqueId(), nextRoleUp);
            player.sendMessage(Utils.colour("&aYou promoted " + target.getName() + " to the " + nextRoleUp + " role on this claim"));

            if (target.isOnline()) {
                target.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " promoted you to the " + nextRoleUp + " role on their claim"));
            }

            // Log it
            MemberModificationLogs.logToFile(player.getName() + " promoted " + target.getName() + " to " + nextRoleUp + " on claim " + claim.id, true);
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

    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (command.getName().equalsIgnoreCase("unlockclaimsetting")) {
                Arrays.stream(ClaimSetting.values()).toList().forEach(setting -> completions.add(setting.name()));
                return Utils.tabComplete(args[0], completions);
            }

            if (command.getName().equalsIgnoreCase("unlockclaimpermission")) {
                Arrays.stream(ClaimPermission.values()).toList().stream().filter(permission -> permission.getUnlockCost() > 0).forEach(permission -> completions.add(permission.name()));
                return Utils.tabComplete(args[0], completions);
            }

            if (command.getName().equalsIgnoreCase("untrust")) {
                if (commandSender instanceof Player player) {
                    plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));

                    Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);
                    if (claim != null) {
                        for (UUID uuid : claim.getClaimMembers(false).keySet()) {
                            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                            String name = Utils.getOfflinePlayerNameFast(p);

                            if (name != null) completions.add(name);
                        }

                        return Utils.tabComplete(args[0], completions);
                    } else return completions;
                }
            }
        }

        if (args.length == 2) {
            if (command.getName().equalsIgnoreCase("trust") || command.getName().equalsIgnoreCase("untrust")) {
                return Utils.tabComplete(args[1], Arrays.asList("all"));
            }
        }

        return null;
    }

    private boolean trustMethod(Player player, String[] args, ClaimRole defaultTrustRole) {
        if (args.length != 1 && args.length != 2) return false;
        if (args.length == 2 && !args[1].equalsIgnoreCase("all")) return false;

        boolean all = args.length == 2;
        OfflinePlayer otherPlayer = Bukkit.getOfflinePlayer(args[0]);

        if (!all) {
            // determine which claim the player is standing in
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, false, null);

            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, "You are not standing in a claim");
                return true;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.TRUST_UNTRUST)) {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.TRUST_UNTRUST.getDenialMessage());
                return true;
            }

            ClaimRole targetRole = claim.getPlayerRole(otherPlayer.getUniqueId());

            if (targetRole != ClaimRole.PUBLIC) {
                GriefPrevention.sendMessage(player, TextMode.Err, "" + otherPlayer.getName() + " is already part of this claim");
                return true;
            }

            claim.members.put(otherPlayer.getUniqueId(), defaultTrustRole);

            player.sendMessage(Utils.colour("&aYou added " + otherPlayer.getName() + " to this claim as a " + defaultTrustRole.name() + ". Edit their permissions with &o/claimmenu &aor remove them again using &o/untrust " + otherPlayer.getName()));
            if (otherPlayer.isOnline()) {
                otherPlayer.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " added you to their claim"));
            }

            // Log it
            MemberModificationLogs.logToFile(player.getName() + " trusted " + otherPlayer.getName() + " on claim " + claim.id + " as a " + defaultTrustRole.name(), true);

            // save changes
            plugin.dataStore.saveClaim(claim);
            return true;

        } else {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            Vector<Claim> claims = playerData.getClaims(true);

            for (Claim claim : claims) {
                ClaimRole targetRole = claim.getPlayerRole(otherPlayer.getUniqueId());
                if (targetRole != ClaimRole.PUBLIC) continue;

                claim.members.put(otherPlayer.getUniqueId(), defaultTrustRole);

                // Log it
                MemberModificationLogs.logToFile(player.getName() + " trusted " + otherPlayer.getName() + " on claim " + claim.id + " as a " + defaultTrustRole.name(), true);

                // save changes
                plugin.dataStore.saveClaim(claim);
            }

            player.sendMessage(Utils.colour("&aYou added " + otherPlayer.getName() + " to &lall&a of your claims as a " + defaultTrustRole.name() + ". Edit their permissions with &o/claimmenu &aor remove them again using &o/untrust " + otherPlayer.getName() + " all"));
            if (otherPlayer.isOnline()) {
                otherPlayer.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " added you to all of their claims"));
            }
        }

        return true;
    }
}
