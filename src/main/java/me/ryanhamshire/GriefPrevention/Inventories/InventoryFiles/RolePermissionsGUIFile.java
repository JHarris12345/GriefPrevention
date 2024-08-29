package me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.bukkit.Bukkit.getLogger;

public class RolePermissionsGUIFile {
    private static File file;
    private static YamlConfiguration customFile;
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void setup() {
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/RolePermissions.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try {
                file.createNewFile();
                customFile = new YamlConfiguration();

                customFile.set("Buttons.Enabled.Item", "LIME_STAINED_GLASS_PANE");
                customFile.set("Buttons.Disabled.Item", "RED_STAINED_GLASS_PANE");

                customFile.set("Permissions.BREAK_BLOCKS.Name", "&bBreak Blocks");
                customFile.set("Permissions.BREAK_BLOCKS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to break", "&7blocks in your claim")));

                customFile.set("Permissions.PLACE_BLOCKS.Name", "&bPlace Blocks");
                customFile.set("Permissions.PLACE_BLOCKS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to place", "&7blocks in your claim")));

                customFile.set("Permissions.INTERACT.Name", "&bInteract");
                customFile.set("Permissions.INTERACT.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to interact with your claim (flip", "&7switches, click buttons, most right-click actions etc)")));

                customFile.set("Permissions.VILLAGER_TRADE.Name", "&bVillager Trading");
                customFile.set("Permissions.VILLAGER_TRADE.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to trade with", "&7villagers in your claim")));

                customFile.set("Permissions.MODIFY_ITEM_FRAMES.Name", "&bItem Frame Interaction");
                customFile.set("Permissions.MODIFY_ITEM_FRAMES.iCoins", 25);
                customFile.set("Permissions.MODIFY_ITEM_FRAMES.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to interact with", "&7item frames in your claim")));

                customFile.set("Permissions.MODIFY_ARMOR_STANDS.Name", "&bArmor Stand Modification");
                customFile.set("Permissions.MODIFY_ARMOR_STANDS.iCoins", 25;
                customFile.set("Permissions.MODIFY_ARMOR_STANDS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to modify", "&7armor stands in your claim")));

                customFile.set("Permissions.ARMOR_STAND_EDITING.Name", "&bArmor Stand Editing");
                customFile.set("Permissions.ARMOR_STAND_EDITING.iCoins", 25);
                customFile.set("Permissions.ARMOR_STAND_EDITING.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to interact with armor", "&7stands using &f/aa&7 in your claim")));

                customFile.set("Permissions.CONTAINER_ACCESS.Name", "&bContainer Access");
                customFile.set("Permissions.CONTAINER_ACCESS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to open", "&7containers in your claim")));

                customFile.set("Permissions.HURT_ANIMALS.Name", "&bHurt Animals");
                customFile.set("Permissions.HURT_ANIMALS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to hurt", "&7animals in your claim")));

                customFile.set("Permissions.BREED_ANIMALS.Name", "&bBreed Animals");
                customFile.set("Permissions.BREED_ANIMALS.iCoins", 25);
                customFile.set("Permissions.BREED_ANIMALS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to breed", "&7animals in your claim")));

                customFile.set("Permissions.READ_LECTERNS.Name", "&bRead Lecterns");
                customFile.set("Permissions.READ_LECTERNS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to read", "&7lecterns in your claim")));

                customFile.set("Permissions.TRUST_UNTRUST.Name", "&bTrust Management");
                customFile.set("Permissions.TRUST_UNTRUST.iCoins", 25);
                customFile.set("Permissions.TRUST_UNTRUST.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to trust and", "&7untrust members from your claim")));

                customFile.set("Permissions.PROMOTE_DEMOTE.Name", "&bManage Roles");
                customFile.set("Permissions.PROMOTE_DEMOTE.iCoins", 25);
                customFile.set("Permissions.PROMOTE_DEMOTE.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to promote and", "&7demote members in your claim")));

                customFile.set("Permissions.MANAGE_PERMISSIONS.Name", "&bPermission Management");
                customFile.set("Permissions.MANAGE_PERMISSIONS.iCoins", 25);
                customFile.set("Permissions.MANAGE_PERMISSIONS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to modify", "&7permissions for your claim")));

                customFile.set("Permissions.MANAGE_SETTINGS.Name", "&bSettings Management");
                customFile.set("Permissions.MANAGE_SETTINGS.iCoins", 25);
                customFile.set("Permissions.MANAGE_SETTINGS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to change", "&7your claim's settings")));

                customFile.set("Permissions.CHORUS_FRUIT_TELEPORT.Name", "&bChorus Fruit Teleport");
                customFile.set("Permissions.CHORUS_FRUIT_TELEPORT.iCoins", 25);
                customFile.set("Permissions.CHORUS_FRUIT_TELEPORT.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to teleport via", "&7chorus fruits in your claim")));

                customFile.set("Permissions.THRU_ACCESS.Name", "&b/thru Access");
                customFile.set("Permissions.THRU_ACCESS.iCoins", 25);
                customFile.set("Permissions.THRU_ACCESS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to use", "&7/thru in your claim")));

                customFile.set("Permissions.SET_WARP_ACCESS.Name", "&bSet Warp Access");
                customFile.set("Permissions.SET_WARP_ACCESS.iCoins", 25);
                customFile.set("Permissions.SET_WARP_ACCESS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to set", "&7warps in your claim")));

                customFile.set("Permissions.SET_HOME_ACCESS.Name", "&bSet Home Access");
                customFile.set("Permissions.SET_HOME_ACCESS.iCoins", 25);
                customFile.set("Permissions.SET_HOME_ACCESS.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to set", "&7homes in your claim")));

                customFile.set("Permissions.MODIFY.Name", "&bClaim Modification");
                customFile.set("Permissions.MODIFY.iCoins", 25);
                customFile.set("Permissions.MODIFY.Lore", new ArrayList<>(Arrays.asList("&7Toggle the ability to expand and", "&7/ or resize your claim")));

                customFile.save(file);
            } catch (IOException e) {
                getLogger().info("Couldn't create.");
            }
        }

        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return customFile;
    }

    public static void reload() {
        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static void save() {
        try {
            customFile.save(file);
        } catch (IOException e) {
            getLogger().info("Couldn't save file.");
        }
    }
}

