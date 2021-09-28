package zone.cogni.asquare.access.graph;

import org.apache.jena.rdf.model.Model;
import org.springframework.context.annotation.Configuration;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.util.function.CachingSupplier;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Configuration
public class GraphApplicationViewFactory {

  private final PrefixCcService prefixCcService;

  public GraphApplicationViewFactory(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public GraphApplicationView createGraphApplicationView(ApplicationProfile profile, Model model, String graphUri) {
    try (RdfStoreService rdfStoreService = new InternalRdfStoreService(model)) {
      return new GraphApplicationView(profile, model, graphUri, new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService));
    }
  }

  public GraphApplicationView createGraphApplicationView(ApplicationProfile profile,
                                                         Model model,
                                                         String graphUri,
                                                         List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks) {
    try (RdfStoreService rdfStoreService = new InternalRdfStoreService(model)) {
      return new GraphApplicationView(profile, model, graphUri, codeBlocks, new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService));
    }
  }

  public MultiGraphApplicationView createMultiGraphApplicationView(ApplicationProfile profile,
                                                         Model model,
                                                         String graphUri,
                                                         List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks,
                                                                   UnaryOperator<String> getGraphQueryByResourceUri,
                                                                   GraphViewService graphViewService) {
    try (RdfStoreService rdfStoreService = new InternalRdfStoreService(model)) {
      return new MultiGraphApplicationView(profile, model, graphUri, codeBlocks, new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService), getGraphQueryByResourceUri, graphViewService);
    }
  }



  public ApplicationView getTreeApplicationView(ApplicationProfile applicationProfile, Model model) {
    Supplier<RdfStoreService> rdfStoreServiceSupplier =
      CachingSupplier.memoize(() -> new InternalRdfStoreService(model));
    AccessService accessService = new SimpleRdfAccessService(prefixCcService, rdfStoreServiceSupplier);
    return new ApplicationView(accessService, applicationProfile);
  }
}
