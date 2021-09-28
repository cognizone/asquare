package zone.cogni.asquare.edit.delta;

import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.rdf.RdfValue;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypedResourceDelta extends Delta {

  @Nonnull
  private final DeltaResource typedResource;

  @Nonnull
  private final Map<ApplicationProfile.Attribute, List<RdfValue>> add = new HashMap<>();

  @Nonnull
  private final Map<ApplicationProfile.Attribute, List<RdfValue>> remove = new HashMap<>();

  public TypedResourceDelta(@Nonnull DeltaResource typedResource) {
    this.typedResource = typedResource;
  }

  public boolean hasChanges() {
    return !getAddStatements().isEmpty() || !getRemoveStatements().isEmpty();
  }

  public boolean hasChanges(ApplicationProfile.Attribute attribute) {
    return hasStatementsToAdd(attribute) || hasStatementsToRemove(attribute);
  }

  public void appendToAdd(ApplicationProfile.Attribute attribute, List<RdfValue> values) {
    if (values.isEmpty()) return;
    add.put(attribute, values);
  }

  public void appendToRemove(ApplicationProfile.Attribute attribute, List<RdfValue> values) {
    if (values.isEmpty()) return;
    remove.put(attribute, values);
  }

  @Override
  public List<Statement> getAddStatements() {
    List<Statement> addStatements = add.entrySet().stream()
            .flatMap(this::getStatements)
            .collect(Collectors.toList());

    if (typedResource.isNew()) {
      addStatements.addAll(getTypeStatements());
    }

    return addStatements;
  }

  @Override
  public List<Statement> getRemoveStatements() {
    List<Statement> removeStatements = remove.entrySet().stream()
            .flatMap(this::getStatements)
            .collect(Collectors.toList());

    if (typedResource.isDeleted()) {
      removeStatements.addAll(getTypeStatements());
    }

    return removeStatements;
  }

  private List<Statement> getTypeStatements() {
    return typedResource.getType().getRules().stream()
            .filter(rule -> rule instanceof RdfType)
            .map(rule -> (RdfType) rule)
            .map(SingleValueRule::getValue)
            .map(rdfType -> ResourceFactory.createStatement(typedResource.getResource(),
                    RDF.type,
                    ResourceFactory.createResource(rdfType)))
            .collect(Collectors.toList());
  }

  private Stream<Statement> getStatements(Map.Entry<ApplicationProfile.Attribute, List<RdfValue>> entry) {
    return entry.getValue().stream()
            .map(value -> getStatement(entry.getKey(), value));
  }

  private Statement getStatement(ApplicationProfile.Attribute attribute, RdfValue value) {
    return ResourceFactory.createStatement(
            typedResource.getResource(),
            ResourceFactory.createProperty(attribute.getUri()),
            value.isLiteral() ? value.getLiteral() : value.getResource()
    );
  }

  private boolean hasStatementsToAdd(ApplicationProfile.Attribute attribute) {
    List<RdfValue> rdfValues = add.get(attribute);
    return rdfValues != null && !rdfValues.isEmpty();
  }

  private boolean hasStatementsToRemove(ApplicationProfile.Attribute attribute) {
    List<RdfValue> rdfValues = remove.get(attribute);
    return rdfValues != null && !rdfValues.isEmpty();
  }


}
