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
        return r.hasCenter && r.hasEdge && r.radius > 0.0 && r.dimension != null;
    }

    public static boolean isSquare() {
        if (config == null || config.state().region == null) return false;
        return "square".equalsIgnoreCase(config.state().region.shape);
    }

    public static boolean contains(ServerLevel level, double x, double z) {
        if (!enabled()) return false;
        SpawnConfig.Region r = config.state().region;
        if (!dimension(level).equals(r.dimension)) return false;
        double dx = Math.abs(x - r.centerX);
        double dz = Math.abs(z - r.centerZ);
        if (isSquare()) {
            return dx <= r.radius && dz <= r.radius;
        }
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
        double dx = Math.abs(x - r.centerX);
        double dz = Math.abs(z - r.centerZ);
        double reach = r.radius + Math.max(0.0F, blastRadius);
        if (isSquare()) {
            return dx <= reach && dz <= reach;
        }
        return dx * dx + dz * dz <= reach * reach;
    }

    static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
