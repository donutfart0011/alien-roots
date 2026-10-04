package com.alienroots;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Small shared helpers for placing blocks safely and spawning effects. */
final class Build {
    static final DustColorTransitionParticleEffect GLOWDUST = new DustColorTransitionParticleEffect(
            new Vector3f(0.3f, 1.0f, 0.6f), new Vector3f(0.7f, 0.3f, 1.0f), 1.2f);

    static boolean loaded(ServerWorld w, BlockPos p) {
        return !w.isOutOfHeightLimit(p) && w.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4);
    }

    /** Blocks this mod places (so roots don't treat their own blocks as terrain to cling to). */
    static boolean isMine(BlockState s) {
        return s.isOf(Blocks.MANGROVE_ROOTS) || s.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
                || s.isOf(Blocks.VERDANT_FROGLIGHT) || s.isOf(Blocks.PEARLESCENT_FROGLIGHT)
                || s.isOf(Blocks.WARPED_WART_BLOCK) || s.isOf(Blocks.SHROOMLIGHT)
                || s.isOf(Blocks.AMETHYST_BLOCK) || s.isOf(Blocks.END_ROD);
    }

    /** True if the block at p is solid natural terrain that roots can cling to. */
    static boolean isSupport(ServerWorld w, BlockPos p) {
        if (!loaded(w, p)) return false;
        BlockState s = w.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !isMine(s) && s.isFullCube(w, p);
    }

    /** Place only into air / replaceable blocks (never overwrites terrain or water). */
    static boolean place(ServerWorld w, BlockPos p, BlockState st) {
        if (!loaded(w, p)) return false;
        BlockState cur = w.getBlockState(p);
        if (!(cur.isAir() || cur.isReplaceable())) return false;
        if (!cur.getFluidState().isEmpty()) return false;
        return w.setBlockState(p, st, Block.NOTIFY_LISTENERS);
    }

    /** Like place, but may also replace our own root blocks (used for glowing nodes). */
    static boolean force(ServerWorld w, BlockPos p, BlockState st) {
        if (!loaded(w, p)) return false;
        BlockState cur = w.getBlockState(p);
        if (!cur.getFluidState().isEmpty()) return false;
        if (cur.isAir() || cur.isReplaceable() || cur.isOf(Blocks.MANGROVE_ROOTS) || cur.isOf(Blocks.MUDDY_MANGROVE_ROOTS)) {
            return w.setBlockState(p, st, Block.NOTIFY_LISTENERS);
        }
        return false;
    }

    /**
     * Finds an air/replaceable block with solid ground beneath it in column (x, z),
     * scanning from startY+up down to startY-down. Null if none or chunk unloaded.
     */
    static BlockPos surface(ServerWorld world, int x, int startY, int z, int up, int down) {
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int y = startY + up; y >= startY - down; y--) {
            if (y <= world.getBottomY() + 1 || y >= world.getTopY() - 1) continue;
            BlockPos p = new BlockPos(x, y, z);
            var s = world.getBlockState(p);
            if (!(s.isAir() || s.isReplaceable())) continue;
            if (!world.getFluidState(p).isEmpty()) continue;
            BlockPos below = p.down();
            if (world.getBlockState(below).isSideSolidFullSquare(world, below, net.minecraft.util.math.Direction.UP)) return p;
        }
        return null;
    }

    static void lightning(ServerWorld w, BlockPos p) {
        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, w);
        bolt.setPosition(Vec3d.ofBottomCenter(p));
        bolt.setCosmetic(true); // pure visual + thunder sound, no fire or damage
        w.spawnEntity(bolt);
    }

    static void sparkle(ServerWorld w, BlockPos p) {
        double x = p.getX() + 0.5, y = p.getY() + 0.6, z = p.getZ() + 0.5;
        Fx.spawn(w, GLOWDUST, x, y, z, 2, 0.3, 0.3, 0.3, 0);
        Fx.spawn(w, ParticleTypes.END_ROD, x, y, z, 1, 0.2, 0.2, 0.2, 0.02);
    }

    private Build() {}
}
