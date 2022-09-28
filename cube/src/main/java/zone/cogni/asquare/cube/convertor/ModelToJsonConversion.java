package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import org.apache.commons.collections4.MapUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.JsonRootType;
import zone.cogni.asquare.cube.convertor.json.ApplicationProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;
import zone.cogni.libs.jena.utils.JenaUtils;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.JsonType;
import static zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.ModelType;

public class ModelToJsonConversion implements BiFunction<Model, String, ObjectNode> {

  private static final Logger log = LoggerFactory.getLogger(ModelToJsonConversion.class);

  public static class Configuration {
    /**
     * JSON contains a field "rootType" which sets the main type like "Dog" without its superclasses.
     */
    public enum JsonRootType {ENABLED, DISABLED}

    /**
     * JSON contains a field "type" which can contain all types like "Dog", "Mammal" and "Animal".
     * But it can also contain only the root type "Dog" or nothing (if JsonRootType is enabled).
     */
    public enum JsonType {ALL, ROOT, DISABLED}

    /**
     * Model typically contains all types, like demo:Dog, demo:Mammal and demo:Animal,
     * or a subset, like demo:Dog and demo:Animal,
     * or the root type only like demo:Dog.
     */
    public enum ModelType {ALL, PROFILE, ROOT}


    private boolean logIssues;
    private Set<String> ignoredProperties = new HashSet<>();

    private JsonRootType jsonRootType = JsonRootType.DISABLED;
    private JsonType jsonType = JsonType.ALL;
    private ModelType modelType = ModelType.ALL;

    private boolean inverseAttributesSupported;

    private boolean contextEnabled;

    public boolean isLogIssues() {
      return logIssues;
    }

    public void setLogIssues(boolean logIssues) {
      this.logIssues = logIssues;
    }

    public Set<String> getIgnoredProperties() {
      return ignoredProperties;
    }

    public void setIgnoredProperties(Set<String> ignoredProperties) {
      this.ignoredProperties = ignoredProperties;
    }

    public boolean isIgnoredProperty(String property) {
      return this.ignoredProperties.contains(property);
    }

    public JsonRootType getJsonRootType() {
      return jsonRootType;
    }

    public boolean isJsonRootType(JsonRootType jsonRootType) {
      return this.jsonRootType == jsonRootType;
    }

    public void setJsonRootType(JsonRootType jsonRootType) {
      this.jsonRootType = jsonRootType;
    }

    public JsonType getJsonType() {
      return jsonType;
    }

    public boolean isJsonType(JsonType jsonType) {
      return this.jsonType == jsonType;
    }

    public void setJsonType(JsonType jsonType) {
      this.jsonType = jsonType;
    }

    public ModelType getModelType() {
      return modelType;
    }

    public boolean isModelType(ModelType modelType) {
      return this.modelType == modelType;
    }

    public void setModelType(ModelType modelType) {
      this.modelType = modelType;
    }

    public boolean isInverseAttributesSupported() {
      return inverseAttributesSupported;
    }

    public void setInverseAttributesSupported(boolean inverseAttributesSupported) {
      this.inverseAttributesSupported = inverseAttributesSupported;
    }

    public boolean isContextEnabled() {
      return contextEnabled;
    }

    public void setContextEnabled(boolean contextEnabled) {
      this.contextEnabled = contextEnabled;
    }

    public void check() {
      if (jsonRootType == null || jsonType == null || modelType == null)
        throw new RuntimeException("Please configure all of 'jsonRootType', 'jsonType' and 'modelType'.");

      if (jsonRootType == JsonRootType.DISABLED && jsonType == JsonType.DISABLED)
        throw new RuntimeException("Please enable at least one of 'jsonRootType' or 'jsonType'.");
    }
  }

  private final Configuration configuration;
  private final ConversionProfile conversionProfile;

  public ModelToJsonConversion(Configuration configuration, ConversionProfile conversionProfile) {
    this.configuration = configuration;
    this.conversionProfile = conversionProfile;

    configuration.check();
  }

