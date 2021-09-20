package zone.cogni.core.util;

import java.util.Date;

import static java.util.Objects.requireNonNull;

public class DataUtils {

  public static Date tryParse(String dateString) {
    Date date = DateFormats.parseXsdDateTimeFormat(dateString);
    if (date == null) date = DateFormats.parse(dateString, DateFormats.Format.JAVAFULLXMLDATETIME);
    if (date == null) date = DateFormats.parse(dateString, DateFormats.Format.FULLXMLDATETIME);
    if (date == null) date = DateFormats.parse(dateString, DateFormats.Format.XMLDATETIME);
    if (date == null) date = DateFormats.parse(dateString, DateFormats.Format.DATE);
    return date;
  }

  public static String parseDateAsString(String dateAsString) {
    Date parsedDate = tryParse(dateAsString);
    requireNonNull(parsedDate, () -> "Illegal date format found '" + dateAsString + "'");
    return DateFormats.formatXsdDateTimeFormat(parsedDate);
  }
}
