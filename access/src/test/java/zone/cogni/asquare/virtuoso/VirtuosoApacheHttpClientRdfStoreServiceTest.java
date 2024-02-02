package zone.cogni.asquare.virtuoso;

import org.junit.jupiter.api.Disabled;

@Disabled("An integration test dependent on a running Virtuoso instance. To run it manually, set the config inside VirtuosoRdfStoreServiceTest.init and run the tests.")
public class VirtuosoApacheHttpClientRdfStoreServiceTest extends VirtuosoRdfStoreServiceTest {

  @Override
  protected VirtuosoRdfStoreService getVirtuosoRdfStoreService(String url, String user,
      String password, boolean useBasicAuth) {
    return new VirtuosoApacheHttpClientRdfStoreService(url, user, password, useBasicAuth);
  }
}
