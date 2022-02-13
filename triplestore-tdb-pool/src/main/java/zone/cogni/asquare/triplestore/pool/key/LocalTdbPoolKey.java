package zone.cogni.asquare.triplestore.pool.key;

import java.io.File;
import java.nio.file.Path;

public class LocalTdbPoolKey extends ConceptUriBasedPoolKey {
  private final String uri;

  public LocalTdbPoolKey(final Path basePath, final String conceptUri) {
    super(basePath, conceptUri);
    this.uri = conceptUri;
  }

  public LocalTdbPoolKey(final File base, final String conceptUri) {
    super(base, conceptUri);
    this.uri = conceptUri;
  }

  public String getUri() {
    return uri;
  }

  @Override
  public String key() {
    return getUri();
  }
}