  public ModelToJsonConversion(Configuration configuration, ApplicationProfile applicationProfile) {
    this(configuration, new ApplicationProfileToConversionProfile().apply(applicationProfile));
  }

  @Override
  public ObjectNode apply(Model model, String root) {
    Context context = new Context(this, model);
    Resource subject = ResourceFactory.createResource(root);

    if (!modelContainsRoot(model, subject))
      throw new RuntimeException("subject '" + root + "' not found in model");

    processContext(context, model);

    ObjectNode data = context.jsonRoot.putObject("data");
    processInstance(model, context, subject, data);

    if (configuration.logIssues) {
      reportMissedSubjects(context, root);
      reportUnprocessedTriples(context, root);
    }

    return context.jsonRoot;
  }

  private boolean modelContainsRoot(Model model, Resource subject) {
    return model.contains(subject, null, (RDFNode) null);
  }

  private void processContext(Context context, Model model) {
    if (!configuration.isContextEnabled()) return;

    Map<String, String> prefixes = mergePrefixMaps(
            conversionProfile.getContext().getPrefixes(),
            model.getNsPrefixMap()
    );
    if (MapUtils.isEmpty(prefixes)) return;

    ObjectNode contextNode = context.jsonRoot.putObject("context");
    ObjectNode prefixNode = contextNode.putObject("prefix");
    prefixes.forEach(prefixNode::put);
  }

  private Map<String, String> mergePrefixMaps(Map<String, String> map1, Map<String, String> map2) {
    Stream<Map.Entry<String, String>> map2FilteredStream = map2.entrySet().stream()
                                                    .filter(e -> !map1.containsValue(e.getValue()))
                                                    .map(e -> {
                                                      if (!map1.containsKey(e.getKey())) return e;
                                                      int i = 0;
                                                      while (map1.containsKey(e.getKey()+i)) i++;
                                                      return Map.entry(e.getKey()+i, e.getValue());
                                                    });


    return Stream.concat(map1.entrySet().stream(), map2FilteredStream)
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

  }

  private void reportMissedSubjects(Context context, String root) {
    Set<Resource> missedSubjects = new HashSet<>(context.subjectTypeMap.keySet());
    missedSubjects.removeAll(context.alreadyProcessedResources);

    if (!missedSubjects.isEmpty()) {
      log.warn("<{}> missed {} subjects out of {}. missed subjects: {}",
               root,
               missedSubjects.size(),
               context.subjectTypeMap.size(),
               missedSubjects);
    }
  }

  private void reportUnprocessedTriples(Context context, String root) {
    if (!log.isWarnEnabled()) return;

    Model remainingModel = context.model.difference(context.alreadyProcessedModel);

    if (!remainingModel.isEmpty()) {
      log.warn("<{}> missed {} triples \n{}",
               root,
               remainingModel.size(),
               JenaUtils.toString(remainingModel, "ttl"));
    }
  }

  /**
   * Processes a single subject with all its properties and values.
   *
   * @param model        being converted
   * @param context      of processing
   * @param subject      currently being added in JSON
   * @param instanceRoot current root where JSON is going to be manipulated
   */
  private void processInstance(@Nonnull Model model,
                               @Nonnull Context context,
                               @Nonnull Resource subject,
                               @Nonnull ObjectNode instanceRoot) {
    // only process once, at most
    if (context.alreadyProcessedResources.contains(subject)) return;

    // process instance fields
    ConversionProfile.Type type = context.subjectTypeMap.get(subject);
    setInstanceUri(subject, instanceRoot);
    setInstanceType(context, model, instanceRoot, subject, type);
    setInstanceRootType(context, model, instanceRoot, subject, type);

    // bookkeeping -> must be before processing attributes !
    context.alreadyProcessedResources.add(subject);
    getTypeStatements(subject, type)
            .forEach(context.alreadyProcessedModel::add);

    // process attributes
    type.getAttributes().forEach(attribute -> {
      processAttribute(model, context, subject, type, instanceRoot, attribute);
    });
  }

