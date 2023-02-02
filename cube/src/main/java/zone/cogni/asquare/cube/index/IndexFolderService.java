package zone.cogni.asquare.cube.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @deprecated switch to {@link IndexingConfiguration}
 */
@Deprecated
public class IndexFolderService extends IndexingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IndexFolderService.class);

  @Deprecated
  private final ResourcePatternResolver resourcePatternResolver;
  @Deprecated
  private final String configurationPath;

  public IndexFolderService() {
    this.resourcePatternResolver = null;
    this.configurationPath = null;
  }

  @Deprecated
  public IndexFolderService(ResourcePatternResolver resourcePatternResolver, String configurationPath) {
    this.resourcePatternResolver = resourcePatternResolver;
    this.configurationPath = calculateConfigurationPath(configurationPath);
  }

  /**
   * Path without / at end which will contain all configuration data.
   *
   * @param configurationPath classpath
   * @return configurationPath path not ending with <code>/</code>
   */
  @Nonnull
  private String calculateConfigurationPath(@Nonnull String configurationPath) {
    return configurationPath.endsWith("/") ? configurationPath.substring(0, configurationPath.length() - 1)
                                           : configurationPath;
  }

  /**
   * Use {@link FromFolderBuilder} instead.
   */
  @PostConstruct
  @Deprecated
  public void init() {
    if (resourcePatternResolver == null && configurationPath == null) {
      log.info("No configuration path set. Please make sure you invoke validate method explicitly.");
      return;
    }

    IndexingConfiguration fromBuilder = new FromFolderBuilder().withConfigurationPath(configurationPath)
                                                               .withResourcePatternResolver(resourcePatternResolver)
                                                               .build();

    setIndexConfigurations(fromBuilder.getIndexConfigurations());
    validate();
  }

  /**
   * Use #getPartitionedIndexConfigurations instead.
   */
  @Deprecated
  public List<IndexingConfiguration.Index> getIndexFolders() {
    return getIndexConfigurations();
  }

}
