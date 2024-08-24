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

public class SettingsGUIFile {
    private static File file;
    private static YamlConfiguration customFile;
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void setup() {
        File folderDir = new File(DataStore.dataLayerFolderPath, "Inventories");
        file = new File(folderDir.getAbsolutePath() + "/Settings.yml");

        if (!file.exists()) {
            folderDir.mkdirs();

            try {
                file.createNewFile();
                customFile = new YamlConfiguration();

                customFile.set("Title", "&0Claim Settings");

                customFile.set("EnabledLore",  new ArrayList<>(Arrays.asList("&7", "&a&lEnabled", "&aClick to disable")));
                customFile.set("DisabledLore", new ArrayList<>(Arrays.asList("&7", "&c&lDisabled", "&cClick to enable")));

                customFile.set("Settings.NATURAL_MONSTER_SPAWNS.Name", "&bNatural Monster Spawns");
                customFile.set("Settings.NATURAL_MONSTER_SPAWNS.Permission", "group.infinite");
                customFile.set("Settings.NATURAL_MONSTER_SPAWNS.Item", "CREEPER_HEAD");
                customFile.set("Settings.NATURAL_MONSTER_SPAWNS.Lore", new ArrayList<>(Arrays.asList("&7Hostile mobs will not spawn", "&7with this set to disabled")));

                customFile.set("Settings.NATURAL_ANIMAL_SPAWNS.Name", "&bNatural Animal Spawns");
                customFile.set("Settings.NATURAL_ANIMAL_SPAWNS.Permission", "group.empyrean");
                customFile.set("Settings.NATURAL_ANIMAL_SPAWNS.Item", "PLAYER_HEAD");
                customFile.set("Settings.NATURAL_ANIMAL_SPAWNS.Base64Value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjY2N2MwZTEwN2JlNzlkNzY3OWJmZTg5YmJjNTdjNmJmMTk4ZWNiNTI5YTMyOTVmY2ZkZmQyZjI0NDA4ZGNhMyJ9fX0=");
                customFile.set("Settings.NATURAL_ANIMAL_SPAWNS.Lore", new ArrayList<>(Arrays.asList("&7Animals mobs will not spawn", "&7with this set to disabled")));
                
                customFile.set("Settings.LEAF_DECAY.Name", "&bLeaf Decay");
                customFile.set("Settings.LEAF_DECAY.Permission", "group.chancellor");
                customFile.set("Settings.LEAF_DECAY.Item", "OAK_LEAVES");
                customFile.set("Settings.LEAF_DECAY.Lore", new ArrayList<>(Arrays.asList("&7Leaves will not decay", "&7with this set to disabled")));

                customFile.set("Settings.ICE_FORM.Name", "&bIce Form");
                customFile.set("Settings.ICE_FORM.Permission", "group.legend");
                customFile.set("Settings.ICE_FORM.Item", "ICE");
                customFile.set("Settings.ICE_FORM.Lore", new ArrayList<>(Arrays.asList("&7Ice will not form with", "&7this set to disabled")));

                customFile.set("Settings.ICE_MELT.Name", "&bIce Melt");
                customFile.set("Settings.ICE_MELT.Permission", "group.chancellor");
                customFile.set("Settings.ICE_MELT.Item", "BLUE_ICE");
                customFile.set("Settings.ICE_MELT.Lore", new ArrayList<>(Arrays.asList("&7Ice will not melt with", "&7this set to disabled")));

                customFile.set("Settings.SNOW_FORM.Name", "&bSnow Form");
                customFile.set("Settings.SNOW_FORM.Permission", "group.legend");
                customFile.set("Settings.SNOW_FORM.Item", "SNOWBALL");
                customFile.set("Settings.SNOW_FORM.Lore", new ArrayList<>(Arrays.asList("&7Snow will not form with", "&7this set to disabled")));

                customFile.set("Settings.SNOW_MELT.Name", "&bSnow Melt");
                customFile.set("Settings.SNOW_MELT.Permission", "group.chancellor");
                customFile.set("Settings.SNOW_MELT.Item", "SNOW_BLOCK");
                customFile.set("Settings.SNOW_MELT.Lore", new ArrayList<>(Arrays.asList("&7Snow will not melt with", "&7this set to disabled")));
                
                customFile.set("Settings.GRASS_SPREAD.Name", "&bGrass Spread");
                customFile.set("Settings.GRASS_SPREAD.Permission", "group.legend");
                customFile.set("Settings.GRASS_SPREAD.Item", "GRASS_BLOCK");
                customFile.set("Settings.GRASS_SPREAD.Lore", new ArrayList<>(Arrays.asList("&7Grass will not spread with", "&7this set to disabled")));

                customFile.set("Settings.VINE_GROWTH.Name", "&bVine Growth");
                customFile.set("Settings.VINE_GROWTH.Permission", "group.empyrean");
                customFile.set("Settings.VINE_GROWTH.Item", "VINE");
                customFile.set("Settings.VINE_GROWTH.Lore", new ArrayList<>(Arrays.asList("&7Vines will not grow with", "&7this set to disabled")));

                customFile.set("Settings.FORCED_WEATHER.Name", "&bForced Weather");
                customFile.set("Settings.FORCED_WEATHER.Permission", "group.empyrean");
                customFile.set("Settings.FORCED_WEATHER.Item", "SUNFLOWER");
                customFile.set("Settings.FORCED_WEATHER.Lore", new ArrayList<>(Arrays.asList("&7Players in this claim will be", "&7set to have this weather", "", "&bForced Weather: &f%forcedWeather%", "&7Click to change")));

                customFile.set("Settings.FORCED_TIME.Name", "&bForced Time");
                customFile.set("Settings.FORCED_TIME.Permission", "group.infinite");
                customFile.set("Settings.FORCED_TIME.Item", "CLOCK");
                customFile.set("Settings.FORCED_TIME.Lore", new ArrayList<>(Arrays.asList("&7Players in this claim will be", "&7set to have this time", "", "&bForced Time: &f%forcedTime%", "&7Click to change")));

                customFile.set("Settings.COPPER_WEATHERING.Name", "&bCopper Weathering");
                customFile.set("Settings.COPPER_WEATHERING.Permission", "group.infinite");
                customFile.set("Settings.COPPER_WEATHERING.Item", "WEATHERED_COPPER");
                customFile.set("Settings.COPPER_WEATHERING.Lore", new ArrayList<>(Arrays.asList("&7Copper will not weather with", "&7this set to disabled")));

                customFile.set("Settings.CORAL_DRY.Name", "&bCoral Drying");
                customFile.set("Settings.CORAL_DRY.Permission", "group.empyrean");
                customFile.set("Settings.CORAL_DRY.Item", "TUBE_CORAL");
                customFile.set("Settings.CORAL_DRY.Lore", new ArrayList<>(Arrays.asList("&7Coral will not dry up without water", "&7with this set to disabled")));

                customFile.set("Settings.CROP_GROWTH.Name", "&bCrop Growth");
                customFile.set("Settings.CROP_GROWTH.Permission", "group.infinite");
                customFile.set("Settings.CROP_GROWTH.Item", "WHEAT");
                customFile.set("Settings.CROP_GROWTH.Lore", new ArrayList<>(Arrays.asList("&7Crops and plants will not grow", "&7with this set to disabled")));

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
