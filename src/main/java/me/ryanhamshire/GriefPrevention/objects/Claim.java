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
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSettingValue;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.xml.crypto.Data;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

//represents a player claim
//creating an instance doesn't make an effective claim
//only claims which have been added to the datastore have any effect
public class Claim {

    public ClaimCorner lesserBoundaryCorner;
    public ClaimCorner greaterBoundaryCorner;
    public long created; // The time the claim was created
    public Date modifiedDate; // Modification date. This comes from the file timestamp during load, and is updated with runtime changes
    public Long id = null; // Unique claim ID
    public String name; // Players can name their claims so they appear more custom on /claimlist and other places claims can appear
    public UUID ownerID; // The owner's UUID. NULL for admin claims. Use getOwnerName() for a friendly name ("administrator" for admin claims)
    public Claim parent = null; // Only not null if it's a subclaim
    public ArrayList<Claim> children = new ArrayList<>(); // Subclaims of this claim. Note that subclaims never have subclaims
    public HashMap<UUID, ClaimRole> members = new HashMap<>(); // A map of all the members and their role in the claim NOT including the owner
    public HashMap<ClaimRole, HashMap<ClaimPermission, Boolean>> permissions = new HashMap<>(); // A map of the claim roles and the set values for the permissions (only includes ones that have been explicitly set)
    public HashMap<ClaimSetting, ClaimSettingValue> settings = new HashMap<>(); // A map of the claim settings and their values
    public List<ClaimPermission> unlockedPermissions = new ArrayList<>(); // A list of the permissions they have purchased toggleability for
    public List<ClaimSetting> unlockedSettings = new ArrayList<>(); // A list of the settings they have purchased toggleability for

    // Whether or not this claim is in the data store
    // If a claim instance isn't in the data store, it isn't "active" - players can't interact with it
    // Why keep this?  so that claims which have been removed from the data store can be correctly
    // Ignored even though they may have references floating around
    public boolean inDataStore = false;

    //main constructor.  note that only creating a claim instance does nothing - a claim must be added to the data store to be effective
    public Claim(String name, ClaimCorner lesserBoundaryCorner, ClaimCorner greaterBoundaryCorner, UUID ownerID, HashMap<UUID, ClaimRole> members, HashMap<ClaimRole, HashMap<ClaimPermission, Boolean>> permissions, Long id) {
        this.modifiedDate = Calendar.getInstance().getTime();
        this.name = name;
        this.id = id;
        this.lesserBoundaryCorner = lesserBoundaryCorner;
        this.greaterBoundaryCorner = greaterBoundaryCorner;
        this.ownerID = ownerID;
        this.members = members;
        this.permissions = permissions;
    }

    //produces a copy of a claim.
    public Claim(Claim claim) {
        this.modifiedDate = claim.modifiedDate;
        this.lesserBoundaryCorner = claim.greaterBoundaryCorner;
        this.greaterBoundaryCorner = claim.greaterBoundaryCorner;
        this.id = claim.id;
        this.ownerID = claim.ownerID;
        this.members = new HashMap<>(claim.members);
        this.inDataStore = false; //since it's a copy of a claim, not in datastore!
        this.parent = claim.parent;
        this.children = new ArrayList<>(claim.children);
        this.permissions = claim.permissions;
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

        Location lesser = DataStore.locationFromClaimCorner(this.getLesserBoundaryCorner());
        Location greater = DataStore.locationFromClaimCorner(this.getGreaterBoundaryCorner());

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
                    if (exclusionClaim != null && exclusionClaim.contains(block.getLocation(), false)) continue;

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
        Location lesser = DataStore.locationFromClaimCorner(this.getLesserBoundaryCorner());
        Location greater = DataStore.locationFromClaimCorner(this.getGreaterBoundaryCorner());

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
        int claimWidth = this.greaterBoundaryCorner.x - this.lesserBoundaryCorner.x + 1;
        int claimHeight = this.greaterBoundaryCorner.z - this.lesserBoundaryCorner.z + 1;

        return claimWidth * claimHeight;
    }

    public int getWidth() {
        return this.greaterBoundaryCorner.x - this.lesserBoundaryCorner.x + 1;
    }

    public int getHeight() {
        return this.greaterBoundaryCorner.z - this.lesserBoundaryCorner.z + 1;
    }

