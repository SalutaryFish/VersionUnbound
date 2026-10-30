package net.salutaryfish.versionunbound.testmod.events;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre.Result;

/**
 * The crop growth events on the server, in the air in a loaded chunk, with blocks placed and removed again in the same
 * tick. {@code CropGrowEvent.Pre} and {@code Post} at each of NeoForge's ten sites, through the block's own
 * {@code randomTick} with a random source whose rolls all pass or all fail (a cocoa pod rolls with the level's random,
 * so its checks don't depend on the roll): the roll as {@code Pre}'s default, {@code GROW} and {@code DO_NOT_GROW}, the
 * positions and states each site passes, and the rolls NeoForge moves into the event's default (cocoa, sweet berries,
 * bamboo). Also NeoForge's {@code CropBlock.getGrowthSpeed(BlockState, BlockGetter, BlockPos)}, called the way a mod's
 * crop calls it (KaleidoscopeCookery's rice), with the soil's {@code canSustainPlant} and {@code isFertile}
 * ({@link FertileSoilBlock}), and the hooks called directly, as Farmer's Delight, KaleidoscopeCookery and Dynamic Trees
 * do. The listeners only look at the scenario's box.
 */
final class CropScenario {
    private static final RandomSource PASS = new FixedRandom(true);
    private static final RandomSource FAIL = new FixedRandom(false);
    private static final Tick NOTHING = new Tick(List.of(), List.of());

    private final Checks checks;
    private volatile BoundingBox area;
    private volatile Result answer = Result.DEFAULT;
    private final List<Pre> pres = new CopyOnWriteArrayList<>();
    private final List<Post> posts = new CopyOnWriteArrayList<>();
    private ServerLevel level;
    private BlockPos center;

    CropScenario(Checks checks) {
        this.checks = checks;
    }

    void pre(CropGrowEvent.Pre event) {
        BoundingBox box = area;
        if (box == null || event.getLevel() != level || !box.isInside(event.getPos())) return;
        pres.add(new Pre(event.getPos().immutable(), event.getState()));
        event.setResult(answer);
    }

    void post(CropGrowEvent.Post event) {
        BoundingBox box = area;
        if (box == null || event.getLevel() != level || !box.isInside(event.getPos())) return;
        posts.add(new Post(event.getPos().immutable(), event.getOriginalState(), event.getState()));
    }

    /** Runs around {@code center}, the middle of a loaded chunk, in the air. */
    void run(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
        BoundingBox box = BoundingBox.fromCorners(center.offset(-8, -2, -8), center.offset(7, 4, 7));
        Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
        area = box;
        try {
            crop(spot(0, 0));
            stem(spot(1, 0));
            netherWart(spot(2, 0));
            cocoa(spot(3, 0));
            sweetBerries(spot(0, 1));
            bamboo(spot(1, 1));
            cactus(spot(2, 1));
            sugarCane(spot(3, 1));
            vines(spot(0, 2));
            chorusFlower(spot(1, 2));
            growthSpeed(spot(2, 2));
            hooks(spot(3, 2));
        } finally {
            area = null;
            answer = Result.DEFAULT;
            before.forEach((pos, state) -> {
                if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            });
        }
    }

    /** One of 12 places, 4 blocks apart, all within the chunk. */
    private BlockPos spot(int x, int z) {
        return center.offset(-6 + 4 * x, 0, -6 + 4 * z);
    }

    private void crop(BlockPos wheat) {
        place(wheat.below(), moistFarmland());
        BlockState young = Blocks.WHEAT.defaultBlockState();
        BlockState grown = young.setValue(CropBlock.AGE, 1);
        place(wheat, young);
        Tick passed = tick(wheat, Result.DEFAULT, PASS);
        boolean grew = level.getBlockState(wheat) == grown;
        place(wheat, young);
        Tick failed = tick(wheat, Result.DEFAULT, FAIL);
        checks.check("CropGrowEvent.Pre decides a crop's growth with its roll as the default, and Post fires after it grew, with the states before and after",
                passed.equals(new Tick(List.of(new Pre(wheat, young)), List.of(new Post(wheat, young, grown)))) && grew
                        && failed.equals(new Tick(List.of(new Pre(wheat, young)), List.of())) && level.getBlockState(wheat) == young);
        checks.check("CropGrowEvent.Pre's GROW grows a crop whose roll fails, and DO_NOT_GROW keeps one whose roll passes from growing",
                forced(wheat, () -> place(wheat, young), () -> level.getBlockState(wheat) == grown));
        place(wheat, young.setValue(CropBlock.AGE, 7));
        checks.check("a crop at its full age fires no CropGrowEvent", tick(wheat, Result.DEFAULT, PASS), NOTHING);
    }

