package zone.cogni.asquare.access;

import com.fasterxml.jackson.databind.node.ObjectNode;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.elasticsearch.Params;

public interface ElasticAccessService extends AccessService {

  void indexResource(TypedResource resource);

  void indexResource(TypedResource resource, Params params);

  void deleteResource(TypedResource resource);

  void resetIndex(ObjectNode indexSettings);

  void deleteAll(ApplicationProfile.Type type);
}
