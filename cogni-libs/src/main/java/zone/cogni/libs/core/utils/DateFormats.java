package zone.cogni.libs.core.utils;

import org.joda.time.format.ISODateTimeFormat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.DatatypeConverter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 */
public class DateFormats {
  public static final String XML_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  private static final DateFormatsThreadLocal FORMATS = new DateFormatsThreadLocal();

  private static SimpleDateFormat getDateFormat(Format format) {
    Map<String, SimpleDateFormat> instanceMap = FORMATS.get();
    String template = format.getDateFormatTemplate();
    if (instanceMap.containsKey(template)) {
      return instanceMap.get(template);
    }
    SimpleDateFormat dateFormat = new SimpleDateFormat(template);
    instanceMap.put(template, dateFormat);
    return dateFormat;
  }

  /**
   * Parses a date with given format to a Date object.<br>
   *
   * @param value  The value to parse to a date.
   * @param format The format to use.
   * @return The date.
   */
  @Nonnull
  public static Date parseMandatory(@Nonnull String value, @Nonnull Format format) {
    Date date = parse(value, format);
    Objects.requireNonNull(date, () -> "The value '" + value + "' cannot be parsed with the format '" + format.dateFormatTemplate + "'.");
    return date;
  }

  /**
   * Parses a date with given format to a Date object. If the parsing fails null is returned.<br>
   * Is null is passed as String to parse, null will be returned.
   *
   * @param value  The value to parse to a date.
   * @param format The format to use.
   * @return The date or null if the string cannot be parsed.
   */
  @Nullable
  public static Date parse(String value, @Nonnull Format format) {
    if (null == value) return null;

    if (format == Format.FULLXMLDATETIME) {
      try {
        return ISODateTimeFormat.dateTime().parseDateTime(value).toDate();
      }
      catch (IllegalArgumentException ignored) {
        return null;
      }
    }

    try {
      return getDateFormat(format).parse(value);
    }
    catch (ParseException ignored) {
      return null;
    }
  }

  /**
   * Parses a date with format 'dd-MM-yyyy' to a Date object. If the parsing fails null is returned.<br>
   * Is null is passed as String to parse, null will be returned.
   *
   * @param dayFirstDate The value to parse to a date.
   * @return The date or null if the string cannot be parsed.
   */
  public static Date parseDayFirstDate(String dayFirstDate) {
    return parse(dayFirstDate, Format.DAYFIRSTDATE);
  }

  /**
   * Parses an XML date to a Date object. If the parsing fails null is returned.<br>
   * Is null is passed as String to parse, null will be returned.
   *
   * @param xmlDate The value to parse to a date.
   * @return The date or null if the string cannot be parsed.
   */
  public static Date parseXmlDateTime(String xmlDate) {
    return parse(xmlDate, Format.XMLDATETIME);
  }

  /**
   * Parses a simple date String (yyyy-MM-dd) to a Date object. If the parsing fails null is returned.
   * Is null is passed as String to parse, null will be returned.
   *
   * @param dateString The value to parse to a date.
   * @return The date or null if the string cannot be parsed.
   */
  public static Date parseDate(String dateString) {
    return parse(dateString, Format.DATE);
  }

  /**
   * Parses a simple date String (yyyy-MM-dd) to a Date object. If the parsing fails null is returned.
   * For this format a string of 10 long is needed.
   * If the passed string is shorter, null is returned.
   * If the passed string is longer, it will be trimmed to length 10.
   * Is null is passed as String to parse, null will be returned.
   *
   * @param dateString The value to parse to a date.
   * @return The date or null if the string cannot be parsed.
   */
  @Nullable
  public static Date parseTrimmedDate(String dateString) {
    if (null == dateString) return null;
    if (dateString.length() < 10) return null;
    if (dateString.length() > 10) dateString = dateString.substring(0, 10);
    return parse(dateString, Format.DATE);
  }

  /**
   * Formats a Date to given date(time) representation.
   * If the passed date is null, an empty String will be returned.
   *
   * @param format The format to use.
   * @param date   The date to convert.
   * @return The formatted date.
   */
  public static String format(Date date, Format format) {
    if (date == null) return "";
    if (format == Format.FULLXMLDATETIME) {
      return ISODateTimeFormat.dateTime().print(date.getTime());
    }
    return getDateFormat(format).format(date);
  }

  /**
   * Formats a Date to the XML dateTime representation (yyyy-MM-dd'T'HH:mm:ss).
   * If the passed date is null, an empty String will be returned.
   *
   * @param date The date to convert.
   * @return The formatted date.
   */
  public static String formatXmlDateTime(Date date) {
    return format(date, Format.XMLDATETIME);
  }

  public static String formatFullXmlDateTime(Date date) {
    return format(date, Format.FULLXMLDATETIME);
  }

  /**
   * Formats a Date to a simple date-only format (yyyy-MM-dd).
   * If the passed date is null, an empty String will be returned.
   *
   * @param date The date to convert.
   * @return The formatted date.
   */
  public static String formatDate(Date date) {
    return format(date, Format.DATE);
  }

