package zone.cogni.core.util;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOHelper {
  public static <X extends Flushable & Closeable> void flushAndClose(X closeFlusher) {
    if (closeFlusher == null) return;

    try {
      closeFlusher.flush();
    }
    catch (IOException ignore) {
    }

    try {
      closeFlusher.close();
    }
    catch (IOException ignore) {
    }
  }

  public static <X extends Flushable> void flush(X closeFlusher) {
    if (closeFlusher == null) return;

    try {
      closeFlusher.flush();
    }
    catch (IOException ignore) {
    }
  }

  public static long copyLarge(InputStream inputStream, OutputStream outputStream) {
    try {
      return IOUtils.copyLarge(inputStream, outputStream);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
