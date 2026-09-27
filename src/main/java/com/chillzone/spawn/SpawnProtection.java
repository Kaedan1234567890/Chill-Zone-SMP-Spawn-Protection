package com.chillzone.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class SpawnProtection {
    private static SpawnConfig config;

    private SpawnProtection() {}

    static void bind(SpawnConfig value) { config = value; }

    public static boolean enabled() {
        return config != null && config.state().protectionEnabled && ready();
    }

    public static boolean ready() {
        if (config == null || config.state().region == null) return false;
        SpawnConfig.Region r = config.state().region;
        return r.hasCenter && r.hasEdge && r.dimension != null && (isSquare() || r.radius > 0.0);
    }

    public static boolean isSquare() {
        if (config == null || config.state().region == null) return false;
        return "square".equalsIgnoreCase(config.state().region.shape);
    }

    public static boolean contains(ServerLevel level, double x, double z) {
        if (!enabled()) return false;
        SpawnConfig.Region r = config.state().region;
        if (!dimension(level).equals(r.dimension)) return false;
        if (isSquare()) {
            // For square mode, pos1 and pos2 are opposite block corners. The region
            // includes the full selected blocks, so the boundary lines up exactly with
            // block edges rather than with the player's fractional standing position.
            double minX = Math.min(r.centerX, r.edgeX) - 0.5;
            double maxX = Math.max(r.centerX, r.edgeX) + 0.5;
            double minZ = Math.min(r.centerZ, r.edgeZ) - 0.5;
            double maxZ = Math.max(r.centerZ, r.edgeZ) + 0.5;
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }
        double dx = x - r.centerX;
        double dz = z - r.centerZ;
        return dx * dx + dz * dz <= r.radius * r.radius;
    }

    public static boolean contains(ServerLevel level, BlockPos pos) {
        return contains(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean contains(Entity entity) {
        return entity.level() instanceof ServerLevel level && contains(level, entity.getX(), entity.getZ());
    }

    public static boolean explosionTouches(ServerLevel level, double x, double z, float blastRadius) {
        if (!enabled()) return false;
        SpawnConfig.Region r = config.state().region;
        if (!dimension(level).equals(r.dimension)) return false;
        double blast = Math.max(0.0F, blastRadius);
        if (isSquare()) {
            double minX = Math.min(r.centerX, r.edgeX) - 0.5 - blast;
            double maxX = Math.max(r.centerX, r.edgeX) + 0.5 + blast;
            double minZ = Math.min(r.centerZ, r.edgeZ) - 0.5 - blast;
            double maxZ = Math.max(r.centerZ, r.edgeZ) + 0.5 + blast;
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
        double dx = x - r.centerX;
        double dz = z - r.centerZ;
        double reach = r.radius + blast;
        return dx * dx + dz * dz <= reach * reach;
    }

    static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
