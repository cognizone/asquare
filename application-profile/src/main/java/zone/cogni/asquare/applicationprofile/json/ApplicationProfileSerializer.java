package zone.cogni.asquare.applicationprofile.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;
import static io.vavr.Predicates.isNull;

public class ApplicationProfileSerializer implements Function<ApplicationProfileDef, JsonNode> {

  private static final Logger log = LoggerFactory.getLogger(ApplicationProfileSerializer.class);

  @Override
  public JsonNode apply(ApplicationProfileDef applicationProfile) {
    return createApplicationProfileNode(applicationProfile.getRootDefinition());
  }

  private JsonNode createApplicationProfileNode(ApplicationProfileDef applicationProfile) {
    ObjectNode applicationProfileNode = JsonNodeFactory.instance.objectNode();

    Preconditions.checkState(StringUtils.isNotBlank(applicationProfile.getUri()));
    applicationProfileNode.put("uri", applicationProfile.getUri());

    processExtra(applicationProfileNode, applicationProfile.getExtra());
    processTypes(applicationProfileNode, applicationProfile.getTypeDefs());
    return applicationProfileNode;
  }

  private void processExtra(ObjectNode parentNode, Extra extra) {
    if (extra.getValue().isEmpty()) return;

    ObjectNode extraNode = JsonNodeFactory.instance.objectNode();
    parentNode.set("extra", extraNode);

    extra.getValue().forEach(rule -> {
      extraNode.put(rule.getProperty(), rule.getValue());
    });
  }

  private void processTypes(ObjectNode applicationProfileNode, Map<String, ApplicationProfileDef.TypeDef> types) {
    types.forEach((name, type) -> {
      applicationProfileNode.set(name, createTypeNode(type));
    });
  }

  private ObjectNode createTypeNode(ApplicationProfileDef.TypeDef type) {
    ObjectNode typeNode = JsonNodeFactory.instance.objectNode();

    processTypeRules(typeNode, type.getRules());
    processExtra(typeNode, type.getExtra());
    processAttributes(typeNode, type.getAttributeDefs());

    return typeNode;
  }

  private void processTypeRules(ObjectNode typeNode, List<Rule> rules) {
    if (rules.isEmpty()) return;

    ObjectNode constraintsNode = JsonNodeFactory.instance.objectNode();
    typeNode.set("constraints", constraintsNode);

    processRules(constraintsNode, rules);
  }

  private void processAttributes(ObjectNode typeNode, Map<String, ApplicationProfileDef.AttributeDef> attributes) {
    attributes.forEach((name, attribute) -> {
      typeNode.set(name, createAttributeNode(attribute));
    });
  }

  private JsonNode createAttributeNode(ApplicationProfileDef.AttributeDef attribute) {
    ObjectNode attributeNode = JsonNodeFactory.instance.objectNode();
    attributeNode.put("uri", attribute.getUri());

    processExtra(attributeNode, attribute.getExtra());
    processRules(attributeNode, attribute.getRules());

    return attributeNode;
  }

  private void processRules(ObjectNode parentNode, List<Rule> rules) {
    rules.forEach(rule -> {
      parentNode.set(rule.getRuleName(), createRuleNode(rule));
    });
  }

  private JsonNode createRuleNode(Rule rule) {
    if (rule instanceof SingleValueRule) {
      return createSingleValueRuleNode((SingleValueRule<?>) rule);
    }

    if (rule instanceof ListSingleValueRule) {
      return createListSingleValueRuleNode((ListSingleValueRule<?>) rule);
    }

    return createGenericRule(rule);
  }

