package net.salutaryfish.versionunbound.neoforge.patches;

/**
 * NeoForge's six-argument {@code BlocksAttacks.hurtBlockingItem(Level, ItemStack, LivingEntity, InteractionHand, float,
 * int fixedDamage)}, which vanilla's five-argument one calls with {@code -1} ("use the item damage function"), is added
 * by {@code BlocksAttacksMixin}; here vanilla's keeps its body (other mods' mixins target it) and the overload hands it
 * the fixed damage through {@link #FIXED_DAMAGE}. A thread-local, since one {@code BlocksAttacks} component is shared by
 * every stack of an item, on the client and server threads alike.
 */
public final class BlocksAttacksPatch {
    /** The shield damage the current {@code hurtBlockingItem} call takes; unset (or negative) for vanilla's function. */
    public static final ThreadLocal<Integer> FIXED_DAMAGE = new ThreadLocal<>();

    private BlocksAttacksPatch() {}

    /** Runs a five-argument {@code hurtBlockingItem} call as NeoForge's overload with this fixed damage. */
    public static void withFixedDamage(int fixedDamage, Runnable hurtBlockingItem) {
        Integer previous = FIXED_DAMAGE.get();
        FIXED_DAMAGE.set(fixedDamage);
        try {
            hurtBlockingItem.run();
        } finally {
            if (previous == null) {
                FIXED_DAMAGE.remove();
            } else {
                FIXED_DAMAGE.set(previous);
            }
        }
    }

    /** NeoForge's {@code fixedDamage < 0 ? this.itemDamage.apply(damage) : fixedDamage}. */
    public static int itemDamage(int vanillaItemDamage) {
        Integer fixed = FIXED_DAMAGE.get();
        return fixed == null || fixed < 0 ? vanillaItemDamage : fixed;
    }
}
