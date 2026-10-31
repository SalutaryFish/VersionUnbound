package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * NeoForge's patch to {@link AttributeSupplier.Builder}, injected into it by the class tweaker. NeoForge keeps the
 * attribute instances in a mutable map, so that a builder can take in another builder's attributes and say which ones it
 * has; {@code AttributeSupplierBuilderMixin} adds that map and exposes it through {@link Entries}.
 */
public interface AttributeSupplierBuilderExtension {
    /**
     * Stands in for NeoForge's {@code Builder(AttributeSupplier)} constructor for Layer A's own code, which compiles
     * against vanilla's class: a builder that starts out with the supplier's attribute instances. Mods call the
     * constructor itself, which {@code AddedStaticMethods} adds and which runs {@link #init}.
     */
    static AttributeSupplier.Builder copyOf(AttributeSupplier attributeMap) {
        AttributeSupplier.Builder builder = new AttributeSupplier.Builder();
        init(builder, attributeMap);
        return builder;
    }

    /** The body of NeoForge's {@code Builder(AttributeSupplier)} constructor, after the no-argument constructor ran. */
    static void init(AttributeSupplier.Builder builder, AttributeSupplier attributeMap) {
        ((Entries) builder).versionunbound$instances().putAll(attributeMap.instances);
    }

    private Entries entries() {
        return (Entries) this;
    }

    default void combine(AttributeSupplier.Builder other) {
        this.entries().versionunbound$instances().putAll(((Entries) other).versionunbound$instances());
        this.entries().versionunbound$others().add(other);
    }

    default boolean hasAttribute(Holder<Attribute> attribute) {
        return this.entries().versionunbound$instances().containsKey(attribute);
    }

    /**
     * The builder's attribute instances, and the builders combined into it (frozen with it), implemented by
     * {@code AttributeSupplierBuilderMixin}.
     */
    interface Entries {
        Map<Holder<Attribute>, AttributeInstance> versionunbound$instances();

        List<AttributeSupplier.Builder> versionunbound$others();
    }
}
