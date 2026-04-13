package me.sunmc.db.api.orm;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * A wrapper list that only executes its underlying data supplier
 * upon the first access (e.g. size(), get(), or iterator()).
 */
public final class LazyList<E> implements List<E> {

    private final Supplier<List<E>> supplier;
    private List<E> internal;

    public LazyList(@NotNull Supplier<List<E>> supplier) {
        this.supplier = supplier;
    }

    private List<E> getInternal() {
        if (internal == null) {
            internal = supplier.get();
        }
        return internal;
    }

    @Override
    public int size() {
        return getInternal().size();
    }

    @Override
    public boolean isEmpty() {
        return getInternal().isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return getInternal().contains(o);
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return getInternal().iterator();
    }


    @Override
    public Object @NotNull [] toArray() {
        return getInternal().toArray();
    }


    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] a) {
        return getInternal().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return getInternal().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return getInternal().remove(o);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return new HashSet<>(getInternal()).containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        return getInternal().addAll(c);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        return getInternal().addAll(index, c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return getInternal().removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return getInternal().retainAll(c);
    }

    @Override
    public void clear() {
        getInternal().clear();
    }

    @Override
    public E get(int index) {
        return getInternal().get(index);
    }

    @Override
    public E set(int index, E element) {
        return getInternal().set(index, element);
    }

    @Override
    public void add(int index, E element) {
        getInternal().add(index, element);
    }

    @Override
    public E remove(int index) {
        return getInternal().remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return getInternal().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return getInternal().lastIndexOf(o);
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return getInternal().listIterator();
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return getInternal().listIterator(index);
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return getInternal().subList(fromIndex, toIndex);
    }
}