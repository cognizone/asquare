package zone.cogni.asquare.service.elasticsearch.collapsedap;

import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;

import java.util.function.Supplier;

public class SupplierServicesProvider implements ServicesProvider {
  private final Supplier<ElasticStore> elasticStoreSupplier;
  private final Supplier<ApplicationProfileConfig> applicationProfileConfigSupplier;

  public SupplierServicesProvider(Supplier<ElasticStore> elasticStoreSupplier, Supplier<ApplicationProfileConfig> applicationProfileConfigSupplier) {
    this.elasticStoreSupplier = elasticStoreSupplier;
    this.applicationProfileConfigSupplier = applicationProfileConfigSupplier;
  }

  @Override
  public ElasticStore getElasticStore() {
    return elasticStoreSupplier.get();
  }

  @Override
  public ApplicationProfileConfig getApplicationProfileConfig() {
    return applicationProfileConfigSupplier.get();
  }
}
