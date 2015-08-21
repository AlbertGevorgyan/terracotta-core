/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.util.concurrent;

import com.google.common.collect.Iterators;
import com.tc.text.PrettyPrintable;
import com.tc.text.PrettyPrinter;
import com.tc.util.concurrent.TCConcurrentStore.TCConcurrentStoreCallback;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A concurrent implementation of a MultiMap (one to many mapping) with configurable concurrency level. Basic methods
 * are implemented. Could one day implement all map interfaces.
 * 
 * @author Saravanan Subbiah
 */
public class TCConcurrentMultiMap<K, V> implements PrettyPrintable {

  private final AddCallBack<K, V>            addCallback    = new AddCallBack<>();
  private final AddAllCallBack<K, V>         addAllCallback = new AddAllCallBack<>();
  private final RemoveCallBack<K, V>         removeCallback = new RemoveCallBack<>();

  private final TCConcurrentStore<K, Set<V>> store;

  /**
   * Creates a Multimap with a default initial capacity (16), load factor (0.75) and concurrencyLevel (16).
   */
  public TCConcurrentMultiMap() {
    this.store = new TCConcurrentStore<>();
  }

  /**
   * Creates a Multimap with the specified initial capacity, and with default load factor (0.75) and concurrencyLevel
   * (16).
   * 
   * @param initialCapacity the initial capacity.
   * @throws IllegalArgumentException if the initial capacity of elements is negative.
   */
  public TCConcurrentMultiMap(int initialCapacity) {
    this.store = new TCConcurrentStore<>(initialCapacity);
  }

  /**
   * Creates a Multimap with the specified initial capacity and load factor and with the default concurrencyLevel (16).
   * 
   * @param initialCapacity the initial capacity.
   * @param loadFactor the load factor threshold, used to control resizing.
   * @throws IllegalArgumentException if the initial capacity of elements is negative or the load factor is non-positive
   */
  public TCConcurrentMultiMap(int initialCapacity, float loadFactor) {
    this.store = new TCConcurrentStore<>(initialCapacity, loadFactor);
  }

  /**
   * Creates a Multimap with the specified initial capacity, load factor and concurrency level.
   * 
   * @param initialCapacity the initial capacity.
   * @param loadFactor the load factor threshold, used to control resizing.
   * @param concurrencyLevel the estimated number of concurrently updating threads.
   * @throws IllegalArgumentException if the initial capacity is negative or the load factor or concurrencyLevel are
   *         non-positive.
   */
  public TCConcurrentMultiMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    this.store = new TCConcurrentStore<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  /**
   * Adds a mapping of key to value to the Multimap. If there already exists a mapping for the key, then the value is
   * added to the set of values mapped to that key. If there already exists a mapping for the key to the value, then the
   * Multimap is not mutated.
   * 
   * @return true, if this is the first mapping for key in this Multimap at this point in time, else false
   * @throws NullPointerException if key or value is null
   */
  public boolean add(K key, V value) {
    checkNotNull(key, "Key is null");
    checkNotNull(value, "Value is null");
    return (Boolean) this.store.executeUnderWriteLock(key, value, this.addCallback);
  }

  /**
   * Adds all mapping of key to Set of values to the Multimap. If there already exists a mapping for the key, then the
   * value is added to the set of values mapped to that key.
   * 
   * @return true, if there exists no mapping for this key before this call.
   * @throws NullPointerException if key or value is null
   */
  public boolean addAll(K key, Set<V> values) {
    checkNotNull(key, "Key is null");
    return (Boolean) this.store.executeUnderWriteLock(key, values, this.addAllCallback);
  }

  /**
   * Removes the mapping of key to value if it exists in the Multimap.
   * 
   * @returns true if the mapping existed and was successfully removed, false if the mapping didn't exist.
   * @throws NullPointerException if key or value is null
   */
  public boolean remove(K key, V value) {
    checkNotNull(key, "Key is null");
    checkNotNull(value, "Value is null");
    return (Boolean)this.store.executeUnderWriteLock(key, value, this.removeCallback);
  }

