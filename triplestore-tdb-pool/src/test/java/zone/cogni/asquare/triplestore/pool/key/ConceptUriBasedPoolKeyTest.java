package zone.cogni.asquare.triplestore.pool.key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptUriBasedPoolKeyTest {

  @TempDir
  Path tempDir;

  @Test
  void testConstructorWithValidArguments() {
    String conceptUri = "http://example.com/concept";
    ConceptUriBasedPoolKey key = new ConceptUriBasedPoolKey(tempDir, conceptUri);

    assertNotNull(key);
    assertNotNull(key.getDirPath());
    assertTrue(key.getDirPath().toString().contains("http___example.com_concept"));
  }

  @Test
  void testConstructorWithFile() {
    String conceptUri = "http://example.com/concept";
    File tempFile = tempDir.toFile();
    ConceptUriBasedPoolKey key = new ConceptUriBasedPoolKey(tempFile, conceptUri);

    assertNotNull(key);
    assertNotNull(key.getDirPath());
    assertTrue(key.getDirPath().toString().contains("http___example.com_concept"));
  }

  @Test
  void testConstructorWithNullPath() {
    String conceptUri = "http://example.com/concept";

    assertThrows(IllegalArgumentException.class, () -> new ConceptUriBasedPoolKey((Path) null, conceptUri));
  }

  @Test
  void testConstructorWithNullConceptUri() {
    assertThrows(IllegalArgumentException.class, () -> new ConceptUriBasedPoolKey(tempDir, null));
  }

  @Test
  void testConstructorWithEmptyConceptUri() {
    assertThrows(IllegalArgumentException.class, () -> new ConceptUriBasedPoolKey(tempDir, ""));
  }

  @Test
  void testConstructorWithBlankConceptUri() {
    assertThrows(IllegalArgumentException.class, () -> new ConceptUriBasedPoolKey(tempDir, "   "));
  }

  @Test
  void testUriToDirNameConversion() {
    assertEquals("http___example.com_concept", ConceptUriBasedPoolKey.uriToDirName("http://example.com/concept"));
    assertEquals("https___example.com_path_to_resource", ConceptUriBasedPoolKey.uriToDirName("https://example.com/path/to/resource"));
    assertEquals("urn_isbn_1234567890", ConceptUriBasedPoolKey.uriToDirName("urn:isbn:1234567890"));
    assertEquals("http___example.com_resource_fragment", ConceptUriBasedPoolKey.uriToDirName("http://example.com/resource#fragment"));
  }

  @Test
  void testUriToDirNameWithComplexUri() {
    String complexUri = "https://example.com:8080/path/to/resource#fragment";
    String expected = "https___example.com_8080_path_to_resource_fragment";
    assertEquals(expected, ConceptUriBasedPoolKey.uriToDirName(complexUri));
  }

  @Test
  void testUriToDirNameWithCaseSensitivity() {
    String mixedCaseUri = "HTTP://EXAMPLE.COM/PATH";
    String expected = "http___example.com_path";
    assertEquals(expected, ConceptUriBasedPoolKey.uriToDirName(mixedCaseUri));
  }

  @Test
  void testKeyMethod() {
    String conceptUri = "http://example.com/concept";
    ConceptUriBasedPoolKey key = new ConceptUriBasedPoolKey(tempDir, conceptUri);

    String keyValue = key.key();
    assertNotNull(keyValue);
    assertTrue(keyValue.contains("http___example.com_concept"));
    assertEquals(key.getDirPath().toAbsolutePath().toString(), keyValue);
  }

  @Test
  void testEqualityAndHashCode() {
    String conceptUri = "http://example.com/concept";
    ConceptUriBasedPoolKey key1 = new ConceptUriBasedPoolKey(tempDir, conceptUri);
    ConceptUriBasedPoolKey key2 = new ConceptUriBasedPoolKey(tempDir, conceptUri);
    ConceptUriBasedPoolKey key3 = new ConceptUriBasedPoolKey(tempDir, "http://example.com/different");

    // Same URI should be equal
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());

    // Different URI should not be equal
    assertNotEquals(key1, key3);
    assertNotEquals(key1.hashCode(), key3.hashCode());
  }

  @Test
  void testToString() {
    String conceptUri = "http://example.com/concept";
    ConceptUriBasedPoolKey key = new ConceptUriBasedPoolKey(tempDir, conceptUri);

    String toString = key.toString();
    assertNotNull(toString);
    assertEquals(key.key(), toString);
    assertTrue(toString.contains("http___example.com_concept"));
  }

  @Test
  void testDifferentBasePaths() {
    String conceptUri = "http://example.com/concept";
    Path differentPath = tempDir.resolve("subfolder");

    ConceptUriBasedPoolKey key1 = new ConceptUriBasedPoolKey(tempDir, conceptUri);
    ConceptUriBasedPoolKey key2 = new ConceptUriBasedPoolKey(differentPath, conceptUri);

    // Keys should be different because they resolve to different absolute paths
    assertNotEquals(key1, key2);
    assertNotEquals(key1.key(), key2.key());
  }

  @Test
  void testGenericTypeInterface() {
    String conceptUri = "http://example.com/concept";
    ConceptUriBasedPoolKey key = new ConceptUriBasedPoolKey(tempDir, conceptUri);

    assertInstanceOf(RdfStoreServicePoolKey.class, key);
  }
}