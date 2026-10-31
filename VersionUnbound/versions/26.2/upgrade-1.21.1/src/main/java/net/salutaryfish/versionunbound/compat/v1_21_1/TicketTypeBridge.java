package net.salutaryfish.versionunbound.compat.v1_21_1;

import java.util.Comparator;
import net.minecraft.server.level.TicketType;

/**
 * 1.21.1's {@code TicketType.create}. A 1.21.1 region ticket counted for both loading and ticking (the distance manager
 * gave it to its ticket tracker and to its ticking tracker), so the 26.2 type gets {@code FLAG_LOADING} and
 * {@code FLAG_SIMULATION}. The name and comparator have no 26.2 counterpart (26.2 names a type by registering it, and
 * its tickets carry no value to compare); the type isn't registered, since mods make theirs after the registries froze.
 */
public final class TicketTypeBridge {
    private static final int FLAGS = TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION;

    private TicketTypeBridge() {}

    /** {@code create(String, Comparator<T>)}: no timeout. */
    public static TicketType create(String name, Comparator<?> comparator) {
        return new TicketType(TicketType.NO_TIMEOUT, FLAGS);
    }

    /** {@code create(String, Comparator<T>, int lifespan)}: the lifespan in ticks is 26.2's timeout. */
    public static TicketType create(String name, Comparator<?> comparator, int lifespan) {
        return new TicketType(lifespan, FLAGS);
    }
}
