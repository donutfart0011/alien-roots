package com.alienroots;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The living colony. Root tips crawl over ANY surface in 3D (ground, cliffs, ceilings, tree trunks),
 * fork, and at their ends the colony builds alien structures. Builders grow a little each tick.
 */
final class Colony {
    interface Builder {
        /** @return true when finished */
        boolean tick();
    }

    // ---- tweak these ----
    static final int STEP_INTERVAL = 2;       // root tips advance every N ticks (higher = slower)
    static final int MAX_ROOT_BLOCKS = 6000;  // crawling root blocks per strike
    static final int MAX_TIPS = 48;           // simultaneous crawling tips
    static final int MAX_RADIUS = 110;        // how far roots may travel from the core (blocks)
    static final int STARTING_TIPS = 10;
    static final int MAX_STRUCTURES = 40;     // trees/arches/spires/etc. besides the Heart
    static final int MAX_STALKS = 80;         // little upward stalks
    static final int STRUCTURE_BUDGET = 16000; // total blocks all structures may use
    // ---------------------

    private static final class Tip {
        BlockPos pos;
        Vec3d heading;
        Vec3i last;
        double lastLen = 1;
        int life;
        int gen;
    }

    final ServerWorld world;
    final Random random;
    final BlockPos origin;
    int structureBudget = STRUCTURE_BUDGET;
    int structures;
    int stalks;
    final List<Vec3d> beacons = new ArrayList<>();

    private final List<Tip> tips = new ArrayList<>();
    private final List<Builder> builders = new ArrayList<>();
    private final List<Builder> pending = new ArrayList<>();
    private int age;
    private int placedRoots;
    private int idleTicks;

    Colony(ServerWorld world, BlockPos origin) {
        this.world = world;
        this.random = world.getRandom();
        this.origin = origin;
        for (int i = 0; i < STARTING_TIPS; i++) {
            Tip t = new Tip();
            t.pos = origin;
            double a = (Math.PI * 2 * i) / STARTING_TIPS + random.nextDouble() * 0.4;
            t.heading = new Vec3d(Math.cos(a), 0, Math.sin(a));
            t.life = 100 + random.nextInt(60);
            tips.add(t);
        }
        Structures.heart(this, origin);
    }

    void spawn(Builder b) {
        pending.add(b);
    }

    void addBeacon(BlockPos p) {
        if (beacons.size() < 60) beacons.add(Vec3d.ofCenter(p));
    }

    /** @return true when everything has finished growing and the afterglow is over */
    boolean tick() {
        age++;
        if (age % STEP_INTERVAL == 0) stepTips();

        Iterator<Builder> it = builders.iterator();
        while (it.hasNext()) {
            if (it.next().tick()) it.remove();
        }
        builders.addAll(pending);
        pending.clear();

        boolean idle = tips.isEmpty() && builders.isEmpty();
        if (!idle && age % 50 == 1) spawn(new Pulse(this));
        if (age % 6 == 0) ambient();

        if (idle) {
            if (++idleTicks > 160) return true; // keep the spores floating for a few more seconds
        } else {
            idleTicks = 0;
        }
        return false;
    }

    // ------------------------------------------------------------------ ambient flair

    private void ambient() {
        if (beacons.isEmpty()) return;
        for (int i = 0; i < Math.min(6, beacons.size()); i++) {
            Vec3d b = beacons.get(random.nextInt(beacons.size()));
            Fx.spawn(world, ParticleTypes.SPORE_BLOSSOM_AIR, b.x, b.y, b.z, 6, 1.5, 1.0, 1.5, 0);
            Fx.spawn(world, ParticleTypes.END_ROD, b.x, b.y + 1, b.z, 2, 0.2, 1.5, 0.2, 0.03);
            if (random.nextInt(8) == 0) Fx.spawn(world, ParticleTypes.GLOW, b.x, b.y, b.z, 4, 1.2, 1.2, 1.2, 0.05);
        }
    }

    // ------------------------------------------------------------------ root tips

    private void stepTips() {
        List<Tip> born = new ArrayList<>();
        Iterator<Tip> it = tips.iterator();
        while (it.hasNext()) {
            Tip t = it.next();
            if (!stepTip(t, born)) {
                endTip(t);
                it.remove();
            }
        }
        for (Tip b : born) {
            if (tips.size() < MAX_TIPS) tips.add(b);
        }
    }

