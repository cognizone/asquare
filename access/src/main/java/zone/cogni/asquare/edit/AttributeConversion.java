package zone.cogni.asquare.edit;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;

public class AttributeConversion implements Supplier<RdfValue> {

  @Nonnull
  private final ApplicationProfile.Attribute attribute;

  @Nonnull
  private final Object value;

  public AttributeConversion(@Nonnull ApplicationProfile.Attribute attribute,
                             @Nonnull Object value) {
    this.attribute = attribute;
    this.value = value;
  }

  @Override
  public RdfValue get() {
    if (value instanceof RdfValue) return (RdfValue) value;

    if (value instanceof Literal) return new BasicRdfValue((Literal) value);
    if (value instanceof Resource) return new BasicRdfValue((Resource) value);

    if (value instanceof String) {
      XSDDatatype type = XSDDatatype.XSDstring;

      checkSimpleTypedLiteral(type);
      return new BasicRdfValue(ResourceFactory.createTypedLiteral((String) value, type));
    }

    // TODO we could handle all cases by checking attribute range (if any)

    throw unsupportedConversion();
  }

  private void checkSimpleTypedLiteral(XSDDatatype datatypeInstance) {
    Range range = attribute.getRule(Range.class).getOrElseThrow(this::unsupportedConversion);

    if (!(range.getValue() instanceof Datatype)) throw unsupportedConversion();

    Datatype datatype = (Datatype) range.getValue();
    if (Objects.equals(datatype.getValue(), RDFS.Literal.getURI())) return;

    if (!Objects.equals(datatype.getValue(), datatypeInstance.getURI())) throw unsupportedConversion();
  }

  private RuntimeException unsupportedConversion() {
    return new RuntimeException("Unsupported conversion for attribute '" + attribute.getAttributeId() + "'" +
                                " and type " + value.getClass().getName());
  }
}
