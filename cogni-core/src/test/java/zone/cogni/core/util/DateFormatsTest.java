package zone.cogni.core.util;

import org.junit.jupiter.api.Test;

import java.util.Date;

class DateFormatsTest {

  @Test
  public void xsd_datetime_formats() {
    String[] datetimes = {
            "2001-10-26T21:32:52",
            "2001-10-26T21:32:52+02:00",
            "2001-10-26T19:32:52+00:00",
            "2001-10-26T19:32:52Z",
            "-2001-10-26T21:32:52",
            "2001-10-26T21:32:52.12679"
    };

    for (String datetime : datetimes) {
      Date date = DateFormats.parseXsdDateTimeFormat(datetime);
      String backToString = DateFormats.formatXsdDateTimeFormat(date);
//      System.out.println("origin       = " + datetime);
//      System.out.println("date         = " + date);
//      System.out.println("date.getTime = " + date.getTime());
//      System.out.println("backToString = " + backToString);
//      System.out.println("");
//      System.out.println("");
//      System.out.println("");
    }
  }
}
