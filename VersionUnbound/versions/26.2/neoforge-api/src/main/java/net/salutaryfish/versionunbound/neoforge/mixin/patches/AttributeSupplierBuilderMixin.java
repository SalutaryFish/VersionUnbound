package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.salutaryfish.versionunbound.neoforge.patches.AttributeSupplierBuilderExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The state behind {@link AttributeSupplierBuilderExtension}: NeoForge's patch replaces the builder's
 * {@code ImmutableMap.Builder} with a {@code HashMap}, and freezes the combined builders' instances when it builds.
 */
@Mixin(AttributeSupplier.Builder.class)
abstract class AttributeSupplierBuilderMixin implements AttributeSupplierBuilderExtension.Entries {
    @Shadow
    private boolean instanceFrozen;

    @Unique
    private final Map<Holder<Attribute>, AttributeInstance> versionunbound$builder = new HashMap<>();
    @Unique
    private final List<AttributeSupplier.Builder> versionunbound$others = new ArrayList<>();

    @Override
    public Map<Holder<Attribute>, AttributeInstance> versionunbound$instances() {
        return this.versionunbound$builder;
    }

    @Override
    public List<AttributeSupplier.Builder> versionunbound$others() {
        return this.versionunbound$others;
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"))
    private ImmutableMap.Builder<Holder<Attribute>, AttributeInstance> versionunbound$putInMap(ImmutableMap.Builder<Holder<Attribute>, AttributeInstance> builder, Object attribute, Object instance) {
        this.versionunbound$builder.put((Holder<Attribute>) attribute, (AttributeInstance) instance);
        return builder;
    }

    @Redirect(method = "build", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;buildKeepingLast()Lcom/google/common/collect/ImmutableMap;"))
    private ImmutableMap<Holder<Attribute>, AttributeInstance> versionunbound$buildFromMap(ImmutableMap.Builder<Holder<Attribute>, AttributeInstance> builder) {
        this.versionunbound$others.forEach(other -> ((AttributeSupplierBuilderMixin) (Object) other).instanceFrozen = true);
        return ImmutableMap.copyOf(this.versionunbound$builder);
    }
}
