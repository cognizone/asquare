package zone.cogni.sem.jena;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFErrorHandler;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFReader;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;
import zone.cogni.core.util.FileHelper;
import zone.cogni.core.util.IOHelper;
import zone.cogni.core.util.MapUtils;
import zone.cogni.sem.jena.functions.RdfNode2Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JenaUtils {
  private static final Logger log = LoggerFactory.getLogger(JenaUtils.class);

  private JenaUtils() {
  }

  public static OntModel createOntModel(OntModelSpec ontModelSpec) {
    return ModelFactory.createOntologyModel(ontModelSpec);
  }

  public static Model newEmptyModel(Map<String, String> prefixes) {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefixes(prefixes);
    return model;
  }

  public static Model create(Model model, boolean copyPrefixes) {
    Model newModel = ModelFactory.createDefaultModel();
    if (copyPrefixes) newModel.setNsPrefixes(model.getNsPrefixMap());
    newModel.add(model);
    return newModel;
  }

  public static Model create(Model... models) {
    Model result = ModelFactory.createDefaultModel();
    for (Model model : models) {
      result.add(model);
    }
    return result;
  }

  public static Model create(Map<String, String> namespaces, Model... models) {
    Model result = ModelFactory.createDefaultModel();
    result.setNsPrefixes(namespaces);
    for (Model model : models) {
      result.add(model);
    }
    return result;
  }

  public static Model produceModel(Consumer<Model> producer) {
    return produceModel(producer, null);
  }

  public static Model produceModel(Consumer<Model> producer, Map<String, String> prefixes) {
    return produceModel(new Function<Model, Model>() {
      @Override
      public Model apply(Model model) {
        producer.accept(model);
        return model;
      }
    }, prefixes).getLeft();
  }

  public static <T> Pair<Model, T> produceModel(Function<Model, T> producer) {
    return produceModel(producer, null);
  }

  public static <T> Pair<Model, T> produceModel(Function<Model, T> producer, Map<String, String> prefixes) {
    Model newModel = prefixes != null ? newEmptyModel(prefixes) : ModelFactory.createDefaultModel();
    try {
      T object = producer.apply(newModel);
      return Pair.of(newModel, object);
    }
    catch (Exception e) {
      JenaUtils.closeQuietly(newModel);
      throw new RuntimeException(e);
    }
  }

  public static void readAndConsume(Consumer<Model> consumer, boolean close, Collection<Resource> resources) {
    readAndConsume(consumer, close, resources.toArray(new Resource[resources.size()]));
  }

  public static void readAndConsume(Consumer<Model> consumer, boolean close, Resource... resources) {
    consume(() -> JenaUtils.read(resources), consumer, close);
  }

  public static void consume(Supplier<Model> modelSupplier, Consumer<Model> consumer, boolean close) {
    Model model = modelSupplier.get();
    try {
      consumer.accept(model);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      if (close) JenaUtils.closeQuietly(model);
    }
  }


  public static Model read(Resource... resources) {
    return read(Arrays.asList(resources));
  }

  public static Model read(Iterable<Resource> resources) {
    return read(resources, null);
  }

  public static Model read(Iterable<Resource> resources, Map<String, Object> readerProperties) {
    Model model = ModelFactory.createDefaultModel();

    for (Resource resource : resources) {
      InputStream inputstream = null;
      try {
        inputstream = ResourceHelper.getInputStream(resource);
        InternalRdfErrorHandler errorHandler = new InternalRdfErrorHandler(resource.getDescription());

        RDFReader rdfReader = getReader(model, resource, errorHandler, readerProperties);
        rdfReader.read(model, inputstream, null);

        Preconditions.checkState(!errorHandler.isFailure(), errorHandler.getInfo());
      }
      catch (RuntimeException e) {
        closeQuietly(model);

        throw e;
      }
      finally {
        IOUtils.closeQuietly(inputstream);
      }
    }

    return model;
  }

  public static Model read(InputStreamSource resource, Map<String, Object> readerProperties) {
    return read(resource, readerProperties, null);
  }

  public static Model read(InputStreamSource resource, Map<String, Object> readerProperties, String language) {
    Model model = ModelFactory.createDefaultModel();

    InputStream inputstream = null;
    try {
      inputstream = resource.getInputStream();
      InternalRdfErrorHandler errorHandler = new InternalRdfErrorHandler("");

      RDFReader rdfReader = getReader(model, errorHandler, readerProperties, language);
      rdfReader.read(model, inputstream, null);

      Preconditions.checkState(!errorHandler.isFailure(), errorHandler.getInfo());
    }
    catch (IOException e) {
      closeQuietly(model);
      throw new RuntimeException(e);
    }
    catch (RuntimeException e) {
      closeQuietly(model);
      throw e;
    }
    finally {
      IOUtils.closeQuietly(inputstream);
    }

    return model;
  }

  public static void readInto(Model model, Resource resource, Map<String, Object> readerProperties) {
    InputStream inputstream = null;
    try {
      inputstream = ResourceHelper.getInputStream(resource);
      InternalRdfErrorHandler errorHandler = new InternalRdfErrorHandler(resource.getDescription());

      RDFReader rdfReader = getReader(model, resource, errorHandler, readerProperties);
      rdfReader.read(model, inputstream, null);

      Preconditions.checkState(errorHandler.isFailure(), errorHandler.getInfo());
    }
    finally {
      IOUtils.closeQuietly(inputstream);
    }
  }

  private static RDFReader getReader(Model model, Resource resource, RDFErrorHandler rdfErrorHandler, Map<String, Object> readerProperties) {
    return getReader(model, rdfErrorHandler, readerProperties, getRdfSyntax(resource));
  }

  private static RDFReader getReader(Model model, RDFErrorHandler rdfErrorHandler, Map<String, Object> readerProperties, String language) {
    RDFReader rdfReader = getReaderByRdfSyntax(model, language);
    rdfReader.setErrorHandler(rdfErrorHandler);
    if (readerProperties == null) return rdfReader;

    for (String propertyName : readerProperties.keySet()) {
      rdfReader.setProperty(propertyName, readerProperties.get(propertyName));
    }
    return rdfReader;
  }

  private static RDFReader getBasicReader(Model model, Resource resource) {
    return getReaderByRdfSyntax(model, getRdfSyntax(resource));
  }

  private static RDFReader getReaderByRdfSyntax(Model model, String language) {
    try {
      return model.getReader(language);
    }
    catch (IllegalStateException ignored) {
      return model.getReader();
    }
  }

  private static String getRdfSyntax(Resource resource) {
    String extension = StringUtils.lowerCase(StringUtils.substringAfterLast(resource.getFilename(), "."));
    if ("owl".equals(extension) || "rdf".equals(extension)) {
      // .rdf is extension for application/rdf+xml and is default syntax in Jena
      // .owl is here for legacy reasons, but it is often RDF XML because it is the Protégé default
      return null;
    }
    if ("nt".equals(extension)) {
      // .nt is extension for application/n-triples
      return "N-TRIPLE";
    }
    if ("n3".equals(extension)) {
      // .n3 is extension for text/n3
      return "N3";
    }
    if ("ttl".equals(extension)) {
      // .ttl is extension for text/turtle
      return "TURTLE";
    }

    // any other extension falls back to RDF XML
    return null;
  }

  public static void write(Model model, File file) {
    write(model, FileHelper.openOutputStream(file));
  }

  public static void write(Model model, File file, String lang) {
    write(model, FileHelper.openOutputStream(file), lang);
  }

  public static void write(Model model, OutputStream out) {
    try {
      model.write(out);
    }
    finally {
      IOHelper.flushAndClose(out);
    }
  }

  public static void write(Model model, OutputStream out, String lang) {
    try {
      model.write(out, lang);
    }
    finally {
      IOHelper.flushAndClose(out);
    }
  }

  public static String toString(Model model) {
    return toString(model, "RDF/XML");
  }

  public static String toString(Model model, String language) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      model.write(out, language);

      return out.toString("UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public static ByteArrayInputStream toInputStream(Model model) {
    return toInputStream(model, "RDF/XML");
  }

  public static ByteArrayInputStream toInputStream(Model model, String language) {
    return new ByteArrayInputStream(toByteArray(model, language));
  }

  public static byte[] toByteArray(Model model, String language) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      model.write(outputStream, language);
      outputStream.flush();
      return outputStream.toByteArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    finally {
      IOUtils.closeQuietly(outputStream);
    }
  }


  public static void closeQuietly(Model... models) {
    Arrays.stream(models).filter(Objects::nonNull).filter(model -> !model.isClosed()).forEach(model -> {
      try {
        model.close();
      }
      catch (Exception e) {
        log.warn("Closing model failed.", e);
      }
    });
  }

  public static void closeQuietly(Iterable<Model> models) {
    for (Model model : models) {
      if (model == null) {
        continue;
      }

      if (model.isClosed()) {
        log.warn("Closing an already closed model.");
        continue;
      }

      try {
        model.close();
      }
      catch (Exception e) {
        log.warn("Closing model failed.", e);
      }
    }
  }

  public static Model readInto(File file, Model model) {
    return readInto(file, model, getLangByResourceName(file.getName()));
  }

  public static String getLangByResourceName(String resourceName) {
    String ext = FilenameUtils.getExtension(resourceName);
    if (ext.equalsIgnoreCase("ttl")) return "TTL";
    //TODO: add other types
    return null;
  }

  public static Model readInto(File file, Model model, String lang) {
    try (InputStream inputStream = FileHelper.openInputStream(file)) {
      return readInto(inputStream, file.getAbsolutePath(), model, lang);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Model readInto(InputStream inputStream, String streamName, Model model) {
    return readInto(inputStream, streamName, model, null);
  }

  public static Model readInto(InputStream inputStream, String streamName, Model model, String lang) {
    try {
      RDFReader reader = model.getReader(lang);
      InternalRdfErrorHandler errorHandler = new InternalRdfErrorHandler(streamName);
      reader.setErrorHandler(errorHandler);
      reader.read(model, inputStream, null);

      Preconditions.checkState(errorHandler.isFailure(), errorHandler.getInfo());
      return model;
    }
    finally {
      IOUtils.closeQuietly(inputStream);
    }
  }

  public static String stringize(RDFNode node) {
    return null == node ? "" : (String) node.visitWith(StringGetterRDFVisitor.stringGetterRDFVisitor);
  }

  public static Optional<String> stringValue(RDFNode node) {
    if (node == null) return Optional.empty();
    return Optional.ofNullable((String) node.visitWith(StringValueRDFVisitor.instance));
  }

  private static class StringGetterRDFVisitor implements RDFVisitor {
    private static final StringGetterRDFVisitor stringGetterRDFVisitor = new StringGetterRDFVisitor();

    @Override
    public Object visitBlank(org.apache.jena.rdf.model.Resource r, AnonId id) {
      return "";
    }

    @Override
    public Object visitURI(org.apache.jena.rdf.model.Resource r, String uri) {
      return uri;
    }

    @Override
    public Object visitLiteral(Literal literal) {
      return literal.getLexicalForm();
    }
  }

  private static class StringValueRDFVisitor implements RDFVisitor {
    private static final StringValueRDFVisitor instance = new StringValueRDFVisitor();

    @Override
    public Object visitBlank(org.apache.jena.rdf.model.Resource r, AnonId id) {
      return null;
    }

    @Override
    public Object visitURI(org.apache.jena.rdf.model.Resource r, String uri) {
      return uri;
    }

    @Override
    public Object visitLiteral(Literal literal) {
      return literal.getString();
    }
  }


  private static class InternalRdfErrorHandler implements RDFErrorHandler {

    private final String info;
    private boolean failure;

    private InternalRdfErrorHandler(String loadedFile) {
      info = "Load rdf file (" + loadedFile + ") problem.";
    }

    public boolean isFailure() {
      return failure;
    }

    public String getInfo() {
      return info;
    }

    @Override
    public void warning(Exception e) {
      String message = e.getMessage();
      if (null != message && message.contains("ISO-639 does not define language:")) {
        log.warn("{}: {}", info, message);
        return;
      }
      log.warn(info, e);
    }

    @Override
    public void error(Exception e) {
      failure = true;
      log.error(info, e);
    }

    @Override
    public void fatalError(Exception e) {
      failure = true;
      log.error(info, e);
    }
  }

  public static Collection<org.apache.jena.rdf.model.Resource> getResources(Iterable<Statement> statements) {
    List<org.apache.jena.rdf.model.Resource> result = new ArrayList<>();
    for (Statement stmt : statements) {
      result.add(stmt.getResource());
    }
    return result;
  }

  public static Collection<org.apache.jena.rdf.model.Resource> getResourceUris(Iterable<Statement> statements) {
    List<org.apache.jena.rdf.model.Resource> result = new ArrayList<>();
    for (Statement stmt : statements) {
      result.add(stmt.getResource());
    }
    return result;
  }

  public static List<String> toListOfUris(Collection<org.apache.jena.rdf.model.Resource> resources) {
    return resources.stream().map(RdfNode2Uri.function).collect(Collectors.toList());
  }


  public static Optional<String> getUri(RDFNode rdfNode) {
    return rdfNode != null ? Optional.ofNullable(RdfNode2Uri.function.apply(rdfNode)) : Optional.empty();
  }

  // some null pointer safe methods
  public static org.apache.jena.rdf.model.Resource getResource(Statement statement) {
    return statement != null ? statement.getResource() : null;
  }

  public static org.apache.jena.rdf.model.Resource asResource(RDFNode rdfNode) {
    return rdfNode != null ? rdfNode.asResource() : null;
  }

  public static Literal asLiteral(RDFNode rdfNode) {
    return rdfNode != null ? rdfNode.asLiteral() : null;
  }

  public static Literal getLiteral(Statement statement) {
    return statement != null ? statement.getLiteral() : null;
  }

  public static String getLexicalForm(Statement statement) {
    return getLexicalForm(getLiteral(statement));
  }

  public static String getString(Statement statement) {
    return getString(getLiteral(statement));
  }

  public static String getLanguage(Statement statement) {
    return getLanguage(getLiteral(statement));
  }

  public static String getLexicalForm(Literal literal) {
    return literal != null ? literal.getLexicalForm() : null;
  }

  public static String getString(Literal literal) {
    return literal != null ? literal.getString() : null;
  }

  public static String getLanguage(Literal literal) {
    return literal != null ? literal.getLanguage() : null;
  }

  public static void addObject(Model model, org.apache.jena.rdf.model.Resource subject, Property predicate, RDFNode object) {
    if (object != null) model.add(subject, predicate, object);
  }

  public static Map<String, String> toLanguageMappedStrings(Collection<Literal> literals) {
    Map<String, String> labelsPerLanguage = new HashMap<>();
    literals.stream().forEach(literal -> labelsPerLanguage.put(literal.getLanguage(), literal.getString()));
    return labelsPerLanguage;
  }

  public static Map<String, Set<String>> toLanguageMappedStringSet(Collection<Literal> literals) {
    Map<String, Set<String>> labelsPerLanguage = new HashMap<>();
    literals.stream().forEach(literal -> MapUtils.addValueToMappedSet(labelsPerLanguage, literal.getLanguage(), literal.getString()));
    return labelsPerLanguage;
  }

//  public static void addObject(Model model, Resource subject, Property predicate, RDFNode object) {
//    if (object != null) model.add(subject, predicate, object);
//  }

  public static org.apache.jena.rdf.model.Resource createUriResource(Model model, String uri) {
    return uri != null ? model.createResource(uri) : null;
  }

  public static Literal createLiteral(Model model, String literal, String language) {
    return StringUtils.isNotBlank(literal) ? model.createLiteral(literal, language) : null;
  }

  public static Literal createTypedLiteral(Model model, String literal, String datatype) {
    return StringUtils.isNotBlank(literal) ? model.createTypedLiteral(literal, datatype) : null;
  }

  public static Literal createDateTimeLiteral(Model model, Date date) {
    return date != null ? model.createTypedLiteral(RdfUtils.format(date), XSD.dateTime.getURI()) : null;
  }

}

