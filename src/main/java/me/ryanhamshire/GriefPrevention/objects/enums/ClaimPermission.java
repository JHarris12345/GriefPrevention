package me.ryanhamshire.GriefPrevention.objects.enums;

public enum ClaimPermission {

    PLACE_BLOCKS("&cYou don't have permission to build on this claim"),
    BREAK_BLOCKS("&cYou don't have permission to build on this claim"),
    MANAGE_PERMISSIONS("&cYou don't have permission to manage permissions for this claim"),
    MANAGE_SETTINGS("&cYou don't have permission manage settings for this claim"),
    INTERACT("&cYou don't have permission to interact with this claim"),
    CONTAINER_ACCESS("&cYou don't have permission to access containers on this claim"),
    ARMOR_STAND_EDITING("&cYou don't have permission to edit armor stands on this claim"),
    HURT_ANIMALS("&cYou don't have permission to hurt animals on this claim"),
    READ_LECTERNS("&cYou don't have permission to read lecterns on this claim"),
    BREED_ANIMALS("&cYou don't have permission to breed animals on this claim"),
    TRUST_UNTRUST("&cYou don't have permission to manage trust for this claim"),
    PROMOTE_DEMOTE("&cYou don't have permission to manage roles for this claim"),
    CHORUS_FRUIT_TELEPORT("&cYou don't have permission to teleport via chorus fruits on this claim"),
    THRU_ACCESS("&cYou don't have permission to use //thru on this claim"),
    MODIFY("&cYou don't have permission to modify this claim");

    private final String denialMessage; // The message sent when a player doesn't have permission for that ClaimPermission

    ClaimPermission(String denialMessage) {
        this.denialMessage = denialMessage;
    }

    public String getDenialMessage() {
        return denialMessage;
    }
}
