package net.minecraft.world.entity.schedule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import java.util.Collection;
import java.util.List;

public class Timeline {
    private final List<Keyframe> keyframes = Lists.newArrayList();
    private int previousIndex;

    public ImmutableList<Keyframe> getKeyframes() {
        return ImmutableList.copyOf(this.keyframes);
    }

    public Timeline addKeyframe(int duration, float active) {
        this.keyframes.add(new Keyframe(duration, active));
        this.sortAndDeduplicateKeyframes();
        return this;
    }

    public Timeline addKeyframes(Collection<Keyframe> frames) {
        this.keyframes.addAll(frames);
        this.sortAndDeduplicateKeyframes();
        return this;
    }

    private void sortAndDeduplicateKeyframes() {
        Int2ObjectSortedMap<Keyframe> int2ObjectSortedMap = new Int2ObjectAVLTreeMap<>();
        this.keyframes.forEach(keyframe2 -> int2ObjectSortedMap.put(keyframe2.getTimeStamp(), keyframe2));
        this.keyframes.clear();
        this.keyframes.addAll(int2ObjectSortedMap.values());
        this.previousIndex = 0;
    }

    public float getValueAt(int dayTime) {
        if (this.keyframes.size() <= 0) {
            return 0.0F;
        }

        Keyframe keyframe = this.keyframes.get(this.previousIndex);
        Keyframe keyframe2 = this.keyframes.get(this.keyframes.size() - 1);
        boolean bl = dayTime < keyframe.getTimeStamp();
        int i = bl ? 0 : this.previousIndex;
        float f = bl ? keyframe2.getValue() : keyframe.getValue();

        for (int j = i; j < this.keyframes.size(); j++) {
            Keyframe keyframe3 = this.keyframes.get(j);
            if (keyframe3.getTimeStamp() > dayTime) {
                break;
            }

            this.previousIndex = j;
            f = keyframe3.getValue();
        }

        return f;
    }
}
