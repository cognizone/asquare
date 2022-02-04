package zone.cogni.sem.jena.template;

@FunctionalInterface
public interface JenaBooleanHandler<T> {

  T handle(boolean askResult);

  JenaBooleanHandler<Boolean> booleanAskResultExtractor = askResult -> askResult;

  JenaBooleanHandler<String> stringAskResultExtractor = String::valueOf;

}
