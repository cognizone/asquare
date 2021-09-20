package zone.cogni.asquare.service.beanloader;

import com.google.common.base.Preconditions;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.ClassUtils;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Until we have a better idea of how to implement this we should not continue development.
 */
@Deprecated
public class BeanRegistry {

  private final BeanServiceRegistry beanServiceRegistry = new BeanServiceRegistry();

  private DefaultListableBeanFactory beanFactory;
  private ApplicationView applicationView;
  private String typeId;
  private String uri;

  public BeanRegistry(DefaultListableBeanFactory beanFactory, ApplicationView applicationView, String typeId, String uri) {
    this.beanFactory = beanFactory;
    this.applicationView = applicationView;
    this.typeId = typeId;
    this.uri = uri;
  }

  public Object get() {
    Object result = getBean(uri);
    if (result != null) return result;

    TypedResource typedResource = getTypedResource(typeId, uri);
    return getRegisteredBean(typedResource);
  }

  private Object getRegisteredBean(TypedResource typedResource) {
    Object bean = getBean(typedResource.getResource().getURI());
    if (bean != null) return bean;

    return getNewlyCachedBean(typedResource);
  }

  private Object getBean(String uri) {
    try {
      return beanFactory.getBean(uri);
    }
    catch (BeansException e) {
      return null;
    }
  }

  private TypedResource getTypedResource(String typeId, String uri) {
    ApplicationProfile.Type type = applicationView.getApplicationProfile().getType(typeId);
    TypedResource typedResource = applicationView.getRepository().getTypedResource(type, ResourceFactory.createResource(uri));
    return typedResource;
  }

  private Object getNewlyCachedBean(TypedResource typedResource) {
    String className = beanServiceRegistry.getServiceClass(typedResource);

    BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(className)
            .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

        typedResource.getType().getAttributeIds()
            .forEach(attribute -> {
              addAttributeToBean(beanDefinitionBuilder, typedResource, attribute);
            });

    beanFactory.registerBeanDefinition(uri, beanDefinitionBuilder.getBeanDefinition());

    return beanFactory.getBean(uri);
  }

  private void addAttributeToBean(BeanDefinitionBuilder beanDefinitionBuilder,
                                  TypedResource typedResource,
                                  String attributeId) {

    List<RdfValue> values = typedResource.getValues(attributeId);

    List<?> convertedValues = values.stream()
            .map(value -> convert(beanDefinitionBuilder, attributeId, value))
            .collect(Collectors.toList());

    Option<Method> methodOption = getMethodForAttribute(beanDefinitionBuilder, attributeId);
    String propertyName = getBeanNameFromMethod(methodOption);
    Object value = toListOrSingleValue(methodOption.get(), convertedValues);
    if (value != null) {
      beanDefinitionBuilder.addPropertyValue(propertyName, value);
    }
  }

  private Object toListOrSingleValue(Method method, List<?> values) {
    Preconditions.checkState(method.getParameterCount() == 1);
    Parameter parameter = method.getParameters()[0];

    if (values.isEmpty()) return null;

    if (ClassUtils.isAssignable(Collection.class, parameter.getType())) {
      Collection<Object> collection = (Collection<Object>) getInstance(parameter.getType());
      collection.addAll(values);
      return collection;
    }
    else {
      Preconditions.checkState(values.size() <= 1, "Cannot have more than one value for method " + method.getName());
      return values.isEmpty() ? null : values.get(0);
    }
  }

