package com.alienroots;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Turns smooth curves into ordered lists of block positions. */
final class Paths {
    /** Samples f(u) for u in [0,1] and returns the distinct block positions it passes through. */
    static List<BlockPos> sample(DoubleFunction<Vec3d> f, int samples) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos last = null;
        for (int i = 0; i <= samples; i++) {
            BlockPos p = BlockPos.ofFloored(f.apply(i / (double) samples));
            if (!p.equals(last)) {
                out.add(p);
                last = p;
            }
        }
        return out;
    }

    static List<BlockPos> line(Vec3d a, Vec3d b) {
        int n = Math.max(1, (int) (a.distanceTo(b) * 3));
        return sample(u -> a.lerp(b, u), n);
    }

    private Paths() {}
}
