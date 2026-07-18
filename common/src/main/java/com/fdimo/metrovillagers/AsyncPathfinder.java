package com.fdimo.metrovillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AsyncPathfinder {

    public static final AtomicInteger executedPathfindings = new AtomicInteger(0);
    public static final AtomicInteger newPathfindings = new AtomicInteger(0);
    private static long lastPrintTime = 0;

    public static void printMetrics(ServerLevel serverLevel) {
        long time = serverLevel.getGameTime();
        if (time - lastPrintTime >= 100) {
            lastPrintTime = time;
            
            int executed = executedPathfindings.getAndSet(0);
            int newlyAdded = newPathfindings.getAndSet(0);
            int pending = Constants.ASYNC_POOL.getQueue().size();

            float executedPerSec = executed / 5.0f;
            float newlyAddedPerSec = newlyAdded / 5.0f;

            if (com.fdimo.metrovillagers.Config.DATA.enableQueueDebug) {
                String msg = String.format("[Metro Villagers Async] Queue: %d | New/sec: %.1f | Executed/sec: %.1f", pending, newlyAddedPerSec, executedPerSec);
                net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.literal("§e" + msg);
                for (net.minecraft.server.level.ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                    player.displayClientMessage(comp, true);
                }
            }
        }
    }

    public static void checkReachability(Villager villager, ServerLevel serverLevel, GlobalPos targetPos, Consumer<Boolean> callback) {
        if (villager.level().dimension() != targetPos.dimension()) {
            callback.accept(false);
            return;
        }

        BlockPos villagerPos = villager.blockPosition();
        BlockPos destination = targetPos.pos();

        // Check if it's within a reasonable distance to even attempt pathfinding (e.g., 64 blocks)
        if (villagerPos.distSqr(destination) > 64 * 64) {
            callback.accept(false); // Too far to even care
            return;
        }

        // Snapshot the region on the main thread
        int range = 64;
        PathNavigationRegion region = new PathNavigationRegion(serverLevel, villagerPos.offset(-range, -range, -range), villagerPos.offset(range, range, range));

        CompletableFuture.runAsync(() -> {
            try {
                WalkNodeEvaluator nodeEvaluator = new WalkNodeEvaluator();
                nodeEvaluator.setCanPassDoors(true);
                nodeEvaluator.setCanOpenDoors(true);
                
                PathFinder pathFinder = new PathFinder(nodeEvaluator, 200); // 200 max visited nodes (similar to vanilla GroundPathNavigation)
                
                Path path = pathFinder.findPath(region, villager, Set.of(destination), 64.0F, 1, 1.0F);
                boolean reachable = path != null && path.canReach();

                serverLevel.getServer().execute(() -> {
                    if (!villager.isRemoved()) {
                        callback.accept(reachable);
                    }
                });
            } catch (Exception e) {
                Constants.LOG.error("Async pathfinding error", e);
                serverLevel.getServer().execute(() -> callback.accept(false));
            }
        }, Constants.ASYNC_POOL);
    }

    public static void checkReachableSites(Villager villager, ServerLevel serverLevel, Set<GlobalPos> sites, Consumer<Set<GlobalPos>> callback) {
        if (sites.isEmpty() || villager.isRemoved()) {
            callback.accept(Set.of());
            return;
        }

        BlockPos villagerPos = villager.blockPosition();
        Set<GlobalPos> validSites = new java.util.HashSet<>();
        
        int maxDistSqr = Config.DATA.pathfindingRadius * Config.DATA.pathfindingRadius;
        for (GlobalPos pos : sites) {
            if (villager.level().dimension() == pos.dimension() && villagerPos.distSqr(pos.pos()) <= maxDistSqr) {
                validSites.add(pos);
            }
        }

        if (validSites.isEmpty()) {
            callback.accept(Set.of());
            return;
        }

        int range = (int)(Config.DATA.pathfindingRadius * 2.0); // 2x buffer for indirect paths
        PathNavigationRegion region = new PathNavigationRegion(serverLevel, villagerPos.offset(-range, -range, -range), villagerPos.offset(range, range, range));

        newPathfindings.addAndGet(validSites.size());

        String prof = villager.getVillagerData().getProfession().name();
        String villagerPrefix = "[" + prof + " at " + villagerPos.toShortString() + "]";
        net.minecraft.world.phys.Vec3 villagerEyePos = villager.getEyePosition();

        CompletableFuture.runAsync(() -> {
            try {
                WalkNodeEvaluator nodeEvaluator = new WalkNodeEvaluator();
                nodeEvaluator.setCanPassDoors(true);
                nodeEvaluator.setCanOpenDoors(true);
                PathFinder pathFinder = new PathFinder(nodeEvaluator, Config.DATA.maxPathfindingNodes);

                Set<GlobalPos> reachableSites = new java.util.HashSet<>();
                Set<GlobalPos> failedSites = new java.util.HashSet<>();
                
                float maxPathDistance = (float)Config.DATA.pathfindingRadius * 2.0F;

                for (GlobalPos pos : validSites) {
                    executedPathfindings.incrementAndGet();
                    Path path = pathFinder.findPath(region, villager, Set.of(pos.pos()), maxPathDistance, 1, 1.0F);
                    if (path != null && path.canReach()) {
                        reachableSites.add(pos);
                        if (Config.DATA.enableDebugLogs) Constants.LOG.info("[Metro Villagers Async] [DEBUG] " + villagerPrefix + " Pathfinding SUCCESS for " + pos.pos().toShortString());
                    } else {
                        failedSites.add(pos);
                        if (Config.DATA.enableDebugLogs) Constants.LOG.info("[Metro Villagers Async] [DEBUG] " + villagerPrefix + " Pathfinding FAILED for " + pos.pos().toShortString());
                    }
                }

                serverLevel.getServer().execute(() -> {
                    if (!villager.isRemoved()) {
                        // Draw beams
                        if (Config.DATA.enableDebugBeams) {
                            org.joml.Vector3f green = new org.joml.Vector3f(0.0f, 1.0f, 0.0f);
                            org.joml.Vector3f red = new org.joml.Vector3f(1.0f, 0.0f, 0.0f);
                            
                            for (GlobalPos pos : reachableSites) {
                                drawBeam(serverLevel, villagerEyePos, net.minecraft.world.phys.Vec3.atCenterOf(pos.pos()), green);
                            }
                            for (GlobalPos pos : failedSites) {
                                drawBeam(serverLevel, villagerEyePos, net.minecraft.world.phys.Vec3.atCenterOf(pos.pos()), red);
                            }
                        }
                        
                        callback.accept(reachableSites);
                    }
                });
            } catch (Exception e) {
                Constants.LOG.error("Async pathfinding error", e);
                serverLevel.getServer().execute(() -> callback.accept(Set.of()));
            }
        }, Constants.ASYNC_POOL);
    }

    public static void drawBeam(ServerLevel level, net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 end, org.joml.Vector3f color) {
        net.minecraft.core.particles.DustParticleOptions particle = new net.minecraft.core.particles.DustParticleOptions(color, 1.0f);
        double dist = start.distanceTo(end);
        int count = (int) (dist * 4); // 4 particles per block
        for (int i = 0; i <= count; i++) {
            double t = count == 0 ? 0 : (double) i / count;
            double px = start.x + (end.x - start.x) * t;
            double py = start.y + (end.y - start.y) * t;
            double pz = start.z + (end.z - start.z) * t;
            level.sendParticles(particle, px, py, pz, 1, 0, 0, 0, 0);
        }
    }
}
