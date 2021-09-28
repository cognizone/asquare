package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.edit.ConstructedResource;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.BasicRdfValue;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.jena.rdf.model.ResourceFactory.createLangLiteral;
import static zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Type.calculateType;

public class JsonToTypedResource implements Supplier<List<MutableResource>> {

  private final TypeMapper typeMapper = TypeMapper.getInstance();

  private ApplicationView applicationView;
  private final PrefixCcService prefixCcService;
  private ObjectNode jsonRoot;
  private final Map<String, MutableResource> addedTypedResources = new HashMap<>();
  private final Map<String, ObjectNode> dataMap = new HashMap<>();
  private final Map<String, ObjectNode> includedMap = new HashMap<>();
  private BiFunction<ApplicationProfile.Type, String, ? extends MutableResource> resourceFactory = ConstructedResource::new;

  public JsonToTypedResource(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public JsonToTypedResource withApplicationView(ApplicationView applicationView) {
    Preconditions.checkNotNull(applicationView);
    this.applicationView = applicationView;
    return this;
  }

  public JsonToTypedResource withJsonRoot(ObjectNode jsonRoot) {
    this.jsonRoot = jsonRoot;
    return this;
  }

  public List<MutableResource> get() {
    Preconditions.checkNotNull(applicationView);
    Preconditions.checkNotNull(jsonRoot);
    JsonNode data = jsonRoot.get("data");
    Preconditions.checkNotNull(data);

    List<MutableResource> list = new ArrayList<>();
    populateIncludedMap();

    if (data.isArray()) {
      populateDataMap();
      data.forEach(d -> list.add(get((ObjectNode) d)));
    }
    else {
      Preconditions.checkState(data.isObject());
      list.add(get((ObjectNode) data));
    }
    return list;
  }

  public <T extends MutableResource> List<T> get(BiFunction<ApplicationProfile.Type, String, T> resourceFactory) {
    this.resourceFactory = resourceFactory;
    return (List<T>) get();
  }

  private MutableResource get(ObjectNode object) {
    MutableResource resource = addedTypedResources.get(object.get("uri").asText());

    if (resource != null) return resource;

    resource = createResource(object);
    addAttributes(object, resource);
    addReferences(object, resource);
    return resource;
  }

  private void addAttributes(ObjectNode objectNode, MutableResource parent) {
    ObjectNode attributes = (ObjectNode) objectNode.get("attributes");
    if (attributes == null) return;

    attributes.fields()
              .forEachRemaining(attributeField -> addAttributeField(parent, attributeField));
  }

  private void addAttributeField(MutableResource parent, Map.Entry<String, JsonNode> attributeField) {
    JsonNode value = attributeField.getValue();
    if (value.getNodeType().equals(JsonNodeType.POJO)) {
      addPojoAttributeField(parent, attributeField.getKey(), (POJONode) value);
    }
    else {
      addJsonNodeAttributeField(parent, attributeField, value);
    }

    // if the attribute was specified in json but without any value we explicitly want to set it to empty
    // On an update if the attribute is explicitly set to empty we know the attribute must be deleted
    // BUT if the attribute is not specified and therefore not explicitly set the attribute will be neglected on an update.
    if (!parent.hasValues(attributeField.getKey())) parent.setValues(attributeField.getKey(), Collections.emptyList());
  }

  private void addJsonNodeAttributeField(MutableResource parent, Map.Entry<String, JsonNode> attributeField, JsonNode value) {
    value.fields().forEachRemaining(typeField -> {
      arrayOrSingleStream(typeField.getValue())
        .forEach(lit -> {
          addLiteral(attributeField.getKey(), typeField.getKey(), lit, parent);
        });
    });
  }

  @SuppressWarnings("unchecked")
  private void addPojoAttributeField(MutableResource parent, String attributeId, POJONode literalPojo) {
    Object pojo = literalPojo.getPojo();

    // weird data for literal pojo
    if (!(pojo instanceof Map)) {
      // hmm, not sure what to do here
      throw new IllegalStateException("Cannot have a language POJO which is not a map: " + pojo);
    }

    Map<String, Object> literalMap = (Map<String, Object>) pojo;
    convertJsonMapToStream(literalMap)
      .forEach(entry -> addTriple(parent, attributeId, getPojoBasicRdfValue(entry.getValue(), entry.getKey())));
  }

  private BasicRdfValue getPojoBasicRdfValue(@Nonnull Object value,
                                             @Nonnull String dataType) {
    RDFDatatype rdfDataType = getDataType(dataType);
    boolean isResource = rdfDataType.getURI().equals(RDFS.Resource.getURI()) && value instanceof String;
    if (isResource) {
      return new BasicRdfValue(ResourceFactory.createResource((String) value));
    }
    else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return new BasicRdfValue(ResourceFactory.createTypedLiteral(value.toString(), rdfDataType));
    }

    throw new IllegalArgumentException("Cannot convert type '" + dataType + "'" +
                                       " and value '" + value + "' of class " + value.getClass().getName());
  }

