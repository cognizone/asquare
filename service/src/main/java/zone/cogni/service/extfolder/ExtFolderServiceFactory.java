package zone.cogni.service.extfolder;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import zone.cogni.core.spring.ResourceHelper;
import zone.cogni.core.util.FileHelper;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtFolderServiceFactory {
  private static final Logger log = LoggerFactory.getLogger(ExtFolderServiceFactory.class);

  private final File extFolder;
  private final boolean required;

  public ExtFolderServiceFactory(File extFolder, boolean required) {
    this.extFolder = extFolder;
    this.required = required;
  }

  public ExtFolderService buildExtFolderService(String name, SynchronizationInfo... synchronizationInfos) {
    final File subFolder = getSubFolder(name);
    Executors.newSingleThreadScheduledExecutor()
             .schedule(() -> Stream.of(synchronizationInfos)
                                              .forEach(synchronizationInfo -> synchronizeSourceAndDestination(subFolder, synchronizationInfo)), 60, TimeUnit.SECONDS);
    return new ExtFolderService(subFolder);
  }

  private void synchronizeSourceAndDestination(File subFolder, SynchronizationInfo synchronizationInfo) {
    String sourcePath = synchronizationInfo.getSource();
    Map<String, Resource> sourceFiles = getSourceFiles(sourcePath);
    Preconditions.checkState(!sourceFiles.isEmpty(), "Argument '%s' (Source of SynchronizationInfo) does not point to a folder", sourcePath);

    File destinationFolder = new File(subFolder, synchronizationInfo.getDestination());
    if (synchronizationInfo.isResetAtStartup() && destinationFolder.exists()) {
      FileHelper.forceDelete(destinationFolder);
    }

    log.info("ExtFolderService synchronizing folder '{}'", destinationFolder.getAbsolutePath());
    sourceFiles.forEach((relativePath, sourceFile) -> {
      File destinationFile = FileUtils.getFile(destinationFolder, relativePath);

      if (!destinationFile.exists()) {
        FileHelper.forceMkdir(destinationFile.getParentFile());
        ResourceHelper.copy(sourceFile, destinationFile);
        log.info("Source file '{}' synchronized", destinationFile.getAbsolutePath());
      }
    });
    log.info("ExtFolderService synchronization finished");
  }

  private Map<String, Resource> getSourceFiles(String sourcePath) {
    Resource sourceFolder = new ClassPathResource(sourcePath);
    Preconditions.checkState(sourceFolder.exists(), "Classpath folder '%s' (Source of SynchronizationInfo) does not exist or is empty.", sourcePath);
    return Stream.of(ResourceHelper.getResources(new PathMatchingResourcePatternResolver(getClass().getClassLoader()), sourcePath + "/**"))
                 .filter(this::isFile)
                 .collect(Collectors.toMap(resource -> getRelativePath(resource, sourceFolder), Function.identity()));
  }

  private boolean isFile(Resource resource) {
    return !ResourceHelper.getUrl(resource).toString().endsWith("/");
  }

  private String getRelativePath(Resource resource, Resource parent) {
    String resourceURL = ResourceHelper.getUrl(resource).toString();
    String parentURL = ResourceHelper.getUrl(parent).toString();
    Preconditions.checkState(resourceURL.startsWith(parentURL), "Resource '%s' is not part of the source folder '%s'", resourceURL, parentURL);
    return StringUtils.removeStart(resourceURL, parentURL);
  }

  private File getSubFolder(String name) {
    File subFolder = new File(extFolder, name);
    if (!required && !subFolder.exists()) {
      if (!subFolder.mkdirs()) {
        throw new RuntimeException("Unable to create extFolder");
      }
    }
    return subFolder;
  }
}