  /**
   * Removes all the mapping for the key and returns as a List. If there are no mapping present for the key, returns an
   * empty list.
   * 
   * @return list of mappings for key
   * @throws NullPointerException if key is null
   */
  public Set<V> removeAll(K key) {
    checkNotNull(key, "Key is null");
    final Set<V> set = this.store.remove(key);
    if (set == null) { return Collections.emptySet(); }
    return set;
  }

  /**
   * Returns all the mapping for the key as an immutable List. If there are no mapping present for the key, returns an
   * empty list. Note that even though the returned list is immutable, the list is backed by the mappings in the
   * Multimap, so iterating the returned list while there are concurrent operations for the same key will produce
   * undetermined results.
   * 
   * @return list of mappings for key
   * @throws NullPointerException if key is null
   */
  public Set<V> get(K key) {
    checkNotNull(key, "Key is null");
    final Set<V> set = this.store.get(key);
    if (set == null) { return Collections.emptySet(); }
    return Collections.unmodifiableSet(set);
  }

  /**
   * Checks the presence of some mapping for the key.
   * 
   * @return true is a mapping exists, false otherwise
   * @throws NullPointerException if key is null
   */
  public boolean containsKey(K key) {
    checkNotNull(key, "Key is null");
    final Set<V> set = this.store.get(key);
    if (set == null) { return false; }
    return true;
  }

  /**
   * Returns the number of keys present across all segments. This method is fully locked and hence costly to call.
   * 
   * @return size
   */
  public int size() {
    return store.size();
  }

  private static class AddCallBack<K, V> implements TCConcurrentStoreCallback<K, Set<V>> {
    // Called under segment lock
    @SuppressWarnings("unchecked")
    @Override
    public Object callback(K key, Object value, Map<K, Set<V>> segment) {
      Set<V> set = segment.get(key);
      if (set == null) {
        segment.put(key, singleton((V)value));
        return true;
      } else if (set instanceof SingletonSet && !set.contains(value)) {
        set = new HashSet<>(set);
        segment.put(key, set);
      }
      set.add((V) value);
      return false;
    }
  }

  private static final class AddAllCallBack<K, V> implements TCConcurrentStoreCallback<K, Set<V>> {
    // Called under segment lock
    @Override
    public Object callback(K key, Object values, Map<K, Set<V>> segment) {
      boolean newEntry = false;
      Set<V> set = segment.get(key);
      if (set == null) {
        set = new HashSet<>();
        segment.put(key, set);
        newEntry = true;
      } else if (set instanceof SingletonSet) {
        set = new HashSet<>(set);
        segment.put(key, set);
      }
      @SuppressWarnings("unchecked")
      Set<V> values2Add = (Set<V>) values;
      set.addAll(values2Add);
      return newEntry;
    }
  }

  private static final class RemoveCallBack<K, V> implements TCConcurrentStoreCallback<K, Set<V>> {
    // Called under segment lock
    @Override
    public Object callback(K key, Object value, Map<K, Set<V>> segment) {
      final Set<V> set = segment.get(key);
      if (set == null) { return false; }
      if (set instanceof SingletonSet && set.contains(value)) {
        segment.remove(key);
        return true;
      }
      final boolean removed = set.remove(value);
      if (set.isEmpty()) {
        segment.remove(key);
      } else if (removed && set.size() == 1) {
        segment.put(key, singleton(set.iterator().next()));
      }
      return removed;
    }
  }

  @Override
  public PrettyPrinter prettyPrint(PrettyPrinter out) {
    out.visit(this.store).flush();
    return out;
  }

  private static <T> Set<T> singleton(T t) {
    return new SingletonSet<>(t);
  }

  private static class SingletonSet<V> extends AbstractSet<V> {
    private final V value;

    private SingletonSet(V value) {
      this.value = value;
    }

    @Override
    public boolean add(V v) {
      if (v.equals(value)) {
        return false;
      } else {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public Iterator<V> iterator() {
      return Iterators.singletonIterator(value);
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean contains(Object o) {
      return value.equals(o);
    }
  }

}