  private void addLiteral(String attributeId, String dataTypeString, JsonNode literal, MutableResource parent) {
    if (literal.isNull()) return;

    RDFDatatype datatype = getDataType(dataTypeString);

    if (RDFLangString.rdfLangString.equals(datatype)) {
      if (literal.getNodeType().equals(JsonNodeType.POJO)) {
        addLanguagePojo(parent, attributeId, (POJONode) literal);
      }
      else {
        addLanguageJsonNode(parent, attributeId, literal);
      }
    }
    else if (RDFS.Resource.getURI().equals(datatype.getURI())) {
      String value = literal.asText();
      if (StringUtils.isBlank(value)) return;

      String uri = prefixCcService.getExpanded(value);
      parent.addValue(parent.getType().getAttribute(attributeId), new BasicRdfValue(ResourceFactory.createResource(uri)));
    }
    else if (literal.isValueNode()) {
      addLiteral(parent, attributeId, literal.asText(), datatype);
    }
    else throw new IllegalStateException("'" + dataTypeString + "' not supported as " + literal.toString());
  }

  @SuppressWarnings("unchecked")
  private void addLanguagePojo(MutableResource parent, String attributeId, POJONode languagePojo) {
    Object pojo = languagePojo.getPojo();

    // weird data for language pojo
    if (!(pojo instanceof Map)) {
      throw new IllegalStateException("Cannot have a language POJO which is not a map: " + pojo);
    }

    // normal data for language pojo
    Map<String, Object> languageMap = (Map<String, Object>) pojo;
    convertJsonMapToStream(languageMap)
      .forEach(entry -> addLiteral(parent, attributeId, getPojoLangLiteral(entry.getValue(), entry.getKey())));
  }

  private Literal getPojoLangLiteral(@Nonnull Object value,
                                     @Nonnull String language) {
    if (!(value instanceof String))
      throw new IllegalStateException("Cannot convert language value which is not a string "
                                      + " for language '" + language + "'"
                                      + " and value '" + value + "'. of class " + value.getClass().getName());

    return createLangLiteral((String) value, language);
  }

  private void addLanguageJsonNode(MutableResource parent, String attributeId, JsonNode languageNode) {
    languageNode.fields()
                .forEachRemaining(langField -> {
                  arrayOrSingleStream(langField.getValue())
                    .forEach(lit -> addLiteral(parent, attributeId, getLangLiteral(langField.getKey(), lit.asText())));
                });
  }

  private Stream<Map.Entry<String, Object>> convertJsonMapToStream(Map<String, Object> map) {
    return map.entrySet()
              .stream()
              .flatMap(entry -> asStream(entry.getValue()).map(v -> Maps.immutableEntry(entry.getKey(), v)));
  }

  private Stream<?> asStream(Object value) {
    return value instanceof List ? ((List) value).stream() : Stream.of(value);
  }

