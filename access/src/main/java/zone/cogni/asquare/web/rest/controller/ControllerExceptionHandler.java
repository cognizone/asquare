package zone.cogni.asquare.web.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import zone.cogni.asquare.web.rest.controller.exceptions.BadInputException;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;

@Deprecated
@ControllerAdvice
public class ControllerExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

  @ExceptionHandler({BadInputException.class, ServletRequestBindingException.class})
  @ResponseBody
  public ResponseEntity<Object> handleBadRequest(Exception exception) {
    log.error("Return 400", exception);
    return new ResponseEntity<>(convertToJson(exception, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
  }

  // todo this handler does not work?
  @ExceptionHandler({HttpMediaTypeNotAcceptableException.class})
  @ResponseBody
  public ResponseEntity<Object> handleNotAcceptableRequest(Exception exception) {
    log.error("Return 406", exception);
    return new ResponseEntity<>(convertToJson(exception, HttpStatus.NOT_ACCEPTABLE), HttpStatus.NOT_ACCEPTABLE);
  }
  //

  @ExceptionHandler(NotFoundException.class)
  @ResponseBody
  public ResponseEntity<Object> handleNotFound(Exception exception) {
    log.error("Return 404", exception);
    return new ResponseEntity<>(convertToJson(exception, HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(UnsupportedOperationException.class)
  @ResponseBody
  public ResponseEntity<Object> notImplemented(Exception exception) {
    log.error("Return 501", exception);
    return new ResponseEntity<>(convertToJson(exception, HttpStatus.NOT_IMPLEMENTED), HttpStatus.NOT_IMPLEMENTED);
  }

  @ExceptionHandler({RuntimeException.class})
  @ResponseBody
  public ResponseEntity<Object> internalError(Exception exception) {
    log.error("Return 500", exception);
    return new ResponseEntity<>(convertToJson(exception, HttpStatus.INTERNAL_SERVER_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ObjectNode convertToJson(Exception exception, HttpStatus status) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

    JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();

    ObjectNode exceptionNode = nodeFactory.objectNode();
    exceptionNode.put("logref", exception.getClass().getSimpleName());
    exceptionNode.put("status", status.value());
    exceptionNode.put("message", exception.getMessage());
    exceptionNode.set("_links", null);

    return exceptionNode;
  }
}
