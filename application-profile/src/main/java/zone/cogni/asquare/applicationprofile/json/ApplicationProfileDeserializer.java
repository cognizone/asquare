package zone.cogni.asquare.applicationprofile.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.BasicApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.MultiApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.builders.ApplicationProfileBuilder;
import zone.cogni.asquare.applicationprofile.model.builders.AttributeBuilder;
import zone.cogni.asquare.applicationprofile.model.builders.TypeBuilder;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.PropertyValue;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.core.spring.ResourceHelper;

import javax.annotation.Nonnull;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.run;
import static io.vavr.Predicates.is;

public class ApplicationProfileDeserializer implements Function<InputStreamSource, ApplicationProfile> {

  private static final Logger log = LoggerFactory.getLogger(ApplicationProfileDeserializer.class);

  private final PrefixCcService prefixCcService;
  private final List<SingleValueRule<String>> prefixRules = new ArrayList<>();
  private Function<String, Resource> resourceSupplier = path -> new ClassPathResource(path);

  public ApplicationProfileDeserializer(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public static JsonNode asJsonNode(Resource jsonResource) {
    return asJsonNode(ResourceHelper.toString(jsonResource, "UTF-8"));
  }

  private static JsonNode asJsonNode(String json) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    return Try.of(() -> objectMapper.readTree(json))
              .getOrElseThrow(e -> new RuntimeException("Json parsing failed.", e));
  }

  @Nonnull
  @Override
  public ApplicationProfile apply(@Nonnull InputStreamSource applicationProfileResource) {
    return applyReadOnlyApplicationProfile(getBasicApplicationProfile(applicationProfileResource));
  }

  private ApplicationProfileDef getBasicApplicationProfile(@Nonnull InputStreamSource applicationProfileResource) {
    JsonNode applicationProfileNode = asJsonNode(ResourceHelper.toString(applicationProfileResource, "UTF-8"));
    ApplicationProfileDef applicationProfile = getBasicApplicationProfile(applicationProfileNode);

    return postprocess(applicationProfile);
  }

  private ApplicationProfileDef postprocess(ApplicationProfileDef applicationProfile) {
    expandAttributeUris(applicationProfile);
    expandPrefixRules();

    return applyMultiApplicationProfile(applicationProfile);
  }

  @Nonnull
  private ApplicationProfile applyReadOnlyApplicationProfile(ApplicationProfileDef applicationProfile) {
    return new BasicApplicationProfile(applicationProfile);
  }

  private void expandPrefixRules() {
    prefixRules.forEach(rule -> {
      rule.setValue(prefixCcService.getExpanded(rule.getValue()));
    });
  }

  private void expandAttributeUris(ApplicationProfileDef applicationProfile) {
    getAllAttributes(applicationProfile).forEach(this::updateAttributeUri);
  }

  @Nonnull
  private ApplicationProfileDef applyMultiApplicationProfile(ApplicationProfileDef applicationProfile) {
    return applicationProfile.getImports().isEmpty() ? applicationProfile
                                                     : MultiApplicationProfileDef.newInstance(applicationProfile);
  }

  private <T extends Rule> Stream<T> getAttributeRules(ApplicationProfileDef applicationProfile, Class<T> ruleType) {
    return getAllAttributes(applicationProfile)
      .flatMap(attribute -> attribute.getRules(ruleType).stream());
  }

  private <T extends Rule> Stream<T> getTypeRules(ApplicationProfileDef applicationProfile, Class<T> ruleType) {
    return getAllTypes(applicationProfile)
      .flatMap(type -> type.getRules(ruleType).stream());
  }

  private Stream<ApplicationProfileDef.AttributeDef> getAllAttributes(ApplicationProfileDef applicationProfile) {
    return getAllTypes(applicationProfile)
      .flatMap(type -> type.getAttributeDefs().values().stream());
  }

  private Stream<ApplicationProfileDef.TypeDef> getAllTypes(ApplicationProfileDef applicationProfile) {
    return applicationProfile.getTypeDefs().values().stream();
  }

