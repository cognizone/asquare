package zone.cogni.sem.jena.template;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import zone.cogni.sem.jena.JenaUtils;

import java.util.Map;
import java.util.function.Function;

import static org.apache.commons.lang3.tuple.Pair.of;
import static org.apache.jena.query.QueryFactory.create;
import static org.apache.jena.query.Syntax.syntaxARQ;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static zone.cogni.sem.jena.JenaUtils.closeQuietly;
import static zone.cogni.sem.jena.JenaUtils.newEmptyModel;
import static zone.cogni.sem.jena.template.JenaBooleanHandler.booleanAskResultExtractor;
import static zone.cogni.sem.jena.template.JenaQueryTemplate.modelAsModel;

public class JenaTemplate implements JenaOperations {

  public static void removeConstruct(final Model model, String construct) {
    JenaTemplate.construct(model, construct, new ModelCallback<Object>() {
      @Override
      public Object doInModel(Model constructed) {
        return model.remove(constructed);
      }
    }, true, false);
  }

  public static void addConstructs(Model model, String... constructs) {
    for (String construct : constructs) {
      addConstruct(model, construct);
    }
  }

  public static void addConstruct(final Model model, String construct) {
    JenaUtils.closeQuietly(addAndReturnConstruct(model, construct));
  }

  public static Model addAndReturnConstruct(final Model model, String construct) {
    try {
      return JenaTemplate.construct(model, construct, new ModelCallback<Model>() {
        @Override
        public Model doInModel(Model constructed) {
          model.add(constructed);
          return constructed;
        }
      }, false, false);
    }
    catch (QueryParseException e) {
      throw new RuntimeException( e.getMessage() + " - query: " + construct, e);
    }

  }

  public static Model construct(Model model, String sparql) {
    return construct(model, sparql, modelAsModel, false, false);
  }

  public static Model construct(Model model, String sparql, QuerySolutionMap initialBinding) {
    return construct(model, sparql, initialBinding, modelAsModel, false, false);
  }

  public static Model construct(Model model, String sparql, boolean closeInput) {
    return construct(model, sparql, modelAsModel, false, closeInput);
  }

  public static Model construct(Model model, String sparql, QuerySolutionMap initialBinding, boolean closeInput) {
    return construct(model, sparql, initialBinding, modelAsModel, false, closeInput);
  }

  public static <T> T construct(Model model, String sparql, ModelCallback<T> modelCallBack, boolean closeResult, boolean closeInput) {
    return construct(model, sparql, null, modelCallBack, closeResult, closeInput);
  }

  public static <T> T construct(Model model, String sparql, QuerySolutionMap initialBinding, ModelCallback<T> modelCallBack, boolean closeResult, boolean closeInput) {
    return JenaQueryTemplate.construct(model, toQuery(sparql), initialBinding, modelCallBack, closeResult, closeInput);
  }

  public static <T> T select(Model model, String sparql, ListOfMapHandler<T> handler) {
    return select(model, sparql, handler, false);
  }

  public static <T> T select(Model model, String sparql, ListOfMapHandler<T> handler, boolean close) {
    return JenaQueryTemplate.select(model, toQuery(sparql), handler, close);
  }

  public static boolean ask(Model model, String sparql) {
    return ask(model, sparql, false);
  }

  public static boolean ask(Model model, String sparql, boolean close) {
    return ask(model, sparql, booleanAskResultExtractor, close);
  }

  public static <T> T ask(Model model, String sparql, JenaBooleanHandler<T> booleanExtractor) {
    return ask(model, sparql, booleanExtractor, false);
  }

  public static <T> T ask(Model model, String sparql, JenaBooleanHandler<T> booleanExtractor, boolean close) {
    return JenaQueryTemplate.ask(model, toQuery(sparql), booleanExtractor, close);
  }

  public static <T> T execute(Model model, ModelCallback<T> modelCallback) {
    return execute(model, modelCallback, false);
  }

  public static <T> T execute(Model model, ModelCallback<T> modelCallback, boolean close) {
    try {
      return modelCallback.doInModel(model);
    }
    catch (Exception e) {
      if (close) closeQuietly(model);
      throw new RuntimeException(e);
    }
    finally {
      if (close) closeQuietly(model);
    }
  }

  public static <T> Pair<Model, T> newModel(Function<Model, T> modelCallback) {
    return newModel(modelCallback, null);
  }

  public static <T> Pair<Model, T> newModel(Function<Model, T> modelCallback, Map<String, String> prefixes) {
    Model m = prefixes != null ? newEmptyModel(prefixes) : createDefaultModel();
    try {
      T object = modelCallback.apply(m);
      return of(m, object);
    }
    finally {
      closeQuietly(m);
    }
  }


  private static Query toQuery(String sparql) {
    return create(sparql, syntaxARQ);
  }

  private final Model model;

  public JenaTemplate(Model model) {
    this.model = model;
  }

  @Override
  public <T> T execute(ModelCallback<T> modelCallBack) {
    return execute(model, modelCallBack);
  }
}
