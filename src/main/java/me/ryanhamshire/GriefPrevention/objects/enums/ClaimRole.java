package me.ryanhamshire.GriefPrevention.objects.enums;

public enum ClaimRole {

    OWNER(20, "Owner"),
    MANAGER(15, "Manager"),
    MEMBER(10, "Member"),
    GUEST(5, "Guest"),
    PUBLIC(0, "Public");

    private final int numericalPriority; // Higher priority = higher role
    private final String readable;

    ClaimRole(int numericalPriority, String readable) {
        this.numericalPriority = numericalPriority;
        this.readable = readable;
    }

    public String readable() {
        return readable;
    }

    public int numericalPriority() {
        return numericalPriority;
    }

    public static boolean isRole1HigherThanRole2(ClaimRole role1, ClaimRole role2) {
        return role1.numericalPriority > role2.numericalPriority;

        /*ClaimRole[] claimRoles = {PUBLIC, GUEST, MEMBER, MANAGER, OWNER};

        int role1Index = 0;
        int role2Index = 0;

        int index = 0;
        for (ClaimRole role : claimRoles) {
            if (role == role1) role1Index = index;
            if (role == role2) role2Index = index;

            index++;
        }

        return (role1Index > role2Index);*/
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
