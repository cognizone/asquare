package zone.cogni.asquare.web.rest.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.function.Supplier;

@Deprecated
@Service
public class RestControllerSupport {
  public static <T> ResponseEntity<T> handleRequest(Supplier<T> bodySupplier, HttpStatus status) {
    return handleHttpEntityRequest(() -> new HttpEntity<T>(bodySupplier.get()), status);
  }

  public static <T> ResponseEntity<T> handleHttpEntityRequest(Supplier<HttpEntity<T>> getEntity, HttpStatus status) {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    HttpEntity<T> httpEntity = getEntity.get();
    T body = httpEntity.getBody();
    HttpHeaders passedHeaders = httpEntity.getHeaders();

    stopWatch.stop();

    HttpHeaders headers = new HttpHeaders();
    if (passedHeaders != null) {
      headers.putAll(passedHeaders);
    }
    headers.set("X-Time-Consumed", String.valueOf(stopWatch.getLastTaskTimeMillis()));
    return new ResponseEntity<>(body, headers, status);
  }
}
