package zone.cogni.core.spring;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourceHelper {
  public static URL getUrl(Resource resource) {
    try {
      return resource.getURL();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Resource getResourceFromUrl(String url) {
    if (url.startsWith("classpath:")) {
      return new ClassPathResource(url.substring(10));
    }
    try {
      return new UrlResource(url);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static Resource[] getResources(ResourcePatternResolver applicationContext, String locationPattern) {
    try {
      return applicationContext.getResources(locationPattern);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String toString(InputStreamSource resource) {
    return toString(resource, "UTF-8");
  }

  public static String toString(InputStreamSource resource, String encoding) {
    try (InputStream inputStream = resource.getInputStream()) {
      return IOUtils.toString(inputStream, encoding);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] toByteArray(InputStreamSource resource) {
    try (InputStream inputStream = resource.getInputStream()) {
      return IOUtils.toByteArray(inputStream);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static UrlResource toUrlResource(String url) {
    try {
      return new UrlResource(url);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T extends InputStreamSource> InputStream getInputStream(T resource) {
    try {
      return resource.getInputStream();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void copy(Resource source, File destination) {
    InputStream inputStream = null;
    try {
      inputStream = source.getInputStream();
      FileUtils.copyInputStreamToFile(inputStream, destination);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      IOUtils.closeQuietly(inputStream);
    }
  }

  public static void copy(Resource source, OutputStream destination) {
    InputStream inputStream = null;
    try {
      inputStream = source.getInputStream();
      IOUtils.copyLarge(inputStream, destination);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      IOUtils.closeQuietly(inputStream);
    }
  }

  public static void zippedCopy(Resource resource, String entryName, OutputStream destination) {
    try {
      ZipOutputStream zippedoutPut = new ZipOutputStream(destination);
      zippedoutPut.putNextEntry(new ZipEntry(entryName));

      copy(resource, zippedoutPut);

      zippedoutPut.finish();
    }
    catch (IOException exc) {
      throw new RuntimeException("Unable to write model to zipped outputstream: " + exc.getMessage(),exc);
    }
  }

  private ResourceHelper() {
  }
}
