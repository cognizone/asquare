package zone.cogni.sem.jena.template;

@FunctionalInterface
public interface JenaBooleanHandler<T> {

  T handle(boolean askResult);

  JenaBooleanHandler<Boolean> booleanAskResultExtractor = new JenaBooleanHandler<Boolean>() {
    @Override
    public Boolean handle(boolean askResult) {
      return askResult;
    }
  };

  JenaBooleanHandler<String> stringAskResultExtractor = new JenaBooleanHandler<String>() {
    @Override
    public String handle(boolean askResult) {
      return String.valueOf(askResult);
    }
  };

}
