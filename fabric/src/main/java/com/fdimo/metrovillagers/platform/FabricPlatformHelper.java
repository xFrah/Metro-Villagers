package com.fdimo.metrovillagers.platform;

import com.fdimo.metrovillagers.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public net.minecraft.core.particles.SimpleParticleType getChattingParticle() {
        return com.fdimo.metrovillagers.MetroVillagersMod.CHATTING_PARTICLE;
    }
}
