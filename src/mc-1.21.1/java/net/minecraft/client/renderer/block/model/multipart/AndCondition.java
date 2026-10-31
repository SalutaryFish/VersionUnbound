package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class AndCondition implements Condition {
    public static final String TOKEN = "AND";
    private final Iterable<? extends Condition> conditions;

    public AndCondition(Iterable<? extends Condition> conditions) {
        this.conditions = conditions;
    }

    @Override
    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> definition) {
        List<Predicate<BlockState>> list = Streams.stream(this.conditions).map(condition -> condition.getPredicate(definition)).collect(Collectors.toList());
        return state -> list.stream().allMatch(predicate -> predicate.test(state));
    }
}
