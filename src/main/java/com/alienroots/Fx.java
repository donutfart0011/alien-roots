package com.alienroots;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Sends particles to every nearby player, even from far away (vanilla's default range is only 32 blocks). */
final class Fx {
    private static final double RANGE_SQ = 160.0 * 160.0;

    static void spawn(ServerWorld world, ParticleEffect particle, double x, double y, double z,
                      int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(x, y, z) <= RANGE_SQ) {
                world.spawnParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    private Fx() {}
}
