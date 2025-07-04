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

package me.ryanhamshire.GriefPrevention.data;

import com.google.common.io.Files;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.ClaimCorner;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSettingValue;
import me.ryanhamshire.GriefPrevention.objects.enums.CustomLogEntryTypes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//manages data stored in the file system
public class FlatFileDataStore extends DataStore {
    private final static String claimDataFolderPath = dataLayerFolderPath + File.separator + "ClaimData";
    private final static String nextClaimIdFilePath = claimDataFolderPath + File.separator + "_nextClaimID";
    private final static String schemaVersionFilePath = dataLayerFolderPath + File.separator + "_schemaVersion";
    public static final LinkedHashMap<String, Long> loadingTimes = new LinkedHashMap<>(); // A map of every key section in a claim and the total time it took to load across EVERY claim

    public static boolean hasData() {
        File claimsDataFolder = new File(claimDataFolderPath);

        return claimsDataFolder.exists();
    }

    //initialization!
    public FlatFileDataStore() throws Exception {
        this.initialize();
    }

    @Override
    public void initialize() throws Exception {
        //ensure data folders exist
        boolean newDataStore = false;
        File playerDataFolder = new File(playerDataFolderPath);
        File claimDataFolder = new File(claimDataFolderPath);
        if (!playerDataFolder.exists() || !claimDataFolder.exists()) {
            newDataStore = true;
            playerDataFolder.mkdirs();
            claimDataFolder.mkdirs();
        }

        //if there's no data yet, then anything written will use the schema implemented by this code
        if (newDataStore) {
            this.setSchemaVersion(DataStore.latestSchemaVersion);
        }

        // What is group data? Can't see any files starting with a $ like it filters for. Removed for now as it takes ~250ms to load but we can always add it back if needed
        //load group data into memory
        /*long groupDataStart = System.currentTimeMillis();
        File[] files = playerDataFolder.listFiles();
        for (File file : files)
        {
            if (!file.isFile()) continue;  //avoids folders

            //all group data files start with a dollar sign.  ignoring the rest, which are player data files.
            if (!file.getName().startsWith("$")) continue;

            String groupName = file.getName().substring(1);
            if (groupName == null || groupName.isEmpty()) continue;  //defensive coding, avoid unlikely cases

            BufferedReader inStream = null;
            try
            {
                inStream = new BufferedReader(new FileReader(file.getAbsolutePath()));
                String line = inStream.readLine();

                int groupBonusBlocks = Integer.parseInt(line);

                this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
            }
            catch (Exception e)
            {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                GriefPrevention.AddLogEntry(errors.toString(), CustomLogEntryTypes.Exception);
            }

            try
            {
                if (inStream != null) inStream.close();
            }
            catch (IOException exception) {}
        }

        GriefPrevention.instance.getLogger().info("Loaded group data in " + (System.currentTimeMillis() - groupDataStart) + "ms");*/

        //load next claim number from file
        File nextClaimIdFile = new File(nextClaimIdFilePath);
        if (nextClaimIdFile.exists()) {
            BufferedReader inStream = null;
            try {
                inStream = new BufferedReader(new FileReader(nextClaimIdFile.getAbsolutePath()));

                //read the id
                String line = inStream.readLine();

                //try to parse into a long value
                this.nextClaimID = Long.parseLong(line);
            }
            catch (Exception e) {}

            try {
                if (inStream != null) inStream.close();
            }
            catch (IOException exception) {}
        }

        //load claims data into memory
        //get a list of all the files in the claims data folder
        File[] files = claimDataFolder.listFiles();

        this.loadClaimData(files);
        super.initialize();
    }

