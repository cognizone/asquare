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
  public void wrongDates() {
    Assertions.assertThrows(RuntimeException.class, this::xxInDateShouldFail);
    Assertions.assertThrows(RuntimeException.class, this::wrongDayShouldFail);
    Assertions.assertThrows(RuntimeException.class, this::withTimeShouldFail);
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


  private Literal getDateLiteral(String value) {
    return ResourceFactory.createTypedLiteral(value, XSDDatatype.XSDdate);
  }
}