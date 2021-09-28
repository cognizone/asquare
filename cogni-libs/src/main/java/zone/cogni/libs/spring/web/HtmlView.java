package zone.cogni.libs.spring.web;


import zone.cogni.libs.core.utils.StringHelper;

public class HtmlView extends DataView {

  public HtmlView(String html) {
    super(StringHelper.toByteArray(html), "text/html", "UTF-8");
  }

}
