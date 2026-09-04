package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProjectUtilTest {

  @Test
  void createDefaultResponse_buildsSuccessResponseForGivenApi() {
    ApiResponse response = ProjectUtil.createDefaultResponse("community.create");

    assertEquals("community.create", response.getId());
    assertEquals(Constants.API_VERSION_1, response.getVer());
    assertEquals(HttpStatus.OK, response.getResponseCode());
    assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    assertNotNull(response.getParams().getResMsgId());
    assertNotNull(response.getTs());
  }
}
