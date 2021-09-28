package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.Rule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class OwlRules {

  private static final Logger log = LoggerFactory.getLogger(OwlRules.class);

  private final List<Rule> rules = new ArrayList<>();
  private final List<Rule> derivedRules = new ArrayList<>();

  private final SortedMap<String, List<Rule>> removedRuleMap = new TreeMap<>();
  private final List<Rule> removedRules = new ArrayList<>();

  private Model model;

  public OwlRules() {
  }

  public OwlRules(Model model) {
    this.model = ModelFactory.createDefaultModel();
    this.model.add(model.listStatements());
  }

  public OwlRules copy() {
    OwlRules result = new OwlRules(model);
    rules.forEach(rule -> result.add(rule.copy()));
    return result;
  }

  public <T extends Rule> void add(@Nonnull List<T> rules) {
    rules.forEach(this::add);
  }

  public void add(@Nonnull Rule rule) {
    this.rules.add(rule);
  }

  public <T extends Rule> void addDerivedRules(String reason, List<T> rules) {
    rules.forEach(rule -> addDerivedRule(reason, rule));
  }

  public void addDerivedRule(String reason, Rule rule) {
    derivedRules.add(rule);
  }

  public <T extends Rule> void remove(String reason, List<T> rules) {
    rules.forEach(rule -> remove(reason, rule));
  }

  public void remove(String reason, Rule rule) {
    log.debug("removing rule: {}", rule);
    boolean expression = rules.contains(rule) || derivedRules.contains(rule);
    Preconditions.checkState(expression,
                             "Rule not found?!!");

    // try removing in both
    rules.remove(rule);
    derivedRules.remove(rule);

    removedRules.add(rule);
    removedRuleMap.computeIfAbsent(reason, (Function<String, List<Rule>>) input -> new ArrayList<>())
                  .add(rule);
  }

  @SuppressWarnings("unchecked")
  public <T extends Rule> List<T> getExactRules(Class<T> type) {
    return (List<T>) getCombinedRules()
            .filter(rule -> type.equals(rule.getClass()))
            .collect(Collectors.toList());
  }

  private Stream<Rule> getCombinedRules() {
    return Stream.concat(rules.stream(), derivedRules.stream());
  }

  public long getCombinedRulesSize() {
    return rules.size() + derivedRules.size();
  }

  @SuppressWarnings("unchecked")
  public <T extends Rule> List<T> getAssignableRules(Class<T> type) {
    return (List<T>) getCombinedRules().filter(rule -> type.isAssignableFrom(rule.getClass()))
                                       .collect(Collectors.toList());
  }

  public List<Rule> getRemainingRules() {
    return rules;
  }

  public List<Rule> getDerivedRules() {
    return derivedRules;
  }

  public List<Rule> getOriginalRules() {
    List<Rule> result = new ArrayList<>();
    result.addAll(this.rules);
    result.addAll(this.removedRules);
    return result;
  }

  public List<Rule> getRemovedRules() {
    return removedRules;
  }

  public Model getModel() {
    return model;
  }

  public void printSummary(String message) {
    log.warn("--------------            {}            --------------", message);
    log.info("");
    log.info("Original count:    {}", getOriginalRules().size());
    log.info("Processed count:   {}", getRemovedRules().size());
    log.info("Unprocessed count: {}", getRemainingRules().size());
    log.info("Combined count:    {}", getCombinedRulesSize());
    log.info("");
    log.info("");
    log.info("Original type count:    {}", getTypeCount(getOriginalRules()));
    log.info("Processed type count:   {}", getTypeCount(getRemovedRules()));
    log.info("Unprocessed type count: {}", getTypeCount(getRemainingRules()));
    log.info("Combined type count:    {}", getTypeCount(getCombinedRules().collect(Collectors.toList())));
    log.info("Combined types:      {}", getCombinedRules().map(r -> r.getClass().getSimpleName())
                                                          .distinct()
                                                          .collect(Collectors.toList()));

    logCombinedRuleSummary();
    logRemovedRuleSummary();

    log.info("");
  }

  private void logCombinedRuleSummary() {
    Map<String, Long> summary = getCombinedRules().collect(groupingBy(r -> r.getClass().getSimpleName(), Collectors.counting()));
    summary = new TreeMap<>(summary);
    log.info("");
    log.info("Combined rules summary");
    summary.forEach((type, count) -> {
      log.info("    {}: {}", StringUtils.leftPad("" + count, 5), type);
    });

    log.info("");
  }

  private void logRemovedRuleSummary() {
    if (removedRuleMap.isEmpty()) return;

    log.info("Removed rules summary");
    removedRuleMap.forEach((reason, rules) -> {
      log.info("    {}: {}", StringUtils.leftPad("" + rules.size(), 5), reason);
    });

    log.info("");
  }

  private long getTypeCount(List<Rule> rules) {
    return rules.stream()
                .map(Rule::getClass)
                .distinct()
                .count();

  }

  public void print() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

    try {
      for (Rule rule : rules) {
        System.out.println(objectMapper.writeValueAsString(rule));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }


}
