package com.alienroots;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<MeteorEntity> METEOR = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(AlienRoots.MOD_ID, "meteor"),
            EntityType.Builder.<MeteorEntity>create(MeteorEntity::new, SpawnGroup.MISC)
                    .dimensions(4.0f, 4.0f)
                    .maxTrackingRange(12)       // visible from ~190 blocks
                    .trackingTickInterval(1)    // update every tick: it moves FAST
                    .makeFireImmune()
                    .build("meteor"));

    /** Touching this class registers the entity. */
    public static void register() {
    }

    private ModEntities() {}
}
