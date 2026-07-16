package com.fdimo.metrovillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import java.util.Set;

public interface IVillagerKnowledge {
    Set<GlobalPos> getKnownJobSites();
    void addKnownJobSite(GlobalPos pos, BlockPos currentVillagerPos);
    void addKnownJobSites(Set<GlobalPos> positions, BlockPos currentVillagerPos);
    void markUnreachable(GlobalPos pos, long currentTime);
}
