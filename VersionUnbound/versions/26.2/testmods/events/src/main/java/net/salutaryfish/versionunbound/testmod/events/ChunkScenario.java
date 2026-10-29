package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import org.slf4j.Logger;

/**
 * Server-side chunk checks, from the chunks the server loads, saves and unloads on its own (and one it's made to save):
 * {@code ChunkEvent.Load} (with a listener asking for the chunk being loaded, which NeoForge's bypass answers instead of
 * deadlocking), {@code ChunkDataEvent.Load} and {@code Save}, {@code ChunkEvent.Unload} (every chunk unloads while the
 * server stops), ticket level updates, and the watch events for a player. Chunks are told apart by level and position;
 * chunk data has no level, so a chunk read from disk as a full chunk is recognized by the chunk object it wraps.
 */
final class ChunkScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    // How many loads ask for their own chunk; each would hang the server without the bypass.
    private static final int SELF_LOOKUPS = 4;
    // NeoForge's ChunkAccess.getLevel, which vanilla's ChunkAccess (what this testmod compiles against) doesn't have.
    // findVirtual resolves it as a mod's invokevirtual does. Null if it's missing, which fails the data load check.
    private static final MethodHandle CHUNK_LEVEL = chunkLevel();

    private static MethodHandle chunkLevel() {
        try {
            return MethodHandles.publicLookup().findVirtual(ChunkAccess.class, "getLevel", MethodType.methodType(Level.class));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[events] ChunkAccess.getLevel is missing", e);
            return null;
        }
    }

    private final Checks checks;
    private final Checks playerChecks;
    private final Set<LevelChunk> readAsFull = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<String> loaded = ConcurrentHashMap.newKeySet();
    private final Set<String> unloaded = ConcurrentHashMap.newKeySet();
    private final Set<String> saved = ConcurrentHashMap.newKeySet();
    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicInteger newLoads = new AtomicInteger();
    private final AtomicInteger dataLoads = new AtomicInteger();
    private final AtomicInteger ticketUpdates = new AtomicInteger();
    private final AtomicInteger selfLookups = new AtomicInteger();
    private final AtomicBoolean loadsConsistent = new AtomicBoolean(true);
    private final AtomicBoolean newChunksConsistent = new AtomicBoolean(true);
    private final AtomicBoolean selfLookupsAnswered = new AtomicBoolean(true);
    private final AtomicBoolean dataLoadsConsistent = new AtomicBoolean(true);
    private final AtomicBoolean savesConsistent = new AtomicBoolean(true);
    private final AtomicBoolean unloadsConsistent = new AtomicBoolean(true);
    private final AtomicBoolean ticketUpdatesConsistent = new AtomicBoolean(true);
    private final Set<String> watched = ConcurrentHashMap.newKeySet();
    private final AtomicInteger watches = new AtomicInteger();
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicInteger unwatches = new AtomicInteger();
    private final AtomicInteger watchedUnwatches = new AtomicInteger();
    private final AtomicBoolean watchesConsistent = new AtomicBoolean(true);
    private final AtomicBoolean sentAfterWatch = new AtomicBoolean(true);
    private String markedForSave;

    ChunkScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    private static String key(ServerLevel level, ChunkPos pos) {
        return level.dimension().identifier().getPath() + "@" + pos.x() + "," + pos.z();
    }

    void load(ChunkEvent.Load event) {
        ServerLevel level = (ServerLevel) event.getLevel();
        LevelChunk chunk = event.getChunk();
        String key = key(level, chunk.getPos());
        loads.incrementAndGet();
        loaded.add(key);
        if (chunk.getLevel() != level) loadsConsistent.set(false);
        // A chunk read from disk as a full chunk isn't new; one generated (or finished from a saved proto chunk) is.
        if (event.isNewChunk()) newLoads.incrementAndGet();
        if (event.isNewChunk() == readAsFull.remove(chunk)) newChunksConsistent.set(false);
        if (selfLookups.getAndIncrement() < SELF_LOOKUPS) {
            ChunkAccess looked = level.getChunk(chunk.getPos().x(), chunk.getPos().z());
            LevelChunk now = level.getChunkSource().getChunkNow(chunk.getPos().x(), chunk.getPos().z());
            if (looked != chunk || now != chunk) selfLookupsAnswered.set(false);
        }
    }

    void dataLoad(ChunkDataEvent.Load event) {
        dataLoads.incrementAndGet();
        ChunkAccess chunk = event.getChunk();
        if (chunk instanceof ImposterProtoChunk imposter) readAsFull.add(imposter.getWrapped());
        // Read chunks are proto chunks (a full one wrapped), which have no level; NeoForge's ChunkAccess.getLevel says so.
        try {
            if (event.getData() == null || event.getLevel() != null || (Level) CHUNK_LEVEL.invoke(chunk) != null) dataLoadsConsistent.set(false);
        } catch (Throwable t) {
            LOGGER.error("[events] ChunkAccess.getLevel threw", t);
            dataLoadsConsistent.set(false);
        }
    }

    void dataSave(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getData() == null) {
            savesConsistent.set(false);
            return;
        }
        saved.add(key(level, event.getChunk().getPos()));
    }

    void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getChunk().getLevel() != level) {
            unloadsConsistent.set(false);
            return;
        }
        String key = key(level, event.getChunk().getPos());
        if (!loaded.contains(key)) unloadsConsistent.set(false);
        unloaded.add(key);
    }

    void ticketLevelUpdated(ChunkTicketLevelUpdatedEvent event) {
        ticketUpdates.incrementAndGet();
        if (event.getOldTicketLevel() == event.getNewTicketLevel() || event.getLevel() == null)
            ticketUpdatesConsistent.set(false);
    }

    void watch(ChunkWatchEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer) || event.getLevel() == null) watchesConsistent.set(false);
        String key = key(event.getLevel(), event.getPos());
        switch (event) {
            case ChunkWatchEvent.Watch watch -> {
                watches.incrementAndGet();
                watched.add(key);
                if (watch.getChunk().getPos().pack() != watch.getPos().pack()) watchesConsistent.set(false);
            }
            case ChunkWatchEvent.Sent sentEvent -> {
                sent.incrementAndGet();
                if (!watched.contains(key)) sentAfterWatch.set(false);
            }
            // Leaving a level unwatches every position in the player's view, also those whose chunk wasn't ready to
            // send yet (never watched), as in NeoForge.
            case ChunkWatchEvent.UnWatch unwatch -> {
                unwatches.incrementAndGet();
                if (watched.contains(key)) watchedUnwatches.incrementAndGet();
            }
            default -> {}
        }
    }

    /** Once the server has started (and loaded the forced chunk): its chunks so far, and one marked to be saved on stop. */
    void started(ServerLevel overworld, boolean newWorld) {
        checks.check("ChunkEvent.Load fires for server chunks", loads.get() > 0);
        checks.check("a ChunkEvent.Load listener can get the chunk being loaded (NeoForge's deadlock bypass)",
                selfLookups.get() > 0 && selfLookupsAnswered.get());
        checks.check(newWorld ? "a new world's chunks load as new chunks" : "ChunkDataEvent.Load fires for chunks read from disk",
                newWorld ? newLoads.get() > 0 : dataLoads.get() > 0);
        LevelChunk chunk = overworld.getChunk(0, 0);
        chunk.markUnsaved();
        markedForSave = key(overworld, chunk.getPos());
    }

    /** After the server stopped: the whole run's events were consistent, and everything saved and unloaded. */
    void stopped() {
        checks.check("ChunkEvent.Load carries the chunk's level", loadsConsistent.get());
        checks.check("ChunkEvent.Load's isNewChunk is false exactly for chunks read from disk as full chunks", newChunksConsistent.get());
        checks.check("ChunkDataEvent.Load has the chunk data, and no level (read chunks are proto chunks)", dataLoadsConsistent.get());
        checks.check("ChunkTicketLevelUpdatedEvent fires as ticket levels change", ticketUpdates.get() > 0 && ticketUpdatesConsistent.get());
        checks.check("ChunkDataEvent.Save fires for a chunk with changes, with its level and data", saved.contains(markedForSave) && savesConsistent.get());
        checks.check("ChunkEvent.Unload fires for every loaded chunk as the server stops", !unloaded.isEmpty() && unloadsConsistent.get() && unloaded.containsAll(loaded));
        LOGGER.info("[events] chunks: {} loads ({} new), {} data loads, {} saved, {} unloaded, {} ticket level updates",
                loads.get(), newLoads.get(), dataLoads.get(), saved.size(), unloaded.size(), ticketUpdates.get());
    }

    /** With a player: the chunks around it were watched and sent, and the dimension changes unwatched them. */
    void finishPlayer() {
        playerChecks.check("ChunkWatchEvent.Watch fires for the chunks around the player", watches.get() > 0 && watchesConsistent.get());
        playerChecks.check("ChunkWatchEvent.Sent fires once a watched chunk is sent", sent.get() > 0 && sentAfterWatch.get());
        playerChecks.check("ChunkWatchEvent.UnWatch fires for the watched chunks when the player leaves a level", watchedUnwatches.get() > 0);
        LOGGER.info("[events] chunk watching: {} watched, {} sent, {} unwatched ({} of them watched)", watches.get(), sent.get(), unwatches.get(), watchedUnwatches.get());
    }
}
