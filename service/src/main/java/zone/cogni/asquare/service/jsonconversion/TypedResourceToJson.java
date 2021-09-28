package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.rdf.model.Literal;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MaxLangCardinality;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypedResourceToJson implements Supplier<ObjectNode> {

  private final PrefixCcService prefixCcService;
  private final Set<String> addedObjectUris = new HashSet<>();
  private TypedResource typedResource;
  private Collection<? extends TypedResource> typedResources;
  private ObjectNode jsonRoot;

  public TypedResourceToJson(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public TypedResourceToJson withTypedResource(TypedResource typedResource) {
    Preconditions.checkNotNull(typedResource);
    this.typedResource = typedResource;
    return this;
  }

  public TypedResourceToJson withTypedResources(Collection<? extends TypedResource> typedResources) {
    Preconditions.checkNotNull(typedResources);
    this.typedResources = typedResources;
    return this;
  }

  @Override
  public ObjectNode get() {

    Preconditions.checkState(isSetupComplete(), "Needs either one or many typed resources.");
    Preconditions.checkState(addedObjectUris.isEmpty(), "Builder not reusable");

    jsonRoot = new ObjectMapper().createObjectNode();

    if (typedResource != null) setSingleData();
    else if (typedResources != null) setData();
    else throw new RuntimeException("paradox!");

    return jsonRoot;
  }

  private boolean isSetupComplete() {
    return (typedResource == null) != (typedResources == null);
  }

  private void setSingleData() {
    ObjectNode data = jsonRoot.putObject("data");
    List<TypedResource> included =  handleTypedResource(data, typedResource)
        .collect(Collectors.toList());

    handleDeeperIncluded(included);
  }

  private void setData() {
    ArrayNode data = jsonRoot.putArray("data");

    List<TypedResource> included = this.typedResources.stream()
        .flatMap(resource -> handleTypedResource(data.addObject(), resource))
        .collect(Collectors.toList());

    handleDeeperIncluded(included);
  }

  private void handleDeeperIncluded(List<TypedResource> included) {
    while (!included.isEmpty()) {
      included = included.stream()
          .filter(this::isUnexploredResource)
          .flatMap(resource -> handleTypedResource(getOrCreateArray(jsonRoot, "included").addObject(), resource))
          .collect(Collectors.toList());
    }
  }

  private Stream<TypedResource> handleTypedResource(ObjectNode object, TypedResource typedResource) {
    addedObjectUris.add(typedResource.getResource().getURI());

    String uri = typedResource.getResource().getURI();
    object.put("uri", uri);

    ApplicationProfile.Type type = typedResource.getType();

    Consumer<String> typeAdder = type.getSuperClassIds().size() > 1
        ? s -> addToJsonAsList(object, "type", s)
        : s -> addToJsonAsSingle(object, "type", s);

    type.getSuperClassIds().forEach(typeAdder);

    return typedResource.getType().getAttributes().values().stream()
        .flatMap(attribute -> handleAttribute(object, typedResource, attribute));
  }

  private Stream<TypedResource> handleAttribute(ObjectNode object, TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    List<RdfValue> values = typedResource.getValues(attribute);
    if (values.isEmpty()) return Stream.empty();

    boolean isList = isList(attribute);
    boolean isLangList = isLangList(attribute);

    CardinalityMap literalMap = CardinalityMap.fromIsList(isList);
    CardinalityMap langMap = CardinalityMap.fromIsList(isLangList);

    Stream.Builder<TypedResource> next = Stream.builder();

    values.forEach(value -> {
      RdfValueCase rdfValueCase = getRdfValueCase(value);
      switch (rdfValueCase) {
        case TypedResource:
          addReference(object, attribute, isList, (TypedResource) value);
          next.add((TypedResource) value);
          break;
        case Language:
          addLangString(langMap, (BasicRdfValue) value);
          break;
        case Resource:
          addResource(literalMap, (BasicRdfValue) value);
          break;
        case Date:
          addDate(literalMap, (BasicRdfValue) value);
          break;
        case Other:
          addOtherLiterals(literalMap, (BasicRdfValue) value);
      }
    });


    if (literalMap.hasValues()) {
      ObjectNode attributesJson = getOrCreateObject(object, "attributes");
      attributesJson.putPOJO(attribute.getAttributeId(), literalMap.getInnerMap());
    }
    if (langMap.hasValues()) {
      ObjectNode attributesJson = getOrCreateObject(object, "attributes");
      ObjectNode dataTypeWrapper = getOrCreateObject(attributesJson, attribute.getAttributeId());
      addToJsonAsSingle(dataTypeWrapper, "rdf:langString", langMap.getInnerMap());
    }

    return next.build();
  }

  private void addReference(ObjectNode object, ApplicationProfile.Attribute attribute, boolean isList, TypedResource typedResource) {
    ObjectNode references = getOrCreateObject(object, "references");
    if (isList) addToJsonAsList(references, attribute.getAttributeId(), typedResource.getResource().getURI());
    else addToJsonAsSingle(references, attribute.getAttributeId(), typedResource.getResource().getURI());
  }

  private void addLangString(CardinalityMap map, BasicRdfValue value) {
    map.put(getLanguageOfLangString(value.getLiteral()), value.getLiteral().getString());
  }

  private void addResource(CardinalityMap map, BasicRdfValue value) {
    map.put("rdfs:Resource", value.getResource().getURI());
  }

  private void addDate(CardinalityMap map, BasicRdfValue value) {
    String typeUri = value.getLiteral().getDatatypeURI();
    String shortTypeUri = prefixCcService.getShortenedUri(typeUri);

    if (XSDDatatype.XSDdate.getURI().equals(typeUri)) map.put(shortTypeUri, literalToDate(value.getLiteral()));
    else if (XSDDatatype.XSDdateTime.getURI().equals(typeUri)) map.put(shortTypeUri, literalToDateTime(value.getLiteral()));
  }

  private void addOtherLiterals(CardinalityMap map, BasicRdfValue value) {
    Preconditions.checkState(value instanceof BasicRdfValue);

    if (value.isLiteral()) {
      Literal literal = value.getLiteral();
      String shortTypeUri = Try.of(() -> prefixCcService.getShortenedUri(literal.getDatatypeURI()))
        .recover(IllegalStateException.class, literal.getDatatypeURI()).get();

      Object litValue = literal.getValue();
      if (litValue instanceof BaseDatatype.TypedValue) litValue = ((BaseDatatype.TypedValue) litValue).lexicalValue;

      map.put(shortTypeUri, litValue);
      return;
    }

    throw new RuntimeException("LiteralNode of type Resource not supported yet.");
  }

  private void addToJsonAsList(ObjectNode object, String attribute, Object value) {
    getOrCreateArray(object, attribute).addPOJO(value);
  }

  private void addToJsonAsSingle(ObjectNode object, String attribute, Object value) {
    // TODO still seems to fail
//    Preconditions.checkState(object.get(attribute) == null);
    object.putPOJO(attribute, value);
  }


  private RdfValueCase getRdfValueCase(RdfValue value) {
    if (value instanceof TypedResource) return RdfValueCase.TypedResource;
    if (value instanceof BasicRdfValue) {
      if (value.isLiteral()) {
        RDFDatatype datatype = value.getLiteral().getDatatype();
        if (RDFLangString.rdfLangString.equals(datatype)) return RdfValueCase.Language;
        if (XSDDatatype.XSDdate.equals(datatype) || XSDDatatype.XSDdateTime.equals(datatype)) return RdfValueCase.Date;
      }
      else if (value.isResource()) {
        return RdfValueCase.Resource;
      }
    }

    return RdfValueCase.Other;
  }

  private boolean isList(ApplicationProfile.Attribute attribute) {
    Option<MaxCardinality> max = attribute.getRule(MaxCardinality.class);

    return max.isEmpty() || max.filter(c -> c.getValue() > 1).isDefined();
  }

  private boolean isLangList(ApplicationProfile.Attribute attribute) {
    Option<MaxLangCardinality> max = attribute.getRule(MaxLangCardinality.class);

    return max.isEmpty() || max.filter(c -> c.getValue() > 1).isDefined();
  }

  private String getLanguageOfLangString(Literal langString) {
    String lang = langString.getLanguage();
    Preconditions.checkNotNull(lang);
    return lang;
  }

  protected String literalToDate(Literal literal) {
    try {
      LocalDate localDate = LocalDate.parse(literal.getLexicalForm(), DateTimeFormatter.ISO_LOCAL_DATE);//ISO_LOCAL_DATE = 2020-06-26
      return localDate.toString(); // The output will be in the ISO-8601 format {@code uuuu-MM-dd}.
    }
    catch (Exception exception) {
      throw new RuntimeException("Failed to convert literal to Date: " + literal, exception);
    }
  }

  private String literalToDateTime(Literal literal) {
    //todo: remove joda
    return dateTime2string(ISODateTimeFormat.dateTimeParser().parseDateTime(literal.getLexicalForm()));
  }

  private String dateTime2string(DateTime dateTime) {
    return dateTime == null ? null : dateTime.withZone(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());
  }

  private ObjectNode getOrCreateObject(ObjectNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null) child = node.putObject(fieldName);
    return (ObjectNode) child;
  }

  private boolean isUnexploredResource(TypedResource typedRdfResource) {
    return !addedObjectUris.contains(typedRdfResource.getResource().getURI());
  }

  private ArrayNode getOrCreateArray(ObjectNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null) child = node.putArray(fieldName);
    return (ArrayNode) child;
  }

  private enum RdfValueCase {
    TypedResource,
    Language,
    Resource,
    Date,
    Other
  }

  private static class CardinalityMap {

    static CardinalityMap single() {
      CardinalityMap m = new CardinalityMap();
      m.singleMap = new HashMap<>();
      m.valueConsumer = (lang, v) -> m.singleMap.put(lang, v);
      return m;
    }

    static CardinalityMap multiple() {
      CardinalityMap m = new CardinalityMap();
      m.multimap = ArrayListMultimap.create();
      m.valueConsumer = (lang, v) -> m.multimap.put(lang, v);
      return m;
    }

    static CardinalityMap fromIsList(boolean isList) {
      return isList ? multiple() : single();
    }

    private Map<String, Object> singleMap;
    private Multimap<String, Object> multimap;
    private BiConsumer<String, Object> valueConsumer;

    void put(String key, Object value) {
      valueConsumer.accept(key, value);
    }

    Map<String, ?> getInnerMap() {
      return singleMap != null ? singleMap : multimap.asMap();
    }

    boolean hasValues() {
      return !getInnerMap().isEmpty();
    }


  }
}