  private Stream<Statement> getTypeStatements(Resource subject, ConversionProfile.Type type) {
    return type.getRdfTypes()
               .stream()
               .map(ResourceFactory::createResource)
               .map(typeResource -> ResourceFactory.createStatement(subject, RDF.type, typeResource));
  }

  /**
   * Process a single attribute of a subject with all its values.
   *
   * @param model        being converted
   * @param context      of processing
   * @param subject      currently being added in JSON
   * @param type         of subject
   * @param instanceRoot current root where JSON is going to be manipulated
   * @param attribute    currently being added in JSON
   */
  private void processAttribute(@Nonnull Model model,
                                @Nonnull Context context,
                                @Nonnull Resource subject,
                                @Nonnull ConversionProfile.Type type,
                                @Nonnull ObjectNode instanceRoot,
                                @Nonnull ConversionProfile.Attribute attribute) {
    // if no values then return
    List<RDFNode> values = getValues(context, subject, attribute);
    if (values.isEmpty()) return;

    // log issue if we find inverses and inverse support is disabled!
    if (attribute.isInverse() && !configuration.inverseAttributesSupported) {
      String valuesAsString = values.stream().map(RDFNode::toString).collect(Collectors.joining(", "));
      log.error("inverse properties disabled and uri '{}' has inverse attribute '{}' with values: {}",
                subject.getURI(), attribute.getAttributeId(), valuesAsString);
      return;
    }

    // add attributes values to JSON
    setJsonAttribute(model, instanceRoot, type, attribute, values);

    // add includes to JSON (here or in setJsonAttribute?)
    if (attribute.isReference()) {
      values.forEach(value -> {
        createAndIncludeInstance(model, context, type, attribute, value);
      });
    }
  }

  /**
   * Returns list of <code>RDFNode</code> which are values of <code>subject</code> and <code>attribute</code>.
   * Takes into account whether attribute is <code>inverse</code> or not.
   *
   * @param context   of processing
   * @param subject   currently being added in JSON
   * @param attribute currently being added in JSON
   * @return list of <code>RDFNode</code> which are values of <code>subject</code> and <code>attribute</code>
   */
  private List<RDFNode> getValues(Context context,
                                  Resource subject,
                                  ConversionProfile.Attribute attribute) {
    StmtIterator iterator = context.model.listStatements(attribute.isInverse() ? null : subject,
                                                         attribute.getProperty(),
                                                         attribute.isInverse() ? subject : null);

    List<RDFNode> result = new ArrayList<>();
    while (iterator.hasNext()) {
      Statement statement = iterator.nextStatement();

      context.alreadyProcessedModel.add(statement);
      result.add(attribute.isInverse() ? statement.getSubject() : statement.getObject());
    }

    return result;
  }

  /**
   * Adds values to <code>instanceRoot</code> JSON.
   *
   * @param instanceRoot current root where JSON is going to be manipulated
   * @param type         of subject
   * @param attribute    currently being added in JSON
   * @param values       to add to <code>instanceRoot</code>
   */
  private void setJsonAttribute(@Nonnull Model model,
                                @Nonnull ObjectNode instanceRoot,
                                @Nonnull ConversionProfile.Type type,
                                @Nonnull ConversionProfile.Attribute attribute,
                                @Nonnull List<RDFNode> values) {
    if (attribute.isReference()) {
      addReferences(model, instanceRoot, attribute, values);
      return;
    }
    else if (attribute.isAttribute()) {
      addAttributes(model, instanceRoot, attribute, values);
      return;
    }

    throw new RuntimeException("should not be able to get here:" +
                               " type " + type.getRootClassId() + " and property " + attribute.getAttributeId());
  }

