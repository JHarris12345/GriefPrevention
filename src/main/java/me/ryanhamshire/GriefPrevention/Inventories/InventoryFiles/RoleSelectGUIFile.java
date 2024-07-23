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

public class RoleSelectGUIFile {
    private static File file;
    private static YamlConfiguration customFile;
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void setup() {
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/RoleSelect.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try {
                file.createNewFile();
                customFile = new YamlConfiguration();
                customFile.set("Title", "&0Role Permissions");
                customFile.set("Size", 27);

                customFile.set("Roles.OWNER.Item", "LIGHT_BLUE_STAINED_GLASS_PANE");
                customFile.set("Roles.OWNER.Name", "&bOwner");
                customFile.set("Roles.OWNER.Lore", new ArrayList<>(Arrays.asList("&7Click to view Owner permissions")));
                customFile.set("Roles.OWNER.Slot", 11);
                customFile.set("Roles.OWNER.GUIName", "&0Owner Permissions");

                customFile.set("Roles.MANAGER.Item", "LIGHT_BLUE_STAINED_GLASS_PANE");
                customFile.set("Roles.MANAGER.Name", "&bManager");
                customFile.set("Roles.MANAGER.Lore", new ArrayList<>(Arrays.asList("&7Click to edit Manager permissions")));
                customFile.set("Roles.MANAGER.Slot", 12);
                customFile.set("Roles.MANAGER.GUIName", "&0Manager Permissions");

                customFile.set("Roles.MEMBER.Item", "LIGHT_BLUE_STAINED_GLASS_PANE");
                customFile.set("Roles.MEMBER.Name", "&bMember");
                customFile.set("Roles.MEMBER.Lore", new ArrayList<>(Arrays.asList("&7Click to edit Member permissions")));
                customFile.set("Roles.MEMBER.Slot", 13);
                customFile.set("Roles.MEMBER.GUIName", "&0Member Permissions");

                customFile.set("Roles.GUEST.Item", "LIGHT_BLUE_STAINED_GLASS_PANE");
                customFile.set("Roles.GUEST.Name", "&bGuest");
                customFile.set("Roles.GUEST.Lore", new ArrayList<>(Arrays.asList("&7Click to edit Guest permissions")));
                customFile.set("Roles.GUEST.Slot", 14);
                customFile.set("Roles.GUEST.GUIName", "&0Guest Permissions");

                customFile.set("Roles.PUBLIC.Item", "LIGHT_BLUE_STAINED_GLASS_PANE");
                customFile.set("Roles.PUBLIC.Name", "&bPublic");
                customFile.set("Roles.PUBLIC.Lore", new ArrayList<>(Arrays.asList("&7Click to edit Public permissions")));
                customFile.set("Roles.PUBLIC.Slot", 15);
                customFile.set("Roles.PUBLIC.GUIName", "&0Public Permissions");

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

    public static void save() {
        try {
            customFile.save(file);
        } catch (IOException e) {
            getLogger().info("Couldn't save file.");
        }
    }
}
