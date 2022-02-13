package zone.cogni.asquare.triplestore.pool.key;

import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public class ConceptUriBasedPoolKey implements RdfStoreServicePoolKey<String> {

  private final Path dirPath;

  public ConceptUriBasedPoolKey(final Path basePath, final String conceptUri) {
    if(basePath == null || !StringUtils.hasText(conceptUri)) {
      throw new IllegalArgumentException("Arguments cannot be null");
    }
    this.dirPath = basePath.resolve(uriToDirName(conceptUri));
  }

  public ConceptUriBasedPoolKey(final File base, final String conceptUri) {
    this(base.toPath(), conceptUri);
  }

  @Override
  public String key() {
    return dirPath.toAbsolutePath().toString();
  }

  public Path getDirPath() {
    return dirPath;
  }

  public static String uriToDirName(final String uri) {
    return uri.replace('/', '_')
      .replace(':', '_')
      .replace('#', '_')
      .toLowerCase();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ConceptUriBasedPoolKey)) return false;
    final ConceptUriBasedPoolKey that = (ConceptUriBasedPoolKey) o;
    return key().equals(that.key());
  }

  @Override
  public int hashCode() {
    return Objects.hash(key());
  }

  @Override
  public String toString() {
    return key();
  }
}