  private void updateAttributeUri(ApplicationProfileDef.AttributeDef attribute) {
    if (attribute.getUri() == null) return;
    attribute.setUri(prefixCcService.getExpanded(attribute.getUri()));
  }

  private ApplicationProfileDef getBasicApplicationProfile(JsonNode applicationProfileNode) {
    ApplicationProfileBuilder applicationProfileBuilder = new ApplicationProfileBuilder();

    applicationProfileNode.fields().forEachRemaining(entry -> {
      String field = entry.getKey();
      JsonNode node = entry.getValue();

      Match(field).of(
        Case($(is("uri")), o -> run(() -> setApplicationProfileUri(applicationProfileBuilder, node))),
        Case($(is("extra")), o -> run(() -> setExtraRules(applicationProfileBuilder, node))),
        Case($(is("imports")), o -> run(() -> setImports(applicationProfileBuilder, node))),
        Case($(), o -> run(() -> setApplicationProfileType(applicationProfileBuilder, field, node)))
      );
    });

    return applicationProfileBuilder.get();
  }

  private void setApplicationProfileUri(ApplicationProfileBuilder applicationProfileBuilder, JsonNode node) {
    Preconditions.checkState(node.isTextual(), "uri for application profile is not a text node");
    applicationProfileBuilder.withUri(node.asText());
  }

  private void setExtraRules(ApplicationProfileBuilder applicationProfileBuilder, JsonNode node) {
    Preconditions.checkState(node.isObject() || node.isArray(), "extra must be an array or object");
    applicationProfileBuilder.withExtra(getExtra(node));
  }

  private void setImports(ApplicationProfileBuilder applicationProfileBuilder, JsonNode node) {
    Preconditions.checkState(node.isArray(), "imports must be an array");

    for (JsonNode next : node) {
      ApplicationProfileDef importApplicationProfile = getImportApplicationProfile(next);
      applicationProfileBuilder.withImport(importApplicationProfile);
    }
  }

  protected void setResourceSupplier(Function<String, Resource> resourceSupplier) {
    this.resourceSupplier = resourceSupplier;
  }

  private ApplicationProfileDef getImportApplicationProfile(JsonNode next) {
    Preconditions.checkState(next.isTextual(), "import elements need to be strings");

    String importPath = next.textValue();
    Resource resource = resourceSupplier.apply(importPath);
    Preconditions.checkState(resource.exists(), "import '" + importPath + "' cannot be found");

    return getBasicApplicationProfile(resource);
  }

  private void setApplicationProfileType(ApplicationProfileBuilder applicationProfileBuilder, String field, JsonNode node) {
    Preconditions.checkState(node.isObject(), "type field '" + field + "' must be an object node");
    applicationProfileBuilder.withType(getType(field, node));
  }

  private TypeBuilder getType(String field, JsonNode node) {
    TypeBuilder typeBuilder = new TypeBuilder()
      .withClassId(field);

    fillType(typeBuilder, node);
    return typeBuilder;
  }

  private void fillType(TypeBuilder typeBuilder, JsonNode typeNode) {
    typeNode.fields().forEachRemaining(entry -> {
      String field = entry.getKey();
      JsonNode node = entry.getValue();

      if (Objects.equals(field, "extra")) {
        typeBuilder.withExtra(getExtra(node));
      }
      else if (Objects.equals(field, "constraints")) {
        fillTypeRules(typeBuilder, node);
      }
      else {
        Preconditions.checkState(node.isObject() || node.isArray(), "attribute field '" + field + "' must be an object or array node");
        typeBuilder.withAttribute(getAttribute(field, node));
      }
    });
  }

  private Extra getExtra(JsonNode node) {
    Preconditions.checkState(node.isObject());
    return new Extra(getPropertyValueList(node));
  }

