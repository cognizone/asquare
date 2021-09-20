package zone.cogni.asquare.applicationprofile.prefix;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.util.SplitIRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Import(PrefixCcConfiguration.class)
public class PrefixCcService {

  private static final Logger log = LoggerFactory.getLogger(PrefixCcService.class);

  private static final int LIMIT = 200;

  private final PrefixCcConfiguration prefixCcConfiguration;

  private final BiMap<String, String> prefixNamespaceMap = HashBiMap.create(LIMIT);


  public PrefixCcService(PrefixCcConfiguration prefixCcConfiguration) {
    this.prefixCcConfiguration = prefixCcConfiguration;
  }

  @PostConstruct
  public void init() {
    loadPrefixCcFile();
    HashMap<String, String> extraCcPrefixes = prefixCcConfiguration.getExtraCcPrefixes();

    extraCcPrefixes.forEach(this::addPrefixToMap);
  }

  private void loadPrefixCcFile() {
    Resource prefixCc = new ClassPathResource("prefix/cc/prefix.cc.csv");
    try {
      LineIterator lineIterator = IOUtils.lineIterator(prefixCc.getInputStream(), "UTF-8");
      int count = 0;
      while (lineIterator.hasNext() && count < LIMIT) {
        String line = lineIterator.nextLine();
        count += 1;

        processLine(line);
      }
    }
    catch (IOException e) {
      throw new RuntimeException("PrefixCc cannot be loaded.", e);
    }
  }

  private void processLine(String line) {
    String prefix = StringUtils.substringBefore(line, ",");
    String namespace = StringUtils.substringAfter(line, ",");

    Preconditions.checkState(prefix.length() + namespace.length() + 1 == line.length());
    addPrefixToMap(prefix, namespace);
  }

  private void addPrefixToMap(String prefix, String namespace) {
    Preconditions.checkState(!prefixNamespaceMap.containsKey(prefix));

    if (prefixNamespaceMap.containsValue(namespace)) return;

    prefixNamespaceMap.put(prefix, namespace);
  }

  public String getExpanded(String compact) {
    if (!compact.contains(":")) return compact;

    String prefix = StringUtils.substringBefore(compact, ":");
    String localName = StringUtils.substringAfter(compact, ":");
    if (localName.startsWith("/")) return compact;

    return getNamespaceOption(prefix)
            .map(namespace -> namespace + localName)
            .orElse(compact);
  }

  private Optional<String> getNamespaceOption(String prefix) {
    String namespace = prefixNamespaceMap.get(prefix);
    return Optional.ofNullable(namespace);
  }

  public String getNamespace(String prefix) {
    return getNamespaceOption(prefix).orElseThrow(() -> new RuntimeException("Prefix " + prefix + " not found."));
  }

  public String getPrefix(String namespace) {
    String prefix = prefixNamespaceMap.inverse().get(namespace);

    if (prefix == null) {
      String message = "Cannot find prefix for namespace " + namespace;
      log.warn(message);
      throw new IllegalStateException(message);
    }

    return prefix;
  }

  public String getShortenedUri(String fullUri) {
    int splitPoint = SplitIRI.splitpoint(fullUri);
    Preconditions.checkState(splitPoint > 0);

    String namespace = fullUri.substring(0, splitPoint);
    String local = fullUri.substring(splitPoint);
    Preconditions.checkState(local.length() > 0);

    String prefix = getPrefix(namespace);
    return String.join(":", prefix, local);
  }

  public String getShortForProperty(Property property) {
    String prefix = getPrefix(property.getNameSpace());
    return prefix + Character.toUpperCase(property.getLocalName().charAt(0)) + property.getLocalName().substring(1);
  }

  public String getPropertyForShort(String shortProperty) {
    String[] strings = StringUtils.splitByCharacterTypeCamelCase(shortProperty);
    String prefix = strings[0];
    String localNameToCorrect = StringUtils.substringAfter(shortProperty, prefix);
    String localName = Character.toLowerCase(localNameToCorrect.charAt(0)) + localNameToCorrect.substring(1);

    String namespace = getNamespace(prefix);
    return namespace + localName;
  }

  @Deprecated
  public void addNewPrefixToMap(Map<String, String> newNamespaceMap) {
    newNamespaceMap.forEach(this::addPrefixToMap);
  }

}
