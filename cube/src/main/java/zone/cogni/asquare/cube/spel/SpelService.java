package zone.cogni.asquare.cube.spel;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import zone.cogni.core.spring.ResourceHelper;

import java.util.HashMap;
import java.util.Map;

public class SpelService {

  private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
  private final Map<String, Expression> expressionCache = new HashMap<>();

  public NamedTemplate processTemplate(NamedTemplate namedTemplate, Object root) {
    String result = processTemplate(namedTemplate.getTemplate(), root);
    return getNewNamedTemplate(namedTemplate, root, result);
  }

  private NamedTemplate getNewNamedTemplate(NamedTemplate namedTemplate, Object root, String result) {
    NamedTemplate copy = namedTemplate.copy();
    copy.setRoot(root);
    copy.setResult(result);
    return copy;
  }

  public String processTemplate(Resource templateResource, Object root) {
    String template = ResourceHelper.toString(templateResource);
    return processTemplate(template, root);
  }

  public String processTemplate(String template, Object root) {
    Expression expression = getExpression(template);
    EvaluationContext context = getContext(root);
    return expression.getValue(context, String.class);
  }

  /**
   * According to stackoverflow https://stackoverflow.com/a/16775689/328808
   * Expression instances are threadsafe, so we can cache them.
   */
  private Expression getExpression(String template) {
    try {
      String md5Hex = DigestUtils.md5Hex(template);

      boolean isCached = expressionCache.containsKey(md5Hex);
      Expression expression = isCached
                              ? expressionCache.get(md5Hex)
                              : spelExpressionParser.parseExpression(template, new TemplateParserContext());

      if (!isCached) expressionCache.put(md5Hex, expression);
      return expression;
    }
    catch (RuntimeException e) {
      throw new RuntimeException("SpEL expression template parsing failed for: \n" + template, e);
    }
  }

  private EvaluationContext getContext(Object root) {
    return root instanceof EvaluationContext ? (EvaluationContext) root
                                             : new StandardEvaluationContext(root);
  }
}
