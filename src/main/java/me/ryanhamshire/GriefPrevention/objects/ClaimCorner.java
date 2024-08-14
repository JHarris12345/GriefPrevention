package me.ryanhamshire.GriefPrevention.objects;

import me.ryanhamshire.GriefPrevention.data.DataStore;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public class ClaimCorner {

    public World world;
    public int x;
    public int y;
    public int z;

    public ClaimCorner(World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location location() {
        return DataStore.locationFromClaimCorner(this);
    }

    public Chunk chunk() {
        return location().getChunk();
    }
}
