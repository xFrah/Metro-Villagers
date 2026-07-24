package com.fdimo.metrovillagers.mixin;

import com.fdimo.metrovillagers.IVillagerKnowledge;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mixin(Villager.class)
public abstract class MixinVillagerAI {

    @Unique
    private static final java.util.Map<net.minecraft.core.BlockPos, Long> OCCUPANCY_CACHE_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    @Unique
    private static final java.util.Map<net.minecraft.core.BlockPos, Integer> OCCUPANCY_CACHE_OWNER = new java.util.concurrent.ConcurrentHashMap<>();

    @Unique
    private boolean metro_isOccupied(ServerLevel serverLevel, net.minecraft.core.BlockPos pos) {
        long currentTime = serverLevel.getGameTime();
        Long lastCheck = OCCUPANCY_CACHE_TIME.get(pos);
        
        // Cache occupancy status based on config duration to prevent spamming spatial queries
        if (lastCheck != null && currentTime - lastCheck < (com.fdimo.metrovillagers.Config.DATA.occupancyCacheDurationSeconds * 20L)) {
            return OCCUPANCY_CACHE_OWNER.getOrDefault(pos, -1) != -1;
        }

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(com.fdimo.metrovillagers.Config.DATA.pathfindingRadius + 16.0D);
        int occupiedBy = -1;
        for (Villager v : serverLevel.getEntitiesOfClass(Villager.class, box)) {
            java.util.Optional<GlobalPos> jobSite = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            java.util.Optional<GlobalPos> potentialJobSite = v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            
            if (jobSite.isPresent() && jobSite.get().pos().equals(pos)) {
                occupiedBy = v.getId();
                break;
            }
            if (potentialJobSite.isPresent() && potentialJobSite.get().pos().equals(pos)) {
                occupiedBy = v.getId();
                break;
            }
        }

        OCCUPANCY_CACHE_TIME.put(pos, currentTime);
        OCCUPANCY_CACHE_OWNER.put(pos, occupiedBy);
        
        return occupiedBy != -1;
    }

    @Unique
    private GlobalPos lastAttemptedJobSite = null;
    private net.minecraft.world.phys.Vec3 metro_lastPosCheck = null;

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void onCustomServerAiStep(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!(self.level() instanceof ServerLevel))
            return;
        ServerLevel serverLevel = (ServerLevel) self.level();

