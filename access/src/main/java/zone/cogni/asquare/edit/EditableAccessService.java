package zone.cogni.asquare.edit;

import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.AccessType;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;

import java.util.List;
import java.util.function.Supplier;

/**
 * TODO implement AccessService or ElasticAccessService {@code ->} i think we need 2 implementations?
 * RDFEditableAccesService and ElasticEditableAccessService (ElasticEditableAccessService might embed an RDFEditableAccesService)
 * <p>
 * TODO extract interface
 * TODO add/implement the needed save and edit functionality
 */
@Deprecated
public class EditableAccessService implements AccessService {

  private final AccessService accessService;

  public EditableAccessService(AccessService accessService) {
    this.accessService = accessService;
  }

  @Override
  public AccessType getAccessType() {
    return AccessType.RDF;
  }

  @Override
  public RdfStoreService getRdfStoreService() {
    return accessService.getRdfStoreService();
  }

  @Override
  public List<? extends TypedResource> findAll(ApplicationProfile.Type type) {
    return accessService.findAll(type);
  }

  @Override
  public List<TypedResource> findAll(Supplier<ApplicationProfile.Type> typeSupplier, ApplicationView.AttributeMatcher... attributeMatchers) {
    return accessService.findAll(typeSupplier, attributeMatchers);
  }

  @Override
  public TypedResource getTypedResource() {
    return accessService.getTypedResource();
  }

  @Override
  public TypedResource getTypedResource(ApplicationProfile.Type type, Resource resource) {
    return accessService.getTypedResource(type, resource);
  }

  public UpdatableResource getUpdatableResource(TypedResource newResource) {
    TypedResource previous;
    try {
      previous = getTypedResource(newResource.getType(), newResource.getResource());
    }
    catch (NotFoundException e) {
      previous = new ConstructedResource(newResource.getType(), newResource.getResource().getURI());
    }
    return new UpdatableResource(newResource, previous);
  }


  @Override
  // todo is this ok? just a simple delegation?
  // todo this method might contain/implement logic that takes synchronisation issues into account if any (e.g in case updated data is saved to triple store but elastic is not synced yet)?
  public <T extends RdfValue> List<T> getValues(ApplicationProfile applicationProfile, TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    return accessService.getValues(applicationProfile, typedResource, attribute);
  }

  @Override
  public void save(List<DeltaResource> deltaResources) {
    accessService.save(deltaResources);
  }
}
