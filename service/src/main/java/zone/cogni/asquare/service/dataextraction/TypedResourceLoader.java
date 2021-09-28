package zone.cogni.asquare.service.dataextraction;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.vavr.control.Option;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.util.function.CachingSupplier;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TypedResourceLoader implements Supplier<Model> {

  public static TypedResourceLoader loader() {
    return new TypedResourceLoader();
  }

  public static TypedResourceContext typedResource(String type, String uri) {
    return new TypedResourceContext(type, uri);
  }

  private final List<TypedResourceContext> typedResourceContexts = new ArrayList<>();
  private Supplier<ApplicationView> applicationViewSupplier;

  private TypedResourceModelBuilder modelBuilder;

  public TypedResourceLoader withView(Supplier<ApplicationView> applicationViewSupplier) {
    this.applicationViewSupplier = CachingSupplier.memoize(applicationViewSupplier);
    return this;
  }

  public TypedResourceLoader loading(TypedResourceContext typedResourceContext) {
    if (applicationViewSupplier == null) throw new RuntimeException("Please call withView first.");

    // lookup
    Optional<TypedResourceContext> trOption = typedResourceContexts.stream()
            .filter(c -> Objects.equals(typedResourceContext.getType(), c.getType())
                         && Objects.equals(typedResourceContext.getUri(), c.getUri()))
            .findFirst();

    // if not present => add
    if (!trOption.isPresent()) {
      typedResourceContexts.add(typedResourceContext);
    }

    // if present => merge
    trOption.ifPresent(tr -> {
      tr.attributes(typedResourceContext.getAttributes());
      tr.paths(typedResourceContext.getPaths());
    });

    return this;
  }

  public Model get() {
    validate();

    if (modelBuilder == null) {
      modelBuilder = new TypedResourceModelBuilder();
      typedResourceContexts.forEach(this::addTypedResourceContext);
    }


//    modelBuilder.get().write(System.out, "turtle");
    return modelBuilder.get();
  }

  private void addTypedResourceContext(TypedResourceContext typedResourceContext) {
    ApplicationProfile.Type type = getType(typedResourceContext);
    Resource resource = ResourceFactory.createResource(typedResourceContext.getUri());
    TypedResource typedResource = getApplicationView().getRepository().getTypedResource(type, resource);

    addInstanceAttributes(typedResourceContext, type, typedResource);
    addInstancePaths(typedResourceContext);
  }

  private ApplicationView getApplicationView() {
    return applicationViewSupplier.get();
  }

  private void addInstancePaths(TypedResourceContext typedResourceContext) {
    ApplicationProfile.Type type = getType(typedResourceContext);
    Resource resource = ResourceFactory.createResource(typedResourceContext.getUri());
    TypedResource typedResource = getApplicationView().getRepository().getTypedResource(type, resource);

    typedResourceContext.getPaths().forEach(path -> addInstancePath(typedResource, path));
  }

  private void addInstanceAttributes(TypedResourceContext typedResourceContext, ApplicationProfile.Type type, TypedResource typedResource) {
    Collection<String> attributes = typedResourceContext.getAttributes().isEmpty() ? type.getAttributes().keySet()
                                                                                   : typedResourceContext.getAttributes();
    modelBuilder.addInstance(type, typedResource, attributes);
  }

  private void addInstancePath(TypedResource instance, String path) {
    addInstancePathElement(instance, Arrays.asList(path.split("\\.")));
  }

  private void addInstancePathElement(TypedResource instance, List<String> pathElements) {
    if (pathElements.isEmpty()) return;

    instance.getValues(pathElements.get(0)).forEach(value -> {
      TypedResource current = (TypedResource) value;
      modelBuilder.addInstance(current.getType(), current);

      addInstancePathElement(current, pathElements.subList(1, pathElements.size()));
    });

  }

  private void validate() {
    if (getApplicationView() == null) throw new RuntimeException("Application View is not configured.");

    List<String> errors = typedResourceContexts.stream()
            .map(this::validateTypedResourceContext)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    if (errors.isEmpty()) return;

    throw new RuntimeException("Incorrect loader \n\t" + Collectors.joining("\n\t"));
  }

  private List<String> validateTypedResourceContext(TypedResourceContext typedResourceContext) {
    ApplicationProfile applicationProfile = getApplicationView().getApplicationProfile();

    if (!applicationProfile.hasType(typedResourceContext.getType())) {
      return Arrays.asList("Unknown type '" + typedResourceContext.getType() + "'.");
    }

    return Lists.newArrayList(Iterables.concat(
            validateAttributes(typedResourceContext),
            validatePaths(typedResourceContext))
    );
  }

  @Nonnull
  private List<String> validateAttributes(TypedResourceContext typedResourceContext) {
    ApplicationProfile.Type type = getType(typedResourceContext);

    List<String> problemAttributes = typedResourceContext.getAttributes().stream()
            .filter(attribute -> !type.hasAttribute(attribute))
            .collect(Collectors.toList());

    if (problemAttributes.isEmpty()) return Collections.emptyList();

    return Arrays.asList(
            "Unknown attributes on type '" + type.getDescription() + "' : " +
            problemAttributes.stream().collect(Collectors.joining(",")));
  }

  @Nonnull
  private ApplicationProfile.Type getType(TypedResourceContext typedResourceContext) {
    ApplicationProfile applicationProfile = getApplicationView().getApplicationProfile();
    return applicationProfile.getType(typedResourceContext.getType());
  }

  @Nonnull
  private List<String> validatePaths(TypedResourceContext typedResourceContext) {
    return typedResourceContexts.stream()
            .map(this::processPaths)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
  }

  private List<String> processPaths(TypedResourceContext resourceContext) {
    return resourceContext.getPaths()
            .stream()
            .map(path -> processPath(resourceContext, path))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
  }

  private List<String> processPath(TypedResourceContext resourceContext, String path) {
    ApplicationProfile.Type currentType = getType(resourceContext);
    for (String pathElement : Arrays.asList(path.split("\\."))) {
      if (!currentType.hasAttribute(pathElement)) {
        return Arrays.asList("Cannot find attribute '" + pathElement + "' on type '" + currentType.getDescription() + "'" +
                             " when applying path '" + path + "' on type '" + resourceContext.getType() + "'.");
      }

      ApplicationProfile.Attribute attribute = currentType.getAttribute(pathElement);
      Option<Range> rangeOption = attribute.getRule(Range.class);

      if (rangeOption.isEmpty()) {
        return Arrays.asList("Cannot find range on attribute '" + pathElement + "' on type '" + String.join(",", currentType.getClassIds()) + "'" +
                             " when applying path '" + path + "' on type '" + resourceContext.getType() + "'.");
      }

      if (!(rangeOption.get().getValue() instanceof ClassId)) {
        // TODO support more
        return Arrays.asList("Expected to find range of type ClassId on attribute '" + pathElement + "' on type '" + currentType.getDescription() + "'" +
                             " when applying path '" + path + "' on type '" + resourceContext.getType() + "'.");
      }

      ClassId classId = (ClassId) rangeOption.get().getValue();
      ApplicationProfile applicationProfile = getApplicationView().getApplicationProfile();
      if (!applicationProfile.hasType(classId.getValue())) {
        return Arrays.asList("Unknown type '" + classId.getValue() + "' on attribute '" + pathElement + "' on type '" + currentType.getDescription() + "'" +
                             " when applying path '" + path + "' on type '" + resourceContext.getType() + "'.");
      }

      currentType = applicationProfile.getType(classId.getValue());
    }

    return Collections.emptyList();
  }

  public static class TypedResourceContext {

    private final String type;
    private final String uri;
    private final Collection<String> attributes = new HashSet<>();
    private final Collection<String> paths = new HashSet<>();

    public TypedResourceContext(@Nonnull String type, @Nonnull String uri) {
      this.type = type;
      this.uri = uri;
    }

    public TypedResourceContext attributes(String... attributes) {
      return attributes(Arrays.asList(attributes));
    }

    public TypedResourceContext attributes(@Nonnull Collection<String> attributes) {
      if (attributes.isEmpty()) return this;

      this.attributes.addAll(attributes);
      return this;
    }

    public TypedResourceContext paths(String... paths) {
      return paths(Arrays.asList(paths));
    }

    public TypedResourceContext paths(@Nonnull Collection<String> paths) {
      if (paths.isEmpty()) return this;

      this.paths.addAll(paths);
      return this;
    }

    public String getType() {
      return type;
    }

    public String getUri() {
      return uri;
    }

    public Collection<String> getAttributes() {
      return attributes;
    }

    public Collection<String> getPaths() {
      return paths;
    }


  }

}