  /**
   * Adds references to <code>instanceRoot</code> JSON.
   *
   * @param model        being converted
   * @param instanceRoot current root where JSON is going to be manipulated
   * @param attribute    currently being added in JSON
   * @param values       to add to <code>instanceRoot</code>
   */
  private void addReferences(@Nonnull Model model,
                             @Nonnull ObjectNode instanceRoot,
                             @Nonnull ConversionProfile.Attribute attribute,
                             @Nonnull List<RDFNode> values) {
    ObjectNode referencesNode = getOrCreateObjectNode(instanceRoot, "references");

    if (attribute.isList()) {
      // list case
      ArrayNode arrayNode = instanceRoot.arrayNode();

      String configuredAttribute = configureString(model, attribute.getAttributeId());
      referencesNode.set(configuredAttribute, arrayNode);

      values.forEach(v -> arrayNode.add(referencesNode.textNode(v.asResource().getURI())));
    }
    else {
      // single case
      if (values.size() != 1) {
        throw new RuntimeException("attribute " + attribute.getAttributeId() + " has values " + values);
      }

      TextNode singleReference = referencesNode.textNode(values.get(0).asResource().getURI());

      String configuredAttribute = configureString(model, attribute.getAttributeId());
      referencesNode.set(configuredAttribute, singleReference);
    }
  }

