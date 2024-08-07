package me.ryanhamshire.GriefPrevention.objects.enums;

public enum ClaimRole {

    OWNER,
    MANAGER,
    MEMBER,
    GUEST,
    PUBLIC;

    public static boolean isRole1HigherThanRole2(ClaimRole role1, ClaimRole role2) {
        ClaimRole[] claimRoles = {PUBLIC, GUEST, MEMBER, MANAGER, OWNER};

        int role1Index = 0;
        int role2Index = 0;

        int index = 0;
        for (ClaimRole role : claimRoles) {
            if (role == role1) role1Index = index;
            if (role == role2) role2Index = index;

            index++;
        }

        return (role1Index > role2Index);
    }

    public static ClaimRole getHigherRole(ClaimRole claimRole) {
        ClaimRole[] claimRoles = {PUBLIC, GUEST, MEMBER, MANAGER, OWNER};

        int currentIndex = 0;
        for (ClaimRole role : claimRoles) {
            if (role == claimRole) break;
            currentIndex++;
        }

        if (currentIndex == 4) {
            return null;
        } else {
            return claimRoles[currentIndex + 1];
        }
    }

    public static ClaimRole getLowerRole(ClaimRole claimRole) {
        ClaimRole[] claimRoles = {PUBLIC, GUEST, MEMBER, MANAGER, OWNER};

        int currentIndex = 0;
        for (ClaimRole role : claimRoles) {
            if (role == claimRole) break;
            currentIndex++;
        }

        if (currentIndex == 0) {
            return null;
        } else {
            return claimRoles[currentIndex - 1];
        }
    }
}