    //distance check for claims, distance in this case is a band around the outside of the claim rather then euclidean distance
    public boolean isNear(Location location, int howNear) {
        int x = this.lesserBoundaryCorner.x;
        int y = this.lesserBoundaryCorner.y;
        int z = this.lesserBoundaryCorner.z;

        ClaimCorner newLesser = new ClaimCorner(location.getWorld(), (x - howNear), y, (z - howNear));
        ClaimCorner newGreater = new ClaimCorner(location.getWorld(), (x + howNear), y, (z + howNear));

        Claim claim = new Claim(null, newLesser, newGreater, null, new HashMap<>(), new HashMap<>(),null);
        return claim.contains(location, true);
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

        PlayerData data = GriefPrevention.plugin.dataStore.getPlayerData(uuid);
        if (data != null && data.ignoreClaims) return true;

        ClaimRole playerRole = getPlayerRole(uuid);
        return permissions.getOrDefault(playerRole, new HashMap<>()).getOrDefault(claimPermission, claimPermission.getDefaultPermission(playerRole));
    }

    public boolean doesRoleHavePermission(ClaimRole claimRole, ClaimPermission claimPermission) {
        if (claimRole == ClaimRole.OWNER) return true;

        if (parent == null) {
            return permissions.getOrDefault(claimRole, new HashMap<>()).getOrDefault(claimPermission, claimPermission.getDefaultPermission(claimRole));
        }

        // If it's a subclaim then we take the parent value if not explicitly set
        HashMap<ClaimPermission, Boolean> subPerms = permissions.getOrDefault(claimRole, new HashMap<>());
        return (subPerms.containsKey(claimPermission)) ? subPerms.get(claimPermission) : parent.doesRoleHavePermission(claimRole, claimPermission);
    }

