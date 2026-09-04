package com.igot.cb.transactional.cassandrautils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class CassandraPropertyReaderTest {

  @Test
  void getInstance_returnsSameSingletonAcrossCalls() {
    CassandraPropertyReader first = CassandraPropertyReader.getInstance();
    CassandraPropertyReader second = CassandraPropertyReader.getInstance();

    assertNotNull(first);
    assertSame(first, second);
  }

  @Test
  void readProperty_returnsKeyItself_whenPropertyNotConfigured() {
    CassandraPropertyReader reader = CassandraPropertyReader.getInstance();

    String value = reader.readProperty("community_id");

    assertEquals("community_id", value);
  }

  @Test
  void readProperty_returnsKeyItself_forArbitraryUnknownKey() {
    CassandraPropertyReader reader = CassandraPropertyReader.getInstance();

    String value = reader.readProperty("some_totally_unmapped_column");

    assertEquals("some_totally_unmapped_column", value);
  }
}
