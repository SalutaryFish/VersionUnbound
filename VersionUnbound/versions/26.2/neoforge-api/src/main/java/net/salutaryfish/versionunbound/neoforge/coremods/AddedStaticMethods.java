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
 * calls another constructor of the class (by default the no-argument one), so field initializers (also those of
 * mixins) run, and so do mixins' injections into that constructor; each of that constructor's arguments is one of the
 * added constructor's own, or computed from some of them by a static method of the {@code patches/} class. Then, if
 * the entry says so, it hands {@code this} and its arguments to a static method named {@code init} in the
 * {@code patches/} class.
 * <p>
 * Added: {@code CropBlock.getGrowthSpeed(BlockState, BlockGetter, BlockPos)} (KaleidoscopeCookery's rice calls it),
 * {@code BoneMealItem.applyBonemeal(ItemStack, Level, BlockPos, Player)}, {@code RegistryOps.retrieveRegistryLookup},
 * {@code BuiltInRegistries.getVanillaRegistrationOrder}, {@code SpawnPlacements.hasPlacement} and
 * {@code fireSpawnPlacementEvent}, {@code create(Identifier)} on {@code BlockTags}, {@code ItemTags} and {@code FluidTags},
 * {@code DyeColor.getColor}, {@code AxeItem.getAxeStrippingState}, {@code ShovelItem.getShovelPathingState}, and the
 * constructors {@code AttributeSupplier.Builder(AttributeSupplier)} and NeoForge's three added {@code BlockEntityType}
 * constructors ({@code (supplier, Set, boolean)} and the two varargs ones). To add one,
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

    private static final String BLOCK_ENTITY_TYPE = "net/minecraft/world/level/block/entity/BlockEntityType";
    /** Vanilla's constructor, which Fabric API's {@code BlockEntityTypeMixin} injects into: NeoForge's all end up there. */
    private static final String BLOCK_ENTITY_TYPE_VANILLA = "(Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;Ljava/util/Set;)V";
    private static final String BLOCK_ENTITY_TYPE_PATCH = "net/salutaryfish/versionunbound/neoforge/patches/BlockEntityTypeExtension";

    private static final List<AddedConstructor> CONSTRUCTORS = List.of(
            new AddedConstructor("net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder", Opcodes.ACC_PUBLIC,
                    "(Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;)V", "()V", List.of(), true,
                    "net/salutaryfish/versionunbound/neoforge/patches/AttributeSupplierBuilderExtension", true),
            // BlockEntityType(supplier, Set, boolean): vanilla's constructor, then init records onlyOpCanSetNbt.
            new AddedConstructor(BLOCK_ENTITY_TYPE, Opcodes.ACC_PUBLIC,
                    "(Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;Ljava/util/Set;Z)V", BLOCK_ENTITY_TYPE_VANILLA,
                    List.of(new Parameter(0), new Parameter(1)), true, BLOCK_ENTITY_TYPE_PATCH, true),
            // BlockEntityType(supplier, Block...): vanilla's constructor with validBlocks(blocks).
            new AddedConstructor(BLOCK_ENTITY_TYPE, Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS,
                    "(Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;[Lnet/minecraft/world/level/block/Block;)V",
                    BLOCK_ENTITY_TYPE_VANILLA, List.of(new Parameter(0), new Computed("validBlocks", 1)), false, BLOCK_ENTITY_TYPE_PATCH, true),
            // BlockEntityType(supplier, boolean, Block...): vanilla's constructor with validBlocks(blocks), then init.
            new AddedConstructor(BLOCK_ENTITY_TYPE, Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS,
                    "(Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;Z[Lnet/minecraft/world/level/block/Block;)V",
                    BLOCK_ENTITY_TYPE_VANILLA, List.of(new Parameter(0), new Computed("validBlocks", 2)), true, BLOCK_ENTITY_TYPE_PATCH, true));

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
     * A constructor of {@code owner} that calls its constructor {@code delegate} with {@code arguments}, then, if
     * {@code init}, {@code implementation.init(owner, parameters...)}.
     */
    private record AddedConstructor(String owner, int access, String descriptor, String delegate, List<Argument> arguments, boolean init,
            String implementation, boolean implementationIsInterface) {
        MethodNode forwarder() {
            Type[] parameters = Type.getArgumentTypes(descriptor);
            int[] slots = new int[parameters.length];
            int slot = 1;
            for (int i = 0; i < parameters.length; i++) {
                slots[i] = slot;
                slot += parameters[i].getSize();
            }
            Type[] delegateParameters = Type.getArgumentTypes(delegate);
            if (delegateParameters.length != arguments.size()) {
                throw new IllegalStateException(owner + ".<init>" + descriptor + ": " + arguments.size() + " arguments for <init>" + delegate);
            }
            MethodNode node = new MethodNode(access, "<init>", descriptor, null, null);
            node.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            int stack = 1;
            int maxStack = 1;
            for (int i = 0; i < arguments.size(); i++) {
                switch (arguments.get(i)) {
                    case Parameter(int index) -> load(node, parameters, slots, index);
                    case Computed(String method, int[] from) -> {
                        StringBuilder computedDescriptor = new StringBuilder("(");
                        int fromSize = 0;
                        for (int index : from) {
                            load(node, parameters, slots, index);
                            computedDescriptor.append(parameters[index].getDescriptor());
                            fromSize += parameters[index].getSize();
                        }
                        maxStack = Math.max(maxStack, stack + fromSize);
                        computedDescriptor.append(')').append(delegateParameters[i].getDescriptor());
                        node.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation, method, computedDescriptor.toString(), implementationIsInterface));
                    }
                }
                stack += delegateParameters[i].getSize();
                maxStack = Math.max(maxStack, stack);
            }
            node.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "<init>", delegate, false));
            if (init) {
                node.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                for (int i = 0; i < parameters.length; i++) {
                    load(node, parameters, slots, i);
                }
                String initDescriptor = "(L" + owner + ";" + descriptor.substring(1);
                node.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation, "init", initDescriptor, implementationIsInterface));
                maxStack = Math.max(maxStack, slot);
            }
            node.instructions.add(new InsnNode(Opcodes.RETURN));
            node.maxLocals = slot;
            node.maxStack = maxStack;
            return node;
        }

        private static void load(MethodNode node, Type[] parameters, int[] slots, int index) {
            node.instructions.add(new VarInsnNode(parameters[index].getOpcode(Opcodes.ILOAD), slots[index]));
        }
    }

    /** Where an added constructor gets one argument of the constructor it calls. */
    private sealed interface Argument permits Parameter, Computed {}

    /** The added constructor's own parameter {@code index} (from 0, {@code this} not counted). */
    private record Parameter(int index) implements Argument {}

    /**
     * The result of the {@code patches/} class's static {@code method}, given the added constructor's parameters
     * {@code from}, in that order; it returns the type the called constructor takes there.
     */
    private record Computed(String method, int... from) implements Argument {}

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
