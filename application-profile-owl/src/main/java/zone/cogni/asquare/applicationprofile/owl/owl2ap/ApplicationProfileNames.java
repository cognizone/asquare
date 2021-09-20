package zone.cogni.asquare.applicationprofile.owl.owl2ap;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;

import java.util.Objects;

public class ApplicationProfileNames {

  private final PrefixCcService prefixCcService;
  private final Supplier<String> uriSupplier;

  public ApplicationProfileNames(PrefixCcService prefixCcService, ApplicationProfileDef applicationProfileDef) {
    this(prefixCcService, applicationProfileDef::getUri);
  }

  public ApplicationProfileNames(PrefixCcService prefixCcService, Supplier<String> uriSupplier) {
    this.prefixCcService = prefixCcService;
    this.uriSupplier = uriSupplier;
  }

  public String getClassId(Resource classUri) {
    Preconditions.checkState(uriSupplier.get() != null,
                             "Application profile uri is not set.");
    Preconditions.checkState(classUri.isURIResource(),
                             "Only supported for URI resource.");

    boolean isPartOfOntology = Objects.equals(stripUri(uriSupplier.get()),
                                              stripUri(classUri.getNameSpace()));

    String prefix = isPartOfOntology ? "" : prefixCcService.getPrefix(classUri.getNameSpace());
    return StringUtils.capitalize(prefix) + classUri.getLocalName();
  }

  public String getPropertyId(Resource propertyUri) {
    Preconditions.checkState(propertyUri.isURIResource(), "Only supported for URI resource.");
    boolean isPartOfOntology = Objects.equals(stripUri(uriSupplier.get()),
                                              stripUri(propertyUri.getNameSpace()));

    String prefix = isPartOfOntology ? "" : prefixCcService.getPrefix(propertyUri.getNameSpace());
    return prefix + StringUtils.capitalize(propertyUri.getLocalName());
  }

  private String stripUri(String uri) {
    return uri.endsWith("#") || uri.endsWith("/") ? uri.substring(0, uri.length() - 1)
                                                  : uri;
  }
}
