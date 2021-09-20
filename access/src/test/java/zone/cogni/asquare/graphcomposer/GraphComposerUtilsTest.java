package zone.cogni.asquare.graphcomposer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphComposerUtilsTest {
 /*

  @Test
  public void testSimple() {
    String testPattern = "...{name1:upcase}---{name2:lowcase}+++{name3}==={name4}\\\\\\";
    String testStr = "...1y---2U+++3===44\\\\\\";
    String correctedTestStr = "...1Y---2u+++3===44\\\\\\";

    Map<String, String> ctx = GraphComposerUtils.getContextByPattern(testPattern, testStr);
    Assertions.assertEquals("1Y", ctx.get("name1"));
    Assertions.assertEquals("2u", ctx.get("name2"));
    Assertions.assertEquals("3", ctx.get("name3"));
    Assertions.assertEquals("44", ctx.get("name4"));

    ctx.put("name1", ctx.get("name1").toLowerCase());

    Assertions.assertEquals(correctedTestStr, GraphComposerUtils.replace(testPattern, ctx));
  }

  @Test
  public void testRegex() {
    String testPattern = "{f1}-{f2:regex(.*?-.*?-.*?)}-{f3:regex(.*?-A-.*?)}-{f4}";
    String testStr = "a1-a2-x-y-a3-A-z-a4";

    Map<String, String> ctx = GraphComposerUtils.getContextByPattern(testPattern, testStr);
    Assertions.assertEquals("a1", ctx.get("f1"));
    Assertions.assertEquals("a2-x-y", ctx.get("f2"));
    Assertions.assertEquals("a3-A-z", ctx.get("f3"));
    Assertions.assertEquals("a4", ctx.get("f4"));

    Assertions.assertEquals(testStr, GraphComposerUtils.replace(testPattern, ctx));
  }

  @Test
  public void testUrlCh() {
    String pathPattern = "/eli/{consolidation collection}/{year}/{natural identifier}/{point in time}/{language}/{format:lowcase}/fedlex-data-admin-ch-eli-{consolidation collection}-{year}-{natural identifier}-{point in time}-{language}-{format:lowcase}.{format:ext}";
    String uriPattern = "https://fedlex.data.admin.ch/eli/{consolidation collection}/{year}/{natural identifier}/{point in time}/{language}/{format:lowcase}";

    String path = "/eli/100/2020/0001/20200101/fr/pdf-a/fedlex-data-admin-ch-eli-100-2020-0001-20200101-fr-pdf-a.pdf";
    String uri = "https://fedlex.data.admin.ch/eli/100/2020/0001/20200101/fr/pdf-a";

    Assertions.assertEquals(uri, GraphComposerUtils.getContextAndApplyToPattern(pathPattern, path, uriPattern));
    Assertions.assertEquals(path, GraphComposerUtils.getContextAndApplyToPattern(uriPattern, uri, pathPattern));
  }

  @Test
  public void testUrlLux() {
    String pathPattern = "/eli/{consolidation collection:regexD2S(.*?\\/.*?\\/.*?)}/{year:regexD2S(.*?\\/.*?\\/.*?)}/{natural identifier}/jo/{language}/{format:lowcase}/data-legilux-public-lu-eli-{consolidation collection:regexS2D(.*?\\-.*?\\-.*?)}-{year:regexS2D(.*?\\-.*?\\-.*?)}-{natural identifier}-jo-{language}-{format:lowcase}.{format:ext}";
    String uriPattern = "http://data.legilux.public.lu/eli/{consolidation collection:regexD2S(.*?\\/.*?\\/.*?)}/{year:regexD2S(.*?\\/.*?\\/.*?)}/{natural identifier}/jo/{language}/{format:lowcase}";

    String path = "/eli/etat/adm/pa/2018/08/07/b2178/jo/fr/pdf/data-legilux-public-lu-eli-etat-adm-pa-2018-08-07-b2178-jo-fr-pdf.pdf";
    String uri = "http://data.legilux.public.lu/eli/etat/adm/pa/2018/08/07/b2178/jo/fr/pdf";

    Assertions.assertEquals(uri, GraphComposerUtils.getContextAndApplyToPattern(pathPattern, path, uriPattern));
    Assertions.assertEquals(path, GraphComposerUtils.getContextAndApplyToPattern(uriPattern, uri, pathPattern));
  }

  @Test
  public void testContext() {
    Assertions.assertEquals("A,B C.XY.Z", GraphComposerUtils.getContextAndApplyToPattern("{str1}-{str2} . {string 3}", "A-B C . XY.Z", "{str1},{str2}.{string 3}"));
    GraphComposerUtils.getContextAndApplyToPattern("https://fedlex.data.admin.ch/eli/{collection}/{year}/{natural identifier}/{language}/{format}/eli-{collection}-{year}-{natural identifier}-{language}-{format}.{format}", "https://fedlex.data.admin.ch/eli/oc/2004/30/en/html/eli-oc-2004-30-en-html.html", "\\eli\\{collection}\\{year}\\{natural identifier}\\{language}\\{format}\\fedlex-data-admin-ch-eli-{collection}-{year}-{natural identifier}-{language}-{format}.{format}");
    GraphComposerUtils.getContextAndApplyToPattern("https://fedlex.data.admin.ch/file/eli-{collection}-{year}-{natural identifier}-{language}-{format}.{format}", "https://fedlex.data.admin.ch/file/eli-oc-2004-30-en-html.html", "\\eli\\{collection}\\{year}\\{natural identifier}\\{language}\\{format}\\fedlex-data-admin-ch-eli-{collection}-{year}-{natural identifier}-{language}-{format}.{format}");
  }
*/
}
