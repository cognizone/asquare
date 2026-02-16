package zone.cogni.asquare.service.elasticsearch.info;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ElasticsearchMetadata {
  private static final Logger log = LoggerFactory.getLogger(ElasticsearchMetadata.class);

  private final Info_stats stats;
  private final Info_clusterState clusterState;

  public ElasticsearchMetadata(JsonNode statsJson, JsonNode clusterStateJson) {
    this.stats = new Info_stats(statsJson);
    this.clusterState = new Info_clusterState(clusterStateJson);
  }

  public List<Index> getIndexes() {
    List<String> statsIndexNames = stats.getIndexNames();
    List<String> clusterStateIndexNames = clusterState.getIndexNames();

    Collection<String> disjunction = CollectionUtils.disjunction(statsIndexNames, clusterStateIndexNames);
    Collection<String> disjunctionNonMetadata = disjunction.stream().filter(name -> !name.startsWith(".")).collect(Collectors.toSet());
    if (!disjunctionNonMetadata.isEmpty())
      throw new RuntimeException("some indexes not found in different metadata sets: " + disjunctionNonMetadata);

    if (!disjunction.isEmpty())
      log.info("some metadata indexes not found in metadata sets: {}", disjunction);

    return statsIndexNames
            .stream()
            .sorted()
            .map(this::buildIndex)
            .collect(Collectors.toList());
  }

  public Index buildIndex(String indexName) {
    Index index = new Index();
    index.setName(indexName);
    index.setUuid(stats.getUuid(indexName));
    index.setCreatedDate(clusterState.getCreatedDate(indexName));
    index.setDocumentCount(stats.getDocumentCount(indexName));
    index.setSizeInBytes(stats.getSizeInBytes(indexName));
    index.setAliases(clusterState.getAliases(indexName));
    return index;
  }

  private Info_stats getStats() {
    return stats;
  }

  private Info_clusterState getClusterState() {
    return clusterState;
  }

  public static class Index {
    private String name;
    private String uuid;
    private String createdDate;
    private long documentCount;
    private long sizeInBytes;
    private List<String> aliases;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUuid() {
      return uuid;
    }

    public void setUuid(String uuid) {
      this.uuid = uuid;
    }

    public String getCreatedDate() {
      return createdDate;
    }

    public void setCreatedDate(String createdDate) {
      this.createdDate = createdDate;
    }

    public long getDocumentCount() {
      return documentCount;
    }

    public void setDocumentCount(long documentCount) {
      this.documentCount = documentCount;
    }

    public long getSizeInBytes() {
      return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
      this.sizeInBytes = sizeInBytes;
    }

    public List<String> getAliases() {
      return aliases;
    }

    public void setAliases(List<String> aliases) {
      this.aliases = aliases;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Index index = (Index) o;
      return uuid.equals(index.uuid);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uuid);
    }
  }

  public static class Configuration {

    private int connectTimeout = 3000;
    private int readTimeout = 10000;

    public int getConnectTimeout() {
      return connectTimeout;
    }

    /**
    * Determines the timeout in milliseconds until a connection is established.
    * A timeout value of zero is interpreted as an infinite timeout. A negative value is interpreted as undefined (system default if applicable).
    * default = 3000
    **/
    public void setConnectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
      return readTimeout;
    }

    /**
    * Defines the socket timeout (SO_TIMEOUT) in milliseconds, which is the timeout for waiting for data or, put differently, a maximum period inactivity between two consecutive data packets).
    * A timeout value of zero is interpreted as an infinite timeout. A negative value is interpreted as undefined (system default if applicable).
    * default = 1000
    **/
    public void setReadTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
    }
  }

}
