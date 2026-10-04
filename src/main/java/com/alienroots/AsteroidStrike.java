package com.alienroots;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
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
 * The whole show: impact warning ring, a real meteor entity falls with a comet tail, huge fiery
 * explosion, lightning + shockwave that ignites and scorches the ground, an alien core appears,
 * then the Colony takes over.
 */
public class AsteroidStrike implements Effects.Effect {

    /** The three kinds of meteor. Tweak the numbers here. */
    public enum Kind {
        //     scale power fireR fireP bolts scorchR speed warnBase warnExtra coreDelay ringMax
        ROOTS(1.0f, 9.0f, 16, 0.30f, 3, 8, 2.2, 2.5, 7.0, 10, 32),
        HIVE(1.0f, 9.0f, 16, 0.30f, 3, 8, 2.2, 2.5, 7.0, 10, 32),
        SEED(0.4f, 2.5f, 5, 0.35f, 0, 0, 2.6, 1.2, 2.5, 4, 10);

        final float scale;        // meteor size (also scales the particle effects)
        final float power;        // explosion power (TNT is 4.0)
        final int fireRadius;     // how far fire is scattered
        final float fireChance;   // chance per spot inside fireRadius
        final int bolts;          // cosmetic lightning strikes at impact
        final int scorchRadius;   // blackstone/magma scorch marks
        final double speed;       // blocks per tick
        final double warnBase, warnExtra;
        final int coreDelay;      // ticks after impact before the core + colony appear
        final int ringMax;        // shockwave size

