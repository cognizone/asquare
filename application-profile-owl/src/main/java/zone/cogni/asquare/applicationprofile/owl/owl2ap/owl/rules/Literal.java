package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class Literal extends RdfNode {

  private String language;
  private Reference datatype;

  public Literal() {
  }

  public Literal(org.apache.jena.rdf.model.Literal literal) {
    String datatype = literal.getDatatypeURI();

    this.setValue(literal.getString());
    this.language = literal.getLanguage();
    this.datatype = datatype == null ? null : new Reference(datatype);
  }

  public Literal(String value, Reference datatype) {
    this(value, datatype, null);
  }

  public Literal(String value, String language) {
    this(value, null, language);
  }

  public Literal(String value, Reference datatype, String language) {
    super(value);
    this.datatype = datatype;
    this.language = language;
  }

  @Override
  public Literal copy() {
    return new Literal(getValueCopy(), getDatatype().copy(), language);
  }

  public String getLanguage() {
    return language;
  }

  public Reference getDatatype() {
    return datatype;
  }
}
