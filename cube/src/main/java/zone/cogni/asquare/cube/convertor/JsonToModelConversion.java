package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.ModelType;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JsonToModelConversion implements Function<JsonNode, Model> {

  private static final Logger log = LoggerFactory.getLogger(JsonToModelConversion.class);

  private final TypeMapper datatypeMapper = TypeMapper.getInstance();

  private final PrefixCcService prefixCcService;
  private final ConversionProfile conversionProfile;
  private final ModelToJsonConversion.Configuration configuration;

  @Deprecated
  public JsonToModelConversion(PrefixCcService prefixCcService, ConversionProfile conversionProfile) {
    this(prefixCcService, conversionProfile, new ModelToJsonConversion.Configuration());
    log.warn(
            "\n\t---------------------------------------------------------------------------------------------" +
            "\n\t  Please switch to JsonToModelConversion(PrefixCcService, ConversionProfile, Configuration)" +
            "\n\t  also make sure Configuration is same in JsonToModelConversion and ModelToJsonConversion" +
            "\n\t---------------------------------------------------------------------------------------------"
    );
  }

  public JsonToModelConversion(PrefixCcService prefixCcService,
                               ConversionProfile conversionProfile,
                               ModelToJsonConversion.Configuration configuration) {
    this.prefixCcService = prefixCcService;
    this.conversionProfile = conversionProfile;
    this.configuration = configuration;
  }

  @Override
  public Model apply(JsonNode root) {
    Model model = ModelFactory.createDefaultModel();

    calculatePrefixes(root, model);

    JsonNode dataNode = root.get("data");

    processSingleNode(model, dataNode);
    addIncluded(model, root);

    return model;
  }

  private void calculatePrefixes(JsonNode root, Model model) {
    if (!root.has("context")) return;
    JsonNode contextNode = root.get("context");

    if (!contextNode.has("prefix")) return;
    JsonNode prefixesNode = contextNode.get("prefix");

    if (!prefixesNode.isObject()) return;
    ObjectNode prefixesObject = (ObjectNode) prefixesNode;

    prefixesObject.fields().forEachRemaining(fieldEntry -> {
      String prefix = fieldEntry.getKey();
      JsonNode value = fieldEntry.getValue();
      if (!value.isTextual())
        throw new RuntimeException("prefixes must by string pairs, problem found for prefix '" + prefix + "'");

      String namespace = value.textValue();
      model.setNsPrefix(prefix, namespace);
    });

    // TODO what with profile... currently missing prefixes??
//    model.setNsPrefixes(conversionProfile.getContext().getPrefixes());
  }

  private void addIncluded(Model model, JsonNode root) {
    if (!root.has("included")) return;

    ArrayNode included = (ArrayNode) root.get("included");
    included
            .forEach(include -> processSingleNode(model, include));
  }

  private void processSingleNode(Model model, JsonNode node) {
    Resource uri = getUri(node);

    ConversionProfile.Type type = getType(model, node);

    addTypes(model, uri, type);
    addAttributes(model, type, uri, node);
    addReferences(model, type, uri, node);
  }

  private void addTypes(Model model, Resource uri, ConversionProfile.Type type) {
    if (configuration.isModelType(ModelType.ROOT)) {
      model.add(uri, RDF.type, ResourceFactory.createResource(type.getExpandedRootRdfType()));
      return;
    }

    if (configuration.isModelType(ModelType.PROFILE)
        || configuration.isModelType(ModelType.ALL)) {
      type.getExpandedRdfTypes()
          .forEach(rdfType -> model.add(uri, RDF.type, ResourceFactory.createResource(rdfType)));
      return;
    }

    throw new RuntimeException("you should never get here");
  }


  private void addAttributes(Model model,
                             ConversionProfile.Type type,
                             Resource uri,
                             JsonNode root) {
    if (!root.has("attributes")) return;

    root.get("attributes")
        .fields()
        .forEachRemaining(attributeNames -> {
          String attributeName = expandPrefix(model, attributeNames.getKey());
          ConversionProfile.Attribute attribute = type.getByAttributeId(attributeName);

          if (attribute == null)
            throw new RuntimeException("attribute '" + attributeName + " not found for type '" + type.getRootClassId() + "'");

          addAttribute(model, attribute, uri, attributeNames.getValue());
        });
  }

  private void addAttribute(Model model,
                            ConversionProfile.Attribute attribute,
                            Resource uri,
                            JsonNode attributeValueNode) {
    attributeValueNode
            .fields()
            .forEachRemaining(typeField -> {
              String attributeType = typeField.getKey();
              JsonNode attributeValue = typeField.getValue();

              if (attributeType.equals("rdf:langString") || attributeType.equals(RDF.langString.getURI())) {
                addLanguages(model, attribute, uri, attributeValue);
                return;
              }

              if (attributeValue.isArray()) {
                attributeValue
                        .forEach(element -> {
                          RDFNode object = getObject(attributeType, element);
                          if (object != null) model.add(uri, attribute.getProperty(), object);
                        });
              }
              else {
                RDFNode object = getObject(attributeType, attributeValue);
                if (object != null) model.add(uri, attribute.getProperty(), object);
              }
            });
  }

  private void addReferences(Model model, ConversionProfile.Type type, Resource uri, JsonNode root) {
    if (!root.has("references")) return;

    root.get("references")
        .fields()
        .forEachRemaining(attributeNames -> {
          String attributeName = attributeNames.getKey();
          ConversionProfile.Attribute attribute = type.getByAttributeId(attributeName);
          if (attribute == null) {
            throw new RuntimeException("Attribute '" + attributeName + "' not found for type " + type.getRootClassId());
          }

          boolean isInverse = attribute.isInverse();

          JsonNode value = attributeNames.getValue();
          if (value.isArray()) {
            value.forEach(element -> addReference(model, uri, attribute.getProperty(), ResourceFactory.createResource(element.textValue()), isInverse));
          }
          else {
            addReference(model, uri, attribute.getProperty(), ResourceFactory.createResource(value.textValue()), isInverse);
          }
        });
  }

  private void addReference(Model model, Resource subject, Property property, Resource object, boolean isInverse) {
    if (isInverse) model.add(object, property, subject);
    else model.add(subject, property, object);
  }

  private Resource getUri(JsonNode dataNode) {
    return ResourceFactory.createResource(dataNode.get("uri").textValue());
  }

  private ConversionProfile.Type getType(Model model, JsonNode dataNode) {
    return configuration.isModelType(ModelType.ROOT)
           ? getTypeUsingRootCase(model, dataNode)
           : getTypeUsingNormalCase(model, dataNode);
  }

  private ConversionProfile.Type getTypeUsingRootCase(Model model, JsonNode dataNode) {
    // root
    if (dataNode.has("rootType")) {
      JsonNode rootType = dataNode.get("rootType");
      String classId = expandPrefix(model, rootType.textValue());
      return conversionProfile.getTypeFromClassId(classId);
    }

    // single solution
    JsonNode type = dataNode.get("type");

    if (!dataNode.isArray()) {
      String classId = expandPrefix(model, type.textValue());
      ConversionProfile.Type result = conversionProfile.getTypeFromClassId(classId);
      if (result != null) return result;
    }

    // array solution
    Set<String> types = asStream(type, JsonNode::textValue).collect(Collectors.toSet());
    if (types.size() == 1) {
      String classId = expandPrefix(model, types.stream().findFirst().get());
      ConversionProfile.Type result = conversionProfile.getTypeFromClassId(classId);
      if (result != null) return result;
    }

    throw new RuntimeException("unable to determine type for node: \n" + dataNode.toPrettyString());
  }

  private ConversionProfile.Type getTypeUsingNormalCase(Model model, JsonNode dataNode) {
    JsonNode type = dataNode.get("type");

    // single solution
    if (!dataNode.isArray()) {
      String classId = expandPrefix(model, type.textValue());
      ConversionProfile.Type result = conversionProfile.getTypeFromClassId(classId);
      if (result != null) return result;
    }

    // array solution
    Set<String> classIds = asStream(type, JsonNode::textValue)
            .map(shortUri -> expandPrefix(model, shortUri))
            .collect(Collectors.toSet());
    return conversionProfile.getTypeFromClassIds(classIds);
  }

  private void addLanguages(Model model,
                            ConversionProfile.Attribute attribute,
                            Resource uri,
                            JsonNode languagesNode) {
    languagesNode
            .fields()
            .forEachRemaining(languageNode -> {
              String language = languageNode.getKey();
              JsonNode languageValue = languageNode.getValue();

              if (languageValue.isArray()) {
                languageValue
                        .forEach(element -> addLanguageAttribute(model, uri, attribute.getProperty(), language, element
                                .textValue()));
              }
              else {
                addLanguageAttribute(model, uri, attribute.getProperty(), language, languageValue.textValue());
              }
            });
  }

  private String expandPrefix(Model model, String string) {
    if (!configuration.isContextEnabled()) return string;

    return model.expandPrefix(string);
  }

  private void addLanguageAttribute(Model model, Resource uri, Property property, String language, String text) {
    if (StringUtils.isNotBlank(text)) model.add(uri, property, ResourceFactory.createLangLiteral(text, language));
  }

  private RDFNode getObject(String attributeType, JsonNode attributeValue) {
    if (attributeType.equals("rdfs:Resource")) {
      Preconditions.checkArgument(attributeValue.isTextual());
      return ResourceFactory.createResource(attributeValue.textValue());
    }

    if (attributeValue.isNull()) return null;
    if (attributeValue.isTextual() && StringUtils.isBlank(attributeValue.textValue())) return null;

    return ResourceFactory.createTypedLiteral(attributeValue.asText(), getDatatype(attributeType));
  }

  private RDFDatatype getDatatype(String datatypeString) {
    Preconditions.checkNotNull(datatypeString);

    String expanded = prefixCcService.getExpanded(datatypeString);
    RDFDatatype datatype = datatypeMapper.getTypeByName(expanded);
    return datatype == null ? new BaseDatatype(expanded) : datatype;
  }

  private <T> Stream<T> asStream(JsonNode node, Function<JsonNode, T> convert) {
    if (!node.isArray()) return Stream.of(node).map(convert);

    return asStream(node).map(convert);
  }

  private Stream<JsonNode> asStream(JsonNode node) {
    return StreamSupport.stream(node.spliterator(), false);
  }


}
