package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

public record ScheduledTick<T>(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
    public static final Comparator<ScheduledTick<?>> DRAIN_ORDER = (scheduledTick, scheduledTick2) -> {
        int i = Long.compare(scheduledTick.triggerTick, scheduledTick2.triggerTick);
        if (i != 0) {
            return i;
        }

        i = scheduledTick.priority.compareTo(scheduledTick2.priority);
        return i != 0 ? i : Long.compare(scheduledTick.subTickOrder, scheduledTick2.subTickOrder);
    };
    public static final Comparator<ScheduledTick<?>> INTRA_TICK_DRAIN_ORDER = (scheduledTick, scheduledTick2) -> {
        int i = scheduledTick.priority.compareTo(scheduledTick2.priority);
        return i != 0 ? i : Long.compare(scheduledTick.subTickOrder, scheduledTick2.subTickOrder);
    };
    public static final Strategy<ScheduledTick<?>> UNIQUE_TICK_HASH = new Strategy<ScheduledTick<?>>() {
        public int hashCode(ScheduledTick<?> scheduledTick) {
            return 31 * scheduledTick.pos().hashCode() + scheduledTick.type().hashCode();
        }

        public boolean equals(@Nullable ScheduledTick<?> first, @Nullable ScheduledTick<?> second) {
            if (first == second) {
                return true;
            } else {
                return first != null && second != null ? first.type() == second.type() && first.pos().equals(second.pos()) : false;
            }
        }
    };

    public ScheduledTick(T type, BlockPos pos, long triggerTick, long subTickOrder) {
        this(type, pos, triggerTick, TickPriority.NORMAL, subTickOrder);
    }

    public ScheduledTick {
        pos = pos.immutable();
    }

    public static <T> ScheduledTick<T> probe(T type, BlockPos pos) {
        return new ScheduledTick<>(type, pos, 0L, TickPriority.NORMAL, 0L);
    }
}
