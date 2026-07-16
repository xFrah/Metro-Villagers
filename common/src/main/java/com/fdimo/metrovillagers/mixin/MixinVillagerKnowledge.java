package com.fdimo.metrovillagers.mixin;

import com.fdimo.metrovillagers.IVillagerKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;

@Mixin(Villager.class)
public class MixinVillagerKnowledge implements IVillagerKnowledge {

    @Unique
    private final Set<GlobalPos> knownJobSites = new HashSet<>();

    @Unique
    private static final int MAX_KNOWN_SITES = 10;

    @Override
    public Set<GlobalPos> getKnownJobSites() {
        return this.knownJobSites;
    }

    @Override
    public void addKnownJobSite(GlobalPos pos, BlockPos currentVillagerPos) {
        if (knownJobSites.contains(pos)) return;

        if (knownJobSites.size() >= MAX_KNOWN_SITES) {
            GlobalPos farthest = knownJobSites.stream()
                .max(Comparator.comparingDouble(p -> p.pos().distSqr(currentVillagerPos)))
                .orElse(null);
            
            if (farthest != null) {
                // If the new one is further away than our farthest known, ignore it
                if (pos.pos().distSqr(currentVillagerPos) >= farthest.pos().distSqr(currentVillagerPos)) {
                    return;
                }
                
                // Otherwise, forget the farthest to make room
                com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Knowledge full! Forgetting farthest job site at " + farthest.pos().toShortString());
                knownJobSites.remove(farthest);
            }
        }

        com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Added new job site to knowledge at " + pos.pos().toShortString());
        knownJobSites.add(pos);
    }

    @Override
    public void addKnownJobSites(Set<GlobalPos> positions, BlockPos currentVillagerPos) {
        if (positions != null && !positions.isEmpty()) {
            com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] Merging " + positions.size() + " job sites into knowledge...");
            for (GlobalPos pos : positions) {
                addKnownJobSite(pos, currentVillagerPos);
            }
        }
    }
}
