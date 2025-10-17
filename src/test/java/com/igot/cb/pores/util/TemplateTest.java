package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateTest {

    @Test
    void testConstructorAndGetters() {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");

        Template template = new Template("sampleData", "templateId", params);

        assertEquals("sampleData", template.getData());
        assertEquals("templateId", template.getId());
        assertEquals(params, template.getParams());
    }

    @Test
    void testSettersAndGetters() {
        Template template = new Template(null, null, null);

        template.setData("newData");
        template.setId("newId");

        Map<String, Object> newParams = new HashMap<>();
        newParams.put("key2", 123);
        template.setParams(newParams);

        assertEquals("newData", template.getData());
        assertEquals("newId", template.getId());
        assertEquals(newParams, template.getParams());
    }

    @Test
    void testToString() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");

        Template template = new Template("data", "id123", params);

        String result = template.toString();
        assertTrue(result.contains("data='data'"));
        assertTrue(result.contains("id='id123'"));
        assertTrue(result.contains("params={name=test}"));
    }
}