    private void stem(BlockPos stem) {
        place(stem.below(), moistFarmland());
        BlockState young = Blocks.PUMPKIN_STEM.defaultBlockState();
        BlockState grown = young.setValue(StemBlock.AGE, 1);
        place(stem, young);
        Tick passed = tick(stem, Result.DEFAULT, PASS);
        boolean grew = level.getBlockState(stem) == grown;
        place(stem, young);
        Tick failed = tick(stem, Result.DEFAULT, FAIL);
        // Vanilla overwrites the state argument when the stem grows; Post still gets the state from before.
        checks.check("CropGrowEvent fires for a stem with its roll as the default, and Post gets the stem's state from before it grew",
                passed.equals(new Tick(List.of(new Pre(stem, young)), List.of(new Post(stem, young, grown)))) && grew
                        && failed.equals(new Tick(List.of(new Pre(stem, young)), List.of())));
        checks.check("CropGrowEvent.Pre's GROW and DO_NOT_GROW decide a stem's growth",
                forced(stem, () -> place(stem, young), () -> level.getBlockState(stem) == grown));
        // At full age the stem tries to grow a pumpkin on its first side (north); with air under that side there's no
        // room, and NeoForge fires Post all the same.
        BlockState full = young.setValue(StemBlock.AGE, 7);
        place(stem, full);
        checks.check("a full-grown stem fires Post after trying to grow a fruit, also when there's no room for one",
                tick(stem, Result.DEFAULT, PASS), new Tick(List.of(new Pre(stem, full)), List.of(new Post(stem, full, full))));
    }

    private void netherWart(BlockPos wart) {
        place(wart.below(), Blocks.SOUL_SAND.defaultBlockState());
        BlockState young = Blocks.NETHER_WART.defaultBlockState();
        BlockState grown = young.setValue(NetherWartBlock.AGE, 1);
        place(wart, young);
        Tick passed = tick(wart, Result.DEFAULT, PASS);
        place(wart, young);
        Tick failed = tick(wart, Result.DEFAULT, FAIL);
        checks.check("CropGrowEvent fires for nether wart with its roll as the default; Post's original state is the grown one, as in NeoForge",
                passed.equals(new Tick(List.of(new Pre(wart, young)), List.of(new Post(wart, grown, grown))))
                        && failed.equals(new Tick(List.of(new Pre(wart, young)), List.of())));
        checks.check("CropGrowEvent.Pre's GROW and DO_NOT_GROW decide nether wart's growth",
                forced(wart, () -> place(wart, young), () -> level.getBlockState(wart) == grown));
    }

