package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayloadValidationTest {

  @Mock
  private JsonSchemaCache schemaCache;

  @Mock
  private JsonSchema jsonSchema;

  private PayloadValidation payloadValidation;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    payloadValidation = new PayloadValidation();
    ReflectionTestUtils.setField(payloadValidation, "schemaCache", schemaCache);
  }

  @Test
  void validatePayload_passes_whenSchemaValidationSucceeds() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"name\":\"community-1\"}");
    when(schemaCache.getSchema("communitySchema")).thenReturn(jsonSchema);
    when(jsonSchema.validate(payload)).thenReturn(Set.of());

    assertDoesNotThrow(() -> payloadValidation.validatePayload("communitySchema", payload));
  }

  @Test
  void validatePayload_throwsCustomException_whenSchemaNotFound() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"name\":\"community-1\"}");
    when(schemaCache.getSchema("missingSchema")).thenReturn(null);

    CustomException exception = assertThrows(CustomException.class,
        () -> payloadValidation.validatePayload("missingSchema", payload));

    org.junit.jupiter.api.Assertions.assertEquals("Failed to validate payload", exception.getCode());
    org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("Schema not found for key"));
  }

  @Test
  void validatePayload_throwsCustomException_whenValidationMessagesPresent() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"name\":\"community-1\"}");
    ValidationMessage message = mock(ValidationMessage.class);
    when(message.getMessage()).thenReturn("name is required");
    when(schemaCache.getSchema("communitySchema")).thenReturn(jsonSchema);
    when(jsonSchema.validate(payload)).thenReturn(Set.of(message));

    assertThrows(CustomException.class,
        () -> payloadValidation.validatePayload("communitySchema", payload));
  }

  @Test
  void validatePayload_validatesEachElement_whenPayloadIsArray() throws Exception {
    JsonNode payload = objectMapper.readTree("[{\"name\":\"c1\"},{\"name\":\"c2\"}]");
    when(schemaCache.getSchema("communitySchema")).thenReturn(jsonSchema);
    when(jsonSchema.validate(org.mockito.ArgumentMatchers.any(JsonNode.class))).thenReturn(Set.of());

    payloadValidation.validatePayload("communitySchema", payload);

    verify(jsonSchema, times(2)).validate(org.mockito.ArgumentMatchers.any(JsonNode.class));
  }
}
