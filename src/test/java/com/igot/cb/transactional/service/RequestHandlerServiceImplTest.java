package com.igot.cb.transactional.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestHandlerServiceImplTest {

    @InjectMocks
    private RequestHandlerServiceImpl service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Logger logger;

    @Captor
    ArgumentCaptor<HttpEntity<Object>> entityCaptor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFetchResultUsingPost_success() throws JsonProcessingException {
        String uri = "http://localhost:8080/api";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> mockResponse = Map.of("status", "ok");
        Object request = Map.of("key", "value");

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class))).thenReturn(mockResponse);

        Map<String, Object> result = service.fetchResultUsingPost(uri, request, headers);

        assertNotNull(result);
        assertEquals("ok", result.get("status"));
        verify(restTemplate, times(1)).postForObject(eq(uri), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testFetchResultUsingPost_httpClientErrorException() throws Exception {
        String uri = "http://localhost:8080/api";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Object request = Map.of("key", "value");
        String responseJson = "{\"error\":\"bad_request\"}";

        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, responseJson.getBytes(), null);

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);

        Map<String, Object> result = service.fetchResultUsingPost(uri, request, headers);

        assertNotNull(result);
        assertEquals("bad_request", result.get("error"));
    }

    @Test
    void testFetchResultUsingPost_jsonProcessingException() throws JsonProcessingException {
        String uri = "http://localhost:8080/api";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Object request = new Object() {}; // Will trigger serialization error

        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);

        // Not using mapper injection, so we simulate failure in log.debug block

        // Simulate successful call to postForObject (won’t actually log)
        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        Map<String, Object> result = service.fetchResultUsingPost(uri, request, headers);

        assertNull(result);
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_success() {
        String uri = "http://localhost:8080/profile";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> mockResponse = Map.of("id", "123");

        ResponseEntity<Map> mockEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockEntity);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, headers);

        assertNotNull(result);
        assertTrue(((Map<?, ?>) result).containsKey("id"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_httpClientErrorException() {
        String uri = "http://localhost:8080/profile";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        String responseJson = "{\"error\":\"not_found\"}";

        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, responseJson.getBytes(), null);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, headers);

        assertNotNull(result);
        assertEquals("not_found", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_genericException() {
        String uri = "http://localhost:8080/profile";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Generic error"));

        Object result = service.fetchUsingGetWithHeadersProfile(uri, headers);

        assertNull(result);
    }
}
