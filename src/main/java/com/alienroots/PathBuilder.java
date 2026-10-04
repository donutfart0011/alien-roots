package com.alienroots;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/** Grows a precomputed path of blocks a little each tick, with glow nodes and sparkles. */
final class PathBuilder implements Colony.Builder {
    private final Colony colony;
    private final List<BlockPos> path;
    private final BlockState state;
    private final double rate;      // blocks per tick
    private final int glowEvery;    // every N blocks, drop a glowing froglight node (0 = never)
    private final boolean thick;    // fatten the first part of the path (tree trunks, pillars)
    private final Runnable onDone;
    private int delay;
    private double acc;
    private int i;

    PathBuilder(Colony colony, List<BlockPos> path, BlockState state, double rate,
                int glowEvery, boolean thick, int delay, Runnable onDone) {
        this.colony = colony;
        this.path = path;
        this.state = state;
        this.rate = rate;
        this.glowEvery = glowEvery;
        this.thick = thick;
        this.delay = delay;
        this.onDone = onDone;
    }

    @Override
    public boolean tick() {
        if (delay > 0) {
            delay--;
            return false;
        }
        acc += rate;
        while (acc >= 1.0) {
            acc -= 1.0;
            if (i >= path.size() || colony.structureBudget <= 0) return finish();
            step();
        }
        if (i >= path.size()) return finish();
        return false;
    }

    private boolean finish() {
        if (onDone != null) onDone.run();
        return true;
    }

    private void step() {
        ServerWorld w = colony.world;
        BlockPos p = path.get(i++);
        boolean placed = Build.place(w, p, state);
        if (placed) colony.structureBudget--;

        if (thick && i < path.size() * 0.4) {
            for (Direction d : Direction.Type.HORIZONTAL) {
                if (Build.place(w, p.offset(d), state)) colony.structureBudget--;
            }
        }
        if (glowEvery > 0 && i % glowEvery == 0) {
            Build.force(w, p, Structures.glow(colony.random));
        }
        if (placed) {
            if (i % 2 == 0) Build.sparkle(w, p);
            if (i % 9 == 0) {
                w.playSound(null, p, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS,
                        0.8f, 0.6f + colony.random.nextFloat() * 0.5f);
            }
        }
    }
}
