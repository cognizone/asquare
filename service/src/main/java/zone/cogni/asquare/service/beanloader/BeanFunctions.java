package zone.cogni.asquare.service.beanloader;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.vocabulary.XSD;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import zone.cogni.asquare.rdf.BasicRdfValue;

import java.net.MalformedURLException;
import java.util.Objects;

/**
 * Until we have a better idea of how to implement this we should not continue development.
 */
@Deprecated
public class BeanFunctions {

  public static Resource resource(Object object) {
    Preconditions.checkState(object instanceof BasicRdfValue, "Type of 'resource' is " + object.getClass().getName());
    BasicRdfValue basicRdfValue = (BasicRdfValue) object;

    Literal literal = basicRdfValue.getLiteral();

    Preconditions.checkState(Objects.equals(literal.getDatatype().getURI(), XSD.xstring.getURI()));
    String resourceString = literal.getString();

    if (resourceString.startsWith("classpath:")) {
      return new ClassPathResource(StringUtils.substringAfter(resourceString, "classpath:"));
    }

    if (resourceString.startsWith("file:"))
      return new FileSystemResource(resourceString);

    if (resourceString.startsWith("http://") || resourceString.startsWith("https://")) {
      try {
        return new UrlResource(resourceString);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    throw new RuntimeException("Unsupported resource " + resourceString);
  }

}
