package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationAsyncRequestTest {

  @Test
  void gettersAndSetters_roundTripAllFields() {
    NotificationAsyncRequest request = new NotificationAsyncRequest();

    request.setType("email");
    request.setPriority(1);
    request.setAction(Map.of("type", "email"));
    request.setIds(List.of("mod1@example.org"));
    request.setCopyEmail(List.of("cc@example.org"));

    assertEquals("email", request.getType());
    assertEquals(1, request.getPriority());
    assertEquals("email", request.getAction().get("type"));
    assertEquals(List.of("mod1@example.org"), request.getIds());
    assertEquals(List.of("cc@example.org"), request.getCopyEmail());
  }
}
