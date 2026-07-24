package com.fdimo.metrovillagers;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.SimpleParticleType;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

public class MetroVillagersMod implements ModInitializer {
    public static final SimpleParticleType CHATTING_PARTICLE = FabricParticleTypes.simple();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chatting"), CHATTING_PARTICLE);
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
    }
}
