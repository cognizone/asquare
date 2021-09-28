package zone.cogni.asquare.applicationprofile.owl;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Resource;

import java.util.HashMap;
import java.util.Map;

public class ClassIdProvider {

  private final Map<String, String> namespaceShortNameMap = new HashMap<>();

  public void setShortNameForNamespace(String shortName, String namespace) {
    Preconditions.checkNotNull(shortName);
    Preconditions.checkNotNull(namespace);
    Preconditions.checkState(!namespaceShortNameMap.containsKey(namespace), "Short name for namespace already defined.");

    namespaceShortNameMap.put(namespace, shortName);
  }

  public String getClassId(Resource resource) {
    Preconditions.checkNotNull(resource);
    Preconditions.checkState(resource.isURIResource());

    String shortName = namespaceShortNameMap.get(resource.getNameSpace());
    return shortName == null ? resource.getLocalName() : shortName + resource.getLocalName();
  }
}
