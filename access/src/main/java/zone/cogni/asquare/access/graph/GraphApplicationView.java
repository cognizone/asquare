package zone.cogni.asquare.access.graph;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class GraphApplicationView extends ApplicationView {

  private static final Logger log = LoggerFactory.getLogger(GraphApplicationView.class);

  private final String graphUri;
  private final Model model;

  private final List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks;

  public GraphApplicationView(ApplicationProfile profile, Model model, String graphUri, AccessService accessService) {
    this(profile, model, graphUri, Collections.emptyList(), accessService);
  }

  public GraphApplicationView(ApplicationProfile profile,
                              Model model,
                              String graphUri,
                              List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks,
                              AccessService accessService) {
    super(accessService, profile);
    this.model = model;
    this.graphUri = graphUri;

    this.codeBlocks = new ArrayList<>(codeBlocks);
  }

  public String getGraphUri() {
    return graphUri;
  }

  public Model getModel() {
    return model;
  }

  @Override
  public void save(List<DeltaResource> deltaResources) {
    // save delta (changes) to in memory model
    log.debug("Save delta (changes) to in memory model");
    saveToModel(deltaResources);

    // execute persist functions (i.e. save model to database, save to elastic,...)
    executeCodeBlocks(deltaResources);
  }

  protected void saveToModel(List<DeltaResource> deltaResources) {
    super.save(deltaResources);
  }

  protected void executeCodeBlocks(List<DeltaResource> deltaResources) {
    getCodeBlocks().forEach(codeBlock -> codeBlock.accept(this, deltaResources));
  }

  protected List<BiConsumer<GraphApplicationView, List<DeltaResource>>> getCodeBlocks() {
    return codeBlocks;
  }
}
