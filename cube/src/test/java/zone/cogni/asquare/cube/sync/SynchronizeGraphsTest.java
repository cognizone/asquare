package zone.cogni.asquare.cube.sync;

import org.apache.jena.rdf.model.RDFNode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest(classes = SynchronizeGraphsTestConfig.class)
class SynchronizeGraphsTest {

  @Autowired
  private SynchronizeGraphsTestConfig config;

  @Test
  public void synchronize_one_into_empty_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore();
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    String graphUri = "http://demo.com/20080240";
    sync.synchronizeOne(graphUri);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(2);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(1);
    Assertions.assertThat(graphs).contains(graphUri);
  }

  @Test
  public void synchronize_one_new_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    String graphUri = "http://demo.com/20080240";
    sync.synchronizeOne(graphUri);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(4);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(2);
    Assertions.assertThat(graphs).contains(graphUri);
    Assertions.assertThat(graphs).contains("http://demo.com/19880130");
  }

  @Test
  public void synchronize_one_modified_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130-wrong-stamp");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    String graphUri = "http://demo.com/19880130";
    sync.synchronizeOne(graphUri);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(2);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(1);
    Assertions.assertThat(graphs).contains(graphUri);
    Assertions.assertThat(getSlice(rows, "o")).contains("1988-08-10T08:00:00Z");
  }

  @Test
  public void synchronize_one_without_stamp_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130-without-stamp", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    String graphUri = "http://demo.com/19880130";
    sync.synchronizeOne(graphUri);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(2);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(1);
    Assertions.assertThat(graphs).contains(graphUri);
    Assertions.assertThat(getSlice(rows, "o")).contains("2020-12-03T09:11:13Z");
  }

  @Test
  public void synchronize_one_deleted_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130", "20080240");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    String graphUri = "http://demo.com/19880130";
    sync.synchronizeOne(graphUri);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(getGraph(rows, graphUri)).isEmpty();

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs.size()).isEqualTo(1);
  }

  @Test
  public void synchronize_many_into_empty_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore();
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    List<String> graphUris = Arrays.asList("http://demo.com/19880130",
                                           "http://demo.com/19880140",
                                           "http://demo.com/20080240");
    sync.synchronizeMany(graphUris);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
    Assertions.assertThat(graphs).containsAll(graphUris);
  }

  @Test
  public void synchronize_many_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130-wrong-stamp", "19880140", "20080240");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    List<String> graphUris = Arrays.asList("http://demo.com/19880130",
                                           "http://demo.com/19880140",
                                           "http://demo.com/20080240");
    sync.synchronizeMany(graphUris);

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);

    List<Map<String, RDFNode>> wrongGraphRows = getGraph(rows, "http://demo.com/19880130");
    Assertions.assertThat(wrongGraphRows).hasSize(2);
    Assertions.assertThat(getSlice(wrongGraphRows, "o")).contains("1988-08-10T08:00:00Z");
  }

  @Test
  public void force_synchronize_into_empty_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore();
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.forceSynchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
  }

  @Test
  public void force_synchronize_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130-wrong-stamp", "19880140", "20080240");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    getTargetData(target);

    // when
    sync.forceSynchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
  }

  @Test
  public void force_synchronize_into_target_with_deletable_graph() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130", "19880140", "20080240", "20080999");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.forceSynchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
    Assertions.assertThat(graphs).doesNotContain("http://demo.com/20080999");
  }

  @Test
  public void normal_synchronize_into_empty_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore();
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.synchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
    Assertions.assertThat(graphs).containsAll(graphs);
  }

  @Test
  public void normal_synchronize_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130-wrong-stamp", "19880140", "20080240");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.synchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);

    List<Map<String, RDFNode>> wrongGraphRows = getGraph(rows, "http://demo.com/19880130");
    Assertions.assertThat(wrongGraphRows).hasSize(2);
    Assertions.assertThat(getSlice(wrongGraphRows, "o")).contains("1988-08-10T08:00:00Z");
  }

  @Test
  public void normal_synchronize_without_stamp_into_target() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130-without-stamp", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130", "19880140", "20080240");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.synchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
    Assertions.assertThat(graphs).containsAll(graphs);

    List<Map<String, RDFNode>> withoutStampRows = getGraph(rows, "http://demo.com/19880130");
    Assertions.assertThat(withoutStampRows).hasSize(2);
    Assertions.assertThat(getSlice(withoutStampRows, "o")).contains("2020-12-03T09:11:13Z");
  }

  @Test
  public void normal_synchronize_into_target_with_deletable_graph() {
    // given
    RdfStoreService source = config.sourceRdfStore("19880130", "19880140", "20080240");
    RdfStoreService target = config.targetRdfStore("19880130", "19880140", "20080240", "20080999");
    SynchronizeGraphs sync = config.synchronizedGraph(source, target);

    // when
    sync.synchronize();

    // then
    List<Map<String, RDFNode>> rows = getTargetData(target);
    Assertions.assertThat(rows).hasSize(6);

    Set<String> graphs = getSlice(rows, "g").collect(Collectors.toSet());
    Assertions.assertThat(graphs).hasSize(3);
    Assertions.assertThat(graphs).doesNotContain("http://demo.com/20080999");
  }

  private List<Map<String, RDFNode>> getTargetData(RdfStoreService target) {
    List<Map<String, RDFNode>> maps = target.executeSelectQuery("select * { graph ?g {?s ?p ?o} } order by ?g ?s ?p ?o",
                                                                JenaResultSetHandlers.listOfMapsResolver);
    maps.forEach(row -> System.out.println("" + row));
    return maps;
  }

  private List<Map<String, RDFNode>> getGraph(List<Map<String, RDFNode>> rows, String graphUri) {
    return rows.stream()
               .filter(row -> row.get("g").asResource().getURI().equals(graphUri))
               .collect(Collectors.toList());
  }


  private Stream<String> getSlice(List<Map<String, RDFNode>> data, String column) {
    return data.stream()
               .map(row -> row.get(column))
               .map(node -> node.isURIResource() ? node.asResource().getURI() : node.asLiteral().getLexicalForm());
  }

}