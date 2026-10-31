package net.minecraft.world.entity.decoration;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public abstract class HangingEntity extends BlockAttachedEntity {
    protected static final Predicate<Entity> HANGING_ENTITY = entity -> entity instanceof HangingEntity;
    /**
     * The direction the entity is facing
     */
    protected Direction direction = Direction.SOUTH;

    protected HangingEntity(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    protected HangingEntity(EntityType<? extends HangingEntity> entityType, Level level, BlockPos pos) {
        this(entityType, level);
        this.pos = pos;
    }

    /**
     * Updates facing and bounding box based on it
     */
    protected void setDirection(Direction facingDirection) {
        Objects.requireNonNull(facingDirection);
        Validate.isTrue(facingDirection.getAxis().isHorizontal());
        this.direction = facingDirection;
        this.setYRot(this.direction.get2DDataValue() * 90);
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected final void recalculateBoundingBox() {
        if (this.direction != null) {
            AABB aABB = this.calculateBoundingBox(this.pos, this.direction);
            Vec3 vec3 = aABB.getCenter();
            this.setPosRaw(vec3.x, vec3.y, vec3.z);
            this.setBoundingBox(aABB);
        }
    }

    protected abstract AABB calculateBoundingBox(BlockPos var1, Direction var2);

    @Override
    public boolean survives() {
        if (!this.level().noCollision(this)) {
            return false;
        }

        boolean bl = BlockPos.betweenClosedStream(this.calculateSupportBox()).allMatch(blockPos -> {
            BlockState blockState = this.level().getBlockState(blockPos);
            return blockState.isSolid() || DiodeBlock.isDiode(blockState);
        });
        return !bl ? false : this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
    }

    protected AABB calculateSupportBox() {
        return this.getBoundingBox().move(this.direction.step().mul(-0.5F)).deflate(1.0E-7);
    }

    @Override
    public Direction getDirection() {
        return this.direction;
    }

    public abstract void playPlacementSound();

    /**
     * Drops an item at the position of the entity.
     */
    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        ItemEntity itemEntity = new ItemEntity(
            this.level(), this.getX() + this.direction.getStepX() * 0.15F, this.getY() + offsetY, this.getZ() + this.direction.getStepZ() * 0.15F, stack
        );
        itemEntity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itemEntity);
        return itemEntity;
    }

    /**
     * Transforms the entity's current yaw with the given Rotation and returns it. This does not have a side-effect.
     */
    @Override
    public float rotate(Rotation transformRotation) {
        if (this.direction.getAxis() != Direction.Axis.Y) {
            switch (transformRotation) {
                case CLOCKWISE_180:
                    this.direction = this.direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    this.direction = this.direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    this.direction = this.direction.getClockWise();
            }
        }

        float f = Mth.wrapDegrees(this.getYRot());

        return switch (transformRotation) {
            case CLOCKWISE_180 -> f + 180.0F;
            case COUNTERCLOCKWISE_90 -> f + 90.0F;
            case CLOCKWISE_90 -> f + 270.0F;
            default -> f;
        };
    }

    /**
     * Transforms the entity's current yaw with the given Mirror and returns it. This does not have a side-effect.
     */
    @Override
    public float mirror(Mirror transformMirror) {
        return this.rotate(transformMirror.getRotation(this.direction));
    }
}
