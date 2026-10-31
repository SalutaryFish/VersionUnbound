package net.salutaryfish.versionunbound.testmod.hooks;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/** Logs "[hooks] PASS|FAIL <check>" lines and a summary per group of checks. */
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
            LOGGER.info("[hooks] PASS {}", what);
        } else {
            failures.add(what);
            LOGGER.error("[hooks] FAIL {}", what);
        }
    }

    void check(String what, Object actual, Object expected) {
        count.incrementAndGet();
        if (Objects.equals(actual, expected)) {
            LOGGER.info("[hooks] PASS {}", what);
        } else {
            failures.add(what);
            LOGGER.error("[hooks] FAIL {}: expected {}, got {}", what, expected, actual);
        }
    }

    /** Runs a check that fails if it throws, so one broken API doesn't hide the checks after it. */
    void check(String what, CheckedCondition condition) {
        boolean passed;
        try {
            passed = condition.test();
        } catch (Throwable t) {
            LOGGER.error("[hooks] {} threw", what, t);
            passed = false;
        }
        check(what, passed);
    }

    void summarize() {
        if (failures.isEmpty()) {
            LOGGER.info("[hooks] all {} {} passed", count.get(), group);
        } else {
            LOGGER.error("[hooks] {} of {} {} FAILED: {}", failures.size(), count.get(), group, failures);
        }
    }

    @FunctionalInterface
    interface CheckedCondition {
        boolean test() throws Throwable;
    }
}
