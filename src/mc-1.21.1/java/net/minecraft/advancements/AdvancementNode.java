package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import javax.annotation.Nullable;

public class AdvancementNode {
    private final AdvancementHolder holder;
    @Nullable
    private final AdvancementNode parent;
    private final Set<AdvancementNode> children = new ReferenceOpenHashSet<>();

    @VisibleForTesting
    public AdvancementNode(AdvancementHolder holder, @Nullable AdvancementNode parent) {
        this.holder = holder;
        this.parent = parent;
    }

    public Advancement advancement() {
        return this.holder.value();
    }

    public AdvancementHolder holder() {
        return this.holder;
    }

    @Nullable
    public AdvancementNode parent() {
        return this.parent;
    }

    public AdvancementNode root() {
        return getRoot(this);
    }

    public static AdvancementNode getRoot(AdvancementNode node) {
        AdvancementNode advancementNode = node;

        while (true) {
            AdvancementNode advancementNode2 = advancementNode.parent();
            if (advancementNode2 == null) {
                return advancementNode;
            }

            advancementNode = advancementNode2;
        }
    }

    public Iterable<AdvancementNode> children() {
        return this.children;
    }

    @VisibleForTesting
    public void addChild(AdvancementNode child) {
        this.children.add(child);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof AdvancementNode advancementNode && this.holder.equals(advancementNode.holder);
    }

    @Override
    public int hashCode() {
        return this.holder.hashCode();
    }

    @Override
    public String toString() {
        return this.holder.id().toString();
    }
}
