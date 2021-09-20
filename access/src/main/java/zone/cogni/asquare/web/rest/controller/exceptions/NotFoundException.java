package zone.cogni.asquare.web.rest.controller.exceptions;

import java.util.function.Supplier;

public class NotFoundException extends RuntimeException {
  public static void when(boolean expression) {
    if (expression) throw new NotFoundException();
  }

  public static void whenNot(boolean expression, Supplier<String> supplier) {
    if (!expression) throw new NotFoundException(supplier.get());
  }

  public static void whenNot(boolean expression, String message) {
    if (!expression) throw new NotFoundException(message);
  }

  public static void when(boolean expression, Supplier<String> supplier) {
    if (expression) throw new NotFoundException(supplier.get());
  }

  public static void when(boolean expression, String message) {
    if (expression) throw new NotFoundException(message);
  }

  private static final long serialVersionUID = 1L;

  public NotFoundException() {
  }

  public NotFoundException(String message) {
    super(message);
  }
}
