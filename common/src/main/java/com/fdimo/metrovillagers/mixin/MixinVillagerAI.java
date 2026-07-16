package com.fdimo.metrovillagers.mixin;

import com.fdimo.metrovillagers.IVillagerKnowledge;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mixin(Villager.class)
public abstract class MixinVillagerAI {

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void onCustomServerAiStep(CallbackInfo ci) {
        Villager self = (Villager)(Object)this;
        if (!(self.level() instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) self.level();

        IVillagerKnowledge knowledge = (IVillagerKnowledge) self;

        // Passive Observation: Run every ~10 seconds (200 ticks)
        if (self.tickCount % 200 == 0) {
            PoiManager poiManager = serverLevel.getPoiManager();
            poiManager.getInRange(
                poiTypeHolder -> poiTypeHolder.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
                self.blockPosition(),
                128, // 128 block radius scan
                PoiManager.Occupancy.HAS_SPACE
            ).forEach(poiRecord -> {
                GlobalPos pos = GlobalPos.of(serverLevel.dimension(), poiRecord.getPos());
                knowledge.addKnownJobSite(pos, self.blockPosition());
            });
        }

        // Active Querying: If jobless and no potential job site, run every ~3 seconds (60 ticks)
        if (self.tickCount % 60 == 0) {
            if (self.getVillagerData().getProfession() == VillagerProfession.NONE) {
                Brain<Villager> brain = self.getBrain();
                if (brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()) {
                    
                    // Look for nearby villagers to ask
                    List<Villager> nearbyVillagers = serverLevel.getEntitiesOfClass(
                        Villager.class, self.getBoundingBox().inflate(5.0)
                    );

                    boolean foundGossip = false;
                    for (Villager nearby : nearbyVillagers) {
                        if (nearby == self) continue;

                        IVillagerKnowledge nearbyKnowledge = (IVillagerKnowledge) nearby;
                        Set<GlobalPos> nearbySites = nearbyKnowledge.getKnownJobSites();

                        if (!nearbySites.isEmpty()) {
                            com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Jobless Villager queried nearby villager and received " + nearbySites.size() + " known sites!");
                            foundGossip = true;
                        }
                        // Add their knowledge to our knowledge (this will respect the max 10 constraint and closest logic)
                        knowledge.addKnownJobSites(nearbySites, self.blockPosition());
                    }

                    // Select the closest one from our newly updated knowledge and set it!
                    Set<GlobalPos> mySites = knowledge.getKnownJobSites();
                    if (!mySites.isEmpty()) {
                        GlobalPos closestSite = mySites.stream()
                            .min(Comparator.comparingDouble(p -> p.pos().distSqr(self.blockPosition())))
                            .orElse(null);

                        if (closestSite != null) {
                            if (foundGossip) {
                                com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Jobless Villager selected closest gossiped job site at " + closestSite.pos().toShortString() + " as their new target!");
                            }
                            brain.setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, closestSite);
                        }
                    }
                }
            }
        }
    }
}
