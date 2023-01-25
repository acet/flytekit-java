/*
 * Copyright 2021 Flyte Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.flyte.flytekit.jackson.deserializers;

import static java.util.stream.Collectors.toList;
import static org.flyte.flytekit.jackson.serializers.SdkBindingDataSerializationProtocol.KIND;
import static org.flyte.flytekit.jackson.serializers.SdkBindingDataSerializationProtocol.LITERAL;
import static org.flyte.flytekit.jackson.serializers.SdkBindingDataSerializationProtocol.SCALAR;
import static org.flyte.flytekit.jackson.serializers.SdkBindingDataSerializationProtocol.TYPE;
import static org.flyte.flytekit.jackson.serializers.SdkBindingDataSerializationProtocol.VALUE;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.flyte.api.v1.Literal;
import org.flyte.api.v1.LiteralType;
import org.flyte.api.v1.Primitive;
import org.flyte.api.v1.Scalar;
import org.flyte.api.v1.SimpleType;
import org.flyte.flytekit.SdkBindingData;

class SdkBindingDataDeserializer extends StdDeserializer<SdkBindingData<?>> {
  private static final long serialVersionUID = 0L;

  public SdkBindingDataDeserializer() {
    super(SdkBindingData.class);
  }

  @Override
  public SdkBindingData<?> deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    return transform(tree);
  }

  private SdkBindingData<?> transform(JsonNode tree) {
    Literal.Kind literalKind = Literal.Kind.valueOf(tree.get(LITERAL).asText());
    switch (literalKind) {
      case SCALAR:
        return transformScalar(tree);
      case COLLECTION:
        return transformCollection(tree);

      case MAP:
        return transformMap(tree);

      default:
        throw new UnsupportedOperationException(
            String.format("Not supported literal type %s", literalKind.name()));
    }
  }

  private static SdkBindingData<? extends Serializable> transformScalar(JsonNode tree) {
    Scalar.Kind scalarKind = Scalar.Kind.valueOf(tree.get(SCALAR).asText());
    switch (scalarKind) {
      case PRIMITIVE:
        Primitive.Kind primitiveKind = Primitive.Kind.valueOf(tree.get("primitive").asText());
        switch (primitiveKind) {
          case INTEGER_VALUE:
            return SdkBindingData.ofInteger(tree.get(VALUE).longValue());
          case BOOLEAN_VALUE:
            return SdkBindingData.ofBoolean(tree.get(VALUE).booleanValue());
          case STRING_VALUE:
            return SdkBindingData.ofString(tree.get(VALUE).asText());
          case DURATION:
            return SdkBindingData.ofDuration(Duration.parse(tree.get(VALUE).asText()));
          case DATETIME:
            return SdkBindingData.ofDatetime(Instant.parse(tree.get(VALUE).asText()));
          case FLOAT_VALUE:
            return SdkBindingData.ofFloat(tree.get(VALUE).doubleValue());
        }
        throw new UnsupportedOperationException(
            "Type contains an unsupported primitive: " + primitiveKind);

      case GENERIC:
      case BLOB:
      default:
        throw new UnsupportedOperationException(
            "Type contains an unsupported scalar: " + scalarKind);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> SdkBindingData<List<T>> transformCollection(JsonNode tree) {
    LiteralType literalType = readLiteralType(tree.get(TYPE));
    Iterator<JsonNode> elements = tree.get(VALUE).elements();

    switch (literalType.getKind()) {
      case SIMPLE_TYPE:
      case MAP_VALUE_TYPE:
      case COLLECTION_TYPE:
        List<? extends SdkBindingData<?>> collection =
            streamOf(elements).map(this::transform).collect(toList());
        return SdkBindingData.ofBindingCollection(
            LiteralType.ofCollectionType(literalType), (List<SdkBindingData<T>>) collection);

      case SCHEMA_TYPE:
      case BLOB_TYPE:
      default:
        throw new UnsupportedOperationException(
            "Type contains a collection of an supported literal type: " + literalType.getKind());
    }
  }

  @SuppressWarnings("unchecked")
  private <T> SdkBindingData<Map<String, T>> transformMap(JsonNode tree) {
    LiteralType literalType = readLiteralType(tree.get(TYPE));
    JsonNode valueNode = tree.get(VALUE);
    List<Map.Entry<String, JsonNode>> entries =
        streamOf(valueNode.fieldNames())
            .map(name -> Map.entry(name, valueNode.get(name)))
            .collect(toList());
    switch (literalType.getKind()) {
      case SIMPLE_TYPE:
      case MAP_VALUE_TYPE:
      case COLLECTION_TYPE:
        Map<String, SdkBindingData<T>> bindingDataMap =
            entries.stream()
                .map(
                    entry ->
                        Map.entry(entry.getKey(), (SdkBindingData<T>) transform(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return SdkBindingData.ofBindingMap(LiteralType.ofMapValueType(literalType), bindingDataMap);

      case SCHEMA_TYPE:
      case BLOB_TYPE:
      default:
        throw new UnsupportedOperationException(
            "Type contains a map of an supported literal type: " + literalType.getKind());
    }
  }

  private LiteralType readLiteralType(JsonNode typeNode) {
    LiteralType.Kind kind = LiteralType.Kind.valueOf(typeNode.get(KIND).asText());
    switch (kind) {
      case SIMPLE_TYPE:
        return LiteralType.ofSimpleType(SimpleType.valueOf(typeNode.get(VALUE).asText()));
      case MAP_VALUE_TYPE:
        return LiteralType.ofMapValueType(readLiteralType(typeNode.get(VALUE).get(TYPE)));
      case COLLECTION_TYPE:
        return LiteralType.ofCollectionType(readLiteralType(typeNode.get(VALUE).get(TYPE)));

      case SCHEMA_TYPE:
      case BLOB_TYPE:
      default:
        throw new UnsupportedOperationException(
            "Type contains a collection/map of an supported literal type: " + kind);
    }
  }

  private <T> Stream<T> streamOf(Iterator<T> nodes) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(nodes, Spliterator.ORDERED), false);
  }
}