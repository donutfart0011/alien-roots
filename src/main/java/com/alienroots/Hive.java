package com.alienroots;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * The Hive Seed's master plan:
 * 1. four roots crawl out to four tower sites and four huge towers grow
 * 2. when all four are done their tips link up with braided energy arcs (ring, then the diagonals)
 * 3. when everything is connected a beam erupts into the sky
 * 4. the beam rains down a meteor shower of small alien seeds around the area
 */
final class Hive implements Colony.Builder {
    // ---- tweak these ----
    private static final int SPACING = 42;          // how far each tower is from the core
    private static final int TOWER_HEIGHT = 40;     // blocks above the highest tower base
    private static final double TOWER_RADIUS = 5.0;
    private static final int METEORS = 24;          // small seeds in the shower
    private static final int METEOR_INTERVAL = 12;  // ticks between seeds
    private static final int BEAM_TICKS = 330;
    // ---------------------

    private static final int LINK_TICKS = 35;
    private static final int DIAG_TICKS = 40;
    private static final int[][] RING = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};

    private enum Phase { GROW, LINK, DIAG, BEAM, END }

    private final Colony c;
    private final ServerWorld world;
    private final BlockPos[] sites = new BlockPos[4];
    private final Vec3d[] tips = new Vec3d[4];
    private final boolean[] started = new boolean[4];
    private final int topY;
    private int towersDone;
    private Phase phase = Phase.GROW;
    private int t;
    private int waited;
    private int meteorsLeft = METEORS;
    private Vec3d center;

    Hive(Colony c) {
        this.c = c;
        this.world = c.world;
        double theta = c.random.nextDouble() * Math.PI * 2;
        int maxY = c.origin.getY();
        for (int k = 0; k < 4; k++) {
            double a = theta + k * Math.PI / 2;
            int x = c.origin.getX() + (int) Math.round(Math.cos(a) * SPACING);
            int z = c.origin.getZ() + (int) Math.round(Math.sin(a) * SPACING);
            BlockPos s = Build.surface(world, x, c.origin.getY(), z, 14, 24);
            sites[k] = s != null ? s : new BlockPos(x, c.origin.getY(), z);
            maxY = Math.max(maxY, sites[k].getY());
        }
        // every tower reaches the same absolute height so their tips line up
        topY = Math.min(world.getTopY() - 30, maxY + TOWER_HEIGHT);
    }

    BlockPos site(int k) {
        return sites[k];
    }

    /** A root arrived (or gave up): raise the tower for it. */
    void onTipEnd(int k, BlockPos tipPos) {
        if (k < 0 || k > 3 || started[k]) return;
        started[k] = true;
        BlockPos base = Build.surface(world, sites[k].getX(), sites[k].getY(), sites[k].getZ(), 14, 24);
        if (base == null) base = tipPos;
        final BlockPos b = base;
        final int height = Math.max(30, topY - b.getY());
        Structures.spire(c, b, height, TOWER_RADIUS, 3, () -> towerDone(k, b, height));
    }

    private void towerDone(int k, BlockPos base, int height) {
        // top of the end-rod antenna that crowns a big spire
        tips[k] = Vec3d.ofCenter(base.up(height + 14));
        towersDone++;
        if (towersDone == 4) {
            center = tips[0].add(tips[1]).add(tips[2]).add(tips[3]).multiply(0.25);
            phase = Phase.LINK;
            t = 0;
        }
    }

    @Override
    public boolean tick() {
        t++;
        switch (phase) {
            case GROW -> {
                // safety: don't wait forever if a root got stuck somewhere strange
                if (++waited > 3600) phase = Phase.END;
            }
            case LINK -> link();
            case DIAG -> diag();
            case BEAM -> beam();
            case END -> {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ linking the towers

    private void link() {
        int idx = t / LINK_TICKS;
        if (idx >= RING.length) {
            phase = Phase.DIAG;
            t = 0;
            return;
        }
        int local = t % LINK_TICKS;
        if (local == 0) {
            Vec3d a = tips[RING[idx][0]];
            world.playSound(null, a.x, a.y, a.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.AMBIENT,
                    10.0f, 0.6f + 0.15f * idx);
        }
        double prog = (local + 1) / (double) LINK_TICKS;
        for (int i = 0; i <= idx; i++) {
            if (i < idx && t % 2 != 0) continue;
            draw(RING[i][0], RING[i][1], i < idx ? 1.0 : prog, t);
        }
        if (local == LINK_TICKS - 1) connectFlash(tips[RING[idx][1]]);
    }

    private void diag() {
        if (t == 1) {
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.AMBIENT, 12.0f, 1.2f);
        }
        double prog = Math.min(1.0, t / (double) DIAG_TICKS);
        if (t % 2 == 0) {
            for (int[] l : RING) draw(l[0], l[1], 1.0, t);
        }
        draw(0, 2, prog, t);
        draw(1, 3, prog, t);
        if (t >= DIAG_TICKS) {
            connectFlash(center);
            phase = Phase.BEAM;
            t = 0;
        }
    }

    private void connectFlash(Vec3d p) {
        Fx.spawn(world, ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Fx.spawn(world, ParticleTypes.END_ROD, p.x, p.y, p.z, 40, 1.5, 1.5, 1.5, 0.3);
        Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 40, 1.5, 1.5, 1.5, 0.5);
        world.playSound(null, p.x, p.y, p.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 10.0f, 1.2f);
    }

    /** Two energy strands braiding around each other between two tower tips. */
    private void draw(int ia, int ib, double frac, int time) {
        Vec3d a = tips[ia], b = tips[ib];
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 1) return;
        Vec3d dn = d.multiply(1.0 / len);
        Vec3d p1 = Math.abs(dn.y) > 0.9 ? new Vec3d(1, 0, 0) : dn.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d p2 = dn.crossProduct(p1).normalize();
        Random r = c.random;

        int n = (int) (len * frac * 1.2);
        for (int s = 0; s <= n; s++) {
            double u = s / (len * 1.2);
            Vec3d base = a.add(d.multiply(u));
            double ph = u * len * 0.55 - time * 0.5;
            for (int strand = 0; strand < 2; strand++) {
                double q = ph + strand * Math.PI;
                Vec3d pt = base.add(p1.multiply(Math.cos(q) * 0.7)).add(p2.multiply(Math.sin(q) * 0.7));
                Fx.spawn(world, Build.GLOWDUST, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            if (s % 3 == 0) Fx.spawn(world, ParticleTypes.END_ROD, base.x, base.y, base.z, 1, 0.15, 0.15, 0.15, 0.01);
            if (r.nextInt(14) == 0) Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, base.x, base.y, base.z, 3, 0.4, 0.4, 0.4, 0.2);
        }
        // glowing head of the arc
        Vec3d head = a.add(d.multiply(frac));
        Fx.spawn(world, ParticleTypes.END_ROD, head.x, head.y, head.z, 6, 0.4, 0.4, 0.4, 0.05);
        Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, head.x, head.y, head.z, 3, 0.3, 0.3, 0.3, 0.03);
    }

    // ------------------------------------------------------------------ beam + meteor shower

    private void beam() {
        double cx = center.x, cy = center.y, cz = center.z;

        if (t == 1) {
            Fx.spawn(world, ParticleTypes.FLASH, cx, cy, cz, 1, 0, 0, 0, 0);
            Fx.spawn(world, ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 2, 2, 2, 2, 0);
            for (Vec3d tip : tips) Build.lightning(world, BlockPos.ofFloored(tip));
            world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.AMBIENT, 12.0f, 0.6f);
            world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 12.0f, 0.5f);
            world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 12.0f, 0.5f);
        }
        if (t % 20 == 0) {
            world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 12.0f, 0.8f);
        }

        if (t % 2 == 0) {
            // keep the web of arcs alive
            for (int[] l : RING) draw(l[0], l[1], 1.0, t);
            draw(0, 2, 1.0, t);
            draw(1, 3, 1.0, t);

            // the great beam, with a spiral of energy winding up it
            for (int h = 0; h <= 150; h += 3) {
                double y = cy + h;
                double w = 1.6 * (1 - h / 260.0);
                Fx.spawn(world, ParticleTypes.END_ROD, cx, y, cz, 1, 0.15, 0, 0.15, 0.02);
                Fx.spawn(world, Build.GLOWDUST, cx, y, cz, 2, w, 0.8, w, 0);
                double ang = h * 0.3 + t * 0.4;
                Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, cx + Math.cos(ang) * 2.2, y, cz + Math.sin(ang) * 2.2, 1, 0, 0, 0, 0);
            }
            // down to the core
            for (double y = cy; y > c.origin.getY() + 1; y -= 2.5) {
                Fx.spawn(world, Build.GLOWDUST, cx, y, cz, 1, 0.5, 0.5, 0.5, 0);
                Fx.spawn(world, ParticleTypes.END_ROD, cx, y, cz, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
        if (t % 4 == 0) {
            // each tower tip fires its own pillar too
            for (Vec3d tip : tips) {
                for (int h = 0; h <= 70; h += 3) {
                    Fx.spawn(world, ParticleTypes.END_ROD, tip.x, tip.y + h, tip.z, 1, 0.2, 0, 0.2, 0.02);
                }
            }
        }

        if (t >= 30 && meteorsLeft > 0 && t % METEOR_INTERVAL == 0) launchSeed();

        if (t >= BEAM_TICKS) {
            Fx.spawn(world, ParticleTypes.FLASH, cx, cy, cz, 1, 0, 0, 0, 0);
            Fx.spawn(world, ParticleTypes.END_ROD, cx, cy, cz, 120, 3, 3, 3, 0.6);
            world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 10.0f, 0.6f);
            phase = Phase.END;
        }
    }

    /** Send one small alien seed streaking out of the beam toward the surrounding land. */
    private void launchSeed() {
        Random r = c.random;
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = 16 + r.nextDouble() * 62;
            int x = c.origin.getX() + (int) Math.round(Math.cos(a) * dist);
            int z = c.origin.getZ() + (int) Math.round(Math.sin(a) * dist);
            BlockPos g = Build.surface(world, x, c.origin.getY(), z, 24, 32);
            if (g == null || nearTower(g)) continue;

            Vec3d start = new Vec3d(center.x + (r.nextDouble() - 0.5) * 3,
                    Math.min(center.y + 110, world.getTopY() - 2),
                    center.z + (r.nextDouble() - 0.5) * 3);
            AsteroidStrike.launchSeed(world, g.down(), start);
            meteorsLeft--;
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 8.0f, 0.5f);
            return;
        }
        meteorsLeft--; // no good spot this time; skip one
    }

    private boolean nearTower(BlockPos p) {
        for (BlockPos s : sites) {
            double dx = p.getX() - s.getX(), dz = p.getZ() - s.getZ();
            if (dx * dx + dz * dz < 12 * 12) return true;
        }
        return false;
    }
}
