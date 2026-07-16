package com.fdimo.metrovillagers.mixin;

import com.fdimo.metrovillagers.IVillagerKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

@Mixin(Villager.class)
public class MixinVillagerKnowledge implements IVillagerKnowledge {

    @Unique
    private final Set<GlobalPos> knownJobSites = new HashSet<>();

    @Unique
    private final Map<GlobalPos, Long> unreachableJobSites = new HashMap<>();

    @Unique
    private static final int MAX_KNOWN_SITES = 10;

    @Override
    public Set<GlobalPos> getKnownJobSites() {
        return this.knownJobSites;
    }

    @Override
    public void addKnownJobSite(GlobalPos pos, BlockPos currentVillagerPos) {
        if (knownJobSites.contains(pos)) return;

        Villager self = (Villager) (Object) this;
        long currentTime = self.level().getGameTime();
        if (unreachableJobSites.containsKey(pos)) {
            if (currentTime - unreachableJobSites.get(pos) < 1200) { // 60 seconds (20 ticks * 60)
                return;
            } else {
                unreachableJobSites.remove(pos);
            }
        }

        String prof = self.getVillagerData().getProfession().name();
        String prefix = "[Metro Villagers] [" + prof + " at " + currentVillagerPos.toShortString() + "] ";

        String blockName = "unknown";
        if (self.level().dimension() == pos.dimension()) {
            net.minecraft.world.level.block.state.BlockState state = self.level().getBlockState(pos.pos());
            blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }

        if (knownJobSites.size() >= MAX_KNOWN_SITES) {
            GlobalPos farthest = knownJobSites.stream()
                .max(Comparator.comparingDouble(p -> p.pos().distSqr(currentVillagerPos)))
                .orElse(null);
            
            if (farthest != null) {
                // If the new one is further away than our farthest known, ignore it
                if (pos.pos().distSqr(currentVillagerPos) >= farthest.pos().distSqr(currentVillagerPos)) {
                    return;
                }
                
                String farthestBlock = "unknown";
                if (self.level().dimension() == farthest.dimension()) {
                    net.minecraft.world.level.block.state.BlockState state = self.level().getBlockState(farthest.pos());
                    farthestBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                }

                // Otherwise, forget the farthest to make room
                com.fdimo.metrovillagers.Constants.LOG.info(prefix + "Knowledge full! Forgetting farthest job site (" + farthestBlock + ") at " + farthest.pos().toShortString());
                knownJobSites.remove(farthest);
            }
        }

        com.fdimo.metrovillagers.Constants.LOG.info(prefix + "Added new job site (" + blockName + ") to knowledge at " + pos.pos().toShortString());
        knownJobSites.add(pos);
    }

    @Override
    public void addKnownJobSites(Set<GlobalPos> positions, BlockPos currentVillagerPos) {
        if (positions != null && !positions.isEmpty()) {
            Villager self = (Villager) (Object) this;
            String prof = self.getVillagerData().getProfession().name();
            com.fdimo.metrovillagers.Constants.LOG.info("[Metro Villagers] [" + prof + " at " + currentVillagerPos.toShortString() + "] Merging " + positions.size() + " job sites into knowledge...");
            for (GlobalPos pos : positions) {
                addKnownJobSite(pos, currentVillagerPos);
            }
        }
    }

    @Override
    public void markUnreachable(GlobalPos pos, long currentTime) {
        unreachableJobSites.put(pos, currentTime);
        knownJobSites.remove(pos);

        Villager self = (Villager) (Object) this;
        String prof = self.getVillagerData().getProfession().name();
        String prefix = "[Metro Villagers] [" + prof + " at " + self.blockPosition().toShortString() + "] ";

        String blockName = "unknown";
        if (self.level().dimension() == pos.dimension()) {
            net.minecraft.world.level.block.state.BlockState state = self.level().getBlockState(pos.pos());
            blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }

        com.fdimo.metrovillagers.Constants.LOG.info(prefix + "Pathfinder failed! Blacklisting (" + blockName + ") at " + pos.pos().toShortString() + " for 60 seconds.");
    }
}
