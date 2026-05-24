package com.almostkoi.lightning.conductor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.joml.Vector3d;
import java.util.Random;
public class ChainRenderer {
    private final World world;
    private final int nodeCount;
    private final Random random;
    private final Particle.DustTransition activeDust = new Particle.DustTransition(
            Color.fromRGB(0, 255, 255),
            Color.fromRGB(255, 255, 255),
            2.0f
    );
    private final Particle.DustTransition formingDust = new Particle.DustTransition(
            Color.fromRGB(0, 100, 255),
            Color.fromRGB(0, 255, 255),
            1.0f
    );
    public ChainRenderer(World world, int nodeCount, long seed) {
        this.world = world;
        this.nodeCount = nodeCount;
        this.random = new Random(seed);
    }
    public void draw(Vector3d[] positions, boolean isBoltFiring, int boltIndex) {
        for (int i = 0; i < nodeCount - 1; i++) {
            Vector3d start = positions[i];
            Vector3d end = positions[i + 1];
            boolean isHighlighted = isBoltFiring && i <= boltIndex;
            drawLine(start, end, isHighlighted);
        }
    }
    private void drawLine(Vector3d start, Vector3d end, boolean isHighlighted) {
        double distance = start.distance(end);
        int particleCount = isHighlighted ? (int) (distance * 8) : (int) (distance * 4);
        particleCount = Math.max(2, particleCount);
        double dx = (end.x - start.x) / particleCount;
        double dy = (end.y - start.y) / particleCount;
        double dz = (end.z - start.z) / particleCount;
        for (int i = 0; i < particleCount; i++) {
            double px = start.x + dx * i;
            double py = start.y + dy * i;
            double pz = start.z + dz * i;
            Location loc = new Location(world, px, py, pz);
            if (isHighlighted) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 5, 0.25, 0.25, 0.25, 0, activeDust);
                if (random.nextDouble() < 0.2) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.3, 0.3, 0.3, 0.05);
                }
            } else {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 3, 0.15, 0.15, 0.15, 0, formingDust);
            }
        }
    }
    public void despawnAll() {}
}