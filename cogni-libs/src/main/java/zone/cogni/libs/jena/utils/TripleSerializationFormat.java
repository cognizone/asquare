package zone.cogni.libs.jena.utils;

public enum TripleSerializationFormat {

  turtle("TTL"),
  rdfXml("RDF/XML"),
  n3("N3");

  private final String jenaLanguage;

  TripleSerializationFormat(String jenaLanguage) {
    this.jenaLanguage = jenaLanguage;
  }

  public String getJenaLanguage() {
    return jenaLanguage;
  }
}
