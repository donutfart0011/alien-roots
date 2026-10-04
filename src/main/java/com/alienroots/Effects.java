package com.alienroots;

import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny scheduler: every active effect gets ticked once per world tick until it reports it is done. */
public final class Effects {
    public interface Effect {
        ServerWorld world();

        /** @return true when finished and should be removed */
        boolean tick();
    }

    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final List<Effect> PENDING = new ArrayList<>(); // added while ticking (e.g. meteor shower seeds)

    public static void add(Effect effect) {
        PENDING.add(effect);
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    public static void tick(ServerWorld world) {
        if (!PENDING.isEmpty()) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Effect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            if (e.world() != world) continue;
            if (e.tick()) it.remove();
        }
    }

    private Effects() {}
}
