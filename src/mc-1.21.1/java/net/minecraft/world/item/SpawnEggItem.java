package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpawnEggItem extends Item {
    private static final Map<EntityType<? extends Mob>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();
    private static final MapCodec<EntityType<?>> ENTITY_TYPE_FIELD_CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id");
    private final int backgroundColor;
    private final int highlightColor;
    private final EntityType<?> defaultType;

    public SpawnEggItem(EntityType<? extends Mob> defaultType, int backgroundColor, int highlightColor, Item.Properties properties) {
        super(properties);
        this.defaultType = defaultType;
        this.backgroundColor = backgroundColor;
        this.highlightColor = highlightColor;
        BY_ID.put(defaultType, this);
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemStack = context.getItemInHand();
            BlockPos blockPos = context.getClickedPos();
            Direction direction = context.getClickedFace();
            BlockState blockState = level.getBlockState(blockPos);
            if (level.getBlockEntity(blockPos) instanceof Spawner spawner) {
                EntityType<?> entityType = this.getType(itemStack);
                spawner.setEntityId(entityType, level.getRandom());
                level.sendBlockUpdated(blockPos, blockState, blockState, 3);
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, blockPos);
                itemStack.shrink(1);
                return InteractionResult.CONSUME;
            } else {
                BlockPos blockPos2;
                if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
                    blockPos2 = blockPos;
                } else {
                    blockPos2 = blockPos.relative(direction);
                }

                EntityType<?> entityType = this.getType(itemStack);
                if (entityType.spawn(
                        (ServerLevel)level,
                        itemStack,
                        context.getPlayer(),
                        blockPos2,
                        MobSpawnType.SPAWN_EGG,
                        true,
                        !Objects.equals(blockPos, blockPos2) && direction == Direction.UP
                    )
                    != null) {
                    itemStack.shrink(1);
                    level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, blockPos);
                }

                return InteractionResult.CONSUME;
            }
        }
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult blockHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (!(level instanceof ServerLevel)) {
            return InteractionResultHolder.success(itemStack);
        }

        BlockHitResult blockHitResult2 = blockHitResult;
        BlockPos blockPos = blockHitResult2.getBlockPos();
        if (!(level.getBlockState(blockPos).getBlock() instanceof LiquidBlock)) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (level.mayInteract(player, blockPos) && player.mayUseItemAt(blockPos, blockHitResult2.getDirection(), itemStack)) {
            EntityType<?> entityType = this.getType(itemStack);
            Entity entity = entityType.spawn((ServerLevel)level, itemStack, player, blockPos, MobSpawnType.SPAWN_EGG, false, false);
            if (entity == null) {
                return InteractionResultHolder.pass(itemStack);
            }

            itemStack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
            return InteractionResultHolder.consume(itemStack);
        } else {
            return InteractionResultHolder.fail(itemStack);
        }
    }

    public boolean spawnsEntity(ItemStack stack, EntityType<?> entityType) {
        return Objects.equals(this.getType(stack), entityType);
    }

    public int getColor(int tintIndex) {
        return tintIndex == 0 ? this.backgroundColor : this.highlightColor;
    }

    @Nullable
    public static SpawnEggItem byId(@Nullable EntityType<?> type) {
        return BY_ID.get(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(BY_ID.values());
    }

    public EntityType<?> getType(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        return !customData.isEmpty() ? customData.read(ENTITY_TYPE_FIELD_CODEC).result().orElse(this.defaultType) : this.defaultType;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.defaultType.requiredFeatures();
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(
        Player player, Mob mob, EntityType<? extends Mob> entityType, ServerLevel serverLevel, Vec3 pos, ItemStack stack
    ) {
        if (!this.spawnsEntity(stack, entityType)) {
            return Optional.empty();
        }

        Mob mob2;
        if (mob instanceof AgeableMob) {
            mob2 = ((AgeableMob)mob).getBreedOffspring(serverLevel, (AgeableMob)mob);
        } else {
            mob2 = entityType.create(serverLevel);
        }

        if (mob2 == null) {
            return Optional.empty();
        }

        mob2.setBaby(true);
        if (!mob2.isBaby()) {
            return Optional.empty();
        }

        mob2.moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
        serverLevel.addFreshEntityWithPassengers(mob2);
        mob2.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        stack.consume(1, player);
        return Optional.of(mob2);
    }
}
