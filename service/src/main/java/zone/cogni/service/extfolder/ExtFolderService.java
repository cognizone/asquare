package zone.cogni.service.extfolder;

import com.google.common.base.Preconditions;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class ExtFolderService {
  private final File extFolder;

  ExtFolderService(@Nonnull File extFolder) {
    Preconditions.checkState(extFolder.isDirectory(), "Given extFolder does not exist: %s", extFolder);
    this.extFolder = extFolder;
  }


  @Nonnull
  public File getExtFolder() {
    return extFolder;
  }

  /**
   * Returns a file without checking if it exists or not.
   * User can then create the file.
   *
   * It also ensures the parent folder is present, so user does not need to create those.
   */
  @Nonnull
  public File getFile(String path) {
    File file = new File(extFolder, path);

    if (!file.getParentFile().exists()) {
      boolean created = file.getParentFile().mkdirs();

      if (!created) throw new IllegalStateException("Cannot create parent folders for ext file " + path + "." +
                                                    " Maybe insufficient rights?");
    }

    return file;
  }

  @Nullable
  public File findFile(String path) {
    File result = new File(extFolder, path);
    return result.exists() ? result : null;
  }


  @Nonnull
  public File findMandatoryFile(String path) {
    return Preconditions.checkNotNull(findFile(path), "File not found ext directories: %s", path);
  }

  @Nonnull
  public Resource findMandatoryResource(String path) {
    return new FileSystemResource(findMandatoryFile(path));
  }

  @Nullable
  public Resource findResource(String path) {
    File file = findFile(path);
    return file != null ? new FileSystemResource(file) : null;
  }
}
