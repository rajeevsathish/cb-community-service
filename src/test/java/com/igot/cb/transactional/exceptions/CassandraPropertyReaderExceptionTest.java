package com.igot.cb.transactional.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class CassandraPropertyReaderExceptionTest {

  @Test
  void constructor_setsMessageAndCause() {
    Throwable cause = new RuntimeException("underlying IO failure");

    CassandraPropertyReaderException exception =
        new CassandraPropertyReaderException("Error loading properties from file 'x.properties'", cause);

    assertEquals("Error loading properties from file 'x.properties'", exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
