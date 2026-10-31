package net.minecraft.client.searchtree;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import java.util.Comparator;
import java.util.Iterator;

public class MergingUniqueIterator<T> extends AbstractIterator<T> {
    private final PeekingIterator<T> firstIterator;
    private final PeekingIterator<T> secondIterator;
    private final Comparator<T> comparator;

    public MergingUniqueIterator(Iterator<T> firstIterator, Iterator<T> secondIterator, Comparator<T> comparator) {
        this.firstIterator = Iterators.peekingIterator(firstIterator);
        this.secondIterator = Iterators.peekingIterator(secondIterator);
        this.comparator = comparator;
    }

    @Override
    protected T computeNext() {
        boolean bl = !this.firstIterator.hasNext();
        boolean bl2 = !this.secondIterator.hasNext();
        if (bl && bl2) {
            return this.endOfData();
        }

        if (bl) {
            return this.secondIterator.next();
        }

        if (bl2) {
            return this.firstIterator.next();
        }

        int i = this.comparator.compare(this.firstIterator.peek(), this.secondIterator.peek());
        if (i == 0) {
            this.secondIterator.next();
        }

        return i <= 0 ? this.firstIterator.next() : this.secondIterator.next();
    }
}
