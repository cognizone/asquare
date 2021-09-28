package zone.cogni.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class MapUtils {

  public static <T, U> Map<T, U> addOrRemove(Map<T, U> map, T key, U value) {
    if (value == null) map.remove(key);
    else map.put(key, value);
    return map;
  }

  public static <T, U> void copyMap(Map<T, Set<U>> map, Map<T, Set<U>> copy) {
    map.entrySet().forEach(entry -> copy.put(entry.getKey(), new HashSet<>(entry.getValue())));
  }

  public static <U, T> boolean addValueToMappedSet(Map<T, Set<U>> map, T key, U value) {
    Set<U> set = map.get(key);
    if (set == null) {
      map.put(key, set = new HashSet<>());
    }
    return set.add(value);
  }

  public static <U, T> boolean addValuesToMappedSet(Map<T, Set<U>> map, T key, Collection<? extends U> values) {
    Set<U> set = map.get(key);
    if (set == null) {
      map.put(key, set = new HashSet<>());
    }
    return set.addAll(values);
  }

  public static <U, T> boolean addValueToMappedList(Map<T, List<U>> map, T key, U value) {
    List<U> set = map.get(key);
    if (set == null) {
      map.put(key, set = new ArrayList<U>());
    }
    return set.add(value);
  }

  public static <U, T> boolean addValuesToMappedList(Map<T, List<U>> map, T key, Collection<? extends U> values) {
    List<U> set = map.get(key);
    if (set == null) {
      map.put(key, set = new ArrayList<U>());
    }
    return set.addAll(values);
  }

  public static <K, U, T> U addEntryToMappedMap(Map<T, Map<K, U>> map, T key, K entryKey, U entryValue) {
    Map<K, U> set = map.get(key);
    if (set == null) {
      map.put(key, set = new HashMap<K, U>());
    }
    return set.put(entryKey, entryValue);
  }
}
