/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention.listeners;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import me.ryanhamshire.GriefPrevention.objects.enums.Messages;
import me.ryanhamshire.GriefPrevention.objects.enums.PistonMode;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import me.ryanhamshire.GriefPrevention.utils.legacies.MaterialUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.block.data.type.Lectern;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

//event handlers related to blocks
public class BlockEventHandler implements Listener {
    //convenience reference to singleton datastore
    private final DataStore dataStore;

    private final EnumSet<Material> trashBlocks;

    //constructor
    public BlockEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;

        //create the list of blocks which will not trigger a warning when they're placed outside of land claims
        this.trashBlocks = EnumSet.noneOf(Material.class);
        this.trashBlocks.add(Material.COBBLESTONE);
        this.trashBlocks.add(Material.TORCH);
        this.trashBlocks.add(Material.DIRT);
        this.trashBlocks.add(Material.OAK_SAPLING);
        this.trashBlocks.add(Material.SPRUCE_SAPLING);
        this.trashBlocks.add(Material.BIRCH_SAPLING);
        this.trashBlocks.add(Material.JUNGLE_SAPLING);
        this.trashBlocks.add(Material.ACACIA_SAPLING);
        this.trashBlocks.add(Material.DARK_OAK_SAPLING);
        this.trashBlocks.add(Material.GRAVEL);
        this.trashBlocks.add(Material.SAND);
        this.trashBlocks.add(Material.TNT);
        this.trashBlocks.add(Material.CRAFTING_TABLE);
        this.trashBlocks.add(Material.TUFF);
        this.trashBlocks.add(Material.COBBLED_DEEPSLATE);
    }

    //when a player breaks a block...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent breakEvent) {
        Player player = breakEvent.getPlayer();

        if (!GriefPrevention.instance.claimsEnabledForWorld(breakEvent.getBlock().getWorld())) return;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = this.dataStore.getClaimAt(breakEvent.getBlock().getLocation(), false, playerData.lastClaim);
        if (claim == null) return;

        if (!claim.hasClaimPermission(breakEvent.getPlayer().getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            Utils.sendTimedMessage(player, ClaimPermission.BREAK_BLOCKS.getDenialMessage(), 100); // Timed message so it doesn't spam when using a trencher
            breakEvent.setCancelled(true);
        }

        // If the block is a container and they don't have container access
        if (breakEvent.getBlock().getState() instanceof Container) {
            if (!claim.hasClaimPermission(breakEvent.getPlayer().getUniqueId(), ClaimPermission.CONTAINER_ACCESS)) {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.CONTAINER_ACCESS.getDenialMessage());
                breakEvent.setCancelled(true);
            }
        }

        // Mark the claim as having been built on
        Claim checkClaim = (claim.parent == null) ? claim : claim.parent;
        if (!checkClaim.builtOn && checkClaim.created > 0) {
            checkClaim.builtOn = true;
            dataStore.saveClaim(checkClaim);
        }

        playerData.lastClaim = claim;
    }

    //when a player changes the text of a sign...
    @EventHandler(ignoreCancelled = true)
    public void onSignChanged(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block sign = event.getBlock();

        if (player == null || sign == null) return;

        Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
        if (claim == null) return;

        if (!claim.hasClaimPermission(event.getPlayer().getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
            GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.PLACE_BLOCKS.getDenialMessage());
            event.setCancelled(true);
        }
    }

    //when a player places multiple blocks...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlocksPlace(BlockMultiPlaceEvent placeEvent) {
        Player player = placeEvent.getPlayer();

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //make sure the player is allowed to build at the location
        for (BlockState block : placeEvent.getReplacedBlockStates()) {
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim);
            if (claim == null) continue;

            if (!claim.hasClaimPermission(placeEvent.getPlayer().getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.PLACE_BLOCKS.getDenialMessage());
                placeEvent.setCancelled(true);
            }

            playerData.lastClaim = claim;
        }
    }

    private boolean doesAllowFireProximityInWorld(World world) {
        if (GriefPrevention.instance.pvpRulesApply(world)) {
            return GriefPrevention.instance.config_pvp_allowFireNearPlayers;
        }
        else {
            return GriefPrevention.instance.config_pvp_allowFireNearPlayers_NonPvp;
        }
    }

    //when a player places a block...
    @SuppressWarnings("null")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent placeEvent) {
        Player player = placeEvent.getPlayer();
        Block block = placeEvent.getBlock();

        if (!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = this.dataStore.getClaimAt(placeEvent.getBlock().getLocation(), false, playerData.lastClaim);

        if (claim != null) {
            // Placing a book on a lectern counts as placing a lectern for some reason
            if (placeEvent.getBlock().getType() == Material.LECTERN) {
                Lectern lectern = (Lectern) placeEvent.getBlock().getBlockData();
                if (lectern.hasBook()) return;
            }

            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.PLACE_BLOCKS.getDenialMessage());
                placeEvent.setCancelled(true);
                return;
            }

            // If the block is a container and they don't have container access (we don't want them to be able to place it and then not break or access it)
            if (placeEvent.getBlock().getState() instanceof Container) {
                if (!claim.hasClaimPermission(placeEvent.getPlayer().getUniqueId(), ClaimPermission.CONTAINER_ACCESS)) {
                    GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.CONTAINER_ACCESS.getDenialMessage());
                    placeEvent.setCancelled(true);
                    return;
                }
            }

            // Mark the claim as having been built on
            Claim checkClaim = (claim.parent == null) ? claim : claim.parent;
            if (!checkClaim.builtOn && checkClaim.created > 0) {
                checkClaim.builtOn = true;
                dataStore.saveClaim(checkClaim);
            }

            playerData.lastClaim = claim;
        }

        //If block is a chest, don't allow a DoubleChest to form across a claim boundary
        denyConnectingDoubleChestsAcrossClaimBoundary(claim, block, player);

        //FEATURE: automatically create a claim when a player who has no claims places a chest

        //otherwise if there's no claim, the player is placing a chest, and new player automatic claims are enabled
        if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1 && player.hasPermission("griefprevention.createclaims") && block.getType() == Material.CHEST) {
            //if the chest is too deep underground, don't create the claim and explain why
            if (GriefPrevention.instance.config_claims_preventTheft && block.getY() < GriefPrevention.instance.config_claims_maxDepth) {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.TooDeepToClaim);
                return;
            }

            int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;

            //if the player doesn't have any claims yet, automatically create a claim centered at the chest
            if (playerData.getClaims(true).size() == 0 && player.getGameMode() == GameMode.SURVIVAL) {
                //radius == 0 means protect ONLY the chest
                if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius == 0) {
                    this.dataStore.createClaim(block.getWorld(), block.getX(), block.getX(), block.getY(), block.getY(), block.getZ(), block.getZ(), player.getUniqueId(), null, null, player, new ArrayList<>());
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.ChestClaimConfirmation);
                }

                //otherwise, create a claim in the area around the chest
                else {
                    //if failure due to insufficient claim blocks available
                    if (playerData.getRemainingClaimBlocks() < Math.pow(1 + 2 * GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadiusMin, 2)) {
                        GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoEnoughBlocksForChestClaim);
                        return;
                    }

                    //as long as the automatic claim overlaps another existing claim, shrink it
                    //note that since the player had permission to place the chest, at the very least, the automatic claim will include the chest
                    CreateClaimResult result = null;
                    while (radius >= GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadiusMin) {
                        int area = (radius * 2 + 1) * (radius * 2 + 1);
                        if (playerData.getRemainingClaimBlocks() >= area) {
                            result = this.dataStore.createClaim(
                                    block.getWorld(),
                                    block.getX() - radius, block.getX() + radius,
                                    block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, block.getY(),
                                    block.getZ() - radius, block.getZ() + radius,
                                    player.getUniqueId(),
                                    null, null,
                                    player,
                                    new ArrayList<>());

                            if (result.succeeded) break;
                        }

                        radius--;
                    }

                    if (result != null && result.claim != null) {
                        if (result.succeeded) {
                            //notify and explain to player
                            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AutomaticClaimNotification);

                            //show the player the protected area
                            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, block);
                        }
                        else {
                            //notify and explain to player
                            GriefPrevention.sendMessage(player, TextMode.Err, Messages.AutomaticClaimOtherClaimTooClose);

                            //show the player the protected area
                            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, block);
                        }
                    }
                }

                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
            }

            //check to see if this chest is in a claim, and warn when it isn't
            if (GriefPrevention.instance.config_claims_preventTheft && this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim) == null) {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnprotectedChestWarning);
            }
        }

        //FEATURE: limit wilderness tree planting to grass, or dirt with more blocks beneath it
        else if (Tag.SAPLINGS.isTagged(block.getType()) && GriefPrevention.instance.config_blockSkyTrees && GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
            Block earthBlock = placeEvent.getBlockAgainst();
            if (earthBlock.getType() != MaterialUtils.of("SHORT_GRASS")) {
                if (earthBlock.getRelative(BlockFace.DOWN).getType() == Material.AIR ||
                        earthBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType() == Material.AIR) {
                    placeEvent.setCancelled(true);
                }
            }
        }

        //FEATURE: warn players when they're placing non-trash blocks outside of their claimed areas
        else if (!this.trashBlocks.contains(block.getType()) && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld())) {
            if (!playerData.warnedAboutBuildingOutsideClaims && !player.hasPermission("griefprevention.adminclaims")
                    && player.hasPermission("griefprevention.createclaims") && ((playerData.lastClaim == null
                    && playerData.getClaims(true).size() == 0) || (playerData.lastClaim != null
                    && playerData.lastClaim.isNear(player.getLocation(), 15)))) {
                Long now = null;
                if (playerData.buildWarningTimestamp == null || (now = System.currentTimeMillis()) - playerData.buildWarningTimestamp > 600000)  //10 minute cooldown
                {
                    GriefPrevention.sendMessage(player, TextMode.Warn, Messages.BuildingOutsideClaims);
                    playerData.warnedAboutBuildingOutsideClaims = true;

                    if (now == null) now = System.currentTimeMillis();
                    playerData.buildWarningTimestamp = now;

                    if (playerData.getClaims(true).size() < 2) {
                        GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                    }

                    if (playerData.lastClaim != null) {
                        BoundaryVisualization.visualizeClaim(player, playerData.lastClaim, VisualizationType.CLAIM, block);
                    }
                }
            }
        }

        //warn players about disabled pistons outside of land claims
        if (GriefPrevention.instance.config_pistonMovement == PistonMode.CLAIMS_ONLY &&
                (block.getType() == Material.PISTON || block.getType() == Material.STICKY_PISTON) &&
                claim == null) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoPistonsOutsideClaims);
        }

        //limit active blocks in creative mode worlds
        if (!player.hasPermission("griefprevention.adminclaims") && GriefPrevention.instance.creativeRulesApply(block.getLocation()) && isActiveBlock(block)) {
            String noPlaceReason = claim.allowMoreActiveBlocks();
            if (noPlaceReason != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, noPlaceReason);
                placeEvent.setCancelled(true);
                return;
            }
        }
    }

    public static boolean isActiveBlock(Block block) {
        return isActiveBlock(block.getType());
    }

    public static boolean isActiveBlock(BlockState state) {
        return isActiveBlock(state.getType());
    }

    public static boolean isActiveBlock(Material type) {
        if (type == Material.HOPPER || type == Material.BEACON || type == Material.SPAWNER) return true;
        return false;
    }

    private static final BlockFace[] HORIZONTAL_DIRECTIONS = new BlockFace[]{
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    private void denyConnectingDoubleChestsAcrossClaimBoundary(Claim claim, Block block, Player player) {
        UUID claimOwner = null;
        if (claim != null)
            claimOwner = claim.getOwnerID();

        // Check for double chests placed just outside the claim boundary
        if (block.getBlockData() instanceof Chest) {
            for (BlockFace face : HORIZONTAL_DIRECTIONS) {
                Block relative = block.getRelative(face);
                if (!(relative.getBlockData() instanceof Chest)) continue;

                Claim relativeClaim = this.dataStore.getClaimAt(relative.getLocation(), true, claim);
                UUID relativeClaimOwner = relativeClaim == null ? null : relativeClaim.getOwnerID();

                // Chests outside claims should connect (both null)
                // and chests inside the same claim should connect (equal)
                if (Objects.equals(claimOwner, relativeClaimOwner)) break;

                // Change both chests to singular chests
                Chest chest = (Chest) block.getBlockData();
                chest.setType(Chest.Type.SINGLE);
                block.setBlockData(chest);

                Chest relativeChest = (Chest) relative.getBlockData();
                relativeChest.setType(Chest.Type.SINGLE);
                relative.setBlockData(relativeChest);

                // Resend relative chest block to prevent visual bug
                player.sendBlockChange(relative.getLocation(), relativeChest);
                break;
            }
        }
    }

    // Prevent pistons pushing blocks into or out of claims.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonExtend(BlockPistonExtendEvent e) {
        // If the blocks are empty, they might be pushing an armor stand so we need to check for that
        if (e.getBlocks().isEmpty()) {
            Location head = e.getBlock().getLocation().add(e.getDirection().getDirection());

            for (Entity entity : head.getNearbyEntities(0.5, 0.5, 0.5)) {
                if (!(entity instanceof ArmorStand)) continue;

                Location toLocation = head.clone().add(e.getDirection().getDirection());

                Claim fromClaim = dataStore.getClaimAt(head, true, null);
                Claim toClaim = dataStore.getClaimAt(toLocation, true, null);

                if (fromClaim == null && toClaim == null) continue;
                if (fromClaim == null || toClaim == null || !fromClaim.id.equals(toClaim.id)) {
                    e.setCancelled(true);
                    return;
                }
            }

            return;
        }

        // The blocks are not empty so it's pushing something. We still need to do armor stand checks for the block AFTER the final block
        Location fromLocation = e.getBlock().getLocation(); // The location of the piston
        Location toLocation = e.getBlocks().get(e.getBlocks().size()-1).getLocation().add(e.getDirection().getDirection()); // The location the final block will end up

        // We need to see if the block after the final block is an armor stand and then modify the toLocation
        toLocation.getNearbyEntities(0.5, 0.5, 0.5).forEach(entity -> {
            if (entity instanceof ArmorStand) toLocation.add(e.getDirection().getDirection());
        });

        Claim fromClaim = dataStore.getClaimAt(fromLocation, true, null);
        Claim toClaim = dataStore.getClaimAt(toLocation, true, null);

        if (fromClaim == null && toClaim == null) return;
        if (fromClaim == null || toClaim == null || !fromClaim.id.equals(toClaim.id)) {
            e.setCancelled(true);
        }
    }

    // Prevent pistons pulling blocks into or out of claims.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().isEmpty()) return;

        Location fromLocation = e.getBlock().getLocation(); // The location of the piston
        Location toLocation = e.getBlocks().get(e.getBlocks().size()-1).getLocation(); // The location the furthest block is being pulled from

        Claim fromClaim = dataStore.getClaimAt(fromLocation, true, null);
        Claim toClaim = dataStore.getClaimAt(toLocation, true, null);

        if (fromClaim == null && toClaim == null) return;

        if (fromClaim == null || toClaim == null || !fromClaim.id.equals(toClaim.id)) {
            e.setCancelled(true);
        }
    }

    // Handle piston push and pulls.
    /*private void onPistonEvent(BlockPistonEvent event, List<Block> blocks, boolean isRetract) {
        PistonMode pistonMode = GriefPrevention.plugin.config_pistonMovement;
        // Return if piston movements are ignored.
        if (pistonMode == PistonMode.IGNORED) return;

        // Don't check in worlds where claims are not enabled.
        if (!GriefPrevention.plugin.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        BlockFace direction = event.getDirection();
        Block pistonBlock = event.getBlock();
        Claim pistonClaim = this.dataStore.getClaimAt(pistonBlock.getLocation(), false,
                pistonMode != PistonMode.CLAIMS_ONLY, null);

        // A claim is required, but the piston is not inside a claim.
        if (pistonClaim == null && pistonMode == PistonMode.CLAIMS_ONLY) {
            event.setCancelled(true);
            return;
        }

        // If no blocks are moving, quickly check if another claim's boundaries are violated.
        if (blocks.isEmpty()) {
            // No block and retraction is always safe.
            if (isRetract) return;

            Block invadedBlock = pistonBlock.getRelative(direction);
            Claim invadedClaim = this.dataStore.getClaimAt(invadedBlock.getLocation(), false,
                    pistonMode != PistonMode.CLAIMS_ONLY, pistonClaim);
            if (invadedClaim != null && (pistonClaim == null || !Objects.equals(pistonClaim.getOwnerID(), invadedClaim.getOwnerID()))) {
                event.setCancelled(true);
            }

            return;
        }

        // Create bounding box for moved blocks.
        BoundingBox movedBlocks = BoundingBox.ofBlocks(blocks);
        // Expand to include invaded zone.
        movedBlocks.resize(direction, 1);

        if (pistonClaim != null) {
            // If blocks are all inside the same claim as the piston, allow.
            if (new BoundingBox(pistonClaim).contains(movedBlocks)) return;

            if (pistonMode == PistonMode.CLAIMS_ONLY) {
                event.setCancelled(true);
                return;
            }
        }

        // Check if blocks are in line vertically.
        if (movedBlocks.getLength() == 1 && movedBlocks.getWidth() == 1) {
            // Pulling up is always safe. The claim may not contain the area pulled from, but claims cannot stack.
            if (isRetract && direction == BlockFace.UP) return;

            // Pushing down is always safe. The claim may not contain the area pushed into, but claims cannot stack.
            if (!isRetract && direction == BlockFace.DOWN) return;
        }

        // Assemble list of potentially intersecting claims from chunks interacted with.
        ArrayList<Claim> intersectable = new ArrayList<>();
        int chunkXMax = movedBlocks.getMaxX() >> 4;
        int chunkZMax = movedBlocks.getMaxZ() >> 4;

        for (int chunkX = movedBlocks.getMinX() >> 4; chunkX <= chunkXMax; ++chunkX) {
            for (int chunkZ = movedBlocks.getMinZ() >> 4; chunkZ <= chunkZMax; ++chunkZ) {
                ArrayList<Claim> chunkClaims = dataStore.chunksToClaimsMap.get(DataStore.getChunkHash(chunkX, chunkZ));
                if (chunkClaims == null) continue;

                for (Claim claim : chunkClaims) {
                    // Ensure claim is not piston claim and is in same world.
                    if (pistonClaim != claim && pistonBlock.getWorld().equals(claim.getLesserBoundaryCorner().world))
                        intersectable.add(claim);
                }
            }
        }

        BiPredicate<Claim, BoundingBox> intersectionHandler;
        final Claim finalPistonClaim = pistonClaim;

        // Fast mode: Bounding box intersection always causes a conflict, even if blocks do not conflict.
        if (pistonMode == PistonMode.EVERYWHERE_SIMPLE) {
            intersectionHandler = (claim, claimBoundingBox) ->
            {
                // If owners are different, cancel.
                if (finalPistonClaim == null || !Objects.equals(finalPistonClaim.getOwnerID(), claim.getOwnerID())) {
                    event.setCancelled(true);
                    return true;
                }

                // Otherwise, proceed to next claim.
                return false;
            };
        }
        // Precise mode: Bounding box intersection may not yield a conflict. Individual blocks must be considered.
        else {
            // Set up list of affected blocks.
            HashSet<Block> checkBlocks = new HashSet<>(blocks);

            // Add all blocks that will be occupied after the shift.
            for (Block block : blocks)
                if (block.getPistonMoveReaction() != PistonMoveReaction.BREAK)
                    checkBlocks.add(block.getRelative(direction));

            intersectionHandler = (claim, claimBoundingBox) ->
            {
                // Ensure that the claim contains an affected block.
                if (checkBlocks.stream().noneMatch(claimBoundingBox::contains)) return false;

                // If pushing this block will change ownership, cancel the event and take away the piston (for performance reasons).
                if (finalPistonClaim == null || !Objects.equals(finalPistonClaim.getOwnerID(), claim.getOwnerID())) {
                    event.setCancelled(true);
                    if (GriefPrevention.plugin.config_pistonExplosionSound) {
                        pistonBlock.getWorld().createExplosion(pistonBlock.getLocation(), 0);
                    }
                    pistonBlock.getWorld().dropItem(pistonBlock.getLocation(), new ItemStack(event.isSticky() ? Material.STICKY_PISTON : Material.PISTON));
                    pistonBlock.setType(Material.AIR);
                    return true;
                }

                // Otherwise, proceed to next claim.
                return false;
            };
        }

        for (Claim claim : intersectable) {
            BoundingBox claimBoundingBox = new BoundingBox(claim);

            // Ensure claim intersects with block bounding box.
            if (!claimBoundingBox.intersects(movedBlocks)) continue;

            // Do additional mode-based handling.
            if (intersectionHandler.test(claim, claimBoundingBox)) return;
        }
    }*/

    //blocks are ignited ONLY by flint and steel (not by being near lava, open flames, etc), unless configured otherwise
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockIgnite(BlockIgniteEvent igniteEvent) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(igniteEvent.getBlock().getWorld())) return;

        if (igniteEvent.getCause() == IgniteCause.LIGHTNING && GriefPrevention.instance.dataStore.getClaimAt(igniteEvent.getIgnitingEntity().getLocation(), false, null) != null) {
            igniteEvent.setCancelled(true); //BlockIgniteEvent is called before LightningStrikeEvent. See #532. However, see #1125 for further discussion on detecting trident-caused lightning.
        }

        // If a fire is started by a fireball from a dispenser, allow it if the dispenser is in the same claim.
        if (igniteEvent.getCause() == IgniteCause.FIREBALL && igniteEvent.getIgnitingEntity() instanceof Fireball) {
            ProjectileSource shooter = ((Fireball) igniteEvent.getIgnitingEntity()).getShooter();
            if (shooter instanceof BlockProjectileSource) {
                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(igniteEvent.getBlock().getLocation(), false, null);
                if (claim != null && GriefPrevention.instance.dataStore.getClaimAt(((BlockProjectileSource) shooter).getBlock().getLocation(), false, claim) == claim) {
                    return;
                }
            }
        }

        // Arrow ignition.
        if (igniteEvent.getCause() == IgniteCause.ARROW && igniteEvent.getIgnitingEntity() != null) {
            // Arrows shot by players may return the shooter, not the arrow.
            if (igniteEvent.getIgnitingEntity() instanceof Player player) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(igniteEvent.getBlock(), player);
                onBlockBreak(breakEvent);
                if (breakEvent.isCancelled()) {
                    igniteEvent.setCancelled(true);
                }
                return;
            }
            // Flammable lightable blocks do not fire EntityChangeBlockEvent when igniting.
            BlockData blockData = igniteEvent.getBlock().getBlockData();
            if (blockData instanceof Lightable lightable) {
                // Set lit for resulting data in event. Currently unused, but may be in the future.
                lightable.setLit(true);

                // Call event.
                EntityChangeBlockEvent changeBlockEvent = new EntityChangeBlockEvent(igniteEvent.getIgnitingEntity(), igniteEvent.getBlock(), blockData);
                GriefPrevention.instance.entityEventHandler.onEntityChangeBLock(changeBlockEvent);

                // Respect event result.
                if (changeBlockEvent.isCancelled()) {
                    igniteEvent.setCancelled(true);
                }
            }
            return;
        }

        if (!GriefPrevention.instance.config_fireSpreads && igniteEvent.getCause() != IgniteCause.FLINT_AND_STEEL && igniteEvent.getCause() != IgniteCause.LIGHTNING) {
            igniteEvent.setCancelled(true);
        }
    }

    //fire doesn't spread unless configured to, but other blocks still do (mushrooms and vines, for example)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockSpread(BlockSpreadEvent spreadEvent) {
        if (spreadEvent.getSource().getType() != Material.FIRE) return;

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(spreadEvent.getBlock().getWorld())) return;

        if (!GriefPrevention.instance.config_fireSpreads) {
            spreadEvent.setCancelled(true);

            Block underBlock = spreadEvent.getSource().getRelative(BlockFace.DOWN);
            if (underBlock.getType() != Material.NETHERRACK) {
                spreadEvent.getSource().setType(Material.AIR);
            }

            return;
        }

        //never spread into a claimed area, regardless of settings
        if (this.dataStore.getClaimAt(spreadEvent.getBlock().getLocation(), false, null) != null) {
            if (GriefPrevention.instance.config_claims_firespreads) return;
            spreadEvent.setCancelled(true);

            //if the source of the spread is not fire on netherrack, put out that source fire to save cpu cycles
            Block source = spreadEvent.getSource();
            if (source.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK) {
                source.setType(Material.AIR);
            }
        }
    }

    //blocks are not destroyed by fire, unless configured to do so
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBurn(BlockBurnEvent burnEvent) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(burnEvent.getBlock().getWorld())) return;

        if (!GriefPrevention.instance.config_fireDestroys) {
            burnEvent.setCancelled(true);
            Block block = burnEvent.getBlock();
            Block[] adjacentBlocks = new Block[]
                    {
                            block.getRelative(BlockFace.UP),
                            block.getRelative(BlockFace.DOWN),
                            block.getRelative(BlockFace.NORTH),
                            block.getRelative(BlockFace.SOUTH),
                            block.getRelative(BlockFace.EAST),
                            block.getRelative(BlockFace.WEST)
                    };

            //pro-actively put out any fires adjacent the burning block, to reduce future processing here
            for (Block adjacentBlock : adjacentBlocks) {
                if (adjacentBlock.getType() == Material.FIRE && adjacentBlock.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK) {
                    adjacentBlock.setType(Material.AIR);
                }
            }

            Block aboveBlock = block.getRelative(BlockFace.UP);
            if (aboveBlock.getType() == Material.FIRE) {
                aboveBlock.setType(Material.AIR);
            }
            return;
        }

        //never burn claimed blocks, regardless of settings
        if (this.dataStore.getClaimAt(burnEvent.getBlock().getLocation(), false, null) != null) {
            if (GriefPrevention.instance.config_claims_firedamages) return;
            burnEvent.setCancelled(true);
        }
    }


    //ensures fluids don't flow into land claims from outside
    private Claim lastSpreadFromClaim = null;
    private Claim lastSpreadToClaim = null;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockFromTo(BlockFromToEvent spreadEvent) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(spreadEvent.getBlock().getWorld())) return;

        //where from and where to?
        Location fromLocation = spreadEvent.getBlock().getLocation();
        Location toLocation = spreadEvent.getToBlock().getLocation();
        boolean isInCreativeRulesWorld = GriefPrevention.instance.creativeRulesApply(toLocation);
        Claim fromClaim = this.dataStore.getClaimAt(fromLocation, false, lastSpreadFromClaim);
        Claim toClaim = this.dataStore.getClaimAt(toLocation, false, lastSpreadToClaim);

        //due to the nature of what causes this event (fluid flow/spread),
        //we'll probably run similar checks for the same pair of claims again,
        //so we cache them to use in claim lookup later
        this.lastSpreadFromClaim = fromClaim;
        this.lastSpreadToClaim = toClaim;

        if (!isFluidFlowAllowed(fromClaim, toClaim, isInCreativeRulesWorld)) {
            spreadEvent.setCancelled(true);
        }

        if (fromClaim != null && !fromClaim.isSettingEnabled(ClaimSetting.FLUID_FLOW)) {
            spreadEvent.setCancelled(true);
        }
    }

    /**
     * Determines whether fluid flow is allowed between two claims.
     *
     * @param from The claim at the source location of the fluid flow, or null if it's wilderness.
     * @param to The claim at the destination location of the fluid flow, or null if it's wilderness.
     * @param creativeRulesApply Whether creative rules apply to the world where claims are located.
     * @return `true` if fluid flow is allowed, `false` otherwise.
     */
    private boolean isFluidFlowAllowed(Claim from, Claim to, boolean creativeRulesApply) {
        // Special case: if in a world with creative rules,
        // don't allow fluids to flow into wilderness.
        if (creativeRulesApply && to == null) return false;

        // The fluid flow should be allowed or denied based on the specific combination
        // of source and destination claim types. The following matrix outlines these
        // combinations and indicates whether fluid flow should be permitted:
        //
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | From \ To    | Wild | Claim A1 | Sub A1_1 | Sub A1_2 | Sub A1_3 (R) | Claim A2 | Claim B |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Wild         | Yes  | -        | -        | -        | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim A1     | Yes  | Yes      | Yes      | Yes      | -            | Yes      | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_1     | Yes  | -        | Yes      | -        | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_2     | Yes  | -        | -        | Yes      | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_3 (R) | Yes  | -        | -        | -        | Yes          | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim A2     | Yes  | Yes      | -        | -        | -            | Yes      | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim B      | Yes  | -        | -        | -        | -            | -        | Yes     |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //
        //   Legend:
        //     Wild = wilderness
        //     Claim A* = claim owned by player A
        //     Sub A*_* = subdivision of Claim A*
        //     (R) = Restricted subdivision
        //     Claim B = claim owned by player B
        //     Yes = fluid flow allowed
        //     - = fluid flow not allowed

        boolean fromWilderness = from == null;
        boolean toWilderness = to == null;
        boolean sameClaim = from != null && to != null && Objects.equals(from.getID(), to.getID());
        boolean sameOwner = from != null && to != null && Objects.equals(from.getOwnerID(), to.getOwnerID());
        boolean isToSubdivision = to != null && to.parent != null;
        boolean isFromSubdivision = from != null && from.parent != null;

        if (toWilderness) return true;
        if (fromWilderness) return false;
        if (sameClaim) return true;
        if (isFromSubdivision) return false;
        return sameOwner;
    }

    //Stop projectiles from destroying blocks that don't fire a proper event
    @EventHandler(ignoreCancelled = true)
    private void chorusFlower(ProjectileHitEvent event) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        Block block = event.getHitBlock();

        // Ensure projectile affects block.
        if (block == null || block.getType() != Material.CHORUS_FLOWER) return;

        Claim claim = dataStore.getClaimAt(block.getLocation(), false, null);
        if (claim == null) return;

        Player shooter = null;
        Projectile projectile = event.getEntity();

        if (projectile.getShooter() instanceof Player)
            shooter = (Player) projectile.getShooter();

        if (shooter == null) {
            event.setCancelled(true);
            return;
        }

        if (!claim.hasClaimPermission(shooter.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            GriefPrevention.sendMessage(shooter, TextMode.Err, ClaimPermission.BREAK_BLOCKS.getDenialMessage());
        }
    }

    //ensures dispensers can't be used to dispense a block(like water or lava) or item across a claim boundary
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent dispenseEvent) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(dispenseEvent.getBlock().getWorld())) return;

        //from where?
        Block fromBlock = dispenseEvent.getBlock();
        BlockData fromData = fromBlock.getBlockData();
        if (!(fromData instanceof Dispenser)) return;
        Dispenser dispenser = (Dispenser) fromData;

        //to where?
        Block toBlock = fromBlock.getRelative(dispenser.getFacing());
        Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, null);
        Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, fromClaim);

        //into wilderness is NOT OK in creative mode worlds
        Material materialDispensed = dispenseEvent.getItem().getType();
        if ((materialDispensed == Material.WATER_BUCKET || materialDispensed == Material.LAVA_BUCKET) && GriefPrevention.instance.creativeRulesApply(dispenseEvent.getBlock().getLocation()) && toClaim == null) {
            dispenseEvent.setCancelled(true);
            return;
        }

        //wilderness to wilderness is OK
        if (fromClaim == null && toClaim == null) return;

        //within claim is OK
        if (fromClaim == toClaim) return;

        //everything else is NOT OK
        dispenseEvent.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent growEvent) {
        //only take these potentially expensive steps if configured to do so
        if (!GriefPrevention.instance.config_limitTreeGrowth) return;

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(growEvent.getWorld())) return;

        Location rootLocation = growEvent.getLocation();
        Claim rootClaim = this.dataStore.getClaimAt(rootLocation, false, null);
        String rootOwnerName = null;

        //who owns the spreading block, if anyone?
        if (rootClaim != null) {
            //tree growth in subdivisions is dependent on who owns the top level claim
            if (rootClaim.parent != null) rootClaim = rootClaim.parent;

            //if an administrative claim, just let the tree grow where it wants
            if (rootClaim.isAdminClaim()) return;

            //otherwise, note the owner of the claim
            rootOwnerName = rootClaim.getOwnerName();
        }

        //for each block growing
        for (int i = 0; i < growEvent.getBlocks().size(); i++) {
            BlockState block = growEvent.getBlocks().get(i);
            Claim blockClaim = this.dataStore.getClaimAt(block.getLocation(), false, rootClaim);

            //if it's growing into a claim
            if (blockClaim != null) {
                //if there's no owner for the new tree, or the owner for the new tree is different from the owner of the claim
                if (rootOwnerName == null || !rootOwnerName.equals(blockClaim.getOwnerName())) {
                    growEvent.getBlocks().remove(i--);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        // Prevent hoppers from taking items dropped by players upon death.
        if (event.getInventory().getType() != InventoryType.HOPPER) {
            return;
        }

        List<MetadataValue> meta = event.getItem().getMetadata("GP_ITEMOWNER");
        // We only care about an item if it has been flagged as belonging to a player.
        if (meta.isEmpty()) {
            return;
        }

        UUID itemOwnerId = (UUID) meta.get(0).value();
        // Determine if the owner has unlocked their dropped items.
        // This first requires that the player is logged in.
        if (Bukkit.getServer().getPlayer(itemOwnerId) != null) {
            PlayerData itemOwner = dataStore.getPlayerData(itemOwnerId);
            // If locked, don't allow pickup
            if (!itemOwner.dropsAreUnlocked) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemFrameBrokenByBoat(final HangingBreakEvent event) {
        // Checks if the event is caused by physics - 90% of cases caused by a boat (other 10% would be block,
        // however since it's in a claim, unless you use a TNT block we don't need to worry about it).
        if (event.getCause() != HangingBreakEvent.RemoveCause.PHYSICS) {
            return;
        }

        // Cancels the event if in a claim, as we can not efficiently retrieve the person/entity who broke the Item Frame/Hangable Item.
        if (this.dataStore.getClaimAt(event.getEntity().getLocation(), false, null) != null) {
            event.setCancelled(true);
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onNetherPortalCreate(final PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) {
            return;
        }

        // Ignore this event if preventNonPlayerCreatedPortals config option is disabled, and we don't know the entity.
        if (!(event.getEntity() instanceof Player) && !GriefPrevention.instance.config_claims_preventNonPlayerCreatedPortals) {
            return;
        }

        for (BlockState blockState : event.getBlocks()) {
            Claim claim = this.dataStore.getClaimAt(blockState.getLocation(), false, null);
            if (claim != null) {
                if (event.getEntity() instanceof Player player) {
                    if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.PLACE_BLOCKS.getDenialMessage());
                        return;
                    }
                }
                else {
                    // Cancels the event if in a claim, as we can not efficiently retrieve the person/entity who created the portal.
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }



    // Prevent crops growingif their settings are false
    @EventHandler
    public void onGrow(BlockGrowEvent e) {
        if (!(e.getBlock().getBlockData() instanceof Ageable)) return;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

        if (claim == null) return;
        if (claim.isSettingEnabled(ClaimSetting.CROP_GROWTH)) return;

        e.setCancelled(true);
    }



    // Prevent block spreading if the setting is disabled
    @EventHandler
    public void onSpread(BlockSpreadEvent e) {
        // Bamboo growth
        if (e.getNewState().getType() == Material.BAMBOO) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.CROP_GROWTH)) return;

            e.setCancelled(true);
        }

        // Vine Growth
        if (e.getBlock().getType() == Material.AIR && (e.getNewState().getType() == Material.VINE || e.getNewState().getType() == Material.CAVE_VINES)) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.VINE_GROWTH)) return;

            e.setCancelled(true);
        }

        // Grass spread
        if (e.getBlock().getType() == Material.DIRT && e.getNewState().getType() == Material.GRASS_BLOCK) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.GRASS_SPREAD)) return;

            e.setCancelled(true);
        }

        // Mycelium spread
        if (e.getNewState().getType() == Material.MYCELIUM) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.MYCELIUM_SPREAD)) return;

            e.setCancelled(true);
        }

        // Sculk spread
        if (e.getNewState().getType() == Material.SCULK || e.getNewState().getType() == Material.SCULK_VEIN) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.SCULK_SPREAD)) return;

            e.setCancelled(true);
        }
    }



    // Prevent blocks forming if their settings are false
    @EventHandler
    public void onForm(BlockFormEvent e) {
        // Don't get the claim here as we only want to get it if the block types are specific (so this is called less)

        // Ice form
        if (e.getBlock().getType() == Material.WATER && e.getNewState().getType() == Material.ICE) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.ICE_FORM)) return;

            e.setCancelled(true);
        }

        // Snow form
        if (e.getBlock().getType() == Material.AIR && e.getNewState().getType() == Material.SNOW) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.SNOW_FORM)) return;

            e.setCancelled(true);
        }

        // Copper weathering
        if (e.getBlock().getType().toString().contains("COPPER")) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.COPPER_WEATHERING)) return;

            e.setCancelled(true);
        }

        // Concrete form
        if (e.getBlock().getType().toString().contains("CONCRETE_POWDER")) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.CONCRETE_FORMING)) return;

            e.setCancelled(true);
        }
    }



    // Prevent blocks fading if their settings are false
    @EventHandler
    public void onFade(BlockFadeEvent e) {
        // Don't get the claim here as we only want to get it if the block types are specific (so this is called less)

        // Ice melt
        if (e.getBlock().getType() == Material.ICE) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.ICE_MELT)) return;

            e.setCancelled(true);
        }

        // Snow melt
        if (e.getBlock().getType() == Material.SNOW) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.SNOW_MELT)) return;

            e.setCancelled(true);
        }

        // Coral drying
        if (e.getBlock().getType().toString().contains("CORAL")) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

            if (claim == null) return;
            if (claim.isSettingEnabled(ClaimSetting.CORAL_DRY)) return;

            e.setCancelled(true);
        }
    }



    // When concrete powder is placed in water, it places as concrete. We need to reverse that (as I can't find any event to cancel it)
    // NOTE: We cannot use the e.getBlockReplacedState() because if you break and place the block VERY quick, then it replaces AIR and not WATER
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onConcreteForm(BlockPlaceEvent e) {
        Block placed = e.getBlock();
        String material = placed.getType().toString();

        if (!material.contains("CONCRETE")) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

        if (claim == null) return;
        if (claim.isSettingEnabled(ClaimSetting.CONCRETE_FORMING)) return;

        // Check if any adjacent block is water
        for (BlockFace face : BlockFace.values()) {
            Block adjacentBlock = placed.getRelative(face);

            if (adjacentBlock.getType() == Material.WATER) {
                placed.setType(Material.valueOf(material + "_POWDER"));
                return;
            }
        }
    }
}
