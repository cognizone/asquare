package zone.cogni.asquareroot;

import org.elasticsearch.node.NodeValidationException;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.util.ReflectionTestUtils;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquareroot.elastic.EmbeddedElasticsearch7Store;
import zone.cogni.asquareroot.sparql.InMemoryRdfStoreService;
import zone.cogni.libs.services.extfolder.ExtFolderServiceFactory;
import zone.cogni.libs.sparqlservice.SparqlService;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EmbeddedContext {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedContext.class);

  private static ThreadLocal<EmbeddedContext> localEmbeddedContext = new ThreadLocal<>();
  private final TestContext testContext;
  private TemporaryFolder rootTempFolder = new TemporaryFolder();
  private Map<String, EmbeddedElasticsearch7Store> embeddedElasticsearch7StoreByOriginalUri = new HashMap<>();
  private Map<String, File> tempFolderByIdentitier = new HashMap<>();
  private Map<String, JenaModelSparqlService> sparqlServiceByOriginalUri = new HashMap<>();
  private Map<String, InMemoryRdfStoreService> rdfServiceByOriginalUri = new HashMap<>();

  private Boolean embeddedElastic = false;
  private Boolean relaxedSelect = false;
  private Boolean realEnvironment = false;

  private EmbeddedContext(TestContext testContext) {
    this.testContext = testContext;
  }

  protected static void resetContext(TestContext testContext) {
    localEmbeddedContext.remove();
    localEmbeddedContext.set(new EmbeddedContext(testContext));
  }

  protected static EmbeddedContext get() {
    return localEmbeddedContext.get();
  }

  public Boolean getRealEnvironment() {
    return realEnvironment;
  }

  public void setRealEnvironment(Boolean realEnvironment) {
    this.realEnvironment = realEnvironment;
  }

  public Boolean getRelaxedSelect() {
    return relaxedSelect;
  }

  public void setRelaxedSelect(Boolean relaxedSelect) {
    this.relaxedSelect = relaxedSelect;
  }

  protected Object getTestInstance() {
    return testContext.getTestInstance();
  }

  protected Boolean getEmbeddedElastic() {
    return embeddedElastic;
  }

  protected void setEmbeddedElastic(Boolean embeddedElastic) {
    this.embeddedElastic = embeddedElastic;
  }

  protected void unregisterEmbeddedContext() {
    getEmbeddedElasticsearch7StoreByOriginalUri().forEach((key, elastic) -> {
      try {
        elastic.close();
      }
      catch (IOException | InterruptedException e) {
        log.error("Embedded elastic server {} shuts down with an exception, {}", key, e.getLocalizedMessage());
      }
    });
    getRootTempFolder().delete();
  }

  protected TemporaryFolder getRootTempFolder() {
    return rootTempFolder;
  }

  protected void setRootTempFolder(TemporaryFolder rootTempFolder) {
    this.rootTempFolder = rootTempFolder;
  }

  protected Map<String, EmbeddedElasticsearch7Store> getEmbeddedElasticsearch7StoreByOriginalUri() {
    return embeddedElasticsearch7StoreByOriginalUri;
  }

  protected void setEmbeddedElasticsearch7StoreByOriginalUri(Map<String, EmbeddedElasticsearch7Store> embeddedElasticsearch7StoreByOriginalUri) {
    this.embeddedElasticsearch7StoreByOriginalUri = embeddedElasticsearch7StoreByOriginalUri;
  }

  protected Map<String, File> getTempFolderByIdentitier() {
    return tempFolderByIdentitier;
  }

  protected void setTempFolderByIdentitier(Map<String, File> tempFolderByIdentitier) {
    this.tempFolderByIdentitier = tempFolderByIdentitier;
  }

  protected Map<String, JenaModelSparqlService> getSparqlServiceByOriginalUri() {
    return sparqlServiceByOriginalUri;
  }

  protected void setSparqlServiceByOriginalUri(Map<String, JenaModelSparqlService> sparqlServiceByOriginalUri) {
    this.sparqlServiceByOriginalUri = sparqlServiceByOriginalUri;
  }

  protected Map<String, InMemoryRdfStoreService> getRdfServiceByOriginalUri() {
    return rdfServiceByOriginalUri;
  }

  protected void setRdfServiceByOriginalUri(Map<String, InMemoryRdfStoreService> rdfServiceByOriginalUri) {
    this.rdfServiceByOriginalUri = rdfServiceByOriginalUri;
  }

  protected File getTemporaryFolder(String identifier) {
    return tempFolderByIdentitier.computeIfAbsent(identifier, m -> {
      try {
        return rootTempFolder.newFolder();
      }
      catch (IOException e) {
        log.error("Can not create temporary folder {}, du to {}", identifier, e.getLocalizedMessage());
      }
      return null;
    });
  }

  protected EmbeddedElasticsearch7Store getEmbeddedElastic(String uri) {

    return embeddedElasticsearch7StoreByOriginalUri.computeIfAbsent(uri, map -> {
      File dataFolder = getTemporaryFolder("elastic: " + uri);

      EmbeddedElasticsearch7Store embeddedElasticsearch7Store = new EmbeddedElasticsearch7Store(dataFolder);
      try {
        embeddedElasticsearch7Store.start();
        return embeddedElasticsearch7Store;
      }
      catch (NodeValidationException e) {
        log.error("Embedded elastic server {} can not be started, {}", uri, e.getLocalizedMessage());
      }
      return null;
    });
  }

  protected JenaModelSparqlService getVirtuosoSparqlService(String uri) {
    return sparqlServiceByOriginalUri.computeIfAbsent(uri, map -> new JenaModelSparqlService(relaxedSelect));
  }

  protected InMemoryRdfStoreService getVirtuosoRdfSparqlService(String uri, String user, String password) {
    return rdfServiceByOriginalUri.computeIfAbsent(uri, map -> new InMemoryRdfStoreService(getVirtuosoSparqlService(uri), uri, user, password));
  }

  public Elasticsearch7Store mockElasticsearch7Store(Object originalBean, String originalBeanName) {
    System.out.println(originalBeanName + " : " + originalBean + " -> Embedded Elasticsearch7Store");

    String url = (String) ReflectionTestUtils.getField(originalBean, "url");
    return getEmbeddedElastic(url);
  }

  public SparqlService mockVirtuosoSparqlService(Object originalBean, String originalBeanName) {
    System.out.println(originalBeanName + " : " + originalBean + " -> Embedded VirtuosoSparqlService");
    String endpointUrl = (String) ReflectionTestUtils.getField(originalBean, "endpointUrl");
    return getVirtuosoSparqlService(endpointUrl);
  }

  public RdfStoreService mockVirtuosoRdfStoreService(Object originalBean, String originalBeanName) {
    System.out.println(originalBeanName + " : " + originalBean + " -> Embedded VirtuosoRdfStoreService");

    String rdfStoreUrl = (String) ReflectionTestUtils.getField(originalBean, "rdfStoreUrl");
    String rdfStoreUser = (String) ReflectionTestUtils.getField(originalBean, "rdfStoreUser");
    String rdfStorePassword = (String) ReflectionTestUtils.getField(originalBean, "rdfStorePassword");
    return getVirtuosoRdfSparqlService(rdfStoreUrl, rdfStoreUser, rdfStorePassword);
  }

  public ExtFolderServiceFactory mockExtFolderServiceFactory(Object originalBean, String originalBeanName) {
    System.out.println(originalBeanName + " : " + originalBean + " -> Embedded ExtFolderServiceFactory");
    File extFolder = (File) ReflectionTestUtils.getField(originalBean, "extFolder");
    File tempFolder = getTemporaryFolder(extFolder.getAbsolutePath());
    return new ExtFolderServiceFactory(tempFolder, false);
  }


}
