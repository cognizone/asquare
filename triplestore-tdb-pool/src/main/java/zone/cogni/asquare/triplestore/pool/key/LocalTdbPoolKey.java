package zone.cogni.asquare.triplestore.pool.key;

import lombok.Getter;

import java.io.File;
import java.nio.file.Path;

@Getter
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

  @Override
  public String key() {
    return getUri();
  }
}
