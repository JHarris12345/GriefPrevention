package me.ryanhamshire.GriefPrevention.objects.enums;

public enum ClaimSettingValue {

    TRUE("True"),
    FALSE("False"),
    SUNNY("Sunny"),
    RAINY("Rainy"),
    DAY("Day"),
    NIGHT("Night"),
    NONE("None");

    private final String readable;

    ClaimSettingValue(String readable) {
        this.readable = readable;
    }

    public String getReadable() {
        return readable;
    }
}