  /**
   * Adds attributes to <code>instanceRoot</code> JSON.
   *
   * @param model        being converted
   * @param instanceRoot current root where JSON is going to be manipulated
   * @param attribute    currently being added in JSON
   * @param values       to add to <code>instanceRoot</code>
   */
  private void addAttributes(@Nonnull Model model,
                             @Nonnull ObjectNode instanceRoot,
                             @Nonnull ConversionProfile.Attribute attribute,
                             @Nonnull List<RDFNode> values) {
    if (values.isEmpty()) return;

    ObjectNode attributesNode = getOrCreateObjectNode(instanceRoot, "attributes");

    String configuredAttributeId = configureString(model, attribute.getAttributeId());

    // single but with multiple languages
    if (attribute.isSingle() && values.size() > 1) {
      ObjectNode attributeNode = getOrCreateObjectNode(attributesNode, configuredAttributeId);

      Set<String> languages = new HashSet<>();
      // assume language nodes!
      values.forEach(languageRdfNode -> {
        Preconditions.checkState(languageRdfNode.isLiteral(), "Node is not a literal: " + attribute.getAttributeId());
        Preconditions.checkState(languageRdfNode.asLiteral().getDatatype()
                                                .equals(RDFLangString.rdfLangString), "Node is not a lang literal: " + attribute.getAttributeId());

        // check for duplicates !
        String language = languageRdfNode.asLiteral().getLanguage();
        Preconditions.checkState(!languages.contains(language), "More than 1 lang literals for the same language: " + attribute.getAttributeId());

        languages.add(language);

        String text = languageRdfNode.asLiteral().getString();
        ObjectNode languageNode = getOrCreateObjectNode(attributeNode, "rdf:langString");
        addToJsonAsSingle(languageNode, language, languageNode.textNode(text));
      });
      return;
    }

    // single
    if (attribute.isSingle()) {
      if (values.size() != 1) {
        throw new RuntimeException("attribute " + attribute.getAttributeId() + " has " + values
                .size() + " values: " + values);
      }

      RDFNode rdfNode = values.get(0);
      ObjectNode attributeNode = getOrCreateObjectNode(attributesNode, configuredAttributeId);

      if (rdfNode.isAnon()) throw new RuntimeException("blank nodes are not supported");

      if (rdfNode.isURIResource()) {
        attributeNode.set("rdfs:Resource", attributeNode.textNode(rdfNode.asResource().getURI()));
        return;
      }

      // literal
      Literal literal = rdfNode.asLiteral();
      RDFDatatype datatype = literal.getDatatype();

      if (RDFLangString.rdfLangString.equals(datatype)) {
        String language = literal.getLanguage();
        ObjectNode languageNode = getOrCreateObjectNode(attributeNode, "rdf:langString");
        addToJsonAsSingle(languageNode, language, languageNode.textNode(literal.getString()));
        return;
      }
      if (XSDDatatype.XSDstring.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:string", getTextNode(literal.getString()));
        return;
      }
      if (XSDDatatype.XSDboolean.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:boolean", getBooleanNode(literal.getBoolean()));
        return;
      }
      if (XSDDatatype.XSDdate.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:date", getTextNode(literalToDate(literal)));
        return;
      }
      if (XSDDatatype.XSDdateTime.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:dateTime", getTextNode(literalToDateTime(literal)));
        return;
      }
      if (XSDDatatype.XSDint.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:int", getNumberNode(literal.getInt()));
        return;
      }
      if (XSDDatatype.XSDinteger.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:integer", getNumberNode(literal.getInt()));
        return;
      }
      if (XSDDatatype.XSDlong.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:long", getNumberNode(literal.getLong()));
        return;
      }
      if (XSDDatatype.XSDfloat.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:float", getNumberNode(literal.getFloat()));
        return;
      }
      if (XSDDatatype.XSDdouble.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:double", getNumberNode(literal.getDouble()));
        return;
      }
      if (XSDDatatype.XSDanyURI.equals(datatype)) {
        addToJsonAsSingle(attributeNode, "xsd:anyURI", getTextNode(literal.getLexicalForm()));
        return;
      }
      if (datatype != null) {
        addToJsonAsSingle(attributeNode, datatype.getURI(), getTextNode(literal.getLexicalForm()));
        return;
      }

      throw new RuntimeException("datatype not found");
    }

    // list
    values.forEach(rdfNode -> {
      if (rdfNode.isAnon()) throw new RuntimeException("blank nodes are not supported");

      String attributeId = configureString(model, attribute.getAttributeId());
      ObjectNode attributeNode = getOrCreateObjectNode(attributesNode, attributeId);

      if (rdfNode.isURIResource()) {
        addToArrayNode(attributeNode, "rdfs:Resource", getTextNode(rdfNode.asResource().getURI()));
        return;
      }

      // literal
      Literal literal = rdfNode.asLiteral();
      RDFDatatype datatype = literal.getDatatype();

      if (RDFLangString.rdfLangString.equals(datatype)) {
        ObjectNode langStringNode = getOrCreateObjectNode(attributeNode, "rdf:langString");

        String language = literal.getLanguage();
        addToArrayNode(langStringNode, language, getTextNode(literal.getString()));
        return;
      }
      if (XSDDatatype.XSDstring.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:string", getTextNode(literal.getString()));
        return;
      }
      if (XSDDatatype.XSDboolean.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:boolean", getBooleanNode(literal.getBoolean()));
        return;
      }
      if (XSDDatatype.XSDdate.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:date", getTextNode(literalToDate(literal)));
        return;
      }
      if (XSDDatatype.XSDdateTime.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:dateTime", getTextNode(literalToDateTime(literal)));
        return;
      }
      if (XSDDatatype.XSDint.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:int", getNumberNode(literal.getInt()));
        return;
      }
      if (XSDDatatype.XSDlong.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:long", getNumberNode(literal.getLong()));
        return;
      }
      if (XSDDatatype.XSDfloat.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:float", getNumberNode(literal.getFloat()));
        return;
      }
      if (XSDDatatype.XSDdouble.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:double", getNumberNode(literal.getDouble()));
        return;
      }
      if (XSDDatatype.XSDanyURI.equals(datatype)) {
        addToArrayNode(attributeNode, "xsd:anyURI", getTextNode(literal.getLexicalForm()));
        return;
      }
      if (datatype != null) {
        addToArrayNode(attributeNode, datatype.getURI(), getTextNode(literal.getLexicalForm()));
        return;
      }

      throw new RuntimeException("datatype not found");
    });
  }

