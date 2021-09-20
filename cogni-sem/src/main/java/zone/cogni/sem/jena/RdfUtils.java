package zone.cogni.sem.jena;

import java.util.Date;
import java.util.Objects;

import static zone.cogni.core.util.DataUtils.tryParse;
import static zone.cogni.core.util.DateFormats.formatXsdDateTimeFormat;

public abstract class RdfUtils {
  public static String parseDateAsString(String dateAsString) {
    return formatXsdDateTimeFormat(parseDate(dateAsString));
  }

  public static Date parseDate(String dateAsString) {
    Date date = tryParse(dateAsString);
    Objects.requireNonNull(date, () -> "Illegal date format found '" + dateAsString + "'");
    return date;
  }

  public static String format(Date date) {
    return formatXsdDateTimeFormat(date);
  }
}
