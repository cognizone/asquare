package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.web.rest.controller.exceptions.BadInputException;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplicationViewToJson implements Supplier<ObjectNode> {

  private final PrefixCcService prefixCcService;
  private Supplier<ApplicationView> applicationViewSupplier;
  private ApplicationProfile.Type dataType;
  private String dataTypeId;
  private String singleTypedResourceUri;
  private List<String> typedResourceUris;
   private boolean isSingleResource;

  private TypedResource singleTypedResourceData;
  private Collection<? extends TypedResource> typedResourceData;
  private ApplicationView applicationView;

  public ApplicationViewToJson(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public ApplicationViewToJson withApplicationViewSupplier( Supplier<ApplicationView> applicationViewSupplier) {
    Preconditions.checkNotNull(applicationViewSupplier);
    this.applicationViewSupplier = applicationViewSupplier;
    return this;
  }

  public ApplicationViewToJson withResourceUri(String dataTypeId, String singleTypedResourceUri) {
    Preconditions.checkNotNull(dataTypeId, "dataTypeId not set");
    Preconditions.checkNotNull(singleTypedResourceUri, "singleTypedResourceUri not set");

    this.dataTypeId = dataTypeId;
    this.singleTypedResourceUri = singleTypedResourceUri;
    isSingleResource = true;
    return this;
  }

  public ApplicationViewToJson withDataTypeId(String dataTypeId, boolean isSingleResource) {
    this.dataTypeId = dataTypeId;
    this.isSingleResource = isSingleResource;
    return this;
  }

  public ApplicationViewToJson withResourceUris(String dataTypeId, List<String> typedResourceUris) {

    Preconditions.checkNotNull(dataTypeId, "dataTypeId not set");
    Preconditions.checkNotNull(typedResourceUris, "typedResourceUris not set");

    this.dataTypeId = dataTypeId;
    this.typedResourceUris = typedResourceUris;
    this.isSingleResource = false;
    return this;
  }

  @Override
  public ObjectNode get() {

    Preconditions.checkNotNull(applicationViewSupplier);
    applicationView = applicationViewSupplier.get();
    Preconditions.checkNotNull(applicationView);

    ensureBuilderSetup();

    TypedResourceToJson toJson = isSingleResource
        ? new TypedResourceToJson(prefixCcService).withTypedResource(singleTypedResourceData)
        : new TypedResourceToJson(prefixCcService).withTypedResources(typedResourceData);

    return toJson.get();
  }

  private void ensureBuilderSetup() {
    if (isSetupComplete()) return;

    initDataTypeFromDataTypeId();

    if (singleTypedResourceUri != null) setDataFromUri();
    else if (typedResourceUris != null) setDataFromUris();
    else if (dataType != null) setDataFromType();

    Preconditions.checkState(isSetupComplete(), "Single or array resource data must be filled.");
  }

  private boolean isSetupComplete() {
    return (singleTypedResourceData == null) != (typedResourceData == null);
  }

  private void initDataTypeFromDataTypeId() {
    if (dataTypeId != null) {
      ApplicationProfile.Type type = applicationView.getApplicationProfile().getType(dataTypeId);
      BadInputException.when(dataType != null && dataType != type);
      dataType = type;
    }
  }

  private void setDataFromUri() {
    singleTypedResourceData = applicationView
            .getRepository()
            .getTypedResource(dataType, ResourceFactory.createResource(singleTypedResourceUri));
  }

  private void setDataFromUris() {
    typedResourceData = typedResourceUris.stream()
        .map(uri -> applicationView.getRepository().getTypedResource(dataType, ResourceFactory.createResource(uri)))
        .collect(Collectors.toList());
  }

  private void setDataFromType() {
    List<? extends TypedResource> data = applicationView.getRepository().findAll(dataType);
    if (isSingleResource) {
      if (data.isEmpty()) throw new NotFoundException("Resource of type " + dataType.getDescription() + " not found");
      Preconditions.checkState(data.size() == 1,
                               "Asked for one, but found many resources of type " + dataType.getDescription());
      singleTypedResourceData = data.get(0);
    }
    else {
      typedResourceData = data;
    }
  }

}