    void loadClaimData(File[] files) throws Exception {
        long claimDataStart = System.currentTimeMillis();
        long sectionStart = System.currentTimeMillis();

        ConcurrentHashMap<Claim, Long> orphans = new ConcurrentHashMap<>();
        Arrays.stream(files).parallel().forEach(file -> {
            //avoids folders
            if (file.isFile()) {
                String fileName = file.getName();

                // DEPRECATED - The try block below this that parses the claim ID should be enough
                //skip any file starting with an underscore, to avoid special files not representing land claims
                /*if (fileName.startsWith("_")) return;

                //delete any which don't end in .yml
                if (!fileName.endsWith(".yml")) {
                    file.delete();
                    return;
                }*/

                //the filename is the claim ID.  try to parse it
                long claimID;
                try {
                    claimID = Long.parseLong(fileName.split("\\.")[0]);
                }
                catch (Exception e) {
                    return;
                }

                try {
                    ArrayList<Long> out_parentID = new ArrayList<>();  //hacky output parameter to see if it has a parent
                    Claim claim = this.loadClaim(file, out_parentID, claimID);

                    // Doesn't have a parent - Is a main claim (load now)
                    if (out_parentID.size() == 0 || out_parentID.get(0) == -1) {
                        this.addClaim(claim, false);
                    }

                    // Has a parent - Is a sub claim (load later)
                    else {
                        orphans.put(claim, out_parentID.get(0));
                    }
                }

                //if there's any problem with the file's content, log an error message and skip it
                catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("World not found")) {
                        GriefPrevention.AddLogEntry("Failed to load a claim (ID:" + claimID + ") because its world isn't loaded (yet?).  If this is not expected, delete this claim.");
                    }
                    else {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        GriefPrevention.AddLogEntry(file.getName() + " " + errors.toString(), CustomLogEntryTypes.Exception);
                    }
                }
            }
        });

        // link children to parents
        for (Claim child : orphans.keySet()) {
            Claim parent = this.getClaim(orphans.get(child));

            if (parent != null) {
                if (!parent.contains(child.lesserBoundaryCorner, true) || !parent.contains(child.greaterBoundaryCorner, true)) {
                    GriefPrevention.instance.getLogger().info("Claim " + child.id + " is a sub claim that is not full within its parent claim");
                    continue;
                }

                child.parent = parent;
                child.ownerID = parent.ownerID;
                this.addClaim(child, false);

            } else {
                GriefPrevention.instance.getLogger().info("Claim " + child.id + " is a sub claim but the parent is null");
            }
        }

        GriefPrevention.instance.getLogger().info("Loaded the claim data in " + (System.currentTimeMillis() - claimDataStart) + "ms");
    }

    Claim loadClaim(File file, ArrayList<Long> out_parentID, long claimID) throws IOException, InvalidConfigurationException, Exception {
        List<String> lines = Files.readLines(file, Charset.forName("UTF-8"));
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }

        return this.loadClaim(builder.toString(), out_parentID, file.lastModified(), claimID, Bukkit.getServer().getWorlds());
    }

    Claim loadClaim(String input, ArrayList<Long> out_parentID, long lastModifiedDate, long claimID, List<World> validWorlds) throws InvalidConfigurationException, Exception {
        long start = System.currentTimeMillis();
        Claim claim = null;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(input);
        loadingTimes.put("yaml", loadingTimes.getOrDefault("yaml", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        //boundaries
        ClaimCorner lesserBoundaryCorner = DataStore.locationStringToClaimCorner(yaml.getString("Lesser Boundary Corner"));
        ClaimCorner greaterBoundaryCorner = DataStore.locationStringToClaimCorner(yaml.getString("Greater Boundary Corner"));
        loadingTimes.put("corners", loadingTimes.getOrDefault("corners", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        lesserBoundaryCorner.y = -64;
        greaterBoundaryCorner.y = 320;

        // name
        String name = yaml.getString("Name");

        //owner
        String ownerIdentifier = yaml.getString("Owner");
        UUID ownerID = null;
        if (!ownerIdentifier.isEmpty()) {
            try {
                ownerID = UUID.fromString(ownerIdentifier);
            }
            catch (Exception ex) {
                GriefPrevention.AddLogEntry("Error - this is not a valid UUID: " + ownerIdentifier + ".");
                GriefPrevention.AddLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
            }
        }

        loadingTimes.put("ownerID", loadingTimes.getOrDefault("ownerID", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        HashMap<UUID, ClaimRole> members = new HashMap<>();
        HashMap<UUID, Long> spentICoins = new HashMap<>();

        // Support for the old data
        ConfigurationSection section = yaml.getConfigurationSection("Members");

        if (section != null) {
            for (String memberUUIDKey : section.getKeys(false)) {
                members.put(UUID.fromString(memberUUIDKey), ClaimRole.valueOf(yaml.getString("Members." + memberUUIDKey)));
            }
        }

        // Old data - all members should be the MEMBER role
        else {
            for (String entry : yaml.getStringList("Builders")) {
                try {
                    members.put(UUID.fromString(entry), ClaimRole.MEMBER);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection iCoinsSection = yaml.getConfigurationSection("SpentICoins");
        if (iCoinsSection != null) {
            for (String uuidKey : iCoinsSection.getKeys(false)) {
                long iCoins = yaml.getLong("SpentICoins." + uuidKey);
                spentICoins.put(UUID.fromString(uuidKey), iCoins);

                GriefPrevention.getInstance().iCoinSpend += iCoins;
            }
        }

        loadingTimes.put("members", loadingTimes.getOrDefault("members", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        out_parentID.add(yaml.getLong("Parent Claim ID", -1L));

        long created = yaml.getLong("Created");
        boolean builtOn = yaml.getBoolean("BuiltOn");
        List<String> ownerRanks = yaml.getStringList("OwnerRanks");

        // instantiate
        claim = new Claim(name, lesserBoundaryCorner, greaterBoundaryCorner, ownerID, members, new HashMap<>(), ownerRanks, created, builtOn, spentICoins, claimID, new ArrayList<>());
        claim.modifiedDate = new Date(lastModifiedDate);
        claim.id = claimID;

        start = System.currentTimeMillis();
        claim.loadPermissions(yaml);
        loadingTimes.put("permissions", loadingTimes.getOrDefault("permissions", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        claim.loadSettings(yaml);
        loadingTimes.put("settings", loadingTimes.getOrDefault("settings", 0L) + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        return claim;
    }

    String getYamlForClaim(Claim claim) {
        YamlConfiguration yaml = new YamlConfiguration();

        //boundaries
        yaml.set("Lesser Boundary Corner", DataStore.locationStringFromClaimCorner(claim.lesserBoundaryCorner));
        yaml.set("Greater Boundary Corner", DataStore.locationStringFromClaimCorner(claim.greaterBoundaryCorner));

        //owner
        String ownerID = "";
        if (claim.ownerID != null) ownerID = claim.ownerID.toString();
        yaml.set("Owner", ownerID);

        //name
        yaml.set("Name", claim.name);

        for (UUID member : claim.members.keySet()) {
            yaml.set("Members." + member.toString(), claim.members.get(member).toString());
        }

        for (UUID member : claim.spentICoins.keySet()) {
            yaml.set("SpentICoins." + member.toString(), claim.spentICoins.get(member));
        }

        Long parentID = -1L;
        if (claim.parent != null) {
            parentID = claim.parent.id;
        }

        yaml.set("Parent Claim ID", parentID);

        // Permissions - We have to save all permissions that have been set EVEN if they're the same as the default
        // because subclaims will not be able to get set to default values independently of main claims otherwise
        for (ClaimRole claimRole : claim.permissions.keySet()) {
            for (ClaimPermission claimPermission : claim.permissions.get(claimRole).keySet()) {
                boolean setValue = claim.permissions.get(claimRole).get(claimPermission);
                yaml.set("Permissions." + claimPermission.name() + "." + claimRole.name(), setValue);
            }
        }

        // Unlocked permissions
        List<String> unlockedPermissions = new ArrayList<>();
        for (ClaimPermission permission : claim.unlockedPermissions) {
            unlockedPermissions.add(permission.name());
        }
        yaml.set("UnlockedPermissions", unlockedPermissions);

        // Settings - We have to save all settings that have been set EVEN if they're the same as the default
        // because subclaims will not be able to get set to default values independently of main claims otherwise
        for (ClaimSetting setting : claim.settings.keySet()) {
            ClaimSettingValue value = claim.settings.get(setting);
            yaml.set("Settings." + setting.name(), value.name());
        }

        // Unlocked settings
        List<String> unlockedSettings = new ArrayList<>();
        for (ClaimSetting setting : claim.unlockedSettings) {
            unlockedSettings.add(setting.name());
        }
        yaml.set("UnlockedSettings", unlockedSettings);

        // Owner ranks
        yaml.set("OwnerRanks", claim.ownerRanks);

        // Claim creation time
        yaml.set("Created", claim.created);

        // If it's been built on
        yaml.set("BuiltOn", claim.builtOn);

        return yaml.saveToString();
    }

    @Override
    synchronized void writeClaimToStorage(Claim claim) {
        String claimID = String.valueOf(claim.id);

        String yaml = this.getYamlForClaim(claim);

        try {
            //open the claim's file
            File claimFile = new File(claimDataFolderPath + File.separator + claimID + ".yml");
            claimFile.createNewFile();
            Files.write(yaml.getBytes("UTF-8"), claimFile);
        }

        //if any problem, log it
        catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.AddLogEntry(claimID + " " + errors.toString(), CustomLogEntryTypes.Exception);
        }
    }

    //deletes a claim from the file system
    @Override
    synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
        String claimID = String.valueOf(claim.id);

        //remove from disk
        File claimFile = new File(claimDataFolderPath + File.separator + claimID + ".yml");
        if (claimFile.exists() && !claimFile.delete()) {
            GriefPrevention.AddLogEntry("Error: Unable to delete claim file \"" + claimFile.getAbsolutePath() + "\".");
        }
    }

    @Override
    public synchronized PlayerData getPlayerDataFromStorage(UUID playerID) {
        File playerFile = new File(playerDataFolderPath + File.separator + playerID.toString());

        PlayerData playerData = new PlayerData();
        playerData.playerID = playerID;

        //if it exists as a file, read the file
        if (playerFile.exists()) {
            boolean needRetry = false;
            int retriesRemaining = 5;
            Exception latestException = null;
            do {
                try {
                    needRetry = false;

                    //read the file content and immediately close it
                    List<String> lines = Files.readLines(playerFile, Charset.forName("UTF-8"));
                    Iterator<String> iterator = lines.iterator();


                    iterator.next();
                    //first line is last login timestamp //RoboMWM - not using this anymore
//
//    				//convert that to a date and store it
//    				DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
//    				try
//    				{
//    					playerData.setLastLogin(dateFormat.parse(lastLoginTimestampString));
//    				}
//    				catch(ParseException parseException)
//    				{
//    					GriefPrevention.AddLogEntry("Unable to load last login for \"" + playerFile.getName() + "\".");
//    					playerData.setLastLogin(null);
//    				}

                    //second line is accrued claim blocks
                    String accruedBlocksString = iterator.next();

                    //convert that to a number and store it
                    playerData.setAccruedClaimBlocks(Integer.parseInt(accruedBlocksString));

                    //third line is any bonus claim blocks granted by administrators
                    String bonusBlocksString = iterator.next();

                    //convert that to a number and store it
                    playerData.setBonusClaimBlocks(Integer.parseInt(bonusBlocksString));

                    //fourth line is a double-semicolon-delimited list of claims, which is currently ignored
                    //String claimsString = inStream.readLine();
                    //iterator.next();
                }

                //if there's any problem with the file's content, retry up to 5 times with 5 milliseconds between
                catch (Exception e) {
                    latestException = e;
                    needRetry = true;
                    retriesRemaining--;
                }

                try {
                    if (needRetry) Thread.sleep(5);
                }
                catch (InterruptedException exception) {}

            } while (needRetry && retriesRemaining >= 0);

            //if last attempt failed, log information about the problem
            if (needRetry) {
                StringWriter errors = new StringWriter();
                latestException.printStackTrace(new PrintWriter(errors));
                GriefPrevention.AddLogEntry("Failed to load PlayerData for " + playerID + ". This usually occurs when your server runs out of storage space, causing any file saves to corrupt. Fix or delete the file in GriefPrevetionData/PlayerData/" + playerID, CustomLogEntryTypes.Debug, false);
                GriefPrevention.AddLogEntry(playerID + " " + errors.toString(), CustomLogEntryTypes.Exception);
            }
        }

        return playerData;
    }

    //saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
    @Override
    public void overrideSavePlayerData(UUID playerID, PlayerData playerData) {
        //never save data for the "administrative" account.  null for claim owner ID indicates administrative account
        if (playerID == null) return;

        StringBuilder fileContent = new StringBuilder();
        try {
            //first line is last login timestamp //RoboMWM - no longer storing/using
            //if(playerData.getLastLogin() == null) playerData.setLastLogin(new Date());
            //DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
            //fileContent.append(dateFormat.format(playerData.getLastLogin()));
            fileContent.append("\n");

            //second line is accrued claim blocks
            fileContent.append(String.valueOf(playerData.getAccruedClaimBlocks()));
            fileContent.append("\n");

            //third line is bonus claim blocks
            fileContent.append(String.valueOf(playerData.getBonusClaimBlocks()));
            fileContent.append("\n");

            //fourth line is blank
            fileContent.append("\n");

            //write data to file
            File playerDataFile = new File(playerDataFolderPath + File.separator + playerID.toString());
            Files.write(fileContent.toString().getBytes("UTF-8"), playerDataFile);
        }

        //if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("GriefPrevention: Unexpected exception saving data for player \"" + playerID.toString() + "\": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    synchronized void incrementNextClaimID() {
        //increment in memory
        this.nextClaimID++;

        BufferedWriter outStream = null;

        try {
            //open the file and write the new value
            File nextClaimIdFile = new File(nextClaimIdFilePath);
            nextClaimIdFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(nextClaimIdFile));

            outStream.write(String.valueOf(this.nextClaimID));
        }

        //if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("Unexpected exception saving next claim ID: " + e.getMessage());
            e.printStackTrace();
        }

        //close the file
        try {
            if (outStream != null) outStream.close();
        }
        catch (IOException exception) {}
    }

    //grants a group (players with a specific permission) bonus claim blocks as long as they're still members of the group
    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
        //write changes to file to ensure they don't get lost
        BufferedWriter outStream = null;
        try {
            //open the group's file
            File groupDataFile = new File(playerDataFolderPath + File.separator + "$" + groupName);
            groupDataFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(groupDataFile));

            //first line is number of bonus blocks
            outStream.write(String.valueOf(currentValue));
            outStream.newLine();
        }

        //if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("Unexpected exception saving data for group \"" + groupName + "\": " + e.getMessage());
        }

        try {
            //close the file
            if (outStream != null) {
                outStream.close();
            }
        }
        catch (IOException exception) {}
    }

    @Override
    public synchronized void close() {}

    @Override
    int getSchemaVersionFromStorage() {
        File schemaVersionFile = new File(schemaVersionFilePath);
        if (schemaVersionFile.exists()) {
            BufferedReader inStream = null;
            int schemaVersion = 0;
            try {
                inStream = new BufferedReader(new FileReader(schemaVersionFile.getAbsolutePath()));

                //read the version number
                String line = inStream.readLine();

                //try to parse into an int value
                schemaVersion = Integer.parseInt(line);
            }
            catch (Exception e) {}

            try {
                if (inStream != null) inStream.close();
            }
            catch (IOException exception) {}

            return schemaVersion;
        }
        else {
            this.updateSchemaVersionInStorage(0);
            return 0;
        }
    }

    @Override
    void updateSchemaVersionInStorage(int versionToSet) {
        BufferedWriter outStream = null;

        try {
            //open the file and write the new value
            File schemaVersionFile = new File(schemaVersionFilePath);
            schemaVersionFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(schemaVersionFile));

            outStream.write(String.valueOf(versionToSet));
        }

        //if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("Unexpected exception saving schema version: " + e.getMessage());
        }

        //close the file
        try {
            if (outStream != null) outStream.close();
        }
        catch (IOException exception) {}

    }
}
