package com.igot.cb.pores.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RespParamTest {

  @Test
  void noArgsConstructor_leavesFieldsNull() {
    RespParam params = new RespParam();

    assertNull(params.getResmsgid());
    assertNull(params.getMsgid());
    assertNull(params.getErr());
    assertNull(params.getStatus());
    assertNull(params.getErrmsg());
  }

  @Test
  void allArgsConstructor_setsEveryFieldInDeclarationOrder() {
    RespParam params = new RespParam("res-1", "msg-1", "ERR_CODE", "FAILED", "something went wrong");

    assertEquals("res-1", params.getResmsgid());
    assertEquals("msg-1", params.getMsgid());
    assertEquals("ERR_CODE", params.getErr());
    assertEquals("FAILED", params.getStatus());
    assertEquals("something went wrong", params.getErrmsg());
  }

  @Test
  void settersUpdateFieldsIndependently() {
    RespParam params = new RespParam();

    params.setStatus("SUCCESS");
    params.setErrmsg(null);

    assertEquals("SUCCESS", params.getStatus());
    assertNull(params.getErrmsg());
  }
}
