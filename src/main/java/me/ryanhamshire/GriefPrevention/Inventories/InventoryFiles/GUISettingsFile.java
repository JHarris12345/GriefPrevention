package me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import static org.bukkit.Bukkit.getLogger;

public class GUISettingsFile {
    private static File file;
    private static YamlConfiguration customFile;
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void setup(){
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/Inventory-Settings.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try{
                file.createNewFile();
                customFile = new YamlConfiguration();
                customFile.set("Background.Item", "GRAY_STAINED_GLASS_PANE");
                customFile.set("BackButton.Item", "PLAYER_HEAD");
                customFile.set("BackButton.Base64Value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==");
                customFile.set("BackButton.Name", "&cBack");
                customFile.set("PageBackButton.Item", "STONE_BUTTON");
                customFile.set("PageBackButton.Name", "&6Previous Page");
                customFile.set("PageForwardButton.Item", "STONE_BUTTON");
                customFile.set("PageForwardButton.Name", "&aNext Page");
                customFile.save(file);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get(){
        return customFile;
    }

    public static void reload(){
        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static void save(){
        try{
            customFile.save(file);
        }catch (IOException e){
            getLogger().info("Couldn't save file.");
        }
    }
}
