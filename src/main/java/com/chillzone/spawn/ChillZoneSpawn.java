package com.chillzone.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Set;

public final class ChillZoneSpawn implements ModInitializer {
    private static final SpawnConfig CONFIG = new SpawnConfig();
    private static int mobSweepTicks;

    @Override
    public void onInitialize() {
        CONFIG.load();
        SpawnProtection.bind(CONFIG);
        registerCommands();

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) return true;
            if (!SpawnProtection.contains(serverLevel, pos)) return true;
            return false;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && SpawnProtection.contains(player)) {
                return false;
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            updatePlayerZoneMessages(server);
            if (++mobSweepTicks >= 5) {
                mobSweepTicks = 0;
                removeMobsInside(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CONFIG.save());
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var spawn = Commands.literal("spawn")
                    .executes(ctx -> teleportSpawn(ctx.getSource()));

            spawn.then(Commands.literal("set")
                    .requires(Permissions::canAdmin)
                    .executes(ctx -> setSpawn(ctx.getSource())));

            spawn.then(Commands.literal("protection")
                    .requires(Permissions::canAdmin)
                    .then(Commands.literal("on").executes(ctx -> setProtection(ctx.getSource(), true)))
                    .then(Commands.literal("off").executes(ctx -> setProtection(ctx.getSource(), false))));

            spawn.then(Commands.literal("region")
                    .requires(Permissions::canAdmin)
                    .then(Commands.literal("pos1").executes(ctx -> setRegionCenter(ctx.getSource())))
                    .then(Commands.literal("pos2").executes(ctx -> setRegionEdge(ctx.getSource())))
                    .then(Commands.literal("show").executes(ctx -> showRegion(ctx.getSource())))
                    .then(Commands.literal("clear").executes(ctx -> clearRegion(ctx.getSource()))));

            dispatcher.register(spawn);
        });
    }

    private static int setSpawn(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Run /spawn set as a player.")); return 0; }

        SpawnConfig.SpawnPoint p = new SpawnConfig.SpawnPoint();
        p.dimension = SpawnProtection.dimension(player.level());
        p.x = player.getX();
        p.y = player.getY();
        p.z = player.getZ();
        p.yaw = player.getYRot();
        p.pitch = player.getXRot();
        CONFIG.state().spawn = p;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn point saved at your exact position and facing direction."), false);
        return 1;
    }

    private static int teleportSpawn(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only a player can use /spawn.")); return 0; }

        SpawnConfig.SpawnPoint p = CONFIG.state().spawn;
        if (p == null) {
            source.sendFailure(Component.literal("Spawn has not been set yet. An OP must run /spawn set."));
            return 0;
        }

        ServerLevel target = findLevel(source.getServer(), p.dimension);
        if (target == null) {
            source.sendFailure(Component.literal("The saved spawn dimension is unavailable."));
            return 0;
        }

        player.resetFallDistance();
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.teleportTo(target, p.x, p.y, p.z, Set.of(), p.yaw, p.pitch, true);
        player.resetFallDistance();
        source.sendSuccess(() -> Component.literal("Teleported to spawn."), false);
        return 1;
    }

    private static int setProtection(CommandSourceStack source, boolean enabled) {
        if (enabled && !SpawnProtection.ready()) {
            source.sendFailure(Component.literal("Set both region points first: /spawn region pos1 then /spawn region pos2."));
            return 0;
        }
        CONFIG.state().protectionEnabled = enabled;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn protection " + (enabled ? "ENABLED" : "DISABLED") + "."), true);
        return 1;
    }

    private static int setRegionCenter(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }

        SpawnConfig.Region r = CONFIG.state().region;
        if (r == null) r = new SpawnConfig.Region();
        r.dimension = SpawnProtection.dimension(player.level());
        r.centerX = player.getX();
        r.centerZ = player.getZ();
        r.hasCenter = true;
        r.hasEdge = false;
        r.radius = 0.0;
        CONFIG.state().region = r;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Safe-zone center set here. Y is ignored; the zone is vertical from world bottom to top."), false);
        return 1;
    }

    private static int setRegionEdge(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }

        SpawnConfig.Region r = CONFIG.state().region;
        if (r == null || !r.hasCenter) {
            source.sendFailure(Component.literal("Set /spawn region pos1 first."));
            return 0;
        }
        String dimension = SpawnProtection.dimension(player.level());
        if (!dimension.equals(r.dimension)) {
            source.sendFailure(Component.literal("pos2 must be in the same dimension as pos1."));
            return 0;
        }
        r.edgeX = player.getX();
        r.edgeZ = player.getZ();
        double dx = r.edgeX - r.centerX;
        double dz = r.edgeZ - r.centerZ;
        r.radius = Math.sqrt(dx * dx + dz * dz);
        r.hasEdge = r.radius >= 1.0;
        CONFIG.save();
        if (!r.hasEdge) {
            source.sendFailure(Component.literal("The edge must be at least 1 block from the center."));
            return 0;
        }
        double radius = r.radius;
        source.sendSuccess(() -> Component.literal(String.format("Safe-zone radius set to %.1f blocks. It extends through the entire world height.", radius)), false);
        return 1;
    }

    private static int showRegion(CommandSourceStack source) {
        SpawnConfig.Region r = CONFIG.state().region;
        if (r == null || !r.hasCenter) {
            source.sendFailure(Component.literal("No spawn region has been defined."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Spawn region: center %.1f, %.1f | radius %.1f | vertical cylinder | protection %s",
                r.centerX, r.centerZ, r.radius, CONFIG.state().protectionEnabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int clearRegion(CommandSourceStack source) {
        CONFIG.state().protectionEnabled = false;
        CONFIG.state().region = null;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn region cleared and protection disabled."), true);
        return 1;
    }

    private static void updatePlayerZoneMessages(MinecraftServer server) {
        if (!SpawnProtection.enabled()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SpawnProtection.contains(player)) {
                player.displayClientMessage(
                        Component.literal("🛡 SAFE ZONE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal("⚔ PVP ZONE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true
                );
            }
        }
    }

    private static void removeMobsInside(MinecraftServer server) {
        if (!SpawnProtection.enabled()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob && SpawnProtection.contains(level, entity.getX(), entity.getZ())) {
                    entity.discard();
                }
            }
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (SpawnProtection.dimension(level).equals(id)) return level;
        }
        return null;
    }
}
