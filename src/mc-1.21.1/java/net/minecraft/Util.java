package net.minecraft;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SingleKeyCache;
import net.minecraft.util.TimeSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public class Util {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final ExecutorService BACKGROUND_EXECUTOR = makeExecutor("Main");
    private static final ExecutorService IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final ExecutorService DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(fileSystemProvider -> fileSystemProvider.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    private static Consumer<String> thePauser = string -> {};

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> property, Object value) {
        return property.getName((T)value);
    }

    public static String makeDescriptionId(String type, @Nullable ResourceLocation id) {
        return id == null ? type + ".unregistered_sadface" : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return timeSource.getAsLong();
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static ExecutorService makeExecutor(String serviceName) {
        int i = Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, getMaxThreads());
        ExecutorService executorService;
        if (i <= 0) {
            executorService = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger atomicInteger = new AtomicInteger(1);
            executorService = new ForkJoinPool(i, forkJoinPool -> {
                ForkJoinWorkerThread forkJoinWorkerThread = new ForkJoinWorkerThread(forkJoinPool) {
                    @Override
                    protected void onTermination(Throwable throwOnTermination) {
                        if (throwOnTermination != null) {
                            Util.LOGGER.warn("{} died", this.getName(), throwOnTermination);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(throwOnTermination);
                    }
                };
                forkJoinWorkerThread.setName("Worker-" + serviceName + "-" + atomicInteger.getAndIncrement());
                return forkJoinWorkerThread;
            }, Util::onThreadException, true);
        }

        return executorService;
    }

    private static int getMaxThreads() {
        String string = System.getProperty("max.bg.threads");
        if (string != null) {
            try {
                int i = Integer.parseInt(string);
                if (i >= 1 && i <= 255) {
                    return i;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", string, 255);
            } catch (NumberFormatException numberFormatException) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", string, 255);
            }
        }

        return 255;
    }

    public static ExecutorService backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static ExecutorService ioPool() {
        return IO_POOL;
    }

    public static ExecutorService nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        shutdownExecutor(BACKGROUND_EXECUTOR);
        shutdownExecutor(IO_POOL);
    }

    private static void shutdownExecutor(ExecutorService service) {
        service.shutdown();

        boolean bl;
        try {
            bl = service.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            bl = false;
        }

        if (!bl) {
            service.shutdownNow();
        }
    }

    private static ExecutorService makeIoExecutor(String name, boolean daemon) {
        AtomicInteger atomicInteger = new AtomicInteger(1);
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(name + atomicInteger.getAndIncrement());
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        });
    }

    public static void throwAsRuntime(Throwable throwable) {
        throw throwable instanceof RuntimeException ? (RuntimeException)throwable : new RuntimeException(throwable);
    }

    private static void onThreadException(Thread thread, Throwable throwable) {
        pauseInIde(throwable);
        if (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof ReportedException reportedException) {
            Bootstrap.realStdoutPrintln(reportedException.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", thread), throwable);
    }

    @Nullable
    public static Type<?> fetchChoiceType(TypeReference type, String choiceName) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(type, choiceName);
    }

    @Nullable
    private static Type<?> doFetchChoiceType(TypeReference type, String choiceName) {
        Type<?> type2 = null;

        try {
            type2 = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))
                .getChoiceType(type, choiceName);
        } catch (IllegalArgumentException illegalArgumentException) {
            LOGGER.error("No data fixer registered for {}", choiceName);
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw illegalArgumentException;
            }
        }

        return type2;
    }

    public static Runnable wrapThreadWithTaskName(String name, Runnable task) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String string2 = thread.getName();
            thread.setName(name);

            try {
                task.run();
            } finally {
                thread.setName(string2);
            }
        } : task;
    }

    public static <V> Supplier<V> wrapThreadWithTaskName(String name, Supplier<V> task) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String string2 = thread.getName();
            thread.setName(name);

            try {
                return task.get();
            } finally {
                thread.setName(string2);
            }
        } : task;
    }

    public static <T> String getRegisteredName(Registry<T> registry, T value) {
        ResourceLocation resourceLocation = registry.getKey(value);
        return resourceLocation == null ? "[unregistered]" : resourceLocation.toString();
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> object -> true;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).and((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicates2 = predicates.toArray(Predicate[]::new);
                yield object -> {
                    for (Predicate<T> predicate : predicates2) {
                        if (!predicate.test((T)object)) {
                            return false;
                        }
                    }

                    return true;
                };
            }
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> object -> false;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).or((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicates2 = predicates.toArray(Predicate[]::new);
                yield object -> {
                    for (Predicate<T> predicate : predicates2) {
                        if (predicate.test((T)object)) {
                            return true;
                        }
                    }

                    return false;
                };
            }
        };
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> list) {
        if (width == 1) {
            return true;
        }

        int i = width / 2;

        for (int j = 0; j < height; j++) {
            for (int k = 0; k < i; k++) {
                int l = width - 1 - k;
                T object = list.get(k + j * width);
                T object2 = list.get(l + j * width);
                if (!object.equals(object2)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static Util.OS getPlatform() {
        String string = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (string.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (string.contains("mac")) {
            return Util.OS.OSX;
        } else if (string.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return string.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI uRI = new URI(uri);
        String string = uRI.getScheme();
        if (string == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        } else {
            String string2 = string.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(string2)) {
                throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
            } else {
                return uRI;
            }
        }
    }

    public static Stream<String> getVmArguments() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return runtimeMXBean.getInputArguments().stream().filter(string -> string.startsWith("-X"));
    }

    public static <T> T lastOf(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static <T> T findNextInIterable(Iterable<T> iterable, @Nullable T element) {
        Iterator<T> iterator = iterable.iterator();
        T object = iterator.next();
        if (element != null) {
            T object2 = object;

            while (object2 != element) {
                if (iterator.hasNext()) {
                    object2 = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return object;
    }

    public static <T> T findPreviousInIterable(Iterable<T> iterable, @Nullable T current) {
        Iterator<T> iterator = iterable.iterator();
        T object = null;

        while (iterator.hasNext()) {
            T object2 = iterator.next();
            if (object2 == current) {
                if (object == null) {
                    object = iterator.hasNext() ? Iterators.getLast(iterator) : current;
                }
                break;
            }

            object = object2;
        }

        return object;
    }

    public static <T> T make(Supplier<T> supplier) {
        return supplier.get();
    }

    public static <T> T make(T object, Consumer<? super T> consumer) {
        consumer.accept(object);
        return object;
    }

    /**
     * Takes a list of futures and returns a future of list that completes when all of them succeed or any of them error,
     */
    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (futures.size() == 1) {
            return futures.get(0).thenApply(List::of);
        }

        CompletableFuture<Void> completableFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return completableFuture.thenApply(void_ -> futures.stream().map(CompletableFuture::join).toList());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, completableFuture::completeExceptionally).applyToEither(completableFuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, throwable -> {
            if (completableFuture.completeExceptionally(throwable)) {
                for (CompletableFuture<? extends V> completableFuture2 : completableFutures) {
                    completableFuture2.cancel(true);
                }
            }
        }).applyToEither(completableFuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(
        List<? extends CompletableFuture<? extends V>> completableFutures, Consumer<Throwable> throwableConsumer
    ) {
        List<V> list = Lists.newArrayListWithCapacity(completableFutures.size());
        CompletableFuture<?>[] completableFutures2 = new CompletableFuture[completableFutures.size()];
        completableFutures.forEach(completableFuture -> {
            int i = list.size();
            list.add(null);
            completableFutures2[i] = completableFuture.whenComplete((object, throwable) -> {
                if (throwable != null) {
                    throwableConsumer.accept(throwable);
                } else {
                    list.set(i, (V)object);
                }
            });
        });
        return CompletableFuture.allOf(completableFutures2).thenApply(void_ -> list);
    }

    public static <T> Optional<T> ifElse(Optional<T> opt, Consumer<T> consumer, Runnable orElse) {
        if (opt.isPresent()) {
            consumer.accept(opt.get());
        } else {
            orElse.run();
        }

        return opt;
    }

    public static <T> Supplier<T> name(Supplier<T> item, Supplier<String> nameSupplier) {
        return item;
    }

    public static Runnable name(Runnable item, Supplier<String> nameSupplier) {
        return item;
    }

    public static void logAndPauseIfInIde(String error) {
        LOGGER.error(error);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(error);
        }
    }

    public static void logAndPauseIfInIde(String message, Throwable error) {
        LOGGER.error(message, error);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }
    }

    public static <T extends Throwable> T pauseInIde(T throwable) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", throwable);
            doPause(throwable.getMessage());
        }

        return throwable;
    }

    public static void setPause(Consumer<String> thePauser) {
        Util.thePauser = thePauser;
    }

    private static void doPause(String message) {
        Instant instant = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean bl = Duration.between(instant, Instant.now()).toMillis() > 500L;
        if (!bl) {
            thePauser.accept(message);
        }
    }

    public static String describeError(Throwable throwable) {
        if (throwable.getCause() != null) {
            return describeError(throwable.getCause());
        } else {
            return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        }
    }

    public static <T> T getRandom(T[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static int getRandom(int[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static <T> T getRandom(List<T> selections, RandomSource random) {
        return selections.get(random.nextInt(selections.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> selections, RandomSource random) {
        return selections.isEmpty() ? Optional.empty() : Optional.of(getRandom(selections, random));
    }

    private static BooleanSupplier createRenamer(Path filePath, Path newName) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(filePath, newName);
                    return true;
                } catch (IOException iOException) {
                    Util.LOGGER.error("Failed to rename", iOException);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + filePath + " to " + newName;
            }
        };
    }

    private static BooleanSupplier createDeleter(Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(filePath);
                    return true;
                } catch (IOException iOException) {
                    Util.LOGGER.warn("Failed to delete", iOException);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + filePath;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... suppliers) {
        for (BooleanSupplier booleanSupplier : suppliers) {
            if (!booleanSupplier.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", booleanSupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int maxTries, String actionName, BooleanSupplier... suppliers) {
        for (int i = 0; i < maxTries; i++) {
            if (executeInSequence(suppliers)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", actionName, i, maxTries);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", actionName);
        return false;
    }

    public static void safeReplaceFile(Path current, Path latest, Path oldBackup) {
        safeReplaceOrMoveFile(current, latest, oldBackup, false);
    }

    public static boolean safeReplaceOrMoveFile(Path current, Path latest, Path oldBackup, boolean bl) {
        if (Files.exists(current)
            && !runWithRetries(10, "create backup " + oldBackup, createDeleter(oldBackup), createRenamer(current, oldBackup), createFileCreatedCheck(oldBackup))
            )
         {
            return false;
        } else if (!runWithRetries(10, "remove old " + current, createDeleter(current), createFileDeletedCheck(current))) {
            return false;
        } else if (!runWithRetries(10, "replace " + current + " with " + latest, createRenamer(latest, current), createFileCreatedCheck(current)) && !bl) {
            runWithRetries(10, "restore " + current + " from " + oldBackup, createRenamer(oldBackup, current), createFileCreatedCheck(current));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String text, int cursorPos, int direction) {
        int i = text.length();
        if (direction >= 0) {
            for (int j = 0; cursorPos < i && j < direction; j++) {
                if (Character.isHighSurrogate(text.charAt(cursorPos++)) && cursorPos < i && Character.isLowSurrogate(text.charAt(cursorPos))) {
                    cursorPos++;
                }
            }
        } else {
            for (int j = direction; cursorPos > 0 && j < 0; j++) {
                cursorPos--;
                if (Character.isLowSurrogate(text.charAt(cursorPos)) && cursorPos > 0 && Character.isHighSurrogate(text.charAt(cursorPos - 1))) {
                    cursorPos--;
                }
            }
        }

        return cursorPos;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> expectedSize) {
        return string2 -> expectedSize.accept(prefix + string2);
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] is = stream.limit(size + 1).toArray();
        if (is.length != size) {
            Supplier<String> supplier = () -> "Input is not a list of " + size + " ints";
            return is.length >= size ? DataResult.error(supplier, Arrays.copyOf(is, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(is);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int expectedSize) {
        long[] ls = stream.limit(expectedSize + 1).toArray();
        if (ls.length != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " longs";
            return ls.length >= expectedSize ? DataResult.error(supplier, Arrays.copyOf(ls, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(ls);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int expectedSize) {
        if (list.size() != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " elements";
            return list.size() >= expectedSize ? DataResult.error(supplier, list.subList(0, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedException) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path fromDirectory, Path toDirectory, Path filePath) throws IOException {
        Path path = fromDirectory.relativize(filePath);
        Path path2 = toDirectory.resolve(path);
        Files.copy(filePath, path2);
    }

    public static String sanitizeName(String fileName, CharPredicate characterValidator) {
        return fileName.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(i -> characterValidator.test((char)i) ? Character.toString((char)i) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> computeValue) {
        return new SingleKeyCache<>(computeValue);
    }

    public static <T, R> Function<T, R> memoize(Function<T, R> memoFunction) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T key) {
                return this.cache.computeIfAbsent(key, memoFunction);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + memoFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(BiFunction<T, U, R> memoBiFunction) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T key1, U key2) {
                return this.cache.computeIfAbsent(Pair.of(key1, key2), pair -> memoBiFunction.apply(pair.getFirst(), pair.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + memoBiFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> objectArrayList = stream.collect(ObjectArrayList.toList());
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList intArrayList = IntArrayList.wrap(stream.toArray());
        int i = intArrayList.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            intArrayList.set(j - 1, intArrayList.set(k, intArrayList.getInt(j - 1)));
        }

        return intArrayList;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> objectArrayList = new ObjectArrayList<>(array);
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> objectArrayList = new ObjectArrayList<>(list);
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int i = list.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            list.set(j - 1, list.set(k, list.get(j - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> task) {
        return blockUntilDone(task, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> task, Predicate<T> donePredicate) {
        BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();
        T object = task.apply(blockingQueue::add);

        while (!donePredicate.test(object)) {
            try {
                Runnable runnable = blockingQueue.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException interruptedException) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int i = blockingQueue.size();
        if (i > 0) {
            LOGGER.warn("Tasks left in queue: {}", i);
        }

        return object;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> list) {
        int i = list.size();
        if (i < 8) {
            return list::indexOf;
        }

        Object2IntMap<T> object2IntMap = new Object2IntOpenHashMap<>(i);
        object2IntMap.defaultReturnValue(-1);

        for (int j = 0; j < i; j++) {
            object2IntMap.put(list.get(j), j);
        }

        return object2IntMap;
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> list) {
        int i = list.size();
        if (i < 8) {
            ReferenceList<T> referenceList = new ReferenceImmutableList<>(list);
            return referenceList::indexOf;
        }

        Reference2IntMap<T> reference2IntMap = new Reference2IntOpenHashMap<>(i);
        reference2IntMap.defaultReturnValue(-1);

        for (int j = 0; j < i; j++) {
            reference2IntMap.put(list.get(j), j);
        }

        return reference2IntMap;
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> type, UnaryOperator<Dynamic<?>> operator) {
        Dynamic<?> dynamic = (Dynamic<?>)typed.write().getOrThrow();
        return readTypedOrThrow(type, operator.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data) {
        return readTypedOrThrow(type, data, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data, boolean partial) {
        DataResult<Typed<T>> dataResult = type.readTyped(data).map(Pair::getFirst);

        try {
            return partial ? dataResult.getPartialOrThrow(IllegalStateException::new) : dataResult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException illegalStateException) {
            CrashReport crashReport = CrashReport.forThrowable(illegalStateException, "Reading type");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Info");
            crashReportCategory.setDetail("Data", data);
            crashReportCategory.setDetail("Type", type);
            throw new ReportedException(crashReport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T value) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(value).build();
    }

    public static <T> List<T> copyAndAdd(T value, List<T> list) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(value).addAll(list).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI uRI) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", uRI.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI uRI) {
                return new String[]{"open", uRI.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(final String telemetryName) {
            this.telemetryName = telemetryName;
        }

        public void openUri(URI uri) {
            try {
                Process process = AccessController.doPrivileged(() -> Runtime.getRuntime().exec(this.getOpenUriArguments(uri)));
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (PrivilegedActionException | IOException exception) {
                Util.LOGGER.error("Couldn't open location '{}'", uri, exception);
            }
        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String string = uri.toString();
            if ("file".equals(uri.getScheme())) {
                string = string.replace("file:", "file://");
            }

            return new String[]{"xdg-open", string};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (URISyntaxException | IllegalArgumentException exception) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, exception);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
