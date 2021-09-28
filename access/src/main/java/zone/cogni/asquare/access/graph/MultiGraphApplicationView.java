package zone.cogni.asquare.access.graph;

import org.apache.jena.rdf.model.Model;
import org.springframework.util.StringUtils;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class MultiGraphApplicationView extends GraphApplicationView {

  private final UnaryOperator<String> getGraphQueryByResourceUri;
  private final GraphViewService graphViewService;

  public MultiGraphApplicationView(ApplicationProfile profile,
                                   Model model,
                                   String graphUri,
                                   AccessService accessService,
                                   UnaryOperator<String> getGraphQueryByResourceUri,
                                   GraphViewService graphViewService) {
    super(profile, model, graphUri, accessService);
    this.getGraphQueryByResourceUri = getGraphQueryByResourceUri;
    this.graphViewService = graphViewService;
  }

  public MultiGraphApplicationView(ApplicationProfile profile,
                                   Model model,
                                   String graphUri,
                                   List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks,
                                   AccessService accessService,
                                   UnaryOperator<String> getGraphQueryByResourceUri,
                                   GraphViewService graphViewService) {
    super(profile, model, graphUri, codeBlocks, accessService);
    this.getGraphQueryByResourceUri = getGraphQueryByResourceUri;
    this.graphViewService = graphViewService;
  }

  @Override
  public TypedResource find(Supplier<ApplicationProfile.Type> typeSupplier, String resourceUri) {
    String alternativeGraph = getGraphQueryByResourceUri.apply(resourceUri);
    if (StringUtils.hasText(alternativeGraph)) {
      return graphViewService.get(alternativeGraph).find(typeSupplier, resourceUri);
    }
    return super.find(typeSupplier, resourceUri);
  }
}
