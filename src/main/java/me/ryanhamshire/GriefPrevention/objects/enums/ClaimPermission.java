package me.ryanhamshire.GriefPrevention.objects.enums;

public enum ClaimPermission {

    PLACE_BLOCKS("&cYou don't have permission to build on this claim", false, true, true, true),
    BREAK_BLOCKS("&cYou don't have permission to build on this claim", false, true, true, true),
    MANAGE_PERMISSIONS("&cYou don't have permission to manage permissions for this claim", false, false, false, true),
    MANAGE_SETTINGS("&cYou don't have permission manage settings for this claim", false, false, false, true),
    INTERACT("&cYou don't have permission to interact with this claim", false, true, true, true),
    CONTAINER_ACCESS("&cYou don't have permission to access containers on this claim", false, true, true, true),
    ARMOR_STAND_EDITING("&cYou don't have permission to edit armor stands on this claim", false, false, true, true),
    HURT_ANIMALS("&cYou don't have permission to hurt animals on this claim", false, true, true, true),
    READ_LECTERNS("&cYou don't have permission to read lecterns on this claim", false, true, true, true),
    BREED_ANIMALS("&cYou don't have permission to breed animals on this claim", false, true, true, true),
    TRUST_UNTRUST("&cYou don't have permission to manage trust for this claim", false, false, false, true),
    PROMOTE_DEMOTE("&cYou don't have permission to manage roles for this claim", false, false, false, true),
    CHORUS_FRUIT_TELEPORT("&cYou don't have permission to teleport via chorus fruits on this claim", false, true, true, true),
    THRU_ACCESS("&cYou don't have permission to use //thru on this claim", false, true, true, true),
    MODIFY("&cYou don't have permission to modify this claim", false, false, false, false);

    private final String denialMessage; // The message sent when a player doesn't have permission for that ClaimPermission

    // The default permission value for the claim roles
    private final boolean defaultPublic;
    private final boolean defaultGuest;
    private final boolean defaultMember;
    private final boolean defaultManager;

    ClaimPermission(String denialMessage, boolean defaultPublic, boolean defaultGuest, boolean defaultMember, boolean defaultManager) {
        this.denialMessage = denialMessage;
        this.defaultPublic = defaultPublic;
        this.defaultGuest = defaultGuest;
        this.defaultMember = defaultMember;
        this.defaultManager = defaultManager;
    }

    public String getDenialMessage() {
        return denialMessage;
    }

    public boolean getDefaultPermission(ClaimRole claimRole) {
        switch (claimRole) {
            case PUBLIC:
                return defaultPublic;

            case GUEST:
                return defaultGuest;

            case MEMBER:
                return defaultMember;

            case MANAGER:
                return defaultManager;

            default:
                return false;
        }
    }
}
