package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import org.slf4j.Logger;

/**
 * Level events on the server. Explosions, in the forced chunk once its entities are visible (an explosion only finds
 * entities a level can see): {@code ExplosionEvent.Start} before an explosion (canceled: nothing happens),
 * {@code Detonate} with the entities in reach and the blocks it destroys (an entity removed from the list is spared, a
 * block removed from it stays), and {@code ExplosionKnockbackEvent} for each entity pushed (its knockback is what pushes
 * the entity). Note blocks: {@code NoteBlockEvent.Play} when one plays (its note and instrument; canceled; another
 * instrument plays instead) and, with the player, {@code NoteBlockEvent.Change} when the player tunes one (the old and
 * new note; another note set instead; canceled). The listeners only look at the scenario's explosions and positions.
 */
final class LevelScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final Checks checks;
    private final Checks playerChecks;
    // Explosions by center: what fired, in order, and what the listeners do.
    private final Set<Vec3> centers = Collections.synchronizedSet(new HashSet<>());
    private final List<Object> explosionEvents = new CopyOnWriteArrayList<>();
    private final Set<Vec3> canceledStarts = Collections.synchronizedSet(new HashSet<>());
    private volatile Entity spared;
    private volatile BlockPos kept;
    private final Map<Entity, Vec3> knockbacks = Collections.synchronizedMap(new HashMap<>());
    // Note blocks by position.
    private final Set<BlockPos> notes = Collections.synchronizedSet(new HashSet<>());
    private final List<Object> noteEvents = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> canceledNotes = Collections.synchronizedSet(new HashSet<>());
    private final Map<BlockPos, NoteBlockInstrument> instruments = Collections.synchronizedMap(new HashMap<>());
    private final Map<BlockPos, NoteBlockEvent.Note> changedNotes = Collections.synchronizedMap(new HashMap<>());
    // The note block the player tuned stays for a while: the note it queued plays when the level runs its block events
    // (later, and only while the block is still there). The client gets a note of its own 20 ticks later, once it has
    // settled after the respawn that follows the player scenario, and the block goes 10 ticks after that.
    private static final int CLIENT_NOTE_AT = 20;
    private static final int REMOVE_AT = 30;
    private ServerLevel playerLevel;
    private BlockPos playedByPlayer;
    private int ticksSincePlayer;

    LevelScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void explosionStart(ExplosionEvent.Start event) {
        Vec3 center = event.getExplosion().center();
        if (!centers.contains(center)) return;
        explosionEvents.add(new Started(center, event.getLevel()));
        if (canceledStarts.contains(center)) event.setCanceled(true);
    }

    void explosionDetonate(ExplosionEvent.Detonate event) {
        Vec3 center = event.getExplosion().center();
        if (!centers.contains(center)) return;
        List<BlockPos> blocks = event.getAffectedBlocks();
        explosionEvents.add(new Detonated(center, List.copyOf(event.getAffectedEntities()), List.copyOf(blocks), blocks));
        Entity entity = spared;
        if (entity != null) event.getAffectedEntities().remove(entity);
        BlockPos pos = kept;
        if (pos != null) blocks.remove(pos);
    }

    void explosionKnockback(ExplosionKnockbackEvent event) {
        Vec3 center = event.getExplosion().center();
        if (!centers.contains(center)) return;
        explosionEvents.add(new Knocked(center, event.getAffectedEntity(), event.getKnockbackVelocity(), event.getAffectedBlocks()));
        Vec3 knockback = knockbacks.get(event.getAffectedEntity());
        if (knockback != null) event.setKnockbackVelocity(knockback);
    }

    void notePlay(NoteBlockEvent.Play event) {
        if (event.getLevel().isClientSide() || !notes.contains(event.getPos())) return;
        noteEvents.add(new Played(event.getPos().immutable(), event.getVanillaNoteId(), event.getInstrument()));
        if (canceledNotes.contains(event.getPos())) event.setCanceled(true);
        NoteBlockInstrument instrument = instruments.get(event.getPos());
        if (instrument != null) event.setInstrument(instrument);
    }

    void noteChange(NoteBlockEvent.Change event) {
        if (event.getLevel().isClientSide() || !notes.contains(event.getPos())) return;
        noteEvents.add(new Changed(event.getPos().immutable(), event.getOldNote(), event.getOldOctave(), event.getVanillaNoteId()));
        if (canceledNotes.contains(event.getPos())) event.setCanceled(true);
        NoteBlockEvent.Note note = changedNotes.get(event.getPos());
        if (note != null) event.setNote(note, NoteBlockEvent.Octave.MID);
    }

    /** At server started: note blocks played by hand, around {@code base}, in the air. */
    void run(ServerLevel level, BlockPos base) {
        BlockPos played = base;
        BlockPos canceled = base.offset(2, 0, 0);
        BlockPos changed = base.offset(4, 0, 0);
        List<BlockPos> placed = List.of(played, canceled, changed);
        notes.addAll(placed);
        canceledNotes.add(canceled);
        instruments.put(changed, NoteBlockInstrument.CUSTOM_HEAD);
        try {
            for (BlockPos pos : placed) level.setBlock(pos, Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 5), FLAGS);
            boolean playedNote = level.getBlockState(played).triggerEvent(level, played, 0, 0);
            checks.check("NoteBlockEvent.Play fires when a note block plays, with its note and instrument",
                    playedNote && noteEvents.equals(List.of(new Played(played, 5, NoteBlockInstrument.HARP))));
            noteEvents.clear();
            boolean canceledNote = level.getBlockState(canceled).triggerEvent(level, canceled, 0, 0);
            checks.check("canceling NoteBlockEvent.Play stops the note", !canceledNote && noteEvents.size() == 1);
            noteEvents.clear();
            // A mob head's sound comes from the head above; without one, nothing plays.
            boolean changedNote = level.getBlockState(changed).triggerEvent(level, changed, 0, 0);
            checks.check("the instrument NoteBlockEvent.Play sets is the one that plays", !changedNote && noteEvents.equals(List.of(new Played(changed, 5, NoteBlockInstrument.HARP))));
        } finally {
            noteEvents.clear();
            notes.removeAll(placed);
            for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** With the player: note blocks tuned through the block's own use, around {@code base}, in the air. */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockPos tuned = base.offset(-4, 3, -4);
        BlockPos changed = base.offset(-2, 3, -4);
        BlockPos canceled = base.offset(0, 3, -4);
        List<BlockPos> placed = List.of(tuned, changed, canceled);
        LOGGER.info("[events] the player's note blocks start at {} ({} there before)", tuned, level.getBlockState(tuned));
        notes.addAll(placed);
        changedNotes.put(changed, NoteBlockEvent.Note.A);
        canceledNotes.add(canceled);
        try {
            for (BlockPos pos : placed) level.setBlock(pos, Blocks.NOTE_BLOCK.defaultBlockState(), FLAGS);
            InteractionResult tunedResult = use(player, level, tuned);
            playerChecks.check("NoteBlockEvent.Change fires when a player tunes a note block, with the old and new notes",
                    tunedResult.consumesAction() && note(level, tuned) == 1
                            && noteEvents.equals(List.of(new Changed(tuned, NoteBlockEvent.Note.F_SHARP, NoteBlockEvent.Octave.LOW, 1))));
            noteEvents.clear();
            use(player, level, changed);
            // A in the middle octave: 3 + 12.
            playerChecks.check("the note NoteBlockEvent.Change sets is the one the block gets", note(level, changed) == 15 && noteEvents.size() == 1);
            noteEvents.clear();
            InteractionResult canceledResult = use(player, level, canceled);
            playerChecks.check("canceling NoteBlockEvent.Change fails the use and keeps the note",
                    canceledResult == InteractionResult.FAIL && note(level, canceled) == 0 && noteEvents.size() == 1);
        } finally {
            noteEvents.clear();
            playerLevel = level;
            playedByPlayer = tuned;
            ticksSincePlayer = 0;
            for (BlockPos pos : placed) {
                if (pos.equals(tuned)) continue;
                notes.remove(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Each server tick (Post), for the note block the player tuned. */
    void tick() {
        if (playedByPlayer == null) return;
        ticksSincePlayer++;
        if (ticksSincePlayer == CLIENT_NOTE_AT) {
            playerChecks.check("the note a player tunes plays through the level's block events, firing NoteBlockEvent.Play",
                    noteEvents.contains(new Played(playedByPlayer, 1, NoteBlockInstrument.HARP)));
            playerLevel.blockEvent(playedByPlayer, Blocks.NOTE_BLOCK, 0, 0);
        } else if (ticksSincePlayer == REMOVE_AT) {
            removePlayedByPlayer();
        }
    }

    /** When the server stops: the tuned note block goes, if the server stops before it did. */
    void stopping() {
        if (playedByPlayer == null) return;
        if (ticksSincePlayer < CLIENT_NOTE_AT) playerChecks.check("the note a player tunes plays before the server stops", false);
        removePlayedByPlayer();
    }

    private void removePlayedByPlayer() {
        notes.remove(playedByPlayer);
        playerLevel.setBlock(playedByPlayer, Blocks.AIR.defaultBlockState(), FLAGS);
        playedByPlayer = null;
    }

    private static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return level.getBlockState(pos).useWithoutItem(level, player, hit);
    }

    private static int note(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(NoteBlock.NOTE);
    }

    /** Once the forced chunk's entities are visible: two explosions of radius 2 around {@code origin}, in the air. */
    void runExplosions(ServerLevel level, BlockPos origin) {
        BlockPos above = origin.offset(3, 0, 3);
        BlockPos quiet = origin.offset(-3, 0, -3);
        Vec3 center = Vec3.atCenterOf(above);
        Vec3 canceledCenter = Vec3.atCenterOf(quiet);
        List<BlockPos> floor = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                floor.add(above.offset(dx, -1, dz));
                floor.add(quiet.offset(dx, -1, dz));
            }
        }
        List<Entity> spawned = new ArrayList<>();
        centers.add(center);
        centers.add(canceledCenter);
        canceledStarts.add(canceledCenter);
        try {
            for (BlockPos pos : floor) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), FLAGS);
            // In the air beside the center, in plain view of it: the zombie is hurt and pushed, the chicken spared.
            LivingEntity zombie = spawn(level, EntityTypes.ZOMBIE, center.add(1.5, -0.5, 0), spawned);
            LivingEntity chicken = spawn(level, EntityTypes.CHICKEN, center.add(-1.5, -0.5, 0), spawned);
            LivingEntity bystander = spawn(level, EntityTypes.ZOMBIE, canceledCenter.add(1.5, -0.5, 0), spawned);
            BlockPos keptPos = above.below();
            spared = chicken;
            kept = keptPos;
            Vec3 lift = new Vec3(0.0, 1.5, 0.0);
            knockbacks.put(zombie, lift);

            level.explode(null, center.x, center.y, center.z, 2.0F, Level.ExplosionInteraction.BLOCK);
            List<Object> events = List.copyOf(explosionEvents);
            Detonated detonated = events.size() >= 2 && events.get(1) instanceof Detonated d ? d : null;
            checks.check("ExplosionEvent.Start fires before an explosion, with its level and center, then Detonate",
                    events.size() >= 2 && events.get(0).equals(new Started(center, level)) && detonated != null && detonated.center().equals(center));
            // Vanilla's list has every position the explosion reaches, air included.
            checks.check("ExplosionEvent.Detonate lists the entities in reach and the blocks the explosion destroys",
                    detonated != null && detonated.entities().contains(zombie) && detonated.entities().contains(chicken)
                            && detonated.blocks().contains(keptPos) && detonated.blocks().contains(above));
            checks.check("an entity removed from Detonate's list is spared, and a block removed from it stays",
                    detonated != null && chicken.getHealth() == chicken.getMaxHealth() && chicken.getDeltaMovement().equals(Vec3.ZERO)
                            && level.getBlockState(keptPos).is(Blocks.DIRT)
                            && detonated.blocks().stream().filter(pos -> !pos.equals(keptPos)).allMatch(pos -> level.getBlockState(pos).isAir()));
            List<Knocked> knocked = events.stream().filter(Knocked.class::isInstance).map(Knocked.class::cast).toList();
            checks.check("ExplosionKnockbackEvent fires for each entity pushed, with vanilla's knockback and Detonate's blocks",
                    knocked.size() == 1 && knocked.get(0).entity() == zombie && knocked.get(0).knockback().x > 0.0
                            && detonated != null && knocked.get(0).blocks() == detonated.blocksList());
            checks.check("the knockback from ExplosionKnockbackEvent is what pushes the entity",
                    zombie.getDeltaMovement().distanceTo(lift) < 1.0E-9 && zombie.getHealth() < zombie.getMaxHealth());

            explosionEvents.clear();
            level.explode(null, canceledCenter.x, canceledCenter.y, canceledCenter.z, 2.0F, Level.ExplosionInteraction.BLOCK);
            checks.check("canceling ExplosionEvent.Start stops the explosion",
                    explosionEvents.equals(List.of(new Started(canceledCenter, level))) && level.getBlockState(quiet.below()).is(Blocks.DIRT)
                            && bystander.getHealth() == bystander.getMaxHealth());
        } finally {
            centers.remove(center);
            centers.remove(canceledCenter);
            canceledStarts.clear();
            spared = null;
            kept = null;
            knockbacks.clear();
            explosionEvents.clear();
            spawned.forEach(Entity::discard);
            // What the destroyed dirt dropped.
            level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8.0)).forEach(Entity::discard);
            for (BlockPos pos : floor) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos, List<Entity> spawned) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(pos);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        spawned.add(mob);
        return mob;
    }

    private record Started(Vec3 center, Level level) {}

    private record Detonated(Vec3 center, List<Entity> entities, List<BlockPos> blocks, List<BlockPos> blocksList) {
        // Compares what it saw, not the live list.
        @Override
        public boolean equals(Object o) {
            return o instanceof Detonated other && center.equals(other.center) && entities.equals(other.entities) && blocks.equals(other.blocks);
        }

        @Override
        public int hashCode() {
            return center.hashCode();
        }
    }

    private record Knocked(Vec3 center, Entity entity, Vec3 knockback, List<BlockPos> blocks) {}

    private record Played(BlockPos pos, int note, NoteBlockInstrument instrument) {}

    private record Changed(BlockPos pos, NoteBlockEvent.Note oldNote, NoteBlockEvent.Octave oldOctave, int newNote) {}
}
