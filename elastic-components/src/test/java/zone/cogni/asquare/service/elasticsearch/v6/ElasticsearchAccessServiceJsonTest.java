package zone.cogni.asquare.service.elasticsearch.v6;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zone.cogni.asquare.service.elasticsearch.ElasticHelper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to examine the exact JSON output from the current Elasticsearch QueryBuilders implementation.
 * This tests the ACTUAL production code from ElasticHelper.buildFindAllQuery().
 */
public class ElasticsearchAccessServiceJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testCurrentQueryBuilderJsonOutput() throws IOException {
        // Test the actual production method
        String typeClassId = "http://example.org/Person";
        ObjectNode searchRequestBody = ElasticHelper.buildFindAllQuery(typeClassId);

        // Print the results for visual inspection
        System.out.println("=== CURRENT ELASTICSEARCH V6 QUERY ===");
        System.out.println("Method: ElasticHelper.buildFindAllQuery()");
        System.out.println();
        System.out.println("=== Pretty-Printed JSON ===");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(searchRequestBody));
        System.out.println();

        // ASSERTIONS - This documents the expected JSON structure
        assertNotNull(searchRequestBody, "Search request should not be null");

        // Root level: must have "query" and "_source"
        assertTrue(searchRequestBody.has("query"), "Root should have 'query' field");
        assertTrue(searchRequestBody.has("_source"), "Root should have '_source' field");

        // Query structure: query.term["data.type.keyword"]
        ObjectNode query = (ObjectNode) searchRequestBody.get("query");
        assertNotNull(query, "Query should not be null");
        assertTrue(query.has("term"), "Query should have 'term' field");

        ObjectNode term = (ObjectNode) query.get("term");
        assertNotNull(term, "Term should not be null");
        assertTrue(term.has("data.type.keyword"), "Term should have 'data.type.keyword' field");

        // The term query value structure
        ObjectNode termValue = (ObjectNode) term.get("data.type.keyword");
        assertNotNull(termValue, "Term value should not be null");
        assertTrue(termValue.has("value"), "Term value should have 'value' field");
        assertTrue(termValue.has("boost"), "Term value should have 'boost' field");
        assertEquals(typeClassId, termValue.get("value").asText(), "Term value should match input typeClassId");
        assertEquals(1.0, termValue.get("boost").asDouble(), 0.001, "Default boost should be 1.0");

        // _source structure: should have includes and excludes arrays
        ObjectNode source = (ObjectNode) searchRequestBody.get("_source");
        assertNotNull(source, "_source should not be null");
        assertTrue(source.has("includes"), "_source should have 'includes' field");
        assertTrue(source.has("excludes"), "_source should have 'excludes' field");
        assertInstanceOf(ArrayNode.class, source.get("includes"), "includes should be an array");
        assertInstanceOf(ArrayNode.class, source.get("excludes"), "excludes should be an array");
        assertEquals(0, ((ArrayNode) source.get("includes")).size(), "includes array should be empty");
        assertEquals(0, ((ArrayNode) source.get("excludes")).size(), "excludes array should be empty");
    }
}
