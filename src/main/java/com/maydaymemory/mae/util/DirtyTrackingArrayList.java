// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.util.DirtyTrackingArrayList
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;

import javax.annotation.Nonnull;

public abstract class DirtyTrackingArrayList<T> implements List<T>, RandomAccess {

    protected final ArrayList<T> innerList;
    private boolean dirty = false;

    public DirtyTrackingArrayList(@Nonnull ArrayList<T> initialList) {
        this.innerList = initialList;
    }

    protected abstract boolean onRefresh();

    public void refresh() {
        if (this.dirty) {
            this.dirty = !this.onRefresh();
        }

    }

    public boolean isDirty() {
        return this.dirty;
    }

    public int size() {
        return this.innerList.size();
    }

    public boolean isEmpty() {
        return this.innerList.isEmpty();
    }

    public boolean contains(Object o) {
        return this.innerList.contains(o);
    }

    @Nonnull
    public Iterator<T> iterator() {
        return this.innerList.iterator();
    }

    @Nonnull
    public Object[] toArray() {
        return this.innerList.toArray();
    }

    @Nonnull
    public <U> U[] toArray(@Nonnull U[] array) {
        return (U[]) this.innerList.toArray(array);
    }

    public boolean add(T item) {
        this.dirty = true;
        return this.innerList.add(item);
    }

    public boolean remove(Object o) {
        boolean removed = this.innerList.remove(o);
        this.dirty = this.dirty || removed;
        return removed;
    }

    public boolean containsAll(@Nonnull Collection<?> collection) {
        return this.innerList.containsAll(collection);
    }

    public boolean addAll(@Nonnull Collection<? extends T> collection) {
        boolean added = this.innerList.addAll(collection);
        this.dirty = this.dirty || added;
        return added;
    }

    public boolean addAll(int i, @Nonnull Collection<? extends T> collection) {
        boolean added = this.innerList.addAll(i, collection);
        this.dirty = this.dirty || added;
        return added;
    }

    public boolean removeAll(@Nonnull Collection<?> collection) {
        boolean removed = this.innerList.removeAll(collection);
        this.dirty = this.dirty || removed;
        return removed;
    }

    public boolean retainAll(@Nonnull Collection<?> collection) {
        boolean retained = this.innerList.retainAll(collection);
        this.dirty = this.dirty || retained;
        return retained;
    }

    public void clear() {
        this.innerList.clear();
        this.dirty = false;
    }

    public T get(int i) {
        return (T) this.innerList.get(i);
    }

    public T set(int i, T keyframe) {
        T oldOne = (T) this.innerList.set(i, keyframe);
        if (!Objects.equals(oldOne, keyframe)) {
            this.dirty = true;
        }

        return oldOne;
    }

    public void add(int i, T item) {
        this.dirty = true;
        this.innerList.add(i, item);
    }

    public T remove(int i) {
        this.dirty = true;
        return (T) this.innerList.remove(i);
    }

    public int indexOf(Object o) {
        return this.innerList.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return this.innerList.lastIndexOf(o);
    }

    @Nonnull
    public ListIterator<T> listIterator() {
        return this.innerList.listIterator();
    }

    @Nonnull
    public ListIterator<T> listIterator(int i) {
        return this.innerList.listIterator(i);
    }

    @Nonnull
    public List<T> subList(int i, int i1) {
        return this.innerList.subList(i, i1);
    }
}
