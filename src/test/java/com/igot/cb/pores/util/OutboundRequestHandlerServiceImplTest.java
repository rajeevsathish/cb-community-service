package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class OutboundRequestHandlerServiceImplTest {

  @Mock
  private RestTemplate restTemplate;

  private OutboundRequestHandlerServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new OutboundRequestHandlerServiceImpl();
    ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
  }

  @Test
  void fetchResultUsingPost_twoArg_returnsResponse_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("status", "ok");
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(expected);

    Object result = service.fetchResultUsingPost("https://example.org/api", Map.of("k", "v"));

    assertEquals(expected, result);
  }

  @Test
  void fetchResultUsingPost_twoArg_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", null, "{\"error\":\"invalid\"}".getBytes(), null);
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenThrow(exception);

    Object result = service.fetchResultUsingPost("https://example.org/api", Map.of("k", "v"));

    assertTrue(result instanceof Map);
    assertEquals("invalid", ((Map<?, ?>) result).get("error"));
  }

  @Test
  void fetchResultUsingPost_twoArg_returnsNull_onGenericException() {
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
        .thenThrow(new RuntimeException("boom"));

    Object result = service.fetchResultUsingPost("https://example.org/api", Map.of("k", "v"));

    assertNull(result);
  }

  @Test
  void fetchResult_returnsResponse_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("name", "value");
    when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(expected);

    Object result = service.fetchResult("https://example.org/api");

    assertEquals(expected, result);
  }

  @Test
  void fetchResult_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.NOT_FOUND, "Not Found", null, "{\"error\":\"missing\"}".getBytes(), null);
    when(restTemplate.getForObject(anyString(), eq(Map.class))).thenThrow(exception);

    Object result = service.fetchResult("https://example.org/api");

    assertEquals("missing", ((Map<?, ?>) result).get("error"));
  }

  @Test
  void fetchUsingGetWithHeaders_returnsBody_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("field", "value");
    ResponseEntity<Map> responseEntity = new ResponseEntity<>(expected, HttpStatus.OK);
    when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
        .thenReturn(responseEntity);

    Object result = service.fetchUsingGetWithHeaders("https://example.org/api", Map.of("X-Auth", "1"));

    assertEquals(expected, result);
  }

  @Test
  void fetchUsingGetWithHeaders_returnsNull_onException() {
    when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
        .thenThrow(new RuntimeException("boom"));

    Object result = service.fetchUsingGetWithHeaders("https://example.org/api", null);

    assertNull(result);
  }

  @Test
  void fetchUsingGetWithHeadersProfile_returnsBody_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("id", "u1");
    ResponseEntity<Map> responseEntity = new ResponseEntity<>(expected, HttpStatus.OK);
    when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
        .thenReturn(responseEntity);

    Object result = service.fetchUsingGetWithHeadersProfile("https://example.org/profile", null);

    assertEquals(expected, result);
  }

  @Test
  void fetchResultUsingPost_threeArg_returnsResponse_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("status", "ok");
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(expected);

    Map<String, Object> result = service.fetchResultUsingPost(
        "https://example.org/api", Map.of("k", "v"), Map.of("X-Auth", "1"));

    assertEquals(expected, result);
  }

  @Test
  void fetchResultUsingPost_threeArg_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", null, "{\"error\":\"invalid\"}".getBytes(), null);
    when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenThrow(exception);

    Map<String, Object> result = service.fetchResultUsingPost(
        "https://example.org/api", Map.of("k", "v"), null);

    assertEquals("invalid", result.get("error"));
  }

  @Test
  void fetchResultUsingPatch_returnsResponse_onSuccess() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("status", "patched");
    when(restTemplate.patchForObject(anyString(), any(), eq(Map.class))).thenReturn(expected);

    Map<String, Object> result = service.fetchResultUsingPatch(
        "https://example.org/api", Map.of("k", "v"), Map.of("X-Auth", "1"));

    assertEquals(expected, result);
  }

  @Test
  void fetchResultUsingPatch_returnsEmptyMap_whenResponseIsNull() {
    when(restTemplate.patchForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

    Map<String, Object> result = service.fetchResultUsingPatch(
        "https://example.org/api", Map.of("k", "v"), null);

    assertTrue(result.isEmpty());
  }

  @Test
  void fetchResultUsingPatch_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", null, "{\"error\":\"invalid\"}".getBytes(), null);
    when(restTemplate.patchForObject(anyString(), any(), eq(Map.class))).thenThrow(exception);

    Map<String, Object> result = service.fetchResultUsingPatch(
        "https://example.org/api", Map.of("k", "v"), null);

    assertEquals("invalid", result.get("error"));
  }

  @Test
  void fetchResultUsingPostAsString_returnsResponse_onSuccess() {
    when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("plain-response");

    Object result = service.fetchResultUsingPostAsString("https://example.org/api", Map.of("k", "v"));

    assertEquals("plain-response", result);
  }

  @Test
  void fetchResultUsingPostAsString_parsesErrorBody_onHttpClientErrorException() {
    HttpClientErrorException exception = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", null, "{\"error\":\"invalid\"}".getBytes(), null);
    when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(exception);

    Object result = service.fetchResultUsingPostAsString("https://example.org/api", Map.of("k", "v"));

    assertEquals("invalid", ((Map<?, ?>) result).get("error"));
  }

  @Test
  void fetchResultUsingPostAsString_returnsNull_onGenericException() {
    when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .thenThrow(new RuntimeException("boom"));

    Object result = service.fetchResultUsingPostAsString("https://example.org/api", Map.of("k", "v"));

    assertNull(result);
  }
}