        Kind(float scale, float power, int fireRadius, float fireChance, int bolts, int scorchRadius,
             double speed, double warnBase, double warnExtra, int coreDelay, int ringMax) {
            this.scale = scale;
            this.power = power;
            this.fireRadius = fireRadius;
            this.fireChance = fireChance;
            this.bolts = bolts;
            this.scorchRadius = scorchRadius;
            this.speed = speed;
            this.warnBase = warnBase;
            this.warnExtra = warnExtra;
            this.coreDelay = coreDelay;
            this.ringMax = ringMax;
        }
    }

    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.25f, 0.95f, 0.45f), 3.0f);
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.55f, 0.2f, 0.9f), 2.5f);
    private static final DustParticleEffect WARNING = new DustParticleEffect(new Vector3f(1.0f, 0.25f, 0.35f), 1.5f);

    private enum Phase { FALLING, AFTERMATH, GROWING }

    private final ServerWorld world;
    private final Random random;
    private final BlockPos target;
    private final Kind kind;
    private final Vec3d velocity;
    private final double startDistance;
    private final MeteorEntity meteor;
    private Vec3d pos;
    private Phase phase = Phase.FALLING;
    private int age;
    private BlockPos impact;
    private Colony colony;

    private AsteroidStrike(ServerWorld world, BlockPos target, Vec3d start, Kind kind) {
        this.world = world;
        this.random = world.getRandom();
        this.target = target;
        this.kind = kind;
        this.pos = start;
        Vec3d to = Vec3d.ofCenter(target);
        this.velocity = to.subtract(start).normalize().multiply(kind.speed);
        this.startDistance = Math.max(1, start.distanceTo(to));

        // the real, visible meteor
        this.meteor = new MeteorEntity(ModEntities.METEOR, world);
        this.meteor.setMeteorScale(kind.scale);
        this.meteor.attached = true;
        this.meteor.setPosition(start.x, start.y - 2, start.z);
        world.spawnEntity(this.meteor);
    }

    /** Called by the seed items: a meteor comes in from a random direction. */
    public static void launch(ServerWorld world, BlockPos target, Kind kind) {
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

        Effects.add(new AsteroidStrike(world, target, start, kind));
    }

    /** Called by the Hive: a small seed streaks out of the beam from a given sky position. */
    static void launchSeed(ServerWorld world, BlockPos target, Vec3d start) {
        Effects.add(new AsteroidStrike(world, target, start, Kind.SEED));
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
        meteor.setPosition(pos.x, pos.y - 2, pos.z);

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
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT,
                    kind == Kind.SEED ? 3.0f : 6.0f, 0.5f);
        }
        if (age > 300) crash(target.up()); // safety net
    }

    /** A red ring on the ground that tightens as the asteroid gets closer. */
    private void drawWarning() {
        Vec3d to = Vec3d.ofCenter(target);
        double frac = MathHelper.clamp(pos.distanceTo(to) / startDistance, 0, 1);
        double rr = kind.warnBase + kind.warnExtra * frac;
        double y = target.getY() + 1.1;
        for (int k = 0; k < 28; k++) {
            double a = Math.PI * 2 * k / 28 + age * 0.15;
            Fx.spawn(world, WARNING, to.x + Math.cos(a) * rr, y, to.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
        }
        if (kind != Kind.SEED) Fx.spawn(world, ParticleTypes.END_ROD, to.x, y + 2, to.z, 1, 0.2, 2, 0.2, 0.01);
    }

    private void drawAsteroid(Vec3d p, boolean heavy) {
        double s = kind.scale;
        if (heavy) {
            // glow around the rock
            Fx.spawn(world, GREEN, p.x, p.y, p.z, 10, 0.9 * s + 0.2, 0.9 * s + 0.2, 0.9 * s + 0.2, 0);
            Fx.spawn(world, PURPLE, p.x, p.y, p.z, 8, 1.1 * s + 0.2, 1.1 * s + 0.2, 1.1 * s + 0.2, 0);
            Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 4, 0.8 * s, 0.8 * s, 0.8 * s, 0.03);
            Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 1.0 * s, 1.0 * s, 1.0 * s, 0.3);
            // long shimmering comet tail
            for (int k = 1; k <= 8; k++) {
                Vec3d back = p.subtract(velocity.multiply(k * 0.6));
                double spread = (0.2 + 0.05 * k) * (0.5 + 0.5 * s);
                Fx.spawn(world, Build.GLOWDUST, back.x, back.y, back.z, 2, spread, spread, spread, 0);
            }
        }
        // blazing trail
        Fx.spawn(world, ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.7 * s, 0.7 * s, 0.7 * s, 0.03);
        Fx.spawn(world, ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.5 * s, 0.5 * s, 0.5 * s, 0.01);
        if (kind != Kind.SEED) Fx.spawn(world, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.05);
    }

    // ------------------------------------------------------------------ impact

    private int q(int count) {
        return Math.max(4, Math.round(count * kind.scale));
    }

    private void crash(BlockPos at) {
        meteor.discard();
        impact = at;
        Vec3d c = Vec3d.ofCenter(at);
        double s = kind.scale;

        // big explosion that also scatters fire
        world.createExplosion(null, c.x, c.y, c.z, kind.power, true, World.ExplosionSourceType.TNT);

        Fx.spawn(world, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        Fx.spawn(world, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, q(5), 3 * s, 1.5 * s, 3 * s, 0);
        Fx.spawn(world, ParticleTypes.FLAME, c.x, c.y + 1, c.z, q(250), 4 * s, 3 * s, 4 * s, 0.5);
        Fx.spawn(world, ParticleTypes.LAVA, c.x, c.y + 1, c.z, q(100), 4 * s, 2 * s, 4 * s, 0);
        Fx.spawn(world, ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1, c.z, q(200), 3 * s, 3 * s, 3 * s, 0.4);
        Fx.spawn(world, GREEN, c.x, c.y + 1, c.z, q(120), 3 * s, 2 * s, 3 * s, 0);
        Fx.spawn(world, PURPLE, c.x, c.y + 1, c.z, q(120), 3 * s, 2 * s, 3 * s, 0);
        Fx.spawn(world, ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, q(80), 2 * s, 2 * s, 2 * s, 0.5);
        Fx.spawn(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.getDefaultState()),
                c.x, c.y + 1, c.z, q(200), 2 * s, 2 * s, 2 * s, 0.8);

        // cosmetic lightning strikes around the crater
        for (int k = 0; k < kind.bolts; k++) {
            double a = Math.PI * 2 * k / Math.max(1, kind.bolts) + random.nextDouble();
            Build.lightning(world, at.add((int) Math.round(Math.cos(a) * 7), 0, (int) Math.round(Math.sin(a) * 7)));
        }

        float vol = kind == Kind.SEED ? 4.0f : 10.0f;
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.AMBIENT, vol, 0.7f);
        if (kind != Kind.SEED) {
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, vol, 0.5f);
        }

        phase = Phase.AFTERMATH;
        age = 0;
    }

    private void aftermath() {
        age++;
        Vec3d c = Vec3d.ofCenter(impact);

        // expanding shockwave ring that rolls along the terrain, igniting and scorching as it goes
        double r = age * 1.8;
        if (r <= kind.ringMax) {
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

                if (r <= kind.scorchRadius && random.nextFloat() < 0.35f) scorch(g.down());
                if (r <= kind.fireRadius && random.nextFloat() < kind.fireChance) {
                    Build.place(world, g, Blocks.FIRE.getDefaultState());
                }
            }
        }

        // alien beam shooting up from the crater
        if (kind != Kind.SEED) Fx.spawn(world, ParticleTypes.END_ROD, c.x, c.y + 8, c.z, 12, 0.4, 8, 0.4, 0.03);

        if (age == kind.coreDelay) {
            BlockPos core = placeCore();
            world.playSound(null, core, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS,
                    kind == Kind.SEED ? 3.0f : 8.0f, 0.5f);
            Fx.spawn(world, ParticleTypes.GLOW, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, q(60), 1.5, 1, 1.5, 0.1);

            Colony.Config cfg = switch (kind) {
                case ROOTS -> Colony.Config.main();
                case HIVE -> Colony.Config.hive();
                case SEED -> Colony.Config.mini();
            };
            colony = new Colony(world, core, cfg);
            phase = Phase.GROWING;
        }
    }

    /** Burn marks: dirt, sand and stone near the impact turn to blackstone, with a few glowing magma patches. */
    private void scorch(BlockPos ground) {
        if (!Build.loaded(world, ground)) return;
        BlockState gs = world.getBlockState(ground);
        if (gs.isIn(BlockTags.DIRT) || gs.isIn(BlockTags.SAND) || gs.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            BlockState burnt = random.nextFloat() < 0.3f ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            world.setBlockState(ground, burnt, Block.NOTIFY_LISTENERS);
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
