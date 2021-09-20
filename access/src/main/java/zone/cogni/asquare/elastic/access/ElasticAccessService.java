package zone.cogni.asquare.elastic.access;

import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.Optional;

/**
 *  todo add and implement method to retrieve all resources for a given set of uri's on access layer! performance -> e.g. only one query in elastic
 *  todo support find by attribute -> find all possible values for a given attribute (e.g to support select boxes)
 *  todo attributes representing relations -> inverse indexing to load and retrieve related documents (resources) directly (~ tagging properties)
 *  todo investigate script fields in elastic
 *  todo investigate inner hits (parent/child, nested) and mappings
 */
@Deprecated
public interface ElasticAccessService extends AccessService {
  TypedResource getTypedResource(TypedResource typedResource);

  // todo move method to access service and implement also on SimpleRDFAccessService?
  Optional<TypedResource> findByUri(String uri);

  PagedResultSet<? extends TypedResource> findAll(ApplicationProfile.Type type, int size, int page);
}