        if (com.fdimo.metrovillagers.Config.DATA.enableDebugBeams && self.tickCount % 5 == 0) {
            org.joml.Vector3f yellow = new org.joml.Vector3f(1.0f, 1.0f, 0.0f);
            self.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(pos -> {
                if (pos.dimension() == serverLevel.dimension()) {
                    com.fdimo.metrovillagers.AsyncPathfinder.drawBeam(serverLevel, net.minecraft.world.phys.Vec3.atCenterOf(pos.pos()), self.getEyePosition(), yellow);
                }
            });
            self.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).ifPresent(pos -> {
                if (pos.dimension() == serverLevel.dimension()) {
                    com.fdimo.metrovillagers.AsyncPathfinder.drawBeam(serverLevel, net.minecraft.world.phys.Vec3.atCenterOf(pos.pos()), self.getEyePosition(), yellow);
                }
            });
        }

        IVillagerKnowledge knowledge = (IVillagerKnowledge) self;

        com.fdimo.metrovillagers.AsyncPathfinder.printMetrics(serverLevel);

        // Passive Observation: Run based on config frequency, staggered by entity ID to prevent lag spikes
        int passiveTicks = com.fdimo.metrovillagers.Config.DATA.passiveLearningFrequencySeconds * 20;
        if (passiveTicks > 0 && self.tickCount % passiveTicks == self.getId() % passiveTicks) {
            PoiManager poiManager = serverLevel.getPoiManager();

            // Validate memory (remove destroyed job sites)
            Set<GlobalPos> toRemove = new java.util.HashSet<>();
            for (GlobalPos pos : knowledge.getKnownJobSites()) {
                if (pos.dimension() == serverLevel.dimension()) {
                    boolean hasSpace = !metro_isOccupied(serverLevel, pos.pos());

                    if (!hasSpace) {
                        toRemove.add(pos);
                        if (com.fdimo.metrovillagers.Config.DATA.enableDebugLogs) {
                            com.fdimo.metrovillagers.Constants.LOG
                                    .info("[Metro Villagers] [Memory Validation] Removed invalid/occupied job site at "
                                            + pos.pos().toShortString());
                        }
                    }
                }
            }
            knowledge.getKnownJobSites().removeAll(toRemove);

            Set<GlobalPos> scannedSites = new java.util.HashSet<>();
            poiManager.getInRange(
                    poiTypeHolder -> poiTypeHolder.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
                    self.blockPosition(),
                    com.fdimo.metrovillagers.Config.DATA.pathfindingRadius,
                    PoiManager.Occupancy.HAS_SPACE).forEach(poiRecord -> {
                        GlobalPos pos = GlobalPos.of(serverLevel.dimension(), poiRecord.getPos());
                        if (!metro_isOccupied(serverLevel, pos.pos())
                                && knowledge.canMemorize(pos, serverLevel.getGameTime())) {
                            scannedSites.add(pos);
                        }
                    });

            if (!scannedSites.isEmpty()) {
                com.fdimo.metrovillagers.AsyncPathfinder.checkReachableSites(self, serverLevel, scannedSites,
                        (reachableSites) -> {
                            for (GlobalPos pos : reachableSites) {
                                knowledge.addKnownJobSite(pos, self.blockPosition());
                            }
                        });
            }
        }

        // Stuck Detection: If they have a potential job site but haven't moved in X seconds
        int stuckTicks = com.fdimo.metrovillagers.Config.DATA.pathfindingStuckTimeSeconds * 20;
        if (stuckTicks > 0 && self.tickCount % stuckTicks == self.getId() % stuckTicks) {
            Brain<Villager> brain = self.getBrain();
            if (brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent()) {
                double stuckDist = com.fdimo.metrovillagers.Config.DATA.pathfindingStuckDistance;
                if (this.metro_lastPosCheck != null && this.metro_lastPosCheck.distanceToSqr(self.position()) < stuckDist * stuckDist) {
                    GlobalPos target = brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get();
                    if (target.dimension() == serverLevel.dimension() && self.blockPosition().distManhattan(target.pos()) > 2) {
                        if (com.fdimo.metrovillagers.Config.DATA.enableDebugLogs) {
                            com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Villager at " + self.blockPosition().toShortString() + " is physically stuck while pathfinding to " + target.pos().toShortString() + "! Blacklisting.");
                        }
                        knowledge.markUnreachable(target, serverLevel.getGameTime());
                        brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                        this.lastAttemptedJobSite = null;
                        
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                                self.getX(), self.getY() + self.getEyeHeight() + 0.5, self.getZ(),
                                5, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                this.metro_lastPosCheck = self.position();
            } else {
                this.metro_lastPosCheck = null;
            }
        }

        // Active Querying: If jobless and no potential job site, run every ~3 seconds
        // (60 ticks)
        if (self.tickCount % 60 == 0) {
            if (self.getVillagerData().profession().is(VillagerProfession.NONE) && !self.isBaby()) {
                Brain<Villager> brain = self.getBrain();
                
                // Only actively search for jobs if the villager is in a normal state (not sleeping, panicking, etc)
                if (brain.isActive(net.minecraft.world.entity.schedule.Activity.IDLE) && brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()) {

                    if (this.lastAttemptedJobSite != null) {
                        knowledge.markUnreachable(this.lastAttemptedJobSite, serverLevel.getGameTime());
                        this.lastAttemptedJobSite = null;
                    }

                    // Gossip cooldown
                    long gossipCooldownTicks = com.fdimo.metrovillagers.Config.DATA.gossipCooldownSeconds * 20L;
                    if (serverLevel.getGameTime() - knowledge.getLastGossipTime() < gossipCooldownTicks) {
                        return;
                    }

                    // Look for nearby villagers to ask
                    List<Villager> nearbyVillagers = serverLevel.getEntitiesOfClass(
                            Villager.class,
                            self.getBoundingBox().inflate(com.fdimo.metrovillagers.Config.DATA.gossipRadius));

                    boolean foundGossip = false;
                    for (Villager nearby : nearbyVillagers) {
                        if (nearby == self)
                            continue;
                        if (!self.getSensing().hasLineOfSight(nearby))
                            continue;

                        net.minecraft.world.level.pathfinder.Path path = self.getNavigation().createPath(nearby, 0);
                        if (path == null || !path.canReach())
                            continue;

                        IVillagerKnowledge nearbyKnowledge = (IVillagerKnowledge) nearby;
                        Set<GlobalPos> nearbySites = nearbyKnowledge.getKnownJobSites();

                        Set<GlobalPos> validGossip = new java.util.HashSet<>();
                        for (GlobalPos pos : nearbySites) {
                            if (pos.dimension() == serverLevel.dimension()
                                    && knowledge.canMemorize(pos, serverLevel.getGameTime())) {
                                boolean hasSpace = !metro_isOccupied(serverLevel, pos.pos());

                                if (hasSpace) {
                                    validGossip.add(pos);
                                }
                            }
                        }

                        if (!validGossip.isEmpty()) {
                            foundGossip = true;
                            net.minecraft.world.phys.Vec3 nearbyEyePos = nearby.getEyePosition();

                            // Verify reachability before merging their knowledge into ours
                            com.fdimo.metrovillagers.AsyncPathfinder.checkReachableSites(self, serverLevel, validGossip,
                                    (reachableSites) -> {
                                        if (!reachableSites.isEmpty()) {
                                            // Draw blue beam between gossiping villagers
                                            if (com.fdimo.metrovillagers.Config.DATA.enableDebugBeams) {
                                                org.joml.Vector3f blue = new org.joml.Vector3f(0.0f, 0.0f, 1.0f);
                                                com.fdimo.metrovillagers.AsyncPathfinder.drawBeam(serverLevel,
                                                        self.getEyePosition(), nearbyEyePos, blue);
                                            }

                                            // Spawn chatting particle on successful gossip!
                                            serverLevel.sendParticles(
                                                    com.fdimo.metrovillagers.platform.Services.PLATFORM
                                                            .getChattingParticle(),
                                                    self.getX(), self.getY() + self.getEyeHeight(), self.getZ(), 5, 0.5,
                                                    0.5, 0.5, 0.0);
                                            serverLevel.sendParticles(
                                                    com.fdimo.metrovillagers.platform.Services.PLATFORM
                                                            .getChattingParticle(),
                                                    nearbyEyePos.x, nearbyEyePos.y, nearbyEyePos.z, 5, 0.5, 0.5, 0.5,
                                                    0.0);

                                            String prof = self.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
                                            if (com.fdimo.metrovillagers.Config.DATA.enableDebugLogs) com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [" + prof
                                                    + " at " + self.blockPosition().toShortString()
                                                    + "] Jobless Villager queried nearby villager and verified "
                                                    + reachableSites.size() + " known sites!");
                                            knowledge.addKnownJobSites(reachableSites, self.blockPosition());
                                        }
                                    });

                            knowledge.setLastGossipTime(serverLevel.getGameTime());
                            break; // Only one successful gossip exchange at a time
                        }
                    }

                    // Select the closest one from our newly updated knowledge and set it!
                    Set<GlobalPos> mySites = knowledge.getKnownJobSites();
                    if (!mySites.isEmpty()) {
                        GlobalPos closestSite = mySites.stream()
                                .filter(p -> !metro_isOccupied(serverLevel, p.pos()))
                                .min(Comparator.comparingDouble(p -> p.pos().distSqr(self.blockPosition())))
                                .orElse(null);

                        if (closestSite != null) {
                            if (foundGossip) {
                                String prof = self.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
                                String blockName = "unknown";
                                if (self.level().dimension() == closestSite.dimension()) {
                                    blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                            .getKey(serverLevel.getBlockState(closestSite.pos()).getBlock()).toString();
                                }
                                if (com.fdimo.metrovillagers.Config.DATA.enableDebugLogs) com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [" + prof + " at "
                                        + self.blockPosition().toShortString()
                                        + "] Jobless Villager selected closest gossiped job site (" + blockName
                                        + ") at " + closestSite.pos().toShortString() + " as their new target!");

                                serverLevel.sendParticles(
                                        com.fdimo.metrovillagers.platform.Services.PLATFORM.getChattingParticle(),
                                        self.getX(), self.getY() + self.getEyeHeight(), self.getZ(), 3, 0.5, 0.5, 0.5,
                                        0.0);
                            }
                            brain.setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, closestSite);
                            this.lastAttemptedJobSite = closestSite;
                            
                            // Immediately update the cache so the next villager knows it's taken!
                            OCCUPANCY_CACHE_OWNER.put(closestSite.pos(), self.getId());
                            OCCUPANCY_CACHE_TIME.put(closestSite.pos(), serverLevel.getGameTime());
                        }
                    }
                }
            }
        }
    }
}
