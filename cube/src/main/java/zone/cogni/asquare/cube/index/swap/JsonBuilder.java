package zone.cogni.asquare.cube.index.swap;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;

class JsonBuilder {

  /**
   * Factory methods for an {@link ObjectNode} builder.
   */
  public static ObjectNodeBuilder object() {
    return object(JsonNodeFactory.instance);
  }

  public static ObjectNodeBuilder object(JsonNodeFactory factory) {
    return new ObjectNodeBuilder(factory);
  }


  /**
   * Factory methods for an {@link ArrayNode} builder.
   */

  public static ArrayNodeBuilder array() {
    return array(JsonNodeFactory.instance);
  }

  public static ArrayNodeBuilder array(JsonNodeFactory factory) {
    return new ArrayNodeBuilder(factory);
  }

  public interface JsonNodeBuilder<T extends JsonNode> {

    /**
     * Construct and return the {@link JsonNode} instance.
     */
    T end();

  }

  private static abstract class AbstractNodeBuilder<T extends JsonNode> implements JsonNodeBuilder<T> {

    /**
     * The source of values.
     */
    @Nonnull
    protected final JsonNodeFactory factory;

    /**
     * The value under construction.
     */
    @Nonnull
    protected final T node;

    public AbstractNodeBuilder(@Nonnull JsonNodeFactory factory, @Nonnull T node) {
      this.factory = factory;
      this.node = node;
    }

    /**
     * Returns a valid JSON string, so long as {@code POJONode}s not used.
     */
    @Override
    public String toString() {
      return node.toString();
    }

  }

  public final static class ObjectNodeBuilder extends AbstractNodeBuilder<ObjectNode> {

    private ObjectNodeBuilder(JsonNodeFactory factory) {
      super(factory, factory.objectNode());
    }

    public ObjectNodeBuilder withNull(@Nonnull String field) {
      return with(field, factory.nullNode());
    }

    public ObjectNodeBuilder with(@Nonnull String field, int value) {
      return with(field, factory.numberNode(value));
    }

    public ObjectNodeBuilder with(@Nonnull String field, float value) {
      return with(field, factory.numberNode(value));
    }

    public ObjectNodeBuilder with(@Nonnull String field, boolean value) {
      return with(field, factory.booleanNode(value));
    }

    public ObjectNodeBuilder with(@Nonnull String field, @Nonnull String value) {
      return with(field, factory.textNode(value));
    }

    public ObjectNodeBuilder with(@Nonnull String field, @Nonnull JsonNode value) {
      node.set(field, value);
      return this;
    }

    public ObjectNodeBuilder with(@Nonnull String field, @Nonnull JsonNodeBuilder<?> builder) {
      return with(field, builder.end());
    }

    @Override
    public ObjectNode end() {
      return node;
    }

  }

  public final static class ArrayNodeBuilder extends AbstractNodeBuilder<ArrayNode> {

    private ArrayNodeBuilder(JsonNodeFactory factory) {
      super(factory, factory.arrayNode());
    }

    public ArrayNodeBuilder with(boolean value) {
      node.add(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull boolean... values) {
      for (boolean value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(long value) {
      node.add(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull long... values) {
      for (long value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(double value) {
      node.add(value);
      return this;
    }

    public ArrayNodeBuilder with(double... values) {
      for (double value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull String value) {
      node.add(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull String... values) {
      for (String value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull Iterable<String> values) {
      for (String value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull JsonNode value) {
      node.add(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull JsonNode... values) {
      for (JsonNode value : values) with(value);
      return this;
    }

    public ArrayNodeBuilder with(@Nonnull JsonNodeBuilder<?> value) {
      return with(value.end());
    }

    public ArrayNodeBuilder with(@Nonnull JsonNodeBuilder<?>... builders) {
      for (JsonNodeBuilder<?> builder : builders) with(builder);
      return this;
    }

    @Override
    public ArrayNode end() {
      return node;
    }

  }

  private JsonBuilder() {
  }

}
