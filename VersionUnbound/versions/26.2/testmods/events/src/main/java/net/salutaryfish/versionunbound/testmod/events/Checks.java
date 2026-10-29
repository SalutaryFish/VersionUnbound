package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/** Logs "[events] PASS|FAIL <check>" lines and a summary per group of checks. */
final class Checks {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String group;
    private final AtomicInteger count = new AtomicInteger();
    private final List<String> failures = new CopyOnWriteArrayList<>();

    Checks(String group) {
        this.group = group;
    }

    void check(String what, boolean passed) {
        count.incrementAndGet();
        if (passed) {
            LOGGER.info("[events] PASS {}", what);
        } else {
            failures.add(what);
            LOGGER.error("[events] FAIL {}", what);
        }
    }

    void check(String what, Object actual, Object expected) {
        count.incrementAndGet();
        if (Objects.equals(actual, expected)) {
            LOGGER.info("[events] PASS {}", what);
        } else {
            failures.add(what);
            LOGGER.error("[events] FAIL {}: expected {}, got {}", what, expected, actual);
        }
    }

    /** Runs a check that fails if it throws, so one broken API doesn't hide the checks after it. */
    void check(String what, CheckedCondition condition) {
        boolean passed;
        try {
            passed = condition.test();
        } catch (Throwable t) {
            LOGGER.error("[events] {} threw", what, t);
            passed = false;
        }
        check(what, passed);
    }

    void summarize() {
        if (failures.isEmpty()) {
            LOGGER.info("[events] all {} {} passed", count.get(), group);
        } else {
            LOGGER.error("[events] {} of {} {} FAILED: {}", failures.size(), count.get(), group, failures);
        }
    }

    @FunctionalInterface
    interface CheckedCondition {
        boolean test() throws Exception;
    }
}
