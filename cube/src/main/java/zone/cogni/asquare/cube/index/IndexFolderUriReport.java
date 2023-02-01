package zone.cogni.asquare.cube.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps a list of all URIs linked to each of the collections. Only URIs are stored to save memory for indexing.
 * When pulling out blocks of URIs which are going to be indexed the memory should remain at a predictable level.
 */
class IndexFolderUriReport {

  private final PartitionedIndexConfiguration partitionedIndexConfiguration;
  private final List<CollectionFolderUriReport> collectionFolderUriReports = new ArrayList<>();

  public IndexFolderUriReport(PartitionedIndexConfiguration partitionedIndexConfiguration) {
    this.partitionedIndexConfiguration = partitionedIndexConfiguration;
  }

  public PartitionedIndexConfiguration getIndexFolder() {
    return partitionedIndexConfiguration;
  }

  public boolean isEmpty() {
    return collectionFolderUriReports.isEmpty();
  }

  public int getSize() {
    return collectionFolderUriReports.stream()
                                     .mapToInt(collectionFolderUriReport -> collectionFolderUriReport.uris.size())
                                     .sum();
  }

  public List<CollectionFolderUriReport> getCollectionFolderReports() {
    return Collections.unmodifiableList(collectionFolderUriReports);
  }

  public void addCollection(PartitionConfiguration partitionConfiguration, List<String> collectionUris) {
    collectionFolderUriReports.add(
            new CollectionFolderUriReport(this, partitionConfiguration, collectionUris)
    );
  }


  /**
   * Creates and returns a new report instance which will contain maximum uris allowed by the size parameter.
   * Those uris are removed from the current report instance.
   *
   * @param size number of URIs to extract from current report
   * @return new instance of a report with number of URIs which exactly matches the size,
   * or if not enough uris are present the remainder of the uris
   */
  public IndexFolderUriReport extractSubset(int size) {
    if (isEmpty())
      throw new RuntimeException("please check for emptiness before invoking this method");

    IndexFolderUriReport extractedSubset = new IndexFolderUriReport(partitionedIndexConfiguration);

    extractSubset(extractedSubset, size);

    return extractedSubset;
  }

  private void extractSubset(IndexFolderUriReport extractedSubset, int sizeToGrow) {
    if (sizeToGrow == 0) return;
    if (collectionFolderUriReports.isEmpty()) return;

    int lastIndex = collectionFolderUriReports.size() - 1;
    CollectionFolderUriReport currentCollectionReport = collectionFolderUriReports.get(lastIndex);

    // support case where collection uris are too many
    int currentCollectionSize = currentCollectionReport.uris.size();
    if (currentCollectionSize > sizeToGrow) {
      List<String> extractedUris = new ArrayList<>(currentCollectionReport.uris.subList(0, sizeToGrow));
      List<String> remainingUris = new ArrayList<>(currentCollectionReport.uris.subList(sizeToGrow, currentCollectionSize));

      // add extracted uris to subset
      extractedSubset.addCollection(currentCollectionReport.partitionConfiguration, extractedUris);

      // create new CollectionFolderUriReport with remaining uris to replace existing instance
      collectionFolderUriReports.set(
              lastIndex,
              new CollectionFolderUriReport(this, currentCollectionReport.partitionConfiguration, remainingUris)
      );

      return;
    }

    // support case where collection uris are not enough
    // here we can move current collection report into extracted subset and remove it from the main
    extractedSubset.collectionFolderUriReports.add(currentCollectionReport);
    collectionFolderUriReports.remove(lastIndex);

    // recursion to fill her up
    extractSubset(extractedSubset, sizeToGrow - currentCollectionSize);
  }

  static class CollectionFolderUriReport {

    private final IndexFolderUriReport indexFolderUriReport;
    private final PartitionConfiguration partitionConfiguration;
    private final List<String> uris;

    public CollectionFolderUriReport(IndexFolderUriReport indexFolderUriReport,
                                     PartitionConfiguration partitionConfiguration,
                                     List<String> uris) {
      this.indexFolderUriReport = indexFolderUriReport;
      this.partitionConfiguration = partitionConfiguration;
      this.uris = uris;
    }

    public IndexFolderUriReport getIndexFolderUriReport() {
      return indexFolderUriReport;
    }

    public PartitionConfiguration getCollectionFolder() {
      return partitionConfiguration;
    }

    public List<String> getUris() {
      return Collections.unmodifiableList(uris);
    }
  }

}
