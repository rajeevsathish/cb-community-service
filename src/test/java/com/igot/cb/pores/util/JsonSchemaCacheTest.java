package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.networknt.schema.JsonSchema;
import org.junit.jupiter.api.Test;

class JsonSchemaCacheTest {

  private final JsonSchemaCache jsonSchemaCache = new JsonSchemaCache();

  @Test
  void getSchema_loadsRealSchemaFromClasspath() {
    JsonSchema schema = jsonSchemaCache.getSchema(Constants.PAYLOAD_VALIDATION_FILE);

    assertNotNull(schema);
  }

  @Test
  void getSchema_returnsCachedInstance_onSecondCall() {
    JsonSchema first = jsonSchemaCache.getSchema(Constants.CATEGORY_PAYLOAD_VALIDATION_FILE);
    JsonSchema second = jsonSchemaCache.getSchema(Constants.CATEGORY_PAYLOAD_VALIDATION_FILE);

    assertSame(first, second);
  }

  @Test
  void getSchema_throwsRuntimeException_whenSchemaPathDoesNotExist() {
    assertThrows(RuntimeException.class,
        () -> jsonSchemaCache.getSchema("/payloadValidation/does-not-exist.json"));
  }
}
