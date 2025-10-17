package com.igot.cb.pores.util;

import com.networknt.schema.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonSchemaCacheTest {

    private JsonSchemaCache jsonSchemaCache;

    @BeforeEach
    void setUp() {
        jsonSchemaCache = new JsonSchemaCache();
    }

    @Test
    void testGetSchema_firstTime_loadsAndCaches() {
        String schemaKey = "test.schema.key";
        String schemaPath = "test-schema.json"; // available in classpath

        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.getProperty(schemaKey)).thenReturn(schemaPath);

            JsonSchema schema = jsonSchemaCache.getSchema(schemaKey);
            assertNotNull(schema);

            // Second call should use cache
            JsonSchema cachedSchema = jsonSchemaCache.getSchema(schemaKey);
            assertSame(schema, cachedSchema);
        }
    }

    @Test
    void testGetSchema_whenSchemaFileIsMissing_shouldThrowException() {
        String schemaKey = "missing.schema";
        String schemaPath = "nonexistent.json";

        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.getProperty(schemaKey)).thenReturn(schemaPath);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                jsonSchemaCache.getSchema(schemaKey);
            });

            assertTrue(exception.getMessage().contains("Failed to load JSON schema"));
        }
    }
}

