package zone.cogni.asquare.access.simplerdf;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.Objects;

public class SparqlFragment {

  public static String getCoreTriple(ApplicationProfile.Attribute attribute) {
    // todo take inverse attributes(e.g. skos:narrower)
    return " ?instance " +
           " <" + attribute.getUri() + "> " +
           " ?" + attribute.getAttributeId() + " ";
  }

  public static String getCoreTriple(TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    // todo take inverse attributes(e.g. skos:narrower)
    return " <" + typedResource.getResource().getURI() + "> " +
           " <" + attribute.getUri() + "> " +
           " ?" + attribute.getAttributeId() + " ";
  }

  /**
   * Only here to make things compile,
   */
  @Deprecated
  public static String getAttributeVar(ApplicationProfile.Attribute attribute) {
    return " ?" + attribute.getAttributeId() + " ";
  }

  private TypedResource typedResource;
  private ApplicationProfile.Attribute attribute;

  private String construct = "";
  private String filter = "";


  public SparqlFragment() {
  }

  public SparqlFragment(String construct, String filter) {
    Preconditions.checkNotNull(construct);
    Preconditions.checkNotNull(filter);

    this.construct = construct;
    this.filter = filter;
  }

  public SparqlFragment(TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    this.typedResource = typedResource;
    this.attribute = attribute;

    Objects.requireNonNull(typedResource);
    Objects.requireNonNull(attribute);
  }

  public TypedResource getTypedResource() {
    return typedResource;
  }

  public ApplicationProfile.Attribute getAttribute() {
    return attribute;
  }

  private boolean isEmpty() {
    return StringUtils.isBlank(construct) && StringUtils.isBlank(filter);
  }

  public String getConstruct() {
    return construct;
  }

  public void setConstruct(String construct) {
    this.construct = construct;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter;
  }
}
