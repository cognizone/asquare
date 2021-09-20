package zone.cogni.asquare.edit.delta;

import org.apache.jena.rdf.model.Statement;
import zone.cogni.asquare.edit.DeltaResource;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MergingDelta extends Delta {

  @Nonnull
  private final Collection<TypedResourceDelta> deltas = new ArrayList<>();

  public MergingDelta(DeltaResource... resources) {
    for (DeltaResource resource : resources) {
      deltas.add(resource.getDelta());
    }
  }

  public MergingDelta(List<DeltaResource> resources) {
    deltas.addAll(resources.stream()
                          .map(DeltaResource::getDelta)
                          .collect(Collectors.toList()));
  }

  @Override
  public List<Statement> getAddStatements() {
    return deltas.stream()
            .flatMap(delta -> delta.getAddStatements().stream())
            .collect(Collectors.toList());
  }

  @Override
  public List<Statement> getRemoveStatements() {
    return deltas.stream()
            .flatMap(delta -> delta.getRemoveStatements().stream())
            .collect(Collectors.toList());
  }
}
