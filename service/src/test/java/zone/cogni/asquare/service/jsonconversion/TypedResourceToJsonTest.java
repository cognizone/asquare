package zone.cogni.asquare.service.jsonconversion;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TypedResourceToJsonTest {

  private TypedResourceToJson typedResourceToJson = new TypedResourceToJson(null);

  @Test
  public void workingToDate() {
    String date1 = typedResourceToJson.literalToDate(getDateLiteral("2020-06-20"));
    Assertions.assertEquals(date1, "2020-06-20");
    String date2 = typedResourceToJson.literalToDate(getDateLiteral("1894-06-01"));
    Assertions.assertEquals(date2, "1894-06-01");
  }

  @Test
  public void workingToTime() {
    String time1 = typedResourceToJson.literalToTime(getTimeLiteral("12:31"));
    Assertions.assertEquals(time1, "12:31:00");
    String time2 = typedResourceToJson.literalToTime(getTimeLiteral("12:34:56"));
    Assertions.assertEquals(time2, "12:34:56");
  }

  @Test
  public void wrongDates() {
    Assertions.assertThrows(RuntimeException.class, this::xxInDateShouldFail);
    Assertions.assertThrows(RuntimeException.class, this::wrongDayShouldFail);
    Assertions.assertThrows(RuntimeException.class, this::withTimeShouldFail);
  }

  @Test
  public void wrongTimes() {
    Assertions.assertThrows(RuntimeException.class, () -> wrongTimeShouldFail("12:xx:00"));
    Assertions.assertThrows(RuntimeException.class, () -> wrongTimeShouldFail("T12:34:00"));
    Assertions.assertThrows(RuntimeException.class, () -> wrongTimeShouldFail("12:44:11+02:00"));
    Assertions.assertThrows(RuntimeException.class, () -> wrongTimeShouldFail("12:44:11Z"));
  }

  private void xxInDateShouldFail() {
    typedResourceToJson.literalToDate(getDateLiteral("1894-xx-01"));
  }

  private void wrongDayShouldFail() {
    typedResourceToJson.literalToDate(getDateLiteral("1894-01-35"));
  }

  private void withTimeShouldFail() {
    typedResourceToJson.literalToDate(getDateLiteral("1894-06-01T12:30"));
  }

  private void wrongTimeShouldFail(String time) {
    typedResourceToJson.literalToTime(getTimeLiteral(time));
  }

  private Literal getDateLiteral(String value) {
    return ResourceFactory.createTypedLiteral(value, XSDDatatype.XSDdate);
  }

  private Literal getTimeLiteral(String value) {
    return ResourceFactory.createTypedLiteral(value, XSDDatatype.XSDtime);
  }
}