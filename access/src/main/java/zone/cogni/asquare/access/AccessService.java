package zone.cogni.asquare.access;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.List;
import java.util.function.Supplier;

/**
 * We need 3 levels of implementation
 * <p>
 * 1) query
 * <p>
 * Fetches data but might not filter yet, either because of query complexity or to allow validations
 * <p>
 * 2) filter
 * <p>
 * Filters data so it complies to the constraints of the model.
 * Data that was entered (validation light) should always be ok;
 * but data that comes from an external system might have issues here and get filtered out!
 * <p>
 * 3) validation
 * <p>
 * a. light: avoid inserting bad data (work in progress)
 * -- in case data is missing we should not fail
 * -- only validation at single property level
 * <p>
 * b. full: avoid data corruption
 * -- report missing values, cardinalities
 * -- validation across properties might be here too
 */
public interface AccessService {

  AccessType getAccessType();

  RdfStoreService getRdfStoreService();

  // factory method to create TypedResource instance abstracting the actual implementing class
  TypedResource getTypedResource();

  // factory method to create TypedResource instance abstracting the actual implementing class
  TypedResource getTypedResource(ApplicationProfile.Type type, Resource resource);

  default TypedResource getTypedResource(Supplier<ApplicationProfile.Type> type, Supplier<String> uri) {
    return getTypedResource(type.get(), ResourceFactory.createResource(uri.get()));
  }

  <T extends RdfValue> List<T> getValues(ApplicationProfile applicationProfile, TypedResource typedResource, ApplicationProfile.Attribute attribute);

  // todo what should this method do? Return all resources? Return defaulted page of resources? Or is actual behavior implementation-specific?
  List<? extends TypedResource> findAll(ApplicationProfile.Type type);

  List<TypedResource> findAll(Supplier<ApplicationProfile.Type> typeSupplier, ApplicationView.AttributeMatcher... attributeMatchers);

  void save(List<DeltaResource> deltaResources);

}
