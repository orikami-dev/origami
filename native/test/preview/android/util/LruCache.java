package android.util;
public class LruCache<K,V> { java.util.HashMap<K,V> m=new java.util.HashMap<K,V>(); public LruCache(int n){} public V get(K k){return m.get(k);} public void put(K k,V v){m.put(k,v);} }
