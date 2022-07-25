package zone.cogni.asquare.cube.urigenerator.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.json5.Json5Light;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UriGeneratorRoot {

  public static UriGeneratorRoot load(InputStreamSource resource) {
    try {
      ObjectMapper mapper = Json5Light.getJson5Mapper();
      UriGeneratorRoot result = mapper.readValue(resource.getInputStream(), UriGeneratorRoot.class);

//      result.validate();
      return result;
    }
    catch (IOException e) {
      String extra = getPath(resource) != null ? " Path " + getPath(resource) : "";
      throw new RuntimeException("Unable to load uri generator configuration." + extra, e);
    }
  }

  private static String getPath(InputStreamSource resource) {
    try {
      if (resource instanceof Resource) {
        Resource r = (Resource) resource;
        return r.getFile().getPath();
      }
    }
    catch (IOException e) {
      return null;
    }
    return null;
  }

  private Map<String, String> prefixes;
  private List<UriGenerator> generators;

  public UriGeneratorRoot() {
  }

  public Map<String, String> getPrefixes() {
    return prefixes;
  }

  public void setPrefixes(Map<String, String> prefixes) {
    this.prefixes = prefixes;
  }

  public List<UriGenerator> getGenerators() {
    return generators;
  }

  public void setGenerators(List<UriGenerator> generators) {
    this.generators = generators;
  }

  public String getPrefixQuery() {
    return prefixes.entrySet()
                   .stream()
                   .map(e -> "PREFIX "
                             + StringUtils.rightPad(e.getKey() + ":", 8) + " <" + e.getValue() + ">\n")
                   .collect(Collectors.joining())
           + "\n";
  }

}
