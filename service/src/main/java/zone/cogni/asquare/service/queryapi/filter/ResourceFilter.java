package zone.cogni.asquare.service.queryapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.service.queryapi.filter.attribute.AttributeFilterPipe;
import zone.cogni.asquare.service.queryapi.filter.attribute.IncludeTypePipe;
import zone.cogni.asquare.service.queryapi.filter.value.AttributeMatcherPipe;
import zone.cogni.asquare.service.queryapi.filter.value.IncludeAttributePipe;
import zone.cogni.asquare.service.queryapi.filter.value.ValueFilterPipe;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceFilter {

  public static ResourceFilter fromJson(ObjectNode json) {
    if (json == null) return defaultFilter();

    ResourceFilter filter = new ResourceFilter();
    ObjectMapper mapper = new ObjectMapper();

    json.get("attribute").forEach(jsonFilter -> {
      filter.addAttributeFilter(jsonToFilterPipe((ObjectNode) jsonFilter, mapper));
    });

    json.get("value").forEach(jsonFilter -> {
      filter.addValueFilter(jsonToFilterPipe((ObjectNode) jsonFilter, mapper));
    });

    return filter;
  }

  private static <T> T jsonToFilterPipe(ObjectNode json, ObjectMapper mapper) {
    Class<?> filterClass = Try.of(() -> Class.forName(json.get("filterClass").asText())).get();
    return (T) Try.of(() -> mapper.treeToValue(json, filterClass)).get();
  }

  public static ResourceFilter defaultFilter() {
    ResourceFilter filter = new ResourceFilter();
    filter.addAttributeFilter(new IncludeTypePipe(1));
    return filter;
  }

  public static ResourceFilter fromQueryParams(MultiValueMap<String, String> queryParams) {
    ResourceFilter filter = new ResourceFilter();
    queryParams.forEach((k, v) -> {
      if (!k.startsWith("filter.")) return;
      v.forEach(value -> filter.addValueFilter(
          new AttributeMatcherPipe(StringUtils.substringAfter(k, "filter."), value)));
    });

//    Optional.ofNullable(queryParams.get("include.type"))
//        .ifPresent(list -> filter.addAttributeFilter(new IncludeTypePipe(1, new HashSet<>(list))));

    List<String> includedAttributes = queryParams.getOrDefault("include", Collections.emptyList());
    filter.addValueFilter(new IncludeAttributePipe(0, new HashSet<>(includedAttributes)));

    return filter;
  }


  private final List<AttributeFilterPipe> attributeFilters = new ArrayList<>();
  private final List<ValueFilterPipe> valueFilters = new ArrayList<>();


  public final boolean filterAttribute(int depth, Attribute attribute) {
    List<AttributeFilterPipe> active = attributeFilters
        .stream()
        .filter(pipe -> pipe.isActiveForDepth(depth))
        .collect(Collectors.toList());

    return active.isEmpty()
        || active.stream().allMatch(pipe -> pipe.getFilter().test(attribute));
  }

  public final <T extends RdfValue> Stream<T> filterValues(int dept, Attribute attribute, List<T> values) {
    Stream<T> stream = values.stream();

    for (ValueFilterPipe filter : getValueFilterPipes(dept).collect(Collectors.toList())) {
      stream = filter.filterStream(attribute, stream);
    }
    return stream;
  }

  private Stream<ValueFilterPipe> getValueFilterPipes (int depth) {
    return valueFilters
        .stream()
        .filter(pipe -> pipe.isActiveForDepth(depth));
  }

  public void addAttributeFilter(AttributeFilterPipe filter) {
    attributeFilters.add(filter);
  }

  public void addValueFilter(ValueFilterPipe filter) {
    valueFilters.add(filter);
  }

  public void addFilter(DepthFilterPipe pipe) {
    if (pipe instanceof AttributeFilterPipe) addAttributeFilter((AttributeFilterPipe) pipe);
    else if (pipe instanceof ValueFilterPipe) addValueFilter((ValueFilterPipe) pipe);
    else throw new IllegalStateException();
  }

  public List<AttributeFilterPipe> getAttributeFilters() {
    return attributeFilters;
  }

  public List<ValueFilterPipe> getValueFilters() {
    return valueFilters;
  }

  public ApplicationView.AttributeMatcher[] getAttributeMatchers() {
    return valueFilters.stream()
        .filter(pipe -> pipe.isActiveForDepth(0))
        .map(ValueFilterPipe::asAttributeMatcher)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toArray(ApplicationView.AttributeMatcher[]::new);
  }


  public ObjectNode toJson(ObjectMapper mapper) {

    ObjectNode rootNode = mapper.createObjectNode();
    rootNode.putPOJO("attribute", attributeFilters);
    rootNode.putPOJO("value", valueFilters);
    return rootNode;
  }

}
