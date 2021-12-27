package zone.cogni.libs.sparqlservice;

import org.springframework.core.env.Environment;
import zone.cogni.libs.core.CognizoneException;
import zone.cogni.libs.sparqlservice.impl.Config;
import zone.cogni.libs.sparqlservice.impl.FusekiConfig;
import zone.cogni.libs.sparqlservice.impl.FusekiSparqlService;
import zone.cogni.libs.sparqlservice.impl.GraphDBConfig;
import zone.cogni.libs.sparqlservice.impl.GraphDBSparqlService;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;
import zone.cogni.libs.sparqlservice.impl.StardogSparqlService;
import zone.cogni.libs.sparqlservice.impl.VirtuosoSparqlService;

import javax.inject.Inject;

public class SparqlServiceProvider {
  private final String configPrefix;

  @Inject
  private Environment environment;

  public SparqlServiceProvider(String configPrefix) {
    this.configPrefix = configPrefix.trim() + (configPrefix.endsWith(".") ? "" : ".");
  }

  public SparqlService createSparqlService(Enum enumValue) {
    String base = configPrefix + enumValue.name() + ".";
    String type = CognizoneException.failIfBlank(environment.getProperty(base + "type"), "Type property not found: " + base + "type");

    switch (type) {
      case "virtuoso":
        return new VirtuosoSparqlService(createDefaultConfig(base));
      case "fuseki":
        return new FusekiSparqlService(createFusekiConfig(base));
      case "inMemory":
        return new JenaModelSparqlService();
      case "graphdb":
        return new GraphDBSparqlService(createGraphDBConfig(base));
      case "stardog":
        return new StardogSparqlService(createDefaultConfig(base));
      default:
        throw new CognizoneException("SparqlService type '{}' unknown.", type);
    }
  }

  private Config createDefaultConfig(String base) {
    Config config = new Config();
    fillDefaultConfig(config, base);
    return config;
  }

  private FusekiConfig createFusekiConfig(String base) {
    FusekiConfig config = new FusekiConfig();
    fillDefaultConfig(config, base);
    config.setQueryUrl(environment.getProperty(base + "queryUrl"));
    config.setUpdateUrl(environment.getProperty(base + "updateUrl"));
    config.setGraphStoreUrl(environment.getProperty(base + "graphStoreUrl"));
    config.setOverwriteTurtleMimeType(environment.getProperty(base + "overwriteTurtleMimeType"));
    return config;
  }

  private GraphDBConfig createGraphDBConfig(String base) {
    GraphDBConfig config = new GraphDBConfig();
    fillDefaultConfig(config, base);
    config.setRepository(environment.getProperty(base + "repository"));
    return config;
  }

  private void fillDefaultConfig(Config config, String base) {
    config.setUrl(environment.getProperty(base + "url"));
    config.setUser(environment.getProperty(base + "user"));
    config.setPassword(environment.getProperty(base + "password"));
    config.setGraphCrudUseBasicAuth(Boolean.parseBoolean(environment.getProperty(base + "sparqlGraphCrudUseBasicAuth")));
  }

}