  private Object getInstance(Class<?> type) {
    try {
      // support collection interfaces
      if (Objects.equals(type, List.class)) return new ArrayList<>();
      if (Objects.equals(type, Set.class)) return new HashSet<>();
      if (Objects.equals(type, Collection.class)) return new ArrayList<>();

      return type.getConstructor().newInstance();
    }
    catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw handledException(e);
    }
  }

  private String getBeanNameFromMethod(Option<Method> methodOption) {
    String methodName = methodOption.get().getName();
    return StringUtils.uncapitalize(StringUtils.stripStart(methodName, "set"));
  }

  private Option<Method> getMethodForAttribute(BeanDefinitionBuilder beanDefinitionBuilder, String attributeId) {
    Option<Method> singularMethod = getMethod(beanDefinitionBuilder, getSetterMethodName(attributeId));
    Option<Method> pluralMethod = getMethod(beanDefinitionBuilder, getSetterMethodName(attributeId) + "s");
    return singularMethod.orElse(pluralMethod);
  }

  private Option<Method> getMethod(BeanDefinitionBuilder beanDefinitionBuilder, String methodName) {
    return getMethod(getTypeFor(beanDefinitionBuilder), methodName);
  }

  private Class<?> getTypeFor(BeanDefinitionBuilder beanDefinitionBuilder) {
    String typeName = beanDefinitionBuilder.getRawBeanDefinition().getBeanClassName();
    return getClass(typeName);
  }

  private Option<Method> getMethod(Class<?> type, String methodName) {
    return Option.ofOptional(
            Arrays.stream(type.getMethods())
                    .filter(method -> Objects.equals(method.getName(), methodName))
                    .findFirst()
    );
  }

  private Class<?> getClass(String className) {
    try {
      return Class.forName(className);
    }
    catch (ClassNotFoundException e) {
      throw handledException(e);
    }
  }

  private String getSetterMethodName(String attributeId) {
    return "set" + StringUtils.capitalize(attributeId);
  }

  private Object convert(BeanDefinitionBuilder beanDefinitionBuilder, String attributeId, RdfValue value) {
    try {

      Option<Method> instanceMethod = getMethod(beanDefinitionBuilder, getSetterMethodName(attributeId));
      if (instanceMethod.isDefined()) {
        Method method = instanceMethod.get();
        Tuple2<Boolean, Object> isCallable = isCallable(method, value);
        if (isCallable._1) return isCallable._2;
      }

      Option<Method> beanFunctionMethod = getMethod(BeanFunctions.class, attributeId);
      if (beanFunctionMethod.isDefined()) {
        return beanFunctionMethod.get().invoke(null, value);
      }

      throw new RuntimeException("Support other conversions!");

    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw handledException(e);
    }
  }

  private Tuple2<Boolean, Object> isCallable(Method method, RdfValue value) {
    Tuple2<Boolean, Object> fail = new Tuple2<>(Boolean.FALSE, null);

    if (method.getParameterCount() != 1) return fail;

    Parameter parameter = method.getParameters()[0];
    Class<?> parameterType = parameter.getType();

    if (value instanceof TypedResource) {
      TypedResource typedResource = (TypedResource) value;
      return new Tuple2<>(true, getRegisteredBean(typedResource));
    }

    if (value instanceof BasicRdfValue) {
      BasicRdfValue basicRdfValue = (BasicRdfValue) value;
      Literal literal = basicRdfValue.isLiteral() ? basicRdfValue.getLiteral() : null;

      if (Objects.equals(parameterType, String.class))
        return new Tuple2<>(true, literal.getString());

      if (Objects.equals(parameterType, Boolean.class) || Objects.equals(parameterType, boolean.class))
        return new Tuple2<>(true, literal.getBoolean());

      if (Objects.equals(parameterType, Integer.class) || Objects.equals(parameterType, int.class))
        return new Tuple2<>(true, literal.getInt());

      if (Objects.equals(parameterType, Long.class) || Objects.equals(parameterType, long.class))
        return new Tuple2<>(true, literal.getLong());

      if (Objects.equals(parameterType, Float.class) || Objects.equals(parameterType, float.class))
        return new Tuple2<>(true, literal.getFloat());

      if (Objects.equals(parameterType, Double.class) || Objects.equals(parameterType, double.class))
        return new Tuple2<>(true, literal.getDouble());

      if (Objects.equals(parameterType, Byte.class) || Objects.equals(parameterType, byte.class))
        return new Tuple2<>(true, literal.getByte());

      if (Objects.equals(parameterType, Character.class) || Objects.equals(parameterType, char.class))
        return new Tuple2<>(true, literal.getChar());

      if (Objects.equals(parameterType, Short.class) || Objects.equals(parameterType, short.class))
        return new Tuple2<>(true, literal.getShort());
    }

    return fail;
  }

  private RuntimeException handledException(Exception e) {
    if (e instanceof RuntimeException) throw (RuntimeException) e;
    if (e instanceof InvocationTargetException) throw new RuntimeException(e.getCause());

    throw new RuntimeException(e);
  }
}
