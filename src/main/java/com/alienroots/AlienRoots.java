package com.alienroots;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class AlienRoots implements ModInitializer {
    public static final String MOD_ID = "alienroots";

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        ServerTickEvents.END_WORLD_TICK.register(Effects::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Effects.clear());
    }
}
