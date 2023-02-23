package zone.cogni.asquare.cube.sort;

import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import zone.cogni.asquare.cube.digest.SortedBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Sorts all statements in a model in a predictable fashion.
 * <ul>
 *   <li>first we use internal logic of <code>SortedBlock</code>s to generate predictable blank node ids</li>
 *   <li>next we convert this model in a list of <code>Statement</code>s</li>
 *   <li>finally we sort statements</li>
 * </ul>
 * <p>
 *   Sorting uses these rules:
 * </p>
 * <ul>
 *   <li>For actual sorting we first sort subject, then predicate and object.</li>
 *   <li>For sorting of type of nodes: uri resource comes first, then literal and blank node.</li>
 *   <li>For sorting of uri resource, we simply sort on uri string </li>
 *   <li>For sorting of blank nodes, we simply sort on blank node id (coming from SortedBlock digest of blank node)</li>
 *   <li>For sorting of literals language literals go before typed literal</li>
 *   <li>For sorting of language literals we first sort on language, next on value</li>
 *   <li>For sorting of typed literals we first sort on datatype, next on value</li>
 * </ul>
 *
 */
public class StatementSorter implements Function<Model, List<Statement>> {

  @Override
  public List<Statement> apply(Model model) {
    List<Statement> statements = getCanonicalStatements(model);
    statements.sort(getStatementComparator());
    return statements;
  }

  private List<Statement> getCanonicalStatements(Model model) {
    List<Statement> statements = new ArrayList<>();
    SortedBlock root = new SortedBlock(model);
    processBlock(statements, null, root);
    return statements;
  }

  private void processBlock(List<Statement> statements, SortedBlock parent, SortedBlock current) {
    if (current.getStatement() != null)
      statements.add(convertStatement(parent, current));

    current.getNestedBlocks()
           .forEach(nested -> processBlock(statements, current, nested));
  }

  private Statement convertStatement(SortedBlock parentBlock, SortedBlock currentBlock) {
    Statement statement = currentBlock.getStatement();

    Resource subject = statement.getSubject().isAnon() ? getDigestBlankNode(parentBlock)
                                                       : statement.getSubject();
    RDFNode object = statement.getObject().isAnon() ? getDigestBlankNode(currentBlock)
                                                    : statement.getObject();
    return ResourceFactory.createStatement(subject,
                                           statement.getPredicate(),
                                           object);
  }

  private ResourceImpl getDigestBlankNode(SortedBlock block) {
    AnonId anonId = new AnonId(block.getDigest());
    return new ResourceImpl(anonId);
  }

  private Comparator<? super Statement> getStatementComparator() {
    return (one, two) -> {

      if (one == two)
        return 0;

      // subject
      if (one.getSubject().isURIResource() && two.getSubject().isAnon())
        return -1;
      if (one.getSubject().isAnon() && two.getSubject().isURIResource())
        return 1;

      Resource subjectOne = one.getSubject();
      Resource subjectTwo = two.getSubject();

      if (subjectOne.isAnon()) {
        String oneId = subjectOne.asResource().getId().getLabelString();
        String twoId = subjectTwo.asResource().getId().getLabelString();
        int result = oneId.compareTo(twoId);
        if (result != 0) return result;
      }

      if (subjectOne.isURIResource()) {
        int subjectCompare = subjectOne.getURI().compareTo(subjectTwo.getURI());
        if (subjectCompare != 0) return subjectCompare;
      }

      // predicate
      String predicateUriOne = one.getPredicate().asResource().getURI();
      String predicateUriTwo = two.getPredicate().asResource().getURI();
      int predicateCompare = predicateUriOne.compareTo(predicateUriTwo);
      if (predicateCompare != 0) return predicateCompare;

      // object

      // order: uri, literal and anon
      if (one.getObject().isURIResource() && !two.getObject().isURIResource())
        return -1;
      if (one.getObject().isLiteral() && two.getObject().isURIResource())
        return 1;
      if (one.getObject().isLiteral() && two.getObject().isAnon())
        return -1;
      if (one.getObject().isAnon() && !two.getObject().isAnon())
        return 1;

      // order uri
      if (one.getObject().isURIResource()) {
        String oneObjectUri = one.getObject().asResource().getURI();
        String twoObjectUri = two.getObject().asResource().getURI();
        int compare = oneObjectUri.compareTo(twoObjectUri);
        if (compare != 0) return compare;
      }

      // order literal
      if (one.getObject().isLiteral()) {
        Literal literalOne = one.getObject().asLiteral();
        Literal literalTwo = two.getObject().asLiteral();

        // different types
        if (literalOne.getLanguage() != null && literalTwo.getLanguage() == null)
          return -1;
        if (literalOne.getDatatypeURI() != null && literalTwo.getDatatypeURI() == null)
          return 1;

        // language different
        if (literalOne.getLanguage() != null) {
          int comparison = literalOne.getLanguage().compareTo(literalTwo.getLanguage());
          if (comparison != 0) return comparison;
        }

        // datatype different
        if (literalOne.getDatatypeURI() != null) {
          int comparison = literalOne.getDatatypeURI().compareTo(literalTwo.getDatatypeURI());
          if (comparison != 0) return comparison;
        }

        // datatype different
        int comparison = literalOne.getString().compareTo(literalTwo.getString());
        if (comparison != 0) return comparison;
      }

      // order anon
      if (one.getObject().isAnon()) {
        String oneId = one.getObject().asResource().getId().getLabelString();
        String twoId = two.getObject().asResource().getId().getLabelString();
        int comparison = oneId.compareTo(twoId);
        if (comparison != 0) return comparison;
      }

      if(one.equals(two)) {
        return 0;
      }
      throw new RuntimeException("missed a case");
    };
  }
}
