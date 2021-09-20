package zone.cogni.libs.sparqlservice;

import org.springframework.core.env.Environment;
import zone.cogni.libs.core.CognizoneException;
import zone.cogni.libs.sparqlservice.impl.Config;
import zone.cogni.libs.sparqlservice.impl.FusekiSparqlService;
import zone.cogni.libs.sparqlservice.impl.GraphDBConfig;
import zone.cogni.libs.sparqlservice.impl.GraphDBSparqlService;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;
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

    Config config = new Config();
    config.setUrl(environment.getProperty(base + "url"));
    config.setUser(environment.getProperty(base + "user"));
    config.setPassword(environment.getProperty(base + "password"));
    config.setGraphCrudUseBasicAuth(Boolean.parseBoolean(environment.getProperty(base + "sparqlGraphCrudUseBasicAuth")));

    switch (type) {
      case "virtuoso":
        return new VirtuosoSparqlService(config);
      case "fuseki":
        return new FusekiSparqlService(config);
      case "inMemory":
        return new JenaModelSparqlService();
      case "graphdb":
        GraphDBConfig graphDBConfig = new GraphDBConfig(config);
        graphDBConfig.setRepository(environment.getProperty(base + "repository"));
        return new GraphDBSparqlService(graphDBConfig);
      default:
        throw new CognizoneException("SparqlService type '{}' unknown.", type);
    }
  }
}
