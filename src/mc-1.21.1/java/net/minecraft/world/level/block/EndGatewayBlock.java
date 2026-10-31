package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return CODEC;
    }

    protected EndGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndGatewayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
            blockEntityType,
            BlockEntityType.END_GATEWAY,
            level.isClientSide ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick
        );
    }

    /**
     * Called periodically clientside on blocks near the player to show effects (like furnace fire particles).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TheEndGatewayBlockEntity) {
            int i = ((TheEndGatewayBlockEntity)blockEntity).getParticleAmount();

            for (int j = 0; j < i; j++) {
                double d = pos.getX() + random.nextDouble();
                double e = pos.getY() + random.nextDouble();
                double f = pos.getZ() + random.nextDouble();
                double g = (random.nextDouble() - 0.5) * 0.5;
                double h = (random.nextDouble() - 0.5) * 0.5;
                double k = (random.nextDouble() - 0.5) * 0.5;
                int l = random.nextInt(2) * 2 - 1;
                if (random.nextBoolean()) {
                    f = pos.getZ() + 0.5 + 0.25 * l;
                    k = random.nextFloat() * 2.0F * l;
                } else {
                    d = pos.getX() + 0.5 + 0.25 * l;
                    g = random.nextFloat() * 2.0F * l;
                }

                level.addParticle(ParticleTypes.PORTAL, d, e, f, g, h, k);
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)
            && !level.isClientSide
            && level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity
            && !theEndGatewayBlockEntity.isCoolingDown()) {
            entity.setAsInsidePortal(this, pos);
            TheEndGatewayBlockEntity.triggerCooldown(level, pos, state, theEndGatewayBlockEntity);
        }
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
            Vec3 vec3 = theEndGatewayBlockEntity.getPortalPosition(level, pos);
            return vec3 != null
                ? new DimensionTransition(
                    level, vec3, calculateExitMovement(entity), entity.getYRot(), entity.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET
                )
                : null;
        } else {
            return null;
        }
    }

    private static Vec3 calculateExitMovement(Entity entity) {
        return entity instanceof ThrownEnderpearl ? new Vec3(0.0, -1.0, 0.0) : entity.getDeltaMovement();
    }
}
