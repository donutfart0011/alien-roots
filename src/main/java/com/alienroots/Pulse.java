package com.alienroots;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** A heartbeat: a glowing ring that races outward from the core, hugging the terrain. */
final class Pulse implements Colony.Builder {
    private final Colony colony;
    private double r = 1;
    private boolean started;

    Pulse(Colony colony) {
        this.colony = colony;
    }

    @Override
    public boolean tick() {
        ServerWorld w = colony.world;
        BlockPos o = colony.origin;
        if (!started) {
            started = true;
            w.playSound(null, o, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 6.0f, 0.7f);
        }
        r += 2.5;
        if (r > 48) return true;

        int n = (int) Math.min(120, Math.max(16, r * 2));
        for (int k = 0; k < n; k++) {
            double a = (Math.PI * 2 * k) / n;
            double x = o.getX() + 0.5 + Math.cos(a) * r;
            double z = o.getZ() + 0.5 + Math.sin(a) * r;
            BlockPos g = Build.surface(w, MathHelper.floor(x), o.getY(), MathHelper.floor(z), 8, 10);
            if (g == null) continue;
            Fx.spawn(w, Build.GLOWDUST, x, g.getY() + 0.4, z, 1, 0.1, 0.15, 0.1, 0);
        }
        return false;
    }
}
