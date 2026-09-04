package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ApiRespParamTest {

  @Test
  void noArgsConstructor_leavesFieldsNull() {
    ApiRespParam params = new ApiRespParam();

    assertNull(params.getResMsgId());
    assertNull(params.getMsgId());
  }

  @Test
  void idConstructor_setsResMsgIdAndMsgIdToSameValue() {
    ApiRespParam params = new ApiRespParam("request-123");

    assertEquals("request-123", params.getResMsgId());
    assertEquals("request-123", params.getMsgId());
  }

  @Test
  void setters_updateFieldsIndependently() {
    ApiRespParam params = new ApiRespParam();

    params.setErr("ERR_VALIDATION");
    params.setStatus("FAILED");
    params.setErrMsg("Invalid payload");

    assertEquals("ERR_VALIDATION", params.getErr());
    assertEquals("FAILED", params.getStatus());
    assertEquals("Invalid payload", params.getErrMsg());
  }

  @Test
  void idConstructor_doesNotAffectErrorFields() {
    ApiRespParam params = new ApiRespParam("request-123");

    assertNull(params.getErr());
    assertNull(params.getStatus());
    assertNull(params.getErrMsg());
  }
}
