package zone.cogni.libs.core.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  public void fixed_date_as_xsd_datetime_format() {
    // given
    LocalDateTime localDateTime = LocalDateTime.of(2015, Month.AUGUST, 20, 6, 30);
    ZoneId cet = ZoneId.of("Europe/Paris");
    ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, cet);

    Date date = Date.from(zonedDateTime.toInstant());

    // when
    String oldFormatter = DateFormats.formatXsdDateTimeFormatOld(date);
    String newFormatter = DateFormats.formatXsdDateTimeFormat(date);

    String oldBeforePlus = StringUtils.substringBefore(oldFormatter, "+");
    String newBeforePlus = StringUtils.substringBefore(newFormatter, "+");

    String oldAfterPlus = StringUtils.substringAfterLast(oldFormatter, "+");
    String newAfterPlus = StringUtils.substringAfterLast(newFormatter, "+");

    // then
    assertThat(oldBeforePlus).startsWith(newBeforePlus);
    assertEquals(oldAfterPlus, newAfterPlus);
  }

  @Test
  public void now_as_xsd_datetime_format() {
    // given
    Date now = new Date();

    // when
    String oldFormatter = DateFormats.formatXsdDateTimeFormatOld(now);
    String newFormatter = DateFormats.formatXsdDateTimeFormat(now);

    String oldBeforePlus = StringUtils.substringBefore(oldFormatter, "+");
    String newBeforePlus = StringUtils.substringBefore(newFormatter, "+");

    String oldAfterPlus = StringUtils.substringAfterLast(oldFormatter, "+");
    String newAfterPlus = StringUtils.substringAfterLast(newFormatter, "+");

    // then
    assertTrue(oldBeforePlus.startsWith(newBeforePlus));
    assertEquals(oldAfterPlus, newAfterPlus);
  }

}