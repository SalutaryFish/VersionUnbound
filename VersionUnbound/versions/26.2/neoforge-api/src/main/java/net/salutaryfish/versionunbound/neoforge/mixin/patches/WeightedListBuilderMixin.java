package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.salutaryfish.versionunbound.neoforge.patches.WeightedListBuilderExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge's patch to {@link WeightedList.Builder}: entries go into a mutable list instead of an
 * {@code ImmutableList.Builder}, so that the methods of {@link WeightedListBuilderExtension} can remove them again.
 * {@code WeightedList}'s constructor copies the entries, as it did before.
 */
@Mixin(WeightedList.Builder.class)
abstract class WeightedListBuilderMixin<E> implements WeightedListBuilderExtension.Entries<E> {
    @Unique
    private final List<Weighted<E>> versionunbound$result = new ArrayList<>();

    @Override
    public List<Weighted<E>> versionunbound$entries() {
        return this.versionunbound$result;
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "add(Ljava/lang/Object;I)Lnet/minecraft/util/random/WeightedList$Builder;", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;add(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;"))
    private ImmutableList.Builder<Weighted<E>> versionunbound$addToList(ImmutableList.Builder<Weighted<E>> result, Object entry) {
        this.versionunbound$result.add((Weighted<E>) entry);
        return result;
    }

    @Redirect(method = "build", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;build()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<Weighted<E>> versionunbound$buildFromList(ImmutableList.Builder<Weighted<E>> result) {
        return ImmutableList.copyOf(this.versionunbound$result);
    }
}
