package zone.cogni.asquare.edit;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.FeatureFlag;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.cachedDelta.CachedDeltaResourceAspect;
import zone.cogni.asquare.edit.delta.SparqlVisitor;
import zone.cogni.asquare.edit.delta.TypedResourceDelta;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.web.rest.controller.exceptions.BadInputException;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DeltaResource implements MutableResource {

  private static final Logger log = LoggerFactory.getLogger(DeltaResource.class);

  public static BiFunction<ApplicationProfile.Type, String, DeltaResource> fromDatabase(ApplicationView applicationView,
                                                                                        BiFunction<ApplicationProfile.Type, String, String> uriStrategy) {
    return (resourceType, resourceUri) -> {
      // load existing resource
      if (FeatureFlag.cachedDeltaResource && CachedDeltaResourceAspect.isResourceCached(applicationView, resourceUri, resourceType)) {
        return CachedDeltaResourceAspect.find(applicationView, resourceUri, resourceType);
      }
      TypedResource previous = null;
      try {
        previous = applicationView.find(() -> resourceType, resourceUri);
      }
      catch (NotFoundException e) {
      }
      if (FeatureFlag.cachedDeltaResource) {
        return DeltaResource.fromDatabase(applicationView, previous).getOrElse(() -> {
          ConstructedResource newResource = ConstructedResource
                  .create(() -> resourceType, () -> uriStrategy.apply(resourceType, resourceUri))
                  .get();
          return constructAndCache(applicationView, null, newResource);
        });
      }
      return DeltaResource.fromDatabase(previous).getOrElse(() -> {
        ConstructedResource newResource = ConstructedResource
                .create(() -> resourceType, () -> uriStrategy.apply(resourceType, resourceUri))
                .get();
        return new DeltaResource(null, newResource);
      });
    };
  }

  // without cache feature
  public static Option<DeltaResource> fromDatabase(TypedResource databaseTypedResource) {
    FeatureFlag.methodIncompatibleWithCachedDeltaResourceFeature();

    if (databaseTypedResource == null) return Option.none();

    if (databaseTypedResource instanceof DeltaResource) return Option.of((DeltaResource) databaseTypedResource);

    return Option.of(new DeltaResource(databaseTypedResource, null));
  }

  public static DeltaResource createNew(Supplier<ApplicationProfile.Type> typeSupplier,
                                        Supplier<String> uriSupplier) {
    FeatureFlag.methodIncompatibleWithCachedDeltaResourceFeature();

    Supplier<ConstructedResource> constructedResourceSupplier = ConstructedResource.create(typeSupplier, uriSupplier);
    return new DeltaResource(null, constructedResourceSupplier.get());
  }

  // with cache feature
  public static DeltaResource findInDatabase(ApplicationView applicationView,
                                             ApplicationProfile.Type resourceType,
                                             String resourceUri) {
    if (CachedDeltaResourceAspect.isResourceCached(applicationView, resourceUri, resourceType)) {
      DeltaResource deltaResource = CachedDeltaResourceAspect.find(applicationView, resourceUri, resourceType);
      if (deltaResource.isNew()) throw new BadInputException(resourceType.getClassId() + " '" +
                                                             resourceUri + "' can't be found in the database, you need to save it first");
      return deltaResource;
    }
    TypedResource previous = applicationView.find(() -> resourceType, resourceUri);
    return constructAndCache(applicationView, previous, null);
  }

  private static Option<DeltaResource> fromDatabase(ApplicationView view, TypedResource databaseTypedResource) {
    if (databaseTypedResource == null) return Option.none();
    return Option.of(constructAndCache(view, databaseTypedResource, null));
  }

  public static DeltaResource editDatabaseResource(ApplicationView view, MutableResource newResource) {
    if (newResource instanceof DeltaResource) {
      CachedDeltaResourceAspect.cacheResource(view, (DeltaResource) newResource);
      return (DeltaResource) newResource;
    }
    TypedResource oldResource = null;
    try {
      oldResource = view.find(newResource::getType, newResource.getResource().getURI());
    }
    catch (NotFoundException ex) {
    }
    return constructAndCache(view, oldResource, newResource);
  }

  public static DeltaResource createNew(ApplicationView view,
                                        Supplier<ApplicationProfile.Type> typeSupplier,
                                        Supplier<String> uriSupplier) {

    Supplier<ConstructedResource> constructedResourceSupplier = ConstructedResource.create(typeSupplier, uriSupplier);
    return constructAndCache(view, null, constructedResourceSupplier.get());
  }

  public static DeltaResource createNew(ApplicationView view,
                                        MutableResource newResource) {
    MutableResource mutableResource = newResource instanceof DeltaResource ? ((DeltaResource) newResource).newResource
                                                                           : newResource;
    return constructAndCache(view, null, mutableResource);
  }

  private static DeltaResource constructAndCache(ApplicationView view, TypedResource oldResource, MutableResource newResource) {
    DeltaResource deltaResource = new DeltaResource(view, oldResource, newResource);
    CachedDeltaResourceAspect.cacheResource(view, deltaResource);
    return deltaResource;
  }

  @Nullable
  protected final MutableResource oldResource;

  @Nonnull
  protected final MutableResource newResource;

  private ApplicationView view;
  private Map<ApplicationProfile.Attribute, List<RdfValue>> addedValues;
  private Map<ApplicationProfile.Attribute, List<RdfValue>> removedValues;

  private boolean deleted = false;

  protected DeltaResource() {
    FeatureFlag.methodIncompatibleWithCachedDeltaResourceFeature();
    oldResource = null;
    newResource = null;
    view = null;
  }

  public DeltaResource(TypedResource oldResource, MutableResource newResource) {
    FeatureFlag.methodIncompatibleWithCachedDeltaResourceFeature();
    Preconditions.checkState(oldResource != null || newResource != null);

    this.oldResource = oldResource == null
                       ? null
                       : ConstructedResource.asConstructedResource().apply(oldResource);
    this.newResource = newResource == null
                       ? new ConstructedResource(oldResource.getType(), oldResource.getResource().getURI())
                       : newResource;

    optimizeNewResource();
    view = null;
    addedValues = null;
    removedValues = null;
  }

  private DeltaResource(ApplicationView view, TypedResource oldResource, MutableResource newResource) {
    Preconditions.checkState(view != null && (oldResource != null || newResource != null));
    Preconditions.checkState(!(oldResource instanceof DeltaResource) && !(newResource instanceof DeltaResource));
    this.view = view;
    this.oldResource = oldResource == null
                       ? null
                       : ConstructedResource.asConstructedResource().apply(oldResource);
    this.newResource = newResource == null
                       ? new ConstructedResource(oldResource.getType(), oldResource.getResource().getURI())
                       : newResource;

    optimizeNewResource();
    initializeMaps();
  }

  private void optimizeNewResource() {
    if (oldResource == null) return;

    Preconditions.checkState(oldResource.isSameAs(newResource));

    newResource.getType().getAttributes().values().forEach(attribute -> {
      if (!newResource.hasAttribute(attribute)) return;

      boolean noChange = hasSameValues(oldResource, newResource, attribute);
      if (noChange) newResource.clearValues(attribute);

    });
  }

  private void initializeMaps() {
    addedValues = new HashMap<>();
    removedValues = new HashMap<>();
    getType().getAttributes().values().forEach(attribute -> {
      if (!newResource.hasAttribute(attribute)) return;
      if (oldResource == null || !oldResource.hasAttribute(attribute)) {
        addedValues.put(attribute, newResource.getValues(attribute).stream()
                .map(value -> CachedDeltaResourceAspect.get(view, value))
                .collect(Collectors.toList()));
        return;
      }
      if (isDeleted()) {
        removedValues.put(attribute, oldResource.getValues(attribute));
        return;
      }
      List<RdfValue> newValues = newResource.getValues(attribute);
      List<RdfValue> oldValues = oldResource.getValues(attribute);
      List<RdfValue> addedValues = getUniqueValuesOfFirstList(newValues, oldValues);
      if (!addedValues.isEmpty()) {
        this.addedValues.put(attribute, addedValues.stream()
                .map(value -> CachedDeltaResourceAspect.get(view, value))
                .collect(Collectors.toList()));
      }
      List<RdfValue> removedValues = getUniqueValuesOfFirstList(oldValues, newValues);
      if (!removedValues.isEmpty()) {
        this.removedValues.put(attribute, removedValues);
      }
    });
  }

  private List<RdfValue> getUniqueValuesOfFirstList(List<RdfValue> oneList, List<RdfValue> otherList) {
    return oneList.stream().filter(value -> !hasValue(otherList, value)).collect(Collectors.toList());
  }

  private boolean hasSameValues(TypedResource oldResource,
                                MutableResource newResource,
                                ApplicationProfile.Attribute attribute) {
    List<RdfValue> newValues = newResource.getValues(attribute);
    List<RdfValue> oldValues = oldResource.getValues(attribute);

    return hasSameValues(newValues, oldValues);
  }

  private boolean hasSameValues(Collection<RdfValue> oneList, Collection<RdfValue> otherList) {
    if (otherList.size() != oneList.size()) return false;

    return otherList.stream().allMatch(oldValue -> hasValue(oneList, oldValue));
  }

  private boolean hasValue(Collection<RdfValue> list, RdfValue value) {
    return list.stream().anyMatch(current -> current.isSameAs(value));
  }

  private boolean valueHasBeenRemoved(ApplicationProfile.Attribute attribute, RdfValue value) {
    return removedValues.containsKey(attribute) && hasValue(removedValues.get(attribute), value);
  }

  @Nonnull
  @Override
  public Resource getResource() {
    return newResource.getResource();
  }

  @Nonnull
  @Override
  public ApplicationProfile.Type getType() {
    return newResource.getType();
  }

  @Nonnull
  @Override
  public <T extends RdfValue> List<T> getValues(@Nonnull ApplicationProfile.Attribute attribute) {
    if (FeatureFlag.cachedDeltaResource) {
      /*if (newResource.hasAttribute(attribute)) {
        return newResource.getValues(attribute).stream()
                .map(value -> CachedDeltaResourceAspect.get(view, (T) value))
                .collect(Collectors.toList());
      }*/
      List<RdfValue> values = Optional.ofNullable(addedValues.get(attribute))
          .map(ArrayList::new).orElseGet(ArrayList::new);

      if (oldResource != null && oldResource.hasAttribute(attribute)) {
        values.addAll(oldResource.getValues(attribute).stream()
                .filter(value -> !valueHasBeenRemoved(attribute, value))
                .map(value -> CachedDeltaResourceAspect.get(view, (T) value)).collect(Collectors.toList()));
      }
      return (List<T>) values;
    }

    if (newResource.hasAttribute(attribute)) return newResource.getValues(attribute);

    if (oldResource != null && oldResource.hasAttribute(attribute)) return oldResource.getValues(attribute);

    return Collections.emptyList();
  }

  public void delete() {
    Preconditions.checkState(oldResource != null);

    deleted = true;

    oldResource.getType().getAttributes().values().forEach(attribute -> {
      List<RdfValue> oldValues = oldResource.getValues(attribute);
      if (oldValues.isEmpty()) {
        newResource.clearValues(attribute);
        return;
      }

      newResource.setValues(attribute, Collections.emptyList());
    });
  }

  public boolean isNew() {
    return oldResource == null;
  }

  public boolean isDeleted() {
    if (oldResource == null) return false;

    return deleted;

//    return oldResource.getType().getAttributes().values().stream()
//            .allMatch(attribute -> {
//                        boolean oldEmpty = oldResource.getValues(attribute).isEmpty();
//                        boolean newEmpty = newResource.hasAttribute(attribute)
//                                           && newResource.getValues(attribute).isEmpty();
//                        return oldEmpty || newEmpty;
//                      }
//            );
  }

  @Override
  public void setValues(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull List<?> values) {
    if (FeatureFlag.cachedDeltaResource) {
      if (oldResource != null && oldResource.hasAttribute(attribute)) {
        List<RdfValue> oldValues = oldResource.getValues(attribute);
        List<RdfValue> addedValues = getUniqueValuesOfFirstList(values.stream()
                                                                         .map(value -> new AttributeConversion(attribute, value).get())
                                                                         .collect(Collectors.toList()),
                                                                 oldValues);
        if (!addedValues.isEmpty()) {
          addedValues.forEach(value -> addValue(attribute, value));
        }
        List<RdfValue> removedValues = getUniqueValuesOfFirstList(oldValues,
                                                  values.stream()
                                                          .map(value -> new AttributeConversion(attribute, value).get())
                                                          .collect(Collectors.toList()));
        if (!removedValues.isEmpty()) {
          removedValues.forEach(value -> removeValue(attribute, value));
        }
      } else {
        values.forEach(value -> addValue(attribute, value));
      }
    }
    newResource.setValues(attribute, values);

    if (oldResource == null) return;

    if (hasSameValues(newResource.getValues(attribute), oldResource.getValues(attribute))) {
      newResource.clearValues(attribute);
    }
  }

  @Override
  public void addValue(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull Object value) {
    RdfValue toAdd = new AttributeConversion(attribute, value).get();
    if (FeatureFlag.cachedDeltaResource) {
      SparqlVisitor sparqlVisitor = SparqlVisitor.instance();
      if (view.getRdfStoreService().executeAskQuery("ASK {<" + getResource().getURI() +
                                                "> <" + attribute.getUri() +
                                                "> " + toAdd.getNode().visitWith(sparqlVisitor) + "}")) {
        log.info("Value {} already present when adding value to attribute '{}'.", toAdd, attribute.getAttributeId());
        return;
      }
      newResource.addValue(attribute, toAdd);
      toAdd = CachedDeltaResourceAspect.get(view, toAdd);
      addedValues.computeIfAbsent(attribute, newList -> new ArrayList<>()).add(toAdd);
    } else {
      List<RdfValue> values = new ArrayList<>(getValues(attribute));
      if (hasValue(values, toAdd)) {
        log.info("Value {} already present when adding value to attribute '{}'.", toAdd, attribute.getAttributeId());
        return;
      }

      List<RdfValue> newValues = new ArrayList<>(values);
      newValues.add(toAdd);
      setValues(attribute, newValues);
    }
  }

  @Override
  public void removeValue(@Nonnull ApplicationProfile.Attribute attribute,
                          @Nonnull Object value) {
    RdfValue toRemove = new AttributeConversion(attribute, value).get();
    if (FeatureFlag.cachedDeltaResource) {
      SparqlVisitor sparqlVisitor = SparqlVisitor.instance();
      if (!view.getRdfStoreService().executeAskQuery("ASK {<" + getResource().getURI() +
                                                    "> <" + attribute.getUri() +
                                                    "> " + toRemove.getNode().visitWith(sparqlVisitor) + "}")) {
        log.info("Cannot find {} when removing value from attribute '{}'.", toRemove, attribute.getAttributeId());
        return;
      }
      newResource.removeValue(attribute, toRemove);
      removedValues.computeIfAbsent(attribute, newList -> new ArrayList<>()).add(toRemove);
    } else {
      List<RdfValue> values = getValues(attribute);
      List<RdfValue> result = values.stream()
              .filter(current -> !Objects.equals(current.getNode(), toRemove.getNode()))
              .collect(Collectors.toList());

      boolean removed = result.size() == values.size() - 1;
      if (!removed) log.info("Cannot find {} when removing value from attribute '{}'.", toRemove, attribute.getAttributeId());

      setValues(attribute, result);
    }
  }

  @Override
  public boolean hasAttribute(ApplicationProfile.Attribute attribute) {
    if (FeatureFlag.cachedDeltaResource) {
      return addedValues.containsKey(attribute) || oldResource != null && oldResource.hasAttribute(attribute);
    }
    return newResource.hasAttribute(attribute) || oldResource != null && oldResource.hasAttribute(attribute);
  }

  @Override
  public void clearValues(ApplicationProfile.Attribute attribute) {
    if (FeatureFlag.cachedDeltaResource) {
      addedValues.remove(attribute);
    } else {
      newResource.clearValues(attribute);
    }
  }

  public TypedResourceDelta getDelta() {
    TypedResourceDelta delta = new TypedResourceDelta(this);

    getType().getAttributes().values()
            .forEach(attribute -> {
              delta.appendToAdd(attribute, getAddValues(attribute));
              delta.appendToRemove(attribute, getRemoveValues(attribute));
            });

    return delta;
  }

  @Nullable
  public MutableResource getOldResource() {
    return oldResource;
  }

  private List<RdfValue> getAddValues(ApplicationProfile.Attribute attribute) {
    if (FeatureFlag.cachedDeltaResource) {
      return addedValues.getOrDefault(attribute, Collections.emptyList());
    }
    boolean nothingOld = oldResource == null || !oldResource.hasAttribute(attribute);
    boolean nothingNew = !newResource.hasAttribute(attribute);

    if (deleted) return Collections.emptyList();

    if (nothingNew) return Collections.emptyList();

    if (nothingOld) return newResource.getValues(attribute);

    List<RdfValue> oldValues = oldResource.getValues(attribute);
    List<RdfValue> newValues = newResource.getValues(attribute);
    return newValues.stream()
            .filter(newValue -> !hasValue(oldValues, newValue))
            .collect(Collectors.toList());
  }

  private List<RdfValue> getRemoveValues(ApplicationProfile.Attribute attribute) {
    if (FeatureFlag.cachedDeltaResource) {
      return removedValues.getOrDefault(attribute, Collections.emptyList());
    }
    boolean nothingOld = oldResource == null || !oldResource.hasAttribute(attribute);
    boolean nothingNew = !deleted && !newResource.hasAttribute(attribute);

    if (nothingOld || nothingNew) return Collections.emptyList();

    List<RdfValue> oldValues = oldResource.getValues(attribute);
    List<RdfValue> newValues = deleted ? Collections.emptyList() : newResource.getValues(attribute);

    return oldValues.stream()
            .filter(oldValue -> !hasValue(newValues, oldValue))
            .collect(Collectors.toList());
  }

}
