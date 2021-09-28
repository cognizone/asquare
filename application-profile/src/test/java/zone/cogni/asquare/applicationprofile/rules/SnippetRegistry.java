package zone.cogni.asquare.applicationprofile.rules;

import java.util.HashMap;
import java.util.Map;

public class SnippetRegistry {

  private Map<String, Snippet> snippets = new HashMap<>();

  public SnippetRegistry() {
    init();
  }

  private void init() {
    snippets.put("person.findByName", new Snippet("?person rdfs:label $personName."));
    snippets.put("person.spouse", new Snippet("$person czonto:spouse ?spouse."));
    snippets.put("person.name", new Snippet("$person rdfs:label ?personName."));
    snippets.put("person.spouseName", new Snippet("?person rdfs:label $personName. optional { ?person czonto:spouse ?spouse. ?spouse rdfs:label ?spouseName }"));
    snippets.put("prefix-with-dash", new Snippet("$person cz-onto:spouse ?spouse"));
  }

  public Snippet getSnippet(String name) {
    return snippets.get(name);
  }

}
