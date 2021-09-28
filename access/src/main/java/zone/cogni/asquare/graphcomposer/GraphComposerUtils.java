package zone.cogni.asquare.graphcomposer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import zone.cogni.asquare.rdf.RdfValue;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GraphComposerUtils {

  private static final MapAccessor mapAccessor = new MapAccessor() {

    @Override
    public boolean canRead(EvaluationContext context, @Nullable Object target, String name) {
      return true;
    }

    @Override
    public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      try {
        return super.read(context, target, name);
      }
      catch (Exception ex) {
        return new TypedValue("");
      }
    }
  };

  public static boolean validatePattern(String pattern, String value) {
    return Pattern.compile(pattern).matcher(value).matches();
  }

  public static Map<String, String> getContextByPattern(String loadPattern, String value) {
    Pattern p = Pattern.compile(loadPattern);
    Matcher m = p.matcher(value);
    if (m.find()) {
      return RegexUtils.getNamedGroups(p).keySet().stream()
                       .filter(key->StringUtils.isNotBlank(m.group(key)))
                       .collect(Collectors.toMap(Function.identity(), m::group));
    }
    return new HashMap<>();
  }

  public static String getContextAndApplyToPattern(String loadPattern, String value, String renderPattern) {
    return getContextAndApplyToPattern(loadPattern, value, renderPattern, null);
  }

  public static String getContextAndApplyToPattern(String loadPattern,
                                                   String value,
                                                   String renderPattern,
                                                   String extraProperty, String extraValue) {
    Map<String, String> context = getContextByPattern(loadPattern, value);
    if (StringUtils.isNotBlank(extraProperty)) {
      context.put(extraProperty, extraValue);
    }
    return replace(renderPattern, context);
  }

  public static String getContextAndApplyToPattern(String loadPattern,
                                                   String value,
                                                   String renderPattern,
                                                   Map<String, String> extraMapContext) {
    Map<String, String> context = getContextByPattern(loadPattern, value);
    if (extraMapContext != null) {
      context.putAll(extraMapContext);
    }
    return replace(renderPattern, context);
  }

  public static String replace(String renderExpression, Map<String, String> mapContext) {
    if(StringUtils.isBlank(renderExpression)) {
      return renderExpression;
    }
    StandardEvaluationContext context = new StandardEvaluationContext(mapContext);
    context.addPropertyAccessor(mapAccessor);
    ExpressionParser expressionParser = new SpelExpressionParser();
    return expressionParser.parseExpression(renderExpression, new TemplateParserContext()).getValue(context, String.class);
  }

  public static Boolean equalsValue(RdfValue rdfValue, String strValue) {
    return (rdfValue.isLiteral() && org.apache.commons.lang3.StringUtils.equals(rdfValue.getLiteral()
                                                                                        .getLexicalForm(), strValue)) ||
           (rdfValue.isResource() && org.apache.commons.lang3.StringUtils.equals(rdfValue.getResource().getURI(), strValue));
  }

  public static Boolean compareUriNamespace(String ns, String uri) {
    return StringUtils.startsWithIgnoreCase(uri, ns);
  }

}
