package com.alienroots;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** The alien architecture: trees, helix spires, arches, crystal spikes, egg pods, stalks, and the Heart. */
final class Structures {
    static final BlockState ROOTS = Blocks.MANGROVE_ROOTS.getDefaultState();
    static final BlockState TRUNK = Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState();
    static final BlockState SKIN = Blocks.WARPED_WART_BLOCK.getDefaultState();
    static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.getDefaultState();

    static BlockState glow(Random r) {
        return (r.nextBoolean() ? Blocks.VERDANT_FROGLIGHT : Blocks.PEARLESCENT_FROGLIGHT).getDefaultState();
    }

    static double rate(int size, double ticks) {
        return Math.max(0.3, size / Math.max(1.0, ticks));
    }

    /** Called where a root ends: decide what the colony builds there. */
    static void colonize(Colony c, BlockPos at) {
        if (c.structures >= c.cfg.maxStructures || c.structureBudget < 200) return;
        c.structures++;
        int roll = c.random.nextInt(100);
        if (roll < 35) tree(c, at, 8 + c.random.nextInt(8), false);
        else if (roll < 55) arch(c, at);
        else if (roll < 75) crystals(c, at);
        else if (roll < 85) spire(c, at, 12 + c.random.nextInt(12), 2.2 + c.random.nextDouble(), 2);
        else pod(c, at, 2 + c.random.nextInt(2), true);
    }

    // ------------------------------------------------------------------ the Heart

