package zone.cogni.libs.core.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DateFormatsTest {

  @Test
  public void parse_xsd_date_normal_case() {
    // given
    String dateString = "2021-09-21T23:00:42.256+02:00";

    // when
    Date newDate = DateFormats.parseXsdDateTimeFormat(dateString);

    // then
    assertEquals(1632258042256L, newDate.getTime());
  }

  @Test
  public void parse_xsd_date_only_tenths() {
    // given
    String dateString = "2021-09-21T23:00:42.2+02:00";

    // when
    Date newDate = DateFormats.parseXsdDateTimeFormat(dateString);

    // then
    assertEquals(1632258042200L, newDate.getTime());
  }

  @Test
  public void parse_xsd_date_only_seconds() {
    // given
    String dateString = "2021-09-21T23:00:42+02:00";

    // when
    Date newDate = DateFormats.parseXsdDateTimeFormat(dateString);

    // then
    assertEquals(1632258042000L, newDate.getTime());
  }

  @Test
  public void fixed_date_as_xsd_datetime_format_europe_paris() {
    // given
    LocalDateTime localDateTime = LocalDateTime.of(2015, Month.AUGUST, 20, 6, 30);
    ZoneId systemDefaultZone = ZoneId.systemDefault();
    ZoneId cet = ZoneId.of("Europe/Paris");
    TimeZone.setDefault(TimeZone.getTimeZone(cet));

    ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, cet);

    Date date = Date.from(zonedDateTime.toInstant());

    // when
    String oldFormatter = DateFormats.formatXsdDateTimeFormatOld(date);
    String newFormatter = DateFormats.formatXsdDateTimeFormat(date);

    String oldBeforePlus = StringUtils.substringBefore(oldFormatter, "+");
    String newBeforePlus = StringUtils.substringBefore(newFormatter, "+");

    String oldAfterPlus = StringUtils.substringAfterLast(oldFormatter, "+");
    String newAfterPlus = StringUtils.substringAfterLast(newFormatter, "+");

    TimeZone.setDefault(TimeZone.getTimeZone(systemDefaultZone));

    // then
    assertThat(oldBeforePlus).startsWith(newBeforePlus);
    assertEquals("02:00", oldAfterPlus);
    assertEquals("02:00", newAfterPlus);
  }

  @Test
  public void fixed_date_as_xsd_datetime_format_zulu() {
    // given
    LocalDateTime localDateTime = LocalDateTime.of(2015, Month.AUGUST, 20, 6, 30);
    ZoneId systemDefaultZone = ZoneId.systemDefault();
    ZoneId utc = ZoneId.of("UTC");
    TimeZone.setDefault(TimeZone.getTimeZone(utc));

    ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, utc);

    Date date = Date.from(zonedDateTime.toInstant());

    // when
    String oldFormatter = DateFormats.formatXsdDateTimeFormatOld(date);
    String newFormatter = DateFormats.formatXsdDateTimeFormat(date);

    String oldBeforeZ = StringUtils.substringBefore(oldFormatter, "Z");
    String newBeforeZ = StringUtils.substringBefore(newFormatter, "Z");

    TimeZone.setDefault(TimeZone.getTimeZone(systemDefaultZone));

    // then
    assertThat(oldFormatter).endsWith("Z");
    assertThat(newFormatter).endsWith("Z");

    assertThat(oldBeforeZ).startsWith(newBeforeZ);
  }

  @Test
  public void now_as_xsd_datetime_format() {
    // given
    TimeZone.setDefault(TimeZone.getTimeZone("GMT+2"));
    long time = System.currentTimeMillis();
    Date now = new Date(time - time % 10); //ms end with 0, because if  oldBeforePlus = "2024-02-05T15:58:33.280" then newBeforePlus = "2024-02-05T15:58:33.28" (just to "test" this)

    // when
    String oldFormatter = DateFormats.formatXsdDateTimeFormatOld(now);
    String newFormatter = DateFormats.formatXsdDateTimeFormat(now);

    String oldBeforePlus = StringUtils.substringBefore(oldFormatter, "+");
    String newBeforePlus = StringUtils.substringBefore(newFormatter, "+");

    String oldAfterPlus = StringUtils.substringAfterLast(oldFormatter, "+");
    String newAfterPlus = StringUtils.substringAfterLast(newFormatter, "+");

    // then
    assertThat(oldBeforePlus).startsWith(newBeforePlus);
    assertThat(oldAfterPlus).isEqualTo(newAfterPlus);
  }

}