  /**
   * Formats a Date to a full dateTime format (yyyyMMdd-HHmmss).
   * If the passed date is null, an empty String will be returned.
   *
   * @param date The date to convert.
   * @return The formatted date.
   */
  public static String formatFullDateTime(Date date) {
    return format(date, Format.DATETIME);
  }

  /**
   * Formats a Date to a full dateTime format (yyyyMMdd-HHmmss[X]SSS).
   * If the passed date is null, an empty String will be returned.
   *
   * @param beforeMsChar The character to place before the milliseconds ([X]), can be an empty string.
   * @param date         The date to convert.
   * @return The formatted date.
   */
  public static String formatFullDateTimeMs(Date date, String beforeMsChar) {
    if (null == date) return "";
    if (null == beforeMsChar) beforeMsChar = "";
    long ms = date.getTime() % 1000;
    if (ms < 100) {
      beforeMsChar += "0";
      if (ms < 10) beforeMsChar += "0";
    }
    return format(date, Format.DATETIME) + beforeMsChar + ms;
  }

  /**
   * Formats a Date to an ureadable full dateTime format (yyyyMMddHHmmss).
   * If the passed date is null, an empty String will be returned.
   *
   * @param date The date to convert.
   * @return The formatted date.
   */
  public static String formatUnreadableFullDateTime(Date date) {
    return format(date, Format.UNREADABLEDATETIME);
  }

  /**
   * Formats a Date to a readable full dateTime format (yyyy-MM-dd HH:mm:ss).
   * If the passed date is null, an empty String will be returned.
   *
   * @param date The date to convert.
   * @return The formatted date.
   */
  public static String formatReadableFullDateTime(Date date) {
    return format(date, Format.READABLEDATETIME);
  }

  public static String formatXsdDateTimeFormat(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    return DatatypeConverter.printDateTime(calendar);
  }

  public static Date parseXsdDateTimeFormat(String date) {
    try {
      Calendar calendar = DatatypeConverter.parseDateTime(date);
      return calendar.getTime();
    }
    catch (Exception ignored) {
      return null;
    }
  }

  private DateFormats() {
  }

  /**
   * XMLDATETIME: yyyy-MM-dd'T'HH:mm:ss
   * DATE: yyyy-MM-dd
   * DATETIME: yyyyMMdd-HHmmss
   * READABLEDATETIME: yyyy-MM-dd' 'HH:mm:ss
   * DAYFIRTFDATE: dd-MM-yyyy
   */
  public enum Format {
    /**
     * Format yyyy-MM-dd'T'HH:mm:ss
     */
    XMLDATETIME("yyyy-MM-dd'T'HH:mm:ss", true, true),
    /**
     * Format yyyy-MM-dd'T'HH:mm:ss.SSSZ (with jodaTime)
     */
    FULLXMLDATETIME(XML_DATE_TIME_FORMAT, true, true),
    /**
     * Format yyyy-MM-dd'T'HH:mm:ss.SSSZ (with java SimpleDateFormat)
     */
    JAVAFULLXMLDATETIME(XML_DATE_TIME_FORMAT, true, true),

    /**
     * Format yyyyMMdd-HHmmss.SSS
     */
    DATETIMEMS("yyyyMMdd-HHmmss.SSS", true, true),
    /**
     * Format: yyyy-MM-dd
     */
    DATE("yyyy-MM-dd", true, false),
    /**
     * Format: yyyyMMdd
     */
    SHORTDATE("yyyyMMdd", true, false),
    /**
     * Format: yyyyMMdd-HHmmss
     */
    DATETIME("yyyyMMdd-HHmmss", true, true),
    /**
     * Format: yyyyMMddHHmmss
     */
    UNREADABLEDATETIME("yyyyMMddHHmmss", true, true),
    /**
     * Format: yyyyMMdd-HHmmss
     */
    DATETIME_AS_FOLDERNAME("yyyyMMdd_HHmmss", true, true),
    /**
     * Format: yyyy-MM-dd' 'HH:mm:ss
     */
    READABLEDATETIME("yyyy-MM-dd' 'HH:mm:ss", true, true),
    /**
     * Format: dd-MM-yyyy
     */
    DAYFIRSTDATE("dd-MM-yyyy", true, false),
    /**
     * Format: dd-MM-yyyy HH:mm
     */
    DMY_HM("dd-MM-yyyy HH:mm", true, true),
    /**
     * Format: HH:mm
     */
    SHORT_TIME("HH:mm", false, true),
    /**
     * Format: HH:mm:ss
     */
    TIME("HH:mm:ss", false, true);

    private final String dateFormatTemplate;
    private final boolean showsDate;
    private final boolean showsTime;

    Format(String dateFormatTemplate, boolean showsDate, boolean showsTime) {
      this.dateFormatTemplate = dateFormatTemplate;
      this.showsDate = showsDate;
      this.showsTime = showsTime;
    }

    public String getDateFormatTemplate() {
      return dateFormatTemplate;
    }

    public boolean isShowsDate() {
      return showsDate;
    }

    public boolean isShowsTime() {
      return showsTime;
    }
  }

  private static class DateFormatsThreadLocal extends ThreadLocal<Map<String, SimpleDateFormat>> {
    @Override
    protected Map<String, SimpleDateFormat> initialValue() {
      return new HashMap<>();
    }
  }
}
