package zone.cogni.asquare.cube.monitoredpool;

import java.util.concurrent.Callable;

/**
 * Stores information on the result of the processed Callable.
 */
public class CallableResult<V> {

  private Callable<V> callable;
  private V result;
  private boolean success;
  private RuntimeException exception;

  public CallableResult(Callable<V> callable, V result) {
    this(callable, result, true, null);
  }

  public CallableResult(Callable<V> callable, RuntimeException exception) {
    this(callable, null, false, exception);
  }

  public CallableResult(Callable<V> callable, V result, boolean success, RuntimeException exception) {
    this.callable = callable;
    this.result = result;
    this.success = success;
    this.exception = exception;
  }

  public String getExceptionMessage() {
    return getException().getMessage();
  }

  public Callable<V> getCallable() {
    return callable;
  }

  public void setCallable(Callable<V> callable) {
    this.callable = callable;
  }

  public V getResult() {
    return result;
  }

  public void setResult(V result) {
    this.result = result;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public RuntimeException getException() {
    return exception;
  }

  public void setException(RuntimeException exception) {
    this.exception = exception;
  }
}
