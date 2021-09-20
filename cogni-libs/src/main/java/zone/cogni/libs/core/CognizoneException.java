package zone.cogni.libs.core;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;

import javax.annotation.Nonnull;

public class CognizoneException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static void when(boolean test) {
    if (test) {
      throw new CognizoneException("Assertion failed");
    }
  }

  public static void when(boolean test, String message) {
    if (test) {
      throw new CognizoneException(message);
    }
  }

  public static void when(boolean test, String message, Object... parameters) {
    if (test) {
      throw new CognizoneException(message, parameters);
    }
  }

  public static CognizoneException rethrow(Throwable exception) {
    if (exception instanceof Error) {
      throw (Error) exception;
    }
    if (exception instanceof RuntimeException) {
      throw (RuntimeException) exception;
    }
    throw new CognizoneException(exception);
  }

  @Nonnull
  public static <T> T failIfNotInstance(Object object, Class<T> clazz, String message, Object... parameters) {
    when(!clazz.isInstance(object), message, parameters);
    //noinspection unchecked
    return (T) object;
  }

  @Nonnull
  public static <T> T failIfNull(T value, String message, Object... parameters) {
    when(null == value, message, parameters);
    //noinspection ConstantConditions
    return value;
  }

  @Nonnull
  public static <T extends CharSequence> T failIfBlank(T value, String message, Object... parameters) {
    when(StringUtils.isBlank(value), message, parameters);
    //noinspection ConstantConditions
    return value;
  }

  public CognizoneException() {
  }

  public CognizoneException(String message) {
    super(message);
  }

  public CognizoneException(Throwable cause, String message, Object... parameters) {
    super(MessageFormatter.arrayFormat(message, parameters).getMessage(), cause);
  }

  public CognizoneException(String message, Object... parameters) {
    super(MessageFormatter.arrayFormat(message, parameters).getMessage());
  }

  public CognizoneException(Throwable cause) {
    super(cause);
  }
}
