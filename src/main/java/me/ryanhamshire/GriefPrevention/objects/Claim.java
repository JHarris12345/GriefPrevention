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

package me.ryanhamshire.GriefPrevention.objects;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.listeners.BlockEventHandler;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
import me.ryanhamshire.GriefPrevention.objects.enums.Messages;
import me.ryanhamshire.GriefPrevention.tasks.RestoreNatureProcessingTask;
import me.ryanhamshire.GriefPrevention.utils.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

//represents a player claim
//creating an instance doesn't make an effective claim
//only claims which have been added to the datastore have any effect
public class Claim {

    public Location lesserBoundaryCorner;
    public Location greaterBoundaryCorner;
    public Date modifiedDate; // Modification date. This comes from the file timestamp during load, and is updated with runtime changes
    public Long id = null; // Unique claim ID
    public String name; // Players can name their claims so they appear more custom on /claimlist and other places claims can appear
    public UUID ownerID; // The owner's UUID. NULL for admin claims. Use getOwnerName() for a friendly name ("administrator" for admin claims)
    public Claim parent = null; // Only not null if it's a subclaim
    public ArrayList<Claim> children = new ArrayList<>(); // Subclaims of this claim. Note that subclaims never have subclaims
    public List<String> members = new ArrayList<>(); // A list of all the UUIDs and roles (stored as "[uuid]:[role]") of the claim members NOT including the owner

    // Whether or not this claim is in the data store
    // If a claim instance isn't in the data store, it isn't "active" - players can't interact with it
    // Why keep this?  so that claims which have been removed from the data store can be correctly
    // Ignored even though they may have references floating around
    public boolean inDataStore = false;

    //main constructor.  note that only creating a claim instance does nothing - a claim must be added to the data store to be effective
    public Claim(String name, Location lesserBoundaryCorner, Location greaterBoundaryCorner, UUID ownerID, List<String> members, Long id) {
        this.modifiedDate = Calendar.getInstance().getTime();
        this.name = name;
        this.id = id;
        this.lesserBoundaryCorner = lesserBoundaryCorner;
        this.greaterBoundaryCorner = greaterBoundaryCorner;
        this.ownerID = ownerID;
        this.members = members;
    }

    //produces a copy of a claim.
    public Claim(Claim claim) {
        this.modifiedDate = claim.modifiedDate;
        this.lesserBoundaryCorner = claim.greaterBoundaryCorner.clone();
        this.greaterBoundaryCorner = claim.greaterBoundaryCorner.clone();
        this.id = claim.id;
        this.ownerID = claim.ownerID;
        this.members = new ArrayList<>(claim.members);
        this.inDataStore = false; //since it's a copy of a claim, not in datastore!
        this.parent = claim.parent;
        this.children = new ArrayList<>(claim.children);
        this.name = claim.name;
    }

