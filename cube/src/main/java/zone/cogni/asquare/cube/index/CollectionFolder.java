package zone.cogni.asquare.cube.index;

import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;

import java.util.List;
import java.util.stream.Collectors;

class CollectionFolder {

  private String name;
  private List<Resource> selectQueryResources;
  private List<String> selectQueries;
  private List<Resource> constructQueryResources;
  private List<String> constructQueries;
  private List<Resource> facetQueryResources;
  private List<String> facetQueries;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Resource> getSelectQueryResources() {
    return selectQueryResources;
  }

  public void setSelectQueryResources(List<Resource> selectQueryResources) {
    this.selectQueryResources = selectQueryResources;
    selectQueries = getQueries(this.selectQueryResources);
  }

  public List<String> getSelectQueries() {
    return selectQueries;
  }

  public List<Resource> getConstructQueryResources() {
    return constructQueryResources;
  }

  public void setConstructQueryResources(List<Resource> constructQueryResources) {
    this.constructQueryResources = constructQueryResources;
    constructQueries = getQueries(this.constructQueryResources);
  }

  public List<String> getConstructQueries() {
    return constructQueries;
  }

  public List<Resource> getFacetQueryResources() {
    return facetQueryResources;
  }

  public void setFacetQueryResources(List<Resource> facetQueryResources) {
    this.facetQueryResources = facetQueryResources;
    facetQueries = getQueries(this.facetQueryResources);
  }

  public List<String> getFacetQueries() {
    return facetQueries;
  }

  private static List<String> getQueries(List<Resource> queryResources) {
    return queryResources.stream()
                         .map(ResourceHelper::toString)
                         .collect(Collectors.toUnmodifiableList());
  }

  public boolean isValid() {
    return !selectQueryResources.isEmpty() && !constructQueryResources.isEmpty();
  }
}
