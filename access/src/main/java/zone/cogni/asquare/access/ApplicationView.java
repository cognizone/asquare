package zone.cogni.asquare.access;

import com.google.common.base.Preconditions;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSupplier;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ApplicationView {

  private AccessService accessService;
  private ApplicationProfile applicationProfile;

  public ApplicationView() {
  }

  public ApplicationView(AccessService accessService, ApplicationProfile applicationProfile) {
    Preconditions.checkNotNull(accessService, "accessService is null");
    Preconditions.checkNotNull(applicationProfile, "applicationProfile is null");

    this.accessService = accessService;
    this.applicationProfile = applicationProfile;
  }

  public AccessService getRepository() {
    return accessService;
  }

  public void setRepository(AccessService accessService) {
    this.accessService = accessService;
  }

  public ApplicationProfile getApplicationProfile() {
    return applicationProfile;
  }

  public void setApplicationProfile(ApplicationProfileSupplier applicationProfileSupplier) {
    this.applicationProfile = applicationProfileSupplier.get();
  }

  public void setApplicationProfile(ApplicationProfile applicationProfile) {
    this.applicationProfile = applicationProfile;
  }

  public RdfStoreService getRdfStoreService() {
    return accessService.getRdfStoreService();
  }

  public void save(DeltaResource... deltaResources) {
    save(Arrays.asList(deltaResources));
  }

  /**
   * TODO original flow => some of it is probably still valid
   * 1. process json
   * 2. create updatableResources
   * 3. collect all to be deleted statements and new statements
   * 4. execute inserts
   * 5. execute deletes
   * 6. execute validation
   * 7. if validation fails rollback
   */
  public void save(List<DeltaResource> deltaResources) {
    // todo throw if cache not init
    Preconditions.checkState(accessService != null, "FATAL: repository missing.");
//    Preconditions.checkState(getRdfStoreService() != null, "Save only works for rdf databases.");

    setCreatedAndModified(deltaResources);

    accessService.save(deltaResources);
  }

  private void setCreatedAndModified(List<DeltaResource> typedResources) {
    Literal now = getNow();

    typedResources.forEach(typedResource -> {

      if (typedResource.isDeleted()) return;
      if (!typedResource.getDelta().hasChanges()) return;

      String modifiedAttribute = "modified";
      if (typedResource.getType().hasAttribute(modifiedAttribute)) {
        typedResource.setValue(modifiedAttribute, now);
      }

      String createdAttribute = "created";
      if (typedResource.getType().hasAttribute(createdAttribute)
          && !typedResource.hasValues(createdAttribute)) {
        typedResource.setValue(createdAttribute, now);

      }
    });
  }

  private Literal getNow() {
    return ResourceFactory.createTypedLiteral(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                                              XSDDatatype.XSDdateTime);
  }

  // moving this to ApplicationView ??!
  public List<TypedResource> findAll(Supplier<ApplicationProfile.Type> typeSupplier,
                                     AttributeMatcher... attributeMatchers) {
    return accessService.findAll(typeSupplier, attributeMatchers);
  }

  public TypedResource find(Supplier<ApplicationProfile.Type> typeSupplier, String resourceUri) {
    return accessService.getTypedResource(typeSupplier, () -> resourceUri);
  }

  public DeltaResource createNewDeltaResource(MutableResource newResource) {
    return DeltaResource.createNew(this, newResource);
  }

  public DeltaResource findDeltaResource(ApplicationProfile.Type resourceType, String resourceUri) {
    return DeltaResource.findInDatabase(this, resourceType, resourceUri);
  }

  public DeltaResource getDeltaResource(Supplier<ApplicationProfile.Type> typeSupplier, String resourceUri) {
    return DeltaResource.fromDatabase(this, (type, provisionalUri) -> provisionalUri).apply(typeSupplier.get(), resourceUri);
  }

  public DeltaResource setDeltaResource(MutableResource newResource) {
    return DeltaResource.editDatabaseResource(this, newResource);
  }

  public static class AttributeMatcher {

    public static Supplier<RDFNode> resource(String uri) {
      return () -> ResourceFactory.createResource(uri);
    }

    public static AttributeMatcher match(String attribute, Supplier<RDFNode> rdfValue) {
      return new AttributeMatcher(attribute, a -> rdfValue.get());
    }

    public static AttributeMatcher match(String attribute, Function<Attribute, RDFNode> rdfValue) {
      return new AttributeMatcher(attribute, rdfValue);
    }

    private final String attribute;
    private final Function<Attribute, RDFNode> rdf;

    public AttributeMatcher(String attribute, Function<Attribute, RDFNode> rdf) {
      this.attribute = attribute;
      this.rdf = rdf;
    }

    public String getAttribute() {
      return attribute;
    }

    public RDFNode getRdf(Attribute attribute) {
      return rdf.apply(attribute);
    }
  }
}
