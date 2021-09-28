package zone.cogni.asquareroot.elastic;

import org.elasticsearch.node.NodeValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.service.elasticsearch.v7.GenericElastic7Configuration;

import java.io.File;

@Configuration
public class EmbeddedGenericElasticConfig extends GenericElastic7Configuration {
  private Elasticsearch7Store store;
  public EmbeddedGenericElasticConfig(@Value("${elasticsearch.tempFolder}") String tempFolder) throws NodeValidationException {
    super(true);
    this.store = new EmbeddedElasticsearch7Store(new File(tempFolder));
  }

  @Override
  @Bean("elasticsearchStore")
  public Elasticsearch7Store getStore() {
    return this.store;
  }

  @Override
  public String getHost() {
    return "inmem";
  }

  @Override
  public String getPort() {
    return "0";
  }
}
