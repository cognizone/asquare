package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.JsonRootType;
import zone.cogni.asquare.cube.convertor.json.ApplicationProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

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

    Map<Resource, Set<String>> subjectTypeMap = getSubjectTypeMap(model);
    Set<Resource> alreadyProcessedResources = new HashSet<>();

    Resource subject = ResourceFactory.createResource(root);

    ObjectNode jsonRoot = JsonNodeFactory.instance.objectNode();
    ObjectNode data = jsonRoot.putObject("data");

    processInstance(model, alreadyProcessedResources, subjectTypeMap, subject, jsonRoot, data);

    Set<Resource> missedSubjects = subjectTypeMap.keySet();
    missedSubjects.removeAll(alreadyProcessedResources);

    if (missedSubjects.size() > 1) {
      log.info("missed {} subjects out of {}. missed subjects: {}",
               missedSubjects.size(),
               subjectTypeMap.size(),
               missedSubjects);
    }

    return jsonRoot;
  }

  private Map<Resource, Set<String>> getSubjectTypeMap(Model model) {
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

  private boolean processInstance(Model model,
                                  Set<Resource> alreadyProcessed,
                                  Map<Resource, Set<String>> subjectTypeMap,
                                  Resource subject,
                                  ObjectNode root,
                                  ObjectNode instanceRoot) {
    // only process once
    if (alreadyProcessed.contains(subject)) return false;
    alreadyProcessed.add(subject);

    ConversionProfile.Type type = getType(model, subject);

    setInstanceUri(subject, instanceRoot);
    setInstanceType(instanceRoot, type);
    setInstanceRootType(instanceRoot, type);

    getPropertyObjectMap(model, subject)
      .forEach((property, values) -> {
        setInstanceProperty(subjectTypeMap, instanceRoot, type, property, values);

        values.forEach(rdfNode -> {
          if (!rdfNode.isResource()) return;
          if (!subjectTypeMap.containsKey(rdfNode.asResource())) return;

          // do not process values if they are missing or rdfs:Resource
          ConversionProfile.Attribute attribute = type.getByAttributeUri(property);
          if (attribute == null || attribute.isAttribute()) return;

          ObjectNode linkedInstance = JsonNodeFactory.instance.objectNode();
          boolean processed = processInstance(model, alreadyProcessed, subjectTypeMap, rdfNode
            .asResource(), root, linkedInstance);
          if (processed) {
            addToArrayNode(root, "included", linkedInstance);
          }
        });
      });

    return true;
  }

  private ConversionProfile.Type getType(Model model, Resource subject) {
    if (configuration.isModelType(ModelType.ROOT)) {
      Set<String> rdfTypes = getRdfTypes(model, subject);
      if (rdfTypes.size() != 1) throw new RuntimeException("expecting exactly one type, found " + rdfTypes);

      String rdfType = rdfTypes.stream().findFirst().get();
      return conversionProfile.getTypeFromRdfType(rdfType);
    }

    if (configuration.isModelType(ModelType.PROFILE)) {
      Set<String> rdfTypes = getRdfTypes(model, subject);
      return conversionProfile.getBestMatchingTypeFromRdfTypes(rdfTypes);
    }

    if (configuration.isModelType(ModelType.ALL)) {
      Set<String> rdfTypes = getRdfTypes(model, subject);
      return conversionProfile.getTypeFromRdfTypes(rdfTypes);
    }

    throw new RuntimeException("should never get here");
  }

  @Nonnull
  private Set<String> getRdfTypes(Model model, Resource subject) {
    Set<String> rdfTypes = new HashSet<>();

    model.listObjectsOfProperty(subject, RDF.type)
         .forEachRemaining(rdfNode -> rdfTypes.add(rdfNode.asResource().getURI()));

    return rdfTypes;
  }

  private void setInstanceUri(Resource subject, ObjectNode instanceRoot) {
    instanceRoot.put("uri", subject.getURI());
  }

  private void setInstanceType(ObjectNode instanceRoot, ConversionProfile.Type type) {
    if (configuration.isJsonType(JsonType.DISABLED))
      return;

    if (configuration.isJsonType(JsonType.ROOT)) {
      instanceRoot.put("type", type.getRootClassId());
      return;
    }

    if (configuration.isJsonType(JsonType.ALL)) {
      Collection<String> classIds = type.getClassIds();
      if (classIds.size() == 1) {
        String typeValue = classIds.stream().findFirst().get();
        instanceRoot.put("type", typeValue);
      }
      else {
        ArrayNode typeArray = getOrCreateArrayNode(instanceRoot, "type");
        classIds.forEach(classId -> {
          typeArray.add(typeArray.textNode(classId));
        });
      }
      return;
    }

    throw new RuntimeException("should never get here");
  }

  private void setInstanceRootType(ObjectNode instanceRoot, ConversionProfile.Type type) {
    if (configuration.isJsonRootType(JsonRootType.DISABLED))
      return;

    if (configuration.isJsonRootType(JsonRootType.ENABLED)) {
      instanceRoot.put("rootType", type.getRootClassId());
      return;
    }

    throw new RuntimeException("should never get here");
  }

  private Map<String, List<RDFNode>> getPropertyObjectMap(Model model, Resource resource) {
    Map<String, List<RDFNode>> propertyObjectMap = new HashMap<>();

    model.listStatements(resource, null, (RDFNode) null)
         .forEachRemaining(statement -> {
           String property = statement.getPredicate().getURI();
           if (!propertyObjectMap.containsKey(property)) {
             propertyObjectMap.put(property, new ArrayList<>());
           }

           RDFNode object = statement.getObject();
           propertyObjectMap.get(property).add(object);
         });

    if (configuration.isInverseAttributesSupported()) {
      model.listStatements(null, null, resource)
           .forEachRemaining(statement -> {
             String property = statement.getPredicate().getURI();
             if (!propertyObjectMap.containsKey(property)) {
               propertyObjectMap.put(property, new ArrayList<>());
             }

             RDFNode subject = statement.getSubject();
             propertyObjectMap.get(property).add(subject);
           });
    }

    return propertyObjectMap;
  }

  private void setInstanceProperty(Map<Resource, Set<String>> subjectTypeMap,
                                   ObjectNode instanceRoot,
                                   ConversionProfile.Type type,
                                   String property,
                                   List<RDFNode> values) {
    ConversionProfile.Attribute attribute = type.getByAttributeUri(property);

    // special case: attribute not found in application profile!!
    if (attribute == null) {
      addUnknownProperty(subjectTypeMap, instanceRoot, type, property, values);
      return;
    }

    // normal case
    if (attribute.isReference()) {
      addReferences(instanceRoot, attribute, values);
      return;
    }
    else if (attribute.isAttribute()) {
      addAttributes(instanceRoot, attribute, values);
      return;
    }

    throw new RuntimeException("should not be able to get here:" +
                               " type " + type.getRootClassId() + " and property " + attribute.getAttributeId());
  }

  private void addUnknownProperty(Map<Resource, Set<String>> subjectTypeMap,
                                  ObjectNode instanceRoot,
                                  ConversionProfile.Type type,
                                  String property,
                                  List<RDFNode> values) {
    // ignore RDF.type
    if (property.equals(RDF.type.getURI())) return;
    if (configuration.isIgnoredProperty(property)) return;

    if (configuration.logIssues) {
      log.warn("cannot find attribute with property {} for type {}", property, type.getRootClassId());
      return;
    }

    // TODO there can be some refinement here but there will always be issues
    if (isUnknownReference(subjectTypeMap, values)) {
      processUnknownReference(instanceRoot, property, values);
    }
    else if (isUnknownAttribute(subjectTypeMap, values)) {
      throw new RuntimeException("processUnknownAttribute not yet supported yet: " + property + " on type " + type.getRootClassId() + " for object " + instanceRoot.get("uri"));
    }
    else {
      throw new RuntimeException("found a mix of attributes and references" +
                                 " for property '" + property + "'" +
                                 " and values " + values);
    }
  }

  private boolean isUnknownReference(Map<Resource, Set<String>> subjectTypeMap, List<RDFNode> values) {
    return values.stream()
                 .allMatch(value -> value.isURIResource() && subjectTypeMap.containsKey(value.asResource()));
  }


  private void processUnknownReference(ObjectNode instanceRoot, String property, List<RDFNode> values) {
    ObjectNode referencesNode = getOrCreateObjectNode(instanceRoot, "references");

    ArrayNode arrayNode = referencesNode.arrayNode();
    referencesNode.set(property, arrayNode);

    values.forEach(v -> arrayNode.add(referencesNode.textNode(v.asResource().getURI())));
  }

  private boolean isUnknownAttribute(Map<Resource, Set<String>> subjectTypeMap, List<RDFNode> values) {
    return values.stream()
                 .allMatch(value -> value.isLiteral() || !subjectTypeMap.containsKey(value.asResource()));
  }


  private void addReferences(ObjectNode instanceRoot, ConversionProfile.Attribute attribute, List<RDFNode> values) {
    ObjectNode referencesNode = getOrCreateObjectNode(instanceRoot, "references");

    if (attribute.isList()) {
      // list case
      ArrayNode arrayNode = instanceRoot.arrayNode();
      referencesNode.set(attribute.getAttributeId(), arrayNode);

      values.forEach(v -> arrayNode.add(referencesNode.textNode(v.asResource().getURI())));
    }
    else {
      // single case
      if (values.size() != 1) {
        throw new RuntimeException("attribute " + attribute.getAttributeId() + " has values " + values);
      }

      TextNode singleReference = referencesNode.textNode(values.get(0).asResource().getURI());

      referencesNode.set(attribute.getAttributeId(), singleReference);
    }
  }

  private void addAttributes(ObjectNode instanceRoot, ConversionProfile.Attribute attribute, List<RDFNode> values) {
    if (values.isEmpty()) return;

    ObjectNode attributesNode = getOrCreateObjectNode(instanceRoot, "attributes");

    // single and but with multiple languages
    if (attribute.isSingle() && values.size() > 1) {
      ObjectNode attributeNode = getOrCreateObjectNode(attributesNode, attribute.getAttributeId());

      Set<String> languages = new HashSet<>();
      // assume language nodes!
      values.forEach(languageRdfNode -> {
        Preconditions.checkState(languageRdfNode.isLiteral(), "Node is not a literal: " + attribute.getAttributeId());
        Preconditions.checkState(languageRdfNode.asLiteral().getDatatype().equals(RDFLangString.rdfLangString), "Node is not a lang literal: " + attribute.getAttributeId());

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
      ObjectNode attributeNode = getOrCreateObjectNode(attributesNode, attribute.getAttributeId());

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

      String attributeId = attribute.getAttributeId();
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
    return date2string(ISODateTimeFormat.dateTimeParser().parseDateTime(literal.getLexicalForm()));
  }

  private String date2string(DateTime date) {
    return date == null ? null : date.toString(ISODateTimeFormat.date());
  }

  private String literalToDateTime(Literal literal) {
    return dateTime2string(ISODateTimeFormat.dateTimeParser().parseDateTime(literal.getLexicalForm()));
  }

  private String dateTime2string(DateTime dateTime) {
    return dateTime == null ? null : dateTime.withZone(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());
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
}
