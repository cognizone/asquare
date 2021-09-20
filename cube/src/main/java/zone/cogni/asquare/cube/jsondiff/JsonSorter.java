package zone.cogni.asquare.cube.jsondiff;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface JsonSorter {

  /**
   * Id is used for reporting.
   */
  Function<ObjectNode, String> getId();

  /**
   * Comparator is used for sorting.
   */
  BiFunction<ObjectNode, ObjectNode, Boolean> getComparator();

}