    public void addPermissionToRole(ClaimPermission permission, ClaimRole role) {
        if (role == ClaimRole.OWNER) return;

        HashMap<ClaimPermission, Boolean> permissions = this.permissions.get(role);
        permissions.put(permission, true);

        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public void removePermissionFromRole(ClaimPermission permission, ClaimRole role) {
        if (role == ClaimRole.OWNER) return;

        HashMap<ClaimPermission, Boolean> permissions = this.permissions.get(role);
        permissions.put(permission, false);

        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    //returns a copy of the location representing lower x, y, z limits
    public ClaimCorner getLesserBoundaryCorner() {
        return this.lesserBoundaryCorner;
    }

    //returns a copy of the location representing upper x, y, z limits
    //NOTE: remember upper Y will always be ignored, all claims always extend to the sky
    public ClaimCorner getGreaterBoundaryCorner() {
        return this.greaterBoundaryCorner;
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

    public boolean contains(ClaimCorner claimCorner, boolean excludeSubdivisions) {
        return contains(claimCorner.location(), excludeSubdivisions);
    }

    //whether or not a location is in a claim
    //excludeSubdivisions = true means that locations inside subdivisions of the claim will return FALSE (unless the claim itself IS the sub division)
    public boolean contains(Location location, boolean excludeSubdivisions) {
        //not in the same world implies false
        if (!Objects.equals(location.getWorld(), lesserBoundaryCorner.world)) return false;

        int smallX = lesserBoundaryCorner.x;
        int smallZ = lesserBoundaryCorner.z;
        int bigX = greaterBoundaryCorner.x;
        int bigZ = greaterBoundaryCorner.z;

        // JHarris - Is this needed?
        //additional check for subdivisions
        //you're only in a subdivision when you're also in its parent claim
        //NOTE: if a player creates subdivions then resizes the parent claim, it's possible that
        //a subdivision can reach outside of its parent's boundaries.  so this check is important!
        /*if (this.parent != null) {
            return this.parent.contains(location, false);
        }*/

        if (location.getBlockX() >= smallX && location.getBlockX() <= bigX &&
                location.getBlockZ() >= smallZ && location.getBlockZ() <= bigZ) {

            // The location is inside the claim. If we aren't excluding subdivisions (or the claim has no sub claims), we can just return true now
            if (!excludeSubdivisions || this.children.isEmpty()) return true;

            // We ARE excluding subdivisions so now we need to see if this location is in the
            // claim's subdivision and, if it is, return false
            for (Claim child : this.children) {
                if (child.contains(location, true)) {
                    return false;
                }
            }

            // The location was NOT in a sub claim
            return true;
        }

        return false;
    }

    //whether or not two claims overlap
    //used internally to prevent overlaps when creating claims
    public boolean overlaps(Claim otherClaim) {
        if (!Objects.equals(lesserBoundaryCorner.world, otherClaim.lesserBoundaryCorner.world)) return false;

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
                if (!(entity instanceof Player) && this.contains(entity.getLocation(), false)) {
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
                    if (this.contains(active.getLocation(), false)) {
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
        String thisWorld = lesserBoundaryCorner.world.getName();
        int thisX = lesserBoundaryCorner.x;
        int thisZ = lesserBoundaryCorner.z;

        String otherWorld = otherClaim.lesserBoundaryCorner.world.getName();
        int otherX = otherClaim.lesserBoundaryCorner.x;
        int otherZ = otherClaim.lesserBoundaryCorner.z;

        if (thisX > otherX) return true;

        if (thisX < otherX) return false;

        if (thisZ > otherZ) return true;

        if (thisZ < otherZ) return false;

        return thisWorld.compareTo(otherWorld) < 0;
    }


    public long getPlayerInvestmentScore() {
        //decide which blocks will be considered player placed
        ClaimCorner lesserBoundaryCorner = this.getLesserBoundaryCorner();
        Location lesserBoundaryLocation = DataStore.locationFromClaimCorner(lesserBoundaryCorner);

        Set<Material> playerBlocks = RestoreNatureProcessingTask.getPlayerBlocks(lesserBoundaryCorner.world.getEnvironment(), lesserBoundaryLocation.getBlock().getBiome());

        //scan the claim for player placed blocks
        double score = 0;

        boolean creativeMode = GriefPrevention.plugin.creativeRulesApply(lesserBoundaryCorner);

        for (int x = this.lesserBoundaryCorner.x; x <= this.greaterBoundaryCorner.x; x++) {
            for (int z = this.lesserBoundaryCorner.z; z <= this.greaterBoundaryCorner.z; z++) {
                int y = this.lesserBoundaryCorner.y;
                for (; y < GriefPrevention.plugin.getSeaLevel(this.lesserBoundaryCorner.world) - 5; y++) {
                    Block block = this.lesserBoundaryCorner.world.getBlockAt(x, y, z);
                    if (playerBlocks.contains(block.getType())) {
                        if (block.getType() == Material.CHEST && !creativeMode) {
                            score += 10;
                        }
                        else {
                            score += .5;
                        }
                    }
                }

                for (; y < this.lesserBoundaryCorner.world.getMaxHeight(); y++) {
                    Block block = this.lesserBoundaryCorner.world.getBlockAt(x, y, z);
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

        World world = this.getLesserBoundaryCorner().world;
        Chunk lesserChunk = this.getLesserBoundaryCorner().chunk();
        Chunk greaterChunk = this.getGreaterBoundaryCorner().chunk();

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

    // LinkedHashMap because we want to retain the order of owner -> guest
    public LinkedHashMap<UUID, ClaimRole> getClaimMembers(boolean includeOwner) {
        LinkedHashMap<UUID, ClaimRole> members = new LinkedHashMap<>();

        if (includeOwner) {
            members.put(ownerID, ClaimRole.OWNER);
        }

        for (UUID member : this.members.keySet()) {
            members.put(member, this.members.get(member));
        }

        return members.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(ClaimRole::numericalPriority).reversed()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public ClaimRole getPlayerRole(UUID player) {
        if (player.equals(ownerID)) return ClaimRole.OWNER;
        return members.getOrDefault(player, ClaimRole.PUBLIC);
    }

    public void setClaimRole(UUID uuid, ClaimRole claimRole) {
        this.members.put(uuid, claimRole);
        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public void loadPermissions(YamlConfiguration claimConfig) {
        HashMap<ClaimRole, HashMap<ClaimPermission, Boolean>> permissions = new HashMap<>();

        for (ClaimRole role : ClaimRole.values()) {
            // Skip owners as they have every perm
            if (role == ClaimRole.OWNER) continue;

            // The list of set permissions
            HashMap<ClaimPermission, Boolean> rolePermissions = new HashMap<>();

            for (ClaimPermission permission : ClaimPermission.values()) {
                // If they don't have this setting specifically set, we don't need to add it as it will take the default value
                if (!claimConfig.isSet("Permissions." + permission.name() + "." + role.name())) continue;

                rolePermissions.put(permission, claimConfig.getBoolean("Permissions." + permission.name() + "." + role.name()));
            }

            permissions.put(role, rolePermissions);
        }

        // Now load if they have purchased any permissions
        List<String> unlockedPermissions = claimConfig.getStringList("UnlockedPermissions");
        for (String uPerm : unlockedPermissions) {
            this.unlockedPermissions.add(ClaimPermission.valueOf(uPerm));
        }

        this.permissions = permissions;
    }

    public void unlockClaimPermission(ClaimPermission claimPermission) {
        Claim claim = (parent == null) ? this : parent;

        if (!claim.unlockedPermissions.contains(claimPermission)) {
            claim.unlockedPermissions.add(claimPermission);
        }

        GriefPrevention.plugin.dataStore.saveClaim(claim);
    }

    public boolean isPermissionUnlocked(ClaimPermission claimPermission) {
        if (claimPermission.getUnlockCost() == 0) return true;

        // If it's a subclaim then we always take the value of the main claim
        return (parent == null) ? unlockedPermissions.contains(claimPermission) : parent.unlockedPermissions.contains(claimPermission);
    }

    public boolean isSettingEnabled(ClaimSetting setting) {
        if (setting != ClaimSetting.FORCED_TIME && setting != ClaimSetting.FORCED_WEATHER) {
            if (parent == null) {
                return settings.getOrDefault(setting, setting.getDefaultValue()) == ClaimSettingValue.TRUE;
            }

            // If it's a subclaim we check if the setting is set for it. If not, get the main claim's setting value
            ClaimSettingValue setValue = settings.getOrDefault(setting, null);
            return (setValue == null) ? parent.isSettingEnabled(setting) : setValue == ClaimSettingValue.TRUE;
        }

        return false;
    }

    public void enableSetting(ClaimSetting setting) {
        settings.put(setting, ClaimSettingValue.TRUE);
        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public void disableSetting(ClaimSetting setting) {
        settings.put(setting, ClaimSettingValue.FALSE);
        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public void setForcedTimeSetting(ClaimSettingValue value) {
        settings.put(ClaimSetting.FORCED_TIME, value);
        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public void setForcedWeatherSetting(ClaimSettingValue value) {
        settings.put(ClaimSetting.FORCED_WEATHER, value);
        GriefPrevention.plugin.dataStore.saveClaim(this);
    }

    public ClaimSettingValue getForcedTimeSetting() {
        if (parent == null) {
            return settings.getOrDefault(ClaimSetting.FORCED_TIME, ClaimSetting.FORCED_TIME.getDefaultValue());
        }

        // If it's a subclaim we check if the setting is set for it. If not, get the main claim's setting value
        ClaimSettingValue setValue = settings.getOrDefault(ClaimSetting.FORCED_TIME, null);
        return (setValue == null) ? parent.getForcedTimeSetting() : setValue;
    }

    public ClaimSettingValue getForcedWeatherSetting() {
        if (parent == null) {
            return settings.getOrDefault(ClaimSetting.FORCED_WEATHER, ClaimSetting.FORCED_WEATHER.getDefaultValue());
        }

        // If it's a subclaim we check if the setting is set for it. If not, get the main claim's setting value
        ClaimSettingValue setValue = settings.getOrDefault(ClaimSetting.FORCED_WEATHER, null);
        return (setValue == null) ? parent.getForcedWeatherSetting() : setValue;
    }

    public void loadSettings(YamlConfiguration claimConfig) {
        HashMap<ClaimSetting, ClaimSettingValue> settings = new HashMap<>();

        for (ClaimSetting setting : ClaimSetting.values()) {
            // If they don't have this setting specifically set, we don't need to add it as it will take the default value
            if (!claimConfig.isSet("Settings." + setting.name())) continue;

            ClaimSettingValue setValue = ClaimSettingValue.valueOf(claimConfig.getString("Settings." + setting.name()));
            settings.put(setting, setValue);
        }

        // Now load if they have purchased any settings
        List<String> unlockedSettings = claimConfig.getStringList("UnlockedSettings");
        for (String uSetting : unlockedSettings) {
            this.unlockedSettings.add(ClaimSetting.valueOf(uSetting));
        }

        this.settings = settings;
    }

    public void unlockClaimSetting(ClaimSetting claimSetting) {
        Claim claim = (parent == null) ? this : parent;

        if (!claim.unlockedSettings.contains(claimSetting)) {
            claim.unlockedSettings.add(claimSetting);
        }

        GriefPrevention.plugin.dataStore.saveClaim(claim);
    }

    public boolean isSettingUnlocked(ClaimSetting claimSetting) {
        if (claimSetting.getUnlockCost() == 0) return true;

        // If it's a subclaim then we always take the value of the main claim
        return (parent == null) ? unlockedSettings.contains(claimSetting) : parent.unlockedSettings.contains(claimSetting);
    }
}
