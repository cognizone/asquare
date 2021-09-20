package zone.cogni.asquare.edit;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Deprecated
public class BasicRdfTransactionService {

  private final RdfStoreService rdfStoreService;

  public BasicRdfTransactionService(RdfStoreService rdfStoreService) {
    this.rdfStoreService = rdfStoreService;
  }

  public void update(List<UpdatableResource> resourceList) {
    extractAllDistinctResources(resourceList)
            .forEach(this::update);
  }

  private void update(TypedResource resource) {

    Model model = ModelFactory.createDefaultModel();

    List<String> typeUris = resource.getType()
            .getRules(RdfType.class).stream()
            .map(SingleValueRule::getValue)
            .collect(Collectors.toList());

    typeUris.forEach(typeUri -> model.add(resource.getResource(), RDF.type, ResourceFactory.createResource(typeUri)));

    resource.getType()
            .getAttributes().values()
            .forEach(a -> {
              // hmm
              resource.getValues(a)
                      .forEach(val -> model.add(resource.getResource(), ResourceFactory.createProperty(a.getUri()), getAsRdfNode(val)));
            });

    rdfStoreService.executeUpdateQuery("delete { ?s ?p ?o } " +
                                       "where { " +
                                       " ?s ?p ?o. FILTER (?s = <" + resource.getResource().getURI() + ">)" +
                                       "}");

    rdfStoreService.addData(model);
  }

  private RDFNode getAsRdfNode(Object object) {
    if (object instanceof RdfValue) {
      RdfValue rdfValue = (RdfValue) object;
      return rdfValue.isResource() ? rdfValue.getResource() : rdfValue.getLiteral();
    }
//    if (object instanceof TypedResource) return ((UpdatableResource) object).getResource();
//    else if (object instanceof RDFNode) return (RDFNode) object;
    throw new IllegalStateException("Unknown type " + object.getClass().getName());
  }

  private Stream<? extends TypedResource> extractAllDistinctResources(List<? extends TypedResource> resources) {

    Set<String> distinctUris = new HashSet<>();
    Predicate<TypedResource> isDistinct = resource -> {
      if (distinctUris.contains(resource.getResource().getURI())) return false;
      else {
        distinctUris.add(resource.getResource().getURI());
        return true;
      }
    };

    return Stream.concat(
            resources.stream(),
            resources.stream()
                    .flatMap(r -> r.getType().getAttributes().values().stream()
                    .flatMap(attribute -> r.getValues(attribute).stream()))
                    .filter(val -> val instanceof TypedResource)
                    .map(val -> (TypedResource) val)
                    .flatMap(val -> extractAllDistinctResources(Collections.singletonList(val))))
            .filter(isDistinct);
  }

}
