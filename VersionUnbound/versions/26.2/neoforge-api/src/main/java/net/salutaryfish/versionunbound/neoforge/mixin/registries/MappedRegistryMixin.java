package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.callback.BakeCallback;
import net.neoforged.neoforge.registries.callback.ClearCallback;
import net.neoforged.neoforge.registries.callback.RegistryCallback;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.salutaryfish.versionunbound.neoforge.registries.MappedRegistryExtension;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@link MappedRegistry} and its superclass {@code BaseMappedRegistry}: the
 * {@link net.neoforged.neoforge.registries.IRegistryExtension} methods (injected into {@link Registry} by the class
 * tweaker), registry callbacks, an id limit and data map storage.
 *
 * <p>Two parts of that patch are Fabric API's already, so they aren't repeated here: binding a holder's value as soon
 * as it's registered, and aliases ({@code addAlias} and the alias lookups are Fabric's implementation, which has the
 * same signature). Sync is Fabric's {@code SYNCED} registry attribute, since Fabric API syncs registry ids.
 *
 * <p>Applied after Fabric API's mixin (higher priority), so add callbacks see the value bound to its holder.
 */
@Mixin(value = MappedRegistry.class, priority = 1100)
abstract class MappedRegistryMixin<T> implements MappedRegistryExtension<T> {
    @Shadow
    @Final
    private ObjectList<Holder.Reference<T>> byId;
    @Shadow
    @Final
    private Map<T, Holder.Reference<T>> byValue;

    @Unique
    private final List<AddCallback<T>> versionunbound$addCallbacks = new ArrayList<>();
    @Unique
    private final List<BakeCallback<T>> versionunbound$bakeCallbacks = new ArrayList<>();
    @Unique
    private final List<ClearCallback<T>> versionunbound$clearCallbacks = new ArrayList<>();
    @Unique
    private final Map<DataMapType<T, ?>, Map<ResourceKey<T>, ?>> versionunbound$dataMaps = new IdentityHashMap<>();
    @Unique
    private int versionunbound$maxId = Integer.MAX_VALUE - 1;
    @Unique
    private boolean versionunbound$listensForRemaps;

    @Shadow
    public abstract ResourceKey<? extends Registry<T>> key();

    @Shadow
    public abstract Optional<Holder.Reference<T>> get(Identifier id);

    @Shadow
    public abstract @Nullable T getValue(@Nullable Identifier id);

    @Shadow
    public abstract @Nullable T getValue(@Nullable ResourceKey<T> key);

    @Shadow
    public abstract int getId(@Nullable T value);

    @Unique
    @SuppressWarnings("unchecked")
    private Registry<T> versionunbound$self() {
        return (Registry<T>) (Object) this;
    }

    // IRegistryExtension

    public boolean doesSync() {
        return RegistryAttributeHolder.get(this.key()).hasAttribute(RegistryAttribute.SYNCED);
    }

    public int getMaxId() {
        return this.versionunbound$maxId;
    }

    public void addCallback(RegistryCallback<T> callback) {
        if (callback instanceof AddCallback<T> addCallback)
            this.versionunbound$addCallbacks.add(addCallback);
        if (callback instanceof BakeCallback<T> bakeCallback)
            this.versionunbound$bakeCallbacks.add(bakeCallback);
        if (callback instanceof ClearCallback<T> clearCallback)
            this.versionunbound$clearCallbacks.add(clearCallback);
        if (callback instanceof BakeCallback<T> || callback instanceof ClearCallback<T>)
            this.versionunbound$listenForRemaps();
    }

    /**
     * NeoForge clears a registry's ids and freezes it again when the client applies the server's ids, and when it goes
     * back to its own ids on disconnect; that runs the clear and bake callbacks. Fabric API remaps ids in place instead,
     * so run them when it does.
     */
    @Unique
    private void versionunbound$listenForRemaps() {
        if (this.versionunbound$listensForRemaps) return;
        this.versionunbound$listensForRemaps = true;
        RegistryIdRemapCallback.event(this.versionunbound$self()).register(state -> {
            this.versionunbound$clearCallbacks.forEach(clearCallback -> clearCallback.onClear(this.versionunbound$self(), false));
            this.versionunbound$bakeCallbacks.forEach(bakeCallback -> bakeCallback.onBake(this.versionunbound$self()));
        });
    }

