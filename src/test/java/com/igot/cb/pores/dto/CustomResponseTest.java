package com.igot.cb.pores.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CustomResponseTest {

  @Test
  void getParams_lazilyCreatesRespParam_whenNotSet() {
    CustomResponse response = new CustomResponse();

    RespParam params = response.getParams();

    assertNotNull(params);
    assertSame(params, response.getParams());
  }

  @Test
  void getParams_returnsExplicitlySetInstance() {
    CustomResponse response = new CustomResponse();
    RespParam params = new RespParam();
    params.setStatus("OK");

    response.setParams(params);

    assertSame(params, response.getParams());
  }

  @Test
  void allArgsConstructor_setsEveryField() {
    RespParam params = new RespParam();
    Map<String, Object> result = Map.of("id", "comm1");

    CustomResponse response = new CustomResponse("created", params, HttpStatus.CREATED, result);

    assertEquals("created", response.getMessage());
    assertSame(params, response.getParams());
    assertEquals(HttpStatus.CREATED, response.getResponseCode());
    assertTrue(response.getResult().containsKey("id"));
  }

  @Test
  void defaultResult_isEmptyMap_whenNotProvided() {
    CustomResponse response = new CustomResponse();

    assertNotNull(response.getResult());
    assertTrue(response.getResult().isEmpty());
  }
}