  private JsonNode createGenericRule(Rule rule) {
    ObjectNode ruleNode = JsonNodeFactory.instance.objectNode();

    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(rule.getClass());
    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      if (propertyDescriptor.getWriteMethod() == null || propertyDescriptor.getReadMethod() == null)
        continue;

      Method readMethod = propertyDescriptor.getReadMethod();
      try {
        Object value = readMethod.invoke(rule);
        JsonNode valueNode = convertValueToNode(rule, value);
        ruleNode.set(propertyDescriptor.getDisplayName(), valueNode);
      }
      catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
        log.error("Ignoring", e);
        // TODO
      }
    }

    return ruleNode;
  }

  private JsonNode createSingleValueRuleNode(SingleValueRule<?> rule) {
    Object value = rule.getValue();

    return convertValueToNode(rule, value);
  }

  private JsonNode convertValueToNode(Rule rule, Object value) {
    JsonNode orElseThrow = Match(value).option(
            Case($(isNull()), JsonNodeFactory.instance.nullNode()),
            Case($(instanceOf(SingleValueRule.class)), this::createValueNode),
            Case($(instanceOf(ListSingleValueRule.class)), this::createValueNode),
            Case($(instanceOf(String.class)), JsonNodeFactory.instance::textNode),
            Case($(instanceOf(Integer.class)), JsonNodeFactory.instance::numberNode),
            Case($(instanceOf(Boolean.class)), JsonNodeFactory.instance::booleanNode)
    ).getOrElseThrow(() -> new RuntimeException("Unsupported rule of type '" + rule.getClass().getName() + "'. Rule as string: " + rule));
    return orElseThrow;
  }

  private JsonNode createListSingleValueRuleNode(ListSingleValueRule<?> rule) {

    if (Rule.class.isAssignableFrom(rule.allowedType())) {
      return getJsonNodeRuleList((List<Rule>) rule.getValue());
    }
    else {
      return getJsonNodeGenericList(rule);
    }

  }

  /**
   * In case all rules are of same class : merge values in an array
   * In case all rules are of different classes: make each rule a property of current object
   * Else: create separate objects for each and everyone of them
   */
  private JsonNode getJsonNodeRuleList(List<Rule> rules) {
    if (rules.isEmpty()) return JsonNodeFactory.instance.nullNode();

    if (isAllSameClass(rules)) {
      return listOfRules2arrayOfValues(rules);
    }
    else if (isOneInstancePerClass(rules)) {
      return listOfRules2properties(rules);
    }
    else {
      return listOfRules2expandedObjects(rules);
    }
  }

  private boolean isOneInstancePerClass(List<Rule> rules) {
    Set<Class> classes = rules.stream().map(Object::getClass).collect(Collectors.toSet());
    return classes.size() == rules.size();
  }

  private boolean isAllSameClass(List<?> rules) {
    Set<Class> classes = rules.stream().map(Object::getClass).collect(Collectors.toSet());
    return classes.size() == 1;
  }

  private JsonNode listOfRules2properties(List<Rule> rules) {
    ObjectNode ruleNode = JsonNodeFactory.instance.objectNode();
    rules.forEach(r -> ruleNode.set(r.getRuleName(), createRuleNode(r)));
    return ruleNode;
  }

  private JsonNode listOfRules2arrayOfValues(List<Rule> rules) {
    ObjectNode ruleNode = JsonNodeFactory.instance.objectNode();

    ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
    ruleNode.set(rules.get(0).getRuleName(), arrayNode);

    rules.forEach(rule -> arrayNode.add(createRuleNode((Rule) rule)));
    return ruleNode;
  }

  private JsonNode listOfRules2expandedObjects(List<Rule> rules) {
    ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
    rules.forEach(r -> arrayNode.add(createValueNode((Rule) r)));
    return arrayNode;
  }

  private JsonNode createValueNode(Rule value) {
    ObjectNode ruleNode = JsonNodeFactory.instance.objectNode();
    ruleNode.set(value.getRuleName(), createRuleNode(value));
    return ruleNode;
  }

  private JsonNode getJsonNodeGenericList(ListSingleValueRule<?> rule) {
    List<?> values = rule.getValue();
    if (values.isEmpty()) return JsonNodeFactory.instance.nullNode();

    Preconditions.checkState(isAllSameClass(values), "Values are of different types.");

    ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> arrayNode.add(convertValueToNode(rule, value)));

    return arrayNode;
  }

}