    /**
     * Fabric API's aliases apply to {@link #get(Identifier)}, so the holder found there carries the name an alias points
     * to. Unlike NeoForge, an alias whose target isn't registered resolves to the name itself.
     */
    public Identifier resolve(Identifier name) {
        return this.get(name).map(holder -> holder.key().identifier()).filter(resolved -> !resolved.equals(name)).orElse(name);
    }

    public ResourceKey<T> resolve(ResourceKey<T> key) {
        Identifier resolvedName = resolve(key.identifier());
        // Try to reuse the key if possible
        return resolvedName == key.identifier() ? key : ResourceKey.create(this.key(), resolvedName);
    }

    public int getId(Identifier name) {
        return getId(getValue(name));
    }

    public int getId(ResourceKey<T> key) {
        return getId(getValue(key));
    }

    public boolean containsValue(T value) {
        return byValue.containsKey(value);
    }

    /** NeoForge's {@code DefaultedMappedRegistry} overrides this to skip the default key; this body does that for both. */
    public @Nullable Identifier getKeyOrNull(T element) {
        Holder.Reference<T> holder = this.byValue.get(element);
        return holder != null ? holder.key().identifier() : null;
    }

    public <A> @Nullable A getData(DataMapType<T, A> type, ResourceKey<T> key) {
        final var innerMap = versionunbound$dataMaps.get(type);
        return innerMap == null ? null : (A) innerMap.get(key);
    }

    public <A> Map<ResourceKey<T>, A> getDataMap(DataMapType<T, A> type) {
        return (Map<ResourceKey<T>, A>) versionunbound$dataMaps.getOrDefault(type, Map.of());
    }

    // MappedRegistryExtension (BaseMappedRegistry)

    /** Only adds the attribute: registries are only built with {@code false}, and Fabric API's attributes can't be removed. */
    @Override
    public void versionunbound$setSync(boolean sync) {
        if (sync) {
            RegistryAttributeHolder.get(this.key()).addAttribute(RegistryAttribute.SYNCED);
        }
    }

    @Override
    public void versionunbound$setMaxId(int maxId) {
        this.versionunbound$maxId = maxId;
    }

    @Override
    public Map<DataMapType<T, ?>, Map<ResourceKey<T>, ?>> versionunbound$getDataMaps() {
        return versionunbound$dataMaps;
    }

    // MappedRegistry

    @Inject(method = "register", at = @At(value = "INVOKE", target = "Ljava/util/Objects;requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1, shift = At.Shift.AFTER))
    private void versionunbound$checkMaxId(ResourceKey<T> key, T value, RegistrationInfo registrationInfo, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        int newId = this.byId.size();
        if (newId > this.getMaxId())
            throw new IllegalStateException(String.format(Locale.ENGLISH, "Invalid id %d - maximum id range of %d exceeded.", newId, this.getMaxId()));
    }

    @Inject(method = "register", at = @At("RETURN"))
    private void versionunbound$runAddCallbacks(ResourceKey<T> key, T value, RegistrationInfo registrationInfo, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (this.versionunbound$addCallbacks.isEmpty()) return;
        int newId = this.getId(value);
        this.versionunbound$addCallbacks.forEach(addCallback -> addCallback.onAdd(this.versionunbound$self(), newId, key, value));
    }

    /** NeoForge runs the bake callbacks once intrusive holders are checked, before tags are bound. */
    @Inject(method = "freeze", at = @At(value = "FIELD", target = "Lnet/minecraft/core/MappedRegistry;allTags:Lnet/minecraft/core/MappedRegistry$TagSet;", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void versionunbound$runBakeCallbacks(CallbackInfoReturnable<Registry<T>> cir) {
        this.versionunbound$bakeCallbacks.forEach(bakeCallback -> bakeCallback.onBake(this.versionunbound$self()));
    }
}
