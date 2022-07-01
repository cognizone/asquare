package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.cube.util.TimingUtil;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class OperationResultProcessor {

  private static final Logger log = LoggerFactory.getLogger(OperationResultProcessor.class);

  /**
   * fast mode reaches 2800 queries per second
   * slow mode get 830 queries per second
   * <p>
   * fast mode querying is about 3.4 times faster; total time spent drops 70% !
   */
  private static final boolean FAST_QUERIES = true;

  private final OperationConfiguration configuration;
  private final SpelService spelService;

  private final OperationRoot operationRoot;

  private final Map<String, Object> context = new HashMap<>();

  public OperationResultProcessor(
    OperationConfiguration configuration,
    SpelService spelService,
    Resource operationsResource
  ) {
    this(configuration, spelService, () -> OperationRoot.load(operationsResource), Collections.emptyMap());
  }

  public OperationResultProcessor(
    OperationConfiguration configuration,
    SpelService spelService,
    Resource operationsResource,
    Map<String, Object> context
  ) {
    this(configuration, spelService, () -> OperationRoot.load(operationsResource), context);
  }

  public OperationResultProcessor(
    OperationConfiguration configuration,
    SpelService spelService,
    Supplier<OperationRoot> operationRootSupplier
  ) {
    this(configuration, spelService, operationRootSupplier, Collections.emptyMap());
  }

  public OperationResultProcessor(
    OperationConfiguration configuration,
    SpelService spelService,
    Supplier<OperationRoot> operationRootSupplier,
    Map<String, Object> context
  ) {
    this.configuration = configuration;
    this.spelService = spelService;

    OperationRoot operationRoot = operationRootSupplier.get();
    validate(operationRoot);
    this.operationRoot = operationRoot;

    this.context.putAll(context);
  }

  private void validate(OperationRoot operationRoot) {
    operationRoot.validate();
    log.info("Operation id's: {}",  String.join(", ", operationRoot.getOperationIds()));
  }

  public OperationRoot getOperationRoot() {
    return operationRoot;
  }

  public boolean validateAny(Supplier<Set<String>> permissions,
                             Model model,
                             String uri,
                             Set<String> pathIds) {
    Set<String> rootGroupIds = pathIds.stream()
                                      .map(pathId -> StringUtils.substringBefore(pathId, "/"))
                                      .collect(Collectors.toSet());
    if (rootGroupIds.size() != 1)
      throw new RuntimeException("must have exactly 1 root group id: " + rootGroupIds);

    String rootGroupId = rootGroupIds.stream().findFirst().get();
    SingleGroupResult root = validate(permissions.get(), model, uri, rootGroupId);

    return pathIds.stream()
                  .map(this::substringAfterSlashOrInput)
                  .anyMatch(currentPath -> findValidateOperation(root, currentPath));
  }

  public boolean validateOne(Supplier<Set<String>> permissions,
                             Model model,
                             String uri,
                             String pathId) {
    String rootGroupId = StringUtils.substringBefore(pathId, "/");
    SingleGroupResult root = validate(permissions.get(), model, uri, rootGroupId);

    String currentPath = substringAfterSlashOrInput(pathId);
    return findValidateOperation(root, currentPath);
  }

  /*
   * substringAfter and substringAfterLast (also see next method) return an
   * empty string if the separator ("/") is not found,
   * but for operations e.g. create/save-button that causes a problem
   * */
  private String substringAfterSlashOrInput(String pathId) {
    return StringUtils.contains(pathId, "/") ?
           StringUtils.substringAfter(pathId, "/") :
           pathId;
  }

  private String substringAfterLastSlashOrInput(String pathId) {
    return StringUtils.contains(pathId, "/") ?
           StringUtils.substringAfterLast(pathId, "/") :
           pathId;
  }

  private boolean findValidateOperation(SingleGroupResult root, String currentPath) {
    SingleGroupResult result = findValidateGroup(root, currentPath);

    String operationId = substringAfterLastSlashOrInput(currentPath);
    return result.getOperationResult(operationId)
                 .isEnabled();
  }

  private SingleGroupResult findValidateGroup(SingleGroupResult root, String currentPath) {
    SingleGroupResult result = root;

    while (currentPath.contains("/")) {
      String currentGroupId = StringUtils.substringBefore(currentPath, "/");
      result = result.getGroupResult(currentGroupId);
      currentPath = substringAfterSlashOrInput(currentPath);
    }

    return result;
  }

  /**
   * @param modelSupplier supplier of a single model containing all graphs of all uris
   * @return list of SingleGroupResults, one for each uri in order of uris
   */
  public List<SingleGroupResult> validate(Supplier<Set<String>> permissionsSupplier,
                                          Supplier<Model> modelSupplier,
                                          List<String> uris,
                                          String operationGroupId) {
    return uris.stream()
               .map(uri-> validate(permissionsSupplier, modelSupplier, uri, operationGroupId))
               .collect(Collectors.toList());
  }

  public SingleGroupResult validate(Supplier<Set<String>> permissionsSupplier,
                                    Supplier<Model> modelSupplier,
                                    String uri,
                                    String operationGroupId) {
    Context context = Context.create(permissionsSupplier, modelSupplier);

    try {
      if (operationRoot.isSingleFile()) {
        return processSingleFileCase(context, uri, operationGroupId);
      }
      else {
        return processMergedFilesCase(context, uri, operationGroupId);
      }
    }
    finally {
      log.debug("(validate) {}", context.monitor);
    }
  }


  public SingleGroupResult validate(Set<String> permissions,
                                    Model model,
                                    String uri,
                                    String operationGroupId) {
    return validate(() -> permissions, () -> model, uri, operationGroupId);
  }

  private SingleGroupResult processSingleFileCase(Context context, String uri, String operationGroupId) {
    OperationGroup operationGroup = operationRoot.getByPathId(operationGroupId);

    SingleGroupResult result = new SingleGroupResult(operationGroup, uri, null);
    processGroup(context, result);

    return result;
  }

  private SingleGroupResult processMergedFilesCase(Context context, String uri, String operationGroupId) {
    OperationGroup rootGroup = operationRoot.getByPathId("root");
    SingleGroupResult rootGroupResult = new SingleGroupResult(rootGroup, uri, null);

    OperationGroup operationGroup = getMergedGroup(operationGroupId);
    processChildGroup(context, rootGroupResult, operationGroup);

    if (log.isDebugEnabled()) log.debug("root group result: \n{}", rootGroupResult);
    return rootGroupResult.getGroupResult(operationGroupId);
  }

  private OperationGroup getMergedGroup(String operationGroupId) {
    if (Objects.equals("..", operationGroupId)) return null;

    boolean hasRoot = operationRoot.hasOperationGroupWithId("root");
    if (!hasRoot) return null;

    OperationGroup rootGroup = operationRoot.getByPathId("root");
    return rootGroup.getOperationGroup(operationGroupId);
  }

  private void processGroup(Context context, SingleGroupResult rootGroup) {
    processChildGroups(context, rootGroup);
    processOperations(context, rootGroup);
  }

  private void processChildGroups(Context context, SingleGroupResult rootGroup) {
    if (!rootGroup.operationGroup.hasOperationGroups()) return;

    List<OperationGroup> childGroups = rootGroup.operationGroup.getOperationGroups();
    childGroups.forEach(childGroup -> processChildGroup(context, rootGroup, childGroup));
  }

  private void processChildGroup(Context context,
                                 SingleGroupResult rootGroup,
                                 OperationGroup childGroup) {
    String contextUri = rootGroup.getContextUri();
    // TODO not sure if this is the correct way: lookup always
    List<String> operationGroupUris = getOperationGroupUris(context, childGroup, contextUri);

    if (childGroup.isMultiselect()) {
      // TODO not sure if this is the correct way see other comments
      operationGroupUris.forEach(childContextUri -> {
        createAndProcessGroup(context, rootGroup, childGroup, childContextUri);
      });
    }
    else {
      if (operationGroupUris.size() > 1) {
        String message = "group " + childGroup.getPathId() + " returned multiple results: " + operationGroupUris;
        throw new RuntimeException(message);
      }

      String childContextUri = operationGroupUris.isEmpty() ? null : operationGroupUris.get(0);
      createAndProcessGroup(context, rootGroup, childGroup, childContextUri);
    }
  }

  private void createAndProcessGroup(Context context, SingleGroupResult rootGroup, OperationGroup childGroup, String childContextUri) {
    SingleGroupResult singleGroupResult = new SingleGroupResult(childGroup, childContextUri, rootGroup);
    rootGroup.addSingleGroup(singleGroupResult);

    processGroup(context, singleGroupResult);
  }

  private void processOperations(Context context, SingleGroupResult groupResult) {
    if (!groupResult.operationGroup.hasOperations()) return;

    List<Operation> operations = groupResult.operationGroup.getOperations();
    operations.forEach(operation -> {
      getOrCreateOperationResult(context, groupResult, operation.getId());
    });
  }

  private boolean calculateEnabled(Context context, SingleGroupResult groupResult, OperationResult operationResult) {
    Operation operation = operationResult.getOperation();
    if (operation.hasTemplate()) {
      return isTemplateEnabled(context, operationResult);
    }

    if (operation.isNegate())
      throw new RuntimeException("negate on operation " + operation.getPathId() + " not supported yet.");

    if (operation.hasRequires()) {
      return isRequiresEnabled(context, groupResult, operation);
    }

    if (operation.hasOptional()) {
      return isOptionalEnabled(context, groupResult, operation);
    }

    throw new RuntimeException("operation must be either 'template', 'requires' or 'optional':" +
                               " problem with " + operation.getPathId());
  }

  private boolean isRequiresEnabled(Context context, SingleGroupResult groupResult, Operation operation) {
    return operation.getRequires()
                    .stream()
                    .allMatch(currentOperation -> isEnabled(context, groupResult, currentOperation));
  }

  private boolean isOptionalEnabled(Context context, SingleGroupResult groupResult, Operation operation) {
    return operation.getOptional()
                    .stream()
                    .anyMatch(currentOperation -> isEnabled(context, groupResult, currentOperation));
  }

  private boolean isEnabled(Context context, SingleGroupResult groupResult, List<String> path) {

    // TODO lookup others if referenced!
    List<SingleGroupResult> currentGroups = getSingleGroupResults(context, groupResult, path);

    boolean result = true;
    String operationId = path.get(path.size() - 1);
    for (SingleGroupResult currentGroup : currentGroups) {
      OperationResult operationResult = getOrCreateOperationResult(context, currentGroup, operationId);

      if (!operationResult.isEnabled())
        result = false;
    }
    return result;
  }

  private List<SingleGroupResult> getSingleGroupResults(Context context, SingleGroupResult groupResult, List<String> requiresList) {
    List<SingleGroupResult> result = new ArrayList<>();
    result.add(groupResult);
    List<String> path = requiresList.subList(0, requiresList.size() - 1);

    return getSingleGroupResults(context, result, path);
  }

  private List<SingleGroupResult> getSingleGroupResults(Context context, List<SingleGroupResult> rootResults, List<String> path) {
    if (path.isEmpty()) return rootResults;

    String id = path.get(0);

    List<SingleGroupResult> result;
    if (id.equals("..")) {
      result = rootResults.stream()
                          .map(SingleGroupResult::getParent)
                          .collect(Collectors.toList());
    }
    else if (id.endsWith("*")) {
      String actualId = id.substring(0, id.length() - 1);
      result = rootResults.stream()
                          .flatMap(groupResult -> {
                            // already present
                            List<SingleGroupResult> childGroupResults = groupResult.getGroupResults(actualId);
                            if (!childGroupResults.isEmpty())
                              return childGroupResults.stream();

                            // does not exists, even after re-query :(
                            // which we should avoid btw!
                            String contextUri = groupResult.getContextUri();
                            OperationGroup childGroup = groupResult.getOperationGroup().getOperationGroup(actualId);
                            List<String> operationGroupUris = getOperationGroupUris(context, childGroup, contextUri);

                            List<SingleGroupResult> emptyList = new ArrayList<>();
                            if (operationGroupUris.isEmpty()) emptyList.stream();

                            // create using operationGroupUris and return results
                            operationGroupUris.forEach(childContextUri -> {
                              createAndProcessGroup(context, groupResult, childGroup, childContextUri);
                            });
                            return groupResult.getGroupResults(actualId).stream();
                          })
                          .collect(Collectors.toList());
    }
    else {
      result = rootResults.stream()
                          .flatMap(groupResult -> getOrCreateOperationGroupResult(context, groupResult, id).stream())
                          .collect(Collectors.toList());
    }

    return getSingleGroupResults(context, result, path.subList(1, path.size()));
  }

  private List<SingleGroupResult> getOrCreateOperationGroupResult(Context context, SingleGroupResult group, String groupId) {
    if (!group.hasGroupResult(groupId)) {
      // TODO single result always null context; but what if we have multiple results? how do we know "empty" list
      OperationGroup operationGroup = group.getOperationGroup().getOperationGroup(groupId);
      processChildGroup(context, group, operationGroup);
    }

    return group.getGroupResults(groupId);
  }

  private OperationResult getOrCreateOperationResult(Context context, SingleGroupResult group, String operationId) {
    if (!group.hasOperationResult(operationId)) {
      Operation operation = group.getOperationGroup().getOperation(operationId);
      processOperation(context, group, operation);
    }

    return group.getOperationResult(operationId);
  }

  private void processOperation(Context context, SingleGroupResult groupResult, Operation operation) {
    OperationResult operationResult = new OperationResult(operation, groupResult.getContextUri());

    boolean isOperationWithUri = operationResult.getUri() != null;
    boolean isOperationEmptyModel = isEmpty(context.rdfStore);
    boolean isOperationAllowed = isOperationAllowed(context.permissions, operation);
    boolean enabled = (isOperationWithUri || isOperationEmptyModel)
                      && isOperationAllowed
                      && calculateEnabled(context, groupResult, operationResult);
    operationResult.setEnabled(enabled);
    groupResult.addOperation(operationResult);
  }

  private boolean isEmpty(RdfStoreService rdfStore) {
    return ((InternalRdfStoreService) rdfStore).getModel().isEmpty();
  }

  private boolean isOperationAllowed(Set<String> permissions, Operation operation) {
    boolean securityDisabled = !configuration.isSecurityEnabled();
    return securityDisabled || permissions.contains(getPermission(operation));
  }

  private String getPermission(Operation operation) {
    return operationRoot.isSingleFile() ? operation.getPathId()
                                        : StringUtils.removeStart(operation.getPathId(), "root/");
  }

  private boolean isTemplateEnabled(Context context, OperationResult operationResult) {
    Operation operation = operationResult.getOperation();

    Operation.TemplateType templateType = operation.getTemplateType();
    if (templateType == Operation.TemplateType.boolean_value) {
      if (operation.isNegate()) throw new RuntimeException("Please do not make it complex!");
      return Boolean.parseBoolean(operation.getFullTemplate());
    }

    if (templateType == Operation.TemplateType.ask_query) {
      long start = System.nanoTime();
      try {
        boolean result = FAST_QUERIES ? askFast(context.rdfStore, operationResult, operation)
                                      : askSlow(context.rdfStore, operationResult, operation);
        return operation.isNegate() != result;
      }
      finally {
        context.monitor.addTiming(operation.getPathId(), System.nanoTime() - start);
      }
    }

    throw new RuntimeException("unexpected template type " + templateType + " for operation " + operation);
  }

  private boolean askFast(RdfStoreService rdfStore, OperationResult operationResult, Operation operation) {
    Query askQuery = operation.getContextQuery();
    if (askQuery == null) return askSlow(rdfStore, operationResult, operation);

    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("contextUri", ResourceFactory.createResource(operationResult.getUri()));
    return rdfStore.executeAskQuery(askQuery, bindings);
  }

  private boolean askSlow(RdfStoreService rdfStore, OperationResult operationResult, Operation operation) {
    String template = operationRoot.getPrefixQuery() + operation.getFullTemplate();
    context.put("uri", operationResult.getUri());
    String sparql = spelService.processTemplate(template, context);

    return rdfStore.executeAskQuery(sparql);
  }


  private List<String> getOperationGroupUris(Context context, OperationGroup operationGroup, String uri) {
    if (!operationGroup.hasSelectorQuery()) return Collections.singletonList(uri);

    long start = System.nanoTime();
    try {
      return FAST_QUERIES ? selectFast(context.rdfStore, operationGroup, uri)
                          : selectSlow(context.rdfStore, operationGroup, uri);
    }
    finally {
      context.monitor.addTiming(operationGroup.getPathId(), System.nanoTime() - start);
    }
  }

  private List<String> selectFast(RdfStoreService rdfStore, OperationGroup operationGroup, String uri) {
    Query selectorQuery = operationGroup.getContextSelectorQuery();
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("contextUri", ResourceFactory.createResource(uri));
    return rdfStore.executeSelectQuery(selectorQuery, bindings, OperationResultProcessor::convertToList);
  }

  private List<String> selectSlow(RdfStoreService rdfStore, OperationGroup operationGroup, String uri) {
    String template = operationRoot.getPrefixQuery() + operationGroup.getSelectorQuery();
    context.put("uri", uri);
    String sparql = spelService.processTemplate(template, context);
    try {
      return rdfStore.executeSelectQuery(sparql, OperationResultProcessor::convertToList);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Query failed:\n" + sparql, e);
    }
  }

  private static List<String> convertToList(ResultSet resultSet) {
    List<String> result = new ArrayList<>();

    resultSet.forEachRemaining(querySolution -> {
      result.add(querySolution.get("uri").asResource().getURI());
    });

    return result;
  }

  public static class SingleGroupResult {

    private final OperationGroup operationGroup;

    private final String contextUri;

    @JsonIgnore
    private final SingleGroupResult parent;

    private final List<SingleGroupResult> childSingleGroupResults = new ArrayList<>();
    private final List<OperationResult> operationResults = new ArrayList<>();

    public SingleGroupResult(OperationGroup operationGroup, String contextUri, SingleGroupResult parent) {
      this.operationGroup = operationGroup;
      this.contextUri = contextUri;
      this.parent = parent;
    }

    public OperationGroup getOperationGroup() {
      return operationGroup;
    }

    public String getContextUri() {
      return contextUri;
    }

    public SingleGroupResult getParent() {
      return parent;
    }

    public boolean hasGroupResults() {
      return !childSingleGroupResults.isEmpty();
    }

    public List<SingleGroupResult> getGroupResults() {
      return Collections.unmodifiableList(childSingleGroupResults);
    }

    public boolean hasGroupResult(String id) {
      return childSingleGroupResults.stream()
                                    .anyMatch(result -> result.getOperationGroup().getId().equals(id));
    }

    public List<SingleGroupResult> getGroupResults(String id) {
      return childSingleGroupResults.stream()
                                    .filter(result -> result.getOperationGroup().getId().equals(id))
                                    .collect(Collectors.toList());
    }

    public SingleGroupResult getGroupResult(String id) {
      return childSingleGroupResults.stream()
                                    .filter(result -> result.getOperationGroup().getId().equals(id))
                                    .peek(this::ensureNotMultiselect)
                                    .findFirst()
                                    .orElseThrow(() -> new RuntimeException("cannot find group with id " + id));
    }

    private void ensureNotMultiselect(SingleGroupResult result) {
      if (result.getOperationGroup().isMultiselect())
        throw new RuntimeException("result with path " + result.getOperationGroup().getPathId() + " is multiselect");
    }

    public boolean hasOperationResults() {
      return !operationResults.isEmpty();
    }

    public List<OperationResult> getOperationResults() {
      return Collections.unmodifiableList(operationResults);
    }

    public boolean hasOperationResult(String id) {
      return operationResults.stream()
                             .anyMatch(result -> result.getOperation().getId().equals(id));
    }

    public OperationResult getOperationResult(String id) {
      return operationResults.stream()
                             .filter(result -> result.getOperation().getId().equals(id))
                             .findFirst()
                             .orElseThrow(() -> new RuntimeException("cannot find operation with id '" + id + "'"));
    }

    public void addSingleGroup(@Nonnull SingleGroupResult childGroupResult) {
      childSingleGroupResults.add(childGroupResult);
    }

    public void addOperation(@Nonnull OperationResult operationResult) {
      Optional<OperationResult> first = operationResults.stream()
                                                        .filter(x -> x.getOperation().getId()
                                                                      .equals(operationResult.getOperation().getId()))
                                                        .findFirst();
      if (first.isPresent()) {
        throw new RuntimeException("weird, duplicate");
      }
      operationResults.add(operationResult);
    }

    public String toString() {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
      }
      catch (JsonProcessingException e) {
        throw new RuntimeException("toString failed", e);
      }
    }
  }

  private static class Monitor {

    private final MonitorSummary monitorSummary = new MonitorSummary(null);
    private final TreeMap<String, MonitorSummary> pathIdSummary = new TreeMap<>();

    private void addTiming(String operationId, long queryTime) {
      monitorSummary.addTiming(queryTime);

      if (!log.isDebugEnabled()) return;
      if (!pathIdSummary.containsKey(operationId)) {
        pathIdSummary.put(operationId, new MonitorSummary(monitorSummary));
      }
      pathIdSummary.get(operationId).addTiming(queryTime);
    }

    public String toString() {
      String result = "\n" + monitorSummary;
      Set<Map.Entry<String, MonitorSummary>> entries = pathIdSummary.entrySet();
      for (Map.Entry<String, MonitorSummary> entry : entries) {
        result += "\n" + entry.getValue() + " - " + entry.getKey();
      }
      return result;
    }
  }

  private static class MonitorSummary {
    private final MonitorSummary total;

    private int count;
    private long timing;

    public MonitorSummary(MonitorSummary total) {
      this.total = total;
    }

    public void addTiming(long queryTime) {
      count += 1;
      timing += queryTime;
    }

    public String toString() {
      String count = StringUtils.leftPad(String.valueOf(this.count), 4) + "x";
      String totalTime = StringUtils.leftPad(TimingUtil.nanoToMillis(timing, 2), 8);
      String averageTime = StringUtils.leftPad("(" + TimingUtil.nanoToMillis(timing / this.count, 2) + ")", 10);
      String percentage = getPercentage();
      return count
             + totalTime
             + percentage
             + averageTime;
    }

    private String getPercentage() {
      if (total == null) return StringUtils.leftPad("%", 8);

      long percentageTimes100 = timing * 10000 / total.timing;
      if (percentageTimes100 < 100) {
        String result = "0." + StringUtils.leftPad(String.valueOf(percentageTimes100), 2, '0');
        return StringUtils.leftPad(result, 8);
      }

      String fullString = String.valueOf(percentageTimes100);
      String result = fullString.substring(0, fullString.length() - 2) + "." + fullString
        .substring(fullString.length() - 2);
      return StringUtils.leftPad(result, 8);
    }
  }

  private static class Context {

    private static Context create(Supplier<Set<String>> permissionsSupplier, Supplier<Model> modelSupplier) {
      return new Context(new Monitor(),
                         permissionsSupplier.get(),
                         getRdfStore(modelSupplier.get()));
    }

    private static RdfStoreService getRdfStore(Model model) {
      return new InternalRdfStoreService(model);
    }

    private final Monitor monitor;
    private final Set<String> permissions;
    private final RdfStoreService rdfStore;

    private Context(Monitor monitor, Set<String> permissions, RdfStoreService rdfStore) {
      this.monitor = monitor;
      this.permissions = permissions;
      this.rdfStore = rdfStore;
    }
  }
}
