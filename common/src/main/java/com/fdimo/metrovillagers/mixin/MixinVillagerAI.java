package com.fdimo.metrovillagers.mixin;

import com.fdimo.metrovillagers.IVillagerKnowledge;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;
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
    private GlobalPos lastAttemptedJobSite = null;

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void onCustomServerAiStep(CallbackInfo ci) {
        Villager self = (Villager)(Object)this;
        if (!(self.level() instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) self.level();

        IVillagerKnowledge knowledge = (IVillagerKnowledge) self;

        com.fdimo.metrovillagers.AsyncPathfinder.printMetrics(serverLevel);

        // Passive Observation: Run every ~10 seconds (200 ticks)
        if (self.tickCount % 200 == 0) {
            PoiManager poiManager = serverLevel.getPoiManager();

            // Validate memory (remove destroyed job sites)
            Set<GlobalPos> toRemove = new java.util.HashSet<>();
            for (GlobalPos pos : knowledge.getKnownJobSites()) {
                if (pos.dimension() == serverLevel.dimension()) {
                    if (!poiManager.exists(pos.pos(), poiTypeHolder -> poiTypeHolder.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE))) {
                        toRemove.add(pos);
                        com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [Memory Validation] Removed destroyed job site at " + pos.pos().toShortString());
                    }
                }
            }
            knowledge.getKnownJobSites().removeAll(toRemove);

            Set<GlobalPos> scannedSites = new java.util.HashSet<>();
            poiManager.getInRange(
                poiTypeHolder -> poiTypeHolder.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
                self.blockPosition(),
                48, // 48 block radius scan (Vanilla range)
                PoiManager.Occupancy.HAS_SPACE
            ).forEach(poiRecord -> {
                GlobalPos pos = GlobalPos.of(serverLevel.dimension(), poiRecord.getPos());
                if (knowledge.canMemorize(pos, serverLevel.getGameTime())) {
                    scannedSites.add(pos);
                }
            });
            
            if (!scannedSites.isEmpty()) {
                com.fdimo.metrovillagers.AsyncPathfinder.checkReachableSites(self, serverLevel, scannedSites, (reachableSites) -> {
                    for (GlobalPos pos : reachableSites) {
                        knowledge.addKnownJobSite(pos, self.blockPosition());
                    }
                });
            }
        }

        // Active Querying: If jobless and no potential job site, run every ~3 seconds (60 ticks)
        if (self.tickCount % 60 == 0) {
            if (self.getVillagerData().getProfession() == VillagerProfession.NONE) {
                Brain<Villager> brain = self.getBrain();
                if (brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()) {
                    
                    if (this.lastAttemptedJobSite != null) {
                        knowledge.markUnreachable(this.lastAttemptedJobSite, serverLevel.getGameTime());
                        this.lastAttemptedJobSite = null;
                    }
                    // Look for nearby villagers to ask
                    List<Villager> nearbyVillagers = serverLevel.getEntitiesOfClass(
                        Villager.class, self.getBoundingBox().inflate(5.0)
                    );

                    boolean foundGossip = false;
                    for (Villager nearby : nearbyVillagers) {
                        if (nearby == self) continue;

                        IVillagerKnowledge nearbyKnowledge = (IVillagerKnowledge) nearby;
                        Set<GlobalPos> nearbySites = nearbyKnowledge.getKnownJobSites();

                        Set<GlobalPos> validGossip = new java.util.HashSet<>();
                        for (GlobalPos pos : nearbySites) {
                            if (knowledge.canMemorize(pos, serverLevel.getGameTime())) {
                                validGossip.add(pos);
                            }
                        }

                        if (!validGossip.isEmpty()) {
                            foundGossip = true;
                            
                            // Draw blue beam between gossiping villagers
                            org.joml.Vector3f blue = new org.joml.Vector3f(0.0f, 0.0f, 1.0f);
                            com.fdimo.metrovillagers.AsyncPathfinder.drawBeam(serverLevel, self.getEyePosition(), nearby.getEyePosition(), blue);

                            // Verify reachability before merging their knowledge into ours
                            com.fdimo.metrovillagers.AsyncPathfinder.checkReachableSites(self, serverLevel, validGossip, (reachableSites) -> {
                                if (!reachableSites.isEmpty()) {
                                    String prof = self.getVillagerData().getProfession().name();
                                    com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [" + prof + " at " + self.blockPosition().toShortString() + "] Jobless Villager queried nearby villager and verified " + reachableSites.size() + " known sites!");
                                    knowledge.addKnownJobSites(reachableSites, self.blockPosition());
                                }
                            });
                        }
                    }

                    // Select the closest one from our newly updated knowledge and set it!
                    Set<GlobalPos> mySites = knowledge.getKnownJobSites();
                    if (!mySites.isEmpty()) {
                        GlobalPos closestSite = mySites.stream()
                            .min(Comparator.comparingDouble(p -> p.pos().distSqr(self.blockPosition())))
                            .orElse(null);

                        if (closestSite != null) {
                            if (foundGossip) {
                                String prof = self.getVillagerData().getProfession().name();
                                String blockName = "unknown";
                                if (self.level().dimension() == closestSite.dimension()) {
                                    blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(serverLevel.getBlockState(closestSite.pos()).getBlock()).toString();
                                }
                                com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [" + prof + " at " + self.blockPosition().toShortString() + "] Jobless Villager selected closest gossiped job site (" + blockName + ") at " + closestSite.pos().toShortString() + " as their new target!");
                                
                                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, self.getX(), self.getY() + self.getEyeHeight(), self.getZ(), 3, 0.5, 0.5, 0.5, 0.0);
                            }
                            brain.setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, closestSite);
                            this.lastAttemptedJobSite = closestSite;
                        }
                    }
                }
            }
        }
    }
}
