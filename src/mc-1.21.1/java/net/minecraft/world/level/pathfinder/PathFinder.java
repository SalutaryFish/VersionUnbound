package net.minecraft.world.level.pathfinder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;

public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private final int maxVisitedNodes;
    private final NodeEvaluator nodeEvaluator;
    private static final boolean DEBUG = false;
    private final BinaryHeap openSet = new BinaryHeap();

    public PathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    /**
     * Finds a path to one of the specified positions and post-processes it or returns null if no path could be found within given accuracy
     */
    @Nullable
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targetPositions, float maxRange, int accuracy, float searchDepthMultiplier) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(region, mob);
        Node node = this.nodeEvaluator.getStart();
        if (node == null) {
            return null;
        }

        Map<Target, BlockPos> map = targetPositions.stream()
            .collect(Collectors.toMap(pos -> this.nodeEvaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()), Function.identity()));
        Path path = this.findPath(region.getProfiler(), node, map, maxRange, accuracy, searchDepthMultiplier);
        this.nodeEvaluator.done();
        return path;
    }

    @Nullable
    private Path findPath(ProfilerFiller profiler, Node node, Map<Target, BlockPos> targetPos, float maxRange, int accuracy, float searchDepthMultiplier) {
        profiler.push("find_path");
        profiler.markForCharting(MetricCategory.PATH_FINDING);
        Set<Target> set = targetPos.keySet();
        node.g = 0.0F;
        node.h = this.getBestH(node, set);
        node.f = node.h;
        this.openSet.clear();
        this.openSet.insert(node);
        Set<Node> set2 = ImmutableSet.of();
        int i = 0;
        Set<Target> set3 = Sets.newHashSetWithExpectedSize(set.size());
        int j = (int)(this.maxVisitedNodes * searchDepthMultiplier);

        while (!this.openSet.isEmpty()) {
            if (++i >= j) {
                break;
            }

            Node node2 = this.openSet.pop();
            node2.closed = true;

            for (Target target : set) {
                if (node2.distanceManhattan(target) <= accuracy) {
                    target.setReached();
                    set3.add(target);
                }
            }

            if (!set3.isEmpty()) {
                break;
            }

            if (!(node2.distanceTo(node) >= maxRange)) {
                int k = this.nodeEvaluator.getNeighbors(this.neighbors, node2);

                for (int l = 0; l < k; l++) {
                    Node node3 = this.neighbors[l];
                    float f = this.distance(node2, node3);
                    node3.walkedDistance = node2.walkedDistance + f;
                    float g = node2.g + f + node3.costMalus;
                    if (node3.walkedDistance < maxRange && (!node3.inOpenSet() || g < node3.g)) {
                        node3.cameFrom = node2;
                        node3.g = g;
                        node3.h = this.getBestH(node3, set) * 1.5F;
                        if (node3.inOpenSet()) {
                            this.openSet.changeCost(node3, node3.g + node3.h);
                        } else {
                            node3.f = node3.g + node3.h;
                            this.openSet.insert(node3);
                        }
                    }
                }
            }
        }

        Optional<Path> optional = !set3.isEmpty()
            ? set3.stream()
                .map(pathfinder -> this.reconstructPath(pathfinder.getBestNode(), targetPos.get(pathfinder), true))
                .min(Comparator.comparingInt(Path::getNodeCount))
            : set.stream()
                .map(pathfinder -> this.reconstructPath(pathfinder.getBestNode(), targetPos.get(pathfinder), false))
                .min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
        profiler.pop();
        return optional.isEmpty() ? null : optional.get();
    }

    protected float distance(Node first, Node second) {
        return first.distanceTo(second);
    }

    private float getBestH(Node node, Set<Target> targets) {
        float f = Float.MAX_VALUE;

        for (Target target : targets) {
            float g = node.distanceTo(target);
            target.updateBest(g, node);
            f = Math.min(g, f);
        }

        return f;
    }

    /**
     * Converts a recursive path point structure into a path
     */
    private Path reconstructPath(Node point, BlockPos targetPos, boolean reachesTarget) {
        List<Node> list = Lists.newArrayList();
        Node node = point;
        list.add(0, node);

        while (node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }

        return new Path(list, targetPos, reachesTarget);
    }
}
