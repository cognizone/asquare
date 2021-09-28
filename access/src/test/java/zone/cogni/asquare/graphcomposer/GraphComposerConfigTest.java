package zone.cogni.asquare.graphcomposer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zone.cogni.asquare.graphcomposer.model.GraphComposerAttribute;
import zone.cogni.asquare.graphcomposer.model.GraphComposerModel;
import zone.cogni.asquare.graphcomposer.model.GraphComposerSubject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphComposerConfigTest {

  GraphComposerModel graphComposerModel = new GraphComposerModel();

  {
    List<GraphComposerSubject> subjects = new ArrayList<>();

    GraphComposerSubject graphComposerSubject1 = new GraphComposerSubject();
    graphComposerSubject1.setGraph("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}/graph");
    graphComposerSubject1.setUri("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}");
    graphComposerSubject1.setType("MemorialSerial");

    GraphComposerSubject graphComposerSubject2 = new GraphComposerSubject();
    graphComposerSubject2.setGraph("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/graph");
    graphComposerSubject2.setUri("https://fedlex.data.admin.ch/eli/collection/#{[collection]}");
    graphComposerSubject2.setType("MemorialSerial");

    GraphComposerSubject graphComposerSubject3 = new GraphComposerSubject();
    graphComposerSubject3.setGraph("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}/#{[editionNumber]}/graph");
    graphComposerSubject3.setUri("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}/#{[editionNumber]}");
    graphComposerSubject3.setType("MemorialSerial");

    GraphComposerSubject graphComposerSubject4 = new GraphComposerSubject();
    graphComposerSubject4.setGraph("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}/#{[editionNumber]}/graph");
    graphComposerSubject4.setUri("https://fedlex.data.admin.ch/eli/collection/#{[collection2]}/#{[year]}/#{[editionNumber]}");
    graphComposerSubject4.setType("Act");

    GraphComposerSubject graphComposerSubject5 = new GraphComposerSubject();
    graphComposerSubject5.setGraph("https://fedlex.data.admin.ch/eli/cc/#{[year]}/#{[editionNumber]}/graph");
    graphComposerSubject5.setUri("https://fedlex.data.admin.ch/eli/cc/#{[year]}/#{[editionNumber]}");

    GraphComposerAttribute attribute1 = new GraphComposerAttribute();
    attribute1.setPredicate("isMemberOf");
    attribute1.setObjectType("MemorialSerial");
    attribute1.setObject("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}");
    GraphComposerAttribute attribute2 = new GraphComposerAttribute();
    attribute2.setPredicate("year");
    attribute2.setObjectType("http://www.w3.org/2001/XMLSchema#int");
    attribute2.setObject("1999");

    List<GraphComposerAttribute> attributes = new ArrayList<>();
    attributes.add(attribute1);
    attributes.add(attribute2);
    graphComposerSubject4.setAttributes(attributes);

    subjects.add(graphComposerSubject1);
    subjects.add(graphComposerSubject2);
    subjects.add(graphComposerSubject3);
    subjects.add(graphComposerSubject4);
    subjects.add(graphComposerSubject5);
    graphComposerModel.setSubjects(subjects);
  }

  @Test
  public void testOrderGraph() {
    GraphComposerService graphComposerService = new GraphComposerService(null, null);

    Map<String, String> context = new HashMap<>();
    context.put("collection", "oc");
    context.put("collection2", "oc2");
    context.put("year", "1999");
    context.put("editionNumber", "500");

    Map<String, List<GraphComposerSubject>> graphs = graphComposerService.groupSubjectsByGraph(graphComposerModel, context);

    Assertions.assertEquals(4, graphs.size());
    Assertions.assertEquals(4, graphs.get("https://fedlex.data.admin.ch/eli/collection/oc/1999/500/graph")
                                     .size() + graphs.get("https://fedlex.data.admin.ch/eli/collection/oc/graph")
                                                     .size() + graphs.get("https://fedlex.data.admin.ch/eli/collection/oc/1999/graph")
                                                                     .size());

  }

  @Test
  public void testToString() {
    String str = graphComposerModel.toString();
    Assertions.assertNotNull(str);
    Assertions.assertTrue(str.contains("https://fedlex.data.admin.ch/eli/collection/#{[collection]}/#{[year]}/graph"));
  }

}
