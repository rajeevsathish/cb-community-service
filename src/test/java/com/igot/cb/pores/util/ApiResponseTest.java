package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiResponseTest {

  @Test
  void noArgsConstructor_initializesVersionTimestampAndParams() {
    ApiResponse response = new ApiResponse();

    assertEquals("v1", response.getVer());
    assertNotNull(response.getTs());
    assertNotNull(response.getParams());
    assertNotNull(response.getParams().getResMsgId());
  }

  @Test
  void idConstructor_setsIdAndStillInitializesDefaults() {
    ApiResponse response = new ApiResponse("community.create");

    assertEquals("community.create", response.getId());
    assertEquals("v1", response.getVer());
    assertNotNull(response.getParams());
  }

  @Test
  void put_and_get_storeAndRetrieveValues() {
    ApiResponse response = new ApiResponse();

    response.put("communityId", "comm1");

    assertEquals("comm1", response.get("communityId"));
    assertTrue(response.containsKey("communityId"));
    assertFalse(response.containsKey("missingKey"));
  }

  @Test
  void putAll_mergesGivenMapIntoResult() {
    ApiResponse response = new ApiResponse();
    response.put("existing", "value");

    response.putAll(Map.of("communityId", "comm1", "status", "ACTIVE"));

    assertEquals("value", response.get("existing"));
    assertEquals("comm1", response.get("communityId"));
    assertEquals("ACTIVE", response.get("status"));
  }

  @Test
  void setResult_replacesEntireResultMap() {
    ApiResponse response = new ApiResponse();
    response.put("stale", "data");

    response.setResult(Map.of("fresh", "data"));

    assertFalse(response.containsKey("stale"));
    assertEquals("data", response.get("fresh"));
  }

  @Test
  void settersUpdateFieldsIndependently() {
    ApiResponse response = new ApiResponse();

    response.setId("community.read");
    response.setVer("v2");
    response.setTs("2026-09-04T00:00:00.000Z");
    response.setResponseCode(HttpStatus.OK);
    ApiRespParam params = new ApiRespParam("req-1");
    response.setParams(params);

    assertEquals("community.read", response.getId());
    assertEquals("v2", response.getVer());
    assertEquals("2026-09-04T00:00:00.000Z", response.getTs());
    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(params, response.getParams());
  }
}
