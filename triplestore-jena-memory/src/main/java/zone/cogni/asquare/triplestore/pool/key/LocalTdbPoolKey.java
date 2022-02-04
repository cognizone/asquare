package zone.cogni.asquare.triplestore.pool.key;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocalTdbPoolKey)) return false;
    if (!super.equals(o)) return false;
    final LocalTdbPoolKey that = (LocalTdbPoolKey) o;
    return uri.equals(that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), uri);
  }
}
