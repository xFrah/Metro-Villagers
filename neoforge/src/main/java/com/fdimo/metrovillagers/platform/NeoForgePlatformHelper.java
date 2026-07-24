package com.fdimo.metrovillagers.platform;

import com.fdimo.metrovillagers.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public net.minecraft.core.particles.SimpleParticleType getChattingParticle() {
        return com.fdimo.metrovillagers.MetroVillagersMod.CHATTING_PARTICLE.get();
    }
}