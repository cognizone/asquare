package zone.cogni.libs.services.extfolder;

import java.io.File;

public class ExtFolderServiceFactory {
  private final File extFolder;
  private final boolean required;

  public ExtFolderServiceFactory(File extFolder, boolean required) {
    this.extFolder = extFolder;
    this.required = required;
  }

  public ExtFolderService buildExtFolderService(String name) {
    File subFolder = new File(extFolder, name);
    if (!required && !subFolder.exists()) {
      if (!subFolder.mkdirs()) {
        throw new RuntimeException("Unable to create extFolder");
      }
    }
    return new ExtFolderService(subFolder);
  }
}
