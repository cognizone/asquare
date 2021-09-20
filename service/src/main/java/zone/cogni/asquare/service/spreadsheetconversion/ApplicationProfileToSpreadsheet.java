package zone.cogni.asquare.service.spreadsheetconversion;

import com.google.common.collect.ImmutableList;
import io.vavr.API;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.PropertyValue;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Predicates.instanceOf;
import static io.vavr.API.$;
import static io.vavr.API.Case;

@Service
public class ApplicationProfileToSpreadsheet {

  private static final List<String> headers = new ArrayList<>(Arrays.asList("Entity", "Class URI", "Sub Class Of",
                                                                            "Property Name", "Property URI", "Property Of Class",
                                                                            "Range", "Cardinality", "Description"));
  private final PrefixCcService prefixCcService;
  private int rowCount;

  public ApplicationProfileToSpreadsheet(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public ByteArrayResource getApplicationProfileSpreadsheet(ApplicationProfile profile) {
    rowCount = 0;
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Application Profile Model");

    writeHeader(sheet);
    rowCount++;

    profile.getTypes().values().forEach(classType -> {
      List<String> valuesAfterAddingClass = addClass(sheet, classType);

      classType.getAttributes().values().forEach(attribute -> {
        List<String> valueList = new ArrayList<>(valuesAfterAddingClass);
        valueList.add(attribute.getAttributeId());
        valueList.add(prefixCcService.getShortenedUri(attribute.getUri()));
        valueList.add(attribute.getType().getClassId());
        valueList.add(getRange(attribute.getRules(Range.class)));
        valueList.add(getCardinalities(attribute));
        valueList.add(attribute.getExtra()
                               .getValue()
                               .stream()
                               .findFirst()
                               .map(PropertyValue::getValue)
                               .orElse(""));

        writeAttribute(sheet, valueList);
      });
      rowCount++;
    });

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    saveWorkbook(workbook, byteArrayOutputStream);
    return new ByteArrayResource(byteArrayOutputStream.toByteArray());
  }

  private void writeHeader(Sheet sheet) {
    Row row = sheet.createRow(0);
    rowCount++;
    headers.forEach(header -> row.createCell(headers.indexOf(header)).setCellValue(header));
  }

  private List<String> addClass(Sheet sheet, ApplicationProfile.Type classType) {
    String entityName = classType.getClassId();
    String classUri = getClassUri(classType);

    String subClassOf = classType.getRule(SubClassOf.class)
                                 .map(r -> r.getValue().get(0))
                                 .getOrElse("");

    String description = classType.getExtra()
                                  .getValue()
                                  .stream()
                                  .findFirst()
                                  .map(PropertyValue::getValue)
                                  .orElse("");
    writeClassType(sheet, entityName, classUri, subClassOf, description);
    return ImmutableList.of(entityName, classUri, subClassOf);
  }

  private String getClassUri(ApplicationProfile.Type classType) {
    Set<String> classUris = classType.getTypeDef()
                                     .stream()
                                     .flatMap(r -> r.getRules(RdfType.class).stream())
                                     .map(rdf -> rdf.getValue())
                                     .collect(Collectors.toSet());
    String classUri = "";
    if (classUris.size() == 1) {
      for (String uri : classUris) {
        classUri = prefixCcService.getShortenedUri(uri);
      }
    }
    else {
      throw new IllegalStateException("More than 1 class uri.");
    }
    return classUri;
  }

  private void writeClassType(Sheet sheet, String entityName, String classUri, String subClassOf, String description) {
    Row row = sheet.createRow(rowCount);
    rowCount++;
    row.createCell(headers.indexOf("Entity")).setCellValue(entityName);
    row.createCell(headers.indexOf("Class URI")).setCellValue(classUri);
    row.createCell(headers.indexOf("Sub Class Of")).setCellValue(subClassOf);
    row.createCell(headers.indexOf("Description")).setCellValue(description);
  }

  private String getRange(List<Range> rules) {
    return rules.stream()
                .map(r -> mapRange(r.getValue()))
                .collect(Collectors.joining(", "));
  }

  private String mapRange(Rule rule){
    return API.Match(rule).of(
        Case($(instanceOf(Or.class)), () -> getOr((Or) rule)),
        Case($(instanceOf(And.class)), () -> getAnd((And) rule)),
        Case($(instanceOf(Datatype.class)), () -> getDatatype((Datatype) rule)),
        Case($(instanceOf(ClassId.class)), () -> getClassId((ClassId) rule)),
        Case($(), this::throwUnsupportedOperationException));
  }

  private String getAnd(And rule) {
    return rule.getValue()
               .stream()
               .map(this::mapRange)
               .collect(Collectors.joining(", "));
  }

  private String getOr(Or rule) {
    return rule.getValue()
               .stream()
               .map(this::mapRange)
               .collect(Collectors.joining(", "));
  }

  private String getDatatype(Datatype rule) {
    return prefixCcService.getShortenedUri(rule.getValue());
  }

  private String getClassId(ClassId rule) {
    return rule.getValue();
  }

  private String throwUnsupportedOperationException() {
    throw new UnsupportedOperationException("Range of this type not supported at the moment.");
  }

  private String getCardinalities(ApplicationProfile.Attribute attribute) {
    String minCardinality = attribute.getRules(MinCardinality.class)
                                     .stream()
                                     .findFirst()
                                     .map(r -> r.getValue().toString())
                                     .orElse("0");


    String maxCardinality = attribute.getRules(MaxCardinality.class)
                                     .stream()
                                     .findFirst()
                                     .map(r -> r.getValue().toString())
                                     .orElse("n");


    return minCardinality + ".." + maxCardinality;
  }

  private void writeAttribute(Sheet sheet, List<String> valueList) {
    Row row = sheet.createRow(rowCount);
    rowCount++;
    int column = 0;
    for (String value : valueList) {
      row.createCell(column).setCellValue(value);
      column++;
    }
  }

  private void saveWorkbook(Workbook workbook, ByteArrayOutputStream byteArrayOutputStream) {
    try {
      workbook.write(byteArrayOutputStream);
      workbook.close();
    }
    catch (IOException e) {
      throw new RuntimeException("Error while saving workbook.", e);
    }
  }

}