    private void cocoa(BlockPos log) {
        place(log, Blocks.JUNGLE_LOG.defaultBlockState());
        BlockPos pod = log.north();
        BlockState young = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.SOUTH);
        BlockState grown = young.setValue(CocoaBlock.AGE, 1);
        // Vanilla rolls (1 in 5) before it looks at the pod; NeoForge asks the event first, with the roll as its default.
        // Ten ticks that all fire the event are a 1 in 10^7 chance with vanilla's order.
        place(pod, young);
        List<Tick> refused = new ArrayList<>();
        for (int i = 0; i < 10; i++) refused.add(tick(pod, Result.DO_NOT_GROW, PASS));
        checks.check("CropGrowEvent.Pre fires on every random tick of a cocoa pod that can still grow, whatever the level's roll",
                refused.stream().allMatch(tick -> tick.equals(new Tick(List.of(new Pre(pod, young)), List.of()))) && level.getBlockState(pod) == young);
        checks.check("CropGrowEvent.Pre's GROW grows a cocoa pod, and Post fires",
                tick(pod, Result.GROW, PASS), new Tick(List.of(new Pre(pod, young)), List.of(new Post(pod, young, grown))));
        place(pod, young.setValue(CocoaBlock.AGE, 2));
        checks.check("a cocoa pod at its full age fires no CropGrowEvent", tick(pod, Result.GROW, PASS), NOTHING);
    }

    private void sweetBerries(BlockPos bush) {
        place(bush.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        BlockState young = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
        BlockState grown = young.setValue(SweetBerryBushBlock.AGE, 1);
        place(bush, young);
        Tick passed = tick(bush, Result.DEFAULT, PASS);
        place(bush, young);
        // Vanilla checks the roll before the light; NeoForge checks the light, then asks the event with the roll.
        Tick failed = tick(bush, Result.DEFAULT, FAIL);
        checks.check("CropGrowEvent fires for a sweet berry bush whatever its roll, which is the default, and Post after it grew",
                passed.equals(new Tick(List.of(new Pre(bush, young)), List.of(new Post(bush, young, grown))))
                        && failed.equals(new Tick(List.of(new Pre(bush, young)), List.of())) && level.getBlockState(bush) == young);
        checks.check("CropGrowEvent.Pre's GROW and DO_NOT_GROW decide a sweet berry bush's growth",
                forced(bush, () -> place(bush, young), () -> level.getBlockState(bush) == grown));
    }

    private void bamboo(BlockPos stalk) {
        place(stalk.below(), Blocks.DIRT.defaultBlockState());
        BlockState young = Blocks.BAMBOO.defaultBlockState();
        Runnable reset = () -> {
            place(stalk, young);
            place(stalk.above(), Blocks.AIR.defaultBlockState());
        };
        BooleanSupplier grew = () -> level.getBlockState(stalk.above()).is(Blocks.BAMBOO);
        reset.run();
        Tick passed = tick(stalk, Result.DEFAULT, PASS);
        boolean grewPassed = grew.getAsBoolean();
        reset.run();
        // Vanilla rolls first; NeoForge asks the event once the bamboo has room, light and isn't too high, with the roll.
        Tick failed = tick(stalk, Result.DEFAULT, FAIL);
        checks.check("CropGrowEvent fires for bamboo whatever its roll, which is the default, and Post after it grew a stalk",
                passed.equals(new Tick(List.of(new Pre(stalk, young)), List.of(new Post(stalk, young, young)))) && grewPassed
                        && failed.equals(new Tick(List.of(new Pre(stalk, young)), List.of())) && !grew.getAsBoolean());
        checks.check("CropGrowEvent.Pre's GROW and DO_NOT_GROW decide bamboo's growth", forced(stalk, reset, grew));
    }

    private void cactus(BlockPos cactus) {
        place(cactus.below(), Blocks.SAND.defaultBlockState());
        BlockState young = Blocks.CACTUS.defaultBlockState();
        BlockState grown = young.setValue(CactusBlock.AGE, 1);
        place(cactus, young);
        checks.check("CropGrowEvent.Pre fires for the block above a cactus (default: grow), and Post for the cactus once it grew",
                tick(cactus, Result.DEFAULT, FAIL), new Tick(List.of(new Pre(cactus.above(), young)), List.of(new Post(cactus, young, grown))));
        place(cactus, young);
        checks.check("CropGrowEvent.Pre's DO_NOT_GROW keeps a cactus from growing",
                tick(cactus, Result.DO_NOT_GROW, PASS).equals(new Tick(List.of(new Pre(cactus.above(), young)), List.of())) && level.getBlockState(cactus) == young);
        // A cactus three high at its full age returns before it grows: Pre, no Post.
        BlockState full = young.setValue(CactusBlock.AGE, 15);
        place(cactus.above(), young);
        place(cactus.above(2), full);
        checks.check("the top of a full-grown cactus three high fires CropGrowEvent.Pre but not Post",
                tick(cactus.above(2), Result.DEFAULT, PASS).equals(new Tick(List.of(new Pre(cactus.above(3), full)), List.of()))
                        && level.getBlockState(cactus.above(3)).isAir());
    }

    private void sugarCane(BlockPos cane) {
        place(cane.below(), Blocks.SAND.defaultBlockState());
        BlockState young = Blocks.SUGAR_CANE.defaultBlockState();
        place(cane, young);
        checks.check("CropGrowEvent.Pre fires for each random tick of sugar cane (default: grow), and aging alone fires no Post",
                tick(cane, Result.DEFAULT, PASS).equals(new Tick(List.of(new Pre(cane, young)), List.of()))
                        && level.getBlockState(cane) == young.setValue(SugarCaneBlock.AGE, 1));
        BlockState full = young.setValue(SugarCaneBlock.AGE, 15);
        place(cane, full);
        checks.check("Post fires when sugar cane grows a new block, for that block, with a fresh cane as the original state",
                tick(cane, Result.DEFAULT, PASS).equals(new Tick(List.of(new Pre(cane, full)), List.of(new Post(cane.above(), young, young))))
                        && level.getBlockState(cane) == young);
        place(cane, full);
        place(cane.above(), Blocks.AIR.defaultBlockState());
        checks.check("CropGrowEvent.Pre's DO_NOT_GROW keeps sugar cane from growing",
                tick(cane, Result.DO_NOT_GROW, PASS).equals(new Tick(List.of(new Pre(cane, full)), List.of()))
                        && level.getBlockState(cane) == full && level.getBlockState(cane.above()).isAir());
    }

    private void vines(BlockPos vine) {
        place(vine.below(), Blocks.NETHERRACK.defaultBlockState());
        BlockState young = Blocks.TWISTING_VINES.defaultBlockState();
        BlockPos into = vine.above();
        Runnable reset = () -> {
            place(vine, young);
            place(into, Blocks.AIR.defaultBlockState());
        };
        BooleanSupplier grew = () -> level.getBlockState(into).is(Blocks.TWISTING_VINES);
        reset.run();
        Tick passed = tick(vine, Result.DEFAULT, PASS);
        BlockState head = level.getBlockState(into);
        reset.run();
        Tick failed = tick(vine, Result.DEFAULT, FAIL);
        // NeoForge passes the state it reads back after growing as the original state too.
        checks.check("CropGrowEvent fires for the block a vine grows into, with its roll as the default; Post's states are both the new head's",
                passed.equals(new Tick(List.of(new Pre(into, young)), List.of(new Post(into, head, head)))) && head.is(Blocks.TWISTING_VINES)
                        && failed.equals(new Tick(List.of(new Pre(into, young)), List.of())) && !grew.getAsBoolean());
        checks.check("CropGrowEvent.Pre's GROW and DO_NOT_GROW decide a vine's growth", forced(vine, reset, grew));
    }

    private void chorusFlower(BlockPos flower) {
        place(flower.below(), Blocks.END_STONE.defaultBlockState());
        BlockState young = Blocks.CHORUS_FLOWER.defaultBlockState();
        Runnable reset = () -> {
            place(flower, young);
            place(flower.above(), Blocks.AIR.defaultBlockState());
        };
        reset.run();
        Tick grown = tick(flower, Result.DEFAULT, PASS);
        BlockState plant = level.getBlockState(flower);
        checks.check("CropGrowEvent.Pre fires for the block above a chorus flower (default: grow), and Post for the flower once it grew",
                grown.equals(new Tick(List.of(new Pre(flower.above(), young)), List.of(new Post(flower, young, plant))))
                        && plant.is(Blocks.CHORUS_PLANT) && level.getBlockState(flower.above()).is(Blocks.CHORUS_FLOWER));
        reset.run();
        checks.check("CropGrowEvent.Pre's DO_NOT_GROW keeps a chorus flower from growing",
                tick(flower, Result.DO_NOT_GROW, PASS).equals(new Tick(List.of(new Pre(flower.above(), young)), List.of()))
                        && level.getBlockState(flower) == young && level.getBlockState(flower.above()).isAir());
        place(flower, young.setValue(ChorusFlowerBlock.AGE, 5));
        checks.check("a chorus flower at its full age fires no CropGrowEvent", tick(flower, Result.GROW, PASS), NOTHING);
    }

    private void growthSpeed(BlockPos crop) {
        BlockState wheat = Blocks.WHEAT.defaultBlockState();
        place(crop, wheat);
        // Vanilla's speed: 1, plus 3 for moist soil under the crop (1 when dry), plus a quarter of that for each of the
        // eight around it. So 10 on moist farmland, 4 on dry, and 1 without soil.
        checks.check("NeoForge's CropBlock.getGrowthSpeed(BlockState, BlockGetter, BlockPos) is there for mods' crops and agrees with vanilla's", () -> {
            soil(crop, moistFarmland());
            float moist = GrowthSpeedProbe.byState(wheat, level, crop);
            float vanilla = GrowthSpeedProbe.byBlock(Blocks.WHEAT, level, crop);
            soil(crop, Blocks.FARMLAND.defaultBlockState());
            float dry = GrowthSpeedProbe.byState(wheat, level, crop);
            soil(crop, Blocks.STONE.defaultBlockState());
            float none = GrowthSpeedProbe.byState(wheat, level, crop);
            return List.of(moist, vanilla, dry, none).equals(List.of(10.0F, 10.0F, 4.0F, 1.0F));
        });
        checks.check("the growth speed asks the soil's canSustainPlant and isFertile: a mod's soil that sustains and is fertile counts as moist farmland", () -> {
            soil(crop, EventsMod.FERTILE_SOIL.get().defaultBlockState());
            return GrowthSpeedProbe.byState(wheat, level, crop) == 10.0F && GrowthSpeedProbe.byBlock(Blocks.WHEAT, level, crop) == 10.0F;
        });
    }

    private void hooks(BlockPos pos) {
        BlockState wheat = Blocks.WHEAT.defaultBlockState();
        pres.clear();
        List<Boolean> answers = new ArrayList<>();
        for (Result result : List.of(Result.DEFAULT, Result.DEFAULT, Result.GROW, Result.DO_NOT_GROW)) {
            answer = result;
            // The default the crop computed: true, false, false (GROW overrides it), true (DO_NOT_GROW overrides it).
            boolean def = answers.size() == 0 || answers.size() == 3;
            answers.add(CommonHooks.canCropGrow(level, pos, wheat, def));
        }
        answer = Result.DEFAULT;
        checks.check("CommonHooks.canCropGrow posts CropGrowEvent.Pre and answers with its result, the crop's default for DEFAULT",
                answers.equals(List.of(true, false, true, false)) && pres.equals(List.of(new Pre(pos, wheat), new Pre(pos, wheat), new Pre(pos, wheat), new Pre(pos, wheat))));
        BlockState now = wheat.setValue(CropBlock.AGE, 3);
        place(pos, now);
        posts.clear();
        CommonHooks.fireCropGrowPost(level, pos, wheat);
        checks.check("CommonHooks.fireCropGrowPost posts Post with the level's state as the new one", posts, List.of(new Post(pos, wheat, now)));
    }

    /** {@code GROW} with a failing roll grows the plant, with one Pre and one Post; {@code DO_NOT_GROW} with a passing roll doesn't, without Post. */
    private boolean forced(BlockPos pos, Runnable reset, BooleanSupplier grew) {
        reset.run();
        Tick grow = tick(pos, Result.GROW, FAIL);
        boolean grown = grew.getAsBoolean() && grow.pres().size() == 1 && grow.posts().size() == 1;
        reset.run();
        Tick keep = tick(pos, Result.DO_NOT_GROW, PASS);
        return grown && keep.pres().size() == 1 && keep.posts().isEmpty() && !grew.getAsBoolean();
    }

    /** One random tick of the block at {@code pos}, with the listener answering {@code result}; returns the events it fired. */
    private Tick tick(BlockPos pos, Result result, RandomSource random) {
        pres.clear();
        posts.clear();
        answer = result;
        try {
            level.getBlockState(pos).randomTick(level, pos, random);
        } finally {
            answer = Result.DEFAULT;
        }
        return new Tick(List.copyOf(pres), List.copyOf(posts));
    }

    private void soil(BlockPos crop, BlockState soil) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) place(crop.offset(x, -1, z), soil);
        }
    }

    private void place(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static BlockState moistFarmland() {
        return Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
    }

    private record Pre(BlockPos pos, BlockState state) {}

    private record Post(BlockPos pos, BlockState originalState, BlockState state) {}

    private record Tick(List<Pre> pres, List<Post> posts) {}

    /** Every roll passes ({@code nextInt} answers 0, {@code nextDouble} 0) or fails (the largest value); the rest is a seeded random. */
    private static final class FixedRandom extends LegacyRandomSource {
        private final boolean pass;

        FixedRandom(boolean pass) {
            super(0L);
            this.pass = pass;
        }

        @Override
        public int nextInt(int bound) {
            return pass ? 0 : bound - 1;
        }

        @Override
        public double nextDouble() {
            return pass ? 0.0 : 0.999;
        }
    }

    /**
     * A crop class, for its access to {@code CropBlock}'s protected static methods: vanilla's {@code getGrowthSpeed(Block,
     * ...)} directly, and NeoForge's {@code getGrowthSpeed(BlockState, ...)}, which javac doesn't see on vanilla's class,
     * through a method handle that resolves like a mod crop's {@code invokestatic} (owner: the crop's own class). Never
     * constructed.
     */
    private static final class GrowthSpeedProbe extends CropBlock {
        private GrowthSpeedProbe(Properties properties) {
            super(properties);
        }

        static float byBlock(Block crop, BlockGetter level, BlockPos pos) {
            return getGrowthSpeed(crop, level, pos);
        }

        static float byState(BlockState crop, BlockGetter level, BlockPos pos) throws Throwable {
            MethodHandle handle = MethodHandles.lookup().findStatic(GrowthSpeedProbe.class, "getGrowthSpeed",
                    MethodType.methodType(float.class, BlockState.class, BlockGetter.class, BlockPos.class));
            return (float) handle.invokeExact(crop, level, pos);
        }
    }
}