    private static Vec3d rotY(Vec3d v, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        return new Vec3d(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    /** A free block next to natural terrain: that's where a root may grow. */
    private boolean valid(BlockPos p) {
        if (!Build.loaded(world, p)) return false;
        BlockState s = world.getBlockState(p);
        if (!(s.isAir() || s.isReplaceable())) return false;
        if (!world.getFluidState(p).isEmpty()) return false;
        for (Direction d : Direction.values()) {
            if (Build.isSupport(world, p.offset(d))) return true;
        }
        return false;
    }

    private boolean stepTip(Tip t, List<Tip> born) {
        if (placedRoots >= MAX_ROOT_BLOCKS || t.life-- <= 0) return false;
        double dxo = t.pos.getX() - origin.getX(), dzo = t.pos.getZ() - origin.getZ();
        if (dxo * dxo + dzo * dzo > (double) MAX_RADIUS * MAX_RADIUS) return false;

        // wander
        Vec3d h = rotY(t.heading, (random.nextDouble() - 0.5) * 0.9);

        // wall straight ahead? then climb it
        int ax = Math.abs(h.x) > 0.38 ? (int) Math.signum(h.x) : 0;
        int az = Math.abs(h.z) > 0.38 ? (int) Math.signum(h.z) : 0;
        if ((ax != 0 || az != 0) && Build.isSupport(world, t.pos.add(ax, 0, az))) {
            h = new Vec3d(h.x, h.y + 0.9, h.z).normalize();
        }

        // fork
        if (t.life > 15 && random.nextFloat() < 0.07f && tips.size() + born.size() < MAX_TIPS) {
            Tip c = new Tip();
            c.pos = t.pos;
            c.heading = rotY(h, (random.nextBoolean() ? 1 : -1) * (0.6 + random.nextDouble() * 0.7));
            c.life = (int) (t.life * 0.75);
            c.gen = t.gen + 1;
            born.add(c);
        }

        // pick the best neighbouring cell in full 3D (up, down, sideways, diagonal)
        BlockPos best = null;
        int bestDx = 0, bestDy = 0, bestDz = 0;
        double bestScore = -1e9;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (t.last != null && dx == -t.last.getX() && dy == -t.last.getY() && dz == -t.last.getZ()) continue;
                    BlockPos np = t.pos.add(dx, dy, dz);
                    if (!valid(np)) continue;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double score = (h.x * dx + h.y * dy + h.z * dz) / len - (len - 1) * 0.15 + random.nextDouble() * 0.45;
                    if (t.last != null) {
                        score += 0.6 * (t.last.getX() * dx + t.last.getY() * dy + t.last.getZ() * dz) / (t.lastLen * len);
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = np;
                        bestDx = dx;
                        bestDy = dy;
                        bestDz = dz;
                    }
                }
            }
        }
        if (best == null) return false; // dead end

        Vec3d dir = new Vec3d(bestDx, bestDy, bestDz).normalize();
        Vec3d nh = h.add(dir.multiply(0.25));
        t.heading = new Vec3d(nh.x, nh.y * 0.7, nh.z).normalize();
        t.last = new Vec3i(bestDx, bestDy, bestDz);
        t.lastLen = Math.sqrt(bestDx * bestDx + bestDy * bestDy + bestDz * bestDz);
        t.pos = best;
        placeRoot(t, best);
        return true;
    }

    private void placeRoot(Tip t, BlockPos np) {
        if (!Build.place(world, np, Structures.ROOTS)) return;
        placedRoots++;

        // infect the ground below
        BlockPos below = np.down();
        BlockState g = world.getBlockState(below);
        if ((g.isIn(BlockTags.DIRT) || g.isIn(BlockTags.SAND)) && random.nextFloat() < 0.7f) {
            world.setBlockState(below, Blocks.ROOTED_DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        BlockPos above = np.up();
        float f = random.nextFloat();
        if (t.gen == 0 && f < 0.3f) {
            if (Build.place(world, above, Structures.ROOTS)) placedRoots++;
        } else if (f > 0.96f) {
            Build.place(world, above, Structures.glow(random));
        } else if (f > 0.75f) {
            BlockPos side = np.offset(Direction.Type.HORIZONTAL.random(random));
            if (Build.isSupport(world, side.down())) {
                Build.place(world, side, Blocks.MOSS_CARPET.getDefaultState());
            }
        }
        if (random.nextFloat() < 0.02f) Structures.stalk(this, np);

        double x = np.getX() + 0.5, y = np.getY() + 0.6, z = np.getZ() + 0.5;
        Fx.spawn(world, Build.GLOWDUST, x, y, z, 3, 0.3, 0.3, 0.3, 0);
        Fx.spawn(world, ParticleTypes.GLOW, x, y, z, 2, 0.3, 0.4, 0.3, 0.05);
        Fx.spawn(world, ParticleTypes.END_ROD, x, y, z, 1, 0.2, 0.2, 0.2, 0.02);
        if (placedRoots % 6 == 0) {
            world.playSound(null, np, Structures.ROOTS.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS,
                    1.0f, 0.6f + random.nextFloat() * 0.4f);
        }
    }

    /** A root finishes: it blooms, and usually the colony builds something there. */
    private void endTip(Tip t) {
        BlockPos tip = t.pos;
        if (!Build.loaded(world, tip)) return;

        Build.place(world, tip.up(), Blocks.VERDANT_FROGLIGHT.getDefaultState());
        double x = tip.getX() + 0.5, y = tip.getY() + 1.2, z = tip.getZ() + 0.5;
        Fx.spawn(world, ParticleTypes.END_ROD, x, y, z, 20, 0.5, 0.5, 0.5, 0.1);
        Fx.spawn(world, ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 30, 1.5, 1.0, 1.5, 0);
        world.playSound(null, tip, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 2.0f,
                0.8f + random.nextFloat() * 0.8f);

        if (random.nextFloat() < 0.8f) Structures.colonize(this, tip);
    }
}
