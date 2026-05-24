package com.almostkoi.lightning.conductor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.joml.Vector3d;
public class VerletChain {
    private final Vector3d[] positions;
    private final Vector3d[] oldPositions;
    private final boolean[] pinned;
    private final int nodeCount;
    private final double restLength;
    private final World world;
    private static final Vector3d GRAVITY = new Vector3d(0.0, -0.04, 0.0);
    private static final double DAMPING = 0.98;
    public VerletChain(Location start, Location end, int nodeCount, World world) {
        this.nodeCount = Math.max(2, nodeCount);
        this.world = world;
        this.positions = new Vector3d[this.nodeCount];
        this.oldPositions = new Vector3d[this.nodeCount];
        this.pinned = new boolean[this.nodeCount];
        double totalDistance = start.distance(end);
        this.restLength = totalDistance / (this.nodeCount - 1);
        Vector3d s = toVec(start);
        Vector3d e = toVec(end);
        for (int i = 0; i < this.nodeCount; i++) {
            double t = (double) i / (this.nodeCount - 1);
            positions[i] = new Vector3d(s).lerp(e, t);
            oldPositions[i] = new Vector3d(positions[i]);
        }
        pinned[0] = true;
        pinned[this.nodeCount - 1] = true;
    }
    public void pinStart(Location loc) {
        Vector3d v = toVec(loc);
        positions[0].set(v);
        oldPositions[0].set(v);
    }
    public void pinEnd(Location loc) {
        Vector3d v = toVec(loc);
        positions[nodeCount - 1].set(v);
        oldPositions[nodeCount - 1].set(v);
    }
    public void simulate(int constraintIterations) {
        for (int i = 0; i < nodeCount; i++) {
            if (pinned[i]) continue;
            Vector3d velocity = new Vector3d(positions[i]).sub(oldPositions[i]);
            velocity.mul(DAMPING);
            oldPositions[i].set(positions[i]);
            positions[i].add(velocity).add(GRAVITY);
        }
        for (int iter = 0; iter < constraintIterations; iter++) {
            for (int i = 0; i < nodeCount - 1; i++) {
                Vector3d delta = new Vector3d(positions[i + 1]).sub(positions[i]);
                double currentLength = delta.length();
                if (currentLength < 1e-6) continue;
                double error = (currentLength - restLength) / currentLength;
                Vector3d correction = new Vector3d(delta).mul(error * 0.5);
                if (!pinned[i]) {
                    positions[i].add(correction);
                }
                if (!pinned[i + 1]) {
                    positions[i + 1].sub(correction);
                }
            }
        }
        for (int i = 0; i < nodeCount; i++) {
            if (pinned[i]) continue;
            resolveCollision(i);
        }
    }
    private void resolveCollision(int index) {
        Vector3d pos = positions[index];
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        if (by < world.getMinHeight() || by >= world.getMaxHeight()) return;
        Block block = world.getBlockAt(bx, by, bz);
        if (block.getType().isSolid()) {
            pos.y = by + 1.01; 
            oldPositions[index].y = pos.y;
        }
    }
    public Vector3d[] getPositions() {
        return positions;
    }
    public int getNodeCount() {
        return nodeCount;
    }
    public double getRestLength() {
        return restLength;
    }
    private static Vector3d toVec(Location loc) {
        return new Vector3d(loc.getX(), loc.getY(), loc.getZ());
    }
}