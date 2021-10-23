package zone.cogni.asquare.cube.digest;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import java.util.function.Function;

/**
 * <p>
 * Tries to generate a predictable digest for a model.
 * </p>
 * <p>
 * For this to be possible we must be able to sort all triples.
 * First, each triple will receive its own digest;
 * next, triples are sorted on their digest;
 * finally, we can join all digests to calculate the final digest.
 * </p>
 * <p>
 * For calculation of digest of a triple we can simply generate a predicable string for the triple.
 * That string can then be run through a digest algorithm to link the triple to a digest.
 * </p>
 * <p>
 * Blank nodes, however, add an extra layer of complexity.
 * Since blank nodes are "somewhat random" in their naming, we cannot rely on blank nodes.
 * We must use their referenced data, as input for the digest of the blank node.
 * </p>
 * <pre>{@code
 * <http://demo.com/1> <http://demo.com/model/measurement> _:a.
 * _:a <http://demo.com/model/value> "18.6"^^xsd:decimal.
 * _:a <http://demo.com/model/unit> <http://demo.com/model/temperature>.
 * }</pre>
 * <p>
 * From this example, it should be clear that the triple with blank node only makes sense
 * if you include the triples where the blank node is a subject. If there are other objects which
 * are also blank nodes this continues recursively.
 * </p>
 * <p>
 * For the calculation of the digest of a triple where the "object" is a blank node, the algorithm needs
 * to be adapted. Here we first calculate the digests of the triples where the
 * blank node is a subject. These triples are then sorted and according to their digests
 * and then are joined to the "subject" "predicate" part of the original triple (where the "object" was
 * the blank node.
 * </p>
 * <p>
 * This way a set of related triples gets a single digest.
 * </p>
 * <p>
 * In case we have blank nodes recursively, the algorithm runs recursively, depth first.
 * </p>
 * <p>
 * As starting points of the sets of related triples we have
 *   <ol>
 *     <li>Triples where "subject" is a URI resource,
 *     <ol>
 *     <li>
 *      in this case, if the "object" is also a URI resource, the set only contains one triple
 *     </li>
 *     <li>
 *       in the case the "object" is a blank node, the recursion of triples starts, continuing until
 *       we have all blank nodes
 *     </li>
 *     </ol>
 *     </li>
 *     <li>
 *       <p>
 *       Triples where "subject" is a blank node, but the blank node is never used as an "object".
 *       In this case all triples on the same (blank node) "subject" will be collected in to one set
 *       and will follow the blank node algorithm as described about.
 *       </p>
 *       <p>Note: in case a blank node is used as an object, it would already be included in a set of the first case</p>
 *     </li>
 *   </ol>
 * </p>
 * <p>
 *   Finally, it should be noted that if a blank node is an "object" but it can be reached via 2 different paths
 *   the algorithm would not work, so we must fail at the start.
 * </p>
 */
public class ModelDigest implements Function<Model, String> {

  private static final Query illegalGraphQuery = getIllegalGraphQuery();

  private static Query getIllegalGraphQuery() {
    String query =
            "  ask {" +
            "    ?s ?p ?o." +
            "    filter (isblank(?o))" +
            "  }" +
            "  group by ?o" +
            "  having (count(?s) > 1)";
    return QueryFactory.create(query);
  }

  @Override
  public String apply(Model model) {
    if (isIllegalGraph(model)) throw new RuntimeException("blank node is used more than once");

    return new SortedBlock(model).getDigest();
  }

  private boolean isIllegalGraph(Model model) {
    RdfStoreService rdfStore = new InternalRdfStoreService(model);
    return rdfStore.executeAskQuery(illegalGraphQuery, new QuerySolutionMap());
  }
}
