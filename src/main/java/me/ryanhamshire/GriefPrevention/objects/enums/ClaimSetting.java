package me.ryanhamshire.GriefPrevention.objects.enums;

import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.RolePermissionsGUIFile;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.SettingsGUIFile;

public enum ClaimSetting {

    FLUID_FLOW(ClaimSettingValue.TRUE),
    SNOW_MELT(ClaimSettingValue.TRUE),
    ICE_MELT(ClaimSettingValue.TRUE),
    SNOW_FORM(ClaimSettingValue.TRUE),
    ICE_FORM(ClaimSettingValue.TRUE),
    LEAF_DECAY(ClaimSettingValue.TRUE),
    GRASS_SPREAD(ClaimSettingValue.TRUE),
    MYCELIUM_SPREAD(ClaimSettingValue.TRUE),
    SCULK_SPREAD(ClaimSettingValue.TRUE),
    CROP_GROWTH(ClaimSettingValue.TRUE),
    COPPER_WEATHERING(ClaimSettingValue.TRUE),
    NATURAL_MONSTER_SPAWNS(ClaimSettingValue.TRUE),
    FORCED_TIME(ClaimSettingValue.NONE),
    FORCED_WEATHER(ClaimSettingValue.NONE),
    NATURAL_ANIMAL_SPAWNS(ClaimSettingValue.TRUE),
    VINE_GROWTH(ClaimSettingValue.TRUE),
    CORAL_DRY(ClaimSettingValue.TRUE),
    CONCRETE_FORMING(ClaimSettingValue.TRUE),
    ;

    private final ClaimSettingValue settingDefaultValue;

    ClaimSetting(ClaimSettingValue settingDefaultValue) {this.settingDefaultValue = settingDefaultValue;}

    public ClaimSettingValue getDefaultValue() {
        return settingDefaultValue;
    }

    public int getUnlockCost() {
        return (SettingsGUIFile.get().getInt("Settings." + this.name() + ".iCoins"));
    }

    public String getUnlockPermission() {
        return (SettingsGUIFile.get().getString("Settings." + this.name() + ".Permission"));
    }
}
