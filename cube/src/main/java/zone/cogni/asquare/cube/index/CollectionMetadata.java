package zone.cogni.asquare.cube.index;

import org.springframework.core.io.Resource;

import java.util.List;

class CollectionMetadata {

  private String name;
  private List<Resource> selectQueryResources;
  private List<Resource> constructQueryResources;
  private List<Resource> facetQueryResources;

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
  }

  public List<Resource> getConstructQueryResources() {
    return constructQueryResources;
  }

  public void setConstructQueryResources(List<Resource> constructQueryResources) {
    this.constructQueryResources = constructQueryResources;
  }

  public List<Resource> getFacetQueryResources() {
    return facetQueryResources;
  }

  public void setFacetQueryResources(List<Resource> facetQueryResources) {
    this.facetQueryResources = facetQueryResources;
  }

  public boolean isValid() {
    return !selectQueryResources.isEmpty() && !constructQueryResources.isEmpty();
  }
}
