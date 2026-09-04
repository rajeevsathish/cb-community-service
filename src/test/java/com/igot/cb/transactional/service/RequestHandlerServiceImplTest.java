package com.igot.cb.transactional.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RequestHandlerServiceImplTest {

  @Mock
  private RestTemplate restTemplate;

  private RequestHandlerServiceImpl requestHandlerService;

  @BeforeEach
  void setUp() {
    requestHandlerService = new RequestHandlerServiceImpl();
    ReflectionTestUtils.setField(requestHandlerService, "restTemplate", restTemplate);
  }

  @Test
  void fetchResultUsingPost_returnsResponseBody_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("status", "ok");
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(expected);

    Map<String, Object> result = requestHandlerService.fetchResultUsingPost(
        "https://example.org/api", Map.of("key", "value"), null);

    assertEquals(expected, result);
  }

  @Test
  void fetchResultUsingPost_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", null, "{\"error\":\"invalid\"}".getBytes(), null);
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenThrow(exception);

    Map<String, Object> result = requestHandlerService.fetchResultUsingPost(
        "https://example.org/api", Map.of("key", "value"), Map.of("X-Custom", "1"));

    assertEquals("invalid", result.get("error"));
  }

  @Test
  void fetchUsingGetWithHeadersProfile_returnsResponseBody_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("name", "test-user");
    ResponseEntity<Map> responseEntity = new ResponseEntity<>(expected, HttpStatus.OK);
    when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
        .thenReturn(responseEntity);

    Object result = requestHandlerService.fetchUsingGetWithHeadersProfile(
        "https://example.org/profile", null);

    assertEquals(expected, result);
  }

  @Test
  void fetchUsingGetWithHeadersProfile_returnsNull_onGenericException() {
    when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
        .thenThrow(new RuntimeException("boom"));

    Object result = requestHandlerService.fetchUsingGetWithHeadersProfile(
        "https://example.org/profile", null);

    assertNull(result);
  }
}
