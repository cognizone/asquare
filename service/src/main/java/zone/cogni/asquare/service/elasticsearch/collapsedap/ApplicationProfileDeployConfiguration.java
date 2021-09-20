package zone.cogni.asquare.service.elasticsearch.collapsedap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSimpleJson;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.CollapseApplicationProfile;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;

@Configuration
@ConditionalOnProperty(value = "asquare.ap.deploy", matchIfMissing = true)
public class ApplicationProfileDeployConfiguration implements ImportAware, ApplicationListener<ContextRefreshedEvent> {
  private static final Logger log = LoggerFactory.getLogger(ApplicationProfileDeployConfiguration.class);

  private final ServicesProvider servicesProvider;
  private String indexName;
  private String defaultIndexName;
  private String indexDocumentId;
  private Resource resource;
  private ObjectNode jsonElasticDocument;

  public ApplicationProfileDeployConfiguration(@Value("${asquare.ap.deploy:}") Boolean isGo, @Value("${asquare.ap.config:}") String defaultIndexName, ServicesProvider servicesProvider) {
    log.info("construct");
    if (null == isGo || !isGo) throw new RuntimeException("Property asquare.ap.deploy not set or false????");
    this.servicesProvider = servicesProvider;
    this.defaultIndexName = defaultIndexName;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setImportMetadata(AnnotationMetadata importMetadata) {
    log.info("setImportMetadata");
    Map<String, Object> annotationAttributes = importMetadata.getAnnotationAttributes(EnableApplicationProfileDeploy.class.getName());
    if (null == annotationAttributes) throw new RuntimeException("No EnableApplicationProfileDeploy annotations found");

    indexName = (String) annotationAttributes.get("indexName");
    if(StringUtils.equals("${asquare.ap.config}", indexName)) {
      indexName = defaultIndexName;
    }
    if (StringUtils.isBlank(indexName)) throw new RuntimeException("No indexName set");

    indexDocumentId = (String) annotationAttributes.get("indexDocumentId");
    if (StringUtils.isBlank(indexDocumentId)) throw new RuntimeException("No indexDocumentId set");

    String apResource = (String) annotationAttributes.get("apResource");
    resource = new ClassPathResource(apResource);

    initResource();
  }

  private void initResource() {
    log.info("initResource");
    try {
      ApplicationProfile applicationProfile = servicesProvider.getApplicationProfileConfig().getDeserializer().apply(resource);
      ApplicationProfile collapse = new CollapseApplicationProfile().apply(applicationProfile);
      JsonNode jsonNode = new ApplicationProfileSimpleJson().apply(collapse);

      ObjectMapper mapper = new ObjectMapper();
      String json = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(jsonNode);
      String patchedJson = json.replace("\"", "\\\"").replace("\r", "").replace("\n", "");
      jsonElasticDocument = (ObjectNode) mapper.readTree("{\"json\":\"" + patchedJson + "\"}");
    }
    catch (IOException e) {
      throw new RuntimeException("Init resource failed", e);
    }
  }

  @Override
  public void onApplicationEvent(@Nonnull ContextRefreshedEvent event) {
    log.info("Get to business");
    Thread thread = new Thread(this::goForIt, "ApplicationProfileDeployConfiguration");
    thread.setDaemon(true);
    thread.start();
  }

  private void goForIt() {
    while (true) {
      log.info("Trying one CollapseApplicationProfile index...");
      try {
        servicesProvider.getElasticStore().indexDocument(indexName, indexDocumentId, jsonElasticDocument);
        log.info("whoohoo, success");
        return;
      }
      catch (Exception exception) {
        log.warn("Failed to index CollapseApplicationProfile, waiting 30s and retrying", exception);
        try {
          Thread.sleep(30000L);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }
}
