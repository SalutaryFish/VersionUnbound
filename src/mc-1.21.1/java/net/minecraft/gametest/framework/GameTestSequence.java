package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class GameTestSequence {
    final GameTestInfo parent;
    private final List<GameTestEvent> events = Lists.newArrayList();
    private long lastTick;

    GameTestSequence(GameTestInfo testInfo) {
        this.parent = testInfo;
        this.lastTick = testInfo.getTick();
    }

    public GameTestSequence thenWaitUntil(Runnable task) {
        this.events.add(GameTestEvent.create(task));
        return this;
    }

    public GameTestSequence thenWaitUntil(long expectedDelay, Runnable task) {
        this.events.add(GameTestEvent.create(expectedDelay, task));
        return this;
    }

    public GameTestSequence thenIdle(int tick) {
        return this.thenExecuteAfter(tick, () -> {});
    }

    public GameTestSequence thenExecute(Runnable task) {
        this.events.add(GameTestEvent.create(() -> this.executeWithoutFail(task)));
        return this;
    }

    public GameTestSequence thenExecuteAfter(int tick, Runnable task) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + tick) {
                throw new GameTestAssertException("Test timed out before sequence completed");
            }

            this.executeWithoutFail(task);
        }));
        return this;
    }

    public GameTestSequence thenExecuteFor(int tick, Runnable task) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + tick) {
                this.executeWithoutFail(task);
                throw new GameTestAssertException("Test timed out before sequence completed");
            }
        }));
        return this;
    }

    public void thenSucceed() {
        this.events.add(GameTestEvent.create(this.parent::succeed));
    }

    public void thenFail(Supplier<Exception> exception) {
        this.events.add(GameTestEvent.create(() -> this.parent.fail(exception.get())));
    }

    public GameTestSequence.Condition thenTrigger() {
        GameTestSequence.Condition condition = new GameTestSequence.Condition();
        this.events.add(GameTestEvent.create(() -> condition.trigger(this.parent.getTick())));
        return condition;
    }

    public void tickAndContinue(long tick) {
        try {
            this.tick(tick);
        } catch (GameTestAssertException var4) {
        }
    }

    public void tickAndFailIfNotComplete(long ticks) {
        try {
            this.tick(ticks);
        } catch (GameTestAssertException gameTestAssertException) {
            this.parent.fail(gameTestAssertException);
        }
    }

    private void executeWithoutFail(Runnable task) {
        try {
            task.run();
        } catch (GameTestAssertException gameTestAssertException) {
            this.parent.fail(gameTestAssertException);
        }
    }

    private void tick(long tick) {
        Iterator<GameTestEvent> iterator = this.events.iterator();

        while (iterator.hasNext()) {
            GameTestEvent gameTestEvent = iterator.next();
            gameTestEvent.assertion.run();
            iterator.remove();
            long l = tick - this.lastTick;
            long m = this.lastTick;
            this.lastTick = tick;
            if (gameTestEvent.expectedDelay != null && gameTestEvent.expectedDelay != l) {
                this.parent
                    .fail(
                        new GameTestAssertException(
                            "Succeeded in invalid tick: expected " + (m + gameTestEvent.expectedDelay) + ", but current tick is " + tick
                        )
                    );
                break;
            }
        }
    }

    public class Condition {
        private static final long NOT_TRIGGERED = -1L;
        private long triggerTime = -1L;

        void trigger(long triggerTime) {
            if (this.triggerTime != -1L) {
                throw new IllegalStateException("Condition already triggered at " + this.triggerTime);
            }

            this.triggerTime = triggerTime;
        }

        public void assertTriggeredThisTick() {
            long l = GameTestSequence.this.parent.getTick();
            if (this.triggerTime != l) {
                if (this.triggerTime == -1L) {
                    throw new GameTestAssertException("Condition not triggered (t=" + l + ")");
                } else {
                    throw new GameTestAssertException("Condition triggered at " + this.triggerTime + ", (t=" + l + ")");
                }
            }
        }
    }
}
