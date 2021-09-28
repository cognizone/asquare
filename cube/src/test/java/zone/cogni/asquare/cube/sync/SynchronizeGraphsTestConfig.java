package zone.cogni.asquare.cube.sync;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.cube.monitoredpool.MonitoredPool;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.index.DatasetRdfStoreService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.jena.utils.JenaUtils;

import java.util.Arrays;

@Configuration
@Import(value = {SpelService.class})
public class SynchronizeGraphsTestConfig {

  @Autowired
  private SpelService spelService;

  private final PaginatedQuery paginatedQuery = new PaginatedQuery(10000);
  private final MonitoredPool monitoredPool = new MonitoredPool("SyncTest", 1);

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public RdfStoreService sourceRdfStore(String... names) {
    Dataset dataset = DatasetFactory.create();
    Arrays.stream(names)
          .forEach(name -> {
            String id = StringUtils.substringBefore(name, "-");
            dataset.addNamedModel("http://demo.com/" + id,
                                  JenaUtils.read(new ClassPathResource("sync/data/" + name + ".ttl")));

          });
    return new DatasetRdfStoreService(dataset);
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public RdfStoreService targetRdfStore(String... names) {
    Dataset dataset = DatasetFactory.create();
    Arrays.stream(names)
          .forEach(name -> {
            String id = StringUtils.substringBefore(name, "-");
            dataset.addNamedModel("http://demo.com/" + id,
                                  JenaUtils
                                          .read(new ClassPathResource("sync/data/target-" + name + ".ttl")));

          });
    return new DatasetRdfStoreService(dataset);
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  SynchronizeGraphs synchronizedGraph(RdfStoreService source, RdfStoreService target) {
    return new SynchronizeGraphs("sync",
                                 source,
                                 target,
                                 () -> "2020-12-03T09:11:13Z",
                                 spelService,
                                 paginatedQuery,
                                 monitoredPool);
  }

}
