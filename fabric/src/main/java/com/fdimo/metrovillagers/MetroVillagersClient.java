package com.fdimo.metrovillagers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.particle.HeartParticle;

public class MetroVillagersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(MetroVillagersMod.CHATTING_PARTICLE, HeartParticle.Provider::new);
    }
}
