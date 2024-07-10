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

public class MenuGUIFile {
    private static File file;
    private static YamlConfiguration customFile;
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void setup(){
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/ClaimMenu.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try{
                file.createNewFile();
                customFile = new YamlConfiguration();
                customFile.set("Title", "&0Claim Menu");
                customFile.set("Size", 27);

                customFile.set("Icons.Members.Slot", 10);
                customFile.set("Icons.Members.Item", "PLAYER_HEAD");
                customFile.set("Icons.Members.HeadOwner", "%claimOwner%");
                customFile.set("Icons.Members.Name", "&bClaim Members");
                customFile.set("Icons.Members.Lore", new ArrayList<>(Arrays.asList("&7View and manage all the trusted", "&7members on this claim")));

                customFile.set("Icons.Permissions.Slot", 14);
                customFile.set("Icons.Permissions.Item", "PLAYER_HEAD");
                customFile.set("Icons.Permissions.Base64Value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTYzMzBhNGEyMmZmNTU4NzFmYzhjNjE4ZTQyMWEzNzczM2FjMWRjYWI5YzhlMWE0YmI3M2FlNjQ1YTRhNGUifX19");
                customFile.set("Icons.Permissions.Name", "&bClaim Permissions");
                customFile.set("Icons.Permissions.Lore", new ArrayList<>(Arrays.asList("&7Manage the permissions for each", "&7role on this claim")));

                customFile.set("Icons.Settings.Slot", 12);
                customFile.set("Icons.Settings.Item", "PLAYER_HEAD");
                customFile.set("Icons.Settings.Base64Value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGI0ODk4YzE0NDI4ZGQyZTQwMTFkOWJlMzc2MGVjNmJhYjUyMWFlNTY1MWY2ZTIwYWQ1MzQxYTBmNWFmY2UyOCJ9fX0=");
                customFile.set("Icons.Settings.Name", "&bClaim Settings");
                customFile.set("Icons.Settings.Lore", new ArrayList<>(Arrays.asList("&7Toggle settings for this claim")));

                customFile.set("Icons.SetName.Slot", 16);
                customFile.set("Icons.SetName.Item", "WRITABLE_BOOK");
                customFile.set("Icons.SetName.Name", "&bClaim Name");
                customFile.set("Icons.SetName.Lore", new ArrayList<>(Arrays.asList("&7Set or change the name for this", "&7claim so it appears unique when", "&7doing &o/claimlist")));

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
}
