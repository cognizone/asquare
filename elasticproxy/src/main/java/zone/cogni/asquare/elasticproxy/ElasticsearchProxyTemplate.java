package zone.cogni.asquare.elasticproxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElasticsearchProxyTemplate {

  static final Logger log = LoggerFactory.getLogger(ElasticsearchProxyTemplate.class);

  private static final MapAccessor mapAccessor = new MapAccessor() {
    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
      return true;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
      try {
        return super.read(context, target, name);
      }
      catch (Exception ex) {
        return new TypedValue("");
      }
    }
  };

  private final RestClient restClient;
  private final SpringTemplateEngine templateEngine = new SpringTemplateEngine();

  private String name;
  private Map<String, ParamValidator> paramValuesValidator;
  private String urlTemplate;
  private String queryTemplate;
  private Set<String> paramNamesWhitelist;
  private Map<String, Object> queryTemplateParams;
  private HttpMethod httpMethod;

  public ElasticsearchProxyTemplate(HttpHost[] hosts) {
    this.templateEngine.setTemplateResolver(new StringTemplateResolver());
    this.restClient = RestClient.builder(hosts).build();
    this.name = UUID.randomUUID().toString();
  }

  public ElasticsearchProxyTemplate(String name, HttpHost[] hosts) {
    this(hosts);
    setName(name);
  }

  public String getQueryTemplate() {
    return queryTemplate;
  }

  public void setQueryTemplate(String queryTemplate) {
    this.queryTemplate = queryTemplate;
  }

  public String getUrlTemplate() {
    return urlTemplate;
  }

  public void setUrlTemplate(String urlTemplate) {
    this.urlTemplate = urlTemplate;
  }

  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(HttpMethod httpMethod) {
    this.httpMethod = httpMethod;
  }

  public Map<String, Object> getQueryTemplateParams() {
    return queryTemplateParams;
  }

  public void setQueryTemplateParams(Map<String, Object> queryTemplateParams) {
    this.queryTemplateParams = queryTemplateParams;
  }

  public Set<String> getParamNamesWhitelist() {
    return paramNamesWhitelist;
  }

  public void setParamNamesWhitelist(Set<String> paramNamesWhitelist) {
    this.paramNamesWhitelist = paramNamesWhitelist;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Map<String, ParamValidator> getParamValuesValidator() {
    return paramValuesValidator;
  }

  public void setParamValuesValidator(Map<String, ParamValidator> paramValuesValidator) {
    this.paramValuesValidator = paramValuesValidator;
  }

  private String buildUrl(String path) {
    return UriComponentsBuilder.fromPath(path).build(false).toUri().toString();
  }

  private Map<String, Object> mergeParams(MultiValueMap<String, String> urlParams, Map<String, Object> bodyParams) {
    return Stream.of(queryTemplateParams, bodyParams,
                     urlParams.entrySet()
                              .stream()
                              .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().toArray(new String[e.getValue().size()])
                              ))).filter(Objects::nonNull).flatMap(map -> map.entrySet().stream())
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private boolean validateParams(String requestUUID, Map<String, Object> params) {
    if (paramNamesWhitelist != null) {
      if (!paramNamesWhitelist.containsAll(params.keySet())) {
        log.error("Request \"{}\" for elasticproxy template \"{}\" url parameters are not allowed \"{}\".",
                  requestUUID,
                  name,
                  params.keySet());
        return false;
      }
    }

    if (paramValuesValidator != null) {
      for (String paramName : params.keySet()) {
        Object paramValue = params.get(paramName);
        if (paramValue != null && paramValuesValidator.containsKey(name) && !paramValuesValidator.get(name).validate(paramValue)) {
          log.error("Request \"{}\" for elasticproxy template \"{}\" value is not allowed \"{}\" for parameter \"{}\".",
                    requestUUID,
                    name,
                    paramValue,
                    paramName);
          return false;
        }
      }
    }
    return true;
  }

  public ResponseEntity<Resource> process(MultiValueMap<String, String> urlParams, Map<String, Object> bodyParams) throws IOException {
    String requestUUID = UUID.randomUUID().toString();

    Map<String, Object> params = mergeParams(urlParams, bodyParams);

    if (!validateParams(requestUUID, params)) {
      return ResponseEntity.badRequest().body(new InputStreamResource(IOUtils.toInputStream(requestUUID, StandardCharsets.UTF_8)));
    }

    return executeRequest(requestUUID, httpMethod, urlTemplate, params);
  }

  private ResponseEntity<Resource> executeRequest(String requestUUID, HttpMethod httpMethod, String url, Map<String, Object> params) throws IOException {
    Request request = new Request(httpMethod.name(), buildUrl(spel(url, params)));

    if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) {
      request.setJsonEntity(thymeleaf(getTemplateContent(), params));
    }

    try {
      Response response = restClient.performRequest(request);
      HttpStatus status = HttpStatus.resolve(response.getStatusLine().getStatusCode());

      if (status.is2xxSuccessful()) {
        return ResponseEntity.ok().body(new InputStreamResource(response.getEntity().getContent()));
      }
      return ResponseEntity.status(status).build();
    }
    catch (ResponseException ex) {
      Response response = ex.getResponse();
      log.error("Request \"{}\" failed. Elastic proxy template {}. Elastic request: {}, response: {}",
                requestUUID,
                params,
                response.getRequestLine(),
                response.getStatusLine());
      return ResponseEntity.badRequest().body(new InputStreamResource(IOUtils.toInputStream(requestUUID, StandardCharsets.UTF_8)));
    }
  }

  private String getTemplateContent() throws IOException {
    return IOUtils.toString(new DefaultResourceLoader().getResource(queryTemplate).getInputStream(), StandardCharsets.UTF_8);
  }

  private String spel(String expression, Map<String, Object> map) {
    StandardEvaluationContext context = new StandardEvaluationContext(map);
    context.addPropertyAccessor(mapAccessor);
    ExpressionParser expressionParser = new SpelExpressionParser();
    return expressionParser.parseExpression(expression, new TemplateParserContext()).getValue(context, String.class);
  }

  private String thymeleaf(String expression, Map<String, Object> map) {
    Context ctx = new Context();
    ctx.setVariables(map);
    return templateEngine.process(expression, ctx);
  }

  public enum HttpMethod {
    GET, POST, PUT
  }

}