  private Literal getLangLiteral(String language, String value) {
    return createLangLiteral(value, language);
  }

  private RDFDatatype getDataType(String dataTypeString) {
    Preconditions.checkNotNull(dataTypeString);
    String expanded = prefixCcService.getExpanded(dataTypeString);
    RDFDatatype datatype = typeMapper.getTypeByName(expanded);
    return datatype == null ? new BaseDatatype(expanded) : datatype;
  }

  private Stream<JsonNode> arrayOrSingleStream(JsonNode arrayOrSingle) {
    Stream.Builder<JsonNode> builder = Stream.builder();
    if (arrayOrSingle.isArray()) arrayOrSingle.forEach(builder);
    else builder.accept(arrayOrSingle);
    return builder.build();
  }

  private void addReferences(ObjectNode objectNode, MutableResource resource) {
    ObjectNode references = (ObjectNode) objectNode.get("references");
    if (references == null) return;

    references.fields().forEachRemaining(field -> addReferenceField(resource, field));
  }

  private void addReferenceField(MutableResource resource, Map.Entry<String, JsonNode> referenceField) {
    addReference(resource, referenceField.getKey(), referenceField.getValue());

    // if the attribute was specified in json but without any value we explicitly want to set it to empty
    // On an update if the attribute is explicitly set to empty we know the attribute must be deleted
    // BUT if the attribute is not specified and therefore not explicitly set the attribute will be neglected on an update.
    if (!resource.hasValues(referenceField.getKey())) resource.setValues(referenceField.getKey(), Collections.emptyList());
  }

  private void addReference(MutableResource parent, String attributeId, JsonNode value) {
    if (value.isNull()) return;
    arrayOrSingleStream(value)
      .forEach(val -> {
        MutableResource child = addedTypedResources.get(val.asText());
        if (child == null) child = get(lookUp(val.asText()));
        addResource(parent, attributeId, child);
      });
  }

  private void addResource(MutableResource parent, String attributeId, MutableResource resource) {
    parent.addValue(parent.getType().getAttribute(attributeId), resource);
  }

  private void addLiteral(MutableResource parent, String attributeId, String value, RDFDatatype datatype) {
    addLiteral(parent, attributeId, ResourceFactory.createTypedLiteral(value, datatype));
  }

  private void addLiteral(MutableResource parent, String attributeId, Literal literal) {
    addTriple(parent, attributeId, new BasicRdfValue(literal));
  }

  private void addTriple(MutableResource parent, String attributeId, BasicRdfValue basicRdfValue) {
    parent.addValue(parent.getType().getAttribute(attributeId), basicRdfValue);
  }

  private ObjectNode lookUp(String uri) {
    ObjectNode node = includedMap.getOrDefault(uri, dataMap.get(uri));
    Preconditions.checkNotNull(node, "missing json object for reference '" + uri + "'");
    return node;
  }

  private void populateIncludedMap() {
    JsonNode included = this.jsonRoot.get("included");
    if (included == null) return;

    included.forEach(val -> includedMap.put(val.get("uri").asText(), (ObjectNode) val));
  }

  private void populateDataMap() {
    JsonNode included = this.jsonRoot.get("data");
    included.forEach(val -> dataMap.put(val.get("uri").asText(), (ObjectNode) val));
  }

  private MutableResource createResource(ObjectNode object) {

    Preconditions.checkNotNull(object.get("uri"));
    String uri = object.get("uri").asText();
    List<ApplicationProfile.Type> types = arrayOrSingleStream(object.get("type"))
      .map(JsonNode::asText)
      .map(typeId -> applicationView.getApplicationProfile().getType(typeId))
      .collect(Collectors.toList());
    Preconditions.checkState(!types.isEmpty());

    ApplicationProfile.Type type = calculateType(types);
    MutableResource resource = resourceFactory.apply(type, uri);

    addedTypedResources.put(uri, resource);
    return resource;
  }
}
