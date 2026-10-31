package net.salutaryfish.versionunbound.neoforge.coremods;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Static methods NeoForge's patches add to vanilla classes, which mods call. A mixin can't add them (Mixin rejects
 * non-private static methods), so {@link CoremodMixinPlugin} adds each one in {@code preApply} of the carrier mixin
 * {@code AddedStaticMethodsTargetsMixin}, as a method with NeoForge's access, name and descriptor that hands its
 * arguments to the static method of the same name and descriptor in a {@code patches/} class, where the code is.
 * Mods' {@code invokestatic} calls (also those naming a subclass as the owner) then resolve as on NeoForge.
 * <p>
 * <p>
 * The same goes for constructors NeoForge's patches add (Mixin can't add constructors either): the added constructor
 * calls the class's no-argument constructor, so field initializers (also those of mixins) run, then hands {@code this}
 * and its arguments to a static method named {@code init} in the {@code patches/} class.
 * <p>
 * Added: {@code CropBlock.getGrowthSpeed(BlockState, BlockGetter, BlockPos)} (KaleidoscopeCookery's rice calls it),
 * {@code BoneMealItem.applyBonemeal(ItemStack, Level, BlockPos, Player)}, {@code RegistryOps.retrieveRegistryLookup},
 * {@code BuiltInRegistries.getVanillaRegistrationOrder}, {@code SpawnPlacements.hasPlacement} and
 * {@code fireSpawnPlacementEvent}, {@code create(Identifier)} on {@code BlockTags}, {@code ItemTags} and {@code FluidTags},
 * {@code DyeColor.getColor}, {@code AxeItem.getAxeStrippingState}, {@code ShovelItem.getShovelPathingState}, and the
 * constructor {@code AttributeSupplier.Builder(AttributeSupplier)}. To add one,
 * add a line here and the class to the carrier.
 */
final class AddedStaticMethods {
    private static final List<AddedStaticMethod> METHODS = List.of(
            new AddedStaticMethod("net/minecraft/world/level/block/CropBlock", Opcodes.ACC_PROTECTED, "getGrowthSpeed",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
                    "net/salutaryfish/versionunbound/neoforge/patches/CropBlockPatch"),
            new AddedStaticMethod("net/minecraft/world/item/BoneMealItem", Opcodes.ACC_PUBLIC, "applyBonemeal",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z",
                    "net/salutaryfish/versionunbound/neoforge/patches/BoneMealItemPatch"),
            new AddedStaticMethod("net/minecraft/resources/RegistryOps", Opcodes.ACC_PUBLIC, "retrieveRegistryLookup",
                    "(Lnet/minecraft/resources/ResourceKey;)Lcom/mojang/serialization/MapCodec;",
                    "net/salutaryfish/versionunbound/neoforge/patches/RegistryOpsPatch"),
            new AddedStaticMethod("net/minecraft/core/registries/BuiltInRegistries", Opcodes.ACC_PUBLIC, "getVanillaRegistrationOrder",
                    "()Ljava/util/Set;", "net/salutaryfish/versionunbound/neoforge/patches/BuiltInRegistriesPatch"),
            new AddedStaticMethod("net/minecraft/world/entity/SpawnPlacements", Opcodes.ACC_PUBLIC, "hasPlacement",
                    "(Lnet/minecraft/world/entity/EntityType;)Z", "net/salutaryfish/versionunbound/neoforge/patches/SpawnPlacementsPatch"),
            new AddedStaticMethod("net/minecraft/world/entity/SpawnPlacements", Opcodes.ACC_PUBLIC, "fireSpawnPlacementEvent",
                    "()V", "net/salutaryfish/versionunbound/neoforge/patches/SpawnPlacementsPatch"),
            new AddedStaticMethod("net/minecraft/tags/BlockTags", Opcodes.ACC_PUBLIC, "create", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/tags/TagKey;",
                    "net/salutaryfish/versionunbound/neoforge/patches/TagsPatch", "createBlockTag", false),
            new AddedStaticMethod("net/minecraft/tags/ItemTags", Opcodes.ACC_PUBLIC, "create", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/tags/TagKey;",
                    "net/salutaryfish/versionunbound/neoforge/patches/TagsPatch", "createItemTag", false),
            new AddedStaticMethod("net/minecraft/tags/FluidTags", Opcodes.ACC_PUBLIC, "create", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/tags/TagKey;",
                    "net/salutaryfish/versionunbound/neoforge/patches/TagsPatch", "createFluidTag", false),
            new AddedStaticMethod("net/minecraft/world/item/DyeColor", Opcodes.ACC_PUBLIC, "getColor",
                    "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/DyeColor;",
                    "net/salutaryfish/versionunbound/neoforge/patches/DyeColorExtension", "getColor", true),
            new AddedStaticMethod("net/minecraft/world/item/AxeItem", Opcodes.ACC_PUBLIC, "getAxeStrippingState",
                    "(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;",
                    "net/salutaryfish/versionunbound/neoforge/patches/ToolItemsPatch"),
            new AddedStaticMethod("net/minecraft/world/item/ShovelItem", Opcodes.ACC_PUBLIC, "getShovelPathingState",
                    "(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;",
                    "net/salutaryfish/versionunbound/neoforge/patches/ToolItemsPatch"));

    private static final List<AddedConstructor> CONSTRUCTORS = List.of(
            new AddedConstructor("net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder", Opcodes.ACC_PUBLIC,
                    "(Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;)V",
                    "net/salutaryfish/versionunbound/neoforge/patches/AttributeSupplierBuilderExtension", true));

    private AddedStaticMethods() {}

    /** Adds the methods that belong to {@code target}; returns how many it added. */
    static int add(ClassNode target) {
        int added = 0;
        for (AddedStaticMethod method : METHODS) {
            if (!method.owner().equals(target.name)) continue;
            boolean present = target.methods.stream().anyMatch(existing -> existing.name.equals(method.name()) && existing.desc.equals(method.descriptor()));
            if (present) {
                throw new IllegalStateException(target.name + " already has " + method.name() + method.descriptor());
            }
            target.methods.add(method.forwarder());
            added++;
        }
        for (AddedConstructor constructor : CONSTRUCTORS) {
            if (!constructor.owner().equals(target.name)) continue;
            boolean present = target.methods.stream().anyMatch(existing -> existing.name.equals("<init>") && existing.desc.equals(constructor.descriptor()));
            if (present) {
                throw new IllegalStateException(target.name + " already has <init>" + constructor.descriptor());
            }
            target.methods.add(constructor.forwarder());
            added++;
        }
        return added;
    }

    /**
     * A constructor of {@code owner} that calls its no-argument constructor, then
     * {@code implementation.init(owner, arguments...)}.
     */
    private record AddedConstructor(String owner, int access, String descriptor, String implementation, boolean implementationIsInterface) {
        MethodNode forwarder() {
            MethodNode node = new MethodNode(access, "<init>", descriptor, null, null);
            node.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            node.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false));
            Type[] arguments = Type.getArgumentTypes(descriptor);
            node.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            int slot = 1;
            for (Type argument : arguments) {
                node.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
                slot += argument.getSize();
            }
            String initDescriptor = "(L" + owner + ";" + descriptor.substring(1);
            node.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation, "init", initDescriptor, implementationIsInterface));
            node.instructions.add(new InsnNode(Opcodes.RETURN));
            node.maxLocals = slot;
            node.maxStack = slot;
            return node;
        }
    }

    /**
     * A static method on {@code owner} whose body is a call of {@code implementation}'s static method of the same
     * descriptor, named {@code implementationName} (by default the same name).
     */
    private record AddedStaticMethod(String owner, int access, String name, String descriptor, String implementation, String implementationName,
            boolean implementationIsInterface) {
        AddedStaticMethod(String owner, int access, String name, String descriptor, String implementation) {
            this(owner, access, name, descriptor, implementation, name, false);
        }

        MethodNode forwarder() {
            MethodNode node = new MethodNode(access | Opcodes.ACC_STATIC, name, descriptor, null, null);
            int slot = 0;
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                node.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
                slot += argument.getSize();
            }
            node.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation, implementationName, descriptor, implementationIsInterface));
            Type result = Type.getReturnType(descriptor);
            node.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
            node.maxLocals = slot;
            node.maxStack = Math.max(slot, result.getSize());
            return node;
        }
    }
}
