package zone.cogni.asquare.web.rest.controller.exceptions;

import java.util.function.Supplier;

public class BadInputException extends RuntimeException {
  public static void when(boolean expression) {
    if (expression) throw new BadInputException();
  }

  public static void whenNot(boolean expression, Supplier<String> supplier) {
    if (!expression) throw new BadInputException(supplier.get());
  }

  public static void whenNot(boolean expression, String message) {
    if (!expression) throw new BadInputException(message);
  }

  public static void when(boolean expression, Supplier<String> supplier) {
    if (expression) throw new BadInputException(supplier.get());
  }

  public static void when(boolean expression, String message) {
    if (expression) throw new BadInputException(message);
  }

  private static final long serialVersionUID = 1L;

  public BadInputException() {
  }

  public BadInputException(String message) {
    super(message);
  }
}
