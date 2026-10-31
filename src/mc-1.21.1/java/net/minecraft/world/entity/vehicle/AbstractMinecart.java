package net.minecraft.world.entity.vehicle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractMinecart extends VehicleEntity {
    private static final Vec3 LOWERED_PASSENGER_ATTACHMENT = new Vec3(0.0, 0.0, 0.0);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_BLOCK = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_CUSTOM_DISPLAY = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(
        Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1)
    );
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private boolean flipped;
    private boolean onRails;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private Vec3 targetDeltaMovement = Vec3.ZERO;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = Util.make(Maps.newEnumMap(RailShape.class), map -> {
        Vec3i vec3i = Direction.WEST.getNormal();
        Vec3i vec3i2 = Direction.EAST.getNormal();
        Vec3i vec3i3 = Direction.NORTH.getNormal();
        Vec3i vec3i4 = Direction.SOUTH.getNormal();
        Vec3i vec3i5 = vec3i.below();
        Vec3i vec3i6 = vec3i2.below();
        Vec3i vec3i7 = vec3i3.below();
        Vec3i vec3i8 = vec3i4.below();
        map.put(RailShape.NORTH_SOUTH, Pair.of(vec3i3, vec3i4));
        map.put(RailShape.EAST_WEST, Pair.of(vec3i, vec3i2));
        map.put(RailShape.ASCENDING_EAST, Pair.of(vec3i5, vec3i2));
        map.put(RailShape.ASCENDING_WEST, Pair.of(vec3i, vec3i6));
        map.put(RailShape.ASCENDING_NORTH, Pair.of(vec3i3, vec3i8));
        map.put(RailShape.ASCENDING_SOUTH, Pair.of(vec3i7, vec3i4));
        map.put(RailShape.SOUTH_EAST, Pair.of(vec3i4, vec3i2));
        map.put(RailShape.SOUTH_WEST, Pair.of(vec3i4, vec3i));
        map.put(RailShape.NORTH_WEST, Pair.of(vec3i3, vec3i));
        map.put(RailShape.NORTH_EAST, Pair.of(vec3i3, vec3i2));
    });

    protected AbstractMinecart(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    protected AbstractMinecart(EntityType<?> entityType, Level level, double x, double y, double z) {
        this(entityType, level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public static AbstractMinecart createMinecart(
        ServerLevel level, double x, double y, double z, AbstractMinecart.Type type, ItemStack stack, @Nullable Player player
    ) {
        AbstractMinecart abstractMinecart = switch (type) {
            case CHEST -> new MinecartChest(level, x, y, z);
            case FURNACE -> new MinecartFurnace(level, x, y, z);
            case TNT -> new MinecartTNT(level, x, y, z);
            case SPAWNER -> new MinecartSpawner(level, x, y, z);
            case HOPPER -> new MinecartHopper(level, x, y, z);
            case COMMAND_BLOCK -> new MinecartCommandBlock(level, x, y, z);
            default -> new Minecart(level, x, y, z);
        };
        EntityType.<AbstractMinecart>createDefaultStackConfig(level, stack, player).accept(abstractMinecart);
        return abstractMinecart;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_DISPLAY_BLOCK, Block.getId(Blocks.AIR.defaultBlockState()));
        builder.define(DATA_ID_DISPLAY_OFFSET, 6);
        builder.define(DATA_ID_CUSTOM_DISPLAY, false);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return Boat.canVehicleCollide(this, entity);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        boolean bl = entity instanceof Villager || entity instanceof WanderingTrader;
        return bl ? LOWERED_PASSENGER_ATTACHMENT : super.getPassengerAttachmentPoint(entity, dimensions, partialTick);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(livingEntity);
        }

        int[][] is = DismountHelper.offsetsForDirection(direction);
        BlockPos blockPos = this.blockPosition();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        ImmutableList<Pose> immutableList = livingEntity.getDismountPoses();

        for (Pose pose : immutableList) {
            EntityDimensions entityDimensions = livingEntity.getDimensions(pose);
            float f = Math.min(entityDimensions.width(), 1.0F) / 2.0F;

            for (int i : POSE_DISMOUNT_HEIGHTS.get(pose)) {
                for (int[] js : is) {
                    mutableBlockPos.set(blockPos.getX() + js[0], blockPos.getY() + i, blockPos.getZ() + js[1]);
                    double d = this.level()
                        .getBlockFloorHeight(
                            DismountHelper.nonClimbableShape(this.level(), mutableBlockPos),
                            () -> DismountHelper.nonClimbableShape(this.level(), mutableBlockPos.below())
                        );
                    if (DismountHelper.isBlockFloorValid(d)) {
                        AABB aABB = new AABB(-f, 0.0, -f, f, entityDimensions.height(), f);
                        Vec3 vec3 = Vec3.upFromBottomCenterOf(mutableBlockPos, d);
                        if (DismountHelper.canDismountTo(this.level(), livingEntity, aABB.move(vec3))) {
                            livingEntity.setPose(pose);
                            return vec3;
                        }
                    }
                }
            }
        }

        double e = this.getBoundingBox().maxY;
        mutableBlockPos.set(blockPos.getX(), e, blockPos.getZ());

        for (Pose pose2 : immutableList) {
            double g = livingEntity.getDimensions(pose2).height();
            int j = Mth.ceil(e - mutableBlockPos.getY() + g);
            double h = DismountHelper.findCeilingFrom(mutableBlockPos, j, pos -> this.level().getBlockState(pos).getCollisionShape(this.level(), pos));
            if (e + g <= h) {
                livingEntity.setPose(pose2);
                break;
            }
        }

        return super.getDismountLocationForPassenger(livingEntity);
    }

    @Override
    protected float getBlockSpeedFactor() {
        BlockState blockState = this.level().getBlockState(this.blockPosition());
        return blockState.is(BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    @Override
    public void animateHurt(float yaw) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    private static Pair<Vec3i, Vec3i> exits(RailShape shape) {
        return EXITS.get(shape);
    }

    @Override
    public Direction getMotionDirection() {
        return this.flipped ? this.getDirection().getOpposite().getClockWise() : this.getDirection().getClockWise();
    }

    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.005 : 0.04;
    }

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        this.handlePortal();
        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
                this.lerpSteps--;
            } else {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
            }
        } else {
            this.applyGravity();
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());
            if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
                j--;
            }

            BlockPos blockPos = new BlockPos(i, j, k);
            BlockState blockState = this.level().getBlockState(blockPos);
            this.onRails = BaseRailBlock.isRail(blockState);
            if (this.onRails) {
                this.moveAlongTrack(blockPos, blockState);
                if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
                    this.activateMinecart(i, j, k, blockState.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.comeOffTrack();
            }

            this.checkInsideBlocks();
            this.setXRot(0.0F);
            double d = this.xo - this.getX();
            double e = this.zo - this.getZ();
            if (d * d + e * e > 0.001) {
                this.setYRot((float)(Mth.atan2(e, d) * 180.0 / Math.PI));
                if (this.flipped) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double f = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            if (f < -170.0 || f >= 170.0) {
                this.setYRot(this.getYRot() + 180.0F);
                this.flipped = !this.flipped;
            }

            this.setRot(this.getYRot(), this.getXRot());
            if (this.getMinecartType() == AbstractMinecart.Type.RIDEABLE && this.getDeltaMovement().horizontalDistanceSqr() > 0.01) {
                List<Entity> list = this.level().getEntities(this, this.getBoundingBox().inflate(0.2F, 0.0, 0.2F), EntitySelector.pushableBy(this));
                if (!list.isEmpty()) {
                    for (Entity entity : list) {
                        if (!(entity instanceof Player)
                            && !(entity instanceof IronGolem)
                            && !(entity instanceof AbstractMinecart)
                            && !this.isVehicle()
                            && !entity.isPassenger()) {
                            entity.startRiding(this);
                        } else {
                            entity.push(this);
                        }
                    }
                }
            } else {
                for (Entity entity2 : this.level().getEntities(this, this.getBoundingBox().inflate(0.2F, 0.0, 0.2F))) {
                    if (!this.hasPassenger(entity2) && entity2.isPushable() && entity2 instanceof AbstractMinecart) {
                        entity2.push(this);
                    }
                }
            }

            this.updateInWaterStateAndDoFluidPushing();
            if (this.isInLava()) {
                this.lavaHurt();
                this.fallDistance *= 0.5F;
            }

            this.firstTick = false;
        }
    }

    protected double getMaxSpeed() {
        return (this.isInWater() ? 4.0 : 8.0) / 20.0;
    }

    /**
     * Called every tick the minecart is on an activator rail.
     */
    public void activateMinecart(int x, int y, int z, boolean powered) {
    }

    protected void comeOffTrack() {
        double d = this.getMaxSpeed();
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(Mth.clamp(vec3.x, -d, d), vec3.y, Mth.clamp(vec3.z, -d, d));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95));
        }
    }

    protected void moveAlongTrack(BlockPos pos, BlockState state) {
        this.resetFallDistance();
        double d = this.getX();
        double e = this.getY();
        double f = this.getZ();
        Vec3 vec3 = this.getPos(d, e, f);
        e = pos.getY();
        boolean bl = false;
        boolean bl2 = false;
        if (state.is(Blocks.POWERED_RAIL)) {
            bl = state.getValue(PoweredRailBlock.POWERED);
            bl2 = !bl;
        }

        double g = 0.0078125;
        if (this.isInWater()) {
            g *= 0.2;
        }

        Vec3 vec32 = this.getDeltaMovement();
        RailShape railShape = state.getValue(((BaseRailBlock)state.getBlock()).getShapeProperty());
        switch (railShape) {
            case ASCENDING_EAST:
                this.setDeltaMovement(vec32.add(-g, 0.0, 0.0));
                e++;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(vec32.add(g, 0.0, 0.0));
                e++;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(vec32.add(0.0, 0.0, g));
                e++;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(vec32.add(0.0, 0.0, -g));
                e++;
        }

        vec32 = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = exits(railShape);
        Vec3i vec3i = pair.getFirst();
        Vec3i vec3i2 = pair.getSecond();
        double h = vec3i2.getX() - vec3i.getX();
        double i = vec3i2.getZ() - vec3i.getZ();
        double j = Math.sqrt(h * h + i * i);
        double k = vec32.x * h + vec32.z * i;
        if (k < 0.0) {
            h = -h;
            i = -i;
        }

        double l = Math.min(2.0, vec32.horizontalDistance());
        vec32 = new Vec3(l * h / j, vec32.y, l * i / j);
        this.setDeltaMovement(vec32);
        Entity entity = this.getFirstPassenger();
        if (entity instanceof Player) {
            Vec3 vec33 = entity.getDeltaMovement();
            double m = vec33.horizontalDistanceSqr();
            double n = this.getDeltaMovement().horizontalDistanceSqr();
            if (m > 1.0E-4 && n < 0.01) {
                this.setDeltaMovement(this.getDeltaMovement().add(vec33.x * 0.1, 0.0, vec33.z * 0.1));
                bl2 = false;
            }
        }

        if (bl2) {
            double o = this.getDeltaMovement().horizontalDistance();
            if (o < 0.03) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5));
            }
        }

        double o = pos.getX() + 0.5 + vec3i.getX() * 0.5;
        double p = pos.getZ() + 0.5 + vec3i.getZ() * 0.5;
        double q = pos.getX() + 0.5 + vec3i2.getX() * 0.5;
        double r = pos.getZ() + 0.5 + vec3i2.getZ() * 0.5;
        h = q - o;
        i = r - p;
        double s;
        if (h == 0.0) {
            s = f - pos.getZ();
        } else if (i == 0.0) {
            s = d - pos.getX();
        } else {
            double t = d - o;
            double u = f - p;
            s = (t * h + u * i) * 2.0;
        }

        d = o + h * s;
        f = p + i * s;
        this.setPos(d, e, f);
        double t = this.isVehicle() ? 0.75 : 1.0;
        double u = this.getMaxSpeed();
        vec32 = this.getDeltaMovement();
        this.move(MoverType.SELF, new Vec3(Mth.clamp(t * vec32.x, -u, u), 0.0, Mth.clamp(t * vec32.z, -u, u)));
        if (vec3i.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == vec3i.getX() && Mth.floor(this.getZ()) - pos.getZ() == vec3i.getZ()) {
            this.setPos(this.getX(), this.getY() + vec3i.getY(), this.getZ());
        } else if (vec3i2.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == vec3i2.getX() && Mth.floor(this.getZ()) - pos.getZ() == vec3i2.getZ()) {
            this.setPos(this.getX(), this.getY() + vec3i2.getY(), this.getZ());
        }

        this.applyNaturalSlowdown();
        Vec3 vec34 = this.getPos(this.getX(), this.getY(), this.getZ());
        if (vec34 != null && vec3 != null) {
            double v = (vec3.y - vec34.y) * 0.05;
            Vec3 vec35 = this.getDeltaMovement();
            double w = vec35.horizontalDistance();
            if (w > 0.0) {
                this.setDeltaMovement(vec35.multiply((w + v) / w, 1.0, (w + v) / w));
            }

            this.setPos(this.getX(), vec34.y, this.getZ());
        }

        int x = Mth.floor(this.getX());
        int y = Mth.floor(this.getZ());
        if (x != pos.getX() || y != pos.getZ()) {
            Vec3 vec35 = this.getDeltaMovement();
            double w = vec35.horizontalDistance();
            this.setDeltaMovement(w * (x - pos.getX()), vec35.y, w * (y - pos.getZ()));
        }

        if (bl) {
            Vec3 vec35 = this.getDeltaMovement();
            double w = vec35.horizontalDistance();
            if (w > 0.01) {
                double z = 0.06;
                this.setDeltaMovement(vec35.add(vec35.x / w * 0.06, 0.0, vec35.z / w * 0.06));
            } else {
                Vec3 vec36 = this.getDeltaMovement();
                double aa = vec36.x;
                double ab = vec36.z;
                if (railShape == RailShape.EAST_WEST) {
                    if (this.isRedstoneConductor(pos.west())) {
                        aa = 0.02;
                    } else if (this.isRedstoneConductor(pos.east())) {
                        aa = -0.02;
                    }
                } else {
                    if (railShape != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.isRedstoneConductor(pos.north())) {
                        ab = 0.02;
                    } else if (this.isRedstoneConductor(pos.south())) {
                        ab = -0.02;
                    }
                }

                this.setDeltaMovement(aa, vec36.y, ab);
            }
        }
    }

    @Override
    public boolean isOnRails() {
        return this.onRails;
    }

    private boolean isRedstoneConductor(BlockPos pos) {
        return this.level().getBlockState(pos).isRedstoneConductor(this.level(), pos);
    }

    protected void applyNaturalSlowdown() {
        double d = this.isVehicle() ? 0.997 : 0.96;
        Vec3 vec3 = this.getDeltaMovement();
        vec3 = vec3.multiply(d, 0.0, d);
        if (this.isInWater()) {
            vec3 = vec3.scale(0.95F);
        }

        this.setDeltaMovement(vec3);
    }

    @Nullable
    public Vec3 getPosOffs(double x, double y, double z, double offset) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);
        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            j--;
        }

        BlockState blockState = this.level().getBlockState(new BlockPos(i, j, k));
        if (BaseRailBlock.isRail(blockState)) {
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            y = j;
            if (railShape.isAscending()) {
                y = j + 1;
            }

            Pair<Vec3i, Vec3i> pair = exits(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i2 = pair.getSecond();
            double d = vec3i2.getX() - vec3i.getX();
            double e = vec3i2.getZ() - vec3i.getZ();
            double f = Math.sqrt(d * d + e * e);
            d /= f;
            e /= f;
            x += d * offset;
            z += e * offset;
            if (vec3i.getY() != 0 && Mth.floor(x) - i == vec3i.getX() && Mth.floor(z) - k == vec3i.getZ()) {
                y += vec3i.getY();
            } else if (vec3i2.getY() != 0 && Mth.floor(x) - i == vec3i2.getX() && Mth.floor(z) - k == vec3i2.getZ()) {
                y += vec3i2.getY();
            }

            return this.getPos(x, y, z);
        } else {
            return null;
        }
    }

    @Nullable
    public Vec3 getPos(double x, double y, double z) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);
        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            j--;
        }

        BlockState blockState = this.level().getBlockState(new BlockPos(i, j, k));
        if (BaseRailBlock.isRail(blockState)) {
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = exits(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i2 = pair.getSecond();
            double d = i + 0.5 + vec3i.getX() * 0.5;
            double e = j + 0.0625 + vec3i.getY() * 0.5;
            double f = k + 0.5 + vec3i.getZ() * 0.5;
            double g = i + 0.5 + vec3i2.getX() * 0.5;
            double h = j + 0.0625 + vec3i2.getY() * 0.5;
            double l = k + 0.5 + vec3i2.getZ() * 0.5;
            double m = g - d;
            double n = (h - e) * 2.0;
            double o = l - f;
            double p;
            if (m == 0.0) {
                p = z - k;
            } else if (o == 0.0) {
                p = x - i;
            } else {
                double q = x - d;
                double r = z - f;
                p = (q * m + r * o) * 2.0;
            }

            x = d + m * p;
            y = e + n * p;
            z = f + o * p;
            if (n < 0.0) {
                y++;
            } else if (n > 0.0) {
                y += 0.5;
            }

            return new Vec3(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB aABB = this.getBoundingBox();
        return this.hasCustomDisplay() ? aABB.inflate(Math.abs(this.getDisplayOffset()) / 16.0) : aABB;
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.getBoolean("CustomDisplayTile")) {
            this.setDisplayBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("DisplayState")));
            this.setDisplayOffset(compound.getInt("DisplayOffset"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.hasCustomDisplay()) {
            compound.putBoolean("CustomDisplayTile", true);
            compound.put("DisplayState", NbtUtils.writeBlockState(this.getDisplayBlockState()));
            compound.putInt("DisplayOffset", this.getDisplayOffset());
        }
    }

    /**
     * Applies a velocity to the entities, to push them away from each other.
     */
    @Override
    public void push(Entity entity) {
        if (!this.level().isClientSide) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(entity)) {
                    double d = entity.getX() - this.getX();
                    double e = entity.getZ() - this.getZ();
                    double f = d * d + e * e;
                    if (f >= 1.0E-4F) {
                        f = Math.sqrt(f);
                        d /= f;
                        e /= f;
                        double g = 1.0 / f;
                        if (g > 1.0) {
                            g = 1.0;
                        }

                        d *= g;
                        e *= g;
                        d *= 0.1F;
                        e *= 0.1F;
                        d *= 0.5;
                        e *= 0.5;
                        if (entity instanceof AbstractMinecart) {
                            double h = entity.getX() - this.getX();
                            double i = entity.getZ() - this.getZ();
                            Vec3 vec3 = new Vec3(h, 0.0, i).normalize();
                            Vec3 vec32 = new Vec3(Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)), 0.0, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)))
                                .normalize();
                            double j = Math.abs(vec3.dot(vec32));
                            if (j < 0.8F) {
                                return;
                            }

                            Vec3 vec33 = this.getDeltaMovement();
                            Vec3 vec34 = entity.getDeltaMovement();
                            if (((AbstractMinecart)entity).getMinecartType() == AbstractMinecart.Type.FURNACE
                                && this.getMinecartType() != AbstractMinecart.Type.FURNACE) {
                                this.setDeltaMovement(vec33.multiply(0.2, 1.0, 0.2));
                                this.push(vec34.x - d, 0.0, vec34.z - e);
                                entity.setDeltaMovement(vec34.multiply(0.95, 1.0, 0.95));
                            } else if (((AbstractMinecart)entity).getMinecartType() != AbstractMinecart.Type.FURNACE
                                && this.getMinecartType() == AbstractMinecart.Type.FURNACE) {
                                entity.setDeltaMovement(vec34.multiply(0.2, 1.0, 0.2));
                                entity.push(vec33.x + d, 0.0, vec33.z + e);
                                this.setDeltaMovement(vec33.multiply(0.95, 1.0, 0.95));
                            } else {
                                double k = (vec34.x + vec33.x) / 2.0;
                                double l = (vec34.z + vec33.z) / 2.0;
                                this.setDeltaMovement(vec33.multiply(0.2, 1.0, 0.2));
                                this.push(k - d, 0.0, l - e);
                                entity.setDeltaMovement(vec34.multiply(0.2, 1.0, 0.2));
                                entity.push(k + d, 0.0, l + e);
                            }
                        } else {
                            this.push(-d, 0.0, -e);
                            entity.push(d / 4.0, 0.0, e / 4.0);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps + 2;
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    /**
     * Updates the entity motion clientside, called by packets from the server
     */
    @Override
    public void lerpMotion(double x, double y, double z) {
        this.targetDeltaMovement = new Vec3(x, y, z);
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    public abstract AbstractMinecart.Type getMinecartType();

    public BlockState getDisplayBlockState() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayBlockState() : Block.stateById(this.getEntityData().get(DATA_ID_DISPLAY_BLOCK));
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayOffset() : this.getEntityData().get(DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setDisplayBlockState(BlockState displayState) {
        this.getEntityData().set(DATA_ID_DISPLAY_BLOCK, Block.getId(displayState));
        this.setCustomDisplay(true);
    }

    public void setDisplayOffset(int displayOffset) {
        this.getEntityData().set(DATA_ID_DISPLAY_OFFSET, displayOffset);
        this.setCustomDisplay(true);
    }

    public boolean hasCustomDisplay() {
        return this.getEntityData().get(DATA_ID_CUSTOM_DISPLAY);
    }

    public void setCustomDisplay(boolean customDisplay) {
        this.getEntityData().set(DATA_ID_CUSTOM_DISPLAY, customDisplay);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(switch (this.getMinecartType()) {
            case CHEST -> Items.CHEST_MINECART;
            case FURNACE -> Items.FURNACE_MINECART;
            case TNT -> Items.TNT_MINECART;
            default -> Items.MINECART;
            case HOPPER -> Items.HOPPER_MINECART;
            case COMMAND_BLOCK -> Items.COMMAND_BLOCK_MINECART;
        });
    }

    public enum Type {
        RIDEABLE,
        CHEST,
        FURNACE,
        TNT,
        SPAWNER,
        HOPPER,
        COMMAND_BLOCK;
    }
}