    //removes any lava above sea level in a claim
    //exclusionClaim is another claim indicating an sub-area to be excluded from this operation
    //it may be null
    public void removeSurfaceFluids(Claim exclusionClaim) {
        //don't do this for administrative claims
        if (this.isAdminClaim()) return;

        //don't do it for very large claims
        if (this.getArea() > 10000) return;

        //only in creative mode worlds
        if (!GriefPrevention.plugin.creativeRulesApply(this.lesserBoundaryCorner)) return;

        Location lesser = this.getLesserBoundaryCorner();
        Location greater = this.getGreaterBoundaryCorner();

        if (lesser.getWorld().getEnvironment() == Environment.NETHER) return;  //don't clean up lava in the nether

        int seaLevel = 0;  //clean up all fluids in the end

        //respect sea level in normal worlds
        if (lesser.getWorld().getEnvironment() == Environment.NORMAL)
            seaLevel = GriefPrevention.plugin.getSeaLevel(lesser.getWorld());

        for (int x = lesser.getBlockX(); x <= greater.getBlockX(); x++) {
            for (int z = lesser.getBlockZ(); z <= greater.getBlockZ(); z++) {
                for (int y = seaLevel - 1; y <= lesser.getWorld().getMaxHeight(); y++) {
                    //dodge the exclusion claim
                    Block block = lesser.getWorld().getBlockAt(x, y, z);
                    if (exclusionClaim != null && exclusionClaim.contains(block.getLocation(), true, false)) continue;

                    if (block.getType() == Material.LAVA || block.getType() == Material.WATER) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    //accessor for ID
    public Long getID() {
        return this.id;
    }

    //whether or not this is an administrative claim
    //administrative claims are created and maintained by players with the griefprevention.adminclaims permission.
    public boolean isAdminClaim() {
        return this.getOwnerID() == null;
    }

    //determines whether or not a claim has surface lava
    //used to warn players when they abandon their claims about automatic fluid cleanup
    boolean hasSurfaceFluids() {
        Location lesser = this.getLesserBoundaryCorner();
        Location greater = this.getGreaterBoundaryCorner();

        //don't bother for very large claims, too expensive
        if (this.getArea() > 10000) return false;

        int seaLevel = 0;  //clean up all fluids in the end

        //respect sea level in normal worlds
        if (lesser.getWorld().getEnvironment() == Environment.NORMAL)
            seaLevel = GriefPrevention.plugin.getSeaLevel(lesser.getWorld());

        for (int x = lesser.getBlockX(); x <= greater.getBlockX(); x++) {
            for (int z = lesser.getBlockZ(); z <= greater.getBlockZ(); z++) {
                for (int y = seaLevel - 1; y <= lesser.getWorld().getMaxHeight(); y++) {
                    //dodge the exclusion claim
                    Block block = lesser.getWorld().getBlockAt(x, y, z);

                    if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    //measurements.  all measurements are in blocks
    public int getArea() {
        int claimWidth = this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
        int claimHeight = this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;

        return claimWidth * claimHeight;
    }

    public int getWidth() {
        return this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
    }

    public int getHeight() {
        return this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;
    }

    //distance check for claims, distance in this case is a band around the outside of the claim rather then euclidean distance
    public boolean isNear(Location location, int howNear) {
        Claim claim = new Claim
                (null, new Location(this.lesserBoundaryCorner.getWorld(), this.lesserBoundaryCorner.getBlockX() - howNear, this.lesserBoundaryCorner.getBlockY(), this.lesserBoundaryCorner.getBlockZ() - howNear),
                        new Location(this.greaterBoundaryCorner.getWorld(), this.greaterBoundaryCorner.getBlockX() + howNear, this.greaterBoundaryCorner.getBlockY(), this.greaterBoundaryCorner.getBlockZ() + howNear),
                        null, new ArrayList<>(), null);

        return claim.contains(location, false, true);
    }

    private static final Set<Material> PLACEABLE_FARMING_BLOCKS = EnumSet.of(
            Material.PUMPKIN_STEM,
            Material.WHEAT,
            Material.MELON_STEM,
            Material.CARROTS,
            Material.POTATOES,
            Material.NETHER_WART,
            Material.BEETROOTS,
            Material.COCOA,
            Material.GLOW_BERRIES,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT);

    private static boolean placeableForFarming(Material material) {
        return PLACEABLE_FARMING_BLOCKS.contains(material);
    }

    public boolean hasClaimPermission(UUID uuid, ClaimPermission claimPermission) {
        if (uuid.equals(this.getOwnerID())) return true;

        ClaimRole playerRole = getPlayerRole(uuid);


        return false;
    }

    //returns a copy of the location representing lower x, y, z limits
    public Location getLesserBoundaryCorner() {
        return this.lesserBoundaryCorner.clone();
    }

    //returns a copy of the location representing upper x, y, z limits
    //NOTE: remember upper Y will always be ignored, all claims always extend to the sky
    public Location getGreaterBoundaryCorner() {
        return this.greaterBoundaryCorner.clone();
    }

    //returns a friendly owner name (for admin claims, returns "an administrator" as the owner)
    public String getOwnerName() {
        if (this.parent != null) {
            return this.parent.getOwnerName();
        }

        if (this.ownerID == null) {
            return GriefPrevention.plugin.dataStore.getMessage(Messages.OwnerNameForAdminClaims);
        }

        // Dunno what all this fuckery does
        //return GriefPrevention.lookupPlayerName(this.ownerID);

        String name = GriefPrevention.plugin.uuidNameCache.getOrDefault(this.ownerID, null);
        if (name != null) return name;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(this.ownerID);
        if (offlinePlayer.isOnline()) {
            name = offlinePlayer.getPlayer().getName();
            GriefPrevention.plugin.uuidNameCache.put(this.ownerID, name);

            return offlinePlayer.getPlayer().getName();
        }

        name = offlinePlayer.getName();
        GriefPrevention.plugin.uuidNameCache.put(this.ownerID, name);

        return name;
    }

    public UUID getOwnerID() {
        if (this.parent != null) {
            return this.parent.ownerID;
        }
        return this.ownerID;
    }

    //whether or not a location is in a claim
    //ignoreHeight = true means location UNDER the claim will return TRUE
    //excludeSubdivisions = true means that locations inside subdivisions of the claim will return FALSE
    public boolean contains(Location location, boolean ignoreHeight, boolean excludeSubdivisions) {
        //not in the same world implies false
        if (!Objects.equals(location.getWorld(), this.lesserBoundaryCorner.getWorld())) return false;

        BoundingBox boundingBox = new BoundingBox(this);
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // If we're ignoring height, use 2D containment check.
        if (ignoreHeight && !boundingBox.contains2d(x, z)) {
            return false;
        }
        // Otherwise use full containment check.
        else if (!ignoreHeight && !boundingBox.contains(x, location.getBlockY(), z)) {
            return false;
        }

        //additional check for subdivisions
        //you're only in a subdivision when you're also in its parent claim
        //NOTE: if a player creates subdivions then resizes the parent claim, it's possible that
        //a subdivision can reach outside of its parent's boundaries.  so this check is important!
        if (this.parent != null) {
            return this.parent.contains(location, ignoreHeight, false);
        }

        //code to exclude subdivisions in this check
        else if (excludeSubdivisions) {
            //search all subdivisions to see if the location is in any of them
            for (Claim child : this.children) {
                //if we find such a subdivision, return false
                if (child.contains(location, ignoreHeight, true)) {
                    return false;
                }
            }
        }

        //otherwise yes
        return true;
    }

    //whether or not two claims overlap
    //used internally to prevent overlaps when creating claims
    public boolean overlaps(Claim otherClaim) {
        if (!Objects.equals(this.lesserBoundaryCorner.getWorld(), otherClaim.getLesserBoundaryCorner().getWorld()))
            return false;

        return new BoundingBox(this).intersects(new BoundingBox(otherClaim));
    }

    //whether more entities may be added to a claim
    public String allowMoreEntities(boolean remove) {
        if (this.parent != null) return this.parent.allowMoreEntities(remove);

        //this rule only applies to creative mode worlds
        if (!GriefPrevention.plugin.creativeRulesApply(this.getLesserBoundaryCorner())) return null;

        //admin claims aren't restricted
        if (this.isAdminClaim()) return null;

        //don't apply this rule to very large claims
        if (this.getArea() > 10000) return null;

        //determine maximum allowable entity count, based on claim size
        int maxEntities = this.getArea() / 50;
        if (maxEntities == 0) return GriefPrevention.plugin.dataStore.getMessage(Messages.ClaimTooSmallForEntities);

        //count current entities (ignoring players)
        int totalEntities = 0;
        ArrayList<Chunk> chunks = this.getChunks();
        for (Chunk chunk : chunks) {
            Entity[] entities = chunk.getEntities();
            for (Entity entity : entities) {
                if (!(entity instanceof Player) && this.contains(entity.getLocation(), false, false)) {
                    totalEntities++;
                    if (remove && totalEntities > maxEntities) entity.remove();
                }
            }
        }

        if (totalEntities >= maxEntities)
            return GriefPrevention.plugin.dataStore.getMessage(Messages.TooManyEntitiesInClaim);

        return null;
    }

    public String allowMoreActiveBlocks() {
        if (this.parent != null) return this.parent.allowMoreActiveBlocks();

        //determine maximum allowable entity count, based on claim size
        int maxActives = this.getArea() / 100;
        if (maxActives == 0)
            return GriefPrevention.plugin.dataStore.getMessage(Messages.ClaimTooSmallForActiveBlocks);

        //count current actives
        int totalActives = 0;
        ArrayList<Chunk> chunks = this.getChunks();
        for (Chunk chunk : chunks) {
            BlockState[] actives = chunk.getTileEntities();
            for (BlockState active : actives) {
                if (BlockEventHandler.isActiveBlock(active)) {
                    if (this.contains(active.getLocation(), false, false)) {
                        totalActives++;
                    }
                }
            }
        }

        if (totalActives >= maxActives)
            return GriefPrevention.plugin.dataStore.getMessage(Messages.TooManyActiveBlocksInClaim);

        return null;
    }

    //implements a strict ordering of claims, used to keep the claims collection sorted for faster searching
    boolean greaterThan(Claim otherClaim) {
        Location thisCorner = this.getLesserBoundaryCorner();
        Location otherCorner = otherClaim.getLesserBoundaryCorner();

        if (thisCorner.getBlockX() > otherCorner.getBlockX()) return true;

        if (thisCorner.getBlockX() < otherCorner.getBlockX()) return false;

        if (thisCorner.getBlockZ() > otherCorner.getBlockZ()) return true;

        if (thisCorner.getBlockZ() < otherCorner.getBlockZ()) return false;

        return thisCorner.getWorld().getName().compareTo(otherCorner.getWorld().getName()) < 0;
    }


    public long getPlayerInvestmentScore() {
        //decide which blocks will be considered player placed
        Location lesserBoundaryCorner = this.getLesserBoundaryCorner();
        Set<Material> playerBlocks = RestoreNatureProcessingTask.getPlayerBlocks(lesserBoundaryCorner.getWorld().getEnvironment(), lesserBoundaryCorner.getBlock().getBiome());

        //scan the claim for player placed blocks
        double score = 0;

        boolean creativeMode = GriefPrevention.plugin.creativeRulesApply(lesserBoundaryCorner);

        for (int x = this.lesserBoundaryCorner.getBlockX(); x <= this.greaterBoundaryCorner.getBlockX(); x++) {
            for (int z = this.lesserBoundaryCorner.getBlockZ(); z <= this.greaterBoundaryCorner.getBlockZ(); z++) {
                int y = this.lesserBoundaryCorner.getBlockY();
                for (; y < GriefPrevention.plugin.getSeaLevel(this.lesserBoundaryCorner.getWorld()) - 5; y++) {
                    Block block = this.lesserBoundaryCorner.getWorld().getBlockAt(x, y, z);
                    if (playerBlocks.contains(block.getType())) {
                        if (block.getType() == Material.CHEST && !creativeMode) {
                            score += 10;
                        }
                        else {
                            score += .5;
                        }
                    }
                }

                for (; y < this.lesserBoundaryCorner.getWorld().getMaxHeight(); y++) {
                    Block block = this.lesserBoundaryCorner.getWorld().getBlockAt(x, y, z);
                    if (playerBlocks.contains(block.getType())) {
                        if (block.getType() == Material.CHEST && !creativeMode) {
                            score += 10;
                        }
                        else if (creativeMode && (block.getType() == Material.LAVA)) {
                            score -= 10;
                        }
                        else {
                            score += 1;
                        }
                    }
                }
            }
        }

        return (long) score;
    }

    public ArrayList<Chunk> getChunks() {
        ArrayList<Chunk> chunks = new ArrayList<>();

        World world = this.getLesserBoundaryCorner().getWorld();
        Chunk lesserChunk = this.getLesserBoundaryCorner().getChunk();
        Chunk greaterChunk = this.getGreaterBoundaryCorner().getChunk();

        for (int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++) {
            for (int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++) {
                chunks.add(world.getChunkAt(x, z));
            }
        }

        return chunks;
    }

    public ArrayList<Long> getChunkHashes() {
        return DataStore.getChunkHashes(this);
    }

    /*public void loadGUIs() {
        if (!loadedGUIs) {
            menuGUI = new MenuGUI(this);
        }
    }*/

    public String getUUIDFromMemberListEntry(String entry) {
        try {
            return entry.split(":")[0];
        } catch (PatternSyntaxException ex) {
            return entry;
        }
    }

    public ClaimRole getRoleFromMemberListEntry(String entry) {
        try {
            return ClaimRole.valueOf(entry.split(":")[1]);
        } catch (PatternSyntaxException ex) {
            return ClaimRole.GUEST;
        }
    }

    public ClaimRole getPlayerRole(UUID player) {
        if (ownerID == player) return ClaimRole.OWNER;

        for (String entry : members) {
            if (!getUUIDFromMemberListEntry(entry).equals(player.toString())) continue;

            return getRoleFromMemberListEntry(entry);
        }

        return ClaimRole.PUBLIC;
    }
}