    static void heart(Colony c, BlockPos core) {
        spire(c, core.up(), 34, 4.5, 3);
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + 0.5;
            BlockPos g = Build.surface(c.world,
                    core.getX() + (int) Math.round(Math.cos(a) * 10), core.getY(),
                    core.getZ() + (int) Math.round(Math.sin(a) * 10), 6, 8);
            if (g != null) tree(c, g, 14 + c.random.nextInt(5), true);
        }
        c.structures += 4;
    }

    // ------------------------------------------------------------------ trees

    static void tree(Colony c, BlockPos base, int height, boolean big) {
        Random r = c.random;
        double ph = r.nextDouble() * Math.PI * 2;
        double sway = big ? 1.6 : 0.9;
        double bx = base.getX() + 0.5, by = base.getY(), bz = base.getZ() + 0.5;
        List<BlockPos> trunk = Paths.sample(
                u -> new Vec3d(bx + Math.sin(u * 4 + ph) * sway * u, by + u * height, bz + Math.cos(u * 3 + ph) * sway * u),
                height * 5);
        c.spawn(new PathBuilder(c, trunk, TRUNK, rate(trunk.size(), height * 3.0), 0, true, 0,
                () -> crown(c, trunk, big)));
        for (int k = 0; k < 4; k++) {
            c.spawn(new PathBuilder(c, groundLine(c, base, r.nextDouble() * Math.PI * 2, big ? 7 : 4),
                    ROOTS, 1.2, 0, false, 0, null));
        }
    }

    private static void crown(Colony c, List<BlockPos> trunk, boolean big) {
        Random r = c.random;
        BlockPos top = trunk.get(trunk.size() - 1);
        int n = big ? 7 : 3 + r.nextInt(3);
        int[] remaining = {n};
        for (int b = 0; b < n; b++) {
            int idx = Math.min(trunk.size() - 1, (int) (trunk.size() * (0.4 + 0.55 * b / n)));
            BlockPos s = trunk.get(idx);
            final double a = r.nextDouble() * Math.PI * 2;
            final double len = (big ? 8 : 5) + r.nextDouble() * 5;
            final double sx = s.getX() + 0.5, sy = s.getY(), sz = s.getZ() + 0.5;
            List<BlockPos> br = Paths.sample(
                    u -> new Vec3d(sx + Math.cos(a) * len * u, sy + len * 0.5 * (2 * u - u * u) + u * 2, sz + Math.sin(a) * len * u),
                    (int) (len * 7));
            BlockPos end = br.get(br.size() - 1);
            c.spawn(new PathBuilder(c, br, ROOTS, 1.4, 5, false, b * 6, () -> {
                pod(c, end, big ? 2 : 1, false);
                dangle(c, end.down(2), 3 + r.nextInt(4));
                if (--remaining[0] == 0) finishTree(c, top, big);
            }));
        }
    }

    private static void finishTree(Colony c, BlockPos top, boolean big) {
        pod(c, top.up(2), big ? 3 : 2, true);
        antenna(c, top.up(big ? 6 : 5), big ? 5 : 3);
        c.addBeacon(top.up(3));
        if (big || c.random.nextFloat() < 0.3f) Build.lightning(c.world, top.up(2));
    }

    // ------------------------------------------------------------------ spires

    static void spire(Colony c, BlockPos base, int height, double radius, int strands) {
        spire(c, base, height, radius, strands, null);
    }

    /** onComplete runs once the whole spire (core, helix strands and crown) is finished. */
    static void spire(Colony c, BlockPos base, int height, double radius, int strands, Runnable onComplete) {
        double bx = base.getX() + 0.5, bz = base.getZ() + 0.5, by = base.getY();
        double ticks = height * 3.5;
        int[] remaining = {strands + 1};
        Runnable done = () -> {
            if (--remaining[0] == 0) crownSpire(c, base.up(height), radius, onComplete);
        };

        List<BlockPos> core = Paths.line(new Vec3d(bx, by, bz), new Vec3d(bx, by + height, bz));
        c.spawn(new PathBuilder(c, core, TRUNK, rate(core.size(), ticks), 6, true, 0, done));

        double turns = 2.0 + height / 12.0;
        for (int s = 0; s < strands; s++) {
            final double off = s * Math.PI * 2 / strands;
            List<BlockPos> strand = Paths.sample(u -> {
                double a = u * turns * Math.PI * 2 + off;
                double rr = radius * (1 - 0.7 * u);
                return new Vec3d(bx + Math.cos(a) * rr, by + u * height, bz + Math.sin(a) * rr);
            }, height * 14);
            c.spawn(new PathBuilder(c, strand, ROOTS, rate(strand.size(), ticks), 7, false, 0, done));

            // spokes tying the helix to the core
            for (int k = 1; k <= 4; k++) {
                double f = k / 5.0;
                double a = f * turns * Math.PI * 2 + off;
                double rr = radius * (1 - 0.7 * f);
                Vec3d from = new Vec3d(bx, by + f * height, bz);
                Vec3d to = new Vec3d(bx + Math.cos(a) * rr, by + f * height, bz + Math.sin(a) * rr);
                c.spawn(new PathBuilder(c, Paths.line(from, to), ROOTS, 1.0, 0, false, (int) (f * ticks) + 4, null));
            }
        }
    }

    private static void crownSpire(Colony c, BlockPos top, double radius, Runnable onComplete) {
        boolean big = radius > 3.5;
        pod(c, top.up(2), big ? 3 : 2, true);
        antenna(c, top.up(big ? 6 : 5), big ? 8 : 5);
        c.addBeacon(top.up(3));
        Build.lightning(c.world, top.up(2));
        if (onComplete != null) onComplete.run();
    }

    // ------------------------------------------------------------------ arches

    static void arch(Colony c, BlockPos a) {
        Random r = c.random;
        double ang = r.nextDouble() * Math.PI * 2;
        double span = 9 + r.nextDouble() * 8;
        int bx = a.getX() + (int) Math.round(Math.cos(ang) * span);
        int bz = a.getZ() + (int) Math.round(Math.sin(ang) * span);
        BlockPos b = Build.surface(c.world, bx, a.getY(), bz, 6, 8);
        if (b == null) return;

        double h = 4 + span * 0.35;
        Vec3d va = Vec3d.ofCenter(a), vb = Vec3d.ofCenter(b);
        List<BlockPos> path = Paths.sample(
                u -> new Vec3d(MathHelper.lerp(u, va.x, vb.x),
                        MathHelper.lerp(u, va.y, vb.y) + h * 4 * u * (1 - u),
                        MathHelper.lerp(u, va.z, vb.z)),
                (int) (span * 7));
        BlockPos apex = path.get(path.size() / 2);
        c.spawn(new PathBuilder(c, path, ROOTS, rate(path.size(), span * 2.5), 7, true, 0, () -> {
            dangle(c, apex.down(2), 2 + r.nextInt(3));
            c.addBeacon(apex.up());
        }));
    }

    // ------------------------------------------------------------------ crystals

    static void crystals(Colony c, BlockPos base) {
        Random r = c.random;
        ServerWorld w = c.world;
        int n = 3 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            BlockPos g = Build.surface(w, base.getX() + ox, base.getY(), base.getZ() + oz, 3, 4);
            if (g == null) continue;
            int h = 3 + r.nextInt(6);
            List<BlockPos> col = Paths.line(Vec3d.ofCenter(g), Vec3d.ofCenter(g.up(h - 1)));
            c.spawn(new PathBuilder(c, col, CRYSTAL, 0.8, 0, false, i * 5, () -> {
                Build.place(w, g.up(h), Blocks.AMETHYST_CLUSTER.getDefaultState());
                w.playSound(null, g, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.5f,
                        0.7f + r.nextFloat() * 0.8f);
                Fx.spawn(w, ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + h, g.getZ() + 0.5, 10, 0.3, 0.4, 0.3, 0.08);
            }));
        }
    }

    // ------------------------------------------------------------------ small things

    /** A curling stalk that reaches up into the air and ends in a glowing bulb. */
    static void stalk(Colony c, BlockPos base) {
        if (c.stalks >= c.cfg.maxStalks || c.structureBudget < 100) return;
        c.stalks++;
        Random r = c.random;
        ServerWorld w = c.world;
        double len = 4 + r.nextInt(6);
        double a = r.nextDouble() * Math.PI * 2;
        double curl = 1.5 + r.nextDouble() * 2.5;
        double bx = base.getX() + 0.5, by = base.getY(), bz = base.getZ() + 0.5;
        List<BlockPos> p = Paths.sample(
                u -> new Vec3d(bx + Math.cos(a) * curl * u * u, by + len * u, bz + Math.sin(a) * curl * u * u),
                (int) (len * 6));
        BlockPos end = p.get(p.size() - 1);
        c.spawn(new PathBuilder(c, p, ROOTS, 0.9, 0, false, 0, () -> {
            Build.place(w, end.up(), glow(r));
            Fx.spawn(w, ParticleTypes.SPORE_BLOSSOM_AIR, end.getX() + 0.5, end.getY() + 1, end.getZ() + 0.5, 12, 0.8, 0.5, 0.8, 0);
        }));
    }

    /** A hollow egg pod: warped shell with a glowing heart. */
    static void pod(Colony c, BlockPos center, int radius, boolean fx) {
        Random r = c.random;
        ServerWorld w = c.world;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius + 0.3) continue;
                    BlockState st = null;
                    if (d < 0.6) {
                        st = Blocks.VERDANT_FROGLIGHT.getDefaultState();
                    } else if (d > radius - 0.9) {
                        float f = r.nextFloat();
                        if (f < 0.08f) st = Blocks.SHROOMLIGHT.getDefaultState();
                        else if (f < 0.82f) st = SKIN;
                    }
                    if (st != null && c.structureBudget > 0 && Build.place(w, center.add(dx, dy, dz), st)) {
                        c.structureBudget--;
                    }
                }
            }
        }
        if (fx) {
            double x = center.getX() + 0.5, y = center.getY() + 0.5, z = center.getZ() + 0.5;
            Fx.spawn(w, ParticleTypes.END_ROD, x, y, z, 30, radius, radius, radius, 0.1);
            Fx.spawn(w, ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 40, radius + 1, radius, radius + 1, 0);
            w.playSound(null, center, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.BLOCKS, 3.0f, 0.7f);
        }
    }

    private static void dangle(Colony c, BlockPos from, int len) {
        ServerWorld w = c.world;
        Random r = c.random;
        List<BlockPos> path = Paths.line(Vec3d.ofCenter(from), Vec3d.ofCenter(from.down(len)));
        c.spawn(new PathBuilder(c, path, ROOTS, 1.0, 0, false, 0, () -> {
            Build.place(w, from.down(len + 1), glow(r));
            Build.sparkle(w, from.down(len + 1));
        }));
    }

    private static void antenna(Colony c, BlockPos from, int len) {
        List<BlockPos> path = Paths.line(Vec3d.ofCenter(from), Vec3d.ofCenter(from.up(len)));
        c.spawn(new PathBuilder(c, path, Blocks.END_ROD.getDefaultState(), 1.0, 0, false, 0, null));
    }

    private static List<BlockPos> groundLine(Colony c, BlockPos base, double ang, int len) {
        List<BlockPos> out = new ArrayList<>();
        for (int s = 1; s <= len; s++) {
            BlockPos g = Build.surface(c.world,
                    base.getX() + (int) Math.round(Math.cos(ang) * s), base.getY(),
                    base.getZ() + (int) Math.round(Math.sin(ang) * s), 2, 3);
            if (g == null) break;
            out.add(g);
        }
        return out;
    }

    private Structures() {}
}
