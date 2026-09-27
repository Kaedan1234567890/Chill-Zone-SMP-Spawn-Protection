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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChillZoneSpawn implements ModInitializer {
    private static final SpawnConfig CONFIG = new SpawnConfig();
    private static int mobSweepTicks;
    private static final Map<UUID, Boolean> LAST_ZONE_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ZONE_MESSAGE_TICKS = new ConcurrentHashMap<>();
    private static volatile boolean REGION_PREVIEW_ENABLED;
    private static int previewTicks;
    private static final int ZONE_MESSAGE_DURATION_TICKS = 50; // about 2.5 seconds

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
            if (++previewTicks >= 10) {
                previewTicks = 0;
                drawRegionPreview(server);
            }
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

            // Admin-only sound test so the two transition sounds can be verified
            // without repeatedly crossing the safe-zone boundary.
            spawn.then(Commands.literal("soundtest")
                    .requires(Permissions::canAdmin)
                    .then(Commands.literal("safe").executes(ctx -> testZoneSound(ctx.getSource(), true)))
                    .then(Commands.literal("pvp").executes(ctx -> testZoneSound(ctx.getSource(), false))));

            spawn.then(Commands.literal("region")
                    .requires(Permissions::canAdmin)
                    .then(Commands.literal("pos1").executes(ctx -> setRegionCenter(ctx.getSource())))
                    .then(Commands.literal("pos2").executes(ctx -> setRegionEdge(ctx.getSource())))
                    .then(Commands.literal("shape")
                            .then(Commands.literal("circle").executes(ctx -> setRegionShape(ctx.getSource(), "circle")))
                            .then(Commands.literal("square").executes(ctx -> setRegionShape(ctx.getSource(), "square"))))
                    .then(Commands.literal("preview")
                            .then(Commands.literal("on").executes(ctx -> setRegionPreview(ctx.getSource(), true)))
                            .then(Commands.literal("off").executes(ctx -> setRegionPreview(ctx.getSource(), false))))
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
        // Snap selection points to block centers. This makes the protected boundary
        // line up exactly with the selected blocks instead of fractional player coords.
        r.centerX = Math.floor(player.getX()) + 0.5;
        r.centerZ = Math.floor(player.getZ()) + 0.5;
        r.hasCenter = true;
        r.hasEdge = false;
        r.radius = 0.0;
        // Every newly selected region starts as a square by default.
        r.shape = "square";
        CONFIG.state().region = r;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn region pos1 set to this block. Default shape is SQUARE; Y is ignored."), false);
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
        r.edgeX = Math.floor(player.getX()) + 0.5;
        r.edgeZ = Math.floor(player.getZ()) + 0.5;
        double dx = r.edgeX - r.centerX;
        double dz = r.edgeZ - r.centerZ;
        r.radius = Math.sqrt(dx * dx + dz * dz);
        if (r.shape == null || r.shape.isBlank()) r.shape = "square";
        r.hasEdge = Math.abs(dx) >= 1.0 || Math.abs(dz) >= 1.0;
        CONFIG.save();
        if (!r.hasEdge) {
            source.sendFailure(Component.literal("pos2 must be on a different block from pos1."));
            return 0;
        }
        if ("square".equalsIgnoreCase(r.shape)) {
            int width = (int) Math.abs(Math.round(r.edgeX - r.centerX)) + 1;
            int depth = (int) Math.abs(Math.round(r.edgeZ - r.centerZ)) + 1;
            source.sendSuccess(() -> Component.literal("Square safe zone set from pos1 to pos2: " + width + " x " + depth + " blocks, full world height."), false);
        } else {
            double radius = r.radius;
            source.sendSuccess(() -> Component.literal(String.format("Circle safe-zone radius set to %.1f blocks, full world height.", radius)), false);
        }
        return 1;
    }

    private static int showRegion(CommandSourceStack source) {
        SpawnConfig.Region r = CONFIG.state().region;
        if (r == null || !r.hasCenter) {
            source.sendFailure(Component.literal("No spawn region has been defined."));
            return 0;
        }
        if ("square".equals(regionShape(r)) && r.hasEdge) {
            int width = (int) Math.abs(Math.round(r.edgeX - r.centerX)) + 1;
            int depth = (int) Math.abs(Math.round(r.edgeZ - r.centerZ)) + 1;
            source.sendSuccess(() -> Component.literal(String.format(
                    "Spawn region: SQUARE | pos1 %.1f, %.1f | pos2 %.1f, %.1f | %d x %d blocks | full world height | protection %s",
                    r.centerX, r.centerZ, r.edgeX, r.edgeZ, width, depth, CONFIG.state().protectionEnabled ? "ON" : "OFF")), false);
        } else {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Spawn region: CIRCLE | center %.1f, %.1f | radius %.1f | full world height | protection %s",
                    r.centerX, r.centerZ, r.radius, CONFIG.state().protectionEnabled ? "ON" : "OFF")), false);
        }
        return 1;
    }

    private static int clearRegion(CommandSourceStack source) {
        CONFIG.state().protectionEnabled = false;
        CONFIG.state().region = null;
        REGION_PREVIEW_ENABLED = false;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn region cleared and protection disabled."), true);
        return 1;
    }

    private static String regionShape(SpawnConfig.Region r) {
        return (r.shape == null || r.shape.isBlank()) ? "square" : r.shape.toLowerCase();
    }

    private static int setRegionShape(CommandSourceStack source, String shape) {
        SpawnConfig.Region r = CONFIG.state().region;
        if (r == null || !r.hasCenter) {
            source.sendFailure(Component.literal("Set /spawn region pos1 first."));
            return 0;
        }
        r.shape = shape;
        CONFIG.save();
        source.sendSuccess(() -> Component.literal("Spawn region shape set to " + shape.toUpperCase() + ". Use /spawn region preview on to see the boundary."), false);
        return 1;
    }

    private static int setRegionPreview(CommandSourceStack source, boolean enabled) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Run this command as a player.")); return 0; }

        if (!SpawnProtection.ready()) {
            source.sendFailure(Component.literal("Set both region points first."));
            return 0;
        }
        REGION_PREVIEW_ENABLED = enabled;
        source.sendSuccess(() -> Component.literal("Global spawn boundary preview " + (enabled ? "ON" : "OFF") + ". Everyone in the region dimension can see it."), false);
        return 1;
    }

    private static void drawRegionPreview(MinecraftServer server) {
        if (!REGION_PREVIEW_ENABLED || !SpawnProtection.ready()) return;
        SpawnConfig.Region r = CONFIG.state().region;
        String shape = regionShape(r);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            if (!SpawnProtection.dimension(level).equals(r.dimension)) continue;

            double y = player.getY() + 0.15;
            if ("square".equals(shape)) {
                drawSquarePreview(level, player, r, y);
            } else {
                drawCirclePreview(level, player, r, y);
            }
        }
    }

    private static void drawCirclePreview(ServerLevel level, ServerPlayer player, SpawnConfig.Region r, double y) {
        int points = Math.max(32, Math.min(180, (int) Math.ceil(r.radius * 6.0)));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = r.centerX + Math.cos(angle) * r.radius;
            double z = r.centerZ + Math.sin(angle) * r.radius;
            level.sendParticles(player, ParticleTypes.FLAME, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawSquarePreview(ServerLevel level, ServerPlayer player, SpawnConfig.Region r, double y) {
        double minX = Math.min(r.centerX, r.edgeX) - 0.5;
        double maxX = Math.max(r.centerX, r.edgeX) + 0.5;
        double minZ = Math.min(r.centerZ, r.edgeZ) - 0.5;
        double maxZ = Math.max(r.centerZ, r.edgeZ) + 0.5;
        double longest = Math.max(maxX - minX, maxZ - minZ);
        double step = Math.max(1.0, longest / 60.0);
        for (double x = minX; x <= maxX; x += step) {
            level.sendParticles(player, ParticleTypes.FLAME, true, true, x, y, minZ, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, ParticleTypes.FLAME, true, true, x, y, maxZ, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            level.sendParticles(player, ParticleTypes.FLAME, true, true, minX, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, ParticleTypes.FLAME, true, true, maxX, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void updatePlayerZoneMessages(MinecraftServer server) {
        if (!SpawnProtection.enabled()) {
            LAST_ZONE_STATE.clear();
            ZONE_MESSAGE_TICKS.clear();
            return;
        }

        Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            online.add(playerId);

            boolean safeZone = SpawnProtection.contains(player);
            Boolean previous = LAST_ZONE_STATE.put(playerId, safeZone);

            // Show the zone message only when the player first appears or actually crosses
            // the boundary. It is no longer resent every tick, so other action-bar systems
            // (such as the combat timer) can immediately take the action bar back.
            if (previous == null || previous != safeZone) {
                sendZoneMessage(player, safeZone);
                ZONE_MESSAGE_TICKS.put(playerId, ZONE_MESSAGE_DURATION_TICKS);

                // Play sounds only for a real boundary crossing, not simply logging in.
                // Use the vanilla /playsound command from the server command source instead
                // of ServerPlayer.playSound. This sends the sound through Minecraft's normal
                // command packet path and uses the MASTER category, so it is not muted by a
                // low Players/Music slider.
                if (previous != null) {
                    playZoneSound(server, player, safeZone);
                }
            }

            Integer ticks = ZONE_MESSAGE_TICKS.get(playerId);
            if (ticks != null) {
                if (ticks <= 1) {
                    // Clear our own short-lived message after about 2.5 seconds. If another
                    // mod is actively updating the action bar (for example a combat timer),
                    // its next update immediately replaces this empty packet.
                    player.sendOverlayMessage(Component.empty());
                    ZONE_MESSAGE_TICKS.remove(playerId);
                } else {
                    ZONE_MESSAGE_TICKS.put(playerId, ticks - 1);
                }
            }
        }

        LAST_ZONE_STATE.keySet().removeIf(id -> !online.contains(id));
        ZONE_MESSAGE_TICKS.keySet().removeIf(id -> !online.contains(id));
    }


    private static void playZoneSound(MinecraftServer server, ServerPlayer player, boolean safeZone) {
        String playerName = player.getGameProfile().name();

        // IMPORTANT: the old build used ~ ~ ~ from the SERVER command source.
        // That placed the sound at the command source position instead of at the
        // player crossing the boundary, which could make the sound completely
        // inaudible. Use the player's exact coordinates as the sound origin.
        String position = String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f",
                player.getX(), player.getY(), player.getZ());

        String command;
        if (safeZone) {
            // Bright confirmation when entering spawn.
            command = "playsound minecraft:block.note_block.pling master " + playerName
                    + " " + position + " 1.25 1.55 1.0";
        } else {
            // Lower warning tone when entering the PvP area.
            command = "playsound minecraft:block.note_block.bass master " + playerName
                    + " " + position + " 1.25 0.70 1.0";
        }

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                command
        );
    }

    private static int testZoneSound(CommandSourceStack source, boolean safeZone) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Run this sound test as a player."));
            return 0;
        }

        playZoneSound(source.getServer(), player, safeZone);
        source.sendSuccess(() -> Component.literal(
                safeZone ? "Played Safe Zone sound." : "Played PvP Zone sound."), false);
        return 1;
    }

    private static void sendZoneMessage(ServerPlayer player, boolean safeZone) {
        if (safeZone) {
            player.sendOverlayMessage(
                    Component.literal("🛡 SAFE ZONE 🛡").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            );
        } else {
            player.sendOverlayMessage(
                    Component.literal("⚔ PVP ZONE ⚔").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            );
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
