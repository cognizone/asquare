package zone.cogni.asquare.access.simplerdf.snippet;

import zone.cogni.asquare.applicationprofile.rules.Snippet;

import java.util.HashMap;
import java.util.Map;

public class SnippetRegistry {

  private final Map<String, Snippet> snippets = new HashMap<>();

  public SnippetRegistry() {
    init();
  }

  private void init() {
    snippets.put("person.findByName", new Snippet("?person rdfs:label $personName."));
    snippets.put("person.spouse", new Snippet("$person cz-onto:spouse ?spouse."));
    snippets.put("person.name", new Snippet("$person rdfs:label ?personName."));
    snippets.put("person.spouseName", new Snippet("?person rdfs:label $personName. optional { ?person cz-onto:spouse ?spouse. ?spouse rdfs:label ?spouseName }"));
    snippets.put("prefix-with-dash", new Snippet("$person cz-onto:spouse ?spouse"));
  }

  public Snippet getSnippet(String name) {
    return snippets.get(name);
  }

}