  private List<PropertyValue> getPropertyValueList(JsonNode node) {
    List<PropertyValue> list = new ArrayList<>();

    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> next = fields.next();
      String field = next.getKey();
      JsonNode value = next.getValue();

      list.add(new PropertyValue(field, value.textValue()));
    }
    return list;
  }

  private void fillTypeRules(TypeBuilder typeBuilder, JsonNode constraintsNode) {
    constraintsNode.fields().forEachRemaining(entry -> {
      String field = entry.getKey();
      JsonNode node = entry.getValue();

      typeBuilder.withRules(getRules(null, field, node));
    });
  }

  private AttributeBuilder getAttribute(String field, JsonNode attributeNode) {
    AttributeBuilder attributeBuilder = new AttributeBuilder()
      .withAttributeId(field);

    fillAttribute(attributeBuilder, attributeNode);
    return attributeBuilder;
  }

  private List<Rule> getRules(Rule current, String field, JsonNode ruleNode) {
    boolean isRule = isRule(field);
    if (isRule) {
      Option<Class<?>> typeOption = getClass(field);
      if (typeOption.isEmpty()) return Collections.emptyList();

      Class<? extends Rule> type = (Class<? extends Rule>) typeOption.get();

      boolean isOneSimpleSingleValueRule = ruleNode.isValueNode() && isSingleValueRule(type);
      if (isOneSimpleSingleValueRule) {
        SingleValueRule<Object> rule = getSingleValueRule(type, ruleNode);
        return Stream.of(rule).collect(Collectors.toList());
      }

      boolean isOneObjectSingleValueRule = ruleNode.isObject() && isSingleValueRule(type);
      if (isOneObjectSingleValueRule) {
        Rule rule = createInstance(type);

        ObjectNode objectNode = (ObjectNode) ruleNode;
        objectNode.fields().forEachRemaining(pair -> {
          String pairField = pair.getKey();
          JsonNode pairNode = pair.getValue();

          List<Rule> rulesSubset = getRules(rule, pairField, pairNode);
          assignSingleValue((SingleValueRule<Object>) rule, rulesSubset);
        });

        return Stream.of(rule).collect(Collectors.toList());
      }

      boolean isListOfSingleValueRules = ruleNode.isArray() && isSingleValueRule(type);
      if (isListOfSingleValueRules) {
        ArrayNode arrayNode = (ArrayNode) ruleNode;
        List<Rule> rules = new ArrayList<>();
        arrayNode.forEach(value -> {
          rules.add(getSingleValueRule(type, value));
        });
        return rules;
      }

      Preconditions.checkState(!isSingleValueRule(type), "Unable to process single value rule.");

      // case such as languageIn = ["en", "fr"]
      // TODO check if this is enough => what about "object" cases??
      boolean isListSingleValueRule = ruleNode.isArray() && isListSingleValueRule(type);
      if (isListSingleValueRule) {
        ListSingleValueRule<Object> rule = (ListSingleValueRule<Object>) createInstance(type);
        List<Object> objects = convertArrayToValueList((ArrayNode) ruleNode);
        rule.setValue(objects);
        return Stream.of(rule).collect(Collectors.toList());
      }


      boolean isListRule = ruleNode.isObject() && isListSingleValueRule(type);
      if (isListRule) {
        ListSingleValueRule<Object> rule = (ListSingleValueRule<Object>) createInstance(type);

        List<Object> rules = new ArrayList<>();

        ObjectNode objectNode = (ObjectNode) ruleNode;
        objectNode.fields().forEachRemaining(pair -> {
          String pairField = pair.getKey();
          JsonNode pairNode = pair.getValue();

          List<Rule> rulesSubset = getRules(rule, pairField, pairNode);
          rules.addAll(rulesSubset);
        });
        rule.setValue(rules);

        return Stream.of(rule).collect(Collectors.toList());
      }

      Preconditions.checkState(!isListSingleValueRule(type), "Unable to process list single value rule: " + type.getName());

      // normal rule
      boolean isObject = ruleNode.isObject();
      if (isObject) {
        Rule rule = createInstance(type);

        ObjectNode objectNode = (ObjectNode) ruleNode;
        objectNode.fields().forEachRemaining(pair -> {
          String pairField = pair.getKey();
          JsonNode pairNode = pair.getValue();

          if (isRule(pairField)) {
            List<Rule> rulesSubset = getRules(rule, pairField, pairNode);
            Preconditions.checkState(true, "Unsupported state." + pairField);
          }
          else {
            setFieldOnRule(rule, pairField, convertJsonNodeToValue(pairNode));
          }
        });

        return Stream.of(rule).collect(Collectors.toList());
      }

      Preconditions.checkState(true, "Should never get here!");

    }
    else {
      log.info("Ignoring some (superfluous?!) data on field '{}'.", field);
      return Collections.emptyList();
//      Preconditions.checkState(current != null, "Not sure what we have here: " + current);
    }

    return null;
  }


  private <T extends Rule> T createInstance(Class<T> type) {
    T rule = Try.of(type::newInstance)
                .getOrElseThrow(() -> new RuntimeException("cannot instantiate class '" + type.getName() + "'"));
    if (Arrays.asList(RdfType.class, Datatype.class).contains(type)) prefixRules.add((SingleValueRule<String>) rule);
    return rule;
  }

  private Option<Class<?>> getClass(String field) {
    // TODO make it more flexible
    String prefix = "zone.cogni.asquare.applicationprofile.rules";

    Class<?> rule = Try.of(() -> Class.forName(prefix + "." + StringUtils.capitalize(field))).getOrElse((Class) null);
    if (rule != null) return Option.of(rule);

    Class<?> rule2 = Try.of(() -> Class.forName(prefix + ".other." + StringUtils.capitalize(field))).getOrElse((Class) null);
    return Option.of(rule2);
  }


  private void setFieldOnRule(Rule rule, String field, Object object) {
    // TODO improve performance!
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(rule.getClass());
    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {

      if (propertyDescriptor.getWriteMethod() == null || propertyDescriptor.getReadMethod() == null)
        continue;

      if (!Objects.equals(propertyDescriptor.getName(), field))
        continue;

      Method writeMethod = propertyDescriptor.getWriteMethod();
      try {
        writeMethod.invoke(rule, object);
      }
      catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
        log.error("Ignoring", e);
        // TODO
      }
    }
  }

  private void assignSingleValue(SingleValueRule<Object> current, List<Rule> rulesSubset) {
    if (current == null) return;

    Preconditions.checkState(rulesSubset.size() <= 1);
    if (rulesSubset.isEmpty()) return;

    current.setValue(rulesSubset.get(0));
  }

  private SingleValueRule<Object> getSingleValueRule(Class<? extends Rule> type, JsonNode ruleNode) {
    SingleValueRule<Object> rule = (SingleValueRule<Object>) createInstance(type);

    // TODO set
    rule.setValue(convertJsonNodeToValue(ruleNode));
    return rule;
  }


  private List<Object> convertArrayToValueList(ArrayNode array) {
    List<Object> result = new ArrayList<>();

    array.forEach(element -> {
      Object o = convertJsonNodeToValue(element);
      result.add(o);
    });

    return result;
  }

  private Object convertJsonNodeToValue(JsonNode ruleNode) {
    if (ruleNode.isInt()) return ruleNode.intValue();
    else if (ruleNode.isTextual()) return ruleNode.textValue();
    else if (ruleNode.isBoolean()) return ruleNode.booleanValue();
    else throw new RuntimeException("Unsupported node " + ruleNode.asText());
  }

  private boolean isRule(String field) {
    return !getClass(field).isEmpty();
  }

  private boolean isSingleValueRule(Class<?> type) {
    return SingleValueRule.class.isAssignableFrom(type) && !ListSingleValueRule.class.isAssignableFrom(type);
  }

  private boolean isListSingleValueRule(Class<?> type) {
    return ListSingleValueRule.class.isAssignableFrom(type);
  }

  private void fillAttribute(AttributeBuilder attributeBuilder, JsonNode attributeNode) {
    attributeNode.fields().forEachRemaining(entry -> {
      String field = entry.getKey();
      JsonNode node = entry.getValue();

      if (Objects.equals(field, "uri")) {
        Preconditions.checkState(node.isTextual(), "uri for attribute '" + attributeBuilder.getAttributeId() + "' is not a text node");
        attributeBuilder.withUri(node.asText());
      }
      else if (Objects.equals(field, "extra")) {
        attributeBuilder.withExtra(getExtra(node));
      }
      else {
        attributeBuilder.withRules(getRules(null, field, node));
      }
    });
  }


}
