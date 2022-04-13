package zone.cogni.asquare.cube.convertor.json;

import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class CollapsedImportsCompactConversionProfile implements Function<CompactConversionProfile, CompactConversionProfile> {

  private static class Builder implements Supplier<CompactConversionProfile> {

    private final CompactConversionProfile profileWithoutImports = new CompactConversionProfile();

    public void addPrefix(String prefix, String uri) {
      profileWithoutImports.addPrefix(prefix, uri);
    }

    public void addType(CompactConversionProfile.Type type) {
      profileWithoutImports.addType(type);
    }

    public CompactConversionProfile get() {
      return profileWithoutImports;
    }
  }

  @Override
  public CompactConversionProfile apply(CompactConversionProfile compactConversionProfile) {
    Builder builder = new Builder();
    List<CompactConversionProfile> conversionProfiles = getDepthFirstSortedConversionProfiles(compactConversionProfile);
    processConversionProfiles(builder, conversionProfiles);

    return builder.get();
  }

  private List<CompactConversionProfile> getDepthFirstSortedConversionProfiles(CompactConversionProfile root) {
    List<CompactConversionProfile> sorted = new ArrayList<>();
    return calculateDepthFirstSortedConversionProfiles(root, sorted);
  }

  private List<CompactConversionProfile> calculateDepthFirstSortedConversionProfiles(CompactConversionProfile root,
                                                                                     List<CompactConversionProfile> sorted) {

    List<String> imports = root.getImports();
    if (imports != null) {
      imports.forEach(current -> {
        ClassPathResource resource = new ClassPathResource(current);
        CompactConversionProfile newRoot = CompactConversionProfile.read(resource);
        calculateDepthFirstSortedConversionProfiles(newRoot, sorted);
      });
    }

    sorted.add(root);
    return sorted;
  }

  private void processConversionProfiles(Builder builder, List<CompactConversionProfile> conversionProfiles) {
    processPrefixes(builder, conversionProfiles);
    processTypes(builder, conversionProfiles);
  }

  public void processPrefixes(Builder builder, List<CompactConversionProfile> conversionProfiles) {
    conversionProfiles.stream()
                      .filter(conversionProfile -> conversionProfile.getPrefixes() != null)
                      .flatMap(conversionProfile -> conversionProfile.getPrefixes().entrySet().stream())
                      .forEach(entry -> builder.addPrefix(entry.getKey(), entry.getValue()));
  }

  private void processTypes(Builder builder, List<CompactConversionProfile> conversionProfiles) {
    conversionProfiles.stream()
                      .flatMap(conversionProfile -> conversionProfile.getTypes().stream())
                      .forEach(builder::addType);

  }
}
