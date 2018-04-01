package ru.ifmo.rain.borisov.arrayset;

import java.util.*;

public class ArraySet<E extends Comparable<E>> extends AbstractSet<E> implements SortedSet<E> {
    private Comparator<? super E> comparator;
    private final List<E> arr;

    public ArraySet() {
        comparator = null;
        arr = new ArrayList<>();
    }

    public ArraySet(Collection<E> collection, Comparator<? super E> comparator) {
        this.comparator = comparator;
        TreeSet<E> tree = new TreeSet<>(comparator);
        tree.addAll(collection);
        arr = new ArrayList<>(tree);
    }

    private ArraySet(List<E> collection, Comparator<? super E> comparator) {
        this.arr = collection;
        this.comparator = comparator;
    }

    public ArraySet(Collection<E> c) {
        this(c, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(arr, (E) o, comparator) >= 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            Iterator<E> it = arr.iterator();

            public boolean hasNext() {
                return it.hasNext();
            }

            public E next() {
                return it.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public int size() {
        return arr.size();
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return tailSet(fromElement).headSet(toElement);
    }

    private int thImpl(E toElement) {
        int index = Collections.binarySearch(arr, toElement, comparator);
        if (index < 0) {
            index = -index - 1;
        }
        return index;
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return new ArraySet<E>(arr.subList(0, thImpl(toElement)), comparator);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return new ArraySet<E>(arr.subList(thImpl(fromElement), arr.size()), comparator);
    }

    private void flImpl() {
        if (isEmpty()) {
            throw new NoSuchElementException("list is empty");
        }
    }

    @Override
    public E first() {
        flImpl();
        return arr.get(0);
    }

    @Override
    public E last() {
        flImpl();
        return arr.get(size() - 1);
    }
}
