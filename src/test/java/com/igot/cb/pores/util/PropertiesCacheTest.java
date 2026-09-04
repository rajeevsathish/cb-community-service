package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PropertiesCacheTest {

  @Test
  void getInstance_returnsSameSingletonAcrossCalls() {
    PropertiesCache first = PropertiesCache.getInstance();
    PropertiesCache second = PropertiesCache.getInstance();

    assertNotNull(first);
    assertSame(first, second);
  }

  @Test
  void getProperty_returnsRealValue_forConfiguredKey() {
    PropertiesCache cache = PropertiesCache.getInstance();

    String value = cache.getProperty("sso.realm");

    assertEquals("sunbird", value);
  }

  @Test
  void getProperty_returnsKeyItself_whenPropertyNotConfigured() {
    PropertiesCache cache = PropertiesCache.getInstance();

    String value = cache.getProperty("this.key.does.not.exist.anywhere");

    assertEquals("this.key.does.not.exist.anywhere", value);
  }

  @Test
  void readProperty_returnsRealValue_forConfiguredKey() {
    PropertiesCache cache = PropertiesCache.getInstance();

    String value = cache.readProperty("sso.realm");

    assertEquals("sunbird", value);
  }

  @Test
  void readProperty_returnsNull_whenPropertyNotConfigured() {
    PropertiesCache cache = PropertiesCache.getInstance();

    String value = cache.readProperty("this.key.does.not.exist.anywhere");

    assertNull(value);
  }
}
