package zone.cogni.asquare.cube.rules;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * <p>
 * Takes an <code>RdfStoreService</code> and an update query
 * and tries to run <code>"select * where {...}"</code> queries
 * each time adding one more line and logging its results.
 * </p>
 * <p>
 * Very useful for debugging delete/insert statements.
 * </p>
 */
class SparqlRulesDebugger implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(SparqlRulesDebugger.class);

  private final RdfStoreService rdfStore;
  private final String updateQuery;

  public SparqlRulesDebugger(RdfStoreService rdfStore, String updateQuery) {
    this.rdfStore = rdfStore;
    this.updateQuery = updateQuery;
  }

  @Override
  public void run() {
    // find header part
    String headerPart = getHeader(updateQuery);

    // strip header and find update part
    String queryWithoutHeader = StringUtils.substringAfter(updateQuery, headerPart);
    String updatePart = getUpdatePart(queryWithoutHeader);

    // strip update part and find footer
    String queryWithoutUpdate = StringUtils.substringAfter(queryWithoutHeader, updatePart);
    String footerPart = getFooterPart(queryWithoutUpdate);

    // strip footer and find where part (without where keyword)
    String queryWithoutFooter = StringUtils.substringBeforeLast(queryWithoutUpdate, footerPart);
    String wherePart = getWherePart(queryWithoutFooter);

    // get query candidates, run and log them
    List<String> selectQueries = getSelectQueries(headerPart, wherePart, footerPart);
    for (int i = 0; i < selectQueries.size(); i++) {
      String queryString = selectQueries.get(i);
      logQueryResult(i + 1, queryString);
    }
  }

  private void logQueryResult(int numberOfLines, String queryString) {
    List<Map<String, RDFNode>> rows = runQuery(queryString);
    logQueryResult(numberOfLines, queryString, rows);
  }

  private void logQueryResult(int numberOfLines, String queryString, List<Map<String, RDFNode>> rows) {
    if (rows == null) {
      log.trace("Q{}: did not run", numberOfLines);
    }
    else {
      Object resultCount = rows.isEmpty() ? "no" : rows.size();
      String plural = rows.size() == 1 ? "" : "s";
      queryString = queryString.replace("\n", "").replace("\r", "");
      int queryStringStart = queryString.length() - Math.min(40, queryString.length());
      String queryPart = StringUtils.repeat(' ', 15) + "... " + StringUtils.substring(queryString, queryStringStart);
      log.debug("Q{} has {} result{} {}",
                numberOfLines, resultCount, plural, queryPart);
      if (!rows.isEmpty()) {
        log.trace("    " + rows);
      }
    }
  }

  @Nullable
  private List<Map<String, RDFNode>> runQuery(String queryString) {
    Query query = getQuery(queryString);
    if (query == null) return null;

    return rdfStore.executeSelectQuery(query,
                                       new QuerySolutionMap(),
                                       JenaResultSetHandlers.listOfMapsResolver);
  }

  @Nullable
  private Query getQuery(String queryString) {
    try {
      return QueryFactory.create(queryString);
    }
    catch (RuntimeException ignore) {
    }
    return null;
  }

  @Nonnull
  private List<String> getSelectQueries(String headerPart, String wherePart, String footerPart) {
    List<String> result = new ArrayList<>();

    List<String> whereParts = getWhereParts(wherePart);
    for (String wherePartSubset : whereParts) {
      String query = headerPart + "\n" + "select * where { \n" + wherePartSubset + "\n" + "}\n" + footerPart;
      result.add(query);
    }

    return result;
  }

  @Nonnull
  private List<String> getWhereParts(String wherePart) {
    List<String> result = new ArrayList<>();
    List<String> whereLines = getLines(wherePart, false);
    for (int i = 1; i <= whereLines.size(); i++) {
      result.add(joinWhereLines(whereLines, i));
    }
    return result;
  }

  @Nonnull
  private String joinWhereLines(List<String> whereLines, int numberOfLines) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < numberOfLines; i++) {
      String line = whereLines.get(i);
      result.append(line).append("\n");
    }
    return result.toString();
  }

  @Nonnull
  private String getFooterPart(String queryWithoutUpdate) {
    List<String> resultBackwards = new ArrayList<>();

    List<String> lines = getLines(queryWithoutUpdate);
    for (int i = lines.size() - 1; i >= 0; i--) {
      String line = lines.get(i);
      int closeBracePosition = StringUtils.lastIndexOf(line, "}");
      if (closeBracePosition >= 0) {
        resultBackwards.add(StringUtils.substringAfterLast(line, "}"));
        break;
      }

      resultBackwards.add(line);
    }

    StringBuilder builder = new StringBuilder();
    for (int i = resultBackwards.size() - 1; i >= 0; i--) {
      builder.append(resultBackwards.get(i));
    }
    return builder.toString();
  }

  @Nonnull
  private String getWherePart(String queryWithoutFooter) {
    StringBuilder builder = new StringBuilder();

    boolean foundWhere = false;
    List<String> lines = getLines(queryWithoutFooter);
    for (String line : lines) {
      int wherePosition = StringUtils.indexOfIgnoreCase(line, "where");
      if (!foundWhere && wherePosition >= 0) {
        String linePart = StringUtils.substring(line, wherePosition + 5);
        builder.append(linePart);
        foundWhere = true;
      }
      else {
        builder.append(line);
      }
    }

    // strip { and }
    String result = builder.toString();
    result = StringUtils.substringAfter(result, "{");
    result = StringUtils.substringBeforeLast(result, "}");
    return result;
  }

  @Nonnull
  private String getUpdatePart(String queryWithoutHeader) {
    List<String> lines = getLines(queryWithoutHeader);

    StringBuilder result = new StringBuilder();
    for (String line : lines) {
      String lowercaseLine = line.toLowerCase();
      int wherePosition = lowercaseLine.indexOf("where");
      // TODO make it smarter for variables
      if (wherePosition >= 0) {
        result.append(line, 0, wherePosition);
        break;
      }

      result.append(line);
    }

    return result.toString();
  }

  @Nonnull
  private String getHeader(String updateQuery) {
    StringBuilder result = new StringBuilder();

    for (String line : getLines(updateQuery)) {
      int deletePosition = StringUtils.indexOfIgnoreCase(line, "delete");
      if (deletePosition >= 0) {
        result.append(line, 0, deletePosition);
        break;
      }

      int insertPosition = StringUtils.indexOfIgnoreCase(line, "insert");
      if (insertPosition >= 0) {
        result.append(line, 0, insertPosition);
        break;
      }

      result.append(line);
    }

    return result.toString();
  }

  @Nonnull
  private List<String> getLines(String queryPart) {
    return getLines(queryPart, true);
  }

  @Nonnull
  private List<String> getLines(String queryPart, boolean includeTokens) {
    List<String> result = new ArrayList<>();
    StringTokenizer stringTokenizer = new StringTokenizer(queryPart, "\n\r", includeTokens);
    while (stringTokenizer.hasMoreTokens()) {
      result.add(stringTokenizer.nextToken());
    }
    return result;
  }

}
