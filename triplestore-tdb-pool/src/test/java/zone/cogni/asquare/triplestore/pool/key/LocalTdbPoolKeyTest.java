package zone.cogni.asquare.triplestore.pool.key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalTdbPoolKeyTest {

  @TempDir
  Path tempDir;

  @Test
  void testConstructorWithPath() {
    String conceptUri = "http://example.com/concept";
    LocalTdbPoolKey key = new LocalTdbPoolKey(tempDir, conceptUri);

    assertNotNull(key);
    assertEquals(conceptUri, key.getUri());
    assertEquals(conceptUri, key.key());
    assertNotNull(key.getDirPath());
  }

  @Test
  void testConstructorWithFile() {
    String conceptUri = "http://example.com/concept";
    File tempFile = tempDir.toFile();
    LocalTdbPoolKey key = new LocalTdbPoolKey(tempFile, conceptUri);

    assertNotNull(key);
    assertEquals(conceptUri, key.getUri());
    assertEquals(conceptUri, key.key());
    assertNotNull(key.getDirPath());
  }

  @Test
  void testKeyReturnsUri() {
    String conceptUri = "http://example.com/test/concept";
    LocalTdbPoolKey key = new LocalTdbPoolKey(tempDir, conceptUri);

    // LocalTdbPoolKey.key() returns the URI directly, not the directory path
    assertEquals(conceptUri, key.key());
    assertEquals(key.getUri(), key.key());
  }

  @Test
  void testEqualityBasedOnKey() {
    String conceptUri = "http://example.com/concept";
    LocalTdbPoolKey key1 = new LocalTdbPoolKey(tempDir, conceptUri);
    LocalTdbPoolKey key2 = new LocalTdbPoolKey(tempDir, conceptUri);
    LocalTdbPoolKey key3 = new LocalTdbPoolKey(tempDir, "http://example.com/different");

    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());

    assertNotEquals(key1, key3);
    assertNotEquals(key1.hashCode(), key3.hashCode());
  }

  @Test
  void testToString() {
    String conceptUri = "http://example.com/concept";
    LocalTdbPoolKey key = new LocalTdbPoolKey(tempDir, conceptUri);

    String toString = key.toString();
    assertNotNull(toString);
    // toString() returns key() which for LocalTdbPoolKey is the original URI
    assertEquals(conceptUri, toString);
  }

  @Test
  void testInheritsFromConceptUriBasedPoolKey() {
    String conceptUri = "http://example.com/concept";
    LocalTdbPoolKey key = new LocalTdbPoolKey(tempDir, conceptUri);

    assertInstanceOf(ConceptUriBasedPoolKey.class, key);
    assertInstanceOf(RdfStoreServicePoolKey.class, key);
  }

  @Test
  void testDifferentPathsSameUri() {
    String conceptUri = "http://example.com/concept";
    Path differentPath = tempDir.resolve("different");

    LocalTdbPoolKey key1 = new LocalTdbPoolKey(tempDir, conceptUri);
    LocalTdbPoolKey key2 = new LocalTdbPoolKey(differentPath, conceptUri);

    // For LocalTdbPoolKey, equality is based on URI only (not path)
    // so keys with same URI are equal regardless of base path
    assertEquals(key1, key2);
    assertEquals(key1.key(), key2.key());
    // But the directory paths are different
    assertNotEquals(key1.getDirPath(), key2.getDirPath());
    // And the URI should be the same
    assertEquals(key1.getUri(), key2.getUri());
  }
}