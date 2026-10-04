package com.alienroots;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * The visible meteor. It has no logic of its own: AsteroidStrike moves it every tick.
 * (If it ever gets loaded from disk without a strike controlling it, it removes itself.)
 */
public class MeteorEntity extends Entity {
    private static final TrackedData<Float> SCALE =
            DataTracker.registerData(MeteorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public int spin;           // drives the tumbling animation
    public boolean attached;   // true while a strike is controlling this meteor (server only)

    public MeteorEntity(EntityType<? extends MeteorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SCALE, 1.0f);
    }

    public float getMeteorScale() {
        return this.dataTracker.get(SCALE);
    }

    public void setMeteorScale(float scale) {
        this.dataTracker.set(SCALE, scale);
    }

    @Override
    public void tick() {
        super.tick();
        spin++;
        if (!this.getWorld().isClient && !attached) {
            this.discard();
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
