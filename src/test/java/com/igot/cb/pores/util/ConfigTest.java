package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void gettersAndSetters_roundTripAllFields() {
    Config config = new Config();

    config.setSender("no-reply@example.org");
    config.setTopic("community.notify");
    config.setOtp("123456");
    config.setSubject("Moderator request");

    assertEquals("no-reply@example.org", config.getSender());
    assertEquals("community.notify", config.getTopic());
    assertEquals("123456", config.getOtp());
    assertEquals("Moderator request", config.getSubject());
  }
}
