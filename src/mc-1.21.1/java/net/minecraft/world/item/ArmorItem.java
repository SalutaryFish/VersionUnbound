package net.minecraft.world.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class ArmorItem extends Item implements Equipable {
    public static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource blockSource, ItemStack itemStack) {
            return ArmorItem.dispenseArmor(blockSource, itemStack) ? itemStack : super.execute(blockSource, itemStack);
        }
    };
    protected final ArmorItem.Type type;
    protected final Holder<ArmorMaterial> material;
    private final Supplier<ItemAttributeModifiers> defaultModifiers;

    public static boolean dispenseArmor(BlockSource blockSource, ItemStack armorItem) {
        BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
        List<LivingEntity> list = blockSource.level()
            .getEntitiesOfClass(
                LivingEntity.class, new AABB(blockPos), EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(armorItem))
            );
        if (list.isEmpty()) {
            return false;
        }

        LivingEntity livingEntity = list.get(0);
        EquipmentSlot equipmentSlot = livingEntity.getEquipmentSlotForItem(armorItem);
        ItemStack itemStack = armorItem.split(1);
        livingEntity.setItemSlot(equipmentSlot, itemStack);
        if (livingEntity instanceof Mob) {
            ((Mob)livingEntity).setDropChance(equipmentSlot, 2.0F);
            ((Mob)livingEntity).setPersistenceRequired();
        }

        return true;
    }

    public ArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties properties) {
        super(properties);
        this.material = material;
        this.type = type;
        DispenserBlock.registerBehavior(this, DISPENSE_ITEM_BEHAVIOR);
        this.defaultModifiers = Suppliers.memoize(
            () -> {
                int i = material.value().getDefense(type);
                float f = material.value().toughness();
                ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
                EquipmentSlotGroup equipmentSlotGroup = EquipmentSlotGroup.bySlot(type.getSlot());
                ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("armor." + type.getName());
                builder.add(Attributes.ARMOR, new AttributeModifier(resourceLocation, i, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup);
                builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(resourceLocation, f, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup);
                float g = material.value().knockbackResistance();
                if (g > 0.0F) {
                    builder.add(
                        Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(resourceLocation, g, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup
                    );
                }

                return builder.build();
            }
        );
    }

    public ArmorItem.Type getType() {
        return this.type;
    }

    @Override
    public int getEnchantmentValue() {
        return this.material.value().enchantmentValue();
    }

    public Holder<ArmorMaterial> getMaterial() {
        return this.material;
    }

    /**
     * Return whether this item is repairable in an anvil.
     */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return this.material.value().repairIngredient().get().test(repair) || super.isValidRepairItem(toRepair, repair);
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return this.defaultModifiers.get();
    }

    public int getDefense() {
        return this.material.value().getDefense(this.type);
    }

    public float getToughness() {
        return this.material.value().toughness();
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return this.type.getSlot();
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return this.getMaterial().value().equipSound();
    }

    public enum Type implements StringRepresentable {
        HELMET(EquipmentSlot.HEAD, 11, "helmet"),
        CHESTPLATE(EquipmentSlot.CHEST, 16, "chestplate"),
        LEGGINGS(EquipmentSlot.LEGS, 15, "leggings"),
        BOOTS(EquipmentSlot.FEET, 13, "boots"),
        BODY(EquipmentSlot.BODY, 16, "body");

        public static final Codec<ArmorItem.Type> CODEC = StringRepresentable.fromValues(ArmorItem.Type::values);
        private final EquipmentSlot slot;
        private final String name;
        private final int durability;

        Type(final EquipmentSlot slot, final int durability, final String name) {
            this.slot = slot;
            this.name = name;
            this.durability = durability;
        }

        public int getDurability(int durabilityFactor) {
            return this.durability * durabilityFactor;
        }

        public EquipmentSlot getSlot() {
            return this.slot;
        }

        public String getName() {
            return this.name;
        }

        public boolean hasTrims() {
            return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
