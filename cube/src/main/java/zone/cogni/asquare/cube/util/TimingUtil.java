package zone.cogni.asquare.cube.util;

import org.apache.commons.lang3.StringUtils;

public class TimingUtil {

  private TimingUtil() {
  }

  public static String millisSinceStart(long startNanos, long precision) {
    return nanoToMillis(System.nanoTime() - startNanos, precision);
  }

  public static String nanoToMillis(long nanos, long precision) {
    long actualPrecision = precision > 3 ? 3 : precision < 0 ? 0 : precision;

    long millis = nanos / 1_000_000;
    if (actualPrecision == 0) return String.valueOf(millis);

    long microseconds = (nanos / 1000) % 1000;
    if (actualPrecision == 3) {
      return millis + "." + StringUtils.leftPad(String.valueOf(microseconds), 3, '0');
    }

    if (actualPrecision == 1) {
      long fraction = Math.round((microseconds / 100.0));
      if (fraction == 10) return (millis + 1) + ".0";
      return millis + "." + fraction;
    }

    // actualPrecision == 2
    long fraction = Math.round((microseconds / 10.0));
    return millis + "." + StringUtils.leftPad(String.valueOf(fraction), 2, '0');
  }

}
