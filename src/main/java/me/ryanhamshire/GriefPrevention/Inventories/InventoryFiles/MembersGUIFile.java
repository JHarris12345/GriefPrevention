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

public class MembersGUIFile {
    private static File file;
    private static YamlConfiguration customFile;

    public static void setup(){
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/Members.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try{
                file.createNewFile();

                customFile = new YamlConfiguration();
                customFile.set("Title", "&0Claim Members");
                customFile.set("Head.Name", "&b%name%");
                customFile.set("Head.Lore", new ArrayList<>(Arrays.asList("&bRole: &f%role%",
                        "",
                        "&7Left-Click to promote this player",
                        "&7Right-Click to demote this player")));
                customFile.save(file);

            } catch(IOException e){
                e.printStackTrace();
            }
        }

        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get(){
        return customFile;
    }
}
