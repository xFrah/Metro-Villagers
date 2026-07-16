package com.fdimo.metrovillagers;

import net.fabricmc.api.ModInitializer;

public class MetroVillagersMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
    }
}
