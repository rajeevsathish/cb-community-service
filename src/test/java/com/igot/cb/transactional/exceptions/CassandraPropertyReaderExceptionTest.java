package com.igot.cb.transactional.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class CassandraPropertyReaderExceptionTest {

    @Test
    void testConstructor_withMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException("Cassandra property error", cause);

        assertNotNull(exception);
        assertEquals("Cassandra property error", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testConstructor_withNullCause() {
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException("Cassandra property error", null);

        assertNotNull(exception);
        assertEquals("Cassandra property error", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testThrowingException() {
        Throwable cause = new IllegalStateException("Invalid property state");
        CassandraPropertyReaderException ex =
                assertThrows(CassandraPropertyReaderException.class, () -> {
                    throw new CassandraPropertyReaderException("Failed to read Cassandra property", cause);
                });

        assertEquals("Failed to read Cassandra property", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