  /**
   * Adds <code>values</code> which are typed to <code>included</code> section of JSON.
   * Recursively
   *
   * @param model   being converted
   * @param context of processing
   * @param value   to add to <code>included</code> JSON part
   */
  private void createAndIncludeInstance(Model model, Context context,
                                        ConversionProfile.Type type,
                                        ConversionProfile.Attribute attribute,
                                        RDFNode value) {
    if (!value.isResource()) {
      log.error("Type '{}' and attribute '{}' must contain a resource, found '{}'",
                type.getRootClassId(), attribute.getAttributeId(), value);
    }

    if (!context.subjectTypeMap.containsKey(value.asResource())) {
      log.error("Type '{}' and attribute '{}' must contain a typed resource, found a plain resource '{}'",
                type.getRootClassId(), attribute.getAttributeId(), value);
    }

    // already processed
    if (context.alreadyProcessedResources.contains(value.asResource())) return;

    // process and add as included
    ObjectNode linkedInstance = JsonNodeFactory.instance.objectNode();
    processInstance(model, context, value.asResource(), linkedInstance);
    addToArrayNode(context.jsonRoot, "included", linkedInstance);
  }

  private void setInstanceUri(Resource subject, ObjectNode instanceRoot) {
    instanceRoot.put("uri", subject.getURI());
  }

  private void setInstanceType(@Nonnull Context context,
                               @Nonnull Model model,
                               @Nonnull ObjectNode instanceRoot,
                               @Nonnull Resource instance,
                               ConversionProfile.Type type) {
    if (configuration.isJsonType(JsonType.DISABLED))
      return;

    if (type == null) {
      throw new RuntimeException("cannot find type for instance '" + instance.getURI() + "'" +
                                 ": found types '" + context.subjectTypeMap.get(instance) + "'.");
    }

    if (configuration.isJsonType(JsonType.ROOT)) {
      instanceRoot.put("type", configureString(model, type.getRootClassId()));
      return;
    }

    if (configuration.isJsonType(JsonType.ALL)) {
      Collection<String> classIds = type.getClassIds();
      if (classIds.size() == 1) {
        String typeValue = classIds.stream().findFirst().get();
        instanceRoot.put("type", configureString(model, typeValue));
      }
      else {
        ArrayNode typeArray = getOrCreateArrayNode(instanceRoot, "type");
        classIds.forEach(classId -> {
          typeArray.add(typeArray.textNode(configureString(model, classId)));
        });
      }
      return;
    }

    throw new RuntimeException("should never get here");
  }

  private String configureString(Model model, String string) {
    if (!configuration.isContextEnabled()) return string;

    return model.shortForm(string);
  }

  private void setInstanceRootType(@Nonnull Context context,
                                   @Nonnull Model model,
                                   @Nonnull ObjectNode instanceRoot,
                                   @Nonnull Resource instance,
                                   ConversionProfile.Type type) {
    if (configuration.isJsonRootType(JsonRootType.DISABLED))
      return;

    if (type == null) {
      throw new RuntimeException("cannot find type for instance '" + instance.getURI() + "'" +
                                 ": found types '" + context.subjectTypeMap.get(instance) + "'.");
    }

    if (configuration.isJsonRootType(JsonRootType.ENABLED)) {
      instanceRoot.put("rootType", configureString(model, type.getRootClassId()));
      return;
    }

    throw new RuntimeException("should never get here");
  }

  private ObjectNode getOrCreateObjectNode(ObjectNode instanceRoot, String name) {
    if (!instanceRoot.has(name)) {
      instanceRoot.set(name, JsonNodeFactory.instance.objectNode());
    }

    return (ObjectNode) instanceRoot.get(name);
  }

  private void addToArrayNode(ObjectNode instanceRoot, String name, JsonNode value) {
    ArrayNode arrayNode = getOrCreateArrayNode(instanceRoot, name);
    arrayNode.add(value);
  }

  private ArrayNode getOrCreateArrayNode(ObjectNode instanceRoot, String name) {
    if (!instanceRoot.has(name)) {
      instanceRoot.set(name, JsonNodeFactory.instance.arrayNode());
    }

    return (ArrayNode) instanceRoot.get(name);
  }

