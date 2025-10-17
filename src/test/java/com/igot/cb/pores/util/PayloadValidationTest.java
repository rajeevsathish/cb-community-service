package com.igot.cb.pores.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayloadValidationTest {

    private PayloadValidation payloadValidation;
    private JsonSchemaCache schemaCache;
    private JsonSchema jsonSchema;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        schemaCache = mock(JsonSchemaCache.class);
        jsonSchema = mock(JsonSchema.class);
        payloadValidation = new PayloadValidation();
        payloadValidation.schemaCache = schemaCache; // inject manually
        objectMapper = new ObjectMapper();
    }

    @Test
    void testValidatePayload_validSingleObject() throws Exception {
        String json = "{\"name\":\"John\"}";
        JsonNode jsonNode = objectMapper.readTree(json);

        when(schemaCache.getSchema("user")).thenReturn(jsonSchema);
        when(jsonSchema.validate(any())).thenReturn(Collections.emptySet());

        assertDoesNotThrow(() -> payloadValidation.validatePayload("user", jsonNode));
    }

    @Test
    void testValidatePayload_validArray() throws Exception {
        String json = "[{\"name\":\"John\"}, {\"name\":\"Jane\"}]";
        JsonNode jsonNode = objectMapper.readTree(json);

        when(schemaCache.getSchema("user")).thenReturn(jsonSchema);
        when(jsonSchema.validate(any())).thenReturn(Collections.emptySet());

        assertDoesNotThrow(() -> payloadValidation.validatePayload("user", jsonNode));
    }

    @Test
    void testValidatePayload_schemaNotFound() throws Exception {
        String json = "{\"name\":\"John\"}";
        JsonNode jsonNode = objectMapper.readTree(json);

        when(schemaCache.getSchema("missing")).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload("missing", jsonNode));

        assertTrue(ex.getMessage().contains("Schema not found"));
    }

    @Test
    void testValidatePayload_withValidationErrors() throws Exception {
        String json = "{\"name\":\"\"}";
        JsonNode jsonNode = objectMapper.readTree(json);

        // Mock a ValidationMessage
        ValidationMessage mockValidationMessage = mock(ValidationMessage.class);
        when(mockValidationMessage.getMessage()).thenReturn("name must not be empty");

        Set<ValidationMessage> errors = new HashSet<>();
        errors.add(mockValidationMessage);

        when(schemaCache.getSchema("user")).thenReturn(jsonSchema);
        when(jsonSchema.validate(any())).thenReturn(errors);

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload("user", jsonNode));

        assertTrue(ex.getCode().contains("Failed to validate payload"));
    }


    @Test
    void testValidatePayload_unexpectedException() throws Exception {
        String json = "{\"name\":\"John\"}";
        JsonNode jsonNode = objectMapper.readTree(json);

        when(schemaCache.getSchema("user")).thenThrow(new RuntimeException("Unexpected failure"));

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload("user", jsonNode));

        assertEquals("Failed to validate payload", ex.getCode());
        assertEquals("Unexpected failure", ex.getMessage());
    }
}

