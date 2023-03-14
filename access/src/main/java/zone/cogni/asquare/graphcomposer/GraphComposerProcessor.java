package zone.cogni.asquare.graphcomposer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.edit.cachedDelta.CachedDeltaResource;
import zone.cogni.asquare.edit.cachedDelta.CachedDeltaResourceAspect;
import zone.cogni.asquare.graphcomposer.model.GraphComposerAttribute;
import zone.cogni.asquare.graphcomposer.model.GraphComposerSubject;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphComposerProcessor {

  private static final String A2_PREFIX = "a2:";
  private static final String A2_CREATE = "a2:create:";

  private static final Logger log = LoggerFactory.getLogger(GraphComposerProcessor.class);

  private final TypeMapper xsdTypeMapper = new TypeMapper();

  {
    XSDDatatype.loadXSDSimpleTypes(xsdTypeMapper);
  }

  @CachedDeltaResource
  public List<String> createSubjectsInGraph(ApplicationView view,
                                            List<GraphComposerSubject> subjects,
                                            Map<String, String> context) {
    List<String> entitiesList = new ArrayList<>();
    for (GraphComposerSubject subject : subjects) {
      String uri = subject.getUri(context);

      if (!StringUtils.equals("true", subject.getExcludeFromMapping())) {
        entitiesList.add(uri);
      }

      if (StringUtils.isNotBlank(subject.getType(context))) {
        log.info("GraphComposer has started creating subject {}", uri);

        DeltaResource resource = view.getDeltaResource(() -> view.getApplicationProfile()
                                                                 .getType(subject.getType(context)), uri);

        String message = resource.isNew() ? "GraphComposer is creating new resource {}" : "GraphComposer has found resource {}";
        log.info(message, uri);

        log.info("GraphComposer has finished creating subject {}", uri);
      }
    }

    CachedDeltaResourceAspect.saveCache(view);

    return entitiesList;
  }

  private void removeExistingRelations(DeltaResource resource, String predicate, String subjectUri, String object) {
    log.info("GraphComposer is removing existing relations for unique attribute {} -> {}", subjectUri, predicate);
    for (RdfValue value : resource.getValues(predicate)) {

      if (GraphComposerUtils.equalsValue(value, object)) {
        log.info("GraphComposer is ignoring existing relation because its new value is the same {} -> {} -> {}", subjectUri, predicate, value);
      }
      else {
        resource.removeValue(predicate, value);
        log.info("GraphComposer has removed existing relation {} -> {} -> {}", subjectUri, predicate, value);
      }
    }
  }

  private void removeAllExisting(DeltaResource resource, String predicate, String subjectUri) {
    log.info("GraphComposer is removing all existing relations for attribute {} -> {}", subjectUri, predicate);
    for (RdfValue value : resource.getValues(predicate)) {
      resource.removeValue(predicate, value);
      log.info("GraphComposer has removed existing relation {} -> {} -> {}", subjectUri, predicate, value);
    }
  }

  private void addResourceAttribute(DeltaResource resource,
                                    String subjectUri,
                                    String predicate,
                                    String object) {
    resource.addValue(predicate, ResourceFactory.createResource(object));
    log.info("GraphComposer has created relation to rdfs:Resource {} -> {} -> {}", subjectUri, predicate, object);
  }

  private void addXSDAttribute(DeltaResource resource,
                               GraphComposerSubject subject,
                               String subjectUri,
                               String predicate,
                               String object,
                               String objectType) {
    RDFDatatype xsdDatatype = xsdTypeMapper.getTypeByName(objectType);

    if (xsdDatatype == null) {
      log.warn("Unsupported XSD datatype [{}] found in graph model subject {}", objectType, subject);
      xsdDatatype = XSDDatatype.XSDstring;
    }

    Literal typedXsdLiteral = ResourceFactory.createTypedLiteral(object, xsdDatatype);
    resource.addValue(predicate, typedXsdLiteral);

    log.info("GraphComposer has created relation to XSD datatype {} -> {} -> {}^^{}", subjectUri, predicate, object, objectType);
  }

  private void addAppProfileDefinedAttribute(ApplicationView view,
                                             DeltaResource resource,
                                             String subjectUri,
                                             String predicate,
                                             String object,
                                             String objectType) {
    ApplicationProfile appProfile = view.getApplicationProfile();
    TypedResource linkedResource = view.find(() -> appProfile.getType(objectType), object);

    resource.addValue(predicate, linkedResource);
    log.info("GraphComposer has created relation to App Profile supported datatype {} -> {} -> {}^^...#{}", subjectUri, predicate, object, objectType);
  }

  @CachedDeltaResource
  public Map<String, TypedResource> assignAttributesToGraphEntities(ApplicationView view,
                                                                    List<GraphComposerSubject> subjects,
                                                                    Map<String, String> context,
                                                                    Model inputModel) {
    Map<String, TypedResource> resourcesByUri = new HashMap<>();

    for (GraphComposerSubject subject : subjects) {
      if (StringUtils.isNotBlank(subject.getType(context))) {
        String subjectUri = subject.getUri(context);
        try {
          DeltaResource resource = view.findDeltaResource(view.getApplicationProfile()
                                                              .getType(subject.getType(context)), subjectUri);

          resourcesByUri.put(subjectUri, resource);

          if (subject.getAttributes() == null) continue;

          log.info("GraphComposer has started processing attributes for subject {}", subjectUri);
          for (GraphComposerAttribute attribute : subject.getAttributes()) {
            String objectType = attribute.getObjectType(context);
            String object = attribute.getObject(context);
            String predicate = attribute.getPredicate(context);
            Boolean isUnique = attribute.getUnique(context);
            boolean isReplace = attribute.isReplace();
            if (isUnique && isReplace) throw new NonMaskableException("Configuration for " + resource.getType() + " " + predicate + " is both unique and replace");

            if (resource.hasValues(predicate)) {
              List<RdfValue> values = resource.getValues(predicate);   // current values
              String strValues = values.stream().map(Object::toString).collect(Collectors.joining());
              log.info("GraphComposer has found existing relation {} -> {} -> {} and values {}", subjectUri, predicate, object, strValues);
              if (isUnique) {
                removeExistingRelations(resource, predicate, subjectUri, object);
              }
              else if (isReplace) {
                removeAllExisting(resource, predicate, subjectUri);
              }
              else if (attribute.hasVersionPredicate()) {
                String versionPredicate = attribute.getVersionPredicate(context);
                String versionLoadPattern = attribute.getVersionLoadPattern();
                String versionParameter = attribute.getVersionParameter();

                Integer sequence = 1 + //Stream.concat(values.stream(), resource.getValues(versionPredicate).stream())
                                   values.stream().map(rdfValue -> {
                                     resource.addValue(versionPredicate, rdfValue);
                                     return Integer.parseInt(GraphComposerUtils
                                                                     .getContextByPattern(versionLoadPattern,
                                                                                          rdfValue.getResource().getURI())
                                                                     .getOrDefault(versionParameter, "0"));
                                   }).max(Integer::compare).orElse(0);

                context.put(versionParameter, sequence.toString());
                String objectNewVersion = GraphComposerUtils.getContextAndApplyToPattern(versionLoadPattern, object, attribute.getObject(), context);

                removeExistingRelations(resource, predicate, subjectUri, objectNewVersion);
                addResourceAttribute(resource, subjectUri, predicate, objectNewVersion);

                continue;
              }
            }

            if (isReplace) {
              addAllValues(resource, predicate, inputModel);
            }
            else if (StringUtils.isEmpty(objectType)) {
              addResourceAttribute(resource, subjectUri, predicate, object);
            }
            else if (GraphComposerUtils.compareUriNamespace(XSDDatatype.XSD, objectType)) {
              addXSDAttribute(resource, subject, subjectUri, predicate, object, objectType);
            }
            else if (StringUtils.isNotBlank(objectType)) {
              addAppProfileDefinedAttribute(view, resource, subjectUri, predicate, object, objectType);
            }
            else {
              resource.addValue(predicate, object);
              log.info("GraphComposer has created relation to XSD string {} -> {} -> {}", subjectUri, predicate, object);
            }
          }
          log.info("GraphComposer has finished processing attributes for subject {}", subjectUri);
        }
        catch (NonMaskableException ex) {
          throw ex;
        }
        catch (Exception ex) {
          log.error("Can not assign resource {} attributes", subjectUri, ex);
        }
      }
    }
    CachedDeltaResourceAspect.saveCache(view);

    return resourcesByUri;
  }

  private void addAllValues(DeltaResource resource, String predicate, Model inputModel) {
    ApplicationProfile.Attribute attributeForPredicate = resource.getType().getAttribute(predicate);
    if (null == attributeForPredicate) throw new NonMaskableException("Type " + resource.getType() + " does not contain attribute " + predicate);
    String predicateUri = attributeForPredicate.getUri();
    if (StringUtils.isBlank(predicateUri)) throw new NonMaskableException("No URI for attribute " + attributeForPredicate + " in type " + resource.getType());

    log.info("Going to add input values: {} - {}", resource.getResource(), predicate);
    inputModel.listObjectsOfProperty(resource.getResource(), inputModel.getProperty(predicateUri))
              .forEach(node -> {
                log.info("Adding input value {}", node);
                resource.addValue(attributeForPredicate, node);
              });
  }

  private static class NonMaskableException extends RuntimeException {
    private NonMaskableException(String message) {
      super(message);
    }
  }
}
