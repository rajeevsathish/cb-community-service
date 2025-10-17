package com.igot.cb.pores.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomResponseTest {

    @Test
    void testAllArgsConstructorAndGettersSetters() {
        RespParam param = new RespParam();
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("key", "value");

        CustomResponse response = new CustomResponse("Success", param, HttpStatus.OK, resultMap);

        assertEquals("Success", response.getMessage());
        assertEquals(param, response.getParams());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(resultMap, response.getResult());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        CustomResponse response = new CustomResponse();

        RespParam param = new RespParam();
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("item", 123);

        response.setMessage("Done");
        response.setParams(param);
        response.setResponseCode(HttpStatus.CREATED);
        response.setResult(resultMap);

        assertEquals("Done", response.getMessage());
        assertEquals(param, response.getParams());
        assertEquals(HttpStatus.CREATED, response.getResponseCode());
        assertEquals(resultMap, response.getResult());
    }

    @Test
    void testGetParamsCreatesNewWhenNull() {
        CustomResponse response = new CustomResponse();
        response.setParams(null); // explicitly set to null

        RespParam param = response.getParams(); // should initialize a new instance
        assertNotNull(param);
        assertEquals(param, response.getParams()); // should return the same instance next time
    }

    @Test
    void testDefaultResultIsEmptyMap() {
        CustomResponse response = new CustomResponse();
        assertNotNull(response.getResult());
        assertTrue(response.getResult().isEmpty());

        response.getResult().put("test", "value");
        assertEquals("value", response.getResult().get("test"));
    }
}

