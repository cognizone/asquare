package zone.cogni.asquare.cube.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class IndexFolderUriReport {

  static class CollectionFolderUriReport {

    private final IndexFolderUriReport indexFolderUriReport;
    private final CollectionFolder collectionFolder;
    private final List<String> uris;

    public CollectionFolderUriReport(IndexFolderUriReport indexFolderUriReport,
                                     CollectionFolder collectionFolder,
                                     List<String> uris) {
      this.indexFolderUriReport = indexFolderUriReport;
      this.collectionFolder = collectionFolder;
      this.uris = uris;
    }

    public IndexFolderUriReport getIndexFolderUriReport() {
      return indexFolderUriReport;
    }

    public CollectionFolder getCollectionFolder() {
      return collectionFolder;
    }

    public List<String> getUris() {
      return Collections.unmodifiableList(uris);
    }
  }

  private final IndexFolder indexFolder;
  private final List<CollectionFolderUriReport> collectionFolderUriReports = new ArrayList<>();

  public IndexFolderUriReport(IndexFolder indexFolder) {
    this.indexFolder = indexFolder;
  }

  public IndexFolder getIndexFolder() {
    return indexFolder;
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

  public void addCollection(CollectionFolder collectionFolder, List<String> collectionUris) {
    collectionFolderUriReports.add(
            new CollectionFolderUriReport(this, collectionFolder, collectionUris)
    );
  }

  public IndexFolderUriReport extractSubset(int size) {
    if (isEmpty())
      throw new RuntimeException("please check for emptiness before invoking this method");

    IndexFolderUriReport extractedSubset = new IndexFolderUriReport(indexFolder);

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
      extractedSubset.addCollection(currentCollectionReport.collectionFolder, extractedUris);

      // create new CollectionFolderUriReport with remaining uris to replace existing instance
      collectionFolderUriReports.set(
              lastIndex,
              new CollectionFolderUriReport(this, currentCollectionReport.collectionFolder, remainingUris)
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

}
