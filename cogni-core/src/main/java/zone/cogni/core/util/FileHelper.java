package zone.cogni.core.util;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

public class FileHelper {
  private FileHelper() {
  }

  public static String readFileToString(File file, String encoding) {
    try {
      return FileUtils.readFileToString(file, encoding);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] readFileToByteArray(File file) {
    try {
      return FileUtils.readFileToByteArray(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static File createTempFile(String prefix, String suffix) {
    try {
      return File.createTempFile(prefix, suffix);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> readLines(File file) {
    try {
      return FileUtils.readLines(file, "UTF-8");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void copyInputStreamToFile(InputStream inputStream, File outputFile) {
    try {
      FileUtils.copyInputStreamToFile(inputStream, outputFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static FileInputStream openInputStream(File file) {
    try {
      return new FileInputStream(file);
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static FileOutputStream openOutputStream(@Nonnull File file) {
    try {
      Files.createParentDirs(file);
      return FileUtils.openOutputStream(file);
    }
    catch (IOException e) {
      throw new RuntimeException( "Failed to open outputstream to " + file, e);
    }
  }

  public static String toUrlString(File file) {
    try {
      return file.toURI().toURL().toExternalForm();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getNameWithoutSuffix(File file) {
    String name = file.getName();
    int suffixStartIndex = name.lastIndexOf('.');
    return suffixStartIndex == -1 ? name : name.substring(0, suffixStartIndex);
  }

  public static String getFileExtension(File file) {
    return getFilenameExtension(file.getName());
  }

  public static String getFilenameExtension(String filename) {
    return StringUtils.isBlank(filename) ? "" : StringUtils.substringAfterLast(filename, ".");
  }

  /**
   * Returns the path for the given file, starting with the given parentDirectory as root.
   * Returns null if the given parentDirectory is not a parent of the given file.
   * If the 2 arguments point to the same file, an empty string is returned.
   */
  @Nullable
  public static String getPathFrom(File parentDirectory, File file) {
    return getPathFrom(parentDirectory, file, '/');
  }

  public static String getPathFrom(File parentDirectory, File file, char separator) {
    try {
      parentDirectory = parentDirectory.getCanonicalFile();
      file = file.getCanonicalFile();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (parentDirectory.equals(file)) {
      return "";
    }

    StringBuilder stringBuilder = new StringBuilder(file.getName());
    while (null != (file = file.getParentFile())) {
      if (parentDirectory.equals(file)) {
        return stringBuilder.toString();
      }
      stringBuilder.insert(0, separator);
      stringBuilder.insert(0, file.getName());
    }
    return null;
  }

  public static void deleteNowOrOnExit(File file) {
    if (FileUtils.deleteQuietly(file)) return;
    try {
      FileUtils.forceDeleteOnExit(file);
    }
    catch (IOException ignore) {
    }
  }

  public static void forceDelete(File file) {
    try {
      FileUtils.forceDelete(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void moveFile(File sourceFile, File destinationFile) {
    try {
      File destinationFolder = destinationFile.getParentFile();
      if (null != destinationFolder) {
        destinationFolder.mkdirs();
      }
      FileUtils.moveFile(sourceFile, destinationFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void moveFileToDirectory(File sourceFile, File destinationDirectory, boolean createDestinationDirectory) {
    try {
      FileUtils.moveFileToDirectory(sourceFile, destinationDirectory, createDestinationDirectory);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void moveDirectory(File source, File destination) {
    try {
      FileUtils.moveDirectory(source, destination);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean deleteEmptyFoldersQuietly(File file) {
    try {
      deleteEmptyFolders(file);
      return true;
    }
    catch (RuntimeException ignored) {
      return false;
    }
  }

  public static void deleteEmptyFolders(File file) {
    if (null == file) return;

    if (file.isDirectory()) {
      //noinspection ConstantConditions
      if (file.listFiles().length > 0) return;
      forceDelete(file);
    }
    deleteEmptyFolders(file.getParentFile());
  }

  public static void copyURLToFile(URL url, File file) {
    try {
      FileUtils.copyURLToFile(url, file);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void copyFile(File source, File destination) {
    try {
      FileUtils.copyFile(source, destination);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void copyFileToDirectory(File sourceFile, File destinationDirectory) {
    try {
      FileUtils.copyFileToDirectory(sourceFile, destinationDirectory);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void cleanDirectory(File file) {
    try {
      FileUtils.cleanDirectory(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeByteArrayToFile(File file, byte[] data) {
    try {
      FileUtils.writeByteArrayToFile(file, data);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getFilenameFromPath(String path) {
    path = path.replace('\\', '/');
    while (path.endsWith("/")) {
      path = StringUtils.substring(path, 0, -1);
    }
    return path.contains("/") ? StringUtils.substringAfterLast(path, "/") : path;
  }

  public static void writeStringToFile(File file, String data, String encoding) {
    try {
      FileUtils.writeStringToFile(file, data, encoding);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeStringToFile(File file, String data, Charset encoding) {
    try {
      FileUtils.writeStringToFile(file, data, encoding);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @deprecated use {@link #writeStringToFile(File, String, Charset)} instead
   */
  @Deprecated
  public static void writeStringToFile(File file, String data) {
    try {
      FileUtils.writeStringToFile(file, data);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static PrintWriter createPrintWriter(File file) {
    try {
      return new PrintWriter(file, "UTF-8");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void touch(File file) {
    try {
      FileUtils.touch(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void forceMkdir(File file) {
    try {
      FileUtils.forceMkdir(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void copyDirectory(File srcDir, File destDir) {
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getContentType(String filePath) {
    if (StringUtils.endsWithIgnoreCase(filePath, ".jpg") || StringUtils.endsWithIgnoreCase(filePath, ".jpeg")) return "image/jpeg";
    if (StringUtils.endsWithIgnoreCase(filePath, ".xml")) return "application/xml";
    if (StringUtils.endsWithIgnoreCase(filePath, ".png")) return "image/png";
    if (StringUtils.endsWithIgnoreCase(filePath, ".pdf")) return "application/pdf";
    if (StringUtils.endsWithIgnoreCase(filePath, ".zip")) return "application/zip";
    if (StringUtils.endsWithIgnoreCase(filePath, ".csv")) return "text/csv";
    if (StringUtils.endsWithIgnoreCase(filePath, ".rdf")) return "application/rdf+xml";
    if (StringUtils.endsWithIgnoreCase(filePath, ".pptx") || StringUtils.endsWithIgnoreCase(filePath, ".ppt")) return "application/vnd.ms-powerpoint";
    if (StringUtils.endsWithIgnoreCase(filePath, ".xls")) return "application/vnd.ms-excel";
    if (StringUtils.endsWithIgnoreCase(filePath, ".json")) return "application/json";
    if (StringUtils.endsWithIgnoreCase(filePath, ".html")) return "text/html";
    if (StringUtils.endsWithIgnoreCase(filePath, ".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    if (StringUtils.endsWithIgnoreCase(filePath, ".doc")) return "application/msword";
    return "application/octet-stream";
  }

  public static String getExtension(String contentType) {
    if (StringUtils.startsWithIgnoreCase(contentType, "image/jpeg")) return ".jpg";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/xml")) return ".xml";
    if (StringUtils.startsWithIgnoreCase(contentType, "image/png")) return ".png";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/pdf")) return ".pdf";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/zip")) return ".zip";
    if (StringUtils.startsWithIgnoreCase(contentType, "text/csv")) return ".csv";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/rdf+xml")) return ".rdf";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/vnd.ms-powerpoint")) return ".pptx";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/vnd.ms-excel")) return ".xlsx";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/json")) return ".json";
    if (StringUtils.startsWithIgnoreCase(contentType, "text/html")) return ".html";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return ".docx";
    if (StringUtils.startsWithIgnoreCase(contentType, "application/msword")) return ".doc";
    return ".dat";
  }
}
