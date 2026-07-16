package com.fdimo.metrovillagers;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MetroVillagersMod {
    public MetroVillagersMod(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");
        CommonClass.init();
    }
}