  private void addToJsonAsSingle(ObjectNode jsonNode, String attribute, JsonNode value) {
    jsonNode.set(attribute, value);
  }

  private String literalToDate(Literal literal) {
    return LocalDate.parse(literal.getLexicalForm()).toString();
  }

  /**
   * Tested on formats like 2016-01-01T00:00:00Z , 2021-02-11T08:19:21.489344Z and 2022-08-08T08:08:08.888+02:00
   *
   * @param literal dateTime literal
   * @return dateTime string as is but after format validation
   */
  private String literalToDateTime(Literal literal) {
    ZonedDateTime zonedDateTime = ZonedDateTime.parse(literal.getLexicalForm());
    return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private TextNode getTextNode(String value) {
    return JsonNodeFactory.instance.textNode(value);
  }

  private BooleanNode getBooleanNode(boolean value) {
    return JsonNodeFactory.instance.booleanNode(value);
  }

  private NumericNode getNumberNode(int value) {
    return JsonNodeFactory.instance.numberNode(value);
  }

  private NumericNode getNumberNode(long value) {
    return JsonNodeFactory.instance.numberNode(value);
  }

  private NumericNode getNumberNode(float value) {
    return JsonNodeFactory.instance.numberNode(value);
  }

  private NumericNode getNumberNode(double value) {
    return JsonNodeFactory.instance.numberNode(value);
  }

  private static class Context {

    private final ModelToJsonConversion parent;
    private final Model model;
    private final ObjectNode jsonRoot;
    private final Set<Resource> alreadyProcessedResources;
    private final Model alreadyProcessedModel;
    private final Map<Resource, ConversionProfile.Type> subjectTypeMap;

    public Context(ModelToJsonConversion parent, Model model) {
      this.parent = parent;
      this.model = model;

      subjectTypeMap = calculateSubjectTypeMap(model);
      alreadyProcessedResources = new HashSet<>();
      alreadyProcessedModel = ModelFactory.createDefaultModel();
      jsonRoot = JsonNodeFactory.instance.objectNode();
    }

    private Map<Resource, ConversionProfile.Type> calculateSubjectTypeMap(Model model) {
      Map<Resource, Set<String>> rdfTypesMap = calculateSubjectRdfTypesMap(model);

      Map<Resource, ConversionProfile.Type> result = new HashMap<>();
      rdfTypesMap.forEach((resource, rdfTypes) -> {
        result.put(resource, calculateType(rdfTypes));
      });
      return result;
    }

    private Map<Resource, Set<String>> calculateSubjectRdfTypesMap(Model model) {
      Map<Resource, Set<String>> subjectTypeMap = new HashMap<>();

      model.listStatements(null, RDF.type, (RDFNode) null)
           .forEachRemaining(statement -> {
             Resource subject = statement.getSubject();
             if (!subjectTypeMap.containsKey(subject)) {
               subjectTypeMap.put(subject, new HashSet<>());
             }

             String type = statement.getObject().asResource().getURI();
             subjectTypeMap.get(subject).add(type);
           });

      return subjectTypeMap;
    }

    private ConversionProfile.Type calculateType(Set<String> rdfTypes) {
      if (parent.configuration.isModelType(ModelType.ROOT)) {
        if (rdfTypes.size() != 1) throw new RuntimeException("expecting exactly one type, found " + rdfTypes);

        String rdfType = rdfTypes.stream().findFirst().get();
        return parent.conversionProfile.getTypeFromExpandedRdfType(rdfType);
      }

      if (parent.configuration.isModelType(ModelType.PROFILE)) {
        return parent.conversionProfile.getBestMatchingTypeFromRdfTypes(rdfTypes);
      }

      if (parent.configuration.isModelType(ModelType.ALL)) {
        return parent.conversionProfile.getTypeFromRdfTypes(rdfTypes);
      }

      throw new RuntimeException("should never get here");
    }

  }
}
