package com.alienroots;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The whole show: impact warning ring, asteroid falls with a comet tail, explodes,
 * lightning + shockwave, an alien core appears, then the Colony takes over.
 */
public class AsteroidStrike implements Effects.Effect {
    // ---- tweak these ----
    private static final double SPEED = 2.2;            // blocks per tick
    private static final float EXPLOSION_POWER = 4.5f;  // TNT is 4.0
    // ---------------------

    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.25f, 0.95f, 0.45f), 3.0f);
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.55f, 0.2f, 0.9f), 2.5f);
    private static final DustParticleEffect WARNING = new DustParticleEffect(new Vector3f(1.0f, 0.25f, 0.35f), 1.5f);

    private enum Phase { FALLING, AFTERMATH, GROWING }

    private final ServerWorld world;
    private final Random random;
    private final BlockPos target;
    private final Vec3d velocity;
    private final double startDistance;
    private Vec3d pos;
    private Phase phase = Phase.FALLING;
    private int age;
    private BlockPos impact;
    private Colony colony;

    private AsteroidStrike(ServerWorld world, BlockPos target, Vec3d start) {
        this.world = world;
        this.random = world.getRandom();
        this.target = target;
        this.pos = start;
        Vec3d to = Vec3d.ofCenter(target);
        this.velocity = to.subtract(start).normalize().multiply(SPEED);
        this.startDistance = Math.max(1, start.distanceTo(to));
    }

    public static void launch(ServerWorld world, BlockPos target) {
        Random r = world.getRandom();
        double angle = r.nextDouble() * Math.PI * 2;
        double horizontal = 35 + r.nextDouble() * 20;
        double height = Math.max(20, Math.min(85, world.getTopY() - 2 - target.getY()));
        Vec3d start = Vec3d.ofCenter(target).add(Math.cos(angle) * horizontal, height, Math.sin(angle) * horizontal);

        // a tear opens in the sky where the asteroid arrives
        Fx.spawn(world, ParticleTypes.FLASH, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        Fx.spawn(world, ParticleTypes.REVERSE_PORTAL, start.x, start.y, start.z, 120, 3, 3, 3, 0.3);
        for (int k = 0; k < 40; k++) {
            double a = Math.PI * 2 * k / 40;
            Fx.spawn(world, PURPLE, start.x + Math.cos(a) * 5, start.y, start.z + Math.sin(a) * 5, 1, 0, 0, 0, 0);
        }
        world.playSound(null, start.x, start.y, start.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.AMBIENT, 8.0f, 0.6f);

        Effects.add(new AsteroidStrike(world, target, start));
    }

    @Override
    public ServerWorld world() {
        return world;
    }

    @Override
    public boolean tick() {
        switch (phase) {
            case FALLING -> fall();
            case AFTERMATH -> aftermath();
            case GROWING -> {
                return colony.tick();
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ falling

    private void fall() {
        Vec3d prev = pos;
        pos = pos.add(velocity);
        age++;

        drawWarning();

        for (int i = 1; i <= 3; i++) {
            Vec3d p = prev.lerp(pos, i / 3.0);
            drawAsteroid(p, i == 3);

            // Only look at blocks near the target (that area is surely loaded)
            if (p.y < target.getY() + 14) {
                BlockPos bp = BlockPos.ofFloored(p);
                if (world.getChunkManager().isChunkLoaded(bp.getX() >> 4, bp.getZ() >> 4)) {
                    BlockState s = world.getBlockState(bp);
                    if (!s.getCollisionShape(world, bp).isEmpty() || p.y <= target.getY()) {
                        crash(BlockPos.ofFloored(prev.lerp(pos, (i - 1) / 3.0)));
                        return;
                    }
                }
            }
        }

        if (age % 3 == 0) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT, 6.0f, 0.5f);
        }
        if (age > 300) crash(target.up()); // safety net
    }

    /** A red ring on the ground that tightens as the asteroid gets closer. */
    private void drawWarning() {
        Vec3d to = Vec3d.ofCenter(target);
        double frac = MathHelper.clamp(pos.distanceTo(to) / startDistance, 0, 1);
        double rr = 2.5 + 7 * frac;
        double y = target.getY() + 1.1;
        for (int k = 0; k < 28; k++) {
            double a = Math.PI * 2 * k / 28 + age * 0.15;
            Fx.spawn(world, WARNING, to.x + Math.cos(a) * rr, y, to.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
        }
        Fx.spawn(world, ParticleTypes.END_ROD, to.x, y + 2, to.z, 1, 0.2, 2, 0.2, 0.01);
    }

    private void drawAsteroid(Vec3d p, boolean heavy) {
        if (heavy) {
            // glowing alien rock
            Fx.spawn(world, GREEN, p.x, p.y, p.z, 10, 0.7, 0.7, 0.7, 0);
            Fx.spawn(world, PURPLE, p.x, p.y, p.z, 8, 0.9, 0.9, 0.9, 0);
            Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 4, 0.6, 0.6, 0.6, 0.03);
            Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.8, 0.8, 0.8, 0.3);
            // long shimmering comet tail
            for (int k = 1; k <= 8; k++) {
                Vec3d back = p.subtract(velocity.multiply(k * 0.6));
                double spread = 0.2 + 0.05 * k;
                Fx.spawn(world, Build.GLOWDUST, back.x, back.y, back.z, 2, spread, spread, spread, 0);
            }
        }
        // blazing trail
        Fx.spawn(world, ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.5, 0.5, 0.5, 0.02);
        Fx.spawn(world, ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.4, 0.4, 0.4, 0.01);
        Fx.spawn(world, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.05);
    }

    // ------------------------------------------------------------------ impact

    private void crash(BlockPos at) {
        impact = at;
        Vec3d c = Vec3d.ofCenter(at);

        world.createExplosion(null, c.x, c.y, c.z, EXPLOSION_POWER, World.ExplosionSourceType.TNT);

        Fx.spawn(world, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        Fx.spawn(world, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 3, 2, 1, 2, 0);
        Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1, c.z, 200, 3, 3, 3, 0.4);
        Fx.spawn(world, GREEN, c.x, c.y + 1, c.z, 120, 3, 2, 3, 0);
        Fx.spawn(world, PURPLE, c.x, c.y + 1, c.z, 120, 3, 2, 3, 0);
        Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 80, 2, 2, 2, 0.5);
        Fx.spawn(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.getDefaultState()),
                c.x, c.y + 1, c.z, 200, 2, 2, 2, 0.8);

        // three cosmetic lightning strikes around the crater
        for (int k = 0; k < 3; k++) {
            double a = Math.PI * 2 * k / 3 + random.nextDouble();
            Build.lightning(world, at.add((int) Math.round(Math.cos(a) * 6), 0, (int) Math.round(Math.sin(a) * 6)));
        }

        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.AMBIENT, 10.0f, 0.7f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 10.0f, 0.5f);

        phase = Phase.AFTERMATH;
        age = 0;
    }

    private void aftermath() {
        age++;
        Vec3d c = Vec3d.ofCenter(impact);

        // expanding shockwave ring that rolls along the terrain
        double r = age * 1.8;
        if (r <= 32) {
            int n = (int) Math.max(24, r * 3.5);
            for (int k = 0; k < n; k++) {
                double a = (Math.PI * 2 * k) / n;
                double x = c.x + Math.cos(a) * r;
                double z = c.z + Math.sin(a) * r;
                BlockPos g = Build.surface(world, MathHelper.floor(x), impact.getY(), MathHelper.floor(z), 6, 8);
                if (g == null) continue;
                double y = g.getY() + 0.3;
                Fx.spawn(world, ParticleTypes.CLOUD, x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
                if (k % 3 == 0) Fx.spawn(world, GREEN, x, y + 0.3, z, 1, 0.2, 0.3, 0.2, 0);
            }
        }

        // alien beam shooting up from the crater
        Fx.spawn(world, ParticleTypes.END_ROD, c.x, c.y + 8, c.z, 12, 0.4, 8, 0.4, 0.03);

        if (age == 10) {
            BlockPos core = placeCore();
            world.playSound(null, core, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 8.0f, 0.5f);
            Fx.spawn(world, ParticleTypes.GLOW, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, 60, 1.5, 1, 1.5, 0.1);
            colony = new Colony(world, core);
            phase = Phase.GROWING;
        }
    }

    private BlockPos placeCore() {
        BlockPos ground = Build.surface(world, impact.getX(), impact.getY(), impact.getZ(), 4, 14);
        if (ground == null) ground = impact;

        BlockPos below = ground.down();
        BlockState bs = world.getBlockState(below);
        if (bs.getHardness(world, below) >= 0 && world.getBlockEntity(below) == null) {
            world.setBlockState(below, Blocks.CRYING_OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        world.setBlockState(ground, Blocks.VERDANT_FROGLIGHT.getDefaultState(), Block.NOTIFY_LISTENERS);

        // little crown of amethyst around the core
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos n = ground.offset(d);
            BlockPos nb = n.down();
            if (random.nextFloat() < 0.7f
                    && world.getBlockState(n).isAir()
                    && world.getBlockState(nb).isSideSolidFullSquare(world, nb, Direction.UP)) {
                world.setBlockState(n, Blocks.AMETHYST_CLUSTER.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        return ground;
    }
}
