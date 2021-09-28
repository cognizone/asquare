package zone.cogni.asquare.rdf;

import com.google.common.collect.Streams;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.sem.jena.model.QuerySolutionDto;
import zone.cogni.sem.jena.model.RdfBooleanNodeDto;
import zone.cogni.sem.jena.model.RdfNodeDto;
import zone.cogni.sem.jena.model.RdfStringNodeDto;
import zone.cogni.sem.jena.model.ResultSetDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ResultSetMapper {

  private ResultSetMapper() {
  }

  public static ResultSetDto resultSetToResultSetDto(ResultSet resultSet) {
    return resultSetToResultSetDto(resultSet, null);
  }

  public static Integer resultSetToAmount(ResultSet resultSet) {
    ResultSetDto resultSetDto = resultSetToResultSetDto(resultSet, null);
    String strAmount = resultSetDto.collectPropertyValue("amount");

    try {
      return Integer.parseInt(strAmount);
    }
    catch (NumberFormatException ex) {
      return 0;
    }
  }

  public static ResultSetDto resultSetToResultSetDto(ResultSet resultSet, PrefixCcService prefixCcService) {
    ResultSetDto resultSetDto = new ResultSetDto();

    List<String> vars = new ArrayList<>(resultSet.getResultVars());
    resultSetDto.setVars(vars);

    List<QuerySolutionDto> querySolutionsList = new ArrayList<>();
    while (resultSet.hasNext()) {
      querySolutionsList.add(querySolutionToQuerySolutionDto(resultSet.next(), prefixCcService));
    }
    resultSetDto.setQuerySolutions(querySolutionsList);

    return resultSetDto;
  }

  public static QuerySolutionDto querySolutionToQuerySolutionDto(QuerySolution querySolution, PrefixCcService prefixCcService) {
    QuerySolutionDto querySolutionDto = new QuerySolutionDto();
    querySolutionDto.setNodes(new HashMap<>());

    Streams.stream(querySolution.varNames()).forEach(varName -> {
      RDFNode rdfNode = querySolution.get(varName);
      querySolutionDto.getNodes().put(varName, rdfNodeToRDFNodeDto(varName, rdfNode, prefixCcService));
    });

    return querySolutionDto;
  }

  private static RdfNodeDto createNodeDto(RDFNode rdfNode) {
    if (rdfNode.asLiteral().getDatatype().getJavaClass() == Boolean.class) {
      return RdfBooleanNodeDto.create(rdfNode.asLiteral().getBoolean());
    }
    return RdfStringNodeDto.create(rdfNode.asLiteral().getLexicalForm());
  }

  private static RdfNodeDto rdfLiteral(String name, RDFNode rdfNode, PrefixCcService prefixCcService) {
    RdfNodeDto rdfNodeDto = createNodeDto(rdfNode);

    rdfNodeDto.setType("literal");
    if (prefixCcService != null) {
      try {
        rdfNodeDto.setDatatype(prefixCcService.getShortenedUri(rdfNode.asLiteral().getDatatype().getURI()));
      }
      catch (Exception ex) {
        rdfNodeDto.setDatatype(rdfNode.asLiteral().getDatatype().getURI());
      }
    }
    else {
      rdfNodeDto.setDatatype(rdfNode.asLiteral().getDatatype().getURI());
    }
    rdfNodeDto.setLanguage(rdfNode.asLiteral().getLanguage());


    rdfNodeDto.setName(name);

    return rdfNodeDto;
  }

  public static RdfNodeDto rdfNodeToRDFNodeDto(String name, RDFNode rdfNode, PrefixCcService prefixCcService) {
    if (rdfNode.isLiteral()) {
      return rdfLiteral(name, rdfNode, prefixCcService);
    }
    else if (rdfNode.isURIResource()) {
      return RdfStringNodeDto.create(rdfNode.asResource().getURI(), name, "uri");
    }
    else if (rdfNode.isResource()) {
      return RdfStringNodeDto.create(rdfNode.toString(), name, rdfNode.asNode().isBlank() ? "blank" : "resource");
    }
    else if (rdfNode.isAnon()) {
      return RdfStringNodeDto.create(rdfNode.toString(), name, "anon");
    }
    return RdfStringNodeDto.create(null, name, "unsupported");
  }
}
