package zone.cogni.asquare.graphcomposer.model;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class GraphComposerModel {

  private List<GraphComposerSubject> subjects;

  public List<GraphComposerSubject> getSubjects() {
    return subjects;
  }

  public void setSubjects(List<GraphComposerSubject> subjects) {
    this.subjects = subjects;
  }

  public GraphComposerSubject getSubjectByFieldName(String fieldName, String fieldValue) {
    return subjects.stream().filter(s -> StringUtils.equals(s.getField(fieldName), fieldValue)).findFirst().get();
  }

  public GraphComposerSubject getSubjectByName(String name) {
    return getSubjectByFieldName("name", name);
  }

  public String toString() {
    String str = "Graph Model";
    if (subjects != null) {
      str += " subjects: ";
      for (GraphComposerSubject subject : subjects) {
        str += " " + subject.toString() + ",";
      }
    }
    return StringUtils.removeEnd(str, ",");
  }

}
