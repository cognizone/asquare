package zone.cogni.asquare.cube.operation;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OperationGroupResult {

  private final OperationGroup operationGroup;
  private final String contextUri;

  private final Map<String, SingleOperationGroupResult> singleOperationGroupResults = new HashMap<>();

  public OperationGroupResult(OperationGroup operationGroup, String contextUri) {
    this.operationGroup = operationGroup;
    this.contextUri = contextUri;
  }

  public OperationGroup getOperationGroup() {
    return operationGroup;
  }

  public String getContextUri() {
    return contextUri;
  }

  public void addSelectedUris(List<String> selectedUris) {
    selectedUris.forEach(selectedUri -> {
      singleOperationGroupResults.put(selectedUri,
                                      new SingleOperationGroupResult(selectedUri, this));
    });
  }

  public void addOperationResult(String selectedUri, OperationResult operationResult) {
    singleOperationGroupResults.get(selectedUri)
                               .addOperationResults(operationResult);
  }

  public boolean hasSelectedUri() {
    return !singleOperationGroupResults.isEmpty();
  }

  /**
   * Special case since we do NOT support LISTs yet.
   */
  public String getSelectedUri() {
    try {
      Preconditions.checkState(singleOperationGroupResults.size() <= 1);
      return getOperationResults().getSelectedUri();
    }
    catch (RuntimeException e) {
      throw e;
    }
  }

  public SingleOperationGroupResult getOperationResults() {
    if (operationGroup.isMultiselect())
      throw new RuntimeException("Operation group " + operationGroup.getPathId() + " is not single select.");

    if (singleOperationGroupResults.size() > 1)
      throw new RuntimeException("Operation group " + operationGroup.getPathId() +
                                 " has more than one result " + singleOperationGroupResults.size());

    return singleOperationGroupResults.isEmpty() ? new DummySingleOperationGroupResult(this)
                                                 : singleOperationGroupResults.values().stream().findFirst().get();
  }

  public SingleOperationGroupResult getOperationResults(String uri) {
    if (!singleOperationGroupResults.containsKey(uri)) {
      throw new RuntimeException("Operation group " + operationGroup.getPathId() + " has no result uri " + uri +
                                 " uris found: " + singleOperationGroupResults.keySet());
    }

    return singleOperationGroupResults.get(uri);
  }

  public static class SingleOperationGroupResult {

    private final String selectedUri;
    private final OperationGroupResult operationGroupResult;
    private List<OperationResult> operationResults = new ArrayList<>();

    public SingleOperationGroupResult(String selectedUri, OperationGroupResult operationGroupResult) {
      this.selectedUri = selectedUri;
      this.operationGroupResult = operationGroupResult;
    }

    public String getSelectedUri() {
      return selectedUri;
    }

    public List<OperationResult> getOperationResults() {
      return operationResults;
    }

    public String getContextUri() {
      return operationGroupResult.getContextUri();
    }

    public OperationGroup getOperationGroup() {
      return operationGroupResult.getOperationGroup();
    }

    public OperationResult getOperationResult(Operation operation) {
      return operationResults.stream()
                             .filter(result -> result.getOperation().getId()
                                                     .equals(operation.getId()))
                             .findFirst()
                             .orElse(null);
    }

    public void addOperationResults(OperationResult operationResult) {
      this.operationResults.add(operationResult);
    }

    public void addOperationResults(List<OperationResult> operationResults) {
      this.operationResults.addAll(operationResults);
    }
  }

  public static class DummySingleOperationGroupResult extends SingleOperationGroupResult {

    public DummySingleOperationGroupResult(OperationGroupResult operationGroupResult) {
      super("http://i.do.not/exist", operationGroupResult);
    }

    @Override
    public List<OperationResult> getOperationResults() {
      return getOperationGroup().getOperations()
                                .stream()
                                .map(this::getOperationResult)
                                .collect(Collectors.toList());
    }

    @Override
    public OperationResult getOperationResult(Operation operation) {
      OperationResult operationResult = new OperationResult(operation, getSelectedUri());
      operationResult.setEnabled(false);
      return operationResult;
    }

    public void addOperationResults(OperationResult operationResult) {
      throw new UnsupportedOperationException();
    }

    public void addOperationResults(List<OperationResult> operationResults) {
      throw new UnsupportedOperationException();
    }

  }

}